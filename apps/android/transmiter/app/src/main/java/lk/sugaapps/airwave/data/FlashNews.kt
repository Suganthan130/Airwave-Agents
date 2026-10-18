package lk.sugaapps.airwave.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Base64
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import lk.sugaapps.airwave.engine.Playlist

/**
 * AI Flash News: transforms raw breaking news stories into professional
 * radio news bulletins voiced by the AI news anchor (Gemini 2.5 Flash + Cloud TTS),
 * saves as an MP3, and returns a playlist item for live broadcast.
 * Public — no API keys on device.
 */
object FlashNews {

    const val DEFAULT_TEST_HEADLINE =
        "Committee to submit report on prison regulations to President today"

    const val DEFAULT_TEST_CONTENT =
        "The committee appointed to update the rules, regulations and standing orders formulated under the Prisons Ordinance is scheduled to hand over its final report to President Anura Kumara Dissanayake today (14).\n\n" +
        "The five-member committee held its first meeting recently.\n\n" +
        "Its members include Piyumanthi Peiris, Additional Secretary to the Ministry of Justice and National Integration; Wasantha Ariyarathna, Additional Secretary to the Ministry of Public Administration, Provincial Councils and Local Government; R.M.G. Senaratne, Commissioner General of Buddhist Affairs; and A.C. Gajanayake, a Commissioner of the Department of Prisons.\n\n" +
        "The committee was appointed following a meeting between President Anura Kumara Dissanayake and the Chief Prelets representing the tri-sects on the 8th of this month.\n\n" +
        "The meeting took place amid protests over the removal of the robe of the imprisoned Venerable Galagoda Aththe Gnanasara Thera and his being dressed in civilian clothing."

    suspend fun create(
        context: Context,
        headline: String = DEFAULT_TEST_HEADLINE,
        content: String = DEFAULT_TEST_CONTENT,
    ): Playlist.Item = withContext(NonCancellable + Dispatchers.IO) {
        val cleanHeadline = headline.trim().ifBlank { DEFAULT_TEST_HEADLINE }
        val cleanContent = content.trim().ifBlank { DEFAULT_TEST_CONTENT }
        val voice = RjConfig.voiceCode

        val payload = mapOf(
            "type" to "flash_news",
            "headline" to cleanHeadline,
            "content" to cleanContent,
            "voice" to voice,
            "speakingRate" to RjConfig.speakingRate,
        )

        val data = runCatching {
            AgentApiClient.post("flash-news", payload)
        }.getOrElse { error ->
            val fallbackText = "This is an Airwave Flash News bulletin. $cleanHeadline. Stay tuned to Airwave Agents for developing details. Now, heading smoothly back to the music on Airwave Agents."
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
            ?: throw IllegalStateException("No audio data returned by Flash News service.")
        val spokenText = data.optString("text").trim()

        val bytes = Base64.decode(audioBase64, Base64.DEFAULT)
        val file = File(context.cacheDir, "rj_news_${System.currentTimeMillis()}.mp3")
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

        val displayHeadline = if (cleanHeadline.length > 45) {
            cleanHeadline.take(42) + "…"
        } else {
            cleanHeadline
        }

        val subtitle = spokenText.ifBlank { cleanHeadline }
        Playlist.Item(
            uri = uri,
            title = "Flash News · $displayHeadline",
            artist = subtitle,
            durationMs = durationMs,
        )
    }
}
