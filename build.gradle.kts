// Gradle Kotlin DSL build for the King of the Ladder plugin.
// Only the `java` plugin is applied: this is a plain dependency-resolution build
// against paper-api, no paperweight / run-paper plugins.

import org.gradle.jvm.toolchain.JavaLanguageVersion

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

// The two verification tasks below run the standalone checks in tools/. Neither is a JUnit test:
// each needs no test framework, prints every assertion as it goes, and exits non-zero on the
// first failure by throwing. The plugin classes plus paper-api and adventure-api are enough to run
// them, so no test-only dependency is added.
//
// GlickoCheck asserts the eight Glicko-2 steps against the worked example in Mark Glickman's own
// paper. PackageCheck enforces the package layout: files in the directory their package declares,
// no fully-qualified plugin references, only declared cross-package dependencies, and no cycles.
// Shared with the verification tasks below so every compile and every run uses the same JDK 25,
// whatever JVM Gradle itself happens to be running under.
val jdk25Compiler = javaToolchains.compilerFor {
    languageVersion.set(JavaLanguageVersion.of(25))
}
val jdk25Launcher = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(25))
}

val compileTools = tasks.register<JavaCompile>("compileTools") {
    group = "verification"
    description = "Compiles the standalone rating check in tools/."

    source = fileTree("tools") { include("*.java") }
    destinationDirectory.set(layout.buildDirectory.dir("tools"))
    classpath = files(
        sourceSets.main.get().output,
        configurations.compileClasspath
    )
    javaCompiler.set(jdk25Compiler)
    options.release.set(25)
    options.encoding = "UTF-8"
}

val checkGlicko = tasks.register<JavaExec>("checkGlicko") {
    group = "verification"
    description = "Verifies the Glicko-2 implementation against the paper's worked example."

    dependsOn(compileTools)
    // compileClasspath, not runtimeClasspath: every dependency is compileOnly, so the
    // runtime configuration is deliberately empty.
    classpath = files(
        layout.buildDirectory.dir("tools"),
        sourceSets.main.get().output,
        configurations.compileClasspath
    )
    mainClass.set("GlickoCheck")
    javaLauncher.set(jdk25Launcher)
}

// tools/PackageCheck.java guards the package layout. It reads the source tree from disk, so it
// runs from the project directory and needs nothing on the classpath.
val checkPackages = tasks.register<JavaExec>("checkPackages") {
    group = "verification"
    description = "Verifies the package layout: file paths, imports, declared dependencies, cycles."

    dependsOn(compileTools)
    classpath = files(layout.buildDirectory.dir("tools"))
    mainClass.set("PackageCheck")
    javaLauncher.set(jdk25Launcher)
    workingDir = projectDir
}

// `build` runs the layout check, so a misplaced class or an undeclared dependency fails the
// build rather than waiting to be noticed in review.
tasks.named("check") {
    dependsOn(checkPackages)
}