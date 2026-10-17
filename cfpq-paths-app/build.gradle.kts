plugins {
    kotlin("jvm") version "2.3.0"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":solver"))

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.8.1")
}

kotlin {
    jvmToolchain(11)
}

tasks.test { useJUnitPlatform() }

application {
    mainClass = "org.ucfs.paths.MainKt"
}
