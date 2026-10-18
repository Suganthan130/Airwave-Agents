package lk.sugaapps.airwave.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.core.content.FileProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import lk.sugaapps.airwave.engine.BroadcastEngine
import lk.sugaapps.airwave.engine.Playlist

/**
 * Represents a listener on-air chat message and its AI RJ spoken shoutout.
 */
data class StationChatMessage(
    val id: String,
    val stationId: String,
    val userId: String,
    val userName: String,
    val userCountry: String,
    val text: String,
    val status: String, // "queued", "processing", "replied", "skipped"
    val replyText: String = "",
    val replyAudioBase64: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isQueuedOnAir: Boolean = false,
    val isPlayingOnAir: Boolean = false,
    val isPlayedOnAir: Boolean = false,
    val isSongRequest: Boolean = false,
    val songFound: Boolean = false,
    val requestedSongTitle: String = "",
)

/**
 * Manages live listener chat messages and automates on-air AI Radio Jockey (RJ) voice replies.
 * - Listens in real time to the Firestore `messages` collection for this station.
 * - Converts AI-synthesized Gemini RJ replies into audio clips.
 * - Automatically enqueues spoken shoutouts to play on-air at the end of each currently playing song.
 * - Allows broadcasters to review all incoming chats with user location in real-time.
 */
object ChatOnAirManager {

    private const val TAG = "ChatOnAirManager"
    private const val PREFS_NAME = "airwave_chat_onair_prefs"
    private const val KEY_QUEUED_IDS = "queued_message_ids"
    private const val KEY_PLAYED_IDS = "played_message_ids"

    private val _messagesFlow = MutableStateFlow<List<StationChatMessage>>(emptyList())
    val messagesFlow: StateFlow<List<StationChatMessage>> = _messagesFlow.asStateFlow()

    private val _onAirNotificationFlow = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val onAirNotificationFlow: SharedFlow<String> = _onAirNotificationFlow.asSharedFlow()

    private var listenerRegistration: ListenerRegistration? = null
    private var activeStationId: String = ""
    private var coroutineScope: CoroutineScope? = null
    private var sessionStartTime: Long = 0L
    @Volatile
    private var isInitialSnapshot: Boolean = true

    private val queuedMessageIds = mutableSetOf<String>()
    private val playedMessageIds = mutableSetOf<String>()
    private var isPrefsLoaded = false

    private fun loadPrefs(context: Context) {
        if (isPrefsLoaded) return
        isPrefsLoaded = true
        val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        queuedMessageIds.addAll(p.getStringSet(KEY_QUEUED_IDS, emptySet()) ?: emptySet())
        playedMessageIds.addAll(p.getStringSet(KEY_PLAYED_IDS, emptySet()) ?: emptySet())
    }

    private fun savePrefs(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        p.edit()
            .putStringSet(KEY_QUEUED_IDS, HashSet(queuedMessageIds))
            .putStringSet(KEY_PLAYED_IDS, HashSet(playedMessageIds))
            .apply()
    }

    /**
     * Starts the realtime Firestore listener for the broadcaster's station.
     */
    fun start(context: Context, stationId: String, scope: CoroutineScope) {
        if (stationId.isBlank()) return
        if (listenerRegistration != null && activeStationId == stationId) return

        stop()
        activeStationId = stationId
        coroutineScope = scope
        sessionStartTime = System.currentTimeMillis()
        isInitialSnapshot = true
        loadPrefs(context)

        val appContext = context.applicationContext
        val db = FirebaseFirestore.getInstance()

        Log.i(TAG, "Starting realtime chat listener for station: $stationId")

        // Order by createdAt descending, limited to the latest 50 messages
        val query = db.collection("messages")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)

        listenerRegistration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Chat messages listen error: ${error.message}")
                return@addSnapshotListener
            }

            if (snapshot == null) return@addSnapshotListener

            scope.launch(Dispatchers.IO) {
                val list = mutableListOf<StationChatMessage>()
                val isFirst = isInitialSnapshot
                isInitialSnapshot = false

                for (doc in snapshot.documents) {
                    val sId = doc.getString("stationId") ?: "global"
                    // Filter to this station or global broadcast
                    if (!sId.trim().equals(stationId.trim(), ignoreCase = true) &&
                        !sId.trim().equals("global", ignoreCase = true)
                    ) continue

                    val id = doc.id
                    val userId = doc.getString("userId") ?: ""
                    val userName = doc.getString("userName") ?: "Listener"
                    val userCountry = doc.getString("userCountry") ?: "Global"
                    val text = doc.getString("text") ?: ""
                    val status = doc.getString("status") ?: "queued"
                    val replyText = doc.getString("replyText") ?: ""
                    val replyAudioBase64 = doc.getString("replyAudioBase64") ?: ""
                    val isSongReq = doc.getBoolean("isSongRequest") ?: false
                    val songFound = doc.getBoolean("songFound") ?: false
                    val reqSongTitle = doc.getString("requestedSongTitle") ?: ""
                    val createdAt = runCatching { doc.getTimestamp("createdAt")?.toDate()?.time }.getOrNull()
                        ?: runCatching { doc.getLong("createdAt") }.getOrNull()
                        ?: runCatching { doc.getDouble("createdAt")?.toLong() }.getOrNull()
                        ?: runCatching { doc.getString("createdAt")?.toLongOrNull() }.getOrNull()
                        ?: System.currentTimeMillis()

                    val isOldMessage = isFirst || createdAt < (sessionStartTime - 15_000L)
                    val isQueued = queuedMessageIds.contains(id)
                    // "Played" is ONLY what we actually aired (tracked by id). Old
                    // messages are no longer falsely flagged as spoken.
                    val isPlayed = playedMessageIds.contains(id)
                    // "On air now" is the clip currently playing, matched by its exact
                    // source message id — not a fuzzy name/title substring.
                    val isPlaying = Playlist.current?.sourceId == id

                    val msg = StationChatMessage(
                        id = id,
                        stationId = sId,
                        userId = userId,
                        userName = userName,
                        userCountry = userCountry,
                        text = text,
                        status = status,
                        replyText = replyText,
                        replyAudioBase64 = replyAudioBase64,
                        createdAt = createdAt,
                        isQueuedOnAir = isQueued,
                        isPlayingOnAir = isPlaying,
                        isPlayedOnAir = isPlayed,
                        isSongRequest = isSongReq,
                        songFound = songFound,
                        requestedSongTitle = reqSongTitle,
                    )
                    list.add(msg)

                    // Old messages (from past sessions / before this session started) are
                    // never auto-enqueued — but we no longer mark them "played", so their
                    // badge reflects their real state instead of a false "Spoken".
                    if (!isOldMessage) {
                        // NEW message that arrived live during the current session!
                        // 1. If message is replied with audio and not yet queued or played -> Auto-enqueue for end of song!
                        if (status == "replied" && replyAudioBase64.isNotBlank() && !isQueued && !isPlayed) {
                            tryEnqueueForEndOfSong(appContext, msg)
                        }

                        // 2. Active fail-safe: if status is "queued" for > 3 seconds, trigger Cloud Function to guarantee reply
                        if (status == "queued" && (System.currentTimeMillis() - createdAt) > 3000) {
                            triggerBackgroundProcess(id, msg)
                        }
                    }
                }

                savePrefs(appContext)

                // Always sort by epoch time milliseconds DESCENDING so newest incoming chats are at the top!
                list.sortByDescending { it.createdAt }

                _messagesFlow.value = list
            }
        }
    }

    /**
     * Stops the realtime listener.
     */
    fun stop() {
        listenerRegistration?.remove()
        listenerRegistration = null
    }

    /**
     * One-shot manual refresh: re-fetches the latest messages from the server and
     * updates [messagesFlow]. Backs the "refresh" button for when the realtime feed
     * looks stale. Display-only — it does not change on-air queue/play state, so it
     * can never double-air a message.
     */
    fun refresh(context: Context, stationId: String, scope: CoroutineScope) {
        if (stationId.isBlank()) return
        loadPrefs(context)
        val db = FirebaseFirestore.getInstance()
        db.collection("messages")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .get()
            .addOnSuccessListener { snapshot ->
                scope.launch(Dispatchers.IO) {
                    val list = snapshot.documents
                        .mapNotNull { doc -> docToMessage(doc, stationId) }
                        .sortedByDescending { it.createdAt }
                    _messagesFlow.value = list
                    Log.i(TAG, "Manual refresh loaded ${list.size} messages")
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Manual refresh failed: ${e.message}")
            }
    }

    /** Parses a Firestore doc into a display [StationChatMessage], or null if it is for another station. */
    private fun docToMessage(
        doc: com.google.firebase.firestore.DocumentSnapshot,
        stationId: String,
    ): StationChatMessage? {
        val sId = doc.getString("stationId") ?: "global"
        if (!sId.trim().equals(stationId.trim(), ignoreCase = true) &&
            !sId.trim().equals("global", ignoreCase = true)
        ) return null

        val id = doc.id
        val userName = doc.getString("userName") ?: "Listener"
        val replyText = doc.getString("replyText") ?: ""
        val createdAt = runCatching { doc.getTimestamp("createdAt")?.toDate()?.time }.getOrNull()
            ?: runCatching { doc.getLong("createdAt") }.getOrNull()
            ?: runCatching { doc.getDouble("createdAt")?.toLong() }.getOrNull()
            ?: runCatching { doc.getString("createdAt")?.toLongOrNull() }.getOrNull()
            ?: System.currentTimeMillis()

        return StationChatMessage(
            id = id,
            stationId = sId,
            userId = doc.getString("userId") ?: "",
            userName = userName,
            userCountry = doc.getString("userCountry") ?: "Global",
            text = doc.getString("text") ?: "",
            status = doc.getString("status") ?: "queued",
            replyText = replyText,
            replyAudioBase64 = doc.getString("replyAudioBase64") ?: "",
            createdAt = createdAt,
            isQueuedOnAir = queuedMessageIds.contains(id),
            isPlayingOnAir = Playlist.current?.sourceId == id,
            isPlayedOnAir = playedMessageIds.contains(id),
            isSongRequest = doc.getBoolean("isSongRequest") ?: false,
            songFound = doc.getBoolean("songFound") ?: false,
            requestedSongTitle = doc.getString("requestedSongTitle") ?: "",
        )
    }

    /**
     * Converts the base64 audio reply to a local MP3 file and enqueues it to play right after the current song.
     */
    private fun tryEnqueueForEndOfSong(context: Context, msg: StationChatMessage) {
        runCatching {
            val audioBytes = Base64.decode(msg.replyAudioBase64, Base64.DEFAULT)
            if (audioBytes == null || audioBytes.isEmpty()) return

            val file = File(context.cacheDir, "rj_reply_${msg.id}.mp3")
            file.writeBytes(audioBytes)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val durationMs = runCatching {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                val d = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                retriever.release()
                d
            }.getOrDefault(0L)

            val countryTag = if (msg.userCountry.isNotBlank() && msg.userCountry != "Global") " (${msg.userCountry})" else ""
            val isRequest = msg.isSongRequest || detectSongRequest(msg.text)
            val matchedTrack = if (msg.songFound && msg.requestedSongTitle.isNotBlank()) {
                Playlist.findTrackByTitleOrQuery(msg.requestedSongTitle)
            } else if (isRequest) {
                Playlist.findTrackByTitleOrQuery(msg.text)
            } else null

            val title = if (matchedTrack != null) {
                "🎵 Song Request: ${msg.userName}$countryTag"
            } else {
                "💬 RJ Shoutout: ${msg.userName}$countryTag"
            }
            val artist = msg.replyText.ifBlank { "Listener On-Air Reply" }

            val item = Playlist.Item(
                uri = uri,
                title = title,
                artist = artist,
                durationMs = durationMs,
                sourceId = msg.id,
            )

            // 1. Enqueue shoutout right after the currently playing song in Playlist queue
            Playlist.enqueueAtEndOfCurrentSong(item)
            queuedMessageIds.add(msg.id)
            savePrefs(context)

            // 2. If the song is in our playlist: move it to play immediately after this on-air shoutout!
            if (matchedTrack != null) {
                Playlist.moveTrackToPlayNext(matchedTrack)
                Log.i(TAG, "Song request: Moved '${matchedTrack.title}' to play next for ${msg.userName}")
                _onAirNotificationFlow.tryEmit("🎵 Song Request: '${matchedTrack.title}' moved to Up Next for ${msg.userName}!")
            } else if (isRequest) {
                Log.i(TAG, "Song request: '${msg.requestedSongTitle.ifBlank { msg.text }}' not in playlist — RJ promised to add tomorrow")
                _onAirNotificationFlow.tryEmit("💬 Song requested by ${msg.userName} not in library — RJ promised to add tomorrow!")
            } else {
                Log.i(TAG, "Enqueued listener shoutout on-air at end of song: $title")
                _onAirNotificationFlow.tryEmit("💬 New chat from ${msg.userName}$countryTag queued for end of song!")
            }
        }.onFailure { e ->
            Log.w(TAG, "Failed to enqueue chat reply for ${msg.id}: ${e.message}")
        }
    }

    private fun detectSongRequest(text: String): Boolean {
        val lower = text.lowercase().trim()
        return lower.contains("play ") || lower.contains("song") || lower.contains("track") ||
            lower.contains("paattu") || lower.contains("request") || lower.contains("podunga") ||
            lower.contains("pannunga") || lower.contains("hear") || lower.contains("listen to")
    }

    /**
     * Helper to trigger Cloud Run if message is still pending in "queued" state.
     */
    private fun triggerBackgroundProcess(messageId: String, msg: StationChatMessage) {
        (coroutineScope ?: CoroutineScope(Dispatchers.IO)).launch(Dispatchers.IO) {
            runCatching {
                val cur = Playlist.current
                val songTitles = Playlist.musicTracks.map {
                    mapOf("title" to it.title, "artist" to it.artist)
                }
                AgentApiClient.post(
                    "process-message",
                    mapOf(
                        "messageId" to messageId,
                        "nowTrack" to (cur?.title ?: ""),
                        "nowArtist" to (cur?.artist ?: ""),
                        "voice" to RjConfig.voiceCode,
                        "speakingRate" to RjConfig.speakingRate,
                        "playlist" to songTitles,
                    )
                )
            }
        }
    }

    /**
     * Marks a shoutout as played on air so it won't be re-enqueued.
     */
    fun markAsPlayed(context: Context, docId: String) {
        queuedMessageIds.remove(docId)
        playedMessageIds.add(docId)
        savePrefs(context)
        _messagesFlow.value = _messagesFlow.value.map {
            if (it.id == docId) it.copy(isQueuedOnAir = false, isPlayedOnAir = true, isPlayingOnAir = false)
            else it
        }
    }

    /**
     * Marks a shoutout as played on air by matching the audio track title.
     */
    fun markAsPlayedByTitle(context: Context, title: String) {
        val msg = _messagesFlow.value.firstOrNull {
            title.contains(it.userName, ignoreCase = true) ||
                (it.replyText.isNotBlank() && title.contains(it.replyText.take(15), ignoreCase = true))
        }
        if (msg != null) {
            markAsPlayed(context, msg.id)
        }
    }

    /**
     * Broadcasts an AI shoutout immediately on-air with music pause, resuming music upon end.
     */
    fun broadcastNow(
        context: Context,
        msg: StationChatMessage,
        engine: BroadcastEngine,
        scope: CoroutineScope,
        onInterruptedPos: (Uri, Float, String) -> Unit,
    ) {
        if (msg.replyAudioBase64.isBlank()) return
        scope.launch(Dispatchers.IO) {
            runCatching {
                val audioBytes = Base64.decode(msg.replyAudioBase64, Base64.DEFAULT)
                val file = File(context.cacheDir, "rj_reply_${msg.id}.mp3")
                file.writeBytes(audioBytes)

                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val durationMs = runCatching {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(context, uri)
                    val d = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    retriever.release()
                    d
                }.getOrDefault(0L)

                val countryTag = if (msg.userCountry.isNotBlank() && msg.userCountry != "Global") " (${msg.userCountry})" else ""
                val isRequest = msg.isSongRequest || detectSongRequest(msg.text)
                val matched = if (isRequest) {
                    Playlist.findTrackByTitleOrQuery(msg.requestedSongTitle.ifBlank { msg.text })
                } else null

                val title = if (matched != null) {
                    "🎵 Song Request: ${msg.userName}$countryTag"
                } else {
                    "💬 RJ Shoutout: ${msg.userName}$countryTag"
                }
                val item = Playlist.Item(
                    uri = uri,
                    title = title,
                    artist = msg.replyText,
                    durationMs = durationMs,
                    sourceId = msg.id,
                )

                val cur = Playlist.current
                if (cur != null) {
                    val pos = engine.musicPosition.value
                    onInterruptedPos(cur.uri, pos, cur.title)
                }

                val targetIdx = (Playlist.currentIndex + 1).coerceAtLeast(0)
                Playlist.insert(targetIdx, item)
                if (matched != null) {
                    // Move the requested track right after the shoutout
                    val fromIdx = Playlist.items.indexOfFirst { it.uri == matched.uri }
                    if (fromIdx >= 0) {
                        Playlist.move(fromIdx, (targetIdx + 1).coerceIn(0, Playlist.items.size - 1))
                    }
                }
                Playlist.play(targetIdx, engine, context)

                queuedMessageIds.remove(msg.id)
                playedMessageIds.add(msg.id)
                savePrefs(context)

                val notificationMsg = if (matched != null) {
                    "🎵 Broadcasting song request shoutout for ${msg.userName}! '${matched.title}' plays next!"
                } else {
                    "🎙️ Broadcasting shoutout for ${msg.userName} now!"
                }
                _onAirNotificationFlow.tryEmit(notificationMsg)
            }
        }
    }
}
