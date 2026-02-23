package com.snaky.poker.cev.core

import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

suspend fun processFileOrDirectory(file: File, parser: MetaParser) {

    if (!file.exists()) throw FileNotFoundException("File '$file' does not exists")
    println("processing file $file")
    when {
        file.isDirectory -> file.listFiles()?.forEach { processFileOrDirectory(it, parser) }
        file.isFile && file.extension.equals("zip", ignoreCase = true) -> processZipFile(file, parser)
        file.isFile -> file.inputStream().use { parser.parseFile(it) }
    }
}


private suspend fun processZipFile(zipFile: File, parser: MetaParser) {
    zipFile.inputStream().use { processZipStream(it, parser) }
}

private suspend fun processZipStream(inputStream: InputStream, parser: MetaParser) {

    ZipInputStream(inputStream).use { zipStream ->
        zipStream.entriesSequence()
            .filter { !it.isDirectory }
            .forEach { entry ->
                val entryName = entry.name
                if (entryName.endsWith(".zip", ignoreCase = true)) {
                    processZipStream(zipStream, parser)
                } else {
                    parser.parseFile(zipStream)
                }
            }
    }
}

private fun ZipInputStream.entriesSequence(): Sequence<ZipEntry> = sequence {
    var entry = nextEntry
    while (entry != null) {
        yield(entry)
        entry = nextEntry
    }
}
