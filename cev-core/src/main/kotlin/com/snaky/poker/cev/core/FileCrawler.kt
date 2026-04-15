package com.snaky.poker.cev.core

import com.snaky.poker.cev.core.parsers.MetaParser
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.kotlin.logger
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object FileCrawler {
    suspend fun processFileOrDirectory(file: File, parser: MetaParser, isTopLevel: Boolean = true) {

        if (!file.exists()) {
            if(isTopLevel) throw FileNotFoundException("File '$file' does not exists")
            return
        }

        val zipFile = file.extension.equals("zip", ignoreCase = true)
        val level = if(file.isDirectory || zipFile) Level.INFO else Level.TRACE
        logger.log(level) { "processing file $file" }
        when {
            file.isDirectory -> file.listFiles()?.forEach { processFileOrDirectory(it, parser, false) }
            file.isFile && zipFile -> processZipFile(file, parser)
            file.isFile -> file.useSafeInputStream { parser.parseFile(it, file.name) }
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
                        logger.info { "processing zipped file $entryName" }
                        parser.parseFile(zipStream, entryName)
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
}

inline fun <T> File.useSafeInputStream(block: (InputStream) -> T): T {
    return FileChannel.open(this.toPath(), StandardOpenOption.READ).use { channel ->
        Channels.newInputStream(channel).use { block(it) }
    }
}
