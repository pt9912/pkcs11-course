plugins {
    application
}

application {
    mainClass.set("dev.course.pkcs11.Pkcs11CmsTsaDemo")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // bcpkix-jdk18on enthaelt CMS, TSP und PKIX. bcprov wird transitiv gezogen.
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
}
