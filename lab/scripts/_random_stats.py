#!/usr/bin/env python3
"""Minimaler Verteilungs-Check fuer Random-Bytes.

Aufruf:
    _random_stats.py <pfad>

Liest die Datei, berichtet Anzahl Bytes, Shannon-Entropie pro Byte und
einen einfachen Chi-Quadrat-Wert gegen die Gleichverteilung (256 Klassen).

Das ist KEIN Ersatz fuer NIST SP 800-22 oder dieharder — fuer eine Demo
mit ein paar MB Input reicht es, um zu zeigen, dass die Bytes "aussehen
wie Random" (Entropie nahe 8.0 bit/byte, Chi-Quadrat nahe 255).
"""
import math
import sys
from collections import Counter
from pathlib import Path


def main() -> None:
    if len(sys.argv) != 2:
        print(__doc__, file=sys.stderr)
        sys.exit(64)
    data = Path(sys.argv[1]).read_bytes()
    n = len(data)
    if n == 0:
        print("leer", file=sys.stderr)
        sys.exit(2)
    freq = Counter(data)
    # Shannon-Entropie (Basis 2). Bei wirklich uniformer Verteilung -> 8.0.
    entropy = -sum((c / n) * math.log2(c / n) for c in freq.values())
    # Pearson Chi-Quadrat gegen 256 gleichwahrscheinliche Klassen.
    # Erwartung pro Klasse = n/256. Erwartungswert fuer den Chi-Quadrat-Wert
    # ist bei H0 (uniform) gleich der Anzahl der Freiheitsgrade = 255.
    expected = n / 256.0
    chi2 = sum(((freq.get(b, 0) - expected) ** 2) / expected for b in range(256))
    print(f"  Bytes:        {n}")
    print(f"  Shannon-Entropie: {entropy:.4f} bit/byte (Idealwert: 8.0)")
    print(f"  Chi^2 (df=255):   {chi2:.1f} (Erwartung: ~255)")


if __name__ == "__main__":
    main()
