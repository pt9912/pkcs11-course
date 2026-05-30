package main

import (
	"bytes"
	"crypto/rand"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/miekg/pkcs11"
)

// Backup/Restore eines symmetrischen Keys ueber C_WrapKey / C_UnwrapKey:
//  1) Frischen AES-256 Session-Key generieren (extractable).
//  2) Test-Daten damit verschluesseln.
//  3) WrapKey via KEK (CKM_AES_KEY_WRAP_PAD) → opaque Blob auf Disk.
//  4) Session-Key zerstoeren — Disaster simulieren.
//  5) UnwrapKey: Blob + KEK → neuer Session-Key-Handle, gleicher Inhalt.
//  6) Decrypt der Daten mit dem WIEDERHERGESTELLTEN Key. Round-Trip-Check.
//
// Im Gegensatz zu pkcs11-tool koennen wir das Unwrap-Template praezise
// kontrollieren — kein CKA_VALUE_LEN, keine Konflikte mit SoftHSM.

const (
	kekKeyID      = 0x06
	testIVSize    = 16
	testKeySize   = 32 // AES-256
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
	outputDir := env("PKCS11_OUTPUT_DIR", "/workspace/lab/work")

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
	session, err := p11.OpenSession(slot, pkcs11.CKF_SERIAL_SESSION|pkcs11.CKF_RW_SESSION)
	if err != nil {
		return fmt.Errorf("C_OpenSession: %w", err)
	}
	defer func() { logIfError("C_CloseSession", p11.CloseSession(session)) }()
	if err := p11.Login(session, pkcs11.CKU_USER, pin); err != nil {
		return fmt.Errorf("C_Login: %w", err)
	}
	defer func() { logIfError("C_Logout", p11.Logout(session)) }()

	kek, err := findKey(p11, session, pkcs11.CKO_SECRET_KEY, []byte{kekKeyID})
	if err != nil {
		return fmt.Errorf("KEK nicht gefunden (CKA_ID=06 — make gen-kek?): %w", err)
	}

	// 1) Fresh AES-256 als Session-Object (CKA_TOKEN=false), extractable.
	dataKey, err := generateExtractableAES(p11, session, testKeySize)
	if err != nil {
		return fmt.Errorf("AES-Generate: %w", err)
	}

	// 2) Test-Payload verschluesseln.
	payload := []byte("Vertrauliches Material aus Go.\nMuss nach dem Restore lesbar bleiben.\n")
	iv := make([]byte, testIVSize)
	if _, err := rand.Read(iv); err != nil {
		return err
	}
	ciphertext, err := aesCBCPad(p11, session, dataKey, iv, payload, true)
	if err != nil {
		return fmt.Errorf("Encrypt Original: %w", err)
	}

	// 3) Wrap.
	wrapMech := []*pkcs11.Mechanism{pkcs11.NewMechanism(pkcs11.CKM_AES_KEY_WRAP_PAD, nil)}
	wrapped, err := p11.WrapKey(session, wrapMech, kek, dataKey)
	if err != nil {
		return fmt.Errorf("C_WrapKey: %w", err)
	}

	if err := os.MkdirAll(outputDir, 0o755); err != nil {
		return err
	}
	wrapPath := filepath.Join(outputDir, "go-wrap-backup.bin")
	if err := os.WriteFile(wrapPath, wrapped, 0o644); err != nil {
		return err
	}

	// 4) Original zerstoeren.
	if err := p11.DestroyObject(session, dataKey); err != nil {
		return fmt.Errorf("C_DestroyObject: %w", err)
	}

	// 5) Unwrap — bewusst MINIMALES Template, kein CKA_VALUE_LEN.
	// Klasse + KeyType + Encrypt/Decrypt-Flags reichen; Laenge folgt aus dem Blob.
	unwrapTpl := []*pkcs11.Attribute{
		pkcs11.NewAttribute(pkcs11.CKA_CLASS, pkcs11.CKO_SECRET_KEY),
		pkcs11.NewAttribute(pkcs11.CKA_KEY_TYPE, pkcs11.CKK_AES),
		pkcs11.NewAttribute(pkcs11.CKA_TOKEN, false),
		pkcs11.NewAttribute(pkcs11.CKA_ENCRYPT, true),
		pkcs11.NewAttribute(pkcs11.CKA_DECRYPT, true),
		pkcs11.NewAttribute(pkcs11.CKA_LABEL, "go-restored-payload"),
	}
	restored, err := p11.UnwrapKey(session, wrapMech, kek, wrapped, unwrapTpl)
	if err != nil {
		return fmt.Errorf("C_UnwrapKey: %w", err)
	}

	// 6) Mit dem RESTORED Key entschluesseln.
	recovered, err := aesCBCPad(p11, session, restored, iv, ciphertext, false)
	if err != nil {
		return fmt.Errorf("Decrypt mit Restored: %w", err)
	}
	if !bytes.Equal(payload, recovered) {
		return fmt.Errorf("Round-Trip-Mismatch: erwartet %q, bekommen %q", payload, recovered)
	}

	fmt.Println("--- Wrap/Unwrap Round-Trip ---")
	fmt.Printf("Original payload-key:    handle %d (geloescht nach Wrap)\n", dataKey)
	fmt.Printf("Restored payload-key:    handle %d (neue Identitaet, gleiches Material)\n", restored)
	fmt.Printf("Wrap-Blob:               %s (%d Bytes)\n", wrapPath, len(wrapped))
	fmt.Printf("Original-Klartext:       %d Bytes\n", len(payload))
	fmt.Printf("Nach Decrypt mit Restore: %d Bytes — match.\n", len(recovered))
	fmt.Println("Round-Trip: OK")
	return nil
}

func generateExtractableAES(p11 *pkcs11.Ctx, session pkcs11.SessionHandle, size int) (pkcs11.ObjectHandle, error) {
	mech := []*pkcs11.Mechanism{pkcs11.NewMechanism(pkcs11.CKM_AES_KEY_GEN, nil)}
	tpl := []*pkcs11.Attribute{
		pkcs11.NewAttribute(pkcs11.CKA_CLASS, pkcs11.CKO_SECRET_KEY),
		pkcs11.NewAttribute(pkcs11.CKA_KEY_TYPE, pkcs11.CKK_AES),
		pkcs11.NewAttribute(pkcs11.CKA_VALUE_LEN, size),
		pkcs11.NewAttribute(pkcs11.CKA_TOKEN, false),
		pkcs11.NewAttribute(pkcs11.CKA_SENSITIVE, true),
		pkcs11.NewAttribute(pkcs11.CKA_EXTRACTABLE, true),
		pkcs11.NewAttribute(pkcs11.CKA_ENCRYPT, true),
		pkcs11.NewAttribute(pkcs11.CKA_DECRYPT, true),
		pkcs11.NewAttribute(pkcs11.CKA_LABEL, "go-payload-key"),
	}
	return p11.GenerateKey(session, mech, tpl)
}

func aesCBCPad(p11 *pkcs11.Ctx, session pkcs11.SessionHandle, key pkcs11.ObjectHandle, iv, data []byte, encrypt bool) ([]byte, error) {
	mech := []*pkcs11.Mechanism{pkcs11.NewMechanism(pkcs11.CKM_AES_CBC_PAD, iv)}
	if encrypt {
		if err := p11.EncryptInit(session, mech, key); err != nil {
			return nil, fmt.Errorf("C_EncryptInit: %w", err)
		}
		return p11.Encrypt(session, data)
	}
	if err := p11.DecryptInit(session, mech, key); err != nil {
		return nil, fmt.Errorf("C_DecryptInit: %w", err)
	}
	return p11.Decrypt(session, data)
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
