# Lösung 06 — C# über Pkcs11Interop

## Kernablauf

1. `Pkcs11InteropFactories` erzeugen.
2. Library mit dem Pfad aus `PKCS11_MODULE` laden.
3. Slots mit Token lesen und über `GetTokenInfo().Label` den Token `dev-token` finden.
4. Read/Write-Session öffnen und mit `CKU_USER` einloggen.
5. Private-Key-Objekt suchen:
   - `CKA_CLASS = CKO_PRIVATE_KEY`
   - `CKA_ID = 01`
6. Mit Mechanismus `CKM_SHA256_RSA_PKCS` signieren.
7. Signatur mit OpenSSL gegen den exportierten Public Key prüfen.

## Verifikation

```bash
pkcs11-tool --module "$PKCS11_MODULE" --token-label "$PKCS11_TOKEN_LABEL" \
  --read-object --type pubkey --id 01 --output-file lab/work/public-csharp.der

openssl rsa -pubin -inform DER -in lab/work/public-csharp.der -out lab/work/public-csharp.pem
openssl dgst -sha256 -verify lab/work/public-csharp.pem \
  -signature lab/work/csharp.sig lab/work/csharp.txt
```

Erwartet: `Verified OK`.

## Typische Fehler

| Fehler | Ursache |
|---|---|
| Library-Load-Fehler | Falscher Modulpfad oder native Abhängigkeit fehlt |
| `CKR_PIN_INCORRECT` | Falsche User-PIN |
| Kein Key gefunden | Token initialisiert, aber RSA-Key nicht erzeugt oder falsche `CKA_ID` |

## Minimalbeispiel

`Program.cs`:

```csharp
using System;
using System.IO;
using System.Linq;
using System.Text;
using Net.Pkcs11Interop.Common;
using Net.Pkcs11Interop.HighLevelAPI;

var modulePath = Environment.GetEnvironmentVariable("PKCS11_MODULE")
    ?? "/usr/lib/softhsm/libsofthsm2.so";
var tokenLabel = Environment.GetEnvironmentVariable("PKCS11_TOKEN_LABEL") ?? "dev-token";
var pin = Environment.GetEnvironmentVariable("PKCS11_USER_PIN") ?? "987654";
var keyId = new byte[] { 0x01 };

var factories = new Pkcs11InteropFactories();
using var lib = factories.Pkcs11LibraryFactory.LoadPkcs11Library(
    factories, modulePath, AppType.MultiThreaded);

var slot = lib.GetSlotList(SlotsType.WithTokenPresent)
    .First(s => s.GetTokenInfo().Label.TrimEnd() == tokenLabel);

using var session = slot.OpenSession(SessionType.ReadWrite);
session.Login(CKU.CKU_USER, Encoding.UTF8.GetBytes(pin));

var search = new[]
{
    session.Factories.ObjectAttributeFactory.Create(CKA.CKA_CLASS, CKO.CKO_PRIVATE_KEY),
    session.Factories.ObjectAttributeFactory.Create(CKA.CKA_ID, keyId)
};
var key = session.FindAllObjects(search).Single();

var mech = session.Factories.MechanismFactory.Create(CKM.CKM_SHA256_RSA_PKCS);
var data = Encoding.UTF8.GetBytes("hello from csharp pkcs11");
var sig = session.Sign(mech, key, data);

Directory.CreateDirectory("lab/work");
File.WriteAllBytes("lab/work/csharp.txt", data);
File.WriteAllBytes("lab/work/csharp.sig", sig);
Console.WriteLine($"Signatur ({sig.Length} Bytes): lab/work/csharp.sig");

session.Logout();
```

Build und Lauf im C#-Container:

```bash
docker compose -f lab/docker-compose.yml run --rm pkcs11-csharp bash
# im Container:
mkdir -p /tmp/pkcs11-csharp && cd /tmp/pkcs11-csharp
dotnet new console --force
cp /workspace/lab/csharp-demo/Program.cs .
dotnet add package Pkcs11Interop
dotnet run
```

Anschließend Verifikation wie oben.
