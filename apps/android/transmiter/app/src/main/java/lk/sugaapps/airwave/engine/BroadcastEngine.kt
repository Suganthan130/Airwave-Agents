package lk.sugaapps.airwave.engine

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Generic broadcast-engine contract the UI binds to. It intentionally exposes
 * only broadcasting *concepts* — never the private native implementation or its
 * JNI method names. The real engine (native C++ + AudioStreamer) lives in the
 * git-ignored private module and implements this interface; the public build
 * falls back to [NoOpBroadcastEngine].
 */
interface BroadcastEngine {

    // --- Observable state (for meters, timers, status) ---
    val micLevel: StateFlow<Float>        // 0f..1f
    val musicLevel: StateFlow<Float>      // 0f..1f
    val musicPosition: StateFlow<Float>   // 0f..1f
    val isLive: StateFlow<Boolean>
    val isRecording: StateFlow<Boolean>
    val micMuted: StateFlow<Boolean>
    val musicMuted: StateFlow<Boolean>

    // Real connection state (from the engine, not optimistic):
    // 0 = connecting, 1 = connected/live, 2 = offline/disconnected.
    val connectionState: StateFlow<Int>
    val connectionMessage: StateFlow<String>   // human message / error text
    val connectionLatencyMs: StateFlow<Long>
    val connectionDurationMs: StateFlow<Long>

    // Now-playing track
    val currentTrackTitle: StateFlow<String>
    val currentTrackArtist: StateFlow<String>

    // --- Lifecycle ---
    fun initialize(context: Context)
    fun release()

    // --- Broadcast / recording ---
    fun startBroadcast()
    fun stopBroadcast()
    fun startRecording()
    fun stopRecording()

    // --- Mixer ---
    fun setMicMuted(muted: Boolean)
    fun setMusicMuted(muted: Boolean)
    fun setMicVolume(volume: Float)
    fun setMusicVolume(volume: Float)
    fun setMonitorVolume(volume: Float)
    fun setMonitorSource(source: Int)
    fun setNoiseGate(enabled: Boolean)
    fun setAutoDucking(enabled: Boolean)

    // --- Playback / music ---
    fun setMusicPlaying(playing: Boolean)
    fun seek(percent: Float)
    fun selectTrack(context: Context, uri: Uri, autoPlay: Boolean = true, startPercent: Float = 0f)
    fun setCurrentTrackInfo(title: String, artist: String)

    // --- EQ / enhancers ---
    fun setEqEnabled(enabled: Boolean)
    fun setEqBands(gains: FloatArray)
    fun setBassBoost(enabled: Boolean, strength: Float)
    fun setVirtualizer(enabled: Boolean, strength: Float)
}

/** Default engine for the public build: safe no-ops, so the UI runs without any native code. */
object NoOpBroadcastEngine : BroadcastEngine {
    override val micLevel = MutableStateFlow(0f)
    override val musicLevel = MutableStateFlow(0f)
    override val musicPosition = MutableStateFlow(0f)
    override val isLive = MutableStateFlow(false)
    override val isRecording = MutableStateFlow(false)
    override val micMuted = MutableStateFlow(false)
    override val musicMuted = MutableStateFlow(false)
    override val connectionState = MutableStateFlow(2)
    override val connectionMessage = MutableStateFlow("")
    override val connectionLatencyMs = MutableStateFlow(0L)
    override val connectionDurationMs = MutableStateFlow(0L)
    override val currentTrackTitle = MutableStateFlow("No track")
    override val currentTrackArtist = MutableStateFlow("")

    override fun initialize(context: Context) {}
    override fun release() {}
    override fun startBroadcast() { isLive.value = true; connectionState.value = 1 }
    override fun stopBroadcast() { isLive.value = false; isRecording.value = false; connectionState.value = 2 }
    override fun startRecording() { isRecording.value = true }
    override fun stopRecording() { isRecording.value = false }
    override fun setMicMuted(muted: Boolean) { micMuted.value = muted }
    override fun setMusicMuted(muted: Boolean) { musicMuted.value = muted }
    override fun setMicVolume(volume: Float) {}
    override fun setMusicVolume(volume: Float) {}
    override fun setMonitorVolume(volume: Float) {}
    override fun setMonitorSource(source: Int) {}
    override fun setNoiseGate(enabled: Boolean) {}
    override fun setAutoDucking(enabled: Boolean) {}
    override fun setMusicPlaying(playing: Boolean) {}
    override fun seek(percent: Float) {}
    override fun selectTrack(context: Context, uri: Uri, autoPlay: Boolean, startPercent: Float) {}
    override fun setCurrentTrackInfo(title: String, artist: String) {
        currentTrackTitle.value = title
        currentTrackArtist.value = artist
    }
    override fun setEqEnabled(enabled: Boolean) {}
    override fun setEqBands(gains: FloatArray) {}
    override fun setBassBoost(enabled: Boolean, strength: Float) {}
    override fun setVirtualizer(enabled: Boolean, strength: Float) {}
}

/**
 * Holds the active engine. The private module swaps in the real native-backed
 * engine at startup; the public build keeps [NoOpBroadcastEngine].
 */
object EngineRegistry {
    @Volatile
    var engine: BroadcastEngine = NoOpBroadcastEngine
}
