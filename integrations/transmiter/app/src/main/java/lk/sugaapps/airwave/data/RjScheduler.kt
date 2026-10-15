package lk.sugaapps.airwave.data

import android.content.Context
import android.util.Log
import java.util.Calendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import lk.sugaapps.airwave.engine.Playlist

/**
 * Automates periodic AI Radio Jockey time announcements and AI Full Takeover operations during a live broadcast.
 * - When AI Full Takeover is ON:
 *   - Automatically voices and broadcasts Flash News on customized schedule (default every 10 min).
 *   - Automatically voices and announces RJ Time Checks on customized schedule (default every 30 min).
 *   - Manages seamless radio station autonomy without dead air.
 * - When in manual mode:
 *   - Automates standard clock-based time announcements (:00, :15, :30, :45).
 */
object RjScheduler {
    private const val TAG = "RjScheduler"
    private var schedulerJob: Job? = null
    private var lastTriggeredMinuteKey: String = ""
    private var lastNewsTriggerMs: Long = 0L
    private var lastTakeoverTimeTriggerMs: Long = 0L

    var onTriggerNewsNow: ((headline: String, content: String) -> Unit)? = null
    var onTriggerNewsUpNext: ((headline: String, content: String) -> Unit)? = null
    var onTriggerTimeCheck: (() -> Unit)? = null

    private val _announcementEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val announcementEvents: SharedFlow<String> = _announcementEvents

    fun start(context: Context, scope: CoroutineScope) {
        if (schedulerJob?.isActive == true) return

        schedulerJob = scope.launch(Dispatchers.IO) {
            Log.d(TAG, "RjScheduler started. Takeover: ${RjConfig.isTakeoverEnabled}")
            while (isActive) {
                delay(15_000L) // Check every 15 seconds
                checkAndTrigger(context.applicationContext)
            }
        }
    }

    fun stop() {
        schedulerJob?.cancel()
        schedulerJob = null
        lastTriggeredMinuteKey = ""
        lastNewsTriggerMs = 0L
        lastTakeoverTimeTriggerMs = 0L
        Log.d(TAG, "RjScheduler stopped.")
    }

    /**
     * Resets takeover timers when settings change or takeover is toggled.
     */
    fun resetTakeoverTimers() {
        lastNewsTriggerMs = System.currentTimeMillis()
        lastTakeoverTimeTriggerMs = System.currentTimeMillis()
    }

    private suspend fun checkAndTrigger(context: Context) {
        val nowMs = System.currentTimeMillis()

        if (RjConfig.isTakeoverEnabled) {
            // --- 1. AI Takeover: Autonomous News Broadcast ---
            val newsIntervalMs = RjConfig.takeoverNewsIntervalMinutes * 60_000L
            if (lastNewsTriggerMs == 0L) {
                lastNewsTriggerMs = nowMs
            } else if (nowMs - lastNewsTriggerMs >= newsIntervalMs) {
                lastNewsTriggerMs = nowMs
                val nextNews = NewsManager.getNextUnbroadcastNews(context)
                if (nextNews != null) {
                    NewsManager.markBroadcast(context, nextNews.id)
                    val title = nextNews.title
                    val desc = nextNews.description.ifBlank { nextNews.title }
                    Log.i(TAG, "AI Takeover: Triggering news bulletin: $title")
                    if (RjConfig.takeoverNewsMode == RjConfig.NEWS_MODE_INTERRUPT) {
                        onTriggerNewsNow?.invoke(title, desc)
                    } else {
                        onTriggerNewsUpNext?.invoke(title, desc)
                    }
                }
            }

            // --- 2. AI Takeover: Autonomous Time Announcement ---
            val timeIntervalMs = RjConfig.takeoverTimeIntervalMinutes * 60_000L
            if (lastTakeoverTimeTriggerMs == 0L) {
                lastTakeoverTimeTriggerMs = nowMs
            } else if (nowMs - lastTakeoverTimeTriggerMs >= timeIntervalMs) {
                lastTakeoverTimeTriggerMs = nowMs
                Log.i(TAG, "AI Takeover: Triggering time check announcement")
                if (onTriggerTimeCheck != null) {
                    onTriggerTimeCheck?.invoke()
                } else {
                    runCatching {
                        val current = Playlist.current
                        val nextItem = Playlist.items.getOrNull(Playlist.currentIndex + 1)
                        val item = TimeVoiceOver.create(
                            context = context,
                            nowTrack = current?.title.orEmpty(),
                            nowArtist = current?.artist.orEmpty(),
                            nextTrack = nextItem?.title.orEmpty(),
                            nextArtist = nextItem?.artist.orEmpty(),
                        )
                        Playlist.enqueueNext(item)
                        _announcementEvents.tryEmit(item.title)
                    }.onFailure { e ->
                        Log.w(TAG, "Takeover time announcement failed: ${e.message}")
                    }
                }
            }
        } else {
            // Standard clock-based periodic time announcement if configured
            val interval = RjConfig.intervalMinutes
            if (interval > 0) {
                val now = Calendar.getInstance()
                val minute = now.get(Calendar.MINUTE)
                val hour = now.get(Calendar.HOUR_OF_DAY)
                val day = now.get(Calendar.DAY_OF_YEAR)
                val year = now.get(Calendar.YEAR)

                val minuteKey = "$year-$day-$hour-$minute"

                if (minute % interval == 0 && minuteKey != lastTriggeredMinuteKey) {
                    lastTriggeredMinuteKey = minuteKey
                    Log.i(TAG, "Triggering automatic RJ time announcement for $hour:$minute")

                    runCatching {
                        val current = Playlist.current
                        val nextItem = Playlist.items.getOrNull(Playlist.currentIndex + 1)

                        val item = TimeVoiceOver.create(
                            context = context,
                            nowTrack = current?.title.orEmpty(),
                            nowArtist = current?.artist.orEmpty(),
                            nextTrack = nextItem?.title.orEmpty(),
                            nextArtist = nextItem?.artist.orEmpty(),
                        )

                        Playlist.enqueueNext(item)
                        _announcementEvents.tryEmit(item.title)
                        Log.i(TAG, "Successfully enqueued auto time announcement: ${item.title}")
                    }.onFailure { e ->
                        Log.w(TAG, "Automatic time announcement generation failed: ${e.message}")
                    }
                }
            }
        }
    }
}
