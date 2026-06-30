package org.openbabyphone.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Open Babyphone brand palette — see docs/design/app-ui-redesign.md
// Brand accents are shared across themes for the logo gradient and pills.
// Light theme uses darker tonal variants for primary/secondary so text and
// outlined controls stay WCAG AA legible on white/soft-white surfaces.
private val NightCanvas = Color(0xFF080B12)
private val DeepPanel = Color(0xFF101827)
private val SurfaceVariantDark = Color(0xFF1A2235)
private val SoftWhite = Color(0xFFF6F7FB)
private val QuietBlueGrey = Color(0xFFA5B2C8)
internal val LiveAudioCyan = Color(0xFF5FF2D2)
internal val NetworkBlue = Color(0xFF5DA8FF)
private val SoftWhiteBg = Color(0xFFF6F7FB)
private val DeepPanelFg = Color(0xFF101827)
private val MutedLight = Color(0xFF5A6A82)
private val SurfaceVariantLight = Color(0xFFE8ECF2)
private val LiveAudioCyanLight = Color(0xFF006B5E)
private val NetworkBlueLight = Color(0xFF0256A9)
private val CyanTintLight = Color(0xFFB8F5EA)
private val BlueTintLight = Color(0xFFD6EBFF)

private val DarkColorScheme = darkColorScheme(
    primary = LiveAudioCyan,
    onPrimary = NightCanvas,
    primaryContainer = LiveAudioCyan,
    onPrimaryContainer = NightCanvas,
    secondary = NetworkBlue,
    onSecondary = NightCanvas,
    secondaryContainer = NetworkBlue,
    onSecondaryContainer = NightCanvas,
    tertiary = NetworkBlue,
    onTertiary = NightCanvas,
    tertiaryContainer = NetworkBlue,
    onTertiaryContainer = NightCanvas,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = NightCanvas,
    onBackground = SoftWhite,
    surface = DeepPanel,
    onSurface = SoftWhite,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = QuietBlueGrey,
    outline = Color(0xFF5A6A82)
)

private val LightColorScheme = lightColorScheme(
    primary = LiveAudioCyanLight,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = CyanTintLight,
    onPrimaryContainer = LiveAudioCyanLight,
    secondary = NetworkBlueLight,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = BlueTintLight,
    onSecondaryContainer = NetworkBlueLight,
    tertiary = NetworkBlueLight,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = BlueTintLight,
    onTertiaryContainer = NetworkBlueLight,
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = SoftWhiteBg,
    onBackground = DeepPanelFg,
    surface = Color(0xFFFFFFFF),
    onSurface = DeepPanelFg,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = MutedLight,
    outline = Color(0xFF72787D)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val AppTypography = Typography()

@Composable
fun QuietEngineTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S -> {
            val context = androidx.compose.ui.platform.LocalContext.current
            if (darkTheme) androidx.compose.material3.dynamicDarkColorScheme(context)
            else androidx.compose.material3.dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}