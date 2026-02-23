import java.util.Properties
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":cev-core"))

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(compose.desktop.currentOs)
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material)
    implementation(compose.ui)
    implementation(compose.materialIconsExtended)
}
val appName = "Spin-cEV-calculator"
val uiGeneratedDir = layout.buildDirectory.dir("generated/ui-resources")
val generateVersionProperties by tasks.registering {
    group = "generation"
    val versionFile = uiGeneratedDir.get().file("version.properties")

    inputs.property("name", appName)
    inputs.property("version", project.version)
    outputs.file(versionFile)

    doLast {
        val props = Properties()
        props.setProperty("name", appName)
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
    dependsOn(generateVersionProperties, generateIco, generateMacIcon)
}

compose.desktop {
    application {
        mainClass = "com.snaky.poker.cev.ui.UiMainKt"

        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi
            )
            packageName = appName
            packageVersion = project.version.toString().replace("-SNAPSHOT", ".0").replace("-", ".")

            windows {
                iconFile.set(uiGeneratedDir.map { it.file("icon.ico") })
                upgradeUuid = "47230dba-4d99-44b9-8553-85762b4b6f96"
                menu = true
            }

            macOS {
                iconFile.set(uiGeneratedDir.map { it.file("icon.icns") })
                bundleID = "com.snaky.poker.cev"
            }
        }
    }
}

val generateIco by tasks.registering {
    val inputFile = file("src/main/resources/icon.png")
    val outputDir = file(uiGeneratedDir.get().asFile.path)
    val outputFile = file("${outputDir.path}/icon.ico")

    inputs.file(inputFile)
    outputs.file(outputFile)

    doLast {
        if (!inputFile.exists()) return@doLast
        outputDir.mkdirs()

        val source = ImageIO.read(inputFile)

        // We will create a standard 256x256 ICO
        val scaled = BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB)
        val g = scaled.createGraphics()
        g.drawImage(source, 0, 0, 256, 256, null)
        g.dispose()

        FileOutputStream(outputFile).use { fos ->
            val pngData = ByteArrayOutputStream().apply {
                ImageIO.write(scaled, "png", this)
            }.toByteArray()

            // ICO Header (6 bytes)
            val header = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
            header.putShort(0) // Reserved
            header.putShort(1) // Type 1 = ICO
            header.putShort(1) // Number of images
            fos.write(header.array())

            // Icon Directory Entry (16 bytes)
            val entry = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            entry.put(256.toByte()) // Width
            entry.put(256.toByte()) // Height
            entry.put(0.toByte())   // Color palette
            entry.put(0.toByte())   // Reserved
            entry.putShort(1)       // Color planes
            entry.putShort(32)      // Bits per pixel
            entry.putInt(pngData.size) // Image size
            entry.putInt(6 + 16)    // Offset to image data
            fos.write(entry.array())

            // Image Data
            fos.write(pngData)
        }
        println("Real ICO file generated at ${outputFile.absolutePath}")
    }
}

val generateMacIcon by tasks.registering {
    val inputFile = file("src/main/resources/icon.png")
    val outputDir = file(uiGeneratedDir.get().asFile.path)
    val outputFile = file("${outputDir.path}/icon.icns")
    inputs.file(inputFile)
    outputs.file(outputFile)

    doLast {
        if (!inputFile.exists()) return@doLast
        outputDir.mkdirs()

        val source = ImageIO.read(inputFile)
        val pngData = ByteArrayOutputStream().apply {
            ImageIO.write(source, "png", this)
        }.toByteArray()

        FileOutputStream(outputFile).use { fos ->
            // ICNS Header: 'icns' + Total File Size (Big Endian)
            val totalSize = 8 + 8 + pngData.size
            val header = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
            header.put("icns".toByteArray())
            header.putInt(totalSize)
            fos.write(header.array())

            // Block for 1024x1024 (Type 'ic10')
            // This is the most modern format for Retina displays
            val blockHeader = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
            blockHeader.put("ic10".toByteArray())
            blockHeader.putInt(8 + pngData.size)
            fos.write(blockHeader.array())

            // The image data (PNG format is allowed inside these blocks)
            fos.write(pngData)
        }
        println("Verified ICNS with 'icns' signature generated at ${outputFile.absolutePath}")
    }
}