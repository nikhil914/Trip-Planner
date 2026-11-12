package app.tripplanner.fieldguide.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tripplanner.fieldguide.data.AttachmentEntity
import app.tripplanner.fieldguide.data.BlockWithContext
import app.tripplanner.fieldguide.data.Category
import app.tripplanner.fieldguide.data.ContextualItemEntity
import app.tripplanner.fieldguide.data.Status
import app.tripplanner.fieldguide.data.TimelineBlockEntity
import app.tripplanner.fieldguide.data.TripDayEntity
import app.tripplanner.fieldguide.data.TripEntity
import app.tripplanner.fieldguide.logic.hhmm
import app.tripplanner.fieldguide.nav.openInGoogleMaps
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(vm: AppViewModel, onOpenSettings: () -> Unit) {
    val state by vm.today.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val pendingShift by vm.pendingShift.collectAsStateWithLifecycle()
    val lastShift by vm.lastShift.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var flashcard by remember { mutableStateOf<Flashcard?>(null) }
    var ticket by remember { mutableStateOf<AttachmentEntity?>(null) }

    val trip = state.trip
    val day = state.day

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "top") {
                TopBar(trip?.title.orEmpty(), state.days.size, day, onStep = vm::stepDay, onOpenSettings = onOpenSettings)
            }
            if (trip == null || day == null) {
                item(key = "empty") {
                    Text(if (state.loaded) "This trip has no days yet. Pull down to refresh." else "Loading…", color = Dim)
                }
            } else {
                item(key = "briefing") {
                    BriefingHeader(trip, day, state.isToday, state.firstDeparture, state.spendLocal, state.prep, vm::checkItem)
                }
                if (state.isToday) {
                    item(key = "delay") { DelayBar(lastShift, onShift = vm::requestShift, onUndo = vm::undoShift) }
                }
                items(state.agenda, key = { it.block.id }) { row ->
                    AgendaCard(
                        row = row,
                        collides = row.block.id in state.colliding,
                        onNavigate = { b -> openInGoogleMaps(context, b.lat!!, b.lng!!, b.googlePlaceId, b.title) },
                        onFlashcard = { flashcard = it },
                        onCheck = vm::checkItem,
                        onOpenTicket = { ticket = it },
                        onStatus = vm::markBlock,
                    )
                }
                if (state.agenda.isEmpty()) {
                    item(key = "none") { Text("Nothing is scheduled on this day.", color = Dim) }
                }
            }
        }
    }

    flashcard?.let { FlashcardDialog(it) { flashcard = null } }
    ticket?.let { TicketDialog(it) { ticket = null } }

    pendingShift?.let { pending ->
        AlertDialog(
            onDismissRequest = vm::cancelShift,
            title = { Text("Shift by ${pending.delta} min anyway?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    pending.preview.collisions.forEach {
                        Text("${it.blockTitle} would overlap ${it.fixedTitle} (fixed, ${it.fixedStart.hhmm()}) by ${it.overlapMinutes} min.")
                    }
                    pending.preview.lateEnds.forEach {
                        val closing = it.closesMinute
                        Text(
                            if (closing != null && it.endMinute > closing) "${it.title} would end at ${it.endMinute.hhmm()}, after closing at ${closing.hhmm()}."
                            else "${it.title} would run past midnight.",
                        )
                    }
                    Text("Fixed bookings do not move.", color = Dim)
                }
            },
            confirmButton = { TextButton(onClick = vm::confirmShift) { Text("Shift") } },
            dismissButton = { TextButton(onClick = vm::cancelShift) { Text("Cancel") } },
        )
    }
}

@Composable
private fun TopBar(title: String, dayCount: Int, day: TripDayEntity?, onStep: (Int) -> Unit, onOpenSettings: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = onOpenSettings) { Text("Settings") }
        }
        if (day != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onStep(-1) }, enabled = day.dayNumber > 1) { Text("‹ Prev") }
                Text("Day ${day.dayNumber} of $dayCount", Modifier.weight(1f), color = Dim)
                TextButton(onClick = { onStep(1) }, enabled = day.dayNumber < dayCount) { Text("Next ›") }
            }
        }
    }
}

/** The pinned morning card: when to leave, what to carry, what the day should cost. */
@Composable
fun BriefingHeader(
    trip: TripEntity,
    day: TripDayEntity,
    isToday: Boolean,
    firstDeparture: Int?,
    spendLocal: Double,
    prep: List<ContextualItemEntity>,
    onCheck: (id: String, checked: Boolean) -> Unit,
) {
    Panel {
        Text("${if (isToday) "Today" else "Day ${day.dayNumber}"} · ${day.dateIso}", color = Dim)
        Text(
            text = firstDeparture?.let { "Leave by ${it.hhmm()}" } ?: "Nothing left to do",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
        day.weatherSummaryCache?.let { Text(it, color = Dim) }
        if (spendLocal > 0) {
            Text(
                String.format(
                    Locale.ROOT, "About %d %s (%d %s)",
                    spendLocal.roundToInt(), trip.localCurrency, (spendLocal * trip.exchangeRate).roundToInt(), trip.homeCurrency,
                ),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        prep.forEach { item ->
            Row(
                Modifier.fillMaxWidth().clickable { onCheck(item.id, !item.isChecked) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = item.isChecked, onCheckedChange = { onCheck(item.id, it) })
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
                )
            }
        }
    }
}

@Composable
fun DelayBar(lastShift: Int?, onShift: (minutes: Int) -> Unit, onUndo: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Running late?", Modifier.weight(1f), color = Dim)
        if (lastShift != null) TextButton(onClick = onUndo) { Text("Undo") }
        OutlinedButton(onClick = { onShift(30) }) { Text("+30m") }
        OutlinedButton(onClick = { onShift(60) }) { Text("+1h") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AgendaCard(
    row: BlockWithContext,
    collides: Boolean,
    onNavigate: (TimelineBlockEntity) -> Unit,
    onFlashcard: (Flashcard) -> Unit,
    onCheck: (id: String, checked: Boolean) -> Unit,
    onOpenTicket: (AttachmentEntity) -> Unit,
    onStatus: (id: String, status: String) -> Unit,
) {
    val block = row.block
    val pending = block.status == Status.PENDING

    Panel(Modifier.alpha(if (pending) 1f else 0.45f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                block.startMinute?.hhmm().orEmpty(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (collides) MaterialTheme.colorScheme.error else Amber,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(block.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val tags = listOfNotNull(
                    "${block.durationMinutes} min",
                    "FIXED".takeIf { block.isFixedTime },
                    block.bookingRef?.let { "Ref $it" },
                    "Overlaps".takeIf { collides },
                    block.status.takeIf { !pending },
                )
                Text(tags.joinToString(" · "), color = if (collides) MaterialTheme.colorScheme.error else Dim)
            }
        }
        block.subtitleOrTip?.let { Text(it, color = Dim) }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (block.lat != null && block.lng != null) {
                Button(onClick = { onNavigate(block) }) { Text("Navigate") }
            }
            block.localScriptName?.let { local ->
                OutlinedButton(onClick = { onFlashcard(Flashcard(local, block.phoneticPronunciation, block.title)) }) {
                    Text("Show to local")
                }
            }
            row.attachments.forEach { attachment ->
                OutlinedButton(onClick = { onOpenTicket(attachment) }) { Text("Ticket") }
            }
            if (pending) {
                TextButton(onClick = { onStatus(block.id, Status.DONE) }) { Text("Done") }
                TextButton(onClick = { onStatus(block.id, Status.SKIPPED) }) { Text("Skip") }
            } else {
                TextButton(onClick = { onStatus(block.id, Status.PENDING) }) { Text("Reopen") }
            }
        }

        // Nearby errands and food were linked on the server at save time. No sensor is read here.
        row.items.sortedBy { it.distanceFromBlockMeters ?: Int.MAX_VALUE }.forEach { item ->
            HorizontalDivider(color = Outline)
            LinkedItemRow(item, onFlashcard, onCheck)
        }
    }
}

@Composable
private fun LinkedItemRow(item: ContextualItemEntity, onFlashcard: (Flashcard) -> Unit, onCheck: (String, Boolean) -> Unit) {
    val local = item.localScriptText
    Row(
        Modifier.fillMaxWidth().clickable(enabled = local != null) {
            if (local != null) onFlashcard(Flashcard(local, item.phoneticPronunciation, item.title))
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = item.isChecked, onCheckedChange = { onCheck(item.id, it) })
        Column(Modifier.weight(1f)) {
            Text(
                (if (item.category == Category.TO_BUY) "Buy: " else "Eat: ") + item.title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
            )
            val where = listOfNotNull(item.shopOrVenueName, item.distanceFromBlockMeters?.let { "$it m straight line" }).joinToString(" · ")
            if (where.isNotEmpty()) Text(where, color = Dim, style = MaterialTheme.typography.bodySmall)
            item.priceBenchmarkNote?.let { Text(it, color = Amber, style = MaterialTheme.typography.bodySmall) }
        }
        if (local != null) Box(Modifier.padding(start = 8.dp)) { Text(local, color = Dim, maxLines = 1) }
    }
}
