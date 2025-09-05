plugins {
    kotlin("jvm") version "1.9.20"
}

group = "org.pl"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":solver"))
    implementation(project(":generator"))
    implementation("org.junit.jupiter:junit-jupiter:5.8.1")
    implementation("org.jetbrains.kotlin:kotlin-test")

    testImplementation("com.github.tschuchortdev:kotlin-compile-testing:1.5.0")
}

tasks.test {
    useJUnitPlatform()
    val heapSize = (System.getProperty("testMaxHeapSize") ?: "100m") // ограничение памяти для JVM тестов
    maxHeapSize = heapSize
    jvmArgs(
        "-XX:+PrintGCDetails",
        "-Xlog:gc*:file=gc.log:time,uptime,level,tags"
    )
}
kotlin {
    jvmToolchain(11)
}