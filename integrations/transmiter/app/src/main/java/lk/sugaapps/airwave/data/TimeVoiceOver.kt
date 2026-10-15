package lk.sugaapps.airwave.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Base64
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import lk.sugaapps.airwave.engine.Playlist

/**
 * Generates a real AI Radio Jockey (RJ) spoken "current time" clip via the
 * Cloud Function (Gemini host line + Cloud TTS MP3), saves it locally, and
 * returns a playlist item. Competition/Google code — public. No keys on the client.
 */
object TimeVoiceOver {

    suspend fun create(
        context: Context,
        nowTrack: String = "",
        nowArtist: String = "",
        nextTrack: String = "",
        nextArtist: String = "",
        targetTimeMs: Long = Playlist.calculateProjectedTimeCheckMs(),
    ): Playlist.Item = withContext(NonCancellable + Dispatchers.IO) {
        val timeLabel = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(targetTimeMs))
        val voice = RjConfig.voiceCode
        val balanceSec = Playlist.getSongBalanceTimeMs() / 1000L
        val relayDelaySec = 20L

        val payload = mapOf(
            "type" to "time",
            "nowTrack" to nowTrack,
            "nowArtist" to nowArtist,
            "nextTrack" to nextTrack,
            "nextArtist" to nextArtist,
            "timeLabel" to timeLabel,
            "songBalanceSec" to balanceSec,
            "relayDelaySec" to relayDelaySec,
            "voice" to voice,
            "speakingRate" to RjConfig.speakingRate,
        )

        val data = runCatching {
            AgentApiClient.post("rj-line", payload)
        }.getOrElse { error ->
            val fallbackText = "You are listening to Airwave Agents. The time is now $timeLabel."
            AgentApiClient.post(
                "synthesize",
                mapOf(
                    "text" to fallbackText,
                    "voice" to voice,
                    "speakingRate" to RjConfig.speakingRate,
                )
            )
        }

        val audioBase64 = data.optString("audioBase64").takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("No audio data returned by voice service.")
        val spokenText = data.optString("text").trim()

        val bytes = Base64.decode(audioBase64, Base64.DEFAULT)
        val file = File(context.cacheDir, "rj_time_${System.currentTimeMillis()}.mp3")
        file.writeBytes(bytes)

        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )

        val durationMs = runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val d = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            retriever.release()
            d
        }.getOrDefault(0L)

        val subtitle = spokenText.ifBlank { "Airwave Agents · $timeLabel" }
        Playlist.Item(uri, "RJ Time Check · $timeLabel", subtitle, durationMs)
    }
}
