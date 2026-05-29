plugins {
    kotlin("jvm") version "2.1.0"
    application
}

application {
    mainClass.set("dev.course.pkcs11.KotlinPkcs11DemoKt")
}

kotlin {
    jvmToolchain(21)
}
