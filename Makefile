.PHONY: build shell init-token list-slots list-mechanisms gen-rsa list-objects sign verify import-cert gen-ec sign-ec verify-ec sign-pss java-demo go-demo kotlin-demo csharp-demo clean

build:
	docker compose -f lab/docker-compose.yml build

shell:
	docker compose -f lab/docker-compose.yml run --rm pkcs11-lab bash

init-token:
	docker compose -f lab/docker-compose.yml run --rm pkcs11-lab bash -lc 'lab/scripts/01-init-token.sh'

list-slots:
	docker compose -f lab/docker-compose.yml run --rm pkcs11-lab bash -lc 'lab/scripts/02-list-slots.sh'

list-mechanisms:
	docker compose -f lab/docker-compose.yml run --rm pkcs11-lab bash -lc 'lab/scripts/03-list-mechanisms.sh'

gen-rsa:
	docker compose -f lab/docker-compose.yml run --rm pkcs11-lab bash -lc 'lab/scripts/04-generate-rsa.sh'

list-objects:
	docker compose -f lab/docker-compose.yml run --rm pkcs11-lab bash -lc 'lab/scripts/05-list-objects.sh'

sign:
	docker compose -f lab/docker-compose.yml run --rm pkcs11-lab bash -lc 'lab/scripts/06-sign.sh'

verify:
	docker compose -f lab/docker-compose.yml run --rm pkcs11-lab bash -lc 'lab/scripts/07-verify.sh'

import-cert:
	docker compose -f lab/docker-compose.yml run --rm pkcs11-lab bash -lc 'lab/scripts/08-import-cert.sh'

gen-ec:
	docker compose -f lab/docker-compose.yml run --rm pkcs11-lab bash -lc 'lab/scripts/09-generate-ec.sh'

sign-ec:
	docker compose -f lab/docker-compose.yml run --rm pkcs11-lab bash -lc 'lab/scripts/10-sign-ec.sh'

verify-ec:
	docker compose -f lab/docker-compose.yml run --rm pkcs11-lab bash -lc 'lab/scripts/11-verify-ec.sh'

sign-pss:
	docker compose -f lab/docker-compose.yml run --rm pkcs11-lab bash -lc 'lab/scripts/12-sign-pss.sh'

java-demo: import-cert
	docker compose -f lab/docker-compose.yml run --rm pkcs11-lab bash -lc 'cd lab/java/pkcs11-demo && gradle --quiet run'

go-demo: gen-rsa
	docker compose -f lab/docker-compose.yml run --rm pkcs11-go bash -lc 'lab/scripts/13-go-demo.sh'

kotlin-demo: import-cert
	docker compose -f lab/docker-compose.yml run --rm pkcs11-kotlin bash -lc 'lab/scripts/14-kotlin-demo.sh'

csharp-demo: gen-rsa
	docker compose -f lab/docker-compose.yml run --rm pkcs11-csharp bash -lc 'lab/scripts/15-csharp-demo.sh'

clean:
	docker compose -f lab/docker-compose.yml down -v
	rm -rf lab/work/* lab/java/pkcs11-demo/build lab/java/pkcs11-demo/target lab/kotlin/pkcs11-demo/build lab/kotlin/pkcs11-demo/target lab/csharp/Pkcs11Demo/bin lab/csharp/Pkcs11Demo/obj
