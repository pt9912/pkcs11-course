package main

// ECDH + HKDF Demo (Kapitel 24):
//   1) Alice und Bob haben EC-P256-Keypaare im selben Token (Lab-Vereinfachung;
//      in realen Setups liegen die in getrennten Tokens/Geraeten).
//   2) Beide leiten via C_DeriveKey(CKM_ECDH1_DERIVE) das gleiche Shared Secret
//      ab — Alice nutzt ihren Privkey + Bobs Pubkey, Bob umgekehrt.
//   3) Zwei KDF-Pfade:
//        --kdf=raw   nutzt das rohe Shared Secret (P-256 x-Koordinate, 32 Byte)
//                    direkt als AES-256-Key. Funktional ok, aber kein Standard-
//                    Protokoll-Pattern.
//        --kdf=hkdf  laeuft host-side ueber HKDF-SHA256 (RFC 5869) mit den
//                    Lab-Defaults info="ECDH-Lab-V1", salt=zero. SoftHSM 2.x
//                    (PKCS#11 v2.40) bietet CKM_HKDF_DERIVE nicht; reale HSMs
//                    auf PKCS#11 v3.0 koennen es on-Token.
//   4) AES-GCM-Roundtrip: Alice verschluesselt eine Nachricht, Bob entschluesselt.
//      Match-Beweis: wenn der Roundtrip funktioniert, waren die Shared Secrets gleich.

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"flag"
	"fmt"
	"hash"
	"os"
	"strings"

	"github.com/miekg/pkcs11"
	"golang.org/x/crypto/hkdf"
)

const (
	hkdfInfo = "ECDH-Lab-V1"
)

func main() {
	if err := run(); err != nil {
		fmt.Fprintf(os.Stderr, "Fehler: %v\n", err)
		os.Exit(1)
	}
}

func run() error {
	module := flag.String("module", env("PKCS11_MODULE", "/usr/lib/softhsm/libsofthsm2.so"), "PKCS#11 library")
	tokenLabel := flag.String("token", env("PKCS11_TOKEN_LABEL", "dev-token"), "Token-Label")
	pin := flag.String("pin", env("PKCS11_USER_PIN", "987654"), "User-PIN")
	aliceLabel := flag.String("alice-label", env("PKCS11_ECDH_ALICE_LABEL", "alice-ec-key"), "Label fuer Alice' EC-Key")
	bobLabel := flag.String("bob-label", env("PKCS11_ECDH_BOB_LABEL", "bob-ec-key"), "Label fuer Bobs EC-Key")
	kdfMode := flag.String("kdf", "hkdf", "KDF-Pfad: hkdf | raw")
	flag.Parse()

	p := pkcs11.New(*module)
	if p == nil {
		return fmt.Errorf("Modul kann nicht geladen werden: %s", *module)
	}
	defer p.Destroy()
	if err := p.Initialize(); err != nil {
		return fmt.Errorf("C_Initialize: %w", err)
	}
	defer func() { _ = p.Finalize() }()

	slot, err := findSlot(p, *tokenLabel)
	if err != nil {
		return err
	}
	session, err := p.OpenSession(slot, pkcs11.CKF_SERIAL_SESSION|pkcs11.CKF_RW_SESSION)
	if err != nil {
		return fmt.Errorf("C_OpenSession: %w", err)
	}
	defer p.CloseSession(session)
	if err := p.Login(session, pkcs11.CKU_USER, *pin); err != nil {
		return fmt.Errorf("C_Login: %w", err)
	}
	defer p.Logout(session)

	// Schritt 1: Beide Keypaare laden.
	alicePriv, err := findKey(p, session, pkcs11.CKO_PRIVATE_KEY, *aliceLabel)
	if err != nil {
		return fmt.Errorf("Alice-Privkey '%s' nicht gefunden: %w (make gen-ecdh-keys?)", *aliceLabel, err)
	}
	bobPriv, err := findKey(p, session, pkcs11.CKO_PRIVATE_KEY, *bobLabel)
	if err != nil {
		return fmt.Errorf("Bob-Privkey '%s' nicht gefunden: %w", *bobLabel, err)
	}
	alicePubPoint, err := readECPoint(p, session, *aliceLabel)
	if err != nil {
		return fmt.Errorf("Alice-Pubkey-Point lesen: %w", err)
	}
	bobPubPoint, err := readECPoint(p, session, *bobLabel)
	if err != nil {
		return fmt.Errorf("Bob-Pubkey-Point lesen: %w", err)
	}
	fmt.Printf("=== 1) Setup ===\n")
	fmt.Printf("  Alice priv-handle=%d   pub-point=%s...\n", alicePriv, hex.EncodeToString(alicePubPoint[:8]))
	fmt.Printf("  Bob   priv-handle=%d   pub-point=%s...\n", bobPriv, hex.EncodeToString(bobPubPoint[:8]))

	// Schritt 2: ECDH-Derive auf beiden Seiten.
	// Wir lassen das rohe Shared Secret CKK_GENERIC_SECRET sein und extrahieren
	// es zur Demo (CKA_EXTRACTABLE=true). Reale Anwendungen lassen es im Token
	// und benutzen es ueber C_Sign/C_Encrypt — der Extract ist ein Lab-Beweis.
	fmt.Printf("\n=== 2) ECDH-Derive (CKM_ECDH1_DERIVE, kdf=CKD_NULL) ===\n")
	aliceSecret, err := deriveECDH(p, session, alicePriv, bobPubPoint)
	if err != nil {
		return fmt.Errorf("Alice-Derive: %w", err)
	}
	bobSecret, err := deriveECDH(p, session, bobPriv, alicePubPoint)
	if err != nil {
		return fmt.Errorf("Bob-Derive: %w", err)
	}
	if !equalBytes(aliceSecret, bobSecret) {
		return fmt.Errorf("Shared Secrets unterscheiden sich — ECDH-Protokoll kaputt")
	}
	fmt.Printf("  Alice-Secret: %s... (%d Byte)\n", hex.EncodeToString(aliceSecret[:8]), len(aliceSecret))
	fmt.Printf("  Bob-Secret:   %s... (%d Byte)\n", hex.EncodeToString(bobSecret[:8]), len(bobSecret))
	fmt.Printf("  Match: ja  (P-256 x-Koordinate, byte-identisch)\n")

	// Schritt 3: KDF-Pfad.
	var aliceKey, bobKey []byte
	switch strings.ToLower(*kdfMode) {
	case "raw":
		fmt.Printf("\n=== 3) KDF=raw — Shared Secret direkt als AES-256-Key ===\n")
		// P-256 liefert 32 Byte — perfekt fuer AES-256.
		if len(aliceSecret) < 32 {
			return fmt.Errorf("Shared Secret zu kurz fuer AES-256: %d Byte", len(aliceSecret))
		}
		aliceKey = aliceSecret[:32]
		bobKey = bobSecret[:32]
		fmt.Printf("  Funktional ok (vergleichbar ECIES mit KDF=identity), aber kein Standard-Protokoll-Pattern.\n")
	case "hkdf":
		fmt.Printf("\n=== 3) KDF=hkdf — HKDF-SHA256 host-side (RFC 5869) ===\n")
		fmt.Printf("  info=%q  salt=zero  ckm_hkdf_derive nicht in SoftHSM 2.x\n", hkdfInfo)
		aliceKey = hkdfExtractExpand(aliceSecret, []byte(hkdfInfo), 32)
		bobKey = hkdfExtractExpand(bobSecret, []byte(hkdfInfo), 32)
	default:
		return fmt.Errorf("--kdf=%s unbekannt; nutze hkdf oder raw", *kdfMode)
	}
	if !equalBytes(aliceKey, bobKey) {
		return fmt.Errorf("KDF-Output unterscheidet sich — Pfad %q kaputt", *kdfMode)
	}
	fmt.Printf("  AES-Key (gekuerzt): %s...\n", hex.EncodeToString(aliceKey[:8]))

	// Schritt 4: AES-GCM-Roundtrip.
	fmt.Printf("\n=== 4) AES-256-GCM Roundtrip ===\n")
	plaintext := []byte("Hallo Bob — diese Nachricht kommt von Alice ueber ECDH+HKDF.")
	ciphertext, nonce, err := aesGCMSeal(aliceKey, plaintext)
	if err != nil {
		return fmt.Errorf("Alice-encrypt: %w", err)
	}
	recovered, err := aesGCMOpen(bobKey, nonce, ciphertext)
	if err != nil {
		return fmt.Errorf("Bob-decrypt: %w", err)
	}
	fmt.Printf("  Alice -> %d Byte Ciphertext + 12 Byte Nonce + 16 Byte Tag\n", len(ciphertext)-16)
	fmt.Printf("  Bob   <- entschluesselt: %q\n", string(recovered))
	if string(recovered) != string(plaintext) {
		return fmt.Errorf("Roundtrip-Output != Input")
	}
	fmt.Printf("\nFertig — Shared-Secret-Match-Beweis erbracht und AES-Roundtrip erfolgreich.\n")
	return nil
}

// deriveECDH ruft C_DeriveKey(CKM_ECDH1_DERIVE, kdf=CKD_NULL) auf und extrahiert
// das resultierende Shared Secret aus dem Token (Lab-Beweis).
func deriveECDH(p *pkcs11.Ctx, s pkcs11.SessionHandle, myPriv pkcs11.ObjectHandle, peerEcPoint []byte) ([]byte, error) {
	// CK_ECDH1_DERIVE_PARAMS: kdf, ulSharedDataLen, pSharedData, ulPublicDataLen, pPublicData
	// miekg/pkcs11 bietet ECDH1DeriveParams als Convenience-Struct.
	params := pkcs11.NewECDH1DeriveParams(pkcs11.CKD_NULL, nil, peerEcPoint)
	mech := []*pkcs11.Mechanism{pkcs11.NewMechanism(pkcs11.CKM_ECDH1_DERIVE, params)}
	// Template fuers neue Secret-Objekt: GENERIC_SECRET, extractable fuer den
	// Match-Beweis. CKA_VALUE_LEN=32 fragt explizit nach der vollen x-Koordinate.
	template := []*pkcs11.Attribute{
		pkcs11.NewAttribute(pkcs11.CKA_CLASS, pkcs11.CKO_SECRET_KEY),
		pkcs11.NewAttribute(pkcs11.CKA_KEY_TYPE, pkcs11.CKK_GENERIC_SECRET),
		pkcs11.NewAttribute(pkcs11.CKA_TOKEN, false), // Session-Objekt, lebt nur waehrend dieser Demo
		pkcs11.NewAttribute(pkcs11.CKA_SENSITIVE, false),
		pkcs11.NewAttribute(pkcs11.CKA_EXTRACTABLE, true),
		pkcs11.NewAttribute(pkcs11.CKA_ENCRYPT, false),
		pkcs11.NewAttribute(pkcs11.CKA_DECRYPT, false),
		pkcs11.NewAttribute(pkcs11.CKA_SIGN, false),
		pkcs11.NewAttribute(pkcs11.CKA_VERIFY, false),
		pkcs11.NewAttribute(pkcs11.CKA_WRAP, false),
		pkcs11.NewAttribute(pkcs11.CKA_UNWRAP, false),
		pkcs11.NewAttribute(pkcs11.CKA_VALUE_LEN, 32),
	}
	derivedHandle, err := p.DeriveKey(s, mech, myPriv, template)
	if err != nil {
		return nil, fmt.Errorf("C_DeriveKey: %w", err)
	}
	defer p.DestroyObject(s, derivedHandle)
	// CKA_VALUE auslesen, weil extractable=true.
	attrs, err := p.GetAttributeValue(s, derivedHandle, []*pkcs11.Attribute{
		pkcs11.NewAttribute(pkcs11.CKA_VALUE, nil),
	})
	if err != nil {
		return nil, fmt.Errorf("CKA_VALUE lesen: %w", err)
	}
	return attrs[0].Value, nil
}

// readECPoint liest CKA_EC_POINT vom Public Key Object mit dem gegebenen Label.
// CKA_EC_POINT ist DER-encoded OCTET STRING, das wir an C_DeriveKey unveraendert
// weiterreichen (das erwartet die OASIS-Spec exakt so).
func readECPoint(p *pkcs11.Ctx, s pkcs11.SessionHandle, label string) ([]byte, error) {
	template := []*pkcs11.Attribute{
		pkcs11.NewAttribute(pkcs11.CKA_CLASS, pkcs11.CKO_PUBLIC_KEY),
		pkcs11.NewAttribute(pkcs11.CKA_LABEL, label),
	}
	if err := p.FindObjectsInit(s, template); err != nil {
		return nil, err
	}
	objects, _, err := p.FindObjects(s, 1)
	p.FindObjectsFinal(s)
	if err != nil {
		return nil, err
	}
	if len(objects) == 0 {
		return nil, fmt.Errorf("kein Pubkey mit Label %q gefunden", label)
	}
	attrs, err := p.GetAttributeValue(s, objects[0], []*pkcs11.Attribute{
		pkcs11.NewAttribute(pkcs11.CKA_EC_POINT, nil),
	})
	if err != nil {
		return nil, err
	}
	return attrs[0].Value, nil
}

func findKey(p *pkcs11.Ctx, s pkcs11.SessionHandle, class uint, label string) (pkcs11.ObjectHandle, error) {
	template := []*pkcs11.Attribute{
		pkcs11.NewAttribute(pkcs11.CKA_CLASS, class),
		pkcs11.NewAttribute(pkcs11.CKA_LABEL, label),
	}
	if err := p.FindObjectsInit(s, template); err != nil {
		return 0, err
	}
	objects, _, err := p.FindObjects(s, 1)
	p.FindObjectsFinal(s)
	if err != nil {
		return 0, err
	}
	if len(objects) == 0 {
		return 0, fmt.Errorf("keine Treffer fuer class=%d label=%q", class, label)
	}
	return objects[0], nil
}

// hkdfExtractExpand: RFC 5869 Extract+Expand mit HMAC-SHA256.
// hkdf.New macht beides — salt=nil entspricht per Konvention HashLen Nullbytes.
// Das ist exakt dieselbe Semantik wie .NETs HKDF.DeriveKey und die Java/Kotlin-
// Eigenbauten, deshalb byte-identische Outputs ueber alle vier Sprachen.
func hkdfExtractExpand(secret, info []byte, length int) []byte {
	r := hkdf.New(func() hash.Hash { return sha256.New() }, secret, nil, info)
	out := make([]byte, length)
	if _, err := r.Read(out); err != nil {
		panic(err)
	}
	return out
}

func aesGCMSeal(key, plaintext []byte) (ciphertext, nonce []byte, err error) {
	block, err := aes.NewCipher(key)
	if err != nil {
		return nil, nil, err
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return nil, nil, err
	}
	nonce = make([]byte, gcm.NonceSize())
	if _, err := rand.Read(nonce); err != nil {
		return nil, nil, err
	}
	ct := gcm.Seal(nil, nonce, plaintext, nil)
	return ct, nonce, nil
}

func aesGCMOpen(key, nonce, ciphertext []byte) ([]byte, error) {
	block, err := aes.NewCipher(key)
	if err != nil {
		return nil, err
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return nil, err
	}
	return gcm.Open(nil, nonce, ciphertext, nil)
}

func findSlot(p *pkcs11.Ctx, tokenLabel string) (uint, error) {
	slots, err := p.GetSlotList(true)
	if err != nil {
		return 0, err
	}
	for _, slot := range slots {
		info, err := p.GetTokenInfo(slot)
		if err != nil {
			continue
		}
		if strings.TrimSpace(info.Label) == tokenLabel {
			return slot, nil
		}
	}
	return 0, fmt.Errorf("Token %q nicht gefunden", tokenLabel)
}

func equalBytes(a, b []byte) bool {
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

func env(name, fallback string) string {
	if v := os.Getenv(name); v != "" {
		return v
	}
	return fallback
}
