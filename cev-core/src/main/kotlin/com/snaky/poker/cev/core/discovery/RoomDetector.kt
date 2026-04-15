package com.snaky.poker.cev.core.discovery

import com.snaky.poker.cev.core.model.Room
import org.apache.logging.log4j.kotlin.logger
import java.io.IOException
import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.isDirectory

object RoomDetector {
    private const val MAX_DEPTH = 5

    private class RoomConflictException : RuntimeException()

    fun detect(rootPath: String): Room? {
        logger.info { "Starting room detection for path $rootPath" }
        val root = Paths.get(rootPath)
        if (!Files.exists(root) || !root.isDirectory()) return null

        return try {
            scanDirectory(root, 0, null)
        } catch (_: RoomConflictException ) {
            null
        } catch (_: IOException) {
            null
        }
    }

    private fun scanDirectory(path: Path, depth: Int, initialRoom: Room?): Room? {
        if (depth > MAX_DEPTH) return initialRoom

        var currentRoom = initialRoom

        for (room in Room.entries) {
            // if there is a candidate, don't test its pattern anymore
            val pattern = room.takeUnless { it == currentRoom }?.getDetectionPattern() ?: continue

            Files.newDirectoryStream(path, pattern).use { stream ->
                if (stream.any()) {
                    if (currentRoom != null) throw RoomConflictException()
                    currentRoom = room
                }
            }
        }

        // Check sub directories
        val directoryFilter = DirectoryStream.Filter<Path> { Files.isDirectory(it) }
        Files.newDirectoryStream(path, directoryFilter).use { stream ->
            for (subDir in stream) {
                currentRoom = scanDirectory(subDir, depth + 1, currentRoom)
            }
        }

        return currentRoom
    }
}

fun Room.getDetectionPattern(): String? = when (this) {
    Room.WINAMAX -> "*Expresso*.txt"
    Room.UNIBET -> "*Power Spin*.txt"
    Room.IPOKER -> "[0-9]*.xml"
    Room.BETCLIC -> null
}