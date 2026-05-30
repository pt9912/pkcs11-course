package main

import (
	"encoding/base64"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/miekg/pkcs11"
)

// HMAC-SHA256 ueber den HSM: CKM_SHA256_HMAC, CKK_GENERIC_SECRET-Key.
// Demos zwei Use-Cases hintereinander:
//  1) Raw HMAC: signiere Daten, verifiziere via HSM-seitigem C_Verify.
//  2) JWT (HS256): standardisierter JSON-Web-Token-Roundtrip ueber den HSM-Key.
// Der HMAC-Key verlaesst den Token nie — auch die Verifikation ist ein HSM-Call.

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
	session, err := p11.OpenSession(slot, pkcs11.CKF_SERIAL_SESSION|pkcs11.CKF_RW_SESSION)
	if err != nil {
		return fmt.Errorf("C_OpenSession: %w", err)
	}
	defer func() { logIfError("C_CloseSession", p11.CloseSession(session)) }()
	if err := p11.Login(session, pkcs11.CKU_USER, pin); err != nil {
		return fmt.Errorf("C_Login: %w", err)
	}
	defer func() { logIfError("C_Logout", p11.Logout(session)) }()

	hmacKey, err := findKey(p11, session, pkcs11.CKO_SECRET_KEY, keyID)
	if err != nil {
		return fmt.Errorf("HMAC-Key nicht gefunden (CKA_ID=05 — make gen-hmac?): %w", err)
	}

	// --- 1) Raw HMAC ---
	data := []byte("API-Token-Anfrage von client-42 am 30.05.2026T12:00:00Z\n")
	mac, err := hmacSign(p11, session, hmacKey, data)
	if err != nil {
		return fmt.Errorf("HMAC-Sign: %w", err)
	}
	if err := hmacVerify(p11, session, hmacKey, data, mac); err != nil {
		return fmt.Errorf("HMAC-Verify (Original): %w", err)
	}

	// Tamper-Check: vorletztes Byte flippen, Verify muss fehlschlagen.
	tampered := append([]byte(nil), data...)
	tampered[len(tampered)-2] ^= 0x01
	if err := hmacVerify(p11, session, hmacKey, tampered, mac); err == nil {
		return fmt.Errorf("HMAC-Verify haette fehlschlagen muessen, hat aber bestanden")
	}

	if err := os.MkdirAll(outputDir, 0o755); err != nil {
		return err
	}
	if err := os.WriteFile(filepath.Join(outputDir, "go-hmac-data.txt"), data, 0o644); err != nil {
		return err
	}
	if err := os.WriteFile(filepath.Join(outputDir, "go-hmac-data.mac"), mac, 0o644); err != nil {
		return err
	}

	// --- 2) JWT (HS256) ---
	jwt, err := jwtSign(p11, session, hmacKey, map[string]any{
		"sub": "user-42",
		"iss": "pkcs11-lab",
		"iat": time.Now().Unix(),
		"exp": time.Now().Add(time.Hour).Unix(),
	})
	if err != nil {
		return fmt.Errorf("JWT-Sign: %w", err)
	}
	if err := jwtVerify(p11, session, hmacKey, jwt); err != nil {
		return fmt.Errorf("JWT-Verify: %w", err)
	}
	if err := os.WriteFile(filepath.Join(outputDir, "go-hmac.jwt"), []byte(jwt), 0o644); err != nil {
		return err
	}

	fmt.Println("Raw HMAC:")
	fmt.Printf("  Data:  %s (%d Bytes)\n", filepath.Join(outputDir, "go-hmac-data.txt"), len(data))
	fmt.Printf("  MAC:   %s (%d Bytes)\n", filepath.Join(outputDir, "go-hmac-data.mac"), len(mac))
	fmt.Println("  Verify (Original):   OK")
	fmt.Println("  Verify (Tampered):   abgelehnt (erwartet)")
	fmt.Println("JWT (HS256):")
	fmt.Printf("  Token: %s\n", filepath.Join(outputDir, "go-hmac.jwt"))
	fmt.Printf("  Wert:  %s\n", jwt)
	fmt.Println("  Verify: OK")
	return nil
}

func hmacSign(p11 *pkcs11.Ctx, session pkcs11.SessionHandle, key pkcs11.ObjectHandle, data []byte) ([]byte, error) {
	mech := []*pkcs11.Mechanism{pkcs11.NewMechanism(pkcs11.CKM_SHA256_HMAC, nil)}
	if err := p11.SignInit(session, mech, key); err != nil {
		return nil, fmt.Errorf("C_SignInit: %w", err)
	}
	return p11.Sign(session, data)
}

func hmacVerify(p11 *pkcs11.Ctx, session pkcs11.SessionHandle, key pkcs11.ObjectHandle, data, mac []byte) error {
	mech := []*pkcs11.Mechanism{pkcs11.NewMechanism(pkcs11.CKM_SHA256_HMAC, nil)}
	if err := p11.VerifyInit(session, mech, key); err != nil {
		return fmt.Errorf("C_VerifyInit: %w", err)
	}
	return p11.Verify(session, data, mac)
}

// jwtSign baut einen kompakten JWT (Header.Payload.Signature) im HS256-Format.
// Header und Payload sind base64url-codiertes JSON (ohne Padding, RFC 4648 §5).
// Die Signatur ist HMAC-SHA256 ueber "Header.Payload", base64url-codiert.
func jwtSign(p11 *pkcs11.Ctx, session pkcs11.SessionHandle, key pkcs11.ObjectHandle, claims map[string]any) (string, error) {
	header, _ := json.Marshal(map[string]string{"alg": "HS256", "typ": "JWT"})
	payload, _ := json.Marshal(claims)
	signingInput := b64url(header) + "." + b64url(payload)
	mac, err := hmacSign(p11, session, key, []byte(signingInput))
	if err != nil {
		return "", err
	}
	return signingInput + "." + b64url(mac), nil
}

func jwtVerify(p11 *pkcs11.Ctx, session pkcs11.SessionHandle, key pkcs11.ObjectHandle, token string) error {
	parts := strings.Split(token, ".")
	if len(parts) != 3 {
		return fmt.Errorf("JWT muss genau 3 Parts haben, hat %d", len(parts))
	}
	mac, err := base64.RawURLEncoding.DecodeString(parts[2])
	if err != nil {
		return fmt.Errorf("Signatur-Base64URL ungueltig: %w", err)
	}
	signingInput := parts[0] + "." + parts[1]
	return hmacVerify(p11, session, key, []byte(signingInput), mac)
}

func b64url(b []byte) string {
	return base64.RawURLEncoding.EncodeToString(b)
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

func logIfError(op string, err error) {
	if err != nil {
		fmt.Fprintf(os.Stderr, "Warnung: %s: %v\n", op, err)
	}
}
