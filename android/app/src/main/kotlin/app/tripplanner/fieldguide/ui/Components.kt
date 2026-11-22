package app.tripplanner.fieldguide.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.LocalTaxi
import androidx.compose.material.icons.rounded.Luggage
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tripplanner.fieldguide.logic.BlockKind
import app.tripplanner.fieldguide.logic.family

val fg: FgColors @Composable get() = LocalFg.current

fun kindIcon(kind: BlockKind): ImageVector = when (kind) {
    BlockKind.FLIGHT -> Icons.Rounded.Flight
    BlockKind.TRAIN -> Icons.Rounded.Train
    BlockKind.TAXI -> Icons.Rounded.LocalTaxi
    BlockKind.BUS -> Icons.Rounded.DirectionsBus
    BlockKind.CABLE_CAR -> Icons.Rounded.Terrain
    BlockKind.CHECK_IN -> Icons.Rounded.Hotel
    BlockKind.CHECK_OUT -> Icons.Rounded.Luggage
    BlockKind.FOOD -> Icons.Rounded.Restaurant
    BlockKind.TOUR -> Icons.Rounded.Landscape
    BlockKind.SIGHT -> Icons.Rounded.Place
}

@Composable
fun kindColor(kind: BlockKind): Color = fg.family(kind.family)

/** Outlined card on the canvas. */
@Composable
fun FgCard(
    modifier: Modifier = Modifier,
    border: Color? = null,
    background: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(FgDims.radius)
    val c = fg
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background ?: c.card)
            .border(BorderStroke(c.cardBorderWidth.dp, border ?: c.cardBorder), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(FgDims.pad),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

enum class Tone { NEUTRAL, PRIMARY, WARNING, DANGER, SUCCESS }

@Composable
fun toneColors(tone: Tone): Pair<Color, Color> {
    val c = fg
    return when (tone) {
        Tone.NEUTRAL -> c.textSecondary to (if (c.isDark) Color(0xFF262A30) else Color(0xFFEEF0F3))
        Tone.PRIMARY -> c.primary to (if (c.isDark) Color(0xFF13263A) else Color(0xFFE3ECFB))
        Tone.WARNING -> c.warning to c.warningBg
        Tone.DANGER -> c.danger to c.dangerBg
        Tone.SUCCESS -> c.success to c.successBg
    }
}

/** Small status chip. Always icon or text, never colour alone. */
@Composable
fun StatusChip(text: String, tone: Tone = Tone.NEUTRAL, icon: ImageVector? = null, color: Color? = null) {
    val (fgc, bg) = toneColors(tone)
    val ink = color ?: fgc
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(14.dp), tint = ink)
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = FgType.chip, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipRow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}

/** Round badge with the kind icon in its type colour. */
@Composable
fun KindBadge(kind: BlockKind, muted: Boolean = false, size: Int = 36) {
    val color = if (muted) fg.textSecondary else kindColor(kind)
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(kindIcon(kind), contentDescription = null, tint = color, modifier = Modifier.size((size * 0.56f).dp))
    }
}

/** Coloured box with an icon and a few lines: warnings, not-booked guidance, cash needed. */
@Composable
fun Callout(tone: Tone, title: String, lines: List<String> = emptyList(), icon: ImageVector? = null, content: @Composable ColumnScope.() -> Unit = {}) {
    val (ink, bg) = toneColors(tone)
    val glyph = icon ?: when (tone) {
        Tone.WARNING -> Icons.Rounded.Warning
        Tone.DANGER -> Icons.Rounded.Error
        Tone.SUCCESS -> Icons.Rounded.CheckCircle
        else -> Icons.Rounded.Info
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bg)
            .border(1.dp, ink.copy(alpha = 0.35f), RoundedCornerShape(12.dp)).padding(12.dp),
    ) {
        Icon(glyph, null, tint = ink, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = FgType.cardTitle, color = fg.text)
            lines.forEach { Text(it, style = FgType.body, color = fg.text) }
            content()
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = FgType.section, color = fg.textSecondary, modifier = Modifier.weight(1f).semantics { heading() })
        trailing()
    }
}

/** Big primary action, 56 dp. */
@Composable
fun BigAction(text: String, icon: ImageVector?, modifier: Modifier = Modifier, primary: Boolean = true, onClick: () -> Unit) {
    val content: @Composable RowScope.() -> Unit = {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (primary) {
        Button(onClick = onClick, modifier = modifier.heightIn(min = FgDims.bigTouch), shape = RoundedCornerShape(14.dp), content = content)
    } else {
        OutlinedButton(
            onClick = onClick, modifier = modifier.heightIn(min = FgDims.bigTouch), shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, fg.cardBorder), colors = ButtonDefaults.outlinedButtonColors(contentColor = fg.text), content = content,
        )
    }
}

/** Two-column grid of label and value. */
@Composable
fun KeyFacts(facts: List<Pair<String, String>>) {
    if (facts.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        facts.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (label, value) ->
                    Column(Modifier.weight(1f)) {
                        Text(label, style = FgType.chip, color = fg.textSecondary)
                        Text(value, style = FgType.body, color = fg.text)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Ring with "6/11" inside. */
@Composable
fun ProgressRing(done: Int, total: Int, size: Int = 52) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size.dp)) {
        CircularProgressIndicator(
            progress = { if (total == 0) 0f else done.toFloat() / total },
            modifier = Modifier.size(size.dp),
            color = if (done == total && total > 0) fg.success else fg.primary,
            trackColor = fg.cardBorder,
            strokeWidth = 5.dp,
        )
        Text("$done/$total", style = FgType.chip, color = fg.text)
    }
}

/** Header that expands and collapses its content. */
@Composable
fun Expandable(title: String, initiallyOpen: Boolean = false, tone: Tone = Tone.NEUTRAL, content: @Composable ColumnScope.() -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(initiallyOpen) }
    val (ink, _) = toneColors(tone)
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = FgDims.touch).clickable(role = Role.Button) { open = !open },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = FgType.cardTitle, color = if (tone == Tone.NEUTRAL) fg.text else ink, modifier = Modifier.weight(1f))
            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = if (open) "Collapse" else "Expand", tint = fg.textSecondary)
        }
        if (open) Column(verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
fun Bullet(text: String, color: Color? = null) {
    Row {
        Text("•", style = FgType.body, color = fg.textSecondary, modifier = Modifier.width(14.dp))
        Text(text, style = FgType.body, color = color ?: fg.text)
    }
}

@Composable
fun EmptyState(title: String, body: String, icon: ImageVector = Icons.Rounded.Info) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = fg.textSecondary, modifier = Modifier.size(40.dp))
        Text(title, style = FgType.cardTitle, color = fg.text)
        Text(body, style = FgType.body, color = fg.textSecondary)
    }
}
