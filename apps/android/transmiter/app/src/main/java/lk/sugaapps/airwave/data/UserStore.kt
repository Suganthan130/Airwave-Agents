package lk.sugaapps.airwave.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source

/**
 * Cloud Firestore persistence (competition/Google code — public).
 * Stores each user's profile and stream settings under users/{uid}.
 */
object UserStore {

    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    private fun user(uid: String) = db.collection("users").document(uid)

    suspend fun saveProfile(uid: String, name: String, email: String) {
        user(uid).set(
            mapOf(
                "displayName" to name,
                "email" to email,
                "updatedAt" to System.currentTimeMillis(),
            ),
            SetOptions.merge(),
        ).await()
    }

    suspend fun loadProfile(uid: String): Map<String, Any>? =
        runCatching { user(uid).get(Source.SERVER).await().data }
            .getOrElse { runCatching { user(uid).get().await().data }.getOrNull() }

    suspend fun saveSettings(uid: String, settings: Map<String, Any>) {
        val updateData = mutableMapOf<String, Any>()
        settings.forEach { (k, v) ->
            when (k) {
                "publicListeningUrl" -> updateData["streamUrl"] = v
                "settings", "icecast", "rjVoice", "rjSpeed", "speed" -> { /* skip redundant duplicates */ }
                else -> updateData[k] = v
            }
        }

        // Canonical voice & speakingRate resolution
        if (!updateData.containsKey("voice")) {
            (settings["rjVoice"] as? Number)?.let {
                updateData["voice"] = when (it.toInt()) {
                    RjConfig.VOICE_STUDIO -> "studio"
                    RjConfig.VOICE_TAMIL -> "tamil"
                    else -> "chirp"
                }
            }
        }
        if (!updateData.containsKey("speakingRate")) {
            (settings["rjSpeed"] as? Number)?.let {
                updateData["speakingRate"] = when (it.toInt()) {
                    RjConfig.SPEED_RELAXED -> 0.90f
                    RjConfig.SPEED_FAST -> 1.12f
                    else -> 1.00f
                }
            }
        }

        updateData["updatedAt"] = System.currentTimeMillis()
        user(uid).set(updateData, SetOptions.merge()).await()
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun loadSettings(uid: String): Map<String, Any>? {
        val snapshot = runCatching { user(uid).get(Source.SERVER).await() }
            .getOrElse { user(uid).get().await() }
        val data = snapshot.data ?: return null
        val merged = mutableMapOf<String, Any>()

        // Fallback for older legacy documents with nested maps
        (data["icecast"] as? Map<String, Any>)?.let { merged.putAll(it) }
        (data["settings"] as? Map<String, Any>)?.let { merged.putAll(it) }

        // Root canonical fields take priority
        data.forEach { (k, v) ->
            if (k != "settings" && k != "icecast") {
                merged[k] = v
            }
        }

        // Normalize streamUrl <-> publicListeningUrl for callers
        val stream = (merged["streamUrl"] as? String) ?: (merged["publicListeningUrl"] as? String)
        if (!stream.isNullOrBlank()) {
            merged["streamUrl"] = stream
            merged["publicListeningUrl"] = stream
        }

        // Normalize displayName <-> name
        val name = (merged["displayName"] as? String) ?: (merged["name"] as? String)
        if (!name.isNullOrBlank()) {
            merged["displayName"] = name
            merged["name"] = name
        }

        return merged
    }
}
