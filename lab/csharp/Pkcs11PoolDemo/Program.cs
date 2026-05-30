using System.Diagnostics;
using System.Text;
using Net.Pkcs11Interop.Common;
using Net.Pkcs11Interop.HighLevelAPI;

// Session-Pooling in C# / Pkcs11Interop:
// AppType.MultiThreaded laesst die Lib intern locken. ISession ist trotzdem
// NICHT fuer paralleles Senden geeignet — ein C_SignInit waehrend C_Sign
// anderer Threads brennt mit CKR_OPERATION_ACTIVE.
//
// Pattern: ConcurrentBag/Channel als Pool, ein Worker holt eine Session
// und gibt sie zurueck. Wir nehmen einen klassischen BlockingCollection-Pool
// (synchron, einfach lesbar) plus Task.Run-Worker.

const int PoolSize = 8;
const int TotalOps = 10000;
const int MessageSize = 64;

var modulePath = Env("PKCS11_MODULE", "/usr/lib/softhsm/libsofthsm2.so");
var tokenLabel = Env("PKCS11_TOKEN_LABEL", "dev-token");
var pin = Env("PKCS11_USER_PIN", "987654");
var keyId = new byte[] { 0x05 };

var pinBytes = Encoding.UTF8.GetBytes(pin);
var factories = new Pkcs11InteropFactories();
using var library = factories.Pkcs11LibraryFactory.LoadPkcs11Library(factories, modulePath, AppType.MultiThreaded);
var slot = FindSlot(library, tokenLabel);

var sessions = new List<ISession>();
try
{
    for (int i = 0; i < PoolSize; i++)
    {
        sessions.Add(slot.OpenSession(SessionType.ReadWrite));
    }
    // Login auf einer Session — wirkt anwendungsweit fuer alle Sessions
    // desselben Tokens (PKCS#11 §11.4).
    try
    {
        sessions[0].Login(CKU.CKU_USER, pinBytes);
    }
    finally
    {
        Array.Clear(pinBytes, 0, pinBytes.Length);
    }

    IObjectHandle hmacKey = FindKey(sessions[0], factories, CKO.CKO_SECRET_KEY, keyId);

    var data = new byte[MessageSize];
    for (int i = 0; i < data.Length; i++) data[i] = (byte)i;

    // --- Sequenziell ---
    var seqStart = Stopwatch.StartNew();
    for (int i = 0; i < TotalOps; i++)
    {
        HmacOnce(sessions[0], factories, hmacKey, data);
    }
    seqStart.Stop();
    var seqElapsed = seqStart.Elapsed;

    // --- Parallel mit BlockingCollection-Pool ---
    var pool = new System.Collections.Concurrent.BlockingCollection<ISession>(PoolSize);
    foreach (var s in sessions)
    {
        pool.Add(s);
    }

    int counter = 0;
    var parStart = Stopwatch.StartNew();
    var tasks = new Task[PoolSize];
    for (int w = 0; w < PoolSize; w++)
    {
        tasks[w] = Task.Run(() =>
        {
            while (Interlocked.Increment(ref counter) <= TotalOps)
            {
                var s = pool.Take();
                try
                {
                    HmacOnce(s, factories, hmacKey, data);
                }
                finally
                {
                    pool.Add(s);
                }
            }
        });
    }
    Task.WaitAll(tasks);
    parStart.Stop();
    var parElapsed = parStart.Elapsed;

    var speedup = seqElapsed.TotalMilliseconds / parElapsed.TotalMilliseconds;
    Console.WriteLine($"Operationen:    {TotalOps} × HMAC-SHA256({MessageSize} Bytes)");
    Console.WriteLine($"Pool-Groesse:   {PoolSize} Sessions");
    Console.WriteLine($"Sequenziell:    {seqElapsed.TotalMilliseconds:F2} ms ({TotalOps / seqElapsed.TotalSeconds:F0} ops/s)");
    Console.WriteLine($"Parallel (×{PoolSize}): {parElapsed.TotalMilliseconds:F2} ms ({TotalOps / parElapsed.TotalSeconds:F0} ops/s)");
    Console.WriteLine($"Speedup:        {speedup:F2}x");
}
finally
{
    if (sessions.Count > 0)
    {
        try { sessions[0].Logout(); } catch { }
    }
    foreach (var s in sessions)
    {
        s.Dispose();
    }
}

static void HmacOnce(ISession session, Pkcs11InteropFactories factories, IObjectHandle key, byte[] data)
{
    using var mech = factories.MechanismFactory.Create(CKM.CKM_SHA256_HMAC);
    session.Sign(mech, key, data);
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
