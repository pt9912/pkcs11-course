using System.Security.Cryptography;
using System.Text;
using Net.Pkcs11Interop.Common;
using Net.Pkcs11Interop.HighLevelAPI;

// PKCS#11 Multi-Part / Streaming.
// Pkcs11Interop hat fuer ISession.Sign/Encrypt/Decrypt Stream-Ueberladungen,
// die intern C_*Init → loop C_*Update(chunk) → C_*Final aufrufen.
// Speicherbedarf bleibt konstant unabhaengig von der Filegroesse.

var modulePath = Env("PKCS11_MODULE", "/usr/lib/softhsm/libsofthsm2.so");
var tokenLabel = Env("PKCS11_TOKEN_LABEL", "dev-token");
var pin = Env("PKCS11_USER_PIN", "987654");
var outputDir = Env("PKCS11_OUTPUT_DIR", "/workspace/lab/work");
var signKeyId = new byte[] { 0x01 };
var aesKeyId = new byte[] { 0x04 };
var inputPath = Path.Combine(outputDir, "large.bin");
var sigPath = Path.Combine(outputDir, "csharp-stream.sig");
var encPath = Path.Combine(outputDir, "csharp-stream.enc");
var decPath = Path.Combine(outputDir, "csharp-stream.dec");

if (!File.Exists(inputPath))
{
    Console.Error.WriteLine($"Testfile fehlt: {inputPath}. Erst 'make stream-sign' ausfuehren.");
    Environment.Exit(2);
}

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

var inputSize = new FileInfo(inputPath).Length;
try
{
    var signKey = FindKey(session, factories, CKO.CKO_PRIVATE_KEY, signKeyId);
    var aesKey = FindKey(session, factories, CKO.CKO_SECRET_KEY, aesKeyId);

    // --- Sign-Streaming: ISession.Sign(mech, key, Stream) liest in Chunks
    // und ruft intern C_SignUpdate/C_SignFinal.
    using var signMech = factories.MechanismFactory.Create(CKM.CKM_SHA256_RSA_PKCS);
    byte[] signature;
    using (var input = File.OpenRead(inputPath))
    {
        signature = session.Sign(signMech, signKey, input);
    }
    File.WriteAllBytes(sigPath, signature);
    Console.WriteLine($"Sign:    {Path.GetFileName(inputPath)} → {Path.GetFileName(sigPath)} ({signature.Length} Bytes Signatur ueber {inputSize} Bytes Input)");

    // --- Encrypt-Streaming: AES-CBC-PAD mit zufaelligem IV. ISession.Encrypt(mech, key, in, out)
    // streamed Input/Output via C_EncryptUpdate/C_EncryptFinal.
    var iv = RandomNumberGenerator.GetBytes(16);
    using var aesMech = factories.MechanismFactory.Create(CKM.CKM_AES_CBC_PAD, iv);
    using (var input = File.OpenRead(inputPath))
    using (var output = File.Create(encPath))
    {
        session.Encrypt(aesMech, aesKey, input, output);
    }
    Console.WriteLine($"Encrypt: {Path.GetFileName(inputPath)} → {Path.GetFileName(encPath)} ({new FileInfo(encPath).Length} Bytes inkl. PKCS#7-Padding)");

    using var aesMechDec = factories.MechanismFactory.Create(CKM.CKM_AES_CBC_PAD, iv);
    using (var input = File.OpenRead(encPath))
    using (var output = File.Create(decPath))
    {
        session.Decrypt(aesMechDec, aesKey, input, output);
    }
    Console.WriteLine($"Decrypt: {Path.GetFileName(encPath)} → {Path.GetFileName(decPath)} ({new FileInfo(decPath).Length} Bytes)");

    if (!FilesEqual(inputPath, decPath))
    {
        Console.Error.WriteLine("Round-Trip FEHLGESCHLAGEN.");
        Environment.Exit(3);
    }
    Console.WriteLine("Round-Trip: OK");
}
finally
{
    session.Logout();
}

static bool FilesEqual(string a, string b)
{
    using var fa = File.OpenRead(a);
    using var fb = File.OpenRead(b);
    if (fa.Length != fb.Length) return false;
    var ba = new byte[64 * 1024];
    var bb = new byte[64 * 1024];
    while (true)
    {
        int na = fa.Read(ba, 0, ba.Length);
        int nb = fb.Read(bb, 0, bb.Length);
        if (na != nb) return false;
        if (na == 0) return true;
        for (int i = 0; i < na; i++)
        {
            if (ba[i] != bb[i]) return false;
        }
    }
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
