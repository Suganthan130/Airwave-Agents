package lk.sugaapps.airwave.data

import android.content.Context
import android.content.SharedPreferences
import lk.sugaapps.airwave.engine.BroadcastEngine
import lk.sugaapps.airwave.engine.EngineRegistry

/**
 * Local persistence for audio mixer, volume levels, and equalizer DSP settings.
 * Backed by Android SharedPreferences and in-memory cache so values never reset
 * when switching between tabs (Mixer, AI, Tracks, Broadcast, Settings) or on app reopen.
 */
object MixerStore {

    private const val PREFS_NAME = "airwave_mixer_prefs"

    // Mixer Keys
    private const val KEY_MONITOR_LEVEL = "monitor_level"
    private const val KEY_MONITOR_SOURCE = "monitor_source"
    private const val KEY_MIC_VOL = "mic_vol"
    private const val KEY_MIC_MUTED = "mic_muted"
    private const val KEY_MUSIC_VOL = "music_vol"
    private const val KEY_MUSIC_MUTED = "music_muted"
    private const val KEY_MASTER_VOL = "master_vol"
    private const val KEY_COMPRESSOR = "compressor"
    private const val KEY_NOISE_GATE = "noise_gate"
    private const val KEY_DE_ESSER = "de_esser"
    private const val KEY_DUCKING = "ducking"

    // Equalizer Keys
    private const val KEY_EQ_ENABLED = "eq_enabled"
    private const val KEY_EQ_PRESET = "eq_preset"
    private const val KEY_EQ_BANDS = "eq_bands"
    private const val KEY_BASS_ENABLED = "bass_enabled"
    private const val KEY_BASS_BOOST = "bass_boost"
    private const val KEY_VIRTUALIZER_ENABLED = "virtualizer_enabled"
    private const val KEY_VIRTUALIZER = "virtualizer"

    data class MixerState(
        val monitorLevel: Float = 0.6f,
        val monitorSource: Int = 2,
        val micVol: Float = 0.8f,
        val micMuted: Boolean = false,
        val musicVol: Float = 0.5f,
        val musicMuted: Boolean = false,
        val masterVol: Float = 0.75f,
        val compressor: Boolean = true,
        val noiseGate: Boolean = true,
        val deEsser: Boolean = false,
        val ducking: Boolean = true,
    )

    data class EqState(
        val enabled: Boolean = true,
        val selectedPreset: String = "Flat",
        val bands: List<Float> = List(10) { 0f },
        val bassEnabled: Boolean = false,
        val bassBoost: Float = 0f,
        val virtualizerEnabled: Boolean = false,
        val virtualizer: Float = 0f,
    )

    @Volatile
    private var cachedMixer: MixerState? = null

    @Volatile
    private var cachedEq: EqState? = null

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Loads mixer state from memory cache or SharedPreferences.
     */
    fun loadMixer(context: Context): MixerState {
        cachedMixer?.let { return it }
        val p = prefs(context)
        val state = MixerState(
            monitorLevel = p.getFloat(KEY_MONITOR_LEVEL, 0.6f),
            monitorSource = p.getInt(KEY_MONITOR_SOURCE, 2),
            micVol = p.getFloat(KEY_MIC_VOL, 0.8f),
            micMuted = p.getBoolean(KEY_MIC_MUTED, false),
            musicVol = p.getFloat(KEY_MUSIC_VOL, 0.5f),
            musicMuted = p.getBoolean(KEY_MUSIC_MUTED, false),
            masterVol = p.getFloat(KEY_MASTER_VOL, 0.75f),
            compressor = p.getBoolean(KEY_COMPRESSOR, true),
            noiseGate = p.getBoolean(KEY_NOISE_GATE, true),
            deEsser = p.getBoolean(KEY_DE_ESSER, false),
            ducking = p.getBoolean(KEY_DUCKING, true),
        )
        cachedMixer = state
        return state
    }

    /**
     * Persists mixer state immediately to cache and SharedPreferences.
     */
    fun saveMixer(context: Context, state: MixerState) {
        cachedMixer = state
        prefs(context).edit()
            .putFloat(KEY_MONITOR_LEVEL, state.monitorLevel)
            .putInt(KEY_MONITOR_SOURCE, state.monitorSource)
            .putFloat(KEY_MIC_VOL, state.micVol)
            .putBoolean(KEY_MIC_MUTED, state.micMuted)
            .putFloat(KEY_MUSIC_VOL, state.musicVol)
            .putBoolean(KEY_MUSIC_MUTED, state.musicMuted)
            .putFloat(KEY_MASTER_VOL, state.masterVol)
            .putBoolean(KEY_COMPRESSOR, state.compressor)
            .putBoolean(KEY_NOISE_GATE, state.noiseGate)
            .putBoolean(KEY_DE_ESSER, state.deEsser)
            .putBoolean(KEY_DUCKING, state.ducking)
            .apply()
    }

    /**
     * Applies mixer settings to the active broadcast engine.
     */
    fun applyMixerToEngine(state: MixerState, engine: BroadcastEngine = EngineRegistry.engine) {
        runCatching {
            engine.setMonitorVolume(state.monitorLevel)
            engine.setMonitorSource(state.monitorSource)
            engine.setMicVolume(state.micVol)
            engine.setMicMuted(state.micMuted)
            engine.setMusicVolume(state.musicVol)
            engine.setMusicMuted(state.musicMuted)
            engine.setNoiseGate(state.noiseGate)
            engine.setAutoDucking(state.ducking)
        }
    }

    /**
     * Loads equalizer state from memory cache or SharedPreferences.
     */
    fun loadEq(context: Context): EqState {
        cachedEq?.let { return it }
        val p = prefs(context)
        val bandsStr = p.getString(KEY_EQ_BANDS, null)
        val parsedBands = bandsStr?.split(",")?.mapNotNull { it.toFloatOrNull() }
        val bands = if (parsedBands != null && parsedBands.size == 10) parsedBands else List(10) { 0f }

        val state = EqState(
            enabled = p.getBoolean(KEY_EQ_ENABLED, true),
            selectedPreset = p.getString(KEY_EQ_PRESET, "Flat") ?: "Flat",
            bands = bands,
            bassEnabled = p.getBoolean(KEY_BASS_ENABLED, false),
            bassBoost = p.getFloat(KEY_BASS_BOOST, 0f),
            virtualizerEnabled = p.getBoolean(KEY_VIRTUALIZER_ENABLED, false),
            virtualizer = p.getFloat(KEY_VIRTUALIZER, 0f),
        )
        cachedEq = state
        return state
    }

    /**
     * Persists equalizer state immediately to cache and SharedPreferences.
     */
    fun saveEq(context: Context, state: EqState) {
        cachedEq = state
        prefs(context).edit()
            .putBoolean(KEY_EQ_ENABLED, state.enabled)
            .putString(KEY_EQ_PRESET, state.selectedPreset)
            .putString(KEY_EQ_BANDS, state.bands.joinToString(","))
            .putBoolean(KEY_BASS_ENABLED, state.bassEnabled)
            .putFloat(KEY_BASS_BOOST, state.bassBoost)
            .putBoolean(KEY_VIRTUALIZER_ENABLED, state.virtualizerEnabled)
            .putFloat(KEY_VIRTUALIZER, state.virtualizer)
            .apply()
    }

    /**
     * Applies equalizer settings to the active broadcast engine.
     */
    fun applyEqToEngine(state: EqState, engine: BroadcastEngine = EngineRegistry.engine) {
        runCatching {
            engine.setEqEnabled(state.enabled)
            engine.setEqBands(state.bands.toFloatArray())
            engine.setBassBoost(state.bassEnabled, state.bassBoost)
            engine.setVirtualizer(state.virtualizerEnabled, state.virtualizer)
        }
    }

    /**
     * Applies all saved mixer and equalizer settings to the broadcast engine.
     * Called on app startup in MainActivity.onCreate.
     */
    fun applyAllToEngine(context: Context, engine: BroadcastEngine = EngineRegistry.engine) {
        val mixer = loadMixer(context)
        applyMixerToEngine(mixer, engine)
        val eq = loadEq(context)
        applyEqToEngine(eq, engine)
    }
}
