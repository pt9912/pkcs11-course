using System.Text;
using Net.Pkcs11Interop.Common;
using Net.Pkcs11Interop.HighLevelAPI;
using Org.BouncyCastle.Asn1;
using Org.BouncyCastle.Asn1.Pkcs;
using Org.BouncyCastle.Asn1.X509;
using Org.BouncyCastle.Asn1.X9;
using Org.BouncyCastle.Crypto;
using Org.BouncyCastle.Crypto.Parameters;
using Org.BouncyCastle.Math;
using Org.BouncyCastle.Pkcs;

// CSR-Generierung mit HSM-Signing-Key. Bauen wir analog zum CMS-Demo:
//   - Pkcs10CertificationRequest nimmt einen ISignatureFactory.
//   - Unsere ExternalRsaSha256SignatureFactory routet das eigentliche Signing
//     in einen session.Sign(CKM_SHA256_RSA_PKCS)-Aufruf am HSM.
//   - Pubkey lesen wir aus CKA_MODULUS + CKA_PUBLIC_EXPONENT und bauen daraus
//     einen RsaKeyParameters fuer BC.

var modulePath = Env("PKCS11_MODULE", "/usr/lib/softhsm/libsofthsm2.so");
var tokenLabel = Env("PKCS11_TOKEN_LABEL", "dev-token");
var pin = Env("PKCS11_USER_PIN", "987654");
var outputDir = Env("PKCS11_OUTPUT_DIR", "/workspace/lab/work");
var keyId = new byte[] { 0x01 };

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
    var privKey = FindKey(session, factories, CKO.CKO_PRIVATE_KEY, keyId);
    var pubKeyParams = LoadRsaPublicKey(session, factories, keyId);

    byte[] HsmSign(byte[] tbs)
    {
        using var mech = factories.MechanismFactory.Create(CKM.CKM_SHA256_RSA_PKCS);
        return session.Sign(mech, privKey, tbs);
    }

    var subject = new X509Name("CN=csharp-app.example.org, O=PKCS11 Lab");
    var sigFactory = new ExternalRsaSha256SignatureFactory(HsmSign);

    // SAN- und KeyUsage-Extensions in einen extensionRequest-Attribut (RFC 2985).
    // X509ExtensionsGenerator kapselt die ASN.1-Konstruktion sauberer als
    // der direkte X509Extension-Konstruktor (der erwartet DerBoolean +
    // Asn1OctetString und ist unangenehm zu fuettern).
    var extGen = new X509ExtensionsGenerator();
    extGen.AddExtension(X509Extensions.SubjectAlternativeName, critical: false,
        new GeneralNames(new GeneralName(GeneralName.DnsName, "csharp-app.example.org")));
    extGen.AddExtension(X509Extensions.KeyUsage, critical: true,
        new KeyUsage(KeyUsage.DigitalSignature));
    var attrSet = new DerSet(new AttributePkcs(
        PkcsObjectIdentifiers.Pkcs9AtExtensionRequest,
        new DerSet(extGen.Generate())));

    var csr = new Pkcs10CertificationRequest(
        sigFactory,
        subject,
        pubKeyParams,
        attrSet);

    // Self-verify: Signatur vs eingebetteter Pubkey.
    if (!csr.Verify())
    {
        Console.Error.WriteLine("CSR-Selbstsignatur ungueltig.");
        Environment.Exit(3);
    }

    Directory.CreateDirectory(outputDir);
    var csrPath = Path.Combine(outputDir, "csharp-app.csr");
    var pem = "-----BEGIN CERTIFICATE REQUEST-----\n" +
              Convert.ToBase64String(csr.GetEncoded(), Base64FormattingOptions.InsertLineBreaks) +
              "\n-----END CERTIFICATE REQUEST-----\n";
    File.WriteAllText(csrPath, pem);

    Console.WriteLine("--- CSR-Generierung ---");
    Console.WriteLine($"Subject:      {subject}");
    Console.WriteLine($"DNS-SAN:      csharp-app.example.org");
    Console.WriteLine($"SignAlgo:     sha256WithRSAEncryption");
    Console.WriteLine($"CSR:          {csrPath} ({pem.Length} Bytes PEM)");
    Console.WriteLine("Selbstsignatur (PoP): OK");
}
finally
{
    session.Logout();
}

static RsaKeyParameters LoadRsaPublicKey(ISession session, Pkcs11InteropFactories factories, byte[] keyId)
{
    var pubHandle = FindKey(session, factories, CKO.CKO_PUBLIC_KEY, keyId);
    var attrTypes = new List<CKA> { CKA.CKA_MODULUS, CKA.CKA_PUBLIC_EXPONENT };
    var attrs = session.GetAttributeValue(pubHandle, attrTypes);
    BigInteger? modulus = null;
    BigInteger? exponent = null;
    foreach (var a in attrs)
    {
        var bytes = a.GetValueAsByteArray();
        var bi = new BigInteger(1, bytes);
        if (a.Type == (ulong)CKA.CKA_MODULUS) modulus = bi;
        if (a.Type == (ulong)CKA.CKA_PUBLIC_EXPONENT) exponent = bi;
    }
    if (modulus is null || exponent is null)
    {
        throw new InvalidOperationException("RSA-Pubkey-Attribute fehlen.");
    }
    return new RsaKeyParameters(false, modulus, exponent);
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

// ExternalRsaSha256SignatureFactory — identisches Pattern wie im CMS-Demo.
sealed class ExternalRsaSha256SignatureFactory : ISignatureFactory
{
    private readonly AlgorithmIdentifier algId = new(
        PkcsObjectIdentifiers.Sha256WithRsaEncryption, DerNull.Instance);
    private readonly Func<byte[], byte[]> signCallback;

    public ExternalRsaSha256SignatureFactory(Func<byte[], byte[]> signCallback)
    {
        this.signCallback = signCallback ?? throw new ArgumentNullException(nameof(signCallback));
    }

    public object AlgorithmDetails => algId;

    public IStreamCalculator<IBlockResult> CreateCalculator() =>
        new BufferedStreamCalculator(signCallback);

    private sealed class BufferedStreamCalculator : IStreamCalculator<IBlockResult>
    {
        private readonly MemoryStream buffer = new();
        private readonly Func<byte[], byte[]> signCallback;

        public BufferedStreamCalculator(Func<byte[], byte[]> signCallback)
        {
            this.signCallback = signCallback;
        }

        public Stream Stream => buffer;
        public IBlockResult GetResult() => new ByteArrayBlockResult(signCallback(buffer.ToArray()));
    }

    private sealed class ByteArrayBlockResult : IBlockResult
    {
        private readonly byte[] result;
        public ByteArrayBlockResult(byte[] result) => this.result = result;
        public int Length => result.Length;
        public int GetMaxResultLength() => result.Length;
        public byte[] Collect() => (byte[])result.Clone();
        public int Collect(byte[] destination, int offset)
        {
            Array.Copy(result, 0, destination, offset, result.Length);
            return result.Length;
        }
        public int Collect(Span<byte> destination)
        {
            result.AsSpan().CopyTo(destination);
            return result.Length;
        }
    }
}
