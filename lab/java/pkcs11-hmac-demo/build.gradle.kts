plugins {
    application
}

application {
    mainClass.set("dev.course.pkcs11.Pkcs11HmacDemo")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
