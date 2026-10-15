package lk.sugaapps.airwave.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Professional light scheme. Dynamic color is intentionally disabled so the
// brand palette stays consistent across devices.
private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = SurfaceWhite,
    secondary = TextSecondary,
    onSecondary = SurfaceWhite,
    tertiary = OnAirTeal,
    onTertiary = SurfaceWhite,
    background = PageBackground,
    onBackground = TextPrimary,
    surface = SurfaceWhite,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceMuted,
    onSurfaceVariant = TextSecondary,
    outline = BorderStrong,
    outlineVariant = BorderLight,
    error = DangerRed,
    onError = SurfaceWhite,
)

@Composable
fun AirWaveTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography,
        content = content
    )
}
