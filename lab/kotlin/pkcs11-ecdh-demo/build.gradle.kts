plugins {
    kotlin("jvm") version "2.1.0"
    application
}

application {
    mainClass.set("dev.course.pkcs11.KotlinEcdhDemoKt")
}

kotlin {
    jvmToolchain(21)
}
