package lk.sugaapps.airwave.engine

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Shared, observable playlist. The Library screen builds it; Home plays through it.
 * Persists selected songs, queue order, and currentIndex to local storage
 * so songs and "Up Next" queue are never lost when the user closes and reopens the app.
 */
object Playlist {
    /**
     * Determines whether a track is AI-generated (Flash news, Time check, Listener shoutout, AI Host line)
     * or a real music track chosen by the user.
     */
    fun isAiContent(title: String, artist: String = "", uriStr: String = ""): Boolean {
        val t = title.lowercase()
        val a = artist.lowercase()
        val u = uriStr.lowercase()
        return title.startsWith("💬") ||
                title.startsWith("Flash News") ||
                title.startsWith("⚡") ||
                title.startsWith("🎙️") ||
                title.startsWith("RJ Time Check") ||
                title.startsWith("RJ ·") ||
                t.contains("flash news") ||
                t.contains("time check") ||
                t.contains("rj shoutout") ||
                t.contains("listener shoutout") ||
                a.contains("listener on-air") ||
                a.contains("co-host") ||
                a.contains("ai rj") ||
                a.contains("flash news") ||
                a.contains("hiru news") ||
                u.contains("rj_news_") ||
                u.contains("rj_time_") ||
                u.contains("rj_reply_") ||
                u.contains("rj_")
    }

    data class Item(
        val uri: Uri,
        val title: String,
        val artist: String,
        val durationMs: Long,
        val isAi: Boolean = isAiContent(title, artist, uri.toString()),
    )

    val items = mutableStateListOf<Item>()
    var currentIndex by mutableIntStateOf(-1)
        private set

    val current: Item? get() = items.getOrNull(currentIndex)
    val musicTracks: List<Item> get() = items.filter { !it.isAi }
    val aiItems: List<Item> get() = items.filter { it.isAi }
    fun hasNext(): Boolean = items.isNotEmpty() && currentIndex < items.size - 1
    fun hasPrev(): Boolean = currentIndex > 0

    var currentPositionPercent: Float = 0f
    var isMusicPlaying: Boolean = false

    /**
     * Calculates the remaining balance duration (in milliseconds) of the currently playing song.
     * Returns 0 if no music track is playing or position is invalid.
     */
    fun getSongBalanceTimeMs(): Long {
        val cur = current ?: return 0L
        if (!isMusicPlaying) return 0L
        val duration = cur.durationMs
        if (duration <= 0L) return 0L
        val elapsed = (currentPositionPercent.coerceIn(0f, 1f) * duration).toLong()
        return (duration - elapsed).coerceAtLeast(0L)
    }

    /**
     * Calculates the projected time when an AI RJ time check will actually reach listeners:
     * Current System Time + Song Balance (Remaining) Time + 20 seconds Icecast relay delay.
     * Automatically rounds to the nearest minute for broadcast accuracy.
     */
    fun calculateProjectedTimeCheckMs(): Long {
        val balanceMs = getSongBalanceTimeMs()
        val streamRelayDelayMs = 20_000L // 20s Icecast listener relay latency
        val targetTimeMs = System.currentTimeMillis() + balanceMs + streamRelayDelayMs
        // Round to nearest minute for authentic on-air broadcast accuracy
        return ((targetTimeMs + 30_000L) / 60_000L) * 60_000L
    }

    private var appContext: Context? = null
    private var isLoaded = false
    private const val FILE_NAME = "airwave_playlist.json"

    /**
     * Initializes playlist state from local storage on app start.
     * Songs are preserved across restarts. Pre-loaded tracks are only added
     * when explicitly requested by the user.
     */
    fun initialize(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
        if (!isLoaded) {
            isLoaded = true
            loadFromDisk(context.applicationContext)
            // Stale AI shoutouts and news from past sessions must never linger across app restarts
            items.removeAll { it.isAi || isAiContent(it.title, it.artist, it.uri.toString()) }
            if (currentIndex !in items.indices) {
                currentIndex = if (items.isNotEmpty()) 0 else -1
            }
        }
    }

    /**
     * Loads royalty-free test tracks packaged in assets/tracks/ into the app library
     * and Up Next queue. Copies files to internal storage, extracts metadata, and registers
     * them with FileProvider.
     * Returns the number of newly added tracks.
     */
    fun loadDefaultTracksFromAssets(context: Context, force: Boolean = false): Int {
        val tracksDir = File(context.filesDir, "tracks")
        if (!tracksDir.exists()) tracksDir.mkdirs()

        val assetFiles = runCatching {
            context.assets.list("tracks")?.filter { it.endsWith(".mp3", ignoreCase = true) }
        }.getOrNull() ?: return 0

        var addedCount = 0
        assetFiles.sorted().forEach { fileName ->
            val destFile = File(tracksDir, fileName)
            if (!destFile.exists() || destFile.length() == 0L) {
                runCatching {
                    context.assets.open("tracks/$fileName").use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }

            if (destFile.exists() && destFile.length() > 0L) {
                val uri = runCatching {
                    androidx.core.content.FileProvider.getUriForFile(
                        context, "${context.packageName}.fileprovider", destFile
                    )
                }.getOrNull() ?: return@forEach

                // Clean title from filename, e.g. "Across the Oceans (No Copyright).mp3" -> "Across the Oceans"
                val rawName = fileName.removeSuffix(".mp3")
                val cleanTitle = rawName
                    .replace("(No Copyright)", "", ignoreCase = true)
                    .replace("(Vocal)", "Vocal")
                    .trim()

                var title = cleanTitle
                var artist = "Royalty Free"
                var durationMs = 0L

                val retriever = android.media.MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    val metaTitle = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
                    if (!metaTitle.isNullOrBlank()) title = metaTitle.trim()
                    val metaArtist = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    if (!metaArtist.isNullOrBlank()) artist = metaArtist.trim()
                    durationMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull() ?: 0L
                } catch (_: Exception) {
                } finally {
                    runCatching { retriever.release() }
                }

                val item = Item(uri, title, artist, durationMs)

                val alreadyExists = items.any {
                    it.title.equals(title, ignoreCase = true) || it.uri == uri
                }

                if (!alreadyExists || force) {
                    if (!alreadyExists) {
                        items.add(item)
                        addedCount++
                    }
                }
            }
        }

        if (currentIndex < 0 && items.isNotEmpty()) {
            currentIndex = 0
        }
        if (addedCount > 0) {
            save()
        }
        return addedCount
    }

    /**
     * Removes pre-loaded royalty-free test tracks from the library and playlist.
     * User's own custom picked music and AI clips remain untouched.
     * Returns the count of removed tracks.
     */
    fun removeTestTracks(): Int {
        val testTracks = items.filter { !it.isAi && (it.artist == "Royalty Free" || it.uri.toString().contains("/tracks/")) }
        val count = testTracks.size
        if (count > 0) {
            items.removeAll(testTracks)
            if (currentIndex >= items.size) {
                currentIndex = (items.size - 1).coerceAtLeast(-1)
            }
            save()
        }
        return count
    }

    private fun save() {
        val ctx = appContext ?: return
        runCatching {
            val root = JSONObject()
            // Map currentIndex to the non-AI music tracks
            val musicOnly = items.filter { !it.isAi && !isAiContent(it.title, it.artist, it.uri.toString()) }
            val currentItem = items.getOrNull(currentIndex)
            val savedIdx = if (currentItem != null && !currentItem.isAi) {
                musicOnly.indexOf(currentItem).coerceAtLeast(-1)
            } else if (musicOnly.isNotEmpty()) {
                0
            } else {
                -1
            }
            root.put("currentIndex", savedIdx)

            val arr = JSONArray()
            // Only persist real music tracks — NEVER persist ephemeral shoutouts or AI clips!
            musicOnly.forEach { item ->
                val obj = JSONObject()
                obj.put("uri", item.uri.toString())
                obj.put("title", item.title)
                obj.put("artist", item.artist)
                obj.put("durationMs", item.durationMs)
                obj.put("isAi", false)
                arr.put(obj)
            }
            root.put("items", arr)
            val file = File(ctx.filesDir, FILE_NAME)
            file.writeText(root.toString())
        }
    }

    private fun loadFromDisk(context: Context) {
        runCatching {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) return
            val jsonStr = file.readText()
            if (jsonStr.isBlank()) return
            val root = JSONObject(jsonStr)
            val savedIndex = root.optInt("currentIndex", -1)
            val arr = root.optJSONArray("items") ?: return
            val loadedItems = mutableListOf<Item>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val uriStr = obj.optString("uri", "")
                if (uriStr.isNotBlank()) {
                    val uri = Uri.parse(uriStr)
                    val title = obj.optString("title", "Unknown track")
                    val artist = obj.optString("artist", "Unknown artist")
                    val durationMs = obj.optLong("durationMs", 0L)
                    val isAi = obj.optBoolean("isAi", false) || isAiContent(title, artist, uriStr)
                    // Old shoutouts or AI clips from past sessions must not be restored into the queue
                    if (!isAi) {
                        loadedItems.add(Item(uri, title, artist, durationMs, false))
                    }
                }
            }
            items.clear()
            items.addAll(loadedItems)
            if (savedIndex in items.indices) {
                currentIndex = savedIndex
            } else if (items.isNotEmpty()) {
                currentIndex = 0
            } else {
                currentIndex = -1
            }
        }
    }

    fun add(item: Item) {
        if (items.none { it.uri == item.uri }) {
            items.add(item)
            if (currentIndex < 0) {
                currentIndex = 0
            }
            save()
        }
    }

    fun insert(index: Int, item: Item) {
        val target = index.coerceIn(0, items.size)
        items.add(target, item)
        if (target <= currentIndex && currentIndex >= 0) currentIndex += 1
        save()
    }

    /** Add [item] and move it to play right after the current track. */
    fun enqueueNext(item: Item) {
        add(item)
        val idx = items.indexOfFirst { it.uri == item.uri }
        if (idx >= 0) playNext(idx)
        save()
    }

    /**
     * Enqueues an on-air spoken audio clip (like an AI RJ chat shoutout or time check)
     * right after the currently playing song. If other AI shoutouts are already queued
     * for the end of this song, it places this one in sequence right after them.
     */
    fun enqueueAtEndOfCurrentSong(item: Item) {
        if (items.none { it.uri == item.uri }) {
            val cur = currentIndex
            if (cur < 0) {
                add(item)
            } else {
                var insertPos = cur + 1
                while (insertPos < items.size && (
                    items[insertPos].title.startsWith("💬") ||
                    items[insertPos].title.startsWith("Flash News") ||
                    items[insertPos].artist.contains("Co-Host")
                )) {
                    insertPos++
                }
                items.add(insertPos, item)
                save()
            }
        }
    }

    fun remove(index: Int) {
        if (index !in items.indices) return
        items.removeAt(index)
        if (index < currentIndex) currentIndex -= 1
        else if (index == currentIndex) {
            currentIndex = if (items.isNotEmpty()) index.coerceIn(0, items.size - 1) else -1
        }
        save()
    }

    /** Move the track at [index] so it plays right after the current track. */
    fun playNext(index: Int) {
        if (index !in items.indices || index == currentIndex) return
        val item = items.removeAt(index)
        var cur = currentIndex
        if (index < cur) cur -= 1
        val target = (cur + 1).coerceIn(0, items.size)
        items.add(target, item)
        currentIndex = cur
        save()
    }

    /** Move item from [fromIndex] to [toIndex] (reorders the playlist). */
    fun move(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in items.indices || toIndex !in items.indices || fromIndex == toIndex) return
        val currentTrack = current
        val item = items.removeAt(fromIndex)
        items.add(toIndex, item)
        // Maintain currentIndex pointing to the same playing track
        currentIndex = items.indexOf(currentTrack)
        save()
    }

    /** Move item at [index] up by one position. */
    fun moveUp(index: Int) {
        if (index > 0) move(index, index - 1)
    }

    /** Move item at [index] down by one position. */
    fun moveDown(index: Int) {
        if (index < items.size - 1) move(index, index + 1)
    }

    /** Load + play the item at [index] through the engine and update now-playing. */
    fun play(index: Int, engine: BroadcastEngine, context: Context, startPercent: Float = 0f) {
        initialize(context)
        val item = items.getOrNull(index) ?: return
        currentIndex = index
        save()
        engine.selectTrack(context, item.uri, autoPlay = true, startPercent = startPercent)
        engine.setCurrentTrackInfo(item.title, item.artist)
    }

    fun next(engine: BroadcastEngine, context: Context) {
        if (hasNext()) play(currentIndex + 1, engine, context)
    }

    fun prev(engine: BroadcastEngine, context: Context) {
        if (hasPrev()) play(currentIndex - 1, engine, context)
    }

    /**
     * Clears all items and resets index.
     */
    fun clear() {
        items.clear()
        currentIndex = -1
        save()
    }

    /**
     * Clears all AI generated clips (news, time checks, shoutouts) from the playlist,
     * keeping real music tracks intact.
     */
    fun clearAiClips() {
        val cur = current
        items.removeAll { it.isAi }
        currentIndex = if (cur != null) items.indexOf(cur).coerceAtLeast(-1) else -1
        save()
    }
}
