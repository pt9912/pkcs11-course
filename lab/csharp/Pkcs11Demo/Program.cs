using System.Text;
using Net.Pkcs11Interop.Common;
using Net.Pkcs11Interop.HighLevelAPI;
using Net.Pkcs11Interop.HighLevelAPI.Factories;

var modulePath = Env("PKCS11_MODULE", "/usr/lib/softhsm/libsofthsm2.so");
var tokenLabel = Env("PKCS11_TOKEN_LABEL", "dev-token");
var pin = Env("PKCS11_USER_PIN", "987654");
var outputDir = Env("PKCS11_OUTPUT_DIR", "/workspace/lab/work");
var data = Encoding.UTF8.GetBytes("hello from csharp pkcs11");
var keyId = new byte[] { 0x01 };

// PIN als byte[] (UTF-8) an C_Login uebergeben — entspricht dem PKCS#11-Vertrag
// und passt zu den anderen Demos.
var pinBytes = Encoding.UTF8.GetBytes(pin);

var factories = new Pkcs11InteropFactories();
using var library = factories.Pkcs11LibraryFactory.LoadPkcs11Library(factories, modulePath, AppType.MultiThreaded);
var slot = FindSlot(library, tokenLabel);

using var session = slot.OpenSession(SessionType.ReadWrite);
session.Login(CKU.CKU_USER, pinBytes);
try
{
    var key = FindPrivateKey(session, factories, keyId);
    using var mechanism = session.Factories.MechanismFactory.Create(CKM.CKM_SHA256_RSA_PKCS);
    var signature = session.Sign(mechanism, key, data);

    Directory.CreateDirectory(outputDir);
    var dataPath = Path.Combine(outputDir, "csharp.txt");
    var sigPath = Path.Combine(outputDir, "csharp.sig");
    File.WriteAllBytes(dataPath, data);
    File.WriteAllBytes(sigPath, signature);

    Console.WriteLine($"Token: {tokenLabel}");
    Console.WriteLine($"Signatur: {sigPath} ({signature.Length} Bytes)");
    Console.WriteLine($"Daten: {dataPath}");
}
finally
{
    session.Logout();
}

static ISlot FindSlot(IPkcs11Library library, string tokenLabel)
{
    foreach (var slot in library.GetSlotList(SlotsType.WithTokenPresent))
    {
        var info = slot.GetTokenInfo();
        if (info.Label.Trim() == tokenLabel)
        {
            return slot;
        }
    }
    throw new InvalidOperationException($"Token mit Label '{tokenLabel}' nicht gefunden.");
}

static IObjectHandle FindPrivateKey(ISession session, Pkcs11InteropFactories factories, byte[] keyId)
{
    var template = new List<IObjectAttribute>
    {
        factories.ObjectAttributeFactory.Create(CKA.CKA_CLASS, CKO.CKO_PRIVATE_KEY),
        factories.ObjectAttributeFactory.Create(CKA.CKA_ID, keyId)
    };
    var objects = session.FindAllObjects(template);
    if (objects.Count != 1)
    {
        throw new InvalidOperationException($"Erwartet genau einen Private Key mit CKA_ID=01, gefunden: {objects.Count}");
    }
    return objects[0];
}

static string Env(string name, string fallback)
{
    var value = Environment.GetEnvironmentVariable(name);
    return string.IsNullOrWhiteSpace(value) ? fallback : value;
}
