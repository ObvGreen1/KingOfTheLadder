// Gradle Kotlin DSL build for the King of the Ladder plugin.
// Only the `java` plugin is applied: this is a plain dependency-resolution build
// against paper-api, no paperweight / run-paper plugins.

plugins {
    java
}

// Coordinates. Must match plugin.yml's name and the jar name below.
group = "me.obvgreen"
version = "1.0.0"
description = "KingOfTheLadder"

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/releases/")
}

java {
    toolchain {
        // Java 25, as required by Paper 26.2.
        // Toolchain (not just `release`) so the build does not depend on JAVA_HOME.
        // gradle.properties pins the only allowed JDK install to the local Zulu 25
        // and disables auto-detect/auto-download, so nothing is fetched for a JDK.
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

dependencies {
    // compileOnly: the server supplies all three at runtime, so nothing is bundled.
    // Paper ships these on its own classpath; nothing is bundled into the jar.
    compileOnly("io.papermc.paper:paper-api:26.2.build.129-stable")
    compileOnly("me.clip:placeholderapi:2.12.3")
    // Must match the sqlite-jdbc Paper 26.2 bundles (META-INF/libraries.list).
    compileOnly("org.xerial:sqlite-jdbc:3.49.1.0")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)      // belt-and-braces with the toolchain above
    options.compilerArgs.addAll(listOf("-Xlint:all,-serial,-processing"))
}

// Mirrors the two <resource> blocks: filter plugin.yml, copy everything else verbatim.
tasks.processResources {
    val filterProperties = mapOf("project" to mapOf("version" to project.version.toString()))
    inputs.properties(filterProperties)
    filesMatching("plugin.yml") {
        expand(filterProperties) // substitutes ${project.version} -> 1.0.0
    }
}

// The shipped artefact name.
tasks.jar {
    archiveFileName.set("KingOfTheLadder-${project.version}.jar")
}

// --------------------------------------------------------------------------- rating check
// tools/GlickoCheck.java asserts the eight Glicko-2 steps against the worked example in
// Mark Glickman's own paper. It is not a JUnit test: it needs no test framework, prints each
// assertion as it goes, and exits non-zero on the first failure by throwing.
//
// The plugin classes plus paper-api and adventure-api are enough to run it, so no test-only
// dependency is added.
val compileTools by tasks.registering(JavaCompile::class) {
    group = "verification"
    description = "Compiles the standalone rating check in tools/."

    source = fileTree("tools") { include("*.java") }
    destinationDirectory.set(layout.buildDirectory.dir("tools"))
    classpath = sourceSets.main.get().output + configurations.compileClasspath.get()
    options.release.set(25)
    options.encoding = "UTF-8"
}

val checkGlicko by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Verifies the Glicko-2 implementation against the paper's worked example."

    dependsOn(compileTools)
    // compileClasspath, not runtimeClasspath: every dependency is compileOnly, so the
    // runtime configuration is deliberately empty.
    classpath = files(layout.buildDirectory.dir("tools")) +
            sourceSets.main.get().output +
            configurations.compileClasspath.get()
    mainClass.set("GlickoCheck")
}