package dev.course.pkcs11;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.ProviderException;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.SignerInfoGenerator;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.tsp.TSPAlgorithms;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampRequestGenerator;
import org.bouncycastle.tsp.TimeStampResponse;
import org.bouncycastle.tsp.TimeStampToken;

public final class Pkcs11CmsTsaDemo {
    // CAdES-T-aequivalenter Flow: CMS signieren, TimeStampToken besorgen,
    // als signatureTimeStampToken (RFC 3161, OID 1.2.840.113549.1.9.16.2.14)
    // in die SignerInfo-UnsignedAttributes einbauen, am Ende verifizieren.
    //
    // Der Doc-Signer-Key bleibt HSM-resident (SunPKCS11), die TSA hat
    // im Lab einen Software-Key — siehe 85-tsa-setup.sh fuer das Warum.

    private Pkcs11CmsTsaDemo() {}

    public static void main(String[] args) {
        try {
            String configPath = nonEmpty(System.getenv("PKCS11_JAVA_CONFIG"), "src/main/resources/softhsm.cfg");
            char[] pin = nonEmpty(System.getenv("PKCS11_USER_PIN"), "987654").toCharArray();
            String alias = nonEmpty(System.getenv("PKCS11_KEY_ALIAS"), "signing-key");
            String outputDir = nonEmpty(System.getenv("PKCS11_OUTPUT_DIR"), "/workspace/lab/work");
            String tsaUrl = nonEmpty(System.getenv("PKCS11_TSA_URL"), "http://127.0.0.1:8088/");

            Security.addProvider(new BouncyCastleProvider());

            Provider base = Security.getProvider("SunPKCS11");
            if (base == null) {
                throw new IllegalStateException("SunPKCS11 Provider fehlt.");
            }
            Provider provider = base.configure(configPath);
            System.out.println("Provider: " + provider.getName());

            KeyStore keyStore = KeyStore.getInstance("PKCS11", provider);
            try {
                keyStore.load(null, pin);
            } finally {
                Arrays.fill(pin, '\0');
            }

            PrivateKey signerKey = (PrivateKey) keyStore.getKey(alias, null);
            X509Certificate signerCert = (X509Certificate) keyStore.getCertificate(alias);
            if (signerKey == null || signerCert == null) {
                System.err.println("Signer-Key/Cert fuer alias '" + alias + "' fehlt — make import-cert ausfuehren.");
                System.exit(2);
            }

            // === 1) CMS-Signatur bauen ===
            byte[] payload = "Lab-Dokument fuer CMS+TSA-Roundtrip (Java).\n".getBytes(StandardCharsets.UTF_8);

            ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(provider) // SunPKCS11 — die Signaturoperation laeuft im HSM
                .build(signerKey);

            SignerInfoGenerator sig = new JcaSignerInfoGeneratorBuilder(
                new JcaDigestCalculatorProviderBuilder().setProvider("BC").build())
                .build(contentSigner, signerCert);

            CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
            gen.addSignerInfoGenerator(sig);
            gen.addCertificates(new JcaCertStore(List.of(signerCert)));

            CMSSignedData cms = gen.generate(new CMSProcessableByteArray(payload), false);
            System.out.println("=== 1) CMS-Signatur (detached, SHA256withRSA) ===");
            System.out.println("  Signer:    " + signerCert.getSubjectX500Principal().getName());
            System.out.println("  CMS-Blob:  " + cms.getEncoded().length + " Byte");

            // === 2) Hash der SignerInfo-Signatur an die TSA schicken ===
            // RFC 3161: TimeStampReq hashed die Daten, ueber die der Timestamp gilt.
            // Bei CAdES-T ist das der Signature-Value der SignerInfo.
            SignerInformation signerInfo = cms.getSignerInfos().iterator().next();
            byte[] signatureValue = signerInfo.getSignature();

            TimeStampRequestGenerator reqGen = new TimeStampRequestGenerator();
            reqGen.setCertReq(true);
            TimeStampRequest tsReq = reqGen.generate(
                TSPAlgorithms.SHA256,
                java.security.MessageDigest.getInstance("SHA-256").digest(signatureValue),
                BigInteger.valueOf(new SecureRandom().nextInt() & 0x7fffffff));

            byte[] tsReqBytes = tsReq.getEncoded();
            System.out.println("\n=== 2) TimeStampReq erzeugen + POST an TSA ===");
            System.out.println("  TSA-URL:  " + tsaUrl);
            System.out.println("  TSReq:    " + tsReqBytes.length + " Byte (SHA-256 ueber SignerInfo.signature)");

            HttpClient http = HttpClient.newHttpClient();
            HttpRequest httpReq = HttpRequest.newBuilder(URI.create(tsaUrl))
                .header("Content-Type", "application/timestamp-query")
                .POST(HttpRequest.BodyPublishers.ofByteArray(tsReqBytes))
                .build();
            HttpResponse<byte[]> httpResp = http.send(httpReq, HttpResponse.BodyHandlers.ofByteArray());
            if (httpResp.statusCode() != 200) {
                throw new RuntimeException("TSA antwortete HTTP " + httpResp.statusCode());
            }
            TimeStampResponse tsResp = new TimeStampResponse(httpResp.body());
            tsResp.validate(tsReq);
            TimeStampToken tsToken = tsResp.getTimeStampToken();
            if (tsToken == null) {
                throw new RuntimeException("TSA-Status: " + tsResp.getStatusString());
            }
            System.out.println("  TSToken:  " + tsToken.getEncoded().length + " Byte");
            System.out.println("  Gen-Time: " + tsToken.getTimeStampInfo().getGenTime());

            // === 3) TSToken als unsignedAttribute.signatureTimeStampToken einbauen ===
            // RFC 3161 §3.3.1 + RFC 5652 §11.2: id-aa-signatureTimeStampToken =
            // 1.2.840.113549.1.9.16.2.14, Inhalt ist die rohe TSToken-Bytes.
            Attribute tsAttr = new Attribute(
                PKCSObjectIdentifiers.id_aa_signatureTimeStampToken,
                new org.bouncycastle.asn1.DERSet(tsToken.toCMSSignedData().toASN1Structure()));
            AttributeTable unsigned = new AttributeTable(new ASN1EncodableVector() {{
                add(tsAttr);
            }});
            SignerInformation withTimestamp = SignerInformation.replaceUnsignedAttributes(signerInfo, unsigned);
            CMSSignedData cmsT = CMSSignedData.replaceSigners(cms, new SignerInformationStore(List.of(withTimestamp)));

            Path outPath = Path.of(outputDir, "java-cms-tsa.p7s");
            Files.createDirectories(outPath.getParent());
            Files.write(outPath, cmsT.getEncoded());
            System.out.println("\n=== 3) CMS+TSA-Signatur geschrieben ===");
            System.out.println("  Datei:    " + outPath);
            System.out.println("  Groesse:  " + cmsT.getEncoded().length + " Byte (Plain-CMS war " + cms.getEncoded().length + ")");

            // === 4) Roundtrip: CMS-Signatur und Timestamp verifizieren ===
            System.out.println("\n=== 4) Verifikation ===");
            CMSSignedData parsed = new CMSSignedData(new CMSProcessableByteArray(payload), Files.readAllBytes(outPath));
            SignerInformation parsedSigner = parsed.getSignerInfos().iterator().next();
            boolean cmsOk = parsedSigner.verify(
                new org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder()
                    .setProvider("BC").build(signerCert));
            System.out.println("  CMS-Signatur:    " + (cmsOk ? "OK" : "FAIL"));

            // TSToken aus den UnsignedAttrs zurueck-extrahieren und Inhalt pruefen.
            Attribute tsBack = parsedSigner.getUnsignedAttributes()
                .get(PKCSObjectIdentifiers.id_aa_signatureTimeStampToken);
            TimeStampToken tsBackToken = new TimeStampToken(
                new CMSSignedData(tsBack.getAttrValues().getObjectAt(0).toASN1Primitive().getEncoded()));
            tsBackToken.validate(new org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder()
                .setProvider("BC").build(loadTsaCert(outputDir)));
            // Der TSToken hashed Signer.signature — vergleichen.
            byte[] expectedHash = java.security.MessageDigest.getInstance("SHA-256").digest(parsedSigner.getSignature());
            boolean tsHashOk = Arrays.equals(expectedHash, tsBackToken.getTimeStampInfo().getMessageImprintDigest());
            System.out.println("  Timestamp-Hash:  " + (tsHashOk ? "OK" : "FAIL"));
            System.out.println("  Timestamp-Zeit:  " + tsBackToken.getTimeStampInfo().getGenTime());
            System.out.println("  TSA-Subject:     " + loadTsaCert(outputDir).getSubjectX500Principal().getName());

            if (!cmsOk || !tsHashOk) {
                System.exit(3);
            }
            System.out.println("\nFertig — CMS-Signatur + RFC-3161-Timestamp valid (CAdES-T-aequivalent).");
        } catch (Exception e) {
            reportFailure(e);
            System.exit(1);
        }
    }

    private static X509Certificate loadTsaCert(String outputDir) throws Exception {
        try (var in = Files.newInputStream(Path.of(outputDir, "tsa-cert.pem"))) {
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
        }
    }

    private static String nonEmpty(String s, String fallback) {
        return (s == null || s.isEmpty()) ? fallback : s;
    }

    private static void reportFailure(Throwable t) {
        System.err.println("Fehler beim CMS+TSA-Lauf:");
        Throwable cur = t;
        int depth = 0;
        while (cur != null && depth < 8) {
            String label = cur instanceof ProviderException ? "ProviderException" : cur.getClass().getSimpleName();
            System.err.println("  " + label + ": " + cur.getMessage());
            cur = cur.getCause();
            depth++;
        }
    }
}
