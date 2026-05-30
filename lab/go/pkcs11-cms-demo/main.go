package main

import (
	"crypto"
	"crypto/rsa"
	"crypto/x509"
	"encoding/pem"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"

	"github.com/digitorus/pkcs7"
	"github.com/miekg/pkcs11"
)

// CMS/PKCS#7 — detached SignedData mit HSM-Key.
// Schluesselthema: digitorus/pkcs7 will einen crypto.PrivateKey, der das
// crypto.Signer-Interface erfuellt. Wir bauen einen Adapter, der Sign() an
// miekg/pkcs11 weiterreicht — der private Key verlaesst den HSM nie, nur das
// (bereits gehashte) signed-attributes-Digest wandert ueber die Schnittstelle.

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
	certPath := env("PKCS11_CERT_PATH", filepath.Join(outputDir, "cert.pem"))
	keyID := []byte{0x01}
	content := []byte("Vertrag XYZ vom 30.05.2026\nUnterzeichnender: Go-CMS-Demo\n")

	cert, err := loadCert(certPath)
	if err != nil {
		return fmt.Errorf("cert laden (%s): %w", certPath, err)
	}
	pubKey, ok := cert.PublicKey.(*rsa.PublicKey)
	if !ok {
		return fmt.Errorf("cert hat keinen RSA-Pubkey")
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

	priv, err := findPrivateKey(p11, session, keyID)
	if err != nil {
		return fmt.Errorf("Signing-Key nicht gefunden (CKA_ID=01): %w", err)
	}

	signer := &pkcs11RSASigner{p11: p11, session: session, key: priv, pub: pubKey}

	sd, err := pkcs7.NewSignedData(content)
	if err != nil {
		return fmt.Errorf("NewSignedData: %w", err)
	}
	sd.SetDigestAlgorithm(pkcs7.OIDDigestAlgorithmSHA256)
	if err := sd.AddSigner(cert, signer, pkcs7.SignerInfoConfig{}); err != nil {
		return fmt.Errorf("AddSigner: %w", err)
	}
	sd.Detach() // detached: kein Klartext in den SignedData-EncapContentInfo

	der, err := sd.Finish()
	if err != nil {
		return fmt.Errorf("Finish: %w", err)
	}

	if err := os.MkdirAll(outputDir, 0o755); err != nil {
		return err
	}
	contentPath := filepath.Join(outputDir, "go-cms-document.txt")
	sigPath := filepath.Join(outputDir, "go-cms-document.p7s")
	if err := os.WriteFile(contentPath, content, 0o644); err != nil {
		return err
	}
	if err := os.WriteFile(sigPath, der, 0o644); err != nil {
		return err
	}

	// Sofort gegenpruefen — Verifier nutzt pubkey aus dem Cert in der Signatur.
	verified, err := pkcs7.Parse(der)
	if err != nil {
		return fmt.Errorf("Re-Parse: %w", err)
	}
	verified.Content = content // detached: Content extern liefern
	if err := verified.Verify(); err != nil {
		return fmt.Errorf("Self-Verify: %w", err)
	}

	fmt.Printf("Token:          %s\n", tokenLabel)
	fmt.Printf("Signer-Cert:    %s (Subject: %s)\n", certPath, cert.Subject)
	fmt.Printf("Klartext:       %s (%d Bytes)\n", contentPath, len(content))
	fmt.Printf("CMS-Signatur:   %s (%d Bytes, detached)\n", sigPath, len(der))
	fmt.Println("Self-Verify:    OK")
	return nil
}

// pkcs11RSASigner adaptert eine PKCS#11-Private-Key-Handle an Gos crypto.Signer.
// crypto.Signer.Sign bekommt den bereits berechneten Hash und einen Hash-Hinweis;
// fuer CKM_RSA_PKCS muessen wir den Hash in ein DigestInfo wrappen, dann signieren.
type pkcs11RSASigner struct {
	p11     *pkcs11.Ctx
	session pkcs11.SessionHandle
	key     pkcs11.ObjectHandle
	pub     crypto.PublicKey
}

func (s *pkcs11RSASigner) Public() crypto.PublicKey { return s.pub }

func (s *pkcs11RSASigner) Sign(_ io.Reader, digest []byte, opts crypto.SignerOpts) ([]byte, error) {
	prefix, ok := digestInfoPrefix[opts.HashFunc()]
	if !ok {
		return nil, fmt.Errorf("unsupported hash: %v", opts.HashFunc())
	}
	encoded := make([]byte, 0, len(prefix)+len(digest))
	encoded = append(encoded, prefix...)
	encoded = append(encoded, digest...)

	mech := []*pkcs11.Mechanism{pkcs11.NewMechanism(pkcs11.CKM_RSA_PKCS, nil)}
	if err := s.p11.SignInit(s.session, mech, s.key); err != nil {
		return nil, fmt.Errorf("C_SignInit: %w", err)
	}
	sig, err := s.p11.Sign(s.session, encoded)
	if err != nil {
		return nil, fmt.Errorf("C_Sign: %w", err)
	}
	return sig, nil
}

// PKCS#1 v1.5 DigestInfo-Prefixe pro Hash-Funktion (RFC 8017 §9.2 Anhang).
// SHA-1 ausgelassen — wir wollen es nicht versehentlich anbieten.
var digestInfoPrefix = map[crypto.Hash][]byte{
	crypto.SHA256: {0x30, 0x31, 0x30, 0x0d, 0x06, 0x09, 0x60, 0x86, 0x48, 0x01, 0x65, 0x03, 0x04, 0x02, 0x01, 0x05, 0x00, 0x04, 0x20},
	crypto.SHA384: {0x30, 0x41, 0x30, 0x0d, 0x06, 0x09, 0x60, 0x86, 0x48, 0x01, 0x65, 0x03, 0x04, 0x02, 0x02, 0x05, 0x00, 0x04, 0x30},
	crypto.SHA512: {0x30, 0x51, 0x30, 0x0d, 0x06, 0x09, 0x60, 0x86, 0x48, 0x01, 0x65, 0x03, 0x04, 0x02, 0x03, 0x05, 0x00, 0x04, 0x40},
}

func loadCert(path string) (*x509.Certificate, error) {
	raw, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	block, _ := pem.Decode(raw)
	if block == nil {
		return nil, fmt.Errorf("kein PEM-Block in %s", path)
	}
	return x509.ParseCertificate(block.Bytes)
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
		return 0, fmt.Errorf("erwartet genau einen Private Key mit CKA_ID=%x, gefunden: %d", keyID, len(objects))
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
