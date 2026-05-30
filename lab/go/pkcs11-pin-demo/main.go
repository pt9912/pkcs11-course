package main

import (
	"fmt"
	"os"
	"strings"

	"github.com/miekg/pkcs11"
)

// PIN-Lifecycle-Demo:
//   1) TokenInfo.Flags vor allem auswerten (CKF_USER_PIN_*).
//   2) C_SetPIN: User aendert seine eigene PIN, prueft Login mit neuer PIN,
//      setzt zurueck.
//   3) Drei Fehlversuche → CKF_USER_PIN_COUNT_LOW Beobachtung.
//      (SoftHSM 2.6 lockt nie wirklich; reale HSMs locken nach N Tries.)
//   4) C_InitPIN: SO setzt User-PIN auf neuen Wert, simuliert Recovery.
//   5) Cleanup: SO setzt PIN zurueck auf Ausgangswert.

func main() {
	if err := run(); err != nil {
		fmt.Fprintf(os.Stderr, "Fehler: %v\n", err)
		os.Exit(1)
	}
}

func run() error {
	module := env("PKCS11_MODULE", "/usr/lib/softhsm/libsofthsm2.so")
	tokenLabel := env("PKCS11_TOKEN_LABEL", "dev-token")
	userPin := env("PKCS11_USER_PIN", "987654")
	soPin := env("PKCS11_SO_PIN", "1234")
	tmpPin := env("PKCS11_TMP_PIN", "555444")
	wrongPin := env("PKCS11_LOCKOUT_PIN", "000000")

	p11 := pkcs11.New(module)
	if p11 == nil {
		return fmt.Errorf("PKCS#11-Modul kann nicht geladen werden: %s", module)
	}
	defer p11.Destroy()
	if err := p11.Initialize(); err != nil {
		return fmt.Errorf("C_Initialize: %w", err)
	}
	defer func() { logIfError("C_Finalize", p11.Finalize()) }()

	slotID, err := findSlot(p11, tokenLabel)
	if err != nil {
		return err
	}

	// === 1) Initialer PIN-Status ===
	fmt.Println("=== 1) Initialer Token-Status ===")
	if err := printPinFlags(p11, slotID); err != nil {
		return err
	}

	// === 2) C_SetPIN ===
	fmt.Println("=== 2) C_SetPIN: User-PIN aendern ===")
	if err := userSetPin(p11, slotID, userPin, tmpPin); err != nil {
		return fmt.Errorf("SetPIN userPin->tmpPin: %w", err)
	}
	fmt.Printf("  %s -> %s OK\n", userPin, tmpPin)
	if err := loginCheck(p11, slotID, tmpPin); err != nil {
		return fmt.Errorf("Login mit neuer PIN: %w", err)
	}
	fmt.Printf("  Login mit %s funktioniert.\n", tmpPin)
	if err := userSetPin(p11, slotID, tmpPin, userPin); err != nil {
		return fmt.Errorf("SetPIN tmpPin->userPin: %w", err)
	}
	fmt.Printf("  %s -> %s OK, Ausgangs-PIN wiederhergestellt.\n", tmpPin, userPin)

	// === 3) Fehlversuche und Flag-Beobachtung ===
	fmt.Println("=== 3) Drei Fehlversuche mit falschem PIN ===")
	for i := 1; i <= 3; i++ {
		err := failedLogin(p11, slotID, wrongPin)
		fmt.Printf("  Versuch %d: %v\n", i, err)
	}
	fmt.Println("  Token-Flags danach:")
	if err := printPinFlags(p11, slotID); err != nil {
		return err
	}

	// === 4) C_InitPIN per SO ===
	fmt.Println("=== 4) SO setzt User-PIN per C_InitPIN ===")
	recoveredPin := "222333"
	if err := soInitUserPin(p11, slotID, soPin, recoveredPin); err != nil {
		return fmt.Errorf("InitPIN durch SO: %w", err)
	}
	fmt.Printf("  SO-Init OK, User-PIN ist jetzt %s\n", recoveredPin)
	if err := loginCheck(p11, slotID, recoveredPin); err != nil {
		return fmt.Errorf("Login mit Recovered-PIN: %w", err)
	}
	fmt.Printf("  Login mit %s funktioniert — Recovery erfolgreich.\n", recoveredPin)

	// === 5) Cleanup: SO setzt PIN zurueck ===
	fmt.Println("=== 5) Cleanup: SO setzt PIN zurueck auf Standard ===")
	if err := soInitUserPin(p11, slotID, soPin, userPin); err != nil {
		return fmt.Errorf("InitPIN Cleanup: %w", err)
	}
	if err := loginCheck(p11, slotID, userPin); err != nil {
		return fmt.Errorf("Final Login-Check: %w", err)
	}
	fmt.Printf("  Login mit %s wieder OK, Ausgangs-State wiederhergestellt.\n", userPin)
	return nil
}

// userSetPin oeffnet eine RW-Session, loggt User ein, ruft C_SetPIN, schliesst.
func userSetPin(p11 *pkcs11.Ctx, slotID uint, oldPin, newPin string) error {
	s, err := p11.OpenSession(slotID, pkcs11.CKF_SERIAL_SESSION|pkcs11.CKF_RW_SESSION)
	if err != nil {
		return err
	}
	defer p11.CloseSession(s)
	if err := p11.Login(s, pkcs11.CKU_USER, oldPin); err != nil {
		return err
	}
	defer p11.Logout(s)
	return p11.SetPIN(s, oldPin, newPin)
}

// soInitUserPin oeffnet RW-Session, loggt SO ein, ruft C_InitPIN (setzt User-PIN).
func soInitUserPin(p11 *pkcs11.Ctx, slotID uint, soPin, newUserPin string) error {
	s, err := p11.OpenSession(slotID, pkcs11.CKF_SERIAL_SESSION|pkcs11.CKF_RW_SESSION)
	if err != nil {
		return err
	}
	defer p11.CloseSession(s)
	if err := p11.Login(s, pkcs11.CKU_SO, soPin); err != nil {
		return err
	}
	defer p11.Logout(s)
	return p11.InitPIN(s, newUserPin)
}

// loginCheck verifiziert, dass eine PIN nutzbar ist (Login + sofort logout).
func loginCheck(p11 *pkcs11.Ctx, slotID uint, pin string) error {
	s, err := p11.OpenSession(slotID, pkcs11.CKF_SERIAL_SESSION)
	if err != nil {
		return err
	}
	defer p11.CloseSession(s)
	if err := p11.Login(s, pkcs11.CKU_USER, pin); err != nil {
		return err
	}
	return p11.Logout(s)
}

func failedLogin(p11 *pkcs11.Ctx, slotID uint, wrongPin string) error {
	s, err := p11.OpenSession(slotID, pkcs11.CKF_SERIAL_SESSION)
	if err != nil {
		return err
	}
	defer p11.CloseSession(s)
	return p11.Login(s, pkcs11.CKU_USER, wrongPin)
}

// printPinFlags liest TokenInfo und dekodiert die PIN-relevanten Flags.
func printPinFlags(p11 *pkcs11.Ctx, slotID uint) error {
	info, err := p11.GetTokenInfo(slotID)
	if err != nil {
		return fmt.Errorf("C_GetTokenInfo: %w", err)
	}
	flags := []struct {
		bit  uint
		name string
	}{
		{pkcs11.CKF_USER_PIN_COUNT_LOW, "CKF_USER_PIN_COUNT_LOW"},
		{pkcs11.CKF_USER_PIN_FINAL_TRY, "CKF_USER_PIN_FINAL_TRY"},
		{pkcs11.CKF_USER_PIN_LOCKED, "CKF_USER_PIN_LOCKED"},
		{pkcs11.CKF_USER_PIN_TO_BE_CHANGED, "CKF_USER_PIN_TO_BE_CHANGED"},
		{pkcs11.CKF_SO_PIN_COUNT_LOW, "CKF_SO_PIN_COUNT_LOW"},
		{pkcs11.CKF_SO_PIN_FINAL_TRY, "CKF_SO_PIN_FINAL_TRY"},
		{pkcs11.CKF_SO_PIN_LOCKED, "CKF_SO_PIN_LOCKED"},
	}
	set := []string{}
	for _, f := range flags {
		if info.Flags&f.bit != 0 {
			set = append(set, f.name)
		}
	}
	if len(set) == 0 {
		fmt.Printf("  PIN-Flags: keine PIN-Sub-Flags gesetzt (Flags=0x%08x)\n", info.Flags)
	} else {
		fmt.Printf("  PIN-Flags gesetzt: %s\n", strings.Join(set, ", "))
	}
	return nil
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
