plugins {
    application
}

application {
    mainClass.set("dev.course.pkcs11.Pkcs11CmsDemo")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // BouncyCastle PKIX/CMS — die Standard-Antwort fuer CMS in der JVM.
    // bcprov wird transitiv mitgezogen.
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
}
