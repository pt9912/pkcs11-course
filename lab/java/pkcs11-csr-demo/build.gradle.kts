plugins {
    application
}

application {
    mainClass.set("dev.course.pkcs11.Pkcs11CsrDemo")
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
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
}
