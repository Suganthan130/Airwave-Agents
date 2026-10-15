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
                val currentPlaying = Playlist.current?.title.orEmpty()
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
                    val createdAt = runCatching { doc.getTimestamp("createdAt")?.toDate()?.time }.getOrNull()
                        ?: runCatching { doc.getLong("createdAt") }.getOrNull()
                        ?: runCatching { doc.getDouble("createdAt")?.toLong() }.getOrNull()
                        ?: runCatching { doc.getString("createdAt")?.toLongOrNull() }.getOrNull()
                        ?: System.currentTimeMillis()

                    val isOldMessage = isFirst || createdAt < (sessionStartTime - 15_000L)
                    val isQueued = queuedMessageIds.contains(id)
                    val isPlayed = playedMessageIds.contains(id) || (isOldMessage && !isQueued)
                    val isPlaying = currentPlaying.contains(userName) || (replyText.isNotBlank() && currentPlaying.contains(replyText.take(20)))

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
                    )
                    list.add(msg)

                    if (isOldMessage) {
                        // Old messages from past sessions must NEVER be auto-enqueued!
                        if (!playedMessageIds.contains(id)) {
                            playedMessageIds.add(id)
                        }
                    } else {
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
            val title = "💬 RJ Shoutout: ${msg.userName}$countryTag"
            val artist = msg.replyText.ifBlank { "Listener On-Air Reply" }

            val item = Playlist.Item(
                uri = uri,
                title = title,
                artist = artist,
                durationMs = durationMs,
            )

            // Enqueue right after the currently playing song in Playlist queue
            Playlist.enqueueAtEndOfCurrentSong(item)
            queuedMessageIds.add(msg.id)
            savePrefs(context)

            Log.i(TAG, "Enqueued listener shoutout on-air at end of song: $title")
            _onAirNotificationFlow.tryEmit("💬 New chat from ${msg.userName}$countryTag queued for end of song!")
        }.onFailure { e ->
            Log.w(TAG, "Failed to enqueue chat reply for ${msg.id}: ${e.message}")
        }
    }

    /**
     * Helper to trigger Cloud Run if message is still pending in "queued" state.
     */
    private fun triggerBackgroundProcess(messageId: String, msg: StationChatMessage) {
        (coroutineScope ?: CoroutineScope(Dispatchers.IO)).launch(Dispatchers.IO) {
            runCatching {
                val cur = Playlist.current
                AgentApiClient.post(
                    "process-message",
                    mapOf(
                        "messageId" to messageId,
                        "nowTrack" to (cur?.title ?: ""),
                        "nowArtist" to (cur?.artist ?: ""),
                        "voice" to RjConfig.voiceCode,
                        "speakingRate" to RjConfig.speakingRate,
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
                val item = Playlist.Item(
                    uri = uri,
                    title = "💬 RJ Shoutout: ${msg.userName}$countryTag",
                    artist = msg.replyText,
                    durationMs = durationMs,
                )

                val cur = Playlist.current
                if (cur != null) {
                    val pos = engine.musicPosition.value
                    onInterruptedPos(cur.uri, pos, cur.title)
                }

                val targetIdx = (Playlist.currentIndex + 1).coerceAtLeast(0)
                Playlist.insert(targetIdx, item)
                Playlist.play(targetIdx, engine, context)

                queuedMessageIds.remove(msg.id)
                playedMessageIds.add(msg.id)
                savePrefs(context)

                _onAirNotificationFlow.tryEmit("🎙️ Broadcasting shoutout for ${msg.userName} now!")
            }
        }
    }
}
