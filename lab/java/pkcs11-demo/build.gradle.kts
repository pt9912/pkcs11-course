plugins {
    application
}

application {
    mainClass.set("dev.course.pkcs11.Pkcs11Demo")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
