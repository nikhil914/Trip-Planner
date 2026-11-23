package app.tripplanner.fieldguide.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tripplanner.fieldguide.data.Status
import app.tripplanner.fieldguide.logic.gaps
import app.tripplanner.fieldguide.logic.hhmm
import app.tripplanner.fieldguide.logic.humanDuration
import app.tripplanner.fieldguide.logic.spokenDuration

private const val RAIL = 58

/** Chips shown on a card and in the detail header. */
@Composable
fun BlockChips(b: BlockUi, overlaps: Boolean, compact: Boolean = true) {
    val f = b.plan.flags
    ChipRow {
        if (b.plan.isDone) StatusChip("Done", Tone.SUCCESS, Icons.Rounded.CheckCircle)
        if (b.plan.isSkipped) StatusChip("Skipped", Tone.NEUTRAL, Icons.Rounded.Block)
        if (f.notBooked) StatusChip("Not booked", Tone.WARNING, Icons.Rounded.Warning)
        if (f.incomplete != null) StatusChip("Incomplete", Tone.WARNING, Icons.Rounded.Warning)
        if (overlaps) StatusChip("Overlaps", Tone.DANGER, Icons.Rounded.Warning)
        if (b.e.isFixedTime) StatusChip("Fixed", Tone.NEUTRAL, Icons.Rounded.PushPin)
        if (f.cashOnly) StatusChip("Cash only", Tone.WARNING, Icons.Rounded.Payments)
        if (f.nonRefundable) StatusChip("Non-refundable", Tone.DANGER)
        if (f.freeCancelUntil != null) StatusChip("Free cancel until ${f.freeCancelUntil}", Tone.SUCCESS)
        if (f.dressCode) StatusChip("Dress code", Tone.NEUTRAL)
        if (!compact && b.plan.parsed.planB.isNotEmpty()) StatusChip("Plan B", Tone.PRIMARY)
        if (f.choices.isNotEmpty()) StatusChip("Pick one", Tone.PRIMARY)
    }
}

fun spokenCard(b: BlockUi, overlaps: Boolean): String {
    val start = b.e.startMinute
    val parts = mutableListOf<String>()
    if (start != null) parts += "${start.hhmm()} to ${(start + b.e.durationMinutes).hhmm()}"
    parts += b.plan.displayTitle.replace("→", "to")
    parts += spokenDuration(b.e.durationMinutes)
    if (b.e.isFixedTime) parts += "Fixed time"
    if (b.plan.flags.notBooked) parts += "Not booked"
    b.plan.flags.incomplete?.let { parts += "Incomplete: $it" }
    if (b.plan.flags.cashOnly) parts += "Cash only"
    if (b.plan.flags.nonRefundable) parts += "Non-refundable"
    if (overlaps) parts += "Overlaps a fixed booking"
    when (b.e.status) {
        Status.DONE -> parts += "Done"
        Status.SKIPPED -> parts += "Skipped"
    }
    return parts.joinToString(". ") + ". Double tap for details."
}

/** One block on the rail: time on the left, the card on the right. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineCard(vm: AppViewModel, trip: TripUi, b: BlockUi, past: Boolean) {
    val nav = LocalNav.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val pending = b.e.status == Status.PENDING
    val overlaps = b.id in trip.colliding
    val quick = quickAction(context, nav, b)
    val local = trip.trip?.localCurrency ?: ""

    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress); vm.markBlock(b.id, Status.DONE, undoable = true)
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress); vm.markBlock(b.id, Status.SKIPPED, undoable = true)
                }
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false
        },
    )

    Row(Modifier.fillMaxWidth().padding(horizontal = FgDims.margin)) {
        Column(Modifier.width(RAIL.dp).padding(top = 14.dp)) {
            val start = b.e.startMinute ?: 0
            Text(start.hhmm(), style = FgType.railTime, color = if (overlaps) fg.danger else if (pending) fg.text else fg.textSecondary)
            Text((start + b.e.durationMinutes).hhmm(), style = FgType.meta, color = fg.textSecondary)
        }
        SwipeToDismissBox(
            state = state,
            modifier = Modifier.weight(1f),
            enableDismissFromStartToEnd = pending,
            enableDismissFromEndToStart = pending,
            backgroundContent = {
                val toDone = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
                Row(
                    Modifier.fillMaxSize().clip(RoundedCornerShape(FgDims.radius))
                        .background(if (toDone) fg.successBg else toneColors(Tone.NEUTRAL).second).padding(horizontal = 20.dp),
                    horizontalArrangement = if (toDone) Arrangement.Start else Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (toDone) Icons.Rounded.CheckCircle else Icons.Rounded.Block, null, tint = if (toDone) fg.success else fg.textSecondary)
                    Spacer(Modifier.width(8.dp))
                    Text(if (toDone) "Done" else "Skip", style = FgType.cardTitle, color = fg.text)
                }
            },
        ) {
            FgCard(
                modifier = Modifier
                    .alpha(if (!pending || past) 0.6f else 1f)
                    .semantics(mergeDescendants = true) {
                        contentDescription = spokenCard(b, overlaps)
                        customActions = if (pending) listOf(
                            CustomAccessibilityAction("Mark done") { vm.markBlock(b.id, Status.DONE, true); true },
                            CustomAccessibilityAction("Skip") { vm.markBlock(b.id, Status.SKIPPED, true); true },
                        ) else listOf(CustomAccessibilityAction("Reopen") { vm.markBlock(b.id, Status.PENDING, true); true })
                    },
                border = when {
                    overlaps -> fg.danger
                    isProblem(b) && pending -> fg.warning
                    else -> null
                },
                onClick = { nav.push(Route.Block(b.id)) },
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    KindBadge(b.plan.kind, muted = !pending)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            b.plan.displayTitle, style = FgType.cardTitle, color = fg.text, maxLines = 3, overflow = TextOverflow.Ellipsis,
                            textDecoration = if (b.plan.isSkipped) TextDecoration.LineThrough else null,
                        )
                        Text(humanDuration(b.e.durationMinutes), style = FgType.meta, color = fg.textSecondary)
                    }
                }
                keyFact(b, local)?.let { Text(it, style = FgType.body, color = if (b.plan.flags.incomplete != null) fg.warning else fg.text, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                BlockChips(b, overlaps)
                if (quick != null && pending) {
                    TextButton(onClick = quick.run, modifier = Modifier.heightIn(min = FgDims.touch)) {
                        Icon(quick.icon, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(quick.label)
                    }
                }
            }
        }
    }
}

@Composable
fun GapLabel(minutes: Int) {
    Row(Modifier.fillMaxWidth().padding(start = (FgDims.margin + RAIL.dp + 12.dp), end = FgDims.margin, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(2.dp).height(18.dp).background(fg.cardBorder))
        Spacer(Modifier.width(10.dp))
        Text("Free · ${humanDuration(minutes)}", style = FgType.meta, color = fg.textSecondary)
    }
}

@Composable
fun NowLine(minute: Int) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = FgDims.margin, vertical = 4.dp).semantics(mergeDescendants = true) { contentDescription = "Now, ${minute.hhmm()}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Now", style = FgType.chip, color = fg.primary, modifier = Modifier.width(RAIL.dp - 10.dp))
        Box(Modifier.size(10.dp).clip(CircleShape).background(fg.primary))
        Box(Modifier.weight(1f).height(2.dp).background(fg.primary))
        Spacer(Modifier.width(6.dp))
        Text(minute.hhmm(), style = FgType.chip, color = fg.primary)
    }
}

/** The day's rail with gaps and, on today, the now line. */
fun LazyListScope.timeline(vm: AppViewModel, trip: TripUi, day: DayUi, nowMinute: Int?) {
    val blocks = day.blocks
    val gapAfter = gaps(day.plans).associateBy { it.afterId }
    var nowPlaced = nowMinute == null
    blocks.forEachIndexed { i, b ->
        val start = b.e.startMinute ?: 0
        if (!nowPlaced && start > nowMinute!!) {
            nowPlaced = true
            item(key = "now") { NowLine(nowMinute) }
        }
        val past = nowMinute != null && start + b.e.durationMinutes <= nowMinute
        item(key = b.id) { TimelineCard(vm, trip, b, past) }
        val gap = gapAfter[b.id]
        if (gap != null && i < blocks.lastIndex) item(key = "gap-${b.id}") { GapLabel(gap.minutes) }
        else if (i < blocks.lastIndex) item(key = "sp-${b.id}") { Spacer(Modifier.height(FgDims.gap)) }
    }
    if (!nowPlaced && blocks.isNotEmpty()) item(key = "now") { NowLine(nowMinute!!) }
}
