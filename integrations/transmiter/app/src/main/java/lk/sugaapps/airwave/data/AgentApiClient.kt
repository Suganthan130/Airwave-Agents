package lk.sugaapps.airwave.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Native HTTP client for Airwave Agents API on Google Cloud Run.
 * Replaces legacy Firebase Cloud Functions with direct Cloud Run REST endpoints.
 */
object AgentApiClient {

    const val BASE_URL = "https://agent-api-191983702527.us-central1.run.app/api/v1"

    suspend fun post(endpoint: String, payload: Map<String, Any?>): JSONObject = withContext(Dispatchers.IO) {
        val cleanEndpoint = endpoint.trimStart('/')
        val url = URL("$BASE_URL/$cleanEndpoint")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            if (user != null) {
                val token = runCatching { user.getIdToken(false).await().token }.getOrNull()
                if (!token.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer $token")
                }
            }
            doOutput = true
            connectTimeout = 15000
            readTimeout = 45000
        }

        val jsonInput = JSONObject(payload).toString()
        OutputStreamWriter(conn.outputStream, "UTF-8").use { os ->
            os.write(jsonInput)
            os.flush()
        }

        val responseCode = conn.responseCode
        val inputStream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
        val responseText = BufferedReader(InputStreamReader(inputStream, "UTF-8")).use { it.readText() }

        if (responseCode !in 200..299) {
            val errDetail = runCatching { JSONObject(responseText).optString("detail") }.getOrNull()
            throw IllegalStateException(errDetail ?: "Cloud Run call to $endpoint failed with HTTP $responseCode")
        }

        JSONObject(responseText)
    }
}
