import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    implementation(project(":cev-core"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(compose.desktop.currentOs)
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material)
    implementation(compose.ui)
    implementation(compose.materialIconsExtended)
}

val uiGeneratedDir = layout.buildDirectory.dir("generated/ui-resources")
val generateVersionProperties by tasks.registering {
    group = "generation"
    val versionFile = uiGeneratedDir.get().file("version.properties")

    inputs.property("name", project.group)
    inputs.property("version", project.version)
    outputs.file(versionFile)

    doLast {
        val props = Properties()
        props.setProperty("group", project.group.toString())
        props.setProperty("version", project.version.toString())

        versionFile.asFile.parentFile.mkdirs()
        versionFile.asFile.outputStream().use {
            props.store(it, "Generated Build Properties")
        }

    }
}


sourceSets.main {
    resources {
        srcDir(generateVersionProperties.map { it.outputs.files.singleFile.parentFile })
    }
}

tasks.named("processResources") {
    dependsOn(generateVersionProperties)
}

compose.desktop {
    application {
        mainClass = "com.snaky.poker.cev.ui.UiMainKt"

        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe
            )
            packageName = "PokerEVCalculator"
            packageVersion = project.version.toString().replace("-SNAPSHOT", ".0").replace("-", ".")
        }
    }
}
