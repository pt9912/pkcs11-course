using System.Security.Cryptography;
using System.Text;
using Net.Pkcs11Interop.Common;
using Net.Pkcs11Interop.HighLevelAPI;

// ECDH + HKDF Demo, Pkcs11Interop-Pfad. Logisch identisch zum Go-Demo:
//  1) Alice + Bob EC-P256 Keypaare im selben Token.
//  2) C_DeriveKey(CKM_ECDH1_DERIVE, kdf=CKD_NULL) auf beiden Seiten.
//  3) Shared Secret extrahieren (CKA_EXTRACTABLE=true im Derive-Template) und vergleichen.
//  4) HKDF-SHA256 host-side via System.Security.Cryptography.HKDF.
//  5) AES-256-GCM Roundtrip.

var modulePath = Env("PKCS11_MODULE", "/usr/lib/softhsm/libsofthsm2.so");
var tokenLabel = Env("PKCS11_TOKEN_LABEL", "dev-token");
var pin = Env("PKCS11_USER_PIN", "987654");
var aliceLabel = Env("PKCS11_ECDH_ALICE_LABEL", "alice-ec-key");
var bobLabel = Env("PKCS11_ECDH_BOB_LABEL", "bob-ec-key");
var kdfMode = Env("PKCS11_ECDH_KDF", "hkdf");
var hkdfInfo = Encoding.UTF8.GetBytes("ECDH-Lab-V1");

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
    var alicePriv = FindKeyByLabel(session, factories, CKO.CKO_PRIVATE_KEY, aliceLabel);
    var bobPriv = FindKeyByLabel(session, factories, CKO.CKO_PRIVATE_KEY, bobLabel);
    var alicePubPoint = ReadEcPoint(session, factories, aliceLabel);
    var bobPubPoint = ReadEcPoint(session, factories, bobLabel);

    Console.WriteLine("=== 1) Setup ===");
    Console.WriteLine($"  Alice priv-handle  pub-point={HexShort(alicePubPoint, 8)}...");
    Console.WriteLine($"  Bob   priv-handle  pub-point={HexShort(bobPubPoint, 8)}...");

    Console.WriteLine("\n=== 2) ECDH-Derive (CKM_ECDH1_DERIVE, kdf=CKD_NULL) ===");
    var aliceSecret = DeriveEcdh(session, factories, alicePriv, bobPubPoint);
    var bobSecret = DeriveEcdh(session, factories, bobPriv, alicePubPoint);
    if (!aliceSecret.AsSpan().SequenceEqual(bobSecret))
    {
        Console.Error.WriteLine("Shared Secrets unterscheiden sich — ECDH kaputt.");
        Environment.Exit(3);
    }
    Console.WriteLine($"  Alice-Secret: {HexShort(aliceSecret, 8)}... ({aliceSecret.Length} Byte)");
    Console.WriteLine($"  Bob-Secret:   {HexShort(bobSecret, 8)}... ({bobSecret.Length} Byte)");
    Console.WriteLine("  Match: ja  (P-256 x-Koordinate, byte-identisch)");

    byte[] aliceKey, bobKey;
    if (kdfMode.Equals("raw", StringComparison.OrdinalIgnoreCase))
    {
        Console.WriteLine("\n=== 3) KDF=raw — Shared Secret direkt als AES-256-Key ===");
        aliceKey = aliceSecret[..32];
        bobKey = bobSecret[..32];
        Console.WriteLine("  Kein RFC-5869-Standard, fuer Demo aber valider AES-256-Schluessel.");
    }
    else
    {
        Console.WriteLine("\n=== 3) KDF=hkdf — HKDF-SHA256 host-side (RFC 5869) ===");
        Console.WriteLine("  info=\"ECDH-Lab-V1\"  salt=zero  CKM_HKDF_DERIVE nicht in SoftHSM 2.6");
        aliceKey = HKDF.DeriveKey(HashAlgorithmName.SHA256, aliceSecret, 32, null, hkdfInfo);
        bobKey = HKDF.DeriveKey(HashAlgorithmName.SHA256, bobSecret, 32, null, hkdfInfo);
    }
    if (!aliceKey.AsSpan().SequenceEqual(bobKey))
    {
        Console.Error.WriteLine("KDF-Output unterscheidet sich.");
        Environment.Exit(3);
    }
    Console.WriteLine($"  AES-Key (gekuerzt): {HexShort(aliceKey, 8)}...");

    Console.WriteLine("\n=== 4) AES-256-GCM Roundtrip ===");
    var plaintext = Encoding.UTF8.GetBytes("Hallo Bob — diese Nachricht kommt von Alice ueber ECDH+HKDF (C#).");
    var (ciphertext, nonce, tag) = AesGcmSeal(aliceKey, plaintext);
    var recovered = AesGcmOpen(bobKey, nonce, ciphertext, tag);
    Console.WriteLine($"  Alice -> {ciphertext.Length} Byte Ciphertext + 12 Byte Nonce + 16 Byte Tag");
    Console.WriteLine($"  Bob   <- entschluesselt: {Encoding.UTF8.GetString(recovered)}");
    if (!recovered.AsSpan().SequenceEqual(plaintext))
    {
        Console.Error.WriteLine("Roundtrip kaputt.");
        Environment.Exit(3);
    }
    Console.WriteLine("\nFertig — Shared-Secret-Match-Beweis erbracht und AES-Roundtrip erfolgreich.");
}
finally
{
    session.Logout();
}

static byte[] DeriveEcdh(ISession session, Pkcs11InteropFactories factories, IObjectHandle myPriv, byte[] peerPoint)
{
    // CK_ECDH1_DERIVE_PARAMS: kdf, ulSharedDataLen, pSharedData, ulPublicDataLen, pPublicData.
    // Pkcs11Interop kapselt das in MechanismParamsFactory.CreateCkEcdh1DeriveParams.
    using var ckmParams = factories.MechanismParamsFactory.CreateCkEcdh1DeriveParams(
        (ulong)CKD.CKD_NULL, null, peerPoint);
    using var mech = factories.MechanismFactory.Create(CKM.CKM_ECDH1_DERIVE, ckmParams);

    var template = new List<IObjectAttribute>
    {
        factories.ObjectAttributeFactory.Create(CKA.CKA_CLASS, CKO.CKO_SECRET_KEY),
        factories.ObjectAttributeFactory.Create(CKA.CKA_KEY_TYPE, CKK.CKK_GENERIC_SECRET),
        factories.ObjectAttributeFactory.Create(CKA.CKA_TOKEN, false),
        factories.ObjectAttributeFactory.Create(CKA.CKA_SENSITIVE, false),
        factories.ObjectAttributeFactory.Create(CKA.CKA_EXTRACTABLE, true),
        factories.ObjectAttributeFactory.Create(CKA.CKA_VALUE_LEN, (ulong)32),
    };
    var derived = session.DeriveKey(mech, myPriv, template);
    try
    {
        var attrs = session.GetAttributeValue(derived, new List<CKA> { CKA.CKA_VALUE });
        return attrs[0].GetValueAsByteArray();
    }
    finally
    {
        session.DestroyObject(derived);
    }
}

static byte[] ReadEcPoint(ISession session, Pkcs11InteropFactories factories, string label)
{
    var template = new List<IObjectAttribute>
    {
        factories.ObjectAttributeFactory.Create(CKA.CKA_CLASS, CKO.CKO_PUBLIC_KEY),
        factories.ObjectAttributeFactory.Create(CKA.CKA_LABEL, label),
    };
    var objects = session.FindAllObjects(template);
    if (objects.Count == 0)
    {
        throw new InvalidOperationException($"Kein Public Key mit Label '{label}' gefunden.");
    }
    var attrs = session.GetAttributeValue(objects[0], new List<CKA> { CKA.CKA_EC_POINT });
    return attrs[0].GetValueAsByteArray();
}

static IObjectHandle FindKeyByLabel(ISession session, Pkcs11InteropFactories factories, CKO classType, string label)
{
    var template = new List<IObjectAttribute>
    {
        factories.ObjectAttributeFactory.Create(CKA.CKA_CLASS, classType),
        factories.ObjectAttributeFactory.Create(CKA.CKA_LABEL, label),
    };
    var objects = session.FindAllObjects(template);
    if (objects.Count == 0)
    {
        throw new InvalidOperationException($"Kein Objekt class={classType} label='{label}' gefunden (make gen-ecdh-keys?).");
    }
    return objects[0];
}

static (byte[] Ciphertext, byte[] Nonce, byte[] Tag) AesGcmSeal(byte[] key, byte[] plaintext)
{
    var nonce = RandomNumberGenerator.GetBytes(12);
    var ciphertext = new byte[plaintext.Length];
    var tag = new byte[16];
    using var gcm = new AesGcm(key, tag.Length);
    gcm.Encrypt(nonce, plaintext, ciphertext, tag);
    return (ciphertext, nonce, tag);
}

static byte[] AesGcmOpen(byte[] key, byte[] nonce, byte[] ciphertext, byte[] tag)
{
    var plaintext = new byte[ciphertext.Length];
    using var gcm = new AesGcm(key, tag.Length);
    gcm.Decrypt(nonce, ciphertext, tag, plaintext);
    return plaintext;
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

static string HexShort(byte[] data, int count)
{
    var take = Math.Min(count, data.Length);
    return Convert.ToHexString(data, 0, take).ToLowerInvariant();
}

static string Env(string name, string fallback)
{
    var value = Environment.GetEnvironmentVariable(name);
    return string.IsNullOrWhiteSpace(value) ? fallback : value;
}
