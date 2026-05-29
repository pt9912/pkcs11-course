#!/usr/bin/env bash
set -euo pipefail
cd lab/kotlin/pkcs11-demo
mvn -q package
java -jar target/kotlin-pkcs11-demo-1.0.0.jar
