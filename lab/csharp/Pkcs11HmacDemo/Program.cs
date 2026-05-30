using System.Text;
using System.Text.Json;
using Net.Pkcs11Interop.Common;
using Net.Pkcs11Interop.HighLevelAPI;

// HMAC-SHA256 ueber den HSM (CKM_SHA256_HMAC, CKK_GENERIC_SECRET).
// Demo zwei Use-Cases:
//  1) Raw HMAC mit Tamper-Test.
//  2) JWT (HS256) Roundtrip.
// session.Verify routet auf C_Verify — der Token vergleicht in constant time.

var modulePath = Env("PKCS11_MODULE", "/usr/lib/softhsm/libsofthsm2.so");
var tokenLabel = Env("PKCS11_TOKEN_LABEL", "dev-token");
var pin = Env("PKCS11_USER_PIN", "987654");
var outputDir = Env("PKCS11_OUTPUT_DIR", "/workspace/lab/work");
var keyId = new byte[] { 0x05 };

var pinBytes = Encoding.UTF8.GetBytes(pin);
var factories = new Pkcs11InteropFactories();
using var library = factories.Pkcs11LibraryFactory.LoadPkcs11Library(factories, modulePath, AppType.MultiThreaded);
var slot = FindSlot(library, tokenLabel);
using var session = slot.OpenSession(SessionType.ReadWrite);
try
{
    session.Login(CKU.CKU_USER, pinBytes);
}
finally
{
    Array.Clear(pinBytes, 0, pinBytes.Length);
}

try
{
    var hmacKey = FindKey(session, factories, CKO.CKO_SECRET_KEY, keyId);

    // --- 1) Raw HMAC ---
    var data = Encoding.UTF8.GetBytes("API-Token-Anfrage von client-42 am 30.05.2026T12:00:00Z\n");
    var mac = HmacSign(session, factories, hmacKey, data);
    var ok = HmacVerify(session, factories, hmacKey, data, mac);
    if (!ok)
    {
        Console.Error.WriteLine("Verify (Original) FEHLGESCHLAGEN.");
        Environment.Exit(3);
    }

    var tampered = (byte[])data.Clone();
    tampered[^2] ^= 0x01;
    var tamperedOk = HmacVerify(session, factories, hmacKey, tampered, mac);
    if (tamperedOk)
    {
        Console.Error.WriteLine("Tampered-Verify haette fehlschlagen muessen.");
        Environment.Exit(3);
    }

    Directory.CreateDirectory(outputDir);
    File.WriteAllBytes(Path.Combine(outputDir, "csharp-hmac-data.txt"), data);
    File.WriteAllBytes(Path.Combine(outputDir, "csharp-hmac-data.mac"), mac);

    // --- 2) JWT (HS256) ---
    var jwt = JwtSign(session, factories, hmacKey, new Dictionary<string, object>
    {
        ["sub"] = "user-42",
        ["iss"] = "pkcs11-lab",
        ["iat"] = DateTimeOffset.UtcNow.ToUnixTimeSeconds(),
        ["exp"] = DateTimeOffset.UtcNow.AddHours(1).ToUnixTimeSeconds(),
    });
    var jwtOk = JwtVerify(session, factories, hmacKey, jwt);
    if (!jwtOk)
    {
        Console.Error.WriteLine("JWT-Verify fehlgeschlagen.");
        Environment.Exit(3);
    }
    File.WriteAllText(Path.Combine(outputDir, "csharp-hmac.jwt"), jwt);

    Console.WriteLine("Raw HMAC:");
    Console.WriteLine($"  Data:  {Path.Combine(outputDir, "csharp-hmac-data.txt")} ({data.Length} Bytes)");
    Console.WriteLine($"  MAC:   {Path.Combine(outputDir, "csharp-hmac-data.mac")} ({mac.Length} Bytes)");
    Console.WriteLine("  Verify (Original):   OK");
    Console.WriteLine("  Verify (Tampered):   abgelehnt (erwartet)");
    Console.WriteLine("JWT (HS256):");
    Console.WriteLine($"  Token: {Path.Combine(outputDir, "csharp-hmac.jwt")}");
    Console.WriteLine($"  Wert:  {jwt}");
    Console.WriteLine("  Verify: OK");
}
finally
{
    session.Logout();
}

static byte[] HmacSign(ISession session, Pkcs11InteropFactories factories, IObjectHandle key, byte[] data)
{
    using var mech = factories.MechanismFactory.Create(CKM.CKM_SHA256_HMAC);
    return session.Sign(mech, key, data);
}

static bool HmacVerify(ISession session, Pkcs11InteropFactories factories, IObjectHandle key, byte[] data, byte[] mac)
{
    using var mech = factories.MechanismFactory.Create(CKM.CKM_SHA256_HMAC);
    session.Verify(mech, key, data, mac, out bool isValid);
    return isValid;
}

static string JwtSign(ISession session, Pkcs11InteropFactories factories, IObjectHandle key, IDictionary<string, object> claims)
{
    var header = JsonSerializer.SerializeToUtf8Bytes(new { alg = "HS256", typ = "JWT" });
    var payload = JsonSerializer.SerializeToUtf8Bytes(claims);
    var signingInput = $"{B64Url(header)}.{B64Url(payload)}";
    var mac = HmacSign(session, factories, key, Encoding.UTF8.GetBytes(signingInput));
    return $"{signingInput}.{B64Url(mac)}";
}

static bool JwtVerify(ISession session, Pkcs11InteropFactories factories, IObjectHandle key, string token)
{
    var parts = token.Split('.');
    if (parts.Length != 3) return false;
    byte[] mac;
    try
    {
        mac = B64UrlDecode(parts[2]);
    }
    catch
    {
        return false;
    }
    var signingInput = Encoding.UTF8.GetBytes($"{parts[0]}.{parts[1]}");
    return HmacVerify(session, factories, key, signingInput, mac);
}

// Base64URL ohne Padding (RFC 4648 §5) — Standard fuer JWT.
static string B64Url(byte[] data) =>
    Convert.ToBase64String(data).TrimEnd('=').Replace('+', '-').Replace('/', '_');

static byte[] B64UrlDecode(string s)
{
    var padded = s.Replace('-', '+').Replace('_', '/');
    padded += new string('=', (4 - padded.Length % 4) % 4);
    return Convert.FromBase64String(padded);
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

static IObjectHandle FindKey(ISession session, Pkcs11InteropFactories factories, CKO classType, byte[] keyId)
{
    var template = new List<IObjectAttribute>
    {
        factories.ObjectAttributeFactory.Create(CKA.CKA_CLASS, classType),
        factories.ObjectAttributeFactory.Create(CKA.CKA_ID, keyId),
    };
    var objects = session.FindAllObjects(template);
    if (objects.Count != 1)
    {
        throw new InvalidOperationException($"Erwartet genau einen Treffer fuer class={classType} CKA_ID={Convert.ToHexString(keyId)}, gefunden: {objects.Count}");
    }
    return objects[0];
}

static string Env(string name, string fallback)
{
    var value = Environment.GetEnvironmentVariable(name);
    return string.IsNullOrWhiteSpace(value) ? fallback : value;
}
