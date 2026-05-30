.PHONY: build shell restore csharp-restore init-token list-slots list-mechanisms gen-rsa list-objects sign verify import-cert gen-ec sign-ec verify-ec sign-pss java-demo go-demo kotlin-demo csharp-demo gen-rsa-wrap encrypt decrypt issue-wrap-cert java-encrypt-demo go-encrypt-demo kotlin-encrypt-demo csharp-encrypt-demo cms-sign cms-verify java-cms-demo go-cms-demo kotlin-cms-demo csharp-cms-demo gen-aes-stream stream-sign stream-verify stream-encrypt stream-decrypt java-stream-demo go-stream-demo kotlin-stream-demo csharp-stream-demo gen-hmac hmac-sign hmac-verify java-hmac-demo go-hmac-demo kotlin-hmac-demo csharp-hmac-demo go-pool-demo csharp-pool-demo java-pool-demo kotlin-pool-demo clean clean-tokens distclean

# Defaults — koennen via Umgebung (`PKCS11_USER_PIN=... make sign`) oder
# direkt am make-Aufruf (`make sign PKCS11_USER_PIN=...`) ueberschrieben werden.
PKCS11_MODULE ?= /usr/lib/softhsm/libsofthsm2.so
PKCS11_TOKEN_LABEL ?= dev-token
PKCS11_USER_PIN ?= 987654
PKCS11_SO_PIN ?= 1234
PKCS11_MECHANISM ?=
PKCS11_KEY_ALIAS ?=
PKCS11_SLOT_ID ?=
PKCS11_LIBRARY ?=

# Variablen, die in die Lab-Skripte und Demos sichtbar sein muessen.
export PKCS11_MODULE PKCS11_TOKEN_LABEL PKCS11_USER_PIN PKCS11_SO_PIN
export PKCS11_MECHANISM PKCS11_KEY_ALIAS PKCS11_SLOT_ID PKCS11_LIBRARY

ifeq ($(PKCS11_IN_DEVCONTAINER),1)
RUN_LAB = bash -lc
RUN_GO = bash -lc
RUN_KOTLIN = bash -lc
RUN_CSHARP = bash -lc
else
DOCKER_ENV = \
  -e PKCS11_MODULE \
  -e PKCS11_TOKEN_LABEL \
  -e PKCS11_USER_PIN \
  -e PKCS11_SO_PIN \
  -e PKCS11_MECHANISM \
  -e PKCS11_KEY_ALIAS \
  -e PKCS11_SLOT_ID \
  -e PKCS11_LIBRARY
RUN_LAB = docker compose -f lab/docker-compose.yml run --rm $(DOCKER_ENV) pkcs11-lab bash -lc
RUN_GO = docker compose -f lab/docker-compose.yml run --rm $(DOCKER_ENV) pkcs11-go bash -lc
RUN_KOTLIN = docker compose -f lab/docker-compose.yml run --rm $(DOCKER_ENV) pkcs11-kotlin bash -lc
RUN_CSHARP = docker compose -f lab/docker-compose.yml run --rm $(DOCKER_ENV) pkcs11-csharp bash -lc
endif

build:
ifeq ($(PKCS11_IN_DEVCONTAINER),1)
	@echo "Devcontainer mode: image build is managed outside this container."
else
	docker compose -f lab/docker-compose.yml build
endif

shell:
ifeq ($(PKCS11_IN_DEVCONTAINER),1)
	bash
else
	docker compose -f lab/docker-compose.yml run --rm pkcs11-lab bash
endif

restore: csharp-restore

csharp-restore:
	$(RUN_CSHARP) 'dotnet restore lab/csharp/Pkcs11Demo/Pkcs11Demo.csproj'

init-token:
	$(RUN_LAB) 'lab/scripts/01-init-token.sh'

list-slots:
	$(RUN_LAB) 'lab/scripts/02-list-slots.sh'

list-mechanisms:
	$(RUN_LAB) 'lab/scripts/03-list-mechanisms.sh'

gen-rsa: init-token
	$(RUN_LAB) 'lab/scripts/04-generate-rsa.sh'

list-objects: init-token
	$(RUN_LAB) 'lab/scripts/05-list-objects.sh'

sign: gen-rsa
	$(RUN_LAB) 'lab/scripts/06-sign.sh'

verify: sign
	$(RUN_LAB) 'lab/scripts/07-verify.sh'

import-cert: gen-rsa
	$(RUN_LAB) 'lab/scripts/08-import-cert.sh'

gen-ec: init-token
	$(RUN_LAB) 'lab/scripts/09-generate-ec.sh'

sign-ec: gen-ec
	$(RUN_LAB) 'lab/scripts/10-sign-ec.sh'

verify-ec: sign-ec
	$(RUN_LAB) 'lab/scripts/11-verify-ec.sh'

sign-pss: gen-rsa
	$(RUN_LAB) 'lab/scripts/12-sign-pss.sh'

java-demo: import-cert
	$(RUN_LAB) 'cd lab/java/pkcs11-demo && ./gradlew --quiet --no-daemon run'

go-demo: gen-rsa
	$(RUN_GO) 'lab/scripts/13-go-demo.sh'

kotlin-demo: import-cert
	$(RUN_KOTLIN) 'lab/scripts/14-kotlin-demo.sh'

csharp-demo: gen-rsa
	$(RUN_CSHARP) 'lab/scripts/15-csharp-demo.sh'

gen-rsa-wrap: init-token
	$(RUN_LAB) 'lab/scripts/16-generate-rsa-wrap.sh'

encrypt: gen-rsa-wrap
	$(RUN_LAB) 'lab/scripts/17-encrypt-hybrid.sh'

decrypt: encrypt
	$(RUN_LAB) 'lab/scripts/18-decrypt-hybrid.sh'

issue-wrap-cert: gen-rsa-wrap
	$(RUN_LAB) 'lab/scripts/19-issue-wrap-cert.sh'

java-encrypt-demo: issue-wrap-cert
	$(RUN_LAB) 'lab/scripts/20-java-encrypt-demo.sh'

go-encrypt-demo: gen-rsa-wrap
	$(RUN_GO) 'lab/scripts/21-go-encrypt-demo.sh'

kotlin-encrypt-demo: issue-wrap-cert
	$(RUN_KOTLIN) 'lab/scripts/22-kotlin-encrypt-demo.sh'

csharp-encrypt-demo: gen-rsa-wrap
	$(RUN_CSHARP) 'lab/scripts/23-csharp-encrypt-demo.sh'

cms-sign: import-cert
	$(RUN_LAB) 'lab/scripts/24-cms-sign.sh'

cms-verify: cms-sign
	$(RUN_LAB) 'lab/scripts/25-cms-verify.sh'

go-cms-demo: import-cert
	$(RUN_GO) 'lab/scripts/26-go-cms-demo.sh'

csharp-cms-demo: import-cert
	$(RUN_CSHARP) 'lab/scripts/27-csharp-cms-demo.sh'

java-cms-demo: import-cert
	$(RUN_LAB) 'lab/scripts/28-java-cms-demo.sh'

kotlin-cms-demo: import-cert
	$(RUN_KOTLIN) 'lab/scripts/29-kotlin-cms-demo.sh'

gen-aes-stream: init-token
	$(RUN_LAB) 'lab/scripts/30-generate-aes-stream-key.sh'

stream-sign: gen-rsa
	$(RUN_LAB) 'lab/scripts/31-stream-sign.sh'

stream-verify: stream-sign
	$(RUN_LAB) 'lab/scripts/32-stream-verify.sh'

stream-encrypt: gen-aes-stream
	$(RUN_LAB) 'lab/scripts/33-stream-encrypt.sh'

stream-decrypt: stream-encrypt
	$(RUN_LAB) 'lab/scripts/34-stream-decrypt.sh'

go-stream-demo: gen-rsa gen-aes-stream
	$(RUN_GO) 'lab/scripts/35-go-stream-demo.sh'

csharp-stream-demo: gen-rsa gen-aes-stream
	$(RUN_CSHARP) 'lab/scripts/36-csharp-stream-demo.sh'

java-stream-demo: gen-rsa gen-aes-stream
	$(RUN_LAB) 'lab/scripts/37-java-stream-demo.sh'

kotlin-stream-demo: gen-rsa gen-aes-stream
	$(RUN_KOTLIN) 'lab/scripts/38-kotlin-stream-demo.sh'

gen-hmac: init-token
	$(RUN_LAB) 'lab/scripts/39-generate-hmac-key.sh'

hmac-sign: gen-hmac
	$(RUN_LAB) 'lab/scripts/40-hmac-sign.sh'

hmac-verify: hmac-sign
	$(RUN_LAB) 'lab/scripts/41-hmac-verify.sh'

go-hmac-demo: gen-hmac
	$(RUN_GO) 'lab/scripts/42-go-hmac-demo.sh'

csharp-hmac-demo: gen-hmac
	$(RUN_CSHARP) 'lab/scripts/43-csharp-hmac-demo.sh'

java-hmac-demo: gen-hmac
	$(RUN_LAB) 'lab/scripts/44-java-hmac-demo.sh'

kotlin-hmac-demo: gen-hmac
	$(RUN_KOTLIN) 'lab/scripts/45-kotlin-hmac-demo.sh'

go-pool-demo: gen-hmac
	$(RUN_GO) 'lab/scripts/46-go-pool-demo.sh'

csharp-pool-demo: gen-hmac
	$(RUN_CSHARP) 'lab/scripts/47-csharp-pool-demo.sh'

java-pool-demo: gen-hmac
	$(RUN_LAB) 'lab/scripts/48-java-pool-demo.sh'

kotlin-pool-demo: gen-hmac
	$(RUN_KOTLIN) 'lab/scripts/49-kotlin-pool-demo.sh'

# clean entfernt Build-Output und transient erzeugte Daten/Signatur-Artefakte,
# laesst aber die Token-Datenbank in lab/work/tokens intakt. Wer den Token
# komplett wegwerfen will, nutzt `make clean-tokens` oder `make distclean`.
clean:
	rm -rf lab/java/pkcs11-demo/build lab/java/pkcs11-demo/.gradle \
	       lab/java/pkcs11-encrypt-demo/build lab/java/pkcs11-encrypt-demo/.gradle \
	       lab/java/pkcs11-cms-demo/build lab/java/pkcs11-cms-demo/.gradle \
	       lab/java/pkcs11-stream-demo/build lab/java/pkcs11-stream-demo/.gradle \
	       lab/java/pkcs11-hmac-demo/build lab/java/pkcs11-hmac-demo/.gradle \
	       lab/java/pkcs11-pool-demo/build lab/java/pkcs11-pool-demo/.gradle \
	       lab/kotlin/pkcs11-demo/build lab/kotlin/pkcs11-demo/.gradle lab/kotlin/pkcs11-demo/.kotlin \
	       lab/kotlin/pkcs11-encrypt-demo/build lab/kotlin/pkcs11-encrypt-demo/.gradle lab/kotlin/pkcs11-encrypt-demo/.kotlin \
	       lab/kotlin/pkcs11-cms-demo/build lab/kotlin/pkcs11-cms-demo/.gradle lab/kotlin/pkcs11-cms-demo/.kotlin \
	       lab/kotlin/pkcs11-stream-demo/build lab/kotlin/pkcs11-stream-demo/.gradle lab/kotlin/pkcs11-stream-demo/.kotlin \
	       lab/kotlin/pkcs11-hmac-demo/build lab/kotlin/pkcs11-hmac-demo/.gradle lab/kotlin/pkcs11-hmac-demo/.kotlin \
	       lab/kotlin/pkcs11-pool-demo/build lab/kotlin/pkcs11-pool-demo/.gradle lab/kotlin/pkcs11-pool-demo/.kotlin \
	       lab/csharp/Pkcs11Demo/bin lab/csharp/Pkcs11Demo/obj \
	       lab/csharp/Pkcs11EncryptDemo/bin lab/csharp/Pkcs11EncryptDemo/obj \
	       lab/csharp/Pkcs11CmsDemo/bin lab/csharp/Pkcs11CmsDemo/obj \
	       lab/csharp/Pkcs11StreamDemo/bin lab/csharp/Pkcs11StreamDemo/obj \
	       lab/csharp/Pkcs11HmacDemo/bin lab/csharp/Pkcs11HmacDemo/obj \
	       lab/csharp/Pkcs11PoolDemo/bin lab/csharp/Pkcs11PoolDemo/obj
	find lab/work -mindepth 1 -maxdepth 1 ! -name tokens ! -name .gitkeep -exec rm -rf {} +

clean-tokens:
	rm -rf lab/work/tokens
	mkdir -p lab/work/tokens

distclean: clean clean-tokens
ifneq ($(PKCS11_IN_DEVCONTAINER),1)
	docker compose -f lab/docker-compose.yml down -v
endif
