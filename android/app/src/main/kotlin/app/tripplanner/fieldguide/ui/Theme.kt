package app.tripplanner.fieldguide.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Amber = Color(0xFFFFD60A)
val Dim = Color(0xFFBDBDBD)
val Outline = Color(0xFF2C2C2C)

// Pure black surfaces keep OLED pixels off. There is no light theme and no dynamic colour.
private val OledScheme = darkColorScheme(
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF101010),
    surfaceContainer = Color(0xFF101010),
    surfaceContainerHigh = Color(0xFF161616),
    surfaceContainerHighest = Color(0xFF1C1C1C),
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Dim,
    primary = Amber,
    onPrimary = Color.Black,
    secondary = Amber,
    onSecondary = Color.Black,
    error = Color(0xFFFF453A),
    onError = Color.Black,
    outline = Outline,
    outlineVariant = Outline,
)

@Composable
fun FieldGuideTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = OledScheme, content = content)

/** An outlined card. A 1 dp line on black instead of a raised grey surface. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Outline, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}
