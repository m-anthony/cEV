package com.snaky.poker.cev.core

import com.snaky.poker.cev.core.parsers.MetaParser
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.kotlin.logger
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object FileCrawler {
    suspend fun processFileOrDirectory(file: File, parser: MetaParser) {

        if (!file.exists()) throw FileNotFoundException("File '$file' does not exists")
        val zipFile = file.extension.equals("zip", ignoreCase = true)
        val level = if(file.isDirectory || zipFile) Level.INFO else Level.TRACE
        logger.log(level) { "processing file $file" }
        when {
            file.isDirectory -> file.listFiles()?.forEach { processFileOrDirectory(it, parser) }
            file.isFile && zipFile -> processZipFile(file, parser)
            file.isFile -> file.inputStream().use { parser.parseFile(it, file.name) }
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
