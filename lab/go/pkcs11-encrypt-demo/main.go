package main

import (
	"bytes"
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
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
	wrapKeyID := []byte{0x03}
	plaintext := []byte("Vertrauliches Dokument aus Go.\nZeile zwei.\n")

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

	pubKey, err := findKey(p11, session, pkcs11.CKO_PUBLIC_KEY, wrapKeyID)
	if err != nil {
		return fmt.Errorf("Wrap-Pubkey nicht gefunden (CKA_ID=03 — make gen-rsa-wrap ausfuehren?): %w", err)
	}
	privKey, err := findKey(p11, session, pkcs11.CKO_PRIVATE_KEY, wrapKeyID)
	if err != nil {
		return fmt.Errorf("Wrap-Privkey nicht gefunden: %w", err)
	}

	// Host-seitige Session-Materialien — leben nie im HSM.
	aesKey := make([]byte, 32)
	if _, err := rand.Read(aesKey); err != nil {
		return err
	}
	iv := make([]byte, 12)
	if _, err := rand.Read(iv); err != nil {
		return err
	}

	wrapped, err := rsaOAEPEncrypt(p11, session, pubKey, aesKey)
	if err != nil {
		return fmt.Errorf("RSA-OAEP-Wrap: %w", err)
	}
	ciphertext, err := aesGCMEncrypt(aesKey, iv, plaintext)
	if err != nil {
		return fmt.Errorf("AES-GCM-Encrypt: %w", err)
	}

	// Plain-AES-Key in dieser Routine bewusst nullen — die einzige verbleibende
	// Kopie ist die gewrappte Variante, die nur der private RSA-Key im HSM oeffnet.
	for i := range aesKey {
		aesKey[i] = 0
	}

	// --- Empfaengerseite: AES-Key per HSM-RSA-OAEP zurueckgewinnen ---
	recoveredKey, err := rsaOAEPDecrypt(p11, session, privKey, wrapped)
	if err != nil {
		return fmt.Errorf("RSA-OAEP-Unwrap: %w", err)
	}
	recovered, err := aesGCMDecrypt(recoveredKey, iv, ciphertext)
	if err != nil {
		return fmt.Errorf("AES-GCM-Decrypt: %w", err)
	}
	for i := range recoveredKey {
		recoveredKey[i] = 0
	}

	if !bytes.Equal(plaintext, recovered) {
		return fmt.Errorf("Round-Trip fehlgeschlagen: Klartext stimmt nicht ueberein")
	}

	if err := os.MkdirAll(outputDir, 0o755); err != nil {
		return err
	}
	for name, data := range map[string][]byte{
		"go-document.txt":    plaintext,
		"go-wrapped-key.bin": wrapped,
		"go-iv.bin":          iv,
		"go-document.enc":    ciphertext,
	} {
		if err := os.WriteFile(filepath.Join(outputDir, name), data, 0o644); err != nil {
			return err
		}
	}

	fmt.Printf("Token:        %s\n", tokenLabel)
	fmt.Printf("Wrap-Key-ID:  %x\n", wrapKeyID)
	fmt.Printf("Wrapped Key:  %s (%d Bytes)\n", filepath.Join(outputDir, "go-wrapped-key.bin"), len(wrapped))
	fmt.Printf("Ciphertext:   %s (%d Bytes inkl. GCM-Tag)\n", filepath.Join(outputDir, "go-document.enc"), len(ciphertext))
	fmt.Println("Round-Trip:   OK")
	return nil
}

// OAEP-Hash: SHA-1 statt des heute ueblichen SHA-256.
// Grund: SoftHSM 2.6.x liefert beim direkten Aufruf von C_EncryptInit/C_DecryptInit
// mit CKM_RSA_PKCS_OAEP und hashAlg=CKM_SHA256 ein CKR_ARGUMENTS_BAD zurueck.
// SHA-1 OAEP funktioniert, ist aber NICHT die Empfehlung fuer Produktion —
// real-world HSMs (Thales/Utimaco/AWS CloudHSM) akzeptieren SHA-256 OAEP problemlos.
// Die Bash-Demo nutzt openssl + pkcs11-Engine und kommt damit ueber den
// CKM_RSA_X_509-Workaround der Engine an SHA-256 ran. Siehe course/13-verschluesselung.md.
const oaepHash = pkcs11.CKM_SHA_1
const oaepMGF = pkcs11.CKG_MGF1_SHA1

func rsaOAEPEncrypt(p11 *pkcs11.Ctx, session pkcs11.SessionHandle, key pkcs11.ObjectHandle, plaintext []byte) ([]byte, error) {
	params := pkcs11.NewOAEPParams(oaepHash, oaepMGF, pkcs11.CKZ_DATA_SPECIFIED, nil)
	mech := []*pkcs11.Mechanism{pkcs11.NewMechanism(pkcs11.CKM_RSA_PKCS_OAEP, params)}
	if err := p11.EncryptInit(session, mech, key); err != nil {
		return nil, fmt.Errorf("C_EncryptInit: %w", err)
	}
	ct, err := p11.Encrypt(session, plaintext)
	if err != nil {
		return nil, fmt.Errorf("C_Encrypt: %w", err)
	}
	return ct, nil
}

func rsaOAEPDecrypt(p11 *pkcs11.Ctx, session pkcs11.SessionHandle, key pkcs11.ObjectHandle, ciphertext []byte) ([]byte, error) {
	params := pkcs11.NewOAEPParams(oaepHash, oaepMGF, pkcs11.CKZ_DATA_SPECIFIED, nil)
	mech := []*pkcs11.Mechanism{pkcs11.NewMechanism(pkcs11.CKM_RSA_PKCS_OAEP, params)}
	if err := p11.DecryptInit(session, mech, key); err != nil {
		return nil, fmt.Errorf("C_DecryptInit: %w", err)
	}
	pt, err := p11.Decrypt(session, ciphertext)
	if err != nil {
		return nil, fmt.Errorf("C_Decrypt: %w", err)
	}
	return pt, nil
}

func aesGCMEncrypt(key, iv, plaintext []byte) ([]byte, error) {
	block, err := aes.NewCipher(key)
	if err != nil {
		return nil, err
	}
	aead, err := cipher.NewGCM(block)
	if err != nil {
		return nil, err
	}
	return aead.Seal(nil, iv, plaintext, nil), nil
}

func aesGCMDecrypt(key, iv, ciphertext []byte) ([]byte, error) {
	block, err := aes.NewCipher(key)
	if err != nil {
		return nil, err
	}
	aead, err := cipher.NewGCM(block)
	if err != nil {
		return nil, err
	}
	return aead.Open(nil, iv, ciphertext, nil)
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

func findKey(p11 *pkcs11.Ctx, session pkcs11.SessionHandle, class uint, keyID []byte) (pkcs11.ObjectHandle, error) {
	template := []*pkcs11.Attribute{
		pkcs11.NewAttribute(pkcs11.CKA_CLASS, class),
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
