plugins {
    kotlin("jvm") version "2.1.0"
    application
}

application {
    mainClass.set("dev.course.pkcs11.KotlinCsrDemoKt")
}

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
}
