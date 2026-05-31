using System.Net.Http;
using System.Security.Cryptography;
using System.Text;
using Net.Pkcs11Interop.Common;
using Net.Pkcs11Interop.HighLevelAPI;
using Org.BouncyCastle.Asn1;
using Org.BouncyCastle.Asn1.Cms;
using Org.BouncyCastle.Asn1.Pkcs;
using Org.BouncyCastle.Asn1.X509;
using Org.BouncyCastle.Cms;
using Org.BouncyCastle.Crypto;
using Org.BouncyCastle.Tsp;
using Org.BouncyCastle.Utilities.Collections;
using Org.BouncyCastle.X509;

// CAdES-T-aequivalenter Flow:
//   1) CMS via Pkcs11Interop + BouncyCastle (Signing-Operation im HSM).
//   2) SHA-256 ueber SignerInfo.Signature an die TSA POSTen.
//   3) TimeStampToken als unsignedAttribute.signatureTimeStampToken einbauen.
//   4) Verifikation: CMS-Signatur + TS-Hash + TSA-Signatur-Chain.

var modulePath = Env("PKCS11_MODULE", "/usr/lib/softhsm/libsofthsm2.so");
var tokenLabel = Env("PKCS11_TOKEN_LABEL", "dev-token");
var pin = Env("PKCS11_USER_PIN", "987654");
var outputDir = Env("PKCS11_OUTPUT_DIR", "/workspace/lab/work");
var certPath = Env("PKCS11_CERT_PATH", Path.Combine(outputDir, "cert.pem"));
var tsaUrl = Env("PKCS11_TSA_URL", "http://127.0.0.1:8088/");
var keyId = new byte[] { 0x01 };
var content = Encoding.UTF8.GetBytes("Lab-Dokument fuer CMS+TSA-Roundtrip (C#).\n");

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

byte[] cmsBytes;
try
{
    var privKey = FindKey(session, factories, CKO.CKO_PRIVATE_KEY, keyId);

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

    var cms = cmsGen.Generate(new CmsProcessableByteArray(content), encapsulate: false);
    cmsBytes = cms.GetEncoded();
    Console.WriteLine("=== 1) CMS-Signatur (detached, SHA256withRSA) ===");
    Console.WriteLine($"  Signer:    {bcCert.SubjectDN}");
    Console.WriteLine($"  CMS-Blob:  {cmsBytes.Length} Byte");

    // === 2) TimeStampReq erzeugen + POST ===
    var signerInfo = cms.GetSignerInfos().GetSigners().Cast<SignerInformation>().First();
    var signatureValue = signerInfo.GetSignature();
    // Statische SHA256.HashData (.NET 5+) ist threadsicher und braucht keinen
    // Reset-State zwischen Aufrufen — sauberer als ein gemeinsames SHA256-Objekt.
    var sigHash = SHA256.HashData(signatureValue);

    var reqGen = new TimeStampRequestGenerator();
    reqGen.SetCertReq(true);
    var tsReq = reqGen.Generate(TspAlgorithms.Sha256, sigHash, Org.BouncyCastle.Math.BigInteger.ValueOf(RandomNumberGenerator.GetInt32(int.MaxValue)));
    var tsReqBytes = tsReq.GetEncoded();
    Console.WriteLine("\n=== 2) TimeStampReq erzeugen + POST an TSA ===");
    Console.WriteLine($"  TSA-URL:  {tsaUrl}");
    Console.WriteLine($"  TSReq:    {tsReqBytes.Length} Byte (SHA-256 ueber SignerInfo.signature)");

    using var http = new HttpClient();
    var httpReq = new HttpRequestMessage(HttpMethod.Post, tsaUrl)
    {
        Content = new ByteArrayContent(tsReqBytes),
    };
    httpReq.Content.Headers.ContentType = new System.Net.Http.Headers.MediaTypeHeaderValue("application/timestamp-query");
    var httpResp = await http.SendAsync(httpReq);
    if (!httpResp.IsSuccessStatusCode)
    {
        Console.Error.WriteLine($"TSA HTTP {(int)httpResp.StatusCode}");
        Environment.Exit(3);
    }
    var tsRespBytes = await httpResp.Content.ReadAsByteArrayAsync();
    var tsResp = new TimeStampResponse(tsRespBytes);
    tsResp.Validate(tsReq);
    var tsToken = tsResp.TimeStampToken ?? throw new Exception($"TSA-Status: {tsResp.GetStatusString()}");
    Console.WriteLine($"  TSToken:  {tsToken.GetEncoded().Length} Byte");
    Console.WriteLine($"  Gen-Time: {tsToken.TimeStampInfo.GenTime}");

    // === 3) TSToken als unsignedAttribute.signatureTimeStampToken einbauen ===
    var tsAttr = new Org.BouncyCastle.Asn1.Cms.Attribute(
        PkcsObjectIdentifiers.IdAASignatureTimeStampToken,
        new DerSet(Asn1Object.FromByteArray(tsToken.GetEncoded())));
    var unsigned = new Org.BouncyCastle.Asn1.Cms.AttributeTable(new Asn1EncodableVector { tsAttr });
    var withTimestamp = SignerInformation.ReplaceUnsignedAttributes(signerInfo, unsigned);
    var cmsT = CmsSignedData.ReplaceSigners(cms, new SignerInformationStore(new[] { withTimestamp }));

    Directory.CreateDirectory(outputDir);
    var outPath = Path.Combine(outputDir, "csharp-cms-tsa.p7s");
    File.WriteAllBytes(outPath, cmsT.GetEncoded());
    Console.WriteLine("\n=== 3) CMS+TSA-Signatur geschrieben ===");
    Console.WriteLine($"  Datei:    {outPath}");
    Console.WriteLine($"  Groesse:  {cmsT.GetEncoded().Length} Byte (Plain-CMS war {cmsBytes.Length})");

    // === 4) Verifikation ===
    Console.WriteLine("\n=== 4) Verifikation ===");
    var parsed = new CmsSignedData(new CmsProcessableByteArray(content), File.ReadAllBytes(outPath));
    var parsedSigner = parsed.GetSignerInfos().GetSigners().Cast<SignerInformation>().First();
    var cmsOk = parsedSigner.Verify(bcCert.GetPublicKey());
    Console.WriteLine($"  CMS-Signatur:    {(cmsOk ? "OK" : "FAIL")}");

    var tsBack = parsedSigner.UnsignedAttributes[PkcsObjectIdentifiers.IdAASignatureTimeStampToken];
    var tsBackToken = new TimeStampToken(
        new CmsSignedData(tsBack.AttrValues[0].ToAsn1Object().GetEncoded()));
    using var tsaFs = File.OpenRead(Path.Combine(outputDir, "tsa-cert.pem"));
    var tsaCert = new X509CertificateParser().ReadCertificate(tsaFs);
    tsBackToken.Validate(tsaCert);
    var expectedHash = SHA256.HashData(parsedSigner.GetSignature());
    var tsHashOk = expectedHash.AsSpan().SequenceEqual(tsBackToken.TimeStampInfo.GetMessageImprintDigest());
    Console.WriteLine($"  Timestamp-Hash:  {(tsHashOk ? "OK" : "FAIL")}");
    Console.WriteLine($"  Timestamp-Zeit:  {tsBackToken.TimeStampInfo.GenTime}");
    Console.WriteLine($"  TSA-Subject:     {tsaCert.SubjectDN}");

    if (!cmsOk || !tsHashOk) Environment.Exit(3);
    Console.WriteLine("\nFertig — CMS-Signatur + RFC-3161-Timestamp valid (CAdES-T-aequivalent).");
}
finally
{
    session.Logout();
}

static ISlot FindSlot(IPkcs11Library library, string tokenLabel)
{
    foreach (var slot in library.GetSlotList(SlotsType.WithTokenPresent))
    {
        if (slot.GetTokenInfo().Label.Trim() == tokenLabel) return slot;
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

// ExternalRsaSha256SignatureFactory: identische Implementierung wie im CMS-Demo
// (Modul 14). Wir koennen den Code nicht direkt teilen (kein gemeinsames Package),
// daher kopieren wir ihn — bei Bedarf in ein lab-internes Sub-Package faktorisieren.
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
        public BufferedStreamCalculator(Func<byte[], byte[]> signCallback) => this.signCallback = signCallback;
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
