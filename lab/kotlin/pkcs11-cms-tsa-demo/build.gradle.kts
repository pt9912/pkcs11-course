plugins {
    kotlin("jvm") version "2.1.0"
    application
}

application {
    mainClass.set("dev.course.pkcs11.KotlinCmsTsaDemoKt")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
}
