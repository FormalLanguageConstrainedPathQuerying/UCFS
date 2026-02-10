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
}

kotlin {
    jvmToolchain(11)
}

application {
    mainClass = "me.vkutuev.MainKt"
}
