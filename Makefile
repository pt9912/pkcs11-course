.PHONY: build shell restore csharp-restore init-token list-slots list-mechanisms gen-rsa list-objects sign verify import-cert gen-ec sign-ec verify-ec sign-pss java-demo go-demo kotlin-demo csharp-demo gen-rsa-wrap encrypt decrypt issue-wrap-cert java-encrypt-demo go-encrypt-demo kotlin-encrypt-demo csharp-encrypt-demo clean clean-tokens distclean

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

# clean entfernt Build-Output und transient erzeugte Daten/Signatur-Artefakte,
# laesst aber die Token-Datenbank in lab/work/tokens intakt. Wer den Token
# komplett wegwerfen will, nutzt `make clean-tokens` oder `make distclean`.
clean:
	rm -rf lab/java/pkcs11-demo/build lab/java/pkcs11-demo/.gradle \
	       lab/java/pkcs11-encrypt-demo/build lab/java/pkcs11-encrypt-demo/.gradle \
	       lab/kotlin/pkcs11-demo/build lab/kotlin/pkcs11-demo/.gradle lab/kotlin/pkcs11-demo/.kotlin \
	       lab/kotlin/pkcs11-encrypt-demo/build lab/kotlin/pkcs11-encrypt-demo/.gradle lab/kotlin/pkcs11-encrypt-demo/.kotlin \
	       lab/csharp/Pkcs11Demo/bin lab/csharp/Pkcs11Demo/obj \
	       lab/csharp/Pkcs11EncryptDemo/bin lab/csharp/Pkcs11EncryptDemo/obj
	find lab/work -mindepth 1 -maxdepth 1 ! -name tokens ! -name .gitkeep -exec rm -rf {} +

clean-tokens:
	rm -rf lab/work/tokens
	mkdir -p lab/work/tokens

distclean: clean clean-tokens
ifneq ($(PKCS11_IN_DEVCONTAINER),1)
	docker compose -f lab/docker-compose.yml down -v
endif
