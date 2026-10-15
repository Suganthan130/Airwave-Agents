package lk.sugaapps.airwave.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Base64
import androidx.core.content.FileProvider
import java.io.File
import lk.sugaapps.airwave.engine.Playlist

/**
 * AI RJ: asks the `rjLine` Cloud Function for a Gemini-written line spoken by
 * Cloud TTS (MP3), and turns it into a playlist item. Public — no keys on device.
 */
object RjStudio {

    suspend fun create(
        context: Context,
        type: String,
        nowTrack: String = "",
        nowArtist: String = "",
        nextTrack: String = "",
        nextArtist: String = "",
        timeLabel: String = "",
    ): Playlist.Item {
        val payload = mapOf(
            "type" to type,
            "nowTrack" to nowTrack,
            "nowArtist" to nowArtist,
            "nextTrack" to nextTrack,
            "nextArtist" to nextArtist,
            "timeLabel" to timeLabel,
            "voice" to RjConfig.voiceCode,
            "speakingRate" to RjConfig.speakingRate,
        )

        val data = AgentApiClient.post("rj-line", payload)
        val audioBase64 = data.optString("audioBase64").takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("No audio returned from Cloud Run.")
        val text = data.optString("text").trim()

        val bytes = Base64.decode(audioBase64, Base64.DEFAULT)
        val file = File(context.cacheDir, "rj_${type}_${System.currentTimeMillis()}.mp3")
        file.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

        val durationMs = runCatching {
            val r = MediaMetadataRetriever()
            r.setDataSource(context, uri)
            val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            r.release()
            d
        }.getOrDefault(0L)

        val subtitle = text.ifBlank { "Airwave Agents" }
        return Playlist.Item(uri, "RJ · ${label(type)}", subtitle, durationMs)
    }

    private fun label(type: String) = when (type) {
        "intro" -> "Song intro"
        "joke" -> "Joke"
        "station_id" -> "Station ID"
        "greeting" -> "Greeting"
        else -> "On air"
    }
}
