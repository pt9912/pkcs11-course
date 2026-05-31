using System.Diagnostics;
using System.Security.Cryptography;
using System.Text;
using Net.Pkcs11Interop.Common;
using Net.Pkcs11Interop.HighLevelAPI;

// HSM-RNG via C_GenerateRandom (Pkcs11Interop: session.GenerateRandom(length)).
//  1) 32 Byte Proof-of-Life als Hex-Dump.
//  2) Durchsatz HSM vs System.Security.Cryptography.RandomNumberGenerator
//     (auf Linux ist das ein Wrapper um getrandom(2) / /dev/urandom).
//  3) Shannon-Entropie ueber die ersten 64 KB HSM-Bytes.

// SoftHSM teilt im selben Prozess Speicher mit der Anwendung — Vergleich
// ist eher illustrativ. Reale HSMs sind 10-100x langsamer als der Kernel-RNG,
// dafuer compliance-relevant (FIPS-140-2/3, NIST SP 800-90B).

const int ChunkSize = 8 * 1024;
const int TotalBytes = 1 * 1024 * 1024;

var modulePath = Env("PKCS11_MODULE", "/usr/lib/softhsm/libsofthsm2.so");
var tokenLabel = Env("PKCS11_TOKEN_LABEL", "dev-token");
var pin = Env("PKCS11_USER_PIN", "987654");

var pinBytes = Encoding.UTF8.GetBytes(pin);
var factories = new Pkcs11InteropFactories();
using var library = factories.Pkcs11LibraryFactory.LoadPkcs11Library(factories, modulePath, AppType.MultiThreaded);
var slot = FindSlot(library, tokenLabel);

var tokenInfo = slot.GetTokenInfo();
if (!tokenInfo.TokenFlags.Rng)
{
    Console.Error.WriteLine("Token meldet kein CKF_RNG — C_GenerateRandom nicht verfuegbar.");
    Environment.Exit(2);
}
Console.WriteLine("CKF_RNG: gesetzt");

using var session = slot.OpenSession(SessionType.ReadOnly);
try
{
    // Login: spec-mae nicht zwingend fuer RNG-Pfad, aber Vendor-HSMs verlangen
    // ihn haeufig. Wir machen ihn, damit die Demo auf Hardware ohne Anpassung laeuft.
    session.Login(CKU.CKU_USER, pinBytes);
}
finally
{
    Array.Clear(pinBytes, 0, pinBytes.Length);
}

try
{
    Console.WriteLine("\n=== 1) Proof-of-Life: 32 Byte aus dem HSM ===");
    var sample = session.GenerateRandom(32);
    Console.WriteLine($"  Hex: {Convert.ToHexString(sample).ToLowerInvariant()}");

    Console.WriteLine("\n=== 2) Durchsatz HSM vs RandomNumberGenerator ===");
    var (hsmBytes, hsmMs) = TimeGenerate(TotalBytes, ChunkSize, n => session.GenerateRandom(n));
    var (osBytes, osMs) = TimeGenerate(TotalBytes, ChunkSize, n =>
    {
        var buf = new byte[n];
        RandomNumberGenerator.Fill(buf);
        return buf;
    });
    Report("HSM (C_GenerateRandom, persistente Session)", hsmBytes.Length, hsmMs);
    Report("RandomNumberGenerator (Linux getrandom)", osBytes.Length, osMs);
    if (hsmMs > 0 && osMs > 0)
    {
        var ratio = (double)osMs / hsmMs;
        Console.WriteLine(ratio < 1
            ? $"  HSM ist Faktor {1.0 / ratio:F1}x langsamer als RandomNumberGenerator"
            : $"  HSM ist Faktor {ratio:F1}x schneller als RandomNumberGenerator (SoftHSM-Spezialfall)");
    }

    Console.WriteLine("\n=== 3) Verteilungs-Check ueber 64 KB HSM-Bytes ===");
    var bucket = hsmBytes.Length > 64 * 1024 ? hsmBytes[..(64 * 1024)] : hsmBytes;
    var entropy = ShannonEntropy(bucket);
    Console.WriteLine($"  Shannon-Entropie: {entropy:F4} bit/byte (Idealwert: 8.0)");
    if (entropy < 7.5)
    {
        Console.Error.WriteLine($"Entropie {entropy:F4} bit/byte zu niedrig — RNG-Output sieht nicht uniform aus.");
        Environment.Exit(3);
    }

    Console.WriteLine("\nFertig — der HSM-RNG funktioniert wie erwartet.");
}
finally
{
    session.Logout();
}

static (byte[] data, double ms) TimeGenerate(int total, int chunk, Func<int, byte[]> gen)
{
    var buf = new byte[total];
    var offset = 0;
    var sw = Stopwatch.StartNew();
    while (offset < total)
    {
        var need = Math.Min(chunk, total - offset);
        var piece = gen(need);
        Buffer.BlockCopy(piece, 0, buf, offset, piece.Length);
        offset += piece.Length;
    }
    sw.Stop();
    return (buf, sw.Elapsed.TotalMilliseconds);
}

static void Report(string label, int bytes, double ms)
{
    var mbps = ms > 0 ? (bytes / 1024.0 / 1024.0) / (ms / 1000.0) : double.PositiveInfinity;
    Console.WriteLine($"  {label,-50} {ms / 1000.0,7:F3}s  {mbps,8:F2} MB/s");
}

static double ShannonEntropy(byte[] data)
{
    if (data.Length == 0) return 0;
    var counts = new int[256];
    foreach (var b in data) counts[b]++;
    double n = data.Length;
    double h = 0;
    foreach (var c in counts)
    {
        if (c == 0) continue;
        var p = c / n;
        h -= p * Math.Log2(p);
    }
    return h;
}

static ISlot FindSlot(IPkcs11Library library, string tokenLabel)
{
    foreach (var slot in library.GetSlotList(SlotsType.WithTokenPresent))
    {
        if (slot.GetTokenInfo().Label.Trim() == tokenLabel) return slot;
    }
    throw new InvalidOperationException($"Token mit Label '{tokenLabel}' nicht gefunden.");
}

static string Env(string name, string fallback)
{
    var value = Environment.GetEnvironmentVariable(name);
    return string.IsNullOrWhiteSpace(value) ? fallback : value;
}
