package lk.sugaapps.airwave.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import lk.sugaapps.airwave.MainActivity
import lk.sugaapps.airwave.R
import lk.sugaapps.airwave.engine.BroadcastEngine
import lk.sugaapps.airwave.engine.EngineRegistry

/**
 * Foreground Service that keeps the live broadcast running continuously
 * in the background even when the screen is locked or the user switches apps.
 *
 * Acquires a CPU WakeLock and Wi-Fi Lock, posts a persistent ON AIR notification
 * with live track metadata and a quick "Stop" action, and keeps the audio recording
 * and streaming engine active without OS throttling or suspension.
 */
class BroadcastForegroundService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var isForegroundActive = false
    private var notificationJob: Job? = null
    private val logicMonitor = ServiceLogicMonitor()
    private val engine: BroadcastEngine get() = EngineRegistry.engine

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireLocks()
        startNotificationUpdater()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            // Recreated by system in background with null intent;
            // Never call startForeground() here to prevent ForegroundServiceStartNotAllowedException on Android 14+
            stopSelf()
            return START_NOT_STICKY
        }

        val action = intent.action
        if (action == ACTION_STOP) {
            stopBroadcastAndSelf()
            return START_NOT_STICKY
        }

        startForegroundWithNotification()
        return START_NOT_STICKY
    }

    private fun startForegroundWithNotification() {
        val notification = createNotification(currentState())

        if (isForegroundActive) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.notify(NOTIFICATION_ID, notification)
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val hasMicPerm = ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.RECORD_AUDIO
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                val fgsType = if (hasMicPerm) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                }
                startForeground(NOTIFICATION_ID, notification, fgsType)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            isForegroundActive = true
        } catch (t: Throwable) {
            android.util.Log.e("Airwave", "startForeground failed: ${t.message}", t)
            // Catch ForegroundServiceStartNotAllowedException, SecurityException, etc.
            // Stop self cleanly so the entire application process is never brought down.
            stopSelf()
        }
    }

    /** Snapshot of the engine's live state used to render the notification. */
    data class NotificationState(
        val title: String,
        val artist: String,
        val isRecording: Boolean,
        val connectionState: Int,
        val connectionMessage: String,
        val micMuted: Boolean,
        val musicMuted: Boolean,
    )

    private fun currentState() = NotificationState(
        title = engine.currentTrackTitle.value,
        artist = engine.currentTrackArtist.value,
        isRecording = engine.isRecording.value,
        connectionState = engine.connectionState.value,
        connectionMessage = engine.connectionMessage.value,
        micMuted = engine.micMuted.value,
        musicMuted = engine.musicMuted.value,
    )

    /**
     * Single reactive updater (ported from the core app): combines the engine's
     * state into one [NotificationState], keeps the one notification current, and
     * lets [ServiceLogicMonitor] decide when a genuine error should stop the service.
     */
    private fun startNotificationUpdater() {
        notificationJob?.cancel()
        notificationJob = serviceScope.launch {
            combine(
                engine.currentTrackTitle,
                engine.currentTrackArtist,
                engine.isRecording,
                engine.connectionState,
                engine.connectionMessage,
                engine.micMuted,
                engine.musicMuted,
            ) { args: Array<Any?> ->
                NotificationState(
                    title = args[0] as String,
                    artist = args[1] as String,
                    isRecording = args[2] as Boolean,
                    connectionState = args[3] as Int,
                    connectionMessage = args[4] as String,
                    micMuted = args[5] as Boolean,
                    musicMuted = args[6] as Boolean,
                )
            }
                .distinctUntilChanged()
                .conflate()
                .collectLatest { state ->
                    val action = logicMonitor.determineAction(state.connectionState, state.connectionMessage)
                    if (action == ServiceLogicMonitor.ServiceAction.STOP_SERVICE) {
                        // Give the UI a moment to surface the error, then shut down.
                        delay(1000)
                        stopBroadcastAndSelf()
                        return@collectLatest
                    }
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    manager?.notify(NOTIFICATION_ID, createNotification(state))
                }
        }
    }

    private fun createNotification(state: NotificationState): Notification {
        val appIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val stopIntent = Intent(this, BroadcastForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val isConnected = state.connectionState == 1
        val isConnecting = state.connectionState == 0

        val title = if (state.title.isNotBlank() && state.title != "No track") state.title else "Airwave Agents"

        // Content line: artist + mute indicators, with a LIVE / Connecting suffix.
        var statusText = state.artist
        val indicators = mutableListOf<String>()
        if (state.micMuted) indicators.add("🔇 Mic")
        if (state.musicMuted) indicators.add("🔇 Music")
        if (indicators.isNotEmpty()) {
            statusText = if (statusText.isBlank()) indicators.joinToString(" ") else "$statusText • ${indicators.joinToString(" ")}"
        }
        val contentText = when {
            isConnecting -> "Connecting…"
            isConnected -> if (statusText.isBlank()) "LIVE" else "$statusText • LIVE"
            else -> statusText
        }

        val subText = when {
            state.isRecording && isConnected -> "Recording • Live"
            isConnected -> "Live"
            isConnecting -> "Connecting…"
            else -> "Broadcast"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_broadcast)
            .setColor(0xFF2563EB.toInt())
            .setContentTitle(title)
            .setContentText(contentText)
            .setSubText(subText)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(R.drawable.ic_stop, "Stop", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Live Broadcast",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows ongoing live broadcast controls and now playing status when screen is locked"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun acquireLocks() {
        runCatching {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Airwave:BroadcastWakeLock").apply {
                setReferenceCounted(false)
                acquire(24 * 60 * 60 * 1000L) // 24 hours max
            }
        }

        runCatching {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Airwave:BroadcastWifiLock").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun releaseLocks() {
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            wakeLock = null
        }
        runCatching {
            if (wifiLock?.isHeld == true) wifiLock?.release()
            wifiLock = null
        }
    }

    private fun stopBroadcastAndSelf() {
        runCatching { engine.stopBroadcast() }
        runCatching { engine.stopRecording() }
        isForegroundActive = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        isForegroundActive = false
        releaseLocks()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "airwave_live_broadcast"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "lk.sugaapps.airwave.service.START"
        const val ACTION_STOP = "lk.sugaapps.airwave.service.STOP"

        fun start(context: Context) {
            runCatching {
                val intent = Intent(context, BroadcastForegroundService::class.java).apply {
                    action = ACTION_START
                }
                ContextCompat.startForegroundService(context, intent)
            }.onFailure { e ->
                android.util.Log.e("Airwave", "BroadcastForegroundService start error: ${e.message}")
            }
        }

        fun stop(context: Context) {
            runCatching {
                val intent = Intent(context, BroadcastForegroundService::class.java).apply {
                    action = ACTION_STOP
                }
                context.stopService(intent)
            }.onFailure { e ->
                android.util.Log.e("Airwave", "BroadcastForegroundService stop error: ${e.message}")
            }
        }
    }
}
