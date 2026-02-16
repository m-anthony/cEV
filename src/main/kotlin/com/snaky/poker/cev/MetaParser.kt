package com.snaky.poker.cev

import java.io.BufferedInputStream
import java.io.InputStream

class MetaParser : AutoCloseable {

    val spins: Map<String, Spin> get() = parsers.fold(mutableMapOf()) {
        acc, parser -> acc.also { it.putAll(parser.spins) }
    }

    private val parsers = listOf(BetclicParser(), IpokerParser(), WinamaxParser())

    fun parseFile(inputStream: InputStream){
        val bufferedStream = BufferedInputStream(inputStream).also { it.mark(2048) }
        val bytes = ByteArray(512)
        val header = String(bytes, 0, bufferedStream.read(bytes))
        bufferedStream.reset()
        val parser = parsers.first { it.validateHeader(header) }
        parser.parseFile(bufferedStream)
    }


    override fun close() = parsers.forEach { it.close() }
}