plugins {
    java
    kotlin("jvm") version "1.9.20"
    kotlin("plugin.allopen") version "1.9.20"
}

repositories {
    mavenCentral()
    maven("https://releases.usethesource.io/maven/")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.5")
    implementation ("io.github.oshai:kotlin-logging-jvm:5.1.0")
    //needed for .dot parsing
    implementation("org.antlr:antlr4:4.13.1")
    // https://mvnrepository.com/artifact/org.slf4j/slf4j-api
    //needed for kotlin-logging
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("ch.qos.logback:logback-classic:1.5.18")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.8.1")
}

kotlin { jvmToolchain(11) }

tasks.test { useJUnitPlatform() }