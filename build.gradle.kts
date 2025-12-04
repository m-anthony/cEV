import org.gradle.api.artifacts.Configuration

plugins {
    kotlin("jvm") version "2.2.0"
    id("org.gradle.application")
}

group = "com.snaky.poker"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":SKPokerEval:evaluator"))
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("com.snaky.poker.MainKt")
}

val cacheDir = layout.buildDirectory.dir("precalculated_assets")
sourceSets.main.get().resources.srcDir(cacheDir)

val precalculatedCacheFile = cacheDir.get().file("preflop_equity.dat") //EquityCacheKt.FILENAME
val mainSourceSet = sourceSets.main.get()

val calculateCacheClasspath: Configuration by configurations.creating {
    // Étend le classpath de runtime standard.
    extendsFrom(configurations["implementation"])
}
tasks.register<JavaExec>("calculateEquityCache") {
    classpath = mainSourceSet.output.classesDirs + calculateCacheClasspath
    mainClass.set("com.snaky.poker.EquityCacheKt")
    args = listOf(cacheDir.get().asFile.absolutePath)
    inputs.files(sourceSets.main.get().allSource)
    outputs.file(precalculatedCacheFile)

    onlyIf {
        !precalculatedCacheFile.asFile.exists()
    }

    doFirst {
        cacheDir.get().asFile.mkdirs()
    }
}

tasks.getByName("processResources").dependsOn("calculateEquityCache")
tasks.named("run") {
    dependsOn("calculateEquityCache")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}