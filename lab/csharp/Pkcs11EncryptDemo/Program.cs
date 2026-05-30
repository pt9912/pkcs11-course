using System.Security.Cryptography;
using System.Text;
using Net.Pkcs11Interop.Common;
using Net.Pkcs11Interop.HighLevelAPI;
using Net.Pkcs11Interop.HighLevelAPI.MechanismParams;

// Hybrid-Verschluesselung:
// 1) AES-Session-Key + IV als Host-Material erzeugen.
// 2) Sender wrappt den AES-Key per RSA-OAEP via Pkcs11Interop (Public Key).
// 3) Sender verschluesselt das Dokument mit AES-256-GCM (Host).
// 4) Empfaenger unwrappt den AES-Key per RSA-OAEP via Pkcs11Interop (Private Key).
// 5) Empfaenger entschluesselt das Dokument mit AES-256-GCM.
//
// OAEP-Hash: SHA-1 statt SHA-256.
// Grund: SoftHSM 2.6.x liefert beim direkten Aufruf von C_EncryptInit/C_DecryptInit
// mit CKM_RSA_PKCS_OAEP und hashAlg=CKM_SHA256 ein CKR_ARGUMENTS_BAD zurueck.
// Real-world HSMs koennen SHA-256 OAEP problemlos. Siehe course/13-verschluesselung.md.

var modulePath = Env("PKCS11_MODULE", "/usr/lib/softhsm/libsofthsm2.so");
var tokenLabel = Env("PKCS11_TOKEN_LABEL", "dev-token");
var pin = Env("PKCS11_USER_PIN", "987654");
var outputDir = Env("PKCS11_OUTPUT_DIR", "/workspace/lab/work");
var wrapKeyId = new byte[] { 0x03 };
var data = Encoding.UTF8.GetBytes("Vertrauliches Dokument aus C#.\nZeile zwei.\n");

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

byte[] wrapped;
byte[] ciphertext;
byte[] iv;
try
{
    var pubKey = FindKey(session, factories, CKO.CKO_PUBLIC_KEY, wrapKeyId);
    var privKey = FindKey(session, factories, CKO.CKO_PRIVATE_KEY, wrapKeyId);

    var aesKey = RandomNumberGenerator.GetBytes(32);
    iv = RandomNumberGenerator.GetBytes(12);

    // OAEP-Parameter: SHA-1, MGF1-SHA1, kein Label.
    using var oaepParams = factories.MechanismParamsFactory.CreateCkRsaPkcsOaepParams(
        (ulong)CKM.CKM_SHA_1,
        (ulong)CKG.CKG_MGF1_SHA1,
        (ulong)CKZ.CKZ_DATA_SPECIFIED,
        null);
    using var oaepMech = factories.MechanismFactory.Create(CKM.CKM_RSA_PKCS_OAEP, oaepParams);

    wrapped = session.Encrypt(oaepMech, pubKey, aesKey);
    ciphertext = AesGcmEncrypt(aesKey, iv, data);
    Array.Clear(aesKey, 0, aesKey.Length);

    var recoveredKey = session.Decrypt(oaepMech, privKey, wrapped);
    var recovered = AesGcmDecrypt(recoveredKey, iv, ciphertext);
    Array.Clear(recoveredKey, 0, recoveredKey.Length);

    if (!data.SequenceEqual(recovered))
    {
        Console.Error.WriteLine("Round-Trip fehlgeschlagen.");
        Environment.Exit(3);
    }
}
finally
{
    session.Logout();
}

Directory.CreateDirectory(outputDir);
File.WriteAllBytes(Path.Combine(outputDir, "csharp-document.txt"), data);
File.WriteAllBytes(Path.Combine(outputDir, "csharp-wrapped-key.bin"), wrapped);
File.WriteAllBytes(Path.Combine(outputDir, "csharp-iv.bin"), iv);
File.WriteAllBytes(Path.Combine(outputDir, "csharp-document.enc"), ciphertext);

Console.WriteLine($"Token:        {tokenLabel}");
Console.WriteLine($"Wrap-Key-ID:  03");
Console.WriteLine($"Wrapped Key:  {Path.Combine(outputDir, "csharp-wrapped-key.bin")} ({wrapped.Length} Bytes)");
Console.WriteLine($"Ciphertext:   {Path.Combine(outputDir, "csharp-document.enc")} ({ciphertext.Length} Bytes inkl. GCM-Tag)");
Console.WriteLine("OAEP-Hash:    SHA-1 (SoftHSM-Quirk)");
Console.WriteLine("Round-Trip:   OK");

static byte[] AesGcmEncrypt(byte[] key, byte[] iv, byte[] plaintext)
{
    var ciphertext = new byte[plaintext.Length];
    var tag = new byte[16];
    using var aesgcm = new AesGcm(key, tag.Length);
    aesgcm.Encrypt(iv, plaintext, ciphertext, tag);
    // Tag an Ciphertext anhaengen — selbes Layout wie AESGCM.encrypt in Python/Java.
    var combined = new byte[ciphertext.Length + tag.Length];
    Buffer.BlockCopy(ciphertext, 0, combined, 0, ciphertext.Length);
    Buffer.BlockCopy(tag, 0, combined, ciphertext.Length, tag.Length);
    return combined;
}

static byte[] AesGcmDecrypt(byte[] key, byte[] iv, byte[] combined)
{
    const int tagLen = 16;
    if (combined.Length < tagLen)
    {
        throw new ArgumentException("Ciphertext zu kurz fuer GCM-Tag");
    }
    var ciphertext = new byte[combined.Length - tagLen];
    var tag = new byte[tagLen];
    Buffer.BlockCopy(combined, 0, ciphertext, 0, ciphertext.Length);
    Buffer.BlockCopy(combined, ciphertext.Length, tag, 0, tagLen);
    var plaintext = new byte[ciphertext.Length];
    using var aesgcm = new AesGcm(key, tagLen);
    aesgcm.Decrypt(iv, ciphertext, tag, plaintext);
    return plaintext;
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

static IObjectHandle FindKey(ISession session, Pkcs11InteropFactories factories, CKO classType, byte[] keyId)
{
    var template = new List<IObjectAttribute>
    {
        factories.ObjectAttributeFactory.Create(CKA.CKA_CLASS, classType),
        factories.ObjectAttributeFactory.Create(CKA.CKA_ID, keyId)
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
