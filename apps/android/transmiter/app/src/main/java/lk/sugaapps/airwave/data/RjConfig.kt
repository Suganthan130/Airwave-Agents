package lk.sugaapps.airwave.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AI Radio Jockey (RJ) configuration and scheduling preferences.
 * Broadcasters can configure the RJ voice personality and automatic on-air time announcements.
 * Public and safe — contains no private native engine details or credentials.
 */
object RjConfig {
    const val VOICE_CHIRP = 0
    const val VOICE_STUDIO = 1
    const val VOICE_TAMIL = 2

    // Compatibility aliases
    const val VOICE_MALE = 0
    const val VOICE_FEMALE = 0

    private val _voiceIndexFlow = MutableStateFlow(VOICE_CHIRP)
    val voiceIndexFlow: StateFlow<Int> = _voiceIndexFlow.asStateFlow()

    var voiceIndex: Int
        get() = _voiceIndexFlow.value
        set(value) { _voiceIndexFlow.value = value }

    // Auto time announcement interval options:
    // 0 = Off, 1 = Every 15 min, 2 = Every 30 min, 3 = Every 60 min (Top of the hour)
    const val INTERVAL_OFF = 0
    const val INTERVAL_15_MIN = 1
    const val INTERVAL_30_MIN = 2
    const val INTERVAL_60_MIN = 3

    private val _autoTimeIntervalFlow = MutableStateFlow(INTERVAL_OFF)
    val autoTimeIntervalFlow: StateFlow<Int> = _autoTimeIntervalFlow.asStateFlow()

    var autoTimeIntervalIndex: Int
        get() = _autoTimeIntervalFlow.value
        set(value) { _autoTimeIntervalFlow.value = value }

    // Speech speed options: 0 = Relaxed (0.9x), 1 = Normal (1.0x), 2 = Fast (1.1x)
    const val SPEED_RELAXED = 0
    const val SPEED_NORMAL = 1
    const val SPEED_FAST = 2

    private val _speedIndexFlow = MutableStateFlow(SPEED_NORMAL)
    val speedIndexFlow: StateFlow<Int> = _speedIndexFlow.asStateFlow()

    var speedIndex: Int
        get() = _speedIndexFlow.value
        set(value) { _speedIndexFlow.value = value }

    val speakingRate: Float
        get() = when (speedIndex) {
            SPEED_RELAXED -> 0.90f
            SPEED_FAST -> 1.12f
            else -> 1.00f
        }

    val speedDisplayName: String
        get() = when (speedIndex) {
            SPEED_RELAXED -> "Relaxed (0.9x)"
            SPEED_FAST -> "Fast (1.1x)"
            else -> "Normal (1.0x)"
        }

    val voiceCode: String
        get() = when (voiceIndex) {
            VOICE_STUDIO -> "studio"
            VOICE_TAMIL -> "tamil"
            else -> "chirp"
        }

    val voiceDisplayName: String
        get() = when (voiceIndex) {
            VOICE_STUDIO -> "Studio Pro (en-US-Studio-O)"
            VOICE_TAMIL -> "Tamil Host (Chirp 3 HD Leda)"
            else -> "Chirp 3 HD Host (Leda)"
        }

    fun voiceCodeToIndex(code: String?): Int = when (code?.lowercase()?.trim()) {
        "studio", "1", "female" -> VOICE_STUDIO
        "tamil", "2" -> VOICE_TAMIL
        else -> VOICE_CHIRP
    }

    fun speakingRateToIndex(rate: Float?): Int = when {
        rate == null -> SPEED_NORMAL
        rate <= 0.95f -> SPEED_RELAXED
        rate >= 1.08f -> SPEED_FAST
        else -> SPEED_NORMAL
    }

    val intervalMinutes: Int
        get() = when (autoTimeIntervalIndex) {
            INTERVAL_15_MIN -> 15
            INTERVAL_30_MIN -> 30
            INTERVAL_60_MIN -> 60
            else -> 0
        }

    val intervalDisplayName: String
        get() = when (autoTimeIntervalIndex) {
            INTERVAL_15_MIN -> "Every 15 min"
            INTERVAL_30_MIN -> "Every 30 min"
            INTERVAL_60_MIN -> "Every 60 min"
            else -> "Off"
        }

    // --- AI Full Takeover (Auto-Pilot) ---
    private val _isTakeoverEnabledFlow = MutableStateFlow(false)
    val isTakeoverEnabledFlow: StateFlow<Boolean> = _isTakeoverEnabledFlow.asStateFlow()

    var isTakeoverEnabled: Boolean
        get() = _isTakeoverEnabledFlow.value
        set(value) { _isTakeoverEnabledFlow.value = value }

    // News interval in minutes (default 10 min)
    val NEWS_INTERVAL_OPTIONS = listOf(5, 10, 15, 20, 30)
    private val _takeoverNewsIntervalFlow = MutableStateFlow(10)
    val takeoverNewsIntervalFlow: StateFlow<Int> = _takeoverNewsIntervalFlow.asStateFlow()

    var takeoverNewsIntervalMinutes: Int
        get() = _takeoverNewsIntervalFlow.value
        set(value) { _takeoverNewsIntervalFlow.value = value }

    // Time check interval in minutes (default 30 min)
    val TIME_INTERVAL_OPTIONS = listOf(10, 15, 30, 45, 60)
    private val _takeoverTimeIntervalFlow = MutableStateFlow(30)
    val takeoverTimeIntervalFlow: StateFlow<Int> = _takeoverTimeIntervalFlow.asStateFlow()

    var takeoverTimeIntervalMinutes: Int
        get() = _takeoverTimeIntervalFlow.value
        set(value) { _takeoverTimeIntervalFlow.value = value }

    // Auto-Replay (Continuous playlist repeat): loops playlist from start when it reaches end
    private val _autoReplayFlow = MutableStateFlow(true)
    val autoReplayFlow: StateFlow<Boolean> = _autoReplayFlow.asStateFlow()

    var autoReplay: Boolean
        get() = _autoReplayFlow.value
        set(value) { _autoReplayFlow.value = value }

    // News Delivery Mode: 0 = Interrupt & auto-resume (Play Now), 1 = Queue into Up Next
    const val NEWS_MODE_INTERRUPT = 0
    const val NEWS_MODE_UP_NEXT = 1
    private val _takeoverNewsModeFlow = MutableStateFlow(NEWS_MODE_INTERRUPT)
    val takeoverNewsModeFlow: StateFlow<Int> = _takeoverNewsModeFlow.asStateFlow()

    var takeoverNewsMode: Int
        get() = _takeoverNewsModeFlow.value
        set(value) { _takeoverNewsModeFlow.value = value }
}
