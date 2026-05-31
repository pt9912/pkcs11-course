#!/usr/bin/env python3
"""Minimaler RFC-3161-Time-Stamping-Server fuer das Lab.

Nimmt HTTP-POST mit application/timestamp-query (DER-TimeStampReq) entgegen,
gibt es an `openssl ts -reply` weiter (mit Signer-Key aus dem HSM via
pkcs11-engine) und schickt die TimeStampResp zurueck.

openssl ts ist kein eigenes HTTP-Daemon — wir wrappen es. Reale TSAs (DigiCert,
Sectigo) laufen typisch hinter nginx + eigenem TSP-Daemon; der Wrapper hier
ist lehrreich-minimal, keine Production-Empfehlung.

Aufruf:
    _tsa_server.py <openssl-config-path> <tsa-config-section> <port>
"""
import http.server
import os
import socketserver
import subprocess
import sys
import tempfile


def make_handler(openssl_conf: str, tsa_section: str):
    class TSAHandler(http.server.BaseHTTPRequestHandler):
        def do_POST(self):
            length = int(self.headers.get("Content-Length", "0"))
            if length <= 0:
                self.send_error(400, "leerer Request-Body")
                return
            request = self.rfile.read(length)

            # openssl ts -reply liest TSReq von stdin (oder Datei). Wir nehmen Datei,
            # weil -queryfile - in manchen openssl-Versionen unzuverlaessig ist.
            with tempfile.NamedTemporaryFile(suffix=".tsq", delete=False) as fq:
                fq.write(request)
                req_path = fq.name
            try:
                proc = subprocess.run(
                    [
                        "openssl", "ts", "-reply",
                        "-config", openssl_conf,
                        "-section", tsa_section,
                        "-queryfile", req_path,
                    ],
                    capture_output=True,
                )
            finally:
                os.unlink(req_path)

            if proc.returncode != 0:
                msg = proc.stderr.decode(errors="replace")
                # openssl ts schreibt manchmal die TSResp auf stderr und nutzt 0;
                # wenn 0 != exit, ist es ein echter Fehler.
                #
                # Hinweis: wir geben stderr im Response-Body zurueck, damit Lab-
                # Demos die openssl-Diagnose direkt sehen. Fuer produktive TSAs
                # NIEMALS: das wuerde Pfade und interne Fehlermeldungen leaken.
                self.send_response(500)
                self.send_header("Content-Type", "text/plain; charset=utf-8")
                self.end_headers()
                self.wfile.write(("openssl ts -reply schlug fehl:\n" + msg).encode())
                return

            self.send_response(200)
            self.send_header("Content-Type", "application/timestamp-reply")
            self.send_header("Content-Length", str(len(proc.stdout)))
            self.end_headers()
            self.wfile.write(proc.stdout)

        def log_message(self, fmt, *args):
            # Knapp halten — nur Fehler interessant. Wer mehr will, setzt
            # PKCS11_TSA_VERBOSE=1 (Schalter unten).
            import os
            if os.environ.get("PKCS11_TSA_VERBOSE"):
                super().log_message(fmt, *args)

    return TSAHandler


class ReuseAddrServer(socketserver.TCPServer):
    # SO_REUSEADDR muss vor bind() gesetzt werden — d.h. als Klassenattribut,
    # nicht als Instanz-Override nach __init__. Sonst blockiert ein TIME_WAIT-
    # Socket Neustarts ~60s lang (Annoyance in scripted Tests).
    allow_reuse_address = True


def main():
    if len(sys.argv) != 4:
        print(__doc__, file=sys.stderr)
        sys.exit(64)
    openssl_conf, tsa_section, port = sys.argv[1], sys.argv[2], int(sys.argv[3])

    handler = make_handler(openssl_conf, tsa_section)
    with ReuseAddrServer(("127.0.0.1", port), handler) as httpd:
        sys.stderr.write(f"TSA-Server hoert auf http://127.0.0.1:{port}\n")
        sys.stderr.flush()
        httpd.serve_forever()


if __name__ == "__main__":
    main()
