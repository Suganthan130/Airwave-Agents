package lk.sugaapps.airwave.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class NewsItem(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = "World",
    val published: String = "",
    val order: Int = 0,
    val isNew: Boolean = false,
)

/**
 * News Manager for Airwave Agents.
 * - The feed is real Sri Lankan news scraped from Hiru News by the backend
 *   (`/news-feed`), so the AI Co-Host broadcasts genuine, current headlines.
 * - Powers the AI Radio Co-Host News Studio in AiStudioScreen and the live
 *   breaking-news popups during broadcast.
 */
object NewsManager {

    private const val TAG = "NewsManager"
    private const val PREFS_NAME = "airwave_news_prefs"
    private const val KEY_SEEN_IDS = "seen_news_ids"
    private const val KEY_BROADCAST_IDS = "broadcast_news_ids"
    private const val NEWS_FEED_LIMIT = 24

    private val _newsItems = MutableStateFlow<List<NewsItem>>(emptyList())
    val newsItems: StateFlow<List<NewsItem>> = _newsItems.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val seenIds = mutableSetOf<String>()
    private val broadcastIds = mutableSetOf<String>()
    private var isInitialized = false

    private fun loadPrefs(context: Context) {
        if (isInitialized) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        seenIds.addAll(prefs.getStringSet(KEY_SEEN_IDS, emptySet()) ?: emptySet())
        broadcastIds.addAll(prefs.getStringSet(KEY_BROADCAST_IDS, emptySet()) ?: emptySet())
        isInitialized = true
    }

    private fun persistPrefs(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putStringSet(KEY_SEEN_IDS, seenIds)
            .putStringSet(KEY_BROADCAST_IDS, broadcastIds)
            .apply()
    }

    fun markSeen(context: Context, id: String) {
        loadPrefs(context)
        seenIds.add(id)
        persistPrefs(context)
        _newsItems.value = _newsItems.value.map {
            if (it.id == id) it.copy(isNew = false) else it
        }
    }

    fun markBroadcast(context: Context, id: String) {
        loadPrefs(context)
        seenIds.add(id)
        broadcastIds.add(id)
        persistPrefs(context)
        _newsItems.value = _newsItems.value.map {
            if (it.id == id) it.copy(isNew = false) else it
        }
    }

    /**
     * Retrieves the next unbroadcast news story for the live broadcast popup alert.
     * When all stories have been presented, the cycle wraps around so continuous popups can occur.
     */
    fun getNextUnbroadcastNews(context: Context): NewsItem? {
        loadPrefs(context)
        val currentList = _newsItems.value
        if (currentList.isEmpty()) return null

        val unbroadcast = currentList.filter { !broadcastIds.contains(it.id) }
        return if (unbroadcast.isNotEmpty()) {
            unbroadcast.first()
        } else {
            // All items were presented; clear broadcast memory and loop back to the first
            broadcastIds.clear()
            persistPrefs(context)
            currentList.firstOrNull()
        }
    }

    /**
     * Starts the news feed by fetching the latest Hiru stories. Shows the loading
     * state until the first fetch lands so the News Studio shows a spinner instead
     * of a brief "no news" flash on open.
     */
    fun start(context: Context, scope: CoroutineScope) {
        loadPrefs(context)
        if (_newsItems.value.isEmpty()) _isRefreshing.value = true
        scope.launch {
            try {
                fetchHiruNews(context)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /**
     * Explicit one-shot refresh of the Hiru news feed.
     */
    suspend fun refresh(context: Context) {
        _isRefreshing.value = true
        try {
            fetchHiruNews(context)
        } finally {
            _isRefreshing.value = false
        }
    }

    /**
     * Fetches the latest real Hiru News stories via the backend.
     * Failures are logged and leave the existing feed untouched.
     */
    private suspend fun fetchHiruNews(context: Context) {
        loadPrefs(context)
        withContext(Dispatchers.IO) {
            try {
                val resp = AgentApiClient.post("news-feed", mapOf("limit" to NEWS_FEED_LIMIT))
                val arr = resp.optJSONArray("items") ?: return@withContext
                val parsed = mutableListOf<NewsItem>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id").trim()
                    val title = o.optString("title").trim()
                    if (id.isBlank() || title.isBlank()) continue
                    parsed.add(
                        NewsItem(
                            id = id,
                            title = title,
                            description = o.optString("description").trim(),
                            category = o.optString("category").ifBlank { "World" },
                            published = o.optString("published").trim(),
                            order = o.optInt("order", i + 1),
                            isNew = !seenIds.contains(id),
                        )
                    )
                }
                if (parsed.isNotEmpty()) {
                    _newsItems.value = parsed
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch Hiru news feed: ${e.message}")
            }
        }
    }
}
