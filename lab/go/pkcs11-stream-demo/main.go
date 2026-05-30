package main

import (
	"crypto/rand"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"

	"github.com/miekg/pkcs11"
)

// PKCS#11 Multi-Part / Streaming:
// Sign:    C_SignInit  → loop C_SignUpdate(chunk) → C_SignFinal()
// Encrypt: C_EncryptInit → loop C_EncryptUpdate(chunk) → C_EncryptFinal()
// Decrypt analog.
//
// Vorteil: konstanter Speicherbedarf unabhaengig von der Filegroesse.
// Der Token haelt entweder den Hash-State (Sign mit CKM_SHA256_RSA_PKCS)
// oder den Cipher-State + IV (CKM_AES_CBC_PAD).

const chunkSize = 64 * 1024 // 64 KiB — typischer Default fuer disk I/O

func main() {
	if err := run(); err != nil {
		fmt.Fprintf(os.Stderr, "Fehler: %v\n", err)
		os.Exit(1)
	}
}

func run() error {
	module := env("PKCS11_MODULE", "/usr/lib/softhsm/libsofthsm2.so")
	tokenLabel := env("PKCS11_TOKEL_LABEL", "dev-token")
	if v := os.Getenv("PKCS11_TOKEN_LABEL"); v != "" {
		tokenLabel = v
	}
	pin := env("PKCS11_USER_PIN", "987654")
	outputDir := env("PKCS11_OUTPUT_DIR", "/workspace/lab/work")
	signKeyID := []byte{0x01}
	aesKeyID := []byte{0x04}
	inputPath := filepath.Join(outputDir, "large.bin")

	if _, err := os.Stat(inputPath); err != nil {
		return fmt.Errorf("Testfile fehlt: %s (erst 'make stream-sign' ausfuehren oder dd if=/dev/zero of=lab/work/large.bin bs=1M count=100)", inputPath)
	}

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

	signKey, err := findKey(p11, session, pkcs11.CKO_PRIVATE_KEY, signKeyID)
	if err != nil {
		return fmt.Errorf("Signing-Key nicht gefunden: %w", err)
	}
	aesKey, err := findKey(p11, session, pkcs11.CKO_SECRET_KEY, aesKeyID)
	if err != nil {
		return fmt.Errorf("AES-Stream-Key nicht gefunden (CKA_ID=04 — make gen-aes-stream?): %w", err)
	}

	// --- Sign-Streaming ---
	sigPath := filepath.Join(outputDir, "go-stream.sig")
	signed, err := streamSign(p11, session, signKey, inputPath, sigPath)
	if err != nil {
		return fmt.Errorf("Sign-Stream: %w", err)
	}
	fmt.Printf("Sign:    %s → %s (%d Bytes Signatur ueber %d Bytes Input)\n",
		filepath.Base(inputPath), filepath.Base(sigPath), len(signed), fileSize(inputPath))

	// --- Encrypt-Streaming ---
	encPath := filepath.Join(outputDir, "go-stream.enc")
	decPath := filepath.Join(outputDir, "go-stream.dec")
	iv := make([]byte, 16)
	if _, err := rand.Read(iv); err != nil {
		return err
	}
	if err := streamEncrypt(p11, session, aesKey, iv, inputPath, encPath); err != nil {
		return fmt.Errorf("Encrypt-Stream: %w", err)
	}
	fmt.Printf("Encrypt: %s → %s (%d Bytes inkl. PKCS#7-Padding)\n",
		filepath.Base(inputPath), filepath.Base(encPath), fileSize(encPath))

	if err := streamDecrypt(p11, session, aesKey, iv, encPath, decPath); err != nil {
		return fmt.Errorf("Decrypt-Stream: %w", err)
	}
	fmt.Printf("Decrypt: %s → %s (%d Bytes)\n", filepath.Base(encPath), filepath.Base(decPath), fileSize(decPath))

	if err := compareFiles(inputPath, decPath); err != nil {
		return fmt.Errorf("Round-Trip-Check: %w", err)
	}
	fmt.Println("Round-Trip: OK")
	return nil
}

func streamSign(p11 *pkcs11.Ctx, session pkcs11.SessionHandle, key pkcs11.ObjectHandle, inPath, outPath string) ([]byte, error) {
	mech := []*pkcs11.Mechanism{pkcs11.NewMechanism(pkcs11.CKM_SHA256_RSA_PKCS, nil)}
	if err := p11.SignInit(session, mech, key); err != nil {
		return nil, fmt.Errorf("C_SignInit: %w", err)
	}
	in, err := os.Open(inPath)
	if err != nil {
		return nil, err
	}
	defer in.Close()
	buf := make([]byte, chunkSize)
	for {
		n, rerr := in.Read(buf)
		if n > 0 {
			if err := p11.SignUpdate(session, buf[:n]); err != nil {
				return nil, fmt.Errorf("C_SignUpdate: %w", err)
			}
		}
		if rerr == io.EOF {
			break
		}
		if rerr != nil {
			return nil, rerr
		}
	}
	sig, err := p11.SignFinal(session)
	if err != nil {
		return nil, fmt.Errorf("C_SignFinal: %w", err)
	}
	return sig, os.WriteFile(outPath, sig, 0o644)
}

func streamEncrypt(p11 *pkcs11.Ctx, session pkcs11.SessionHandle, key pkcs11.ObjectHandle, iv []byte, inPath, outPath string) error {
	mech := []*pkcs11.Mechanism{pkcs11.NewMechanism(pkcs11.CKM_AES_CBC_PAD, iv)}
	if err := p11.EncryptInit(session, mech, key); err != nil {
		return fmt.Errorf("C_EncryptInit: %w", err)
	}
	return streamUpdateFinal(p11, session, inPath, outPath, p11.EncryptUpdate, p11.EncryptFinal)
}

func streamDecrypt(p11 *pkcs11.Ctx, session pkcs11.SessionHandle, key pkcs11.ObjectHandle, iv []byte, inPath, outPath string) error {
	mech := []*pkcs11.Mechanism{pkcs11.NewMechanism(pkcs11.CKM_AES_CBC_PAD, iv)}
	if err := p11.DecryptInit(session, mech, key); err != nil {
		return fmt.Errorf("C_DecryptInit: %w", err)
	}
	return streamUpdateFinal(p11, session, inPath, outPath, p11.DecryptUpdate, p11.DecryptFinal)
}

// streamUpdateFinal abstrahiert Encrypt/Decrypt: beide haben identische
// Update/Final-Signaturen.
func streamUpdateFinal(
	p11 *pkcs11.Ctx,
	session pkcs11.SessionHandle,
	inPath, outPath string,
	update func(pkcs11.SessionHandle, []byte) ([]byte, error),
	final func(pkcs11.SessionHandle) ([]byte, error),
) error {
	in, err := os.Open(inPath)
	if err != nil {
		return err
	}
	defer in.Close()
	out, err := os.Create(outPath)
	if err != nil {
		return err
	}
	defer out.Close()

	buf := make([]byte, chunkSize)
	for {
		n, rerr := in.Read(buf)
		if n > 0 {
			chunk, err := update(session, buf[:n])
			if err != nil {
				return fmt.Errorf("C_*Update: %w", err)
			}
			if _, err := out.Write(chunk); err != nil {
				return err
			}
		}
		if rerr == io.EOF {
			break
		}
		if rerr != nil {
			return rerr
		}
	}
	last, err := final(session)
	if err != nil {
		return fmt.Errorf("C_*Final: %w", err)
	}
	if _, err := out.Write(last); err != nil {
		return err
	}
	return nil
}

func compareFiles(a, b string) error {
	fa, err := os.Open(a)
	if err != nil {
		return err
	}
	defer fa.Close()
	fb, err := os.Open(b)
	if err != nil {
		return err
	}
	defer fb.Close()
	ba := make([]byte, chunkSize)
	bb := make([]byte, chunkSize)
	for {
		na, errA := io.ReadFull(fa, ba)
		nb, errB := io.ReadFull(fb, bb)
		if na != nb {
			return fmt.Errorf("Groessen weichen ab")
		}
		for i := 0; i < na; i++ {
			if ba[i] != bb[i] {
				return fmt.Errorf("Byte %d weicht ab", i)
			}
		}
		if errA == io.EOF || errA == io.ErrUnexpectedEOF {
			if errB != errA {
				return fmt.Errorf("EOF asymmetrisch")
			}
			return nil
		}
		if errA != nil {
			return errA
		}
	}
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

func fileSize(path string) int64 {
	if info, err := os.Stat(path); err == nil {
		return info.Size()
	}
	return -1
}

func logIfError(op string, err error) {
	if err != nil {
		fmt.Fprintf(os.Stderr, "Warnung: %s: %v\n", op, err)
	}
}
