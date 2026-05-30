package main

import (
	"fmt"
	"os"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"github.com/miekg/pkcs11"
)

// Session-Pooling in Go: miekg/pkcs11 SessionHandle ist NICHT goroutine-safe.
// Wer mehrere Worker laufen lassen will, braucht so viele Sessions wie Worker
// (oder eine Synchronisation, die jeden Sign-Pfad serialisiert — sinnlos).
//
// Pattern: Channel als Pool. Workers ziehen Session raus, geben sie zurueck.
// C_Login muss nur EINMAL pro Token-Application gemacht werden — der Login-State
// ist anwendungsweit, nicht session-weit (PKCS#11 v2.40 §11.4).

const (
	poolSize      = 8
	totalOps      = 10000
	messageSize   = 64
)

func main() {
	if err := run(); err != nil {
		fmt.Fprintf(os.Stderr, "Fehler: %v\n", err)
		os.Exit(1)
	}
}

func run() error {
	module := env("PKCS11_MODULE", "/usr/lib/softhsm/libsofthsm2.so")
	tokenLabel := env("PKCS11_TOKEN_LABEL", "dev-token")
	pin := env("PKCS11_USER_PIN", "987654")
	keyID := []byte{0x05}

	p11 := pkcs11.New(module)
	if p11 == nil {
		return fmt.Errorf("PKCS#11-Modul kann nicht geladen werden: %s", module)
	}
	defer p11.Destroy()
	if err := p11.Initialize(); err != nil {
		return fmt.Errorf("C_Initialize: %w", err)
	}
	defer func() { logIfError("C_Finalize", p11.Finalize()) }()

	slot, err := findSlot(p11, tokenLabel)
	if err != nil {
		return err
	}

	// Pool aufbauen: N Sessions, eine davon loggt ein.
	pool := make(chan pkcs11.SessionHandle, poolSize)
	var allSessions []pkcs11.SessionHandle
	for i := 0; i < poolSize; i++ {
		s, err := p11.OpenSession(slot, pkcs11.CKF_SERIAL_SESSION|pkcs11.CKF_RW_SESSION)
		if err != nil {
			return fmt.Errorf("C_OpenSession (#%d): %w", i, err)
		}
		allSessions = append(allSessions, s)
		pool <- s
	}
	defer func() {
		for _, s := range allSessions {
			logIfError("C_CloseSession", p11.CloseSession(s))
		}
	}()

	// Login auf einer Session — gilt fuer alle Sessions derselben Anwendung+Token.
	if err := p11.Login(allSessions[0], pkcs11.CKU_USER, pin); err != nil {
		return fmt.Errorf("C_Login: %w", err)
	}
	defer func() { logIfError("C_Logout", p11.Logout(allSessions[0])) }()

	// Key handle ist sessionunabhaengig, solange die App eingeloggt ist —
	// wir koennen ihn ueber eine Session ermitteln und in allen wiederverwenden.
	hmacKey, err := findKey(p11, allSessions[0], pkcs11.CKO_SECRET_KEY, keyID)
	if err != nil {
		return fmt.Errorf("HMAC-Key nicht gefunden (CKA_ID=05 — make gen-hmac?): %w", err)
	}

	data := make([]byte, messageSize)
	for i := range data {
		data[i] = byte(i)
	}

	// --- Sequenziell (1 Session, 1 Worker) ---
	seqStart := time.Now()
	for i := 0; i < totalOps; i++ {
		if _, err := hmacOnce(p11, allSessions[0], hmacKey, data); err != nil {
			return fmt.Errorf("sequentieller Sign #%d: %w", i, err)
		}
	}
	seqElapsed := time.Since(seqStart)

	// --- Parallel (N Sessions, N Worker) ---
	var counter atomic.Int64
	var wg sync.WaitGroup
	parStart := time.Now()
	for w := 0; w < poolSize; w++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for counter.Add(1) <= int64(totalOps) {
				s := <-pool
				_, err := hmacOnce(p11, s, hmacKey, data)
				pool <- s
				if err != nil {
					fmt.Fprintf(os.Stderr, "worker sign error: %v\n", err)
					return
				}
			}
		}()
	}
	wg.Wait()
	parElapsed := time.Since(parStart)

	speedup := float64(seqElapsed) / float64(parElapsed)
	fmt.Printf("Operationen:    %d × HMAC-SHA256(%d Bytes)\n", totalOps, messageSize)
	fmt.Printf("Pool-Groesse:   %d Sessions\n", poolSize)
	fmt.Printf("Sequenziell:    %v (%.0f ops/s)\n", seqElapsed, float64(totalOps)/seqElapsed.Seconds())
	fmt.Printf("Parallel (×%d): %v (%.0f ops/s)\n", poolSize, parElapsed, float64(totalOps)/parElapsed.Seconds())
	fmt.Printf("Speedup:        %.2fx\n", speedup)
	return nil
}

func hmacOnce(p11 *pkcs11.Ctx, session pkcs11.SessionHandle, key pkcs11.ObjectHandle, data []byte) ([]byte, error) {
	mech := []*pkcs11.Mechanism{pkcs11.NewMechanism(pkcs11.CKM_SHA256_HMAC, nil)}
	if err := p11.SignInit(session, mech, key); err != nil {
		return nil, fmt.Errorf("C_SignInit: %w", err)
	}
	return p11.Sign(session, data)
}

func findSlot(p11 *pkcs11.Ctx, tokenLabel string) (uint, error) {
	slots, err := p11.GetSlotList(true)
	if err != nil {
		return 0, err
	}
	for _, slot := range slots {
		info, err := p11.GetTokenInfo(slot)
		if err != nil {
			continue
		}
		if strings.TrimSpace(info.Label) == tokenLabel {
			return slot, nil
		}
	}
	return 0, fmt.Errorf("Token mit Label %q nicht gefunden", tokenLabel)
}

func findKey(p11 *pkcs11.Ctx, session pkcs11.SessionHandle, class uint, keyID []byte) (pkcs11.ObjectHandle, error) {
	template := []*pkcs11.Attribute{
		pkcs11.NewAttribute(pkcs11.CKA_CLASS, class),
		pkcs11.NewAttribute(pkcs11.CKA_ID, keyID),
	}
	if err := p11.FindObjectsInit(session, template); err != nil {
		return 0, err
	}
	defer p11.FindObjectsFinal(session)
	objects, _, err := p11.FindObjects(session, 2)
	if err != nil {
		return 0, err
	}
	if len(objects) != 1 {
		return 0, fmt.Errorf("erwartet genau einen Treffer fuer class=%d CKA_ID=%x, gefunden: %d", class, keyID, len(objects))
	}
	return objects[0], nil
}

func env(name, fallback string) string {
	value := os.Getenv(name)
	if value == "" {
		return fallback
	}
	return value
}

func logIfError(op string, err error) {
	if err != nil {
		fmt.Fprintf(os.Stderr, "Warnung: %s: %v\n", op, err)
	}
}
