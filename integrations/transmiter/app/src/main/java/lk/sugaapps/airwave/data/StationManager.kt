package lk.sugaapps.airwave.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

/**
 * Manages broadcaster stations in Cloud Firestore.
 * Each broadcaster has strictly one public listener page (keyed by user UID).
 * Persists unique ID, host, port, serverType, mount, and public listening stream URL.
 */
object StationManager {

    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    const val PUBLIC_BASE_URL = "https://airwave-agents.web.app"

    fun stationUrl(uid: String): String = "$PUBLIC_BASE_URL/?station=$uid"

    /**
     * Checks if a URL is a valid public listener stream URL.
     * Icecast source mounts (e.g. /source, -broadcast.) are strictly rejected.
     */
    fun isValidPublicStreamUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase().trim()
        if (lower.contains("/source") || lower.contains("-broadcast.") || lower.contains("broadcastmyself")) {
            return false
        }
        return lower.startsWith("http://") || lower.startsWith("https://")
    }

    suspend fun getStation(uid: String): Map<String, Any>? =
        runCatching { db.collection("stations").document(uid).get().await().data }.getOrNull()

    /**
     * Registers or refreshes the broadcaster's public listener page in Firestore.
     * Enforces userType = "broadcaster" and stores uniqueId, host, port, serverType, and publicListeningUrl.
     */
    suspend fun registerOrUpdateStation(
        uid: String,
        broadcasterName: String,
        broadcasterEmail: String,
        host: String = "",
        port: String = "80",
        serverType: String = "Icecast 2",
        mount: String = "/live",
        publicListeningUrl: String = "",
        stationTitle: String? = null,
        isLive: Boolean = false,
        nowTrack: String = "",
        nowArtist: String = "",
        rjVoice: Int = RjConfig.voiceIndex,
        rjSpeed: Int = RjConfig.speedIndex,
        aiPersona: String = "",
    ) {
        val title = stationTitle?.takeIf { it.isNotBlank() } ?: "$broadcasterName's Live Station"
        val publicPage = stationUrl(uid)
        val validExplicit = publicListeningUrl.takeIf { isValidPublicStreamUrl(it) }
        val validConfig = lk.sugaapps.airwave.engine.StreamConfig.publicListeningUrl.takeIf { isValidPublicStreamUrl(it) }
        val stream = validExplicit ?: validConfig ?: ""

        val voiceCode = when (rjVoice) {
            RjConfig.VOICE_STUDIO -> "studio"
            RjConfig.VOICE_TAMIL -> "tamil"
            else -> "chirp"
        }
        val speakingRate = when (rjSpeed) {
            RjConfig.SPEED_RELAXED -> 0.90f
            RjConfig.SPEED_FAST -> 1.12f
            else -> 1.00f
        }

        val data = mutableMapOf<String, Any>(
            "uniqueId" to uid,
            "stationId" to uid,
            "broadcasterId" to uid,
            "broadcasterName" to broadcasterName,
            "broadcasterEmail" to broadcasterEmail,
            "host" to host,
            "port" to port,
            "serverType" to serverType,
            "mount" to mount,
            "publicPageUrl" to publicPage,
            "stationTitle" to title,
            "userType" to "broadcaster",
            "isLive" to isLive,
            "nowPlayingTrack" to nowTrack,
            "nowPlayingArtist" to nowArtist,
            "rjVoice" to rjVoice,
            "voice" to voiceCode,
            "rjSpeed" to rjSpeed,
            "speakingRate" to speakingRate,
            "speed" to RjConfig.speedDisplayName,
            "updatedAt" to System.currentTimeMillis(),
        )
        if (aiPersona.isNotBlank()) {
            data["aiPersona"] = aiPersona
        }
        if (stream.isNotBlank()) {
            data["publicListeningUrl"] = stream
            data["streamUrl"] = stream
        }

        val icecastStationSummary = mutableMapOf<String, Any>(
            "host" to host,
            "port" to port,
            "serverType" to serverType,
            "mount" to mount,
            "streamUrl" to stream,
            "publicListeningUrl" to stream,
            "updatedAt" to System.currentTimeMillis(),
        )
        data["icecast"] = icecastStationSummary

        // Save to stations/{uid} — strictly one station per broadcaster user
        db.collection("stations").document(uid).set(data, SetOptions.merge()).await()

        // Also update users/{uid} to mark userType = "broadcaster"
        val userUpdate = mutableMapOf<String, Any>(
            "userType" to "broadcaster",
            "stationId" to uid,
            "stationTitle" to title,
            "host" to host,
            "port" to port,
            "mount" to mount,
            "serverType" to serverType,
            "rjVoice" to rjVoice,
            "voice" to voiceCode,
            "rjSpeed" to rjSpeed,
            "speakingRate" to speakingRate,
            "updatedAt" to System.currentTimeMillis(),
        )
        if (stream.isNotBlank()) {
            userUpdate["publicListeningUrl"] = stream
        }
        db.collection("users").document(uid).set(userUpdate, SetOptions.merge()).await()
    }

    /**
     * Updates AI Voice personality and speech speed for the station in Firestore in real-time.
     */
    suspend fun updateAiVoiceSettings(
        uid: String,
        rjVoice: Int,
        rjSpeed: Int,
        aiPersona: String = "",
    ) {
        if (uid.isBlank()) return
        val voiceCode = when (rjVoice) {
            RjConfig.VOICE_STUDIO -> "studio"
            RjConfig.VOICE_TAMIL -> "tamil"
            else -> "chirp"
        }
        val speakingRate = when (rjSpeed) {
            RjConfig.SPEED_RELAXED -> 0.90f
            RjConfig.SPEED_FAST -> 1.12f
            else -> 1.00f
        }
        val data = mutableMapOf<String, Any>(
            "rjVoice" to rjVoice,
            "voice" to voiceCode,
            "rjSpeed" to rjSpeed,
            "speakingRate" to speakingRate,
            "speed" to RjConfig.speedDisplayName,
            "updatedAt" to System.currentTimeMillis(),
        )
        if (aiPersona.isNotBlank()) {
            data["aiPersona"] = aiPersona
        }
        db.collection("stations").document(uid).set(data, SetOptions.merge()).await()
        db.collection("users").document(uid).set(mapOf("settings" to data), SetOptions.merge()).await()
    }

    /**
     * Saves the public listening stream URL explicitly for this broadcaster's station.
     */
    suspend fun savePublicListeningUrl(uid: String, publicListeningUrl: String) {
        if (!isValidPublicStreamUrl(publicListeningUrl)) return
        val data = mapOf(
            "publicListeningUrl" to publicListeningUrl.trim(),
            "streamUrl" to publicListeningUrl.trim(),
            "updatedAt" to System.currentTimeMillis(),
        )
        db.collection("stations").document(uid).set(data, SetOptions.merge()).await()
        db.collection("users").document(uid).set(data, SetOptions.merge()).await()
    }

    /**
     * Updates live broadcast state (isLive, now playing song).
     */
    suspend fun updateLiveBroadcastState(
        uid: String,
        isLive: Boolean,
        nowTrack: String = "",
        nowArtist: String = "",
        streamUrl: String? = null,
    ) {
        val data = mutableMapOf<String, Any>(
            "isLive" to isLive,
            "nowPlayingTrack" to nowTrack,
            "nowPlayingArtist" to nowArtist,
            "updatedAt" to System.currentTimeMillis(),
        )
        if (!streamUrl.isNullOrBlank() && isValidPublicStreamUrl(streamUrl)) {
            data["publicListeningUrl"] = streamUrl.trim()
            data["streamUrl"] = streamUrl.trim()
        }
        db.collection("stations").document(uid).set(data, SetOptions.merge()).await()
    }

    /**
     * Synchronizes the station's active playlist tracks to Firestore so the web app and
     * Gemini AI Co-Host backend can fulfill listener song requests in real-time.
     */
    suspend fun syncPlaylist(uid: String, tracks: List<Map<String, String>>) {
        if (uid.isBlank()) return
        runCatching {
            db.collection("stations").document(uid).set(
                mapOf(
                    "playlist" to tracks,
                    "updatedAt" to System.currentTimeMillis(),
                ),
                SetOptions.merge()
            ).await()
        }
    }
}

