package lk.sugaapps.airwave.engine

/**
 * Broadcast connection settings the UI writes (from the Settings screen) and the
 * engine reads when going live. Public and generic — no proprietary details.
 */
object StreamConfig {
    @Volatile var host: String = ""
    @Volatile var port: Int = 8000
    @Volatile var mount: String = "/live"
    @Volatile var user: String = "source"
    @Volatile var password: String = ""
    @Volatile var sampleRate: Int = 44100
    @Volatile var bitrate: Int = 128
    @Volatile var channels: Int = 2
    @Volatile var bufferSize: Int = 4096
    @Volatile var autoReconnect: Boolean = true
    @Volatile var publicListeningUrl: String = ""
}
