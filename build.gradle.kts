plugins {
    kotlin("jvm") version "2.1.0"
    application
    id("com.gradleup.shadow") version "9.0.0-beta4"
}

group = "com.androidutil"
version = "1.0.0"

repositories {
    mavenCentral()
    google()
}

dependencies {
    // CLI framework (Clikt 5 includes Mordant 3 transitively)
    implementation("com.github.ajalt.clikt:clikt:5.0.3")

    // Mordant coroutines for progress bars
    implementation("com.github.ajalt.mordant:mordant-coroutines:3.0.2")

    // Bundletool (embedded as library dependency)
    implementation("com.android.tools.build:bundletool:1.17.2")

    // Protobuf for AAB manifest parsing (bundletool transitive dependency)
    implementation("com.android.tools.build:aapt2-proto:8.7.3-12006047")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
}

application {
    mainClass.set("com.androidutil.MainKt")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

tasks.jar {
    // Disable default jar to avoid conflicts with shadow
    enabled = false
}

tasks.shadowJar {
    archiveBaseName.set("androidutil")
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())
    mergeServiceFiles()
}

// Replace jar with shadowJar in all dependent tasks
tasks.named("distZip") { dependsOn(tasks.shadowJar) }
tasks.named("distTar") { dependsOn(tasks.shadowJar) }
tasks.named("startScripts") { dependsOn(tasks.shadowJar) }
tasks.named("startShadowScripts") { dependsOn(tasks.shadowJar) }
