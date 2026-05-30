using System.Text;
using Net.Pkcs11Interop.Common;
using Net.Pkcs11Interop.HighLevelAPI;

// PIN-Lifecycle in C# / Pkcs11Interop — vgl. Pkcs11PinDemo.go.
// Pkcs11Interop bietet:
//   - slot.GetTokenInfo().TokenFlags.UserPinCountLow/FinalTry/Locked/... — bequeme Properties
//   - session.SetPin(oldPin, newPin) — User aendert eigene PIN
//   - session.InitPin(newUserPin) — SO setzt User-PIN (SO-Session noetig)

var modulePath = Env("PKCS11_MODULE", "/usr/lib/softhsm/libsofthsm2.so");
var tokenLabel = Env("PKCS11_TOKEN_LABEL", "dev-token");
var userPin = Env("PKCS11_USER_PIN", "987654");
var soPin = Env("PKCS11_SO_PIN", "1234");
var tmpPin = Env("PKCS11_TMP_PIN", "555444");
var wrongPin = Env("PKCS11_LOCKOUT_PIN", "000000");

var factories = new Pkcs11InteropFactories();
using var library = factories.Pkcs11LibraryFactory.LoadPkcs11Library(factories, modulePath, AppType.MultiThreaded);
var slot = FindSlot(library, tokenLabel);

Console.WriteLine("=== 1) Initialer Token-Status ===");
PrintPinFlags(slot);

Console.WriteLine("=== 2) C_SetPIN: User-PIN aendern ===");
UserSetPin(slot, userPin, tmpPin);
Console.WriteLine($"  {userPin} -> {tmpPin} OK");
LoginCheck(slot, tmpPin);
Console.WriteLine($"  Login mit {tmpPin} funktioniert.");
UserSetPin(slot, tmpPin, userPin);
Console.WriteLine($"  {tmpPin} -> {userPin} OK, Ausgangs-PIN wiederhergestellt.");

Console.WriteLine("=== 3) Drei Fehlversuche mit falschem PIN ===");
for (int i = 1; i <= 3; i++)
{
    var err = TryFailedLogin(slot, wrongPin);
    Console.WriteLine($"  Versuch {i}: {err}");
}
Console.WriteLine("  Token-Flags danach:");
PrintPinFlags(slot);

Console.WriteLine("=== 4) SO setzt User-PIN per C_InitPIN ===");
const string recoveredPin = "222333";
SoInitUserPin(slot, soPin, recoveredPin);
Console.WriteLine($"  SO-Init OK, User-PIN ist jetzt {recoveredPin}");
LoginCheck(slot, recoveredPin);
Console.WriteLine($"  Login mit {recoveredPin} funktioniert — Recovery erfolgreich.");

Console.WriteLine("=== 5) Cleanup: SO setzt PIN zurueck auf Standard ===");
SoInitUserPin(slot, soPin, userPin);
LoginCheck(slot, userPin);
Console.WriteLine($"  Login mit {userPin} wieder OK, Ausgangs-State wiederhergestellt.");

static void UserSetPin(ISlot slot, string oldPin, string newPin)
{
    var oldBytes = Encoding.UTF8.GetBytes(oldPin);
    var newBytes = Encoding.UTF8.GetBytes(newPin);
    using var session = slot.OpenSession(SessionType.ReadWrite);
    try
    {
        session.Login(CKU.CKU_USER, oldBytes);
        session.SetPin(oldBytes, newBytes);
        session.Logout();
    }
    finally
    {
        Array.Clear(oldBytes, 0, oldBytes.Length);
        Array.Clear(newBytes, 0, newBytes.Length);
    }
}

static void SoInitUserPin(ISlot slot, string soPin, string newUserPin)
{
    var soBytes = Encoding.UTF8.GetBytes(soPin);
    var newBytes = Encoding.UTF8.GetBytes(newUserPin);
    using var session = slot.OpenSession(SessionType.ReadWrite);
    try
    {
        session.Login(CKU.CKU_SO, soBytes);
        session.InitPin(newBytes);
        session.Logout();
    }
    finally
    {
        Array.Clear(soBytes, 0, soBytes.Length);
        Array.Clear(newBytes, 0, newBytes.Length);
    }
}

static void LoginCheck(ISlot slot, string pin)
{
    var pinBytes = Encoding.UTF8.GetBytes(pin);
    using var session = slot.OpenSession(SessionType.ReadOnly);
    try
    {
        session.Login(CKU.CKU_USER, pinBytes);
        session.Logout();
    }
    finally
    {
        Array.Clear(pinBytes, 0, pinBytes.Length);
    }
}

static string TryFailedLogin(ISlot slot, string wrongPin)
{
    var pinBytes = Encoding.UTF8.GetBytes(wrongPin);
    using var session = slot.OpenSession(SessionType.ReadOnly);
    try
    {
        session.Login(CKU.CKU_USER, pinBytes);
        return "kein Fehler? Login hat funktioniert.";
    }
    catch (Pkcs11Exception ex)
    {
        return $"{ex.Method} → {ex.RV}";
    }
    finally
    {
        Array.Clear(pinBytes, 0, pinBytes.Length);
    }
}

static void PrintPinFlags(ISlot slot)
{
    var tf = slot.GetTokenInfo().TokenFlags;
    var set = new List<string>();
    if (tf.UserPinCountLow) set.Add("CKF_USER_PIN_COUNT_LOW");
    if (tf.UserPinFinalTry) set.Add("CKF_USER_PIN_FINAL_TRY");
    if (tf.UserPinLocked) set.Add("CKF_USER_PIN_LOCKED");
    if (tf.UserPinToBeChanged) set.Add("CKF_USER_PIN_TO_BE_CHANGED");
    if (tf.SoPinCountLow) set.Add("CKF_SO_PIN_COUNT_LOW");
    if (tf.SoPinFinalTry) set.Add("CKF_SO_PIN_FINAL_TRY");
    if (tf.SoPinLocked) set.Add("CKF_SO_PIN_LOCKED");
    Console.WriteLine(set.Count == 0
        ? $"  PIN-Flags: keine PIN-Sub-Flags gesetzt (Flags=0x{tf.Flags:x8})"
        : $"  PIN-Flags gesetzt: {string.Join(", ", set)}");
}

static ISlot FindSlot(IPkcs11Library library, string tokenLabel)
{
    foreach (var slot in library.GetSlotList(SlotsType.WithTokenPresent))
    {
        if (slot.GetTokenInfo().Label.Trim() == tokenLabel)
        {
            return slot;
        }
    }
    throw new InvalidOperationException($"Token mit Label '{tokenLabel}' nicht gefunden.");
}

static string Env(string name, string fallback)
{
    var value = Environment.GetEnvironmentVariable(name);
    return string.IsNullOrWhiteSpace(value) ? fallback : value;
}
