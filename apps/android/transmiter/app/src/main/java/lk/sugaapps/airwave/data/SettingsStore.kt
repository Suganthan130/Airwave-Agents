package lk.sugaapps.airwave.data

import android.content.Context
import android.content.SharedPreferences
import lk.sugaapps.airwave.engine.StreamConfig

/**
 * Local persistence using Android SharedPreferences.
 * Keeps all settings values permanently on device so they never reset
 * when navigating between screens/tabs or when reopening the app.
 */
object SettingsStore {

    private const val PREFS_NAME = "airwave_settings_prefs"

    private const val KEY_SERVER_TYPE = "server_type"
    private const val KEY_HOST = "host"
    private const val KEY_PORT = "port"
    private const val KEY_MOUNT = "mount"
    private const val KEY_USERNAME = "username"
    private const val KEY_PASSWORD = "password"
    private const val KEY_BITRATE = "bitrate"
    private const val KEY_SAMPLE_RATE = "sample_rate"
    private const val KEY_CHANNELS = "channels"
    private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
    private const val KEY_AUTO_RECONNECT = "auto_reconnect"
    private const val KEY_LOW_LATENCY = "low_latency"
    private const val KEY_RJ_VOICE = "rj_voice"
    private const val KEY_RJ_SPEED = "rj_speed"
    private const val KEY_AUTO_TIME_INTERVAL = "auto_time_interval"
    private const val KEY_STATION_TITLE = "station_title"
    private const val KEY_PUBLIC_LISTENING_URL = "public_listening_url"
    private const val KEY_INITIALIZED = "has_saved_settings"
    private const val KEY_TAKEOVER_ENABLED = "takeover_enabled"
    private const val KEY_TAKEOVER_NEWS_INTERVAL = "takeover_news_interval"
    private const val KEY_TAKEOVER_TIME_INTERVAL = "takeover_time_interval"
    private const val KEY_TAKEOVER_AUTO_REPLAY = "takeover_auto_replay"
    private const val KEY_TAKEOVER_NEWS_MODE = "takeover_news_mode"

    data class State(
        val serverType: Int = 0,
        val host: String = "163.245.218.61",
        val port: String = "8005",
        val mount: String = "/",
        val username: String = "source",
        val password: String = "9EpTzPzj",
        val bitrate: Int = 1,
        val sampleRate: Int = 0,
        val channels: Int = 1,
        val keepScreenOn: Boolean = true,
        val autoReconnect: Boolean = true,
        val lowLatency: Boolean = false,
        val rjVoice: Int = 0,
        val rjSpeed: Int = 1,
        val autoTimeInterval: Int = 0,
        val stationTitle: String = "",
        val publicListeningUrl: String = "https://us1.radiostreamhub.com/listen/ai_builder_cup_2026_-_airwave-agents/radio.mp3",
        val takeoverEnabled: Boolean = false,
        val takeoverNewsInterval: Int = 10,
        val takeoverTimeInterval: Int = 30,
        val takeoverAutoReplay: Boolean = true,
        val takeoverNewsMode: Int = 0,
    )

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun reset(context: Context) {
        prefs(context).edit().clear().apply()
    }

    /**
     * Loads all settings from SharedPreferences.
     * If not previously saved, falls back to current StreamConfig / PrivateExperience defaults.
     */
    fun load(context: Context): State {
        val p = prefs(context)
        val defaultHost = StreamConfig.host.ifBlank { "http://us1.radiostreamhub.com" }
        val defaultPort = if (StreamConfig.port > 0) StreamConfig.port.toString() else "8005"
        val defaultMount = StreamConfig.mount.ifBlank { "/live" }
        val defaultUser = StreamConfig.user.ifBlank { "source" }
        val defaultPass = StreamConfig.password.ifBlank { "hAdLdHy4" }
        val defaultBitrate = when (StreamConfig.bitrate) {
            64 -> 0
            192 -> 2
            320 -> 3
            else -> 1 // 128
        }
        val defaultSampleRate = if (StreamConfig.sampleRate == 48000) 1 else 0
        val defaultChannels = if (StreamConfig.channels == 1) 0 else 1
        val defaultAutoReconnect = StreamConfig.autoReconnect
        val defaultPublicUrl = StreamConfig.publicListeningUrl.ifBlank {
            "https://us1.radiostreamhub.com/listen/ai_builder_cup_2026_-_airwave-agents/radio.mp3"
        }

        return State(
            serverType = p.getInt(KEY_SERVER_TYPE, 0),
            host = p.getString(KEY_HOST, defaultHost) ?: defaultHost,
            port = p.getString(KEY_PORT, defaultPort) ?: defaultPort,
            mount = p.getString(KEY_MOUNT, defaultMount) ?: defaultMount,
            username = p.getString(KEY_USERNAME, defaultUser) ?: defaultUser,
            password = p.getString(KEY_PASSWORD, defaultPass) ?: defaultPass,
            bitrate = p.getInt(KEY_BITRATE, defaultBitrate),
            sampleRate = p.getInt(KEY_SAMPLE_RATE, defaultSampleRate),
            channels = p.getInt(KEY_CHANNELS, defaultChannels),
            keepScreenOn = p.getBoolean(KEY_KEEP_SCREEN_ON, true),
            autoReconnect = p.getBoolean(KEY_AUTO_RECONNECT, defaultAutoReconnect),
            lowLatency = p.getBoolean(KEY_LOW_LATENCY, false),
            rjVoice = p.getInt(KEY_RJ_VOICE, RjConfig.voiceIndex),
            rjSpeed = p.getInt(KEY_RJ_SPEED, RjConfig.speedIndex),
            autoTimeInterval = p.getInt(KEY_AUTO_TIME_INTERVAL, RjConfig.autoTimeIntervalIndex),
            stationTitle = p.getString(KEY_STATION_TITLE, "") ?: "",
            publicListeningUrl = p.getString(KEY_PUBLIC_LISTENING_URL, defaultPublicUrl) ?: defaultPublicUrl,
            takeoverEnabled = p.getBoolean(KEY_TAKEOVER_ENABLED, false),
            takeoverNewsInterval = p.getInt(KEY_TAKEOVER_NEWS_INTERVAL, 10),
            takeoverTimeInterval = p.getInt(KEY_TAKEOVER_TIME_INTERVAL, 30),
            takeoverAutoReplay = p.getBoolean(KEY_TAKEOVER_AUTO_REPLAY, true),
            takeoverNewsMode = p.getInt(KEY_TAKEOVER_NEWS_MODE, 0),
        )
    }

    /**
     * Saves all settings permanently to SharedPreferences.
     */
    fun save(context: Context, state: State) {
        prefs(context).edit().apply {
            putBoolean(KEY_INITIALIZED, true)
            putInt(KEY_SERVER_TYPE, state.serverType)
            putString(KEY_HOST, state.host)
            putString(KEY_PORT, state.port)
            putString(KEY_MOUNT, state.mount)
            putString(KEY_USERNAME, state.username)
            putString(KEY_PASSWORD, state.password)
            putInt(KEY_BITRATE, state.bitrate)
            putInt(KEY_SAMPLE_RATE, state.sampleRate)
            putInt(KEY_CHANNELS, state.channels)
            putBoolean(KEY_KEEP_SCREEN_ON, state.keepScreenOn)
            putBoolean(KEY_AUTO_RECONNECT, state.autoReconnect)
            putBoolean(KEY_LOW_LATENCY, state.lowLatency)
            putInt(KEY_RJ_VOICE, state.rjVoice)
            putInt(KEY_RJ_SPEED, state.rjSpeed)
            putInt(KEY_AUTO_TIME_INTERVAL, state.autoTimeInterval)
            putString(KEY_STATION_TITLE, state.stationTitle)
            putString(KEY_PUBLIC_LISTENING_URL, state.publicListeningUrl)
            putBoolean(KEY_TAKEOVER_ENABLED, state.takeoverEnabled)
            putInt(KEY_TAKEOVER_NEWS_INTERVAL, state.takeoverNewsInterval)
            putInt(KEY_TAKEOVER_TIME_INTERVAL, state.takeoverTimeInterval)
            putBoolean(KEY_TAKEOVER_AUTO_REPLAY, state.takeoverAutoReplay)
            putInt(KEY_TAKEOVER_NEWS_MODE, state.takeoverNewsMode)
            apply()
        }
    }

    /**
     * Applies stored settings to the active StreamConfig and RjConfig in memory.
     */
    fun applyToEngine(state: State) {
        StreamConfig.host = state.host.trim()
        StreamConfig.port = state.port.toIntOrNull() ?: 8000
        StreamConfig.mount = state.mount.trim()
        StreamConfig.user = state.username.trim()
        StreamConfig.password = state.password
        StreamConfig.bitrate = intArrayOf(64, 128, 192, 320).getOrElse(state.bitrate) { 128 }
        StreamConfig.sampleRate = if (state.sampleRate == 1) 48000 else 44100
        StreamConfig.channels = if (state.channels == 0) 1 else 2
        StreamConfig.autoReconnect = state.autoReconnect
        StreamConfig.serverType = state.serverType
        if (state.publicListeningUrl.isNotBlank() && StationManager.isValidPublicStreamUrl(state.publicListeningUrl)) {
            StreamConfig.publicListeningUrl = state.publicListeningUrl.trim()
        }

        RjConfig.voiceIndex = state.rjVoice
        RjConfig.speedIndex = state.rjSpeed
        RjConfig.autoTimeIntervalIndex = state.autoTimeInterval
        RjConfig.isTakeoverEnabled = state.takeoverEnabled
        RjConfig.takeoverNewsIntervalMinutes = state.takeoverNewsInterval
        RjConfig.takeoverTimeIntervalMinutes = state.takeoverTimeInterval
        RjConfig.autoReplay = state.takeoverAutoReplay
        RjConfig.takeoverNewsMode = state.takeoverNewsMode
    }
}
