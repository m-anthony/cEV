plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

dependencies {
    implementation(project(":SKPokerEval:evaluator"))
    implementation(libs.kotlinx.coroutines.core)
}


application {
    mainClass.set("com.snaky.poker.cev.core.MainKt")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
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

val generatorClasspath: Configuration by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
    extendsFrom(configurations.implementation.get())
}


val generatedResourcesDir = layout.buildDirectory.dir("generated/resources")

val generateEquityCache = tasks.register<JavaExec>("generateEquityCache") {
    group = "generation"

    classpath = sourceSets.main.get().output.classesDirs + generatorClasspath

    mainClass.set("com.snaky.poker.cev.core.EquityCacheKt")

    val outputFile = generatedResourcesDir.get().file("preflop_equity.dat")
    args = listOf(generatedResourcesDir.get().asFile.absolutePath)
    outputs.file(outputFile)

    dependsOn(tasks.named("compileKotlin"))

    onlyIf { !outputFile.asFile.exists() }

    doFirst {
        generatedResourcesDir.get().asFile.mkdirs()
    }
}

sourceSets.main {
    resources.srcDir(generateEquityCache.map { it.outputs.files.singleFile.parentFile })
}

tasks.named("processResources") {
    dependsOn(generateEquityCache)
}
