package com.snaky.poker.cev.core.watchers

import com.snaky.poker.cev.core.model.Room
import com.snaky.poker.cev.core.model.Spin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.apache.logging.log4j.kotlin.loggerOf
import java.io.Closeable
import java.io.File
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.TimeUnit

sealed class WatcherEvent {
    data class SpinFinished(val spin: Spin) : WatcherEvent()
    data class InProgressUpdate(val activeCount: Int, val staledCount: Int = 0) : WatcherEvent()
    object HeartBeat : WatcherEvent()
}

interface HistoryWatcherConfiguration {
    val sourcesFlow: Flow<Map<Room, List<Path>>>
}

interface RoomWatcher {
    suspend fun onFileCreated(file: File)
}


private val logger = loggerOf(HistoryWatcher::class.java)

class HistoryWatcher(
    private val scope: CoroutineScope,
    private val config: HistoryWatcherConfiguration
) : Closeable {

    private val watchService = FileSystems.getDefault().newWatchService()
    private val activeKeys = mutableMapOf<WatchKey, Pair<Path, Room>>() //path from config, key can point to subdir
    private val _events = Channel<WatcherEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val roomWatchers = mutableMapOf<Room, RoomWatcher>()
    private val roomStatesMutex = Mutex()
    private val roomStates = mutableMapOf<Room, WatcherEvent.InProgressUpdate>()

    init {
        startWorker()

        scope.launch(Dispatchers.IO) {
            config.sourcesFlow.collect { newSources -> applyConfigDiff(newSources) }
        }
    }

    private fun applyConfigDiff(newSources: Map<Room, List<Path>>) {
        val newRoots = newSources.flatMap { (room, paths) -> paths.map { it to room } }.toMap().toMutableMap()
        val processedRoots = mutableSetOf<Path>()

        // cleanup old sources
        val iterator = activeKeys.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val (rootPath, room) = entry.value
            if (newRoots[rootPath] != room) {
                entry.key.cancel()
                iterator.remove()
                logger.info { "Stopped watching: ${entry.key.watchable()} (part of group: $rootPath)" }
            } else {
                processedRoots.add(rootPath) //no change for this one => already processed
            }
        }

        // add new sources
        newRoots.forEach { (rootPath, room) ->
            if (processedRoots.add(rootPath)) registerRecursive(rootPath, rootPath, room)
        }
    }

    private fun registerRecursive(rootPath: Path, currentPath: Path, room: Room) {
        if (!Files.exists(currentPath) || !Files.isDirectory(currentPath)) return

        Files.walkFileTree(currentPath, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                try {
                    if (attrs.isSymbolicLink) return FileVisitResult.SKIP_SUBTREE
                    val key = dir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE)
                    activeKeys[key] = rootPath to room
                    logger.info { "Watching sub-dir: $dir (Root: $rootPath)" }
                } catch (e: Exception) {
                    logger.error(e) { "Failed to register: $dir" }
                }
                return FileVisitResult.CONTINUE
            }
        })
    }

    private fun startWorker() {
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                val key = watchService.poll(1, TimeUnit.SECONDS) ?: continue // Bloquant jusqu'à un événement
                val (rootPath, room) = activeKeys[key] ?: continue

                for (event in key.pollEvents()) {
                    val context = event.context() as? Path ?: continue
                    val fullPath = (key.watchable() as Path).resolve(context)

                    if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) { //skip OVERFLOW
                        if (Files.isDirectory(fullPath, LinkOption.NOFOLLOW_LINKS)) {
                            registerRecursive(rootPath, fullPath, room)
                        } else {
                            processEvent(event, key, room)
                        }
                    }
                }

                if (!key.reset()) activeKeys.remove(key)
            }
        }
    }

    private suspend fun processEvent(
        event: WatchEvent<*>,
        watchKey: WatchKey,
        room: Room,
    ) {
        val watcher = getOrRegisterWatcher(room) ?: return
        val fileName = event.context() as Path
        val dirPath = watchKey.watchable() as Path
        val fullPath = dirPath.resolve(fileName).toFile()
        watcher.onFileCreated(fullPath)
    }

    private fun getOrRegisterWatcher(room: Room): RoomWatcher? {
        return roomWatchers.getOrPut(room) {
            when (room) {
                Room.UNIBET -> UnibetWatcher(scope) { event -> handleRoomEvent(room, event) }
                Room.WINAMAX -> WinamaxWatcher(scope) { event -> handleRoomEvent(room, event) }
                else -> return null
            }
        }
    }

    private suspend fun handleRoomEvent(room: Room, roomEvent: WatcherEvent) {
        val globalEvent = when (roomEvent) {
            is WatcherEvent.InProgressUpdate -> roomStatesMutex.withLock {
                roomStates[room] = roomEvent
                val totalActive = roomStates.values.sumOf { it.activeCount }
                val totalStaled = roomStates.values.sumOf { it.staledCount }
                WatcherEvent.InProgressUpdate(totalActive, totalStaled)
            }
            else -> roomEvent
        }
        _events.send(globalEvent)
    }

    override fun close() {
        try {
            watchService.close()
            logger.info { "WatchService closed successfully." }
        } catch (e: Exception) {
            logger.error(e) { "Error while closing WatchService" }
        }
    }
}