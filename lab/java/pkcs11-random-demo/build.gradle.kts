plugins {
    application
}

application {
    mainClass.set("dev.course.pkcs11.Pkcs11RandomDemo")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
