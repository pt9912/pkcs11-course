package main

import (
	"crypto"
	"crypto/rand"
	"crypto/rsa"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/pem"
	"fmt"
	"io"
	"math/big"
	"os"
	"path/filepath"
	"strings"

	"github.com/miekg/pkcs11"
)

// CSR-Generierung mit HSM-Signing-Key:
// Wir bauen einen crypto.Signer-Adapter (wie im CMS-Demo) und reichen ihn
// an crypto/x509.CreateCertificateRequest. Go signiert die DER-Bytes der
// CSR-TBS-Struktur durch unseren Signer; der Signer routet das in einen
// C_Sign(CKM_SHA256_RSA_PKCS)-Aufruf am HSM.
//
// Output: lab/work/go-app.csr — kann mit `openssl req -text -verify` oder
// von der Bash-CA (make issue-leaf-cert) gegengezeichnet werden.

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

	privHandle, err := findKey(p11, session, pkcs11.CKO_PRIVATE_KEY, keyID)
	if err != nil {
		return fmt.Errorf("Signing-Privkey nicht gefunden: %w", err)
	}
	pubKey, err := loadRsaPublicKey(p11, session, keyID)
	if err != nil {
		return fmt.Errorf("Signing-Pubkey: %w", err)
	}

	signer := &pkcs11RSASigner{p11: p11, session: session, key: privHandle, pub: pubKey}

	template := &x509.CertificateRequest{
		Subject: pkix.Name{
			CommonName:   "go-app.example.org",
			Organization: []string{"PKCS11 Lab"},
		},
		DNSNames:           []string{"go-app.example.org"},
		SignatureAlgorithm: x509.SHA256WithRSA,
	}
	csrDer, err := x509.CreateCertificateRequest(rand.Reader, template, signer)
	if err != nil {
		return fmt.Errorf("CreateCertificateRequest: %w", err)
	}

	// Self-verify: parsen, Signatur pruefen.
	parsed, err := x509.ParseCertificateRequest(csrDer)
	if err != nil {
		return fmt.Errorf("ParseCertificateRequest: %w", err)
	}
	if err := parsed.CheckSignature(); err != nil {
		return fmt.Errorf("CSR-Selbstsignatur ungueltig: %w", err)
	}

	if err := os.MkdirAll(outputDir, 0o755); err != nil {
		return err
	}
	csrPath := filepath.Join(outputDir, "go-app.csr")
	pemBytes := pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE REQUEST", Bytes: csrDer})
	if err := os.WriteFile(csrPath, pemBytes, 0o644); err != nil {
		return err
	}

	fmt.Println("--- CSR-Generierung ---")
	fmt.Printf("Subject:      %s\n", parsed.Subject)
	fmt.Printf("DNS-SAN:      %v\n", parsed.DNSNames)
	fmt.Printf("SignAlgo:     %s\n", parsed.SignatureAlgorithm)
	fmt.Printf("CSR:          %s (%d Bytes PEM)\n", csrPath, len(pemBytes))
	fmt.Println("Selbstsignatur (PoP): OK")
	return nil
}

// pkcs11RSASigner — siehe pkcs11-cms-demo fuer Details. crypto.Signer-Adapter,
// der DigestInfo wrappt und CKM_RSA_PKCS auf dem HSM aufruft.
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
	encoded := append(append([]byte{}, prefix...), digest...)
	mech := []*pkcs11.Mechanism{pkcs11.NewMechanism(pkcs11.CKM_RSA_PKCS, nil)}
	if err := s.p11.SignInit(s.session, mech, s.key); err != nil {
		return nil, fmt.Errorf("C_SignInit: %w", err)
	}
	return s.p11.Sign(s.session, encoded)
}

var digestInfoPrefix = map[crypto.Hash][]byte{
	crypto.SHA256: {0x30, 0x31, 0x30, 0x0d, 0x06, 0x09, 0x60, 0x86, 0x48, 0x01, 0x65, 0x03, 0x04, 0x02, 0x01, 0x05, 0x00, 0x04, 0x20},
	crypto.SHA384: {0x30, 0x41, 0x30, 0x0d, 0x06, 0x09, 0x60, 0x86, 0x48, 0x01, 0x65, 0x03, 0x04, 0x02, 0x02, 0x05, 0x00, 0x04, 0x30},
	crypto.SHA512: {0x30, 0x51, 0x30, 0x0d, 0x06, 0x09, 0x60, 0x86, 0x48, 0x01, 0x65, 0x03, 0x04, 0x02, 0x03, 0x05, 0x00, 0x04, 0x40},
}

// loadRsaPublicKey liest CKA_MODULUS und CKA_PUBLIC_EXPONENT vom Public Key
// im Token und baut einen *rsa.PublicKey. Brauchen wir, damit
// CreateCertificateRequest den Pubkey in die CSR einbetten kann.
func loadRsaPublicKey(p11 *pkcs11.Ctx, session pkcs11.SessionHandle, keyID []byte) (*rsa.PublicKey, error) {
	pubHandle, err := findKey(p11, session, pkcs11.CKO_PUBLIC_KEY, keyID)
	if err != nil {
		return nil, err
	}
	attrs, err := p11.GetAttributeValue(session, pubHandle, []*pkcs11.Attribute{
		pkcs11.NewAttribute(pkcs11.CKA_MODULUS, nil),
		pkcs11.NewAttribute(pkcs11.CKA_PUBLIC_EXPONENT, nil),
	})
	if err != nil {
		return nil, fmt.Errorf("C_GetAttributeValue: %w", err)
	}
	pub := &rsa.PublicKey{}
	for _, a := range attrs {
		switch a.Type {
		case pkcs11.CKA_MODULUS:
			pub.N = new(intFromBytes).set(a.Value).int
		case pkcs11.CKA_PUBLIC_EXPONENT:
			e := 0
			for _, b := range a.Value {
				e = e<<8 | int(b)
			}
			pub.E = e
		}
	}
	if pub.N == nil || pub.E == 0 {
		return nil, fmt.Errorf("RSA-Pubkey-Felder unvollstaendig")
	}
	return pub, nil
}

// intFromBytes ist ein winziger Helper um aus dem big-endian Modulus-Bytes
// ein *big.Int zu bauen, ohne crypto/rand.big_ usw. zu re-importieren.
type intFromBytes struct {
	int *big.Int
}

func (i *intFromBytes) set(b []byte) *intFromBytes {
	i.int = new(big.Int).SetBytes(b)
	return i
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
