using System.Text;
using Net.Pkcs11Interop.Common;
using Net.Pkcs11Interop.HighLevelAPI;
using Org.BouncyCastle.Asn1;
using Org.BouncyCastle.Asn1.Pkcs;
using Org.BouncyCastle.Asn1.X509;
using Org.BouncyCastle.Cms;
using Org.BouncyCastle.Crypto;
using Org.BouncyCastle.Utilities.Collections;
using Org.BouncyCastle.X509;

// CMS/PKCS#7 — detached SignedData mit HSM-Key.
//
// .NETs System.Security.Cryptography.Pkcs.SignedCms verlangt auf Linux (OpenSSL-Backend)
// via X509Certificate2.CopyWithPrivateKey(RSA) eine RSA-Instanz, die ExportParameters(true)
// inklusive konsistenter p, q, d, dp, dq, qInv liefert — OpenSSL prueft n = p*q.
// Mit einem nicht-extractable HSM-Key geht das prinzipiell nicht.
//
// Loesung wie im Java/Kotlin-Pfad: BouncyCastle.Cryptography. CmsSignedDataGenerator
// nimmt eine ISignatureFactory entgegen, die nur die rohe Signatur liefern muss —
// die DigestInfo-Verpackung uebernimmt das Token via CKM_SHA256_RSA_PKCS.

var modulePath = Env("PKCS11_MODULE", "/usr/lib/softhsm/libsofthsm2.so");
var tokenLabel = Env("PKCS11_TOKEN_LABEL", "dev-token");
var pin = Env("PKCS11_USER_PIN", "987654");
var outputDir = Env("PKCS11_OUTPUT_DIR", "/workspace/lab/work");
var certPath = Env("PKCS11_CERT_PATH", Path.Combine(outputDir, "cert.pem"));
var keyId = new byte[] { 0x01 };
var content = Encoding.UTF8.GetBytes("Vertrag XYZ vom 30.05.2026\nUnterzeichnender: C#-CMS-Demo\n");

if (!File.Exists(certPath))
{
    Console.Error.WriteLine($"Cert fehlt: {certPath}. Erst 'make import-cert' ausfuehren.");
    Environment.Exit(2);
}
X509Certificate bcCert;
using (var fs = File.OpenRead(certPath))
{
    bcCert = new X509CertificateParser().ReadCertificate(fs);
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

byte[] der;
try
{
    var privKey = FindKey(session, factories, CKO.CKO_PRIVATE_KEY, keyId);

    // Callback: bekommt die DER-codierten SignedAttributes (raw, nicht gehasht)
    // und liefert eine sha256WithRSAEncryption-Signatur. CKM_SHA256_RSA_PKCS
    // laesst das Token hashen, DigestInfo packen und PKCS#1 v1.5 padden.
    byte[] HsmSign(byte[] toBeSigned)
    {
        using var mech = factories.MechanismFactory.Create(CKM.CKM_SHA256_RSA_PKCS);
        return session.Sign(mech, privKey, toBeSigned);
    }

    var sigFactory = new ExternalRsaSha256SignatureFactory(HsmSign);
    var signerInfoGen = new SignerInfoGeneratorBuilder().Build(sigFactory, bcCert);

    var cmsGen = new CmsSignedDataGenerator();
    cmsGen.AddSignerInfoGenerator(signerInfoGen);
    cmsGen.AddCertificates(CollectionUtilities.CreateStore(new[] { bcCert }));

    var signedData = cmsGen.Generate(new CmsProcessableByteArray(content), encapsulate: false);
    der = signedData.GetEncoded();

    // Sofort gegenpruefen via BC.
    var parsed = new CmsSignedData(new CmsProcessableByteArray(content), der);
    var signers = parsed.GetSignerInfos();
    foreach (SignerInformation si in signers.GetSigners())
    {
        if (!si.Verify(bcCert.GetPublicKey()))
        {
            Console.Error.WriteLine("Self-Verify fehlgeschlagen.");
            Environment.Exit(3);
        }
    }
}
finally
{
    session.Logout();
}

Directory.CreateDirectory(outputDir);
var contentPath = Path.Combine(outputDir, "csharp-cms-document.txt");
var sigPath = Path.Combine(outputDir, "csharp-cms-document.p7s");
File.WriteAllBytes(contentPath, content);
File.WriteAllBytes(sigPath, der);

Console.WriteLine($"Token:          {tokenLabel}");
Console.WriteLine($"Signer-Cert:    {certPath} (Subject: {bcCert.SubjectDN})");
Console.WriteLine($"Klartext:       {contentPath} ({content.Length} Bytes)");
Console.WriteLine($"CMS-Signatur:   {sigPath} ({der.Length} Bytes, detached)");
Console.WriteLine("Self-Verify:    OK");

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

// ExternalRsaSha256SignatureFactory: ISignatureFactory, die das Signing an einen
// externen Callback weiterreicht. BC speist die DER-codierten SignedAttributes in
// den IStreamCalculator; GetResult() ruft den Callback mit dem gepufferten Inhalt.
sealed class ExternalRsaSha256SignatureFactory : ISignatureFactory
{
    private readonly AlgorithmIdentifier algId = new(
        PkcsObjectIdentifiers.Sha256WithRsaEncryption,
        DerNull.Instance);
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
