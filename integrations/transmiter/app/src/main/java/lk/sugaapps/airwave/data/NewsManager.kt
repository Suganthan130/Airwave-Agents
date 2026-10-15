package lk.sugaapps.airwave.data

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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
 * Cloud Firestore powered News Manager for Airwave Agents.
 * - Synchronizes 10+ curated real-time breaking news items from Firestore `news` collection.
 * - Powers the AI Radio Co-Host News Studio in AiStudioScreen.
 * - Cycles through stories every 1 minute during live broadcast to pop the breaking news dialog.
 */
object NewsManager {

    private const val TAG = "NewsManager"
    private const val COLLECTION_NAME = "news"
    private const val PREFS_NAME = "airwave_news_prefs"
    private const val KEY_SEEN_IDS = "seen_news_ids"
    private const val KEY_BROADCAST_IDS = "broadcast_news_ids"
    private const val KEY_LAST_BROADCAST_INDEX = "last_broadcast_index"

    private val defaultNews = listOf(
        NewsItem(
            id = "news_01",
            title = "Next-Gen Quantum Satellite Network Successfully Deployed in Orbit",
            description = "An international consortium of space agencies confirmed the successful orbital insertion of the Quantum-1 satellite constellation, creating the first tamper-proof global communications network using quantum entanglement encryption.",
            category = "Space & Science",
            published = "Oct 15, 2026 · 09:15 AM",
            order = 1
        ),
        NewsItem(
            id = "news_02",
            title = "Breakthrough Solid-State Battery Begins Mass Production for Electric Vehicles",
            description = "Automotive engineers have achieved a historic milestone with the commercial rollout of solid-state lithium-metal batteries, delivering over 1,000 kilometers of driving range on a single 12-minute ultra-fast charge.",
            category = "Clean Energy",
            published = "Oct 15, 2026 · 10:30 AM",
            order = 2
        ),
        NewsItem(
            id = "news_03",
            title = "Global Treaty on Autonomous AI Safety Ratified by 68 Nations",
            description = "Delegates at the Geneva Technology Summit unanimously signed the Geneva Accord on Autonomous Systems, establishing strict international safety thresholds, kill-switch requirements, and watermarking for synthetic media.",
            category = "AI & Society",
            published = "Oct 15, 2026 · 11:00 AM",
            order = 3
        ),
        NewsItem(
            id = "news_04",
            title = "High-Speed Trans-Continental Hyperloop Passenger Route Enters Final Testing",
            description = "Engineers successfully completed a full-speed vacuum tube trial reaching 980 km/h with passenger pods, paving the way for commercial launch between major metropolitan hubs by year end.",
            category = "Transit & Tech",
            published = "Oct 15, 2026 · 11:45 AM",
            order = 4
        ),
        NewsItem(
            id = "news_05",
            title = "Universal mRNA Cancer Vaccine Shows 88% Remission Rate in Phase 3 Trials",
            description = "Medical researchers announced unprecedented clinical trial results for a personalized mRNA immunotherapy that trains the immune system to recognize and eradicate multiple solid tumor mutations without chemotherapy.",
            category = "Medicine & Health",
            published = "Oct 15, 2026 · 12:20 PM",
            order = 5
        ),
        NewsItem(
            id = "news_06",
            title = "Renewable Energy Surpasses 70% of Total Power Grid Generation Across Europe",
            description = "Grid operators reported that solar, offshore wind, and advanced geothermal power supplied a record 72 percent of total electricity demand throughout the continent during the past quarter.",
            category = "Environment & Climate",
            published = "Oct 15, 2026 · 01:05 PM",
            order = 6
        ),
        NewsItem(
            id = "news_07",
            title = "Deep Ocean Exploration Submersible Discovers 400 New Marine Species",
            description = "Marine biologists aboard the autonomous explorer Nautilus-IV mapped uncharted hydrothermal vents in the Pacific Trench, cataloging hundreds of previously unknown bioluminescent organisms and unique ecosystems.",
            category = "Oceanography",
            published = "Oct 15, 2026 · 01:50 PM",
            order = 7
        ),
        NewsItem(
            id = "news_08",
            title = "World Athletics Championships: Historic Record Smashed in Men's 100m Sprint",
            description = "In a electrifying photo-finish final, the world record for the 100-meter dash was shattered before a sold-out stadium of 80,000 roaring spectators, clocking an astonishing 9.49 seconds.",
            category = "Sports",
            published = "Oct 15, 2026 · 02:15 PM",
            order = 8
        ),
        NewsItem(
            id = "news_09",
            title = "Global Reforestation Coalition Plants One Billionth Native Tree Ahead of Schedule",
            description = "A collaborative effort combining satellite monitoring, automated drone seed-planting, and community forestry projects achieved the ambitious target of restoring 2 million hectares of degraded tropical canopy.",
            category = "Conservation",
            published = "Oct 15, 2026 · 02:40 PM",
            order = 9
        ),
        NewsItem(
            id = "news_10",
            title = "Commercial Fusion Reactor Prototype Achieves Net Energy Gain for 72 Consecutive Hours",
            description = "Physicists operating the Commonwealth Torus tokamak sustained continuous net energy output of 250 megawatts for three full days, marking the dawn of virtually limitless clean baseload power.",
            category = "Physics & Energy",
            published = "Oct 15, 2026 · 03:10 PM",
            order = 10
        ),
        NewsItem(
            id = "news_11",
            title = "Archaeologists Uncover Submerged Ancient City Dating Back 8,000 Years",
            description = "High-resolution underwater LIDAR sonar off the Mediterranean coast revealed an expansive Neolithic settlement with stone architecture, paved avenues, and intact agricultural tools preserved beneath the seabed.",
            category = "Archaeology",
            published = "Oct 15, 2026 · 03:45 PM",
            order = 11
        ),
        NewsItem(
            id = "news_12",
            title = "Global Tourism Reaches All-Time High as Eco-Travel and Rail Vacations Surge",
            description = "International travelers are embracing zero-emission sleeper trains and community-based eco-lodges, with scenic alpine and coastal railways reporting full bookings for the upcoming holiday season.",
            category = "Travel & Lifestyle",
            published = "Oct 15, 2026 · 04:30 PM",
            order = 12
        ),
    )

    private val _newsItems = MutableStateFlow<List<NewsItem>>(defaultNews)
    val newsItems: StateFlow<List<NewsItem>> = _newsItems.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var listenerRegistration: ListenerRegistration? = null
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
     * Retrieves the next unbroadcast news story for the 1-minute live broadcast popup alert.
     * When all stories have been presented, the cycle wraps around so continuous popups can occur.
     */
    fun getNextUnbroadcastNews(context: Context): NewsItem? {
        loadPrefs(context)
        val currentList = _newsItems.value
        if (currentList.isEmpty()) return null

        val unbroadcast = currentList.filter { !broadcastIds.contains(it.id) }
        val nextItem = if (unbroadcast.isNotEmpty()) {
            unbroadcast.first()
        } else {
            // All items were presented; clear broadcast memory and loop back to the first
            broadcastIds.clear()
            persistPrefs(context)
            currentList.firstOrNull()
        }
        return nextItem
    }

    /**
     * Starts Firestore real-time listener to keep news stories synced live.
     */
    fun start(context: Context, scope: CoroutineScope) {
        loadPrefs(context)
        if (listenerRegistration != null) return

        try {
            val db = FirebaseFirestore.getInstance()
            listenerRegistration = db.collection(COLLECTION_NAME)
                .orderBy("order", Query.Direction.ASCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Firestore news listener error", error)
                        return@addSnapshotListener
                    }
                    if (snapshot != null && !snapshot.isEmpty) {
                        val items = snapshot.documents.mapNotNull { doc ->
                            try {
                                val id = doc.id
                                val title = doc.getString("title") ?: return@mapNotNull null
                                val description = doc.getString("description").orEmpty()
                                val category = doc.getString("category") ?: "World"
                                val published = doc.getString("published").orEmpty()
                                val order = doc.getLong("order")?.toInt() ?: 0
                                val isSeen = seenIds.contains(id)
                                NewsItem(
                                    id = id,
                                    title = title,
                                    description = description,
                                    category = category,
                                    published = published,
                                    order = order,
                                    isNew = !isSeen,
                                )
                            } catch (e: Exception) {
                                null
                            }
                        }
                        if (items.isNotEmpty()) {
                            _newsItems.value = items
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Firestore news listener", e)
        }
    }

    fun stop() {
        listenerRegistration?.remove()
        listenerRegistration = null
    }

    /**
     * Explicit one-shot refresh from Cloud Firestore.
     */
    suspend fun refresh(context: Context) {
        _isRefreshing.value = true
        withContext(Dispatchers.IO) {
            try {
                loadPrefs(context)
                val db = FirebaseFirestore.getInstance()
                val snapshot = db.collection(COLLECTION_NAME)
                    .orderBy("order", Query.Direction.ASCENDING)
                    .get()
                    .await()
                if (!snapshot.isEmpty) {
                    val items = snapshot.documents.mapNotNull { doc ->
                        try {
                            val id = doc.id
                            val title = doc.getString("title") ?: return@mapNotNull null
                            val description = doc.getString("description").orEmpty()
                            val category = doc.getString("category") ?: "World"
                            val published = doc.getString("published").orEmpty()
                            val order = doc.getLong("order")?.toInt() ?: 0
                            val isSeen = seenIds.contains(id)
                            NewsItem(
                                id = id,
                                title = title,
                                description = description,
                                category = category,
                                published = published,
                                order = order,
                                isNew = !isSeen,
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }
                    if (items.isNotEmpty()) {
                        _newsItems.value = items
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh news from Firestore", e)
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
