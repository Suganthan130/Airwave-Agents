package lk.sugaapps.airwave.data

import android.content.Context
import android.os.Build
import android.text.Html
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

data class HiruNewsItem(
    val id: String,
    val title: String,
    val url: String,
    val description: String = "",
    val published: String = "",
    val isNew: Boolean = false,
)

/**
 * Native Android client for Hiru News Flash Ticker (derived from news/hiru_flash.py).
 * Runs directly on the device with no cloud API or proxy needed.
 */
object HiruNewsManager {

    private const val HIRU_URL = "https://www.hirunews.lk/en/"
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) Airwave-Flash-Watcher"
    private const val PREFS_NAME = "hiru_news_prefs"
    private const val KEY_SEEN_IDS = "seen_news_ids"

    private val _newsItems = MutableStateFlow<List<HiruNewsItem>>(emptyList())
    val newsItems: StateFlow<List<HiruNewsItem>> = _newsItems.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _newAlertFlow = MutableSharedFlow<HiruNewsItem>(extraBufferCapacity = 5)
    val newAlertFlow: SharedFlow<HiruNewsItem> = _newAlertFlow.asSharedFlow()

    private var watcherJob: Job? = null
    private val seenIds = mutableSetOf<String>()
    private var isInitialized = false

    private fun loadSeenIds(context: Context) {
        if (isInitialized) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getStringSet(KEY_SEEN_IDS, emptySet()) ?: emptySet()
        seenIds.addAll(saved)
        isInitialized = true
    }

    private fun persistSeenIds(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_SEEN_IDS, seenIds).apply()
    }

    fun markSeen(context: Context, id: String) {
        seenIds.add(id)
        persistSeenIds(context)
        _newsItems.value = _newsItems.value.map {
            if (it.id == id) it.copy(isNew = false) else it
        }
    }

    /**
     * Start periodic watcher coroutine. Checks every [intervalSeconds] seconds.
     */
    fun startWatcher(context: Context, scope: CoroutineScope, intervalSeconds: Long = 60L) {
        watcherJob?.cancel()
        watcherJob = scope.launch(Dispatchers.IO) {
            loadSeenIds(context)
            // Initial fetch
            refreshInternal(context, notifyNew = false)
            while (isActive) {
                delay(intervalSeconds * 1000L)
                refreshInternal(context, notifyNew = true)
            }
        }
    }

    fun stopWatcher() {
        watcherJob?.cancel()
        watcherJob = null
    }

    suspend fun refresh(context: Context, notifyNew: Boolean = false) {
        withContext(Dispatchers.IO) {
            refreshInternal(context, notifyNew)
        }
    }

    private suspend fun refreshInternal(context: Context, notifyNew: Boolean) {
        _isRefreshing.value = true
        loadSeenIds(context)
        try {
            val rawItems = fetchFlashTicker()
            if (rawItems.isEmpty()) {
                _isRefreshing.value = false
                return
            }

            val newlyDetected = mutableListOf<HiruNewsItem>()
            val processed = rawItems.mapIndexed { index, item ->
                val isUnseen = !seenIds.contains(item.id)
                // Fetch first 3 articles' details for instant reading
                val detailed = if (index < 3 || isUnseen) {
                    fetchArticleDetails(item)
                } else {
                    item
                }

                if (isUnseen) {
                    newlyDetected.add(detailed)
                }
                detailed.copy(isNew = isUnseen)
            }

            _newsItems.value = processed

            // If new items arrived and this isn't the cold initial baseline:
            if (notifyNew && newlyDetected.isNotEmpty()) {
                for (newItem in newlyDetected) {
                    seenIds.add(newItem.id)
                    _newAlertFlow.emit(newItem)
                }
                persistSeenIds(context)
            } else if (!notifyNew && seenIds.isEmpty()) {
                // Baseline initial seen IDs so first run doesn't blast 10 alerts
                seenIds.addAll(rawItems.map { it.id })
                persistSeenIds(context)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            _isRefreshing.value = false
        }
    }

    /**
     * Fetch HTML from Hiru News ticker and parse the headline pairs.
     */
    private fun fetchFlashTicker(): List<HiruNewsItem> {
        val html = fetchUrl(HIRU_URL) ?: return emptyList()

        val tickerPattern = Pattern.compile("news-marquee-ticker(.*?)</div>\\s*</div>\\s*</div>", Pattern.DOTALL)
        val tickerMatcher = tickerPattern.matcher(html)
        val block = if (tickerMatcher.find()) tickerMatcher.group(1).orEmpty() else ""

        val itemPattern = Pattern.compile("<span class=\"news-heading\">\\s*<a href=\"([^\"]+)\"[^>]*>(.*?)</a>", Pattern.DOTALL)
        val itemMatcher = itemPattern.matcher(block)

        val idPattern = Pattern.compile("/(\\d+)/")
        val items = mutableListOf<HiruNewsItem>()

        while (itemMatcher.find()) {
            val url = itemMatcher.group(1).orEmpty().trim()
            val rawTitle = itemMatcher.group(2).orEmpty()
            val cleanTitle = cleanHtml(rawTitle)

            val idMatcher = idPattern.matcher(url)
            val id = if (idMatcher.find()) idMatcher.group(1).orEmpty() else url

            if (cleanTitle.isNotBlank()) {
                items.add(
                    HiruNewsItem(
                        id = id,
                        title = cleanTitle,
                        url = url,
                    )
                )
            }
        }

        // Sort by ID descending (highest numeric ID first)
        items.sortByDescending { it.id.toLongOrNull() ?: 0L }
        return items
    }

    /**
     * Fetch the article page to retrieve datePublished and first body paragraph.
     */
    fun fetchArticleDetails(item: HiruNewsItem): HiruNewsItem {
        val html = fetchUrl(item.url) ?: return item

        var published = ""
        val pubPattern = Pattern.compile("\"datePublished\":\\s*\"([^\"]+)\"")
        val pubMatcher = pubPattern.matcher(html)
        if (pubMatcher.find()) {
            published = pubMatcher.group(1).orEmpty().trim()
        }

        var description = ""
        val bodyPattern = Pattern.compile("\"articleBody\":\\s*\"?(.*?)\"\\s*[},]", Pattern.DOTALL)
        val bodyMatcher = bodyPattern.matcher(html)
        if (bodyMatcher.find()) {
            val body = bodyMatcher.group(1).orEmpty()
            val firstParagraph = body.split(Regex("(?:<br\\s*/?>\\s*)+")).firstOrNull().orEmpty()
            description = cleanHtml(firstParagraph).trim().removePrefix("\"").removeSuffix("\"")
        }

        if (description.isBlank()) {
            val ogPattern = Pattern.compile("<meta[^>]+property=\"og:description\"[^>]*content=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE)
            val ogMatcher = ogPattern.matcher(html)
            if (ogMatcher.find()) {
                description = cleanHtml(ogMatcher.group(1).orEmpty())
            }
        }

        return item.copy(
            description = if (description.isNotBlank()) description else item.title,
            published = published,
        )
    }

    private fun cleanHtml(htmlStr: String): String {
        val stripped = htmlStr.replace(Regex("<[^>]+>"), "")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(stripped, Html.FROM_HTML_MODE_LEGACY).toString().trim()
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(stripped).toString().trim()
        }
    }

    private fun fetchUrl(urlStr: String): String? {
        return try {
            val url = URL(urlStr)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12000
                readTimeout = 15000
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                instanceFollowRedirects = true
            }
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            null
        }
    }
}
