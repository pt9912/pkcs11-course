package main

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/miekg/pkcs11"
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
	keyID := []byte{0x01}
	data := []byte("hello from go pkcs11")

	p11 := pkcs11.New(module)
	if p11 == nil {
		return fmt.Errorf("PKCS#11-Modul kann nicht geladen werden: %s", module)
	}
	defer p11.Destroy()

	if err := p11.Initialize(); err != nil {
		return fmt.Errorf("C_Initialize: %w", err)
	}
	defer p11.Finalize()

	slot, err := findSlot(p11, tokenLabel)
	if err != nil {
		return err
	}

	session, err := p11.OpenSession(slot, pkcs11.CKF_SERIAL_SESSION|pkcs11.CKF_RW_SESSION)
	if err != nil {
		return fmt.Errorf("C_OpenSession: %w", err)
	}
	defer p11.CloseSession(session)

	if err := p11.Login(session, pkcs11.CKU_USER, pin); err != nil {
		return fmt.Errorf("C_Login: %w", err)
	}
	defer p11.Logout(session)

	key, err := findPrivateKey(p11, session, keyID)
	if err != nil {
		return err
	}

	mech := []*pkcs11.Mechanism{pkcs11.NewMechanism(pkcs11.CKM_SHA256_RSA_PKCS, nil)}
	if err := p11.SignInit(session, mech, key); err != nil {
		return fmt.Errorf("C_SignInit: %w", err)
	}
	signature, err := p11.Sign(session, data)
	if err != nil {
		return fmt.Errorf("C_Sign: %w", err)
	}

	if err := os.MkdirAll(outputDir, 0o755); err != nil {
		return err
	}
	dataPath := filepath.Join(outputDir, "go.txt")
	sigPath := filepath.Join(outputDir, "go.sig")
	if err := os.WriteFile(dataPath, data, 0o644); err != nil {
		return err
	}
	if err := os.WriteFile(sigPath, signature, 0o644); err != nil {
		return err
	}

	fmt.Printf("Token: %s\n", tokenLabel)
	fmt.Printf("Signatur: %s (%d Bytes)\n", sigPath, len(signature))
	fmt.Printf("Daten: %s\n", dataPath)
	return nil
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

func findPrivateKey(p11 *pkcs11.Ctx, session pkcs11.SessionHandle, keyID []byte) (pkcs11.ObjectHandle, error) {
	template := []*pkcs11.Attribute{
		pkcs11.NewAttribute(pkcs11.CKA_CLASS, pkcs11.CKO_PRIVATE_KEY),
		pkcs11.NewAttribute(pkcs11.CKA_ID, keyID),
	}
	if err := p11.FindObjectsInit(session, template); err != nil {
		return 0, fmt.Errorf("C_FindObjectsInit: %w", err)
	}
	defer p11.FindObjectsFinal(session)

	objects, _, err := p11.FindObjects(session, 2)
	if err != nil {
		return 0, fmt.Errorf("C_FindObjects: %w", err)
	}
	if len(objects) != 1 {
		return 0, fmt.Errorf("erwartet genau einen Private Key mit CKA_ID=01, gefunden: %d", len(objects))
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
