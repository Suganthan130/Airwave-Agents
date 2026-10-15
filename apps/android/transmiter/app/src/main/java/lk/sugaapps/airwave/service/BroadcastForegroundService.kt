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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
    private val engine: BroadcastEngine get() = EngineRegistry.engine

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireLocks()
        observeEngineState()
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
        val title = when (engine.connectionState.value) {
            0 -> "🟡 Connecting... — Airwave Agents"
            1 -> "🔴 ON AIR — Airwave Agents"
            else -> if (engine.isRecording.value) "🎙️ Recording — Airwave Agents" else "🎙️ Broadcasting — Airwave Agents"
        }
        val notification = buildNotification(
            title = title,
            text = formatNowPlayingText(engine.currentTrackTitle.value, engine.currentTrackArtist.value)
        )

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

    private fun observeEngineState() {
        // Update notification text when tracks change
        serviceScope.launch {
            engine.currentTrackTitle.collect { title ->
                updateNotification(title, engine.currentTrackArtist.value)
            }
        }

        serviceScope.launch {
            engine.currentTrackArtist.collect { artist ->
                updateNotification(engine.currentTrackTitle.value, artist)
            }
        }

        // Auto-update notification and auto-stop service when connection ends
        serviceScope.launch {
            engine.connectionState.collect { state ->
                when (state) {
                    0, 1 -> updateNotification(engine.currentTrackTitle.value, engine.currentTrackArtist.value)
                    2 -> {
                        if (!engine.isRecording.value) {
                            stopBroadcastAndSelf()
                        }
                    }
                }
            }
        }

        serviceScope.launch {
            engine.isRecording.collect { isRec ->
                if (isRec) {
                    updateNotification(engine.currentTrackTitle.value, engine.currentTrackArtist.value)
                } else if (engine.connectionState.value == 2) {
                    stopBroadcastAndSelf()
                }
            }
        }
    }

    private fun updateNotification(track: String, artist: String) {
        val title = when (engine.connectionState.value) {
            0 -> "🟡 Connecting... — Airwave Agents"
            1 -> "🔴 ON AIR — Airwave Agents"
            else -> if (engine.isRecording.value) "🎙️ Recording — Airwave Agents" else "🎙️ Broadcasting — Airwave Agents"
        }
        val notification = buildNotification(
            title = title,
            text = formatNowPlayingText(track, artist)
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, notification)
    }

    private fun formatNowPlayingText(track: String, artist: String): String {
        return if (track.isNotBlank() && track != "No track") {
            if (artist.isNotBlank()) "Now Playing: $track — $artist" else "Now Playing: $track"
        } else {
            "Broadcasting live to listeners"
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
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

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_broadcast)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText("Live Broadcast")
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
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
