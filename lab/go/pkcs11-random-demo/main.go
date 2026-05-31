package main

import (
	"crypto/rand"
	"encoding/hex"
	"fmt"
	"math"
	"os"
	"strings"
	"time"

	"github.com/miekg/pkcs11"
)

// HSM-RNG (C_GenerateRandom) Demo:
//  1) 32 Byte Proof-of-Life — Hex-Dump, beweist dass der Pfad funktioniert.
//  2) Durchsatz-Vergleich gegen Go-`crypto/rand` (auf Linux = getrandom(2),
//     also der Kernel-RNG ueber /dev/urandom-Pfad).
//  3) Verteilungs-Check: Shannon-Entropie der ersten 64 KB.
//
// Anders als der Bash-Test haelt diese Demo die Session offen — der
// HSM-Overhead je Aufruf entfaellt fast komplett. Echte HSMs sind selbst
// dann oft 10-100x langsamer als der Host-Kernel, weil der Hardware-TRNG
// physikalisch entropie-limitiert ist.

const (
	// SoftHSM hat keine harte Output-Groesse pro Aufruf, viele reale HSMs
	// limitieren auf 256 Byte (RSA-Modulus-aligned) oder 1024 Byte. Wir
	// schicken 8 KB pro Call — passt in alle gaengigen Vendor-Limits, wenn
	// man es konservativ formuliert, und macht den Per-Call-Overhead
	// vernachlaessigbar gegenueber dem Datentransfer.
	chunkSize  = 8 * 1024
	totalBytes = 1 * 1024 * 1024
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

	// CKF_RNG-Check: ohne dieses Bit ist C_GenerateRandom nicht garantiert.
	info, err := p11.GetTokenInfo(slot)
	if err != nil {
		return fmt.Errorf("C_GetTokenInfo: %w", err)
	}
	if info.Flags&pkcs11.CKF_RNG == 0 {
		return fmt.Errorf("Token meldet kein CKF_RNG — C_GenerateRandom nicht verfuegbar")
	}
	fmt.Println("CKF_RNG: gesetzt")

	session, err := p11.OpenSession(slot, pkcs11.CKF_SERIAL_SESSION)
	if err != nil {
		return fmt.Errorf("C_OpenSession: %w", err)
	}
	defer func() { logIfError("C_CloseSession", p11.CloseSession(session)) }()
	// Login ist fuer den RNG-Pfad spec-mae 'CKR_USER_NOT_LOGGED_IN'-frei,
	// aber manche HSMs verlangen ihn (Vendor-Erweiterung). Wir loggen ein,
	// damit die Demo auf Hardware-HSMs ohne Anpassung laeuft.
	if err := p11.Login(session, pkcs11.CKU_USER, pin); err != nil {
		return fmt.Errorf("C_Login: %w", err)
	}
	defer func() { logIfError("C_Logout", p11.Logout(session)) }()

	// 1) 32-Byte Proof-of-Life.
	fmt.Println("\n=== 1) Proof-of-Life: 32 Byte aus dem HSM ===")
	sample, err := p11.GenerateRandom(session, 32)
	if err != nil {
		return fmt.Errorf("C_GenerateRandom(32): %w", err)
	}
	fmt.Printf("  Hex: %s\n", hex.EncodeToString(sample))

	// 2) Durchsatz-Vergleich.
	fmt.Println("\n=== 2) Durchsatz HSM vs crypto/rand ===")
	hsmBytes, hsmDur, err := timeGenerate(totalBytes, chunkSize, func(n int) ([]byte, error) {
		return p11.GenerateRandom(session, n)
	})
	if err != nil {
		return fmt.Errorf("HSM-Sammeln: %w", err)
	}
	osBytes, osDur, err := timeGenerate(totalBytes, chunkSize, func(n int) ([]byte, error) {
		buf := make([]byte, n)
		_, err := rand.Read(buf)
		return buf, err
	})
	if err != nil {
		return fmt.Errorf("crypto/rand-Sammeln: %w", err)
	}
	report("HSM (C_GenerateRandom, persistente Session)", len(hsmBytes), hsmDur)
	report("crypto/rand (Linux getrandom)", len(osBytes), osDur)
	if hsmDur > 0 && osDur > 0 {
		ratio := float64(osDur) / float64(hsmDur)
		if ratio < 1 {
			fmt.Printf("  HSM ist Faktor %.1fx langsamer als crypto/rand\n", 1.0/ratio)
		} else {
			fmt.Printf("  HSM ist Faktor %.1fx schneller als crypto/rand (unerwartet — SoftHSM-Spezialfall)\n", ratio)
		}
	}

	// 3) Verteilungs-Check der ersten 64 KB.
	fmt.Println("\n=== 3) Verteilungs-Check ueber 64 KB HSM-Bytes ===")
	bucket := hsmBytes
	if len(bucket) > 64*1024 {
		bucket = bucket[:64*1024]
	}
	ent := shannonEntropy(bucket)
	fmt.Printf("  Shannon-Entropie: %.4f bit/byte (Idealwert: 8.0)\n", ent)
	if ent < 7.5 {
		return fmt.Errorf("Entropie %.4f bit/byte zu niedrig — RNG-Output sieht nicht uniform aus", ent)
	}
	fmt.Println("\nFertig — der HSM-RNG funktioniert wie erwartet.")
	return nil
}

// timeGenerate ruft `gen(chunk)` solange auf, bis `total` Bytes gesammelt sind,
// und liefert (bytes, wall-clock-dauer).
func timeGenerate(total, chunk int, gen func(int) ([]byte, error)) ([]byte, time.Duration, error) {
	out := make([]byte, 0, total)
	start := time.Now()
	for len(out) < total {
		need := chunk
		if total-len(out) < need {
			need = total - len(out)
		}
		buf, err := gen(need)
		if err != nil {
			return nil, 0, err
		}
		out = append(out, buf...)
	}
	return out, time.Since(start), nil
}

func report(label string, bytes int, dur time.Duration) {
	mbps := (float64(bytes) / 1024.0 / 1024.0) / dur.Seconds()
	fmt.Printf("  %-50s %7.3fs  %8.2f MB/s\n", label, dur.Seconds(), mbps)
}

func shannonEntropy(data []byte) float64 {
	if len(data) == 0 {
		return 0
	}
	var counts [256]int
	for _, b := range data {
		counts[b]++
	}
	n := float64(len(data))
	var h float64
	for _, c := range counts {
		if c == 0 {
			continue
		}
		p := float64(c) / n
		h -= p * math.Log2(p)
	}
	return h
}

func findSlot(p11 *pkcs11.Ctx, tokenLabel string) (uint, error) {
	slots, err := p11.GetSlotList(true)
	if err != nil {
		return 0, fmt.Errorf("C_GetSlotList: %w", err)
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
