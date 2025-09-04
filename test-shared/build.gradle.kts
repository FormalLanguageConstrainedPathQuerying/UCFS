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
    val special_case = System.getProperty("special_case") ?: "nothing"
    val count_for_case = System.getProperty("count_for_case") ?: "50"
    maxHeapSize = heapSize

    jvmArgs(
      //  "-XX:+PrintGCDetails",
    //    "-Xlog:gc*:file=gc.log:time,uptime,level,tags",
        "-Dspecial_case=$special_case",
        "-Dcount_for_case=$count_for_case"
    )
}
kotlin {
    jvmToolchain(11)
}