import org.gradle.api.artifacts.Configuration

plugins {
    kotlin("jvm") version "2.2.0"
    id("org.gradle.application")
}

group = "com.snaky.poker"
version = "0.2-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":SKPokerEval:evaluator"))
    testImplementation(kotlin("test"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}

application {
    mainClass.set("com.snaky.poker.cev.MainKt")
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
    mainClass.set("com.snaky.poker.cev.EquityCacheKt")
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

tasks.named<Zip>("distZip") {
    from(projectDir) {
        include("cev.config.txt.example")
        rename { it.removeSuffix(".example") }
        into("${application.applicationName}-${project.version}/bin")
    }
}

tasks.named<Tar>("distTar") {
    from(projectDir) {
        include("cev.config.txt.example")
        rename { it.removeSuffix(".example") }
        into("${application.applicationName}-${project.version}/bin")
    }
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}