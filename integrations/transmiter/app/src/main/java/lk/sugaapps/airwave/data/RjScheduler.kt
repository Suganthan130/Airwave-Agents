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
 * Automates periodic AI Radio Jockey time announcements during a live broadcast.
 * Follows professional radio clock scheduling (:00, :15, :30, :45).
 * Public and safe — binds only to Playlist and TimeVoiceOver.
 */
object RjScheduler {
    private const val TAG = "RjScheduler"
    private var schedulerJob: Job? = null
    private var lastTriggeredMinuteKey: String = ""

    private val _announcementEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val announcementEvents: SharedFlow<String> = _announcementEvents

    fun start(context: Context, scope: CoroutineScope) {
        if (schedulerJob?.isActive == true) return

        schedulerJob = scope.launch(Dispatchers.IO) {
            Log.d(TAG, "RjScheduler started. Interval: ${RjConfig.intervalMinutes} min")
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
        Log.d(TAG, "RjScheduler stopped.")
    }

    private suspend fun checkAndTrigger(context: Context) {
        val interval = RjConfig.intervalMinutes
        if (interval <= 0) return

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
