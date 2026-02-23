package com.snaky.poker.cev.core

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.BufferedInputStream
import java.io.InputStream

class MetaParser : AutoCloseable {

    val spins: Map<String, Spin> get() = parsers.fold(mutableMapOf()) {
        acc, parser -> acc.also { it.putAll(parser.spins) }
    }

    private val parsers = listOf(BetclicParser(), IpokerParser(), WinamaxParser())

    suspend fun parseFile(inputStream: InputStream){
        currentCoroutineContext().ensureActive()
        val bufferedStream = BufferedInputStream(inputStream).also { it.mark(2048) }
        val bytes = ByteArray(512)
        val header = String(bytes, 0, bufferedStream.read(bytes))
        bufferedStream.reset()
        val parser = parsers.firstOrNull { it.validateHeader(header) }
        parser?.parseFile(bufferedStream)
    }


    override fun close() = parsers.forEach { it.close() }
    suspend fun waitForResults() = parsers.forEach { it.waitForResults() }
}