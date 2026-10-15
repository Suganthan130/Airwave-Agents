package lk.sugaapps.airwave.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

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
                "name" to name,
                "email" to email,
                "updatedAt" to System.currentTimeMillis(),
            ),
            SetOptions.merge(),
        ).await()
    }

    suspend fun loadProfile(uid: String): Map<String, Any>? =
        user(uid).get().await().data

    suspend fun saveSettings(uid: String, settings: Map<String, Any>) {
        val icecastMap = mutableMapOf<String, Any>()
        settings["host"]?.let { icecastMap["host"] = it }
        settings["port"]?.let { icecastMap["port"] = it }
        settings["mount"]?.let { icecastMap["mount"] = it }
        settings["username"]?.let { icecastMap["username"] = it }
        settings["password"]?.let { icecastMap["password"] = it }
        settings["serverType"]?.let {
            icecastMap["serverType"] = if (it == 1 || it == "Shoutcast") "Shoutcast" else "Icecast 2"
        }
        settings["bitrate"]?.let { icecastMap["bitrate"] = it }
        settings["sampleRate"]?.let { icecastMap["sampleRate"] = it }
        settings["channels"]?.let { icecastMap["channels"] = it }
        settings["autoReconnect"]?.let { icecastMap["autoReconnect"] = it }
        settings["publicListeningUrl"]?.let { icecastMap["publicListeningUrl"] = it }
        icecastMap["updatedAt"] = System.currentTimeMillis()

        val updateData = mutableMapOf<String, Any>(
            "settings" to settings,
            "icecast" to icecastMap,
            "updatedAt" to System.currentTimeMillis(),
        )
        settings["host"]?.let { updateData["host"] = it }
        settings["port"]?.let { updateData["port"] = it }
        settings["mount"]?.let { updateData["mount"] = it }
        settings["username"]?.let { updateData["username"] = it }
        settings["publicListeningUrl"]?.let { updateData["publicListeningUrl"] = it }
        settings["stationTitle"]?.let { updateData["stationTitle"] = it }

        user(uid).set(updateData, SetOptions.merge()).await()
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun loadSettings(uid: String): Map<String, Any>? {
        val snapshot = user(uid).get().await()
        val settings = snapshot.get("settings") as? Map<String, Any>
        val icecast = snapshot.get("icecast") as? Map<String, Any>
        if (settings == null && icecast == null) return null
        val merged = mutableMapOf<String, Any>()
        if (settings != null) merged.putAll(settings)
        if (icecast != null) {
            icecast.forEach { (k, v) ->
                if (!merged.containsKey(k) || merged[k] == null || (merged[k] as? String).isNullOrBlank()) {
                    merged[k] = v
                }
            }
        }
        return merged
    }
}
