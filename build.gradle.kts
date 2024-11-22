plugins {
    kotlin("jvm") version "2.0.21"
}

group = "com.serranofp"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

dependencies {
    implementation(kotlin("reflect"))
    // implementation("org.ow2.asm:asm:9.7.1")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}