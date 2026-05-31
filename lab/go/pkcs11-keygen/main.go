package main

// pkcs11-keygen: erzeugt Keys mit explizitem CKA-Template direkt ueber
// C_GenerateKey / C_GenerateKeyPair. Loest die SoftHSM-Default-Profil-
// Falle aus 0.15.1: pkcs11-tool --usage-* setzt nur die ausgewaehlten
// Usages, die anderen erbt SoftHSM aus seinen breiten Defaults. Wir setzen
// ALLE Usage-Attribute explizit auf TRUE oder FALSE.
//
// Aufruf-Beispiele:
//   pkcs11-keygen --type rsa --bits 2048 --label signing-key --id 01 --sign
//   pkcs11-keygen --type rsa --bits 2048 --label wrap-key   --id 03 --encrypt --wrap
//   pkcs11-keygen --type ec  --curve secp256r1 --label ec-signing-key --id 02 --sign
//   pkcs11-keygen --type aes --bits 256 --label aes-stream-key --id 04 --encrypt
//   pkcs11-keygen --type generic --bits 256 --label hmac-key --id 05 --sign
//   pkcs11-keygen --type aes --bits 256 --label kek --id 06 --wrap
//   pkcs11-keygen --type rsa --bits 2048 --label ca-key --id 08 --sign

import (
	"encoding/asn1"
	"encoding/hex"
	"flag"
	"fmt"
	"os"
	"strings"

	"github.com/miekg/pkcs11"
)

type usageProfile struct {
	sign    bool
	encrypt bool
	wrap    bool
	derive  bool
}

func main() {
	if err := run(); err != nil {
		fmt.Fprintf(os.Stderr, "pkcs11-keygen: %v\n", err)
		os.Exit(1)
	}
}

func run() error {
	module := flag.String("module", env("PKCS11_MODULE", "/usr/lib/softhsm/libsofthsm2.so"), "PKCS#11 library path")
	tokenLabel := flag.String("token", env("PKCS11_TOKEN_LABEL", "dev-token"), "Token-Label")
	pin := flag.String("pin", env("PKCS11_USER_PIN", "987654"), "User-PIN")

	keyType := flag.String("type", "", "Key-Typ: rsa | ec | aes | generic")
	bits := flag.Int("bits", 0, "Schluessellaenge in Bit (RSA: 2048+, AES/GENERIC: 128|192|256)")
	curve := flag.String("curve", "secp256r1", "EC-Kurve (nur fuer --type ec): secp256r1 | secp384r1 | secp521r1")
	label := flag.String("label", "", "CKA_LABEL")
	idHex := flag.String("id", "", "CKA_ID als Hex (z.B. 01)")

	// High-level Usage-Flags. Bei Keypairs auf die jeweilige Haelfte gemappt:
	//   --sign     priv=CKA_SIGN, pub=CKA_VERIFY
	//   --encrypt  priv=CKA_DECRYPT, pub=CKA_ENCRYPT
	//   --wrap     priv=CKA_UNWRAP, pub=CKA_WRAP
	//   --derive   priv=CKA_DERIVE, pub=CKA_DERIVE
	// Bei Secret-Keys werden beide Halften des Paares auf den Secret-Key gesetzt:
	//   --sign     CKA_SIGN + CKA_VERIFY
	//   --encrypt  CKA_ENCRYPT + CKA_DECRYPT
	//   --wrap     CKA_WRAP + CKA_UNWRAP
	//   --derive   CKA_DERIVE
	sign := flag.Bool("sign", false, "Sign-Use-Case freigeben")
	encrypt := flag.Bool("encrypt", false, "Encrypt-Use-Case freigeben")
	wrap := flag.Bool("wrap", false, "Wrap-Use-Case freigeben")
	derive := flag.Bool("derive", false, "Derive-Use-Case freigeben")

	extractable := flag.Bool("extractable", false, "CKA_EXTRACTABLE=TRUE (Default: FALSE)")
	skipIfExists := flag.Bool("skip-if-exists", true, "Bei vorhandenem Label idempotent ueberspringen")

	flag.Parse()
	if *keyType == "" || *label == "" || *idHex == "" {
		flag.Usage()
		return fmt.Errorf("missing --type/--label/--id")
	}
	id, err := hex.DecodeString(*idHex)
	if err != nil {
		return fmt.Errorf("--id %q ist kein Hex: %w", *idHex, err)
	}
	prof := usageProfile{sign: *sign, encrypt: *encrypt, wrap: *wrap, derive: *derive}

	p := pkcs11.New(*module)
	if p == nil {
		return fmt.Errorf("Modul konnte nicht geladen werden: %s", *module)
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

	if *skipIfExists {
		exists, err := labelExists(p, session, *label)
		if err != nil {
			return err
		}
		if exists {
			fmt.Printf("Key '%s' existiert bereits (Label-Treffer).\n", *label)
			return nil
		}
	}

	switch strings.ToLower(*keyType) {
	case "rsa":
		if *bits == 0 {
			*bits = 2048
		}
		return generateRSA(p, session, *bits, *label, id, prof, *extractable)
	case "ec":
		return generateEC(p, session, *curve, *label, id, prof, *extractable)
	case "aes":
		if *bits == 0 {
			*bits = 256
		}
		return generateSecret(p, session, pkcs11.CKK_AES, pkcs11.CKM_AES_KEY_GEN, *bits/8, *label, id, prof, *extractable)
	case "generic":
		if *bits == 0 {
			*bits = 256
		}
		return generateSecret(p, session, pkcs11.CKK_GENERIC_SECRET, pkcs11.CKM_GENERIC_SECRET_KEY_GEN, *bits/8, *label, id, prof, *extractable)
	default:
		return fmt.Errorf("unbekannter --type: %s (erwartet rsa|ec|aes|generic)", *keyType)
	}
}

func generateRSA(p *pkcs11.Ctx, s pkcs11.SessionHandle, bits int, label string, id []byte, u usageProfile, extractable bool) error {
	pubTemplate := []*pkcs11.Attribute{
		pkcs11.NewAttribute(pkcs11.CKA_CLASS, pkcs11.CKO_PUBLIC_KEY),
		pkcs11.NewAttribute(pkcs11.CKA_KEY_TYPE, pkcs11.CKK_RSA),
		pkcs11.NewAttribute(pkcs11.CKA_TOKEN, true),
		pkcs11.NewAttribute(pkcs11.CKA_PRIVATE, false),
		pkcs11.NewAttribute(pkcs11.CKA_LABEL, label),
		pkcs11.NewAttribute(pkcs11.CKA_ID, id),
		pkcs11.NewAttribute(pkcs11.CKA_MODULUS_BITS, bits),
		// RSA-F4 = 0x010001 als Big-Endian-Bytes.
		pkcs11.NewAttribute(pkcs11.CKA_PUBLIC_EXPONENT, []byte{0x01, 0x00, 0x01}),
		// Public-Key-Haelfte des Usage-Profils:
		pkcs11.NewAttribute(pkcs11.CKA_VERIFY, u.sign),
		pkcs11.NewAttribute(pkcs11.CKA_VERIFY_RECOVER, false),
		pkcs11.NewAttribute(pkcs11.CKA_ENCRYPT, u.encrypt),
		pkcs11.NewAttribute(pkcs11.CKA_WRAP, u.wrap),
		pkcs11.NewAttribute(pkcs11.CKA_DERIVE, u.derive),
	}
	privTemplate := []*pkcs11.Attribute{
		pkcs11.NewAttribute(pkcs11.CKA_CLASS, pkcs11.CKO_PRIVATE_KEY),
		pkcs11.NewAttribute(pkcs11.CKA_KEY_TYPE, pkcs11.CKK_RSA),
		pkcs11.NewAttribute(pkcs11.CKA_TOKEN, true),
		pkcs11.NewAttribute(pkcs11.CKA_PRIVATE, true),
		pkcs11.NewAttribute(pkcs11.CKA_SENSITIVE, true),
		pkcs11.NewAttribute(pkcs11.CKA_EXTRACTABLE, extractable),
		pkcs11.NewAttribute(pkcs11.CKA_LABEL, label),
		pkcs11.NewAttribute(pkcs11.CKA_ID, id),
		// Private-Key-Haelfte des Usage-Profils:
		pkcs11.NewAttribute(pkcs11.CKA_SIGN, u.sign),
		pkcs11.NewAttribute(pkcs11.CKA_SIGN_RECOVER, false),
		pkcs11.NewAttribute(pkcs11.CKA_DECRYPT, u.encrypt),
		pkcs11.NewAttribute(pkcs11.CKA_UNWRAP, u.wrap),
		pkcs11.NewAttribute(pkcs11.CKA_DERIVE, u.derive),
	}
	mech := []*pkcs11.Mechanism{pkcs11.NewMechanism(pkcs11.CKM_RSA_PKCS_KEY_PAIR_GEN, nil)}
	pubH, privH, err := p.GenerateKeyPair(s, mech, pubTemplate, privTemplate)
	if err != nil {
		return fmt.Errorf("C_GenerateKeyPair(RSA): %w", err)
	}
	fmt.Printf("RSA-%d Keypair erzeugt: label=%s id=%s pub=%d priv=%d\n",
		bits, label, hex.EncodeToString(id), pubH, privH)
	logUsage("priv", u)
	logUsage("pub", u)
	return nil
}

func generateEC(p *pkcs11.Ctx, s pkcs11.SessionHandle, curveName, label string, id []byte, u usageProfile, extractable bool) error {
	ecParams, err := ecParamsForCurve(curveName)
	if err != nil {
		return err
	}
	pubTemplate := []*pkcs11.Attribute{
		pkcs11.NewAttribute(pkcs11.CKA_CLASS, pkcs11.CKO_PUBLIC_KEY),
		pkcs11.NewAttribute(pkcs11.CKA_KEY_TYPE, pkcs11.CKK_EC),
		pkcs11.NewAttribute(pkcs11.CKA_TOKEN, true),
		pkcs11.NewAttribute(pkcs11.CKA_PRIVATE, false),
		pkcs11.NewAttribute(pkcs11.CKA_LABEL, label),
		pkcs11.NewAttribute(pkcs11.CKA_ID, id),
		pkcs11.NewAttribute(pkcs11.CKA_EC_PARAMS, ecParams),
		pkcs11.NewAttribute(pkcs11.CKA_VERIFY, u.sign),
		pkcs11.NewAttribute(pkcs11.CKA_ENCRYPT, u.encrypt),
		pkcs11.NewAttribute(pkcs11.CKA_WRAP, u.wrap),
		pkcs11.NewAttribute(pkcs11.CKA_DERIVE, u.derive),
	}
	privTemplate := []*pkcs11.Attribute{
		pkcs11.NewAttribute(pkcs11.CKA_CLASS, pkcs11.CKO_PRIVATE_KEY),
		pkcs11.NewAttribute(pkcs11.CKA_KEY_TYPE, pkcs11.CKK_EC),
		pkcs11.NewAttribute(pkcs11.CKA_TOKEN, true),
		pkcs11.NewAttribute(pkcs11.CKA_PRIVATE, true),
		pkcs11.NewAttribute(pkcs11.CKA_SENSITIVE, true),
		pkcs11.NewAttribute(pkcs11.CKA_EXTRACTABLE, extractable),
		pkcs11.NewAttribute(pkcs11.CKA_LABEL, label),
		pkcs11.NewAttribute(pkcs11.CKA_ID, id),
		pkcs11.NewAttribute(pkcs11.CKA_SIGN, u.sign),
		pkcs11.NewAttribute(pkcs11.CKA_DECRYPT, u.encrypt),
		pkcs11.NewAttribute(pkcs11.CKA_UNWRAP, u.wrap),
		pkcs11.NewAttribute(pkcs11.CKA_DERIVE, u.derive),
	}
	mech := []*pkcs11.Mechanism{pkcs11.NewMechanism(pkcs11.CKM_EC_KEY_PAIR_GEN, nil)}
	pubH, privH, err := p.GenerateKeyPair(s, mech, pubTemplate, privTemplate)
	if err != nil {
		return fmt.Errorf("C_GenerateKeyPair(EC %s): %w", curveName, err)
	}
	fmt.Printf("EC-%s Keypair erzeugt: label=%s id=%s pub=%d priv=%d\n",
		curveName, label, hex.EncodeToString(id), pubH, privH)
	logUsage("priv", u)
	logUsage("pub", u)
	return nil
}

func generateSecret(p *pkcs11.Ctx, s pkcs11.SessionHandle, keyType, mechType uint, bytes int, label string, id []byte, u usageProfile, extractable bool) error {
	template := []*pkcs11.Attribute{
		pkcs11.NewAttribute(pkcs11.CKA_CLASS, pkcs11.CKO_SECRET_KEY),
		pkcs11.NewAttribute(pkcs11.CKA_KEY_TYPE, keyType),
		pkcs11.NewAttribute(pkcs11.CKA_TOKEN, true),
		pkcs11.NewAttribute(pkcs11.CKA_PRIVATE, true),
		pkcs11.NewAttribute(pkcs11.CKA_SENSITIVE, true),
		pkcs11.NewAttribute(pkcs11.CKA_EXTRACTABLE, extractable),
		pkcs11.NewAttribute(pkcs11.CKA_LABEL, label),
		pkcs11.NewAttribute(pkcs11.CKA_ID, id),
		pkcs11.NewAttribute(pkcs11.CKA_VALUE_LEN, bytes),
		// Bei Secret-Keys werden Sign/Verify, Encrypt/Decrypt, Wrap/Unwrap
		// jeweils paarweise gesetzt — die Operation ist symmetrisch.
		pkcs11.NewAttribute(pkcs11.CKA_SIGN, u.sign),
		pkcs11.NewAttribute(pkcs11.CKA_VERIFY, u.sign),
		pkcs11.NewAttribute(pkcs11.CKA_ENCRYPT, u.encrypt),
		pkcs11.NewAttribute(pkcs11.CKA_DECRYPT, u.encrypt),
		pkcs11.NewAttribute(pkcs11.CKA_WRAP, u.wrap),
		pkcs11.NewAttribute(pkcs11.CKA_UNWRAP, u.wrap),
		pkcs11.NewAttribute(pkcs11.CKA_DERIVE, u.derive),
	}
	mech := []*pkcs11.Mechanism{pkcs11.NewMechanism(mechType, nil)}
	h, err := p.GenerateKey(s, mech, template)
	if err != nil {
		return fmt.Errorf("C_GenerateKey: %w", err)
	}
	fmt.Printf("Secret-Key erzeugt: label=%s id=%s handle=%d\n", label, hex.EncodeToString(id), h)
	logUsage("secret", u)
	return nil
}

// ecParamsForCurve liefert die DER-encoded OID einer EC-Kurve, wie sie
// CKA_EC_PARAMS erwartet. Die OIDs sind aus ANSI X9.62 / SEC 2.
func ecParamsForCurve(name string) ([]byte, error) {
	var oid asn1.ObjectIdentifier
	switch strings.ToLower(name) {
	case "secp256r1", "p-256", "prime256v1":
		oid = asn1.ObjectIdentifier{1, 2, 840, 10045, 3, 1, 7}
	case "secp384r1", "p-384":
		oid = asn1.ObjectIdentifier{1, 3, 132, 0, 34}
	case "secp521r1", "p-521":
		oid = asn1.ObjectIdentifier{1, 3, 132, 0, 35}
	default:
		return nil, fmt.Errorf("unbekannte Kurve: %s", name)
	}
	return asn1.Marshal(oid)
}

func labelExists(p *pkcs11.Ctx, s pkcs11.SessionHandle, label string) (bool, error) {
	template := []*pkcs11.Attribute{
		pkcs11.NewAttribute(pkcs11.CKA_LABEL, label),
	}
	if err := p.FindObjectsInit(s, template); err != nil {
		return false, err
	}
	defer p.FindObjectsFinal(s)
	objects, _, err := p.FindObjects(s, 1)
	if err != nil {
		return false, err
	}
	return len(objects) > 0, nil
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

func logUsage(role string, u usageProfile) {
	parts := []string{}
	switch role {
	case "secret":
		if u.sign {
			parts = append(parts, "SIGN", "VERIFY")
		}
		if u.encrypt {
			parts = append(parts, "ENCRYPT", "DECRYPT")
		}
		if u.wrap {
			parts = append(parts, "WRAP", "UNWRAP")
		}
	case "pub":
		if u.sign {
			parts = append(parts, "VERIFY")
		}
		if u.encrypt {
			parts = append(parts, "ENCRYPT")
		}
		if u.wrap {
			parts = append(parts, "WRAP")
		}
	default: // priv
		if u.sign {
			parts = append(parts, "SIGN")
		}
		if u.encrypt {
			parts = append(parts, "DECRYPT")
		}
		if u.wrap {
			parts = append(parts, "UNWRAP")
		}
	}
	if u.derive {
		parts = append(parts, "DERIVE")
	}
	if len(parts) == 0 {
		parts = []string{"(none)"}
	}
	fmt.Printf("  %-6s Usage: %s\n", role, strings.Join(parts, ", "))
}

func env(name, fallback string) string {
	if v := os.Getenv(name); v != "" {
		return v
	}
	return fallback
}
