package app.tripplanner.fieldguide.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tripplanner.fieldguide.data.ThemeMode
import app.tripplanner.fieldguide.logic.KindFamily

/** Field Guide colour tokens. Type colours stay fixed; there is no dynamic colour. */
@Immutable
data class FgColors(
    val isDark: Boolean,
    val background: Color,
    val card: Color,
    val cardBorder: Color,
    val cardBorderWidth: Float,
    val text: Color,
    val textSecondary: Color,
    val primary: Color,
    val onPrimary: Color,
    val transit: Color,
    val stay: Color,
    val food: Color,
    val activity: Color,
    val warning: Color,
    val warningBg: Color,
    val danger: Color,
    val dangerBg: Color,
    val success: Color,
    val successBg: Color,
) {
    fun family(f: KindFamily): Color = when (f) {
        KindFamily.TRANSIT -> transit
        KindFamily.STAY -> stay
        KindFamily.FOOD -> food
        KindFamily.ACTIVITY -> activity
    }
}

val DarkColors = FgColors(
    isDark = true,
    background = Color(0xFF000000),
    card = Color(0xFF1A1D21),
    cardBorder = Color(0xFF2C313A),
    cardBorderWidth = 1f,
    text = Color(0xFFF3F5F7),
    textSecondary = Color(0xFFB4BCC6),
    primary = Color(0xFF64B5F6),
    onPrimary = Color(0xFF001E33),
    transit = Color(0xFF4FC3F7),
    stay = Color(0xFFBA68C8),
    food = Color(0xFFFFB74D),
    activity = Color(0xFF4DB6AC),
    warning = Color(0xFFFFD54F),
    warningBg = Color(0xFF2E250B),
    danger = Color(0xFFFF8A80),
    dangerBg = Color(0xFF311212),
    success = Color(0xFF81C784),
    successBg = Color(0xFF122B18),
)

val LightColors = FgColors(
    isDark = false,
    background = Color(0xFFF8F9FA),
    card = Color(0xFFFFFFFF),
    cardBorder = Color(0xFFE0E3E7),
    cardBorderWidth = 1f,
    text = Color(0xFF111315),
    textSecondary = Color(0xFF4A525D),
    primary = Color(0xFF0B57D0),
    onPrimary = Color(0xFFFFFFFF),
    transit = Color(0xFF0277BD),
    stay = Color(0xFF7B1FA2),
    food = Color(0xFFE65100),
    activity = Color(0xFF00695C),
    warning = Color(0xFFB26A00),
    warningBg = Color(0xFFFFF8E1),
    danger = Color(0xFFC62828),
    dangerBg = Color(0xFFFFEBEE),
    success = Color(0xFF2E7D32),
    successBg = Color(0xFFE8F5E9),
)

/** High contrast for bright sun: pure white, black text, 2 dp black outlines. */
val SunlightColors = FgColors(
    isDark = false,
    background = Color(0xFFFFFFFF),
    card = Color(0xFFFFFFFF),
    cardBorder = Color(0xFF000000),
    cardBorderWidth = 2f,
    text = Color(0xFF000000),
    textSecondary = Color(0xFF1F2428),
    primary = Color(0xFF003B99),
    onPrimary = Color(0xFFFFFFFF),
    transit = Color(0xFF01579B),
    stay = Color(0xFF4A148C),
    food = Color(0xFFBF360C),
    activity = Color(0xFF004D40),
    warning = Color(0xFF8C5000),
    warningBg = Color(0xFFFFECB3),
    danger = Color(0xFFB71C1C),
    dangerBg = Color(0xFFFFCDD2),
    success = Color(0xFF1B5E20),
    successBg = Color(0xFFC8E6C9),
)

val LocalFg = staticCompositionLocalOf { DarkColors }

/** Tabular figures so times line up in the rail. */
private const val TNUM = "tnum"

object FgType {
    val hero = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TNUM)
    val railTime = TextStyle(fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TNUM)
    val cardTitle = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    val body = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontFeatureSettings = TNUM)
    val meta = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontFeatureSettings = TNUM)
    val chip = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
    val section = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp)
    val code = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TNUM, letterSpacing = 1.sp)
}

object FgDims {
    val margin = 16.dp
    val gap = 12.dp
    val pad = 16.dp
    val radius = 16.dp
    val touch = 48.dp
    val bigTouch = 56.dp
}

fun colorsFor(mode: ThemeMode, systemDark: Boolean): FgColors = when (mode) {
    ThemeMode.DARK -> DarkColors
    ThemeMode.LIGHT -> LightColors
    ThemeMode.SUNLIGHT -> SunlightColors
    ThemeMode.SYSTEM -> if (systemDark) DarkColors else LightColors
}

@Composable
fun FieldGuideTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val fg = colorsFor(mode, isSystemInDarkTheme())
    val scheme = if (fg.isDark) {
        darkColorScheme(
            background = fg.background, surface = fg.background, surfaceVariant = fg.card,
            surfaceContainerLowest = fg.background, surfaceContainerLow = fg.card, surfaceContainer = fg.card,
            surfaceContainerHigh = Color(0xFF22262B), surfaceContainerHighest = Color(0xFF2A2F35),
            onBackground = fg.text, onSurface = fg.text, onSurfaceVariant = fg.textSecondary,
            primary = fg.primary, onPrimary = fg.onPrimary, secondaryContainer = Color(0xFF1E3A52),
            onSecondaryContainer = fg.text, error = fg.danger, onError = Color.Black,
            outline = fg.cardBorder, outlineVariant = fg.cardBorder,
        )
    } else {
        lightColorScheme(
            background = fg.background, surface = fg.background, surfaceVariant = fg.card,
            surfaceContainerLowest = fg.card, surfaceContainerLow = fg.card, surfaceContainer = fg.card,
            surfaceContainerHigh = Color(0xFFF0F2F4), surfaceContainerHighest = Color(0xFFE8EBEE),
            onBackground = fg.text, onSurface = fg.text, onSurfaceVariant = fg.textSecondary,
            primary = fg.primary, onPrimary = fg.onPrimary, secondaryContainer = Color(0xFFD6E4FA),
            onSecondaryContainer = fg.text, error = fg.danger, onError = Color.White,
            outline = fg.cardBorder, outlineVariant = fg.cardBorder,
        )
    }
    val base = Typography()
    val type = base.copy(
        bodyLarge = base.bodyLarge.copy(fontFeatureSettings = TNUM),
        bodyMedium = FgType.body,
        titleMedium = FgType.cardTitle,
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
    CompositionLocalProvider(LocalFg provides fg) {
        MaterialTheme(colorScheme = scheme, typography = type, content = content)
    }
}
