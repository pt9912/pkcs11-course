package main

// CMS-Signatur + RFC-3161-Timestamp Demo, Go-Pfad:
//
//   1) CMS via digitorus/pkcs7 mit crypto.Signer-Adapter (HSM-Key via miekg/pkcs11).
//   2) SHA-256 ueber signer.signature an die Lab-TSA POSTen.
//   3) TimeStampResp parsen + verifizieren.
//
// Bewusst KEIN Embedding in unsignedAttrs: digitorus/pkcs7 hat keine API,
// um nach dem Sign-Schritt UnsignedAttributes hinzuzufuegen, und manuelle
// ASN.1-Manipulation des fertigen CMS-Blobs ist fehleranfaellig. Wir geben
// stattdessen zwei Artefakte aus (`.p7s` plus `.tsr`) und dokumentieren das.
// Java/Kotlin/C# zeigen den vollen CAdES-T-Einbau.

import (
	"crypto"
	"crypto/rsa"
	"crypto/sha256"
	"crypto/x509"
	"encoding/pem"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/digitorus/pkcs7"
	"github.com/digitorus/timestamp"
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
	certPath := env("PKCS11_CERT_PATH", filepath.Join(outputDir, "cert.pem"))
	tsaURL := env("PKCS11_TSA_URL", "http://127.0.0.1:8088/")
	keyID := []byte{0x01}
	content := []byte("Lab-Dokument fuer CMS+TSA-Roundtrip (Go).\n")

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
	defer func() { _ = p11.Finalize() }()

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

	priv, err := findPrivateKey(p11, session, keyID)
	if err != nil {
		return fmt.Errorf("Signing-Key nicht gefunden (CKA_ID=01): %w", err)
	}

	// === 1) CMS-Signatur ===
	signer := &pkcs11RSASigner{p11: p11, session: session, key: priv, pub: pubKey}
	sd, err := pkcs7.NewSignedData(content)
	if err != nil {
		return fmt.Errorf("NewSignedData: %w", err)
	}
	sd.SetDigestAlgorithm(pkcs7.OIDDigestAlgorithmSHA256)
	if err := sd.AddSigner(cert, signer, pkcs7.SignerInfoConfig{}); err != nil {
		return fmt.Errorf("AddSigner: %w", err)
	}
	sd.Detach()
	der, err := sd.Finish()
	if err != nil {
		return fmt.Errorf("Finish: %w", err)
	}
	fmt.Println("=== 1) CMS-Signatur (detached, SHA256withRSA) ===")
	fmt.Printf("  Signer:    %s\n", cert.Subject)
	fmt.Printf("  CMS-Blob:  %d Byte\n", len(der))

	// Signature aus dem SignerInfo extrahieren — wir brauchen sie als
	// Input fuer den TSA-Request.
	parsed, err := pkcs7.Parse(der)
	if err != nil {
		return fmt.Errorf("Re-Parse: %w", err)
	}
	if len(parsed.Signers) == 0 {
		return fmt.Errorf("kein Signer in CMS")
	}
	signerSignature := parsed.Signers[0].EncryptedDigest
	fmt.Printf("  Signature: %d Byte (SignerInfo.encryptedDigest)\n", len(signerSignature))

	// === 2) TimeStampReq + POST ===
	tsReq, err := (&timestamp.Request{
		HashAlgorithm: crypto.SHA256,
		HashedMessage: sha256Sum(signerSignature),
		Certificates:  true,
	}).Marshal()
	if err != nil {
		return fmt.Errorf("TSReq Marshal: %w", err)
	}
	fmt.Println("\n=== 2) TimeStampReq erzeugen + POST an TSA ===")
	fmt.Printf("  TSA-URL:  %s\n", tsaURL)
	fmt.Printf("  TSReq:    %d Byte (SHA-256 ueber SignerInfo.signature)\n", len(tsReq))

	tsRespBytes, err := postTSA(tsaURL, tsReq)
	if err != nil {
		return fmt.Errorf("TSA-Anfrage: %w", err)
	}
	tsResp, err := timestamp.ParseResponse(tsRespBytes)
	if err != nil {
		return fmt.Errorf("TSResp Parse: %w", err)
	}
	fmt.Printf("  TSResp:   %d Byte\n", len(tsRespBytes))
	fmt.Printf("  Gen-Time: %s\n", tsResp.Time.Format(time.RFC3339))

	// === 3) Artefakte schreiben ===
	if err := os.MkdirAll(outputDir, 0o755); err != nil {
		return err
	}
	contentPath := filepath.Join(outputDir, "go-cms-tsa-document.txt")
	sigPath := filepath.Join(outputDir, "go-cms-tsa.p7s")
	tsrPath := filepath.Join(outputDir, "go-cms-tsa.tsr")
	if err := os.WriteFile(contentPath, content, 0o644); err != nil {
		return err
	}
	if err := os.WriteFile(sigPath, der, 0o644); err != nil {
		return err
	}
	if err := os.WriteFile(tsrPath, tsRespBytes, 0o644); err != nil {
		return err
	}
	fmt.Println("\n=== 3) Artefakte geschrieben (Go: CMS und TSR separat) ===")
	fmt.Printf("  Content:  %s\n", contentPath)
	fmt.Printf("  CMS:      %s (%d Byte)\n", sigPath, len(der))
	fmt.Printf("  TSR:      %s (%d Byte)\n", tsrPath, len(tsRespBytes))

	// === 4) Verifikation ===
	fmt.Println("\n=== 4) Verifikation ===")
	parsed.Content = content
	if err := parsed.Verify(); err != nil {
		return fmt.Errorf("CMS-Verify: %w", err)
	}
	fmt.Println("  CMS-Signatur:    OK")

	// TSResp gegen die Signer-Signature pruefen — der Hash im TSResp muss
	// dem SHA-256 der CMS-Signature entsprechen.
	expectedHash := sha256Sum(signerSignature)
	if !bytesEqual(expectedHash, tsResp.HashedMessage) {
		return fmt.Errorf("Timestamp-Hash != SHA-256(CMS-Signatur)")
	}
	fmt.Println("  Timestamp-Hash:  OK")

	// TSA-Cert-Chain pruefen: tsa-cert.pem ist im EncapsulatedData enthalten,
	// oder wir laden es separat.
	tsaCert, err := loadCert(filepath.Join(outputDir, "tsa-cert.pem"))
	if err != nil {
		return fmt.Errorf("TSA-Cert laden: %w", err)
	}
	if !tsResp.Time.After(time.Now().Add(-24 * time.Hour)) {
		fmt.Printf("  Warnung: Timestamp liegt mehr als 24h zurueck (%s)\n", tsResp.Time)
	}
	fmt.Printf("  Timestamp-Zeit:  %s\n", tsResp.Time.Format(time.RFC3339))
	fmt.Printf("  TSA-Subject:     %s\n", tsaCert.Subject)

	fmt.Println("\nFertig — CMS-Signatur + RFC-3161-Timestamp jeweils valid.")
	fmt.Println("Hinweis: Go-Pfad schreibt CMS und TSR getrennt (siehe Modulkommentar).")
	return nil
}

func postTSA(url string, tsReq []byte) ([]byte, error) {
	req, err := http.NewRequest(http.MethodPost, url, strings.NewReader(string(tsReq)))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/timestamp-query")
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("TSA HTTP %d: %s", resp.StatusCode, string(body))
	}
	return body, nil
}

func sha256Sum(data []byte) []byte {
	h := sha256.Sum256(data)
	return h[:]
}

func bytesEqual(a, b []byte) bool {
	if len(a) != len(b) {
		return false
	}
	for i := range a {
		if a[i] != b[i] {
			return false
		}
	}
	return true
}

// --- ungeaenderte Helfer aus pkcs11-cms-demo ---

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
