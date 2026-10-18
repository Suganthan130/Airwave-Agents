package lk.sugaapps.airwave.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import lk.sugaapps.airwave.data.MixerStore
import lk.sugaapps.airwave.engine.EngineRegistry
import lk.sugaapps.airwave.ui.components.SectionCard
import lk.sugaapps.airwave.ui.components.SegmentedPills
import lk.sugaapps.airwave.ui.components.SliderRow
import lk.sugaapps.airwave.ui.components.SwitchRow
import lk.sugaapps.airwave.ui.theme.BrandBlue
import lk.sugaapps.airwave.ui.theme.DangerRed
import lk.sugaapps.airwave.ui.theme.OnAirTeal
import lk.sugaapps.airwave.ui.theme.SurfaceMuted
import lk.sugaapps.airwave.ui.theme.WarnAmber
import lk.sugaapps.airwave.ui.theme.TextPrimary
import lk.sugaapps.airwave.ui.theme.TextSecondary

/** Broadcast mixer. State is permanently persisted in MixerStore and applied to the engine. */
@Composable
fun MixerScreen(
    modifier: Modifier = Modifier,
    onOpenEqualizer: () -> Unit = {},
) {
    val context = LocalContext.current
    val engine = remember { EngineRegistry.engine }
    val micLevel by engine.micLevel.collectAsState()
    val musicLevel by engine.musicLevel.collectAsState()

    val initial = remember { MixerStore.loadMixer(context) }

    var monitorLevel by rememberSaveable { mutableFloatStateOf(initial.monitorLevel) }
    var monitorSource by rememberSaveable { mutableIntStateOf(initial.monitorSource) }
    var micVol by rememberSaveable { mutableFloatStateOf(initial.micVol) }
    var micMuted by rememberSaveable { mutableStateOf(initial.micMuted) }
    var musicVol by rememberSaveable { mutableFloatStateOf(initial.musicVol) }
    var musicMuted by rememberSaveable { mutableStateOf(initial.musicMuted) }
    var masterVol by rememberSaveable { mutableFloatStateOf(initial.masterVol) }
    var compressor by rememberSaveable { mutableStateOf(initial.compressor) }
    var noiseGate by rememberSaveable { mutableStateOf(initial.noiseGate) }
    var deEsser by rememberSaveable { mutableStateOf(initial.deEsser) }
    var ducking by rememberSaveable { mutableStateOf(initial.ducking) }

    // Persist immediately on any change so values are never reset on navigation or app restart
    LaunchedEffect(
        monitorLevel, monitorSource, micVol, micMuted,
        musicVol, musicMuted, masterVol, compressor,
        noiseGate, deEsser, ducking
    ) {
        val state = MixerStore.MixerState(
            monitorLevel = monitorLevel,
            monitorSource = monitorSource,
            micVol = micVol,
            micMuted = micMuted,
            musicVol = musicVol,
            musicMuted = musicMuted,
            masterVol = masterVol,
            compressor = compressor,
            noiseGate = noiseGate,
            deEsser = deEsser,
            ducking = ducking,
        )
        MixerStore.saveMixer(context, state)
        MixerStore.applyMixerToEngine(state, engine)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard(title = "Monitoring") {
            SliderRow("Level", monitorLevel, {
                monitorLevel = it
                engine.setMonitorVolume(it)
            }, "${(monitorLevel * 100).toInt()}%")
            Spacer(Modifier.height(12.dp))
            Text("Monitor source", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            SegmentedPills(listOf("Music", "Mic", "Both"), monitorSource) {
                monitorSource = it
                engine.setMonitorSource(it)
            }
        }

        ChannelCard(
            name = "Mic",
            volume = micVol,
            onVolumeChange = {
                micVol = it
                engine.setMicVolume(it)
            },
            muted = micMuted,
            onToggleMute = {
                val newMute = !micMuted
                micMuted = newMute
                engine.setMicMuted(newMute)
            },
            level = micLevel,
        )
        ChannelCard(
            name = "Music",
            volume = musicVol,
            onVolumeChange = {
                musicVol = it
                engine.setMusicVolume(it)
            },
            muted = musicMuted,
            onToggleMute = {
                val newMute = !musicMuted
                musicMuted = newMute
                engine.setMusicMuted(newMute)
            },
            level = musicLevel,
        )

        SectionCard(title = "Master") {
            SliderRow("Output", masterVol, { masterVol = it }, "${(masterVol * 100).toInt()}%")
        }

        SectionCard(title = "Pro broadcast FX") {
            SwitchRow("Compressor", compressor, { compressor = it })
            SwitchRow("Noise gate", noiseGate, {
                noiseGate = it
                engine.setNoiseGate(it)
            })
            SwitchRow("De-esser", deEsser, { deEsser = it })
            SwitchRow("Auto-ducking", ducking, {
                ducking = it
                engine.setAutoDucking(it)
            }, subtitle = "Lower music under the mic")
        }

        SectionCard(title = "Equalizer") {
            Text(
                "10-band EQ, presets, bass boost and virtualizer.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onOpenEqualizer, shape = RoundedCornerShape(8.dp)) {
                Text("Open equalizer", color = BrandBlue, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun ChannelCard(
    name: String,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    muted: Boolean,
    onToggleMute: () -> Unit,
    level: Float,
) {
    SectionCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onToggleMute) {
                Text(
                    if (muted) "Muted" else "Live",
                    color = if (muted) DangerRed else OnAirTeal,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        LevelMeter(if (muted) 0f else level)
        Spacer(Modifier.height(12.dp))
        SliderRow("Volume", volume, onVolumeChange, "${(volume * 100).toInt()}%")
    }
}

@Composable
private fun LevelMeter(level: Float) {
    val segments = 20
    val active = (level * segments).toInt()
    val isClipping = active > (segments * 0.85f)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .semantics {
                    contentDescription = if (isClipping) {
                        "Level peaking — reduce gain"
                    } else {
                        "Signal level ${(level * 100).toInt()} percent"
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            for (i in 0 until segments) {
                val color = when {
                    i >= active -> SurfaceMuted
                    i > segments * 0.85f -> DangerRed
                    i > segments * 0.6f -> WarnAmber
                    else -> OnAirTeal
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(color),
                )
            }
        }
        // Clipping is signalled by text + color, never color alone.
        if (isClipping) {
            Spacer(Modifier.width(6.dp))
            Text(
                "PEAK",
                style = MaterialTheme.typography.labelSmall,
                color = DangerRed,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
