using System.Security.Cryptography;
using System.Text;
using Net.Pkcs11Interop.Common;
using Net.Pkcs11Interop.HighLevelAPI;

// Backup/Restore eines symmetrischen Keys via C_WrapKey/C_UnwrapKey ueber
// Pkcs11Interop. Identische Storyline wie das Go-Demo:
//  1) Fresh AES-256 Session-Key generieren (extractable).
//  2) Test-Daten verschluesseln.
//  3) Wrap unter KEK (CKM_AES_KEY_WRAP_PAD).
//  4) Original zerstoeren.
//  5) Unwrap → neuer Session-Key (anderer Handle, gleicher Inhalt).
//  6) Decrypt + Verify.

const byte KEK_ID = 0x06;

var modulePath = Env("PKCS11_MODULE", "/usr/lib/softhsm/libsofthsm2.so");
var tokenLabel = Env("PKCS11_TOKEN_LABEL", "dev-token");
var pin = Env("PKCS11_USER_PIN", "987654");
var outputDir = Env("PKCS11_OUTPUT_DIR", "/workspace/lab/work");

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
    var kek = FindKey(session, factories, CKO.CKO_SECRET_KEY, new byte[] { KEK_ID });

    // 1) Frischen AES-Session-Key erzeugen.
    var dataKey = GenerateExtractableAes(session, factories, 32);

    // 2) Test-Payload verschluesseln.
    var payload = Encoding.UTF8.GetBytes("Vertrauliches Material aus C#.\nMuss nach Restore lesbar bleiben.\n");
    var iv = RandomNumberGenerator.GetBytes(16);
    using var cbcMech = factories.MechanismFactory.Create(CKM.CKM_AES_CBC_PAD, iv);
    var ciphertext = session.Encrypt(cbcMech, dataKey, payload);

    // 3) Wrap.
    using var wrapMech = factories.MechanismFactory.Create(CKM.CKM_AES_KEY_WRAP_PAD);
    var wrapped = session.WrapKey(wrapMech, kek, dataKey);

    Directory.CreateDirectory(outputDir);
    var wrapPath = Path.Combine(outputDir, "csharp-wrap-backup.bin");
    File.WriteAllBytes(wrapPath, wrapped);

    // 4) Original zerstoeren.
    session.DestroyObject(dataKey);

    // 5) Unwrap. Minimal-Template ohne CKA_VALUE_LEN — Laenge folgt aus Blob.
    var unwrapTpl = new List<IObjectAttribute>
    {
        factories.ObjectAttributeFactory.Create(CKA.CKA_CLASS, CKO.CKO_SECRET_KEY),
        factories.ObjectAttributeFactory.Create(CKA.CKA_KEY_TYPE, CKK.CKK_AES),
        factories.ObjectAttributeFactory.Create(CKA.CKA_TOKEN, false),
        factories.ObjectAttributeFactory.Create(CKA.CKA_ENCRYPT, true),
        factories.ObjectAttributeFactory.Create(CKA.CKA_DECRYPT, true),
        factories.ObjectAttributeFactory.Create(CKA.CKA_LABEL, "csharp-restored-payload"),
    };
    var restored = session.UnwrapKey(wrapMech, kek, wrapped, unwrapTpl);

    // 6) Decrypt mit restored.
    using var cbcMechDec = factories.MechanismFactory.Create(CKM.CKM_AES_CBC_PAD, iv);
    var recovered = session.Decrypt(cbcMechDec, restored, ciphertext);

    if (!payload.SequenceEqual(recovered))
    {
        Console.Error.WriteLine("Round-Trip FEHLGESCHLAGEN.");
        Environment.Exit(3);
    }

    Console.WriteLine("--- Wrap/Unwrap Round-Trip ---");
    Console.WriteLine($"Original payload-key:    handle {dataKey.ObjectId} (geloescht nach Wrap)");
    Console.WriteLine($"Restored payload-key:    handle {restored.ObjectId} (neue Identitaet, gleiches Material)");
    Console.WriteLine($"Wrap-Blob:               {wrapPath} ({wrapped.Length} Bytes)");
    Console.WriteLine($"Original-Klartext:       {payload.Length} Bytes");
    Console.WriteLine($"Nach Decrypt mit Restore: {recovered.Length} Bytes — match.");
    Console.WriteLine("Round-Trip: OK");
}
finally
{
    session.Logout();
}

static IObjectHandle GenerateExtractableAes(ISession session, Pkcs11InteropFactories factories, ulong valueLen)
{
    var tpl = new List<IObjectAttribute>
    {
        factories.ObjectAttributeFactory.Create(CKA.CKA_CLASS, CKO.CKO_SECRET_KEY),
        factories.ObjectAttributeFactory.Create(CKA.CKA_KEY_TYPE, CKK.CKK_AES),
        factories.ObjectAttributeFactory.Create(CKA.CKA_VALUE_LEN, valueLen),
        factories.ObjectAttributeFactory.Create(CKA.CKA_TOKEN, false),
        factories.ObjectAttributeFactory.Create(CKA.CKA_SENSITIVE, true),
        factories.ObjectAttributeFactory.Create(CKA.CKA_EXTRACTABLE, true),
        factories.ObjectAttributeFactory.Create(CKA.CKA_ENCRYPT, true),
        factories.ObjectAttributeFactory.Create(CKA.CKA_DECRYPT, true),
        factories.ObjectAttributeFactory.Create(CKA.CKA_LABEL, "csharp-payload-key"),
    };
    using var mech = factories.MechanismFactory.Create(CKM.CKM_AES_KEY_GEN);
    return session.GenerateKey(mech, tpl);
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
