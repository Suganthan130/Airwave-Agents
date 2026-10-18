package lk.sugaapps.airwave.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import lk.sugaapps.airwave.data.MixerStore
import lk.sugaapps.airwave.engine.EngineRegistry
import lk.sugaapps.airwave.ui.components.SectionCard
import lk.sugaapps.airwave.ui.components.SliderRow
import lk.sugaapps.airwave.ui.components.SwitchRow
import lk.sugaapps.airwave.ui.theme.BorderStrong
import lk.sugaapps.airwave.ui.theme.BrandBlue
import lk.sugaapps.airwave.ui.theme.PageBackground
import lk.sugaapps.airwave.ui.theme.SurfaceMuted
import lk.sugaapps.airwave.ui.theme.TextPrimary
import lk.sugaapps.airwave.ui.theme.TextSecondary

private val eqBands = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

private val eqPresets: Map<String, List<Float>> = mapOf(
    "Flat" to List(10) { 0f },
    "Bass boost" to listOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f),
    "Vocal" to listOf(-2f, -1f, 0f, 2f, 4f, 4f, 3f, 1f, 0f, -1f),
    "Rock" to listOf(4f, 3f, -1f, -2f, -1f, 1f, 3f, 4f, 4f, 4f),
    "Treble" to listOf(0f, 0f, 0f, 0f, 0f, 1f, 3f, 5f, 6f, 6f),
)

/** Equalizer. State is persisted in MixerStore and applied to the engine. */
@Composable
fun EqualizerScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val engine = remember { EngineRegistry.engine }
    val initial = remember { MixerStore.loadEq(context) }

    var enabled by rememberSaveable { mutableStateOf(initial.enabled) }
    var selectedPreset by rememberSaveable { mutableStateOf(initial.selectedPreset) }
    var bassBoost by rememberSaveable { mutableFloatStateOf(initial.bassBoost) }
    var bassEnabled by rememberSaveable { mutableStateOf(initial.bassEnabled) }
    var virtualizer by rememberSaveable { mutableFloatStateOf(initial.virtualizer) }
    var virtualizerEnabled by rememberSaveable { mutableStateOf(initial.virtualizerEnabled) }
    val bands: SnapshotStateList<Float> = remember { initial.bands.toMutableStateList() }

    LaunchedEffect(
        enabled, selectedPreset, bassEnabled, bassBoost,
        virtualizerEnabled, virtualizer, bands.toList()
    ) {
        val state = MixerStore.EqState(
            enabled = enabled,
            selectedPreset = selectedPreset,
            bands = bands.toList(),
            bassEnabled = bassEnabled,
            bassBoost = bassBoost,
            virtualizerEnabled = virtualizerEnabled,
            virtualizer = virtualizer,
        )
        MixerStore.saveEq(context, state)
        MixerStore.applyEqToEngine(state, engine)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PageBackground)
            .statusBarsPadding(),
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text("‹ Back", color = BrandBlue, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.weight(1f))
            Text("Equalizer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            Switch(
                checked = enabled,
                onCheckedChange = { enabled = it; engine.setEqEnabled(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = BrandBlue,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = BorderStrong,
                    uncheckedBorderColor = BorderStrong,
                ),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Presets
            SectionCard(title = "Presets") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    eqPresets.keys.forEach { preset ->
                        PresetChip(preset, preset == selectedPreset) {
                            selectedPreset = preset
                            val values = eqPresets[preset] ?: List(10) { 0f }
                            values.forEachIndexed { i, v -> bands[i] = v }
                            engine.setEqBands(bands.toFloatArray())
                        }
                    }
                }
            }

            // Bands
            SectionCard(title = "10-band equalizer") {
                eqBands.forEachIndexed { i, freq ->
                    SliderRow(
                        label = "$freq Hz",
                        value = bands[i],
                        onValueChange = { bands[i] = it; selectedPreset = "Custom"; engine.setEqBands(bands.toFloatArray()) },
                        valueLabel = "${if (bands[i] >= 0) "+" else ""}${bands[i].toInt()} dB",
                        valueRange = -12f..12f,
                    )
                    if (i < eqBands.lastIndex) Spacer(Modifier.height(4.dp))
                }
            }

            // Enhancers
            SectionCard(title = "Enhancers") {
                SwitchRow("Bass boost", bassEnabled, { bassEnabled = it; engine.setBassBoost(it, bassBoost) })
                if (bassEnabled) {
                    SliderRow("Strength", bassBoost, { bassBoost = it; engine.setBassBoost(true, it) }, "${(bassBoost * 100).toInt()}%")
                    Spacer(Modifier.height(8.dp))
                }
                SwitchRow("Virtualizer", virtualizerEnabled, { virtualizerEnabled = it; engine.setVirtualizer(it, virtualizer) })
                if (virtualizerEnabled) {
                    SliderRow("Strength", virtualizer, { virtualizer = it; engine.setVirtualizer(true, it) }, "${(virtualizer * 100).toInt()}%")
                }
            }
        }
    }
}

@Composable
private fun PresetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    // Whole chip is a >=48dp tap target (WCAG 2.5.8 / Fitts's Law).
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) BrandBlue else SurfaceMuted)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = if (selected) Color.White else TextSecondary,
        )
    }
}
