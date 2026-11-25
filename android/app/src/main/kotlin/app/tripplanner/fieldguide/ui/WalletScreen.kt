package app.tripplanner.fieldguide.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.tripplanner.fieldguide.data.TRIP_DOCS
import app.tripplanner.fieldguide.logic.BlockKind
import app.tripplanner.fieldguide.logic.amount
import app.tripplanner.fieldguide.logic.KindFamily
import app.tripplanner.fieldguide.logic.dayLabel
import app.tripplanner.fieldguide.logic.family
import app.tripplanner.fieldguide.logic.hhmm
import app.tripplanner.fieldguide.logic.label
import app.tripplanner.fieldguide.logic.todayIso
import app.tripplanner.fieldguide.logic.zoneOrDefault

private enum class WalletFilter(val label: String) { ALL("All"), FLIGHTS("Flights"), TRAINS("Trains"), STAYS("Stays"), TOURS("Tours"), DOCS("Documents") }

/** Things to show at a counter: bookings with a reference or a file. Check-outs repeat their check-in, so they are left out. */
fun walletPasses(trip: TripUi): List<BlockUi> = trip.scheduled.filter { b ->
    b.plan.kind != BlockKind.CHECK_OUT && (!b.e.bookingRef.isNullOrBlank() || b.files.isNotEmpty())
}

// ---------- S13 Wallet ----------

@Composable
fun WalletTab(vm: AppViewModel, trip: TripUi) {
    val nav = LocalNav.current
    var filter by rememberSaveable { mutableStateOf(WalletFilter.ALL) }
    var removing by remember { mutableStateOf<FileUi?>(null) }
    // Saveable: the picker can outlive this screen if the system reclaims the app while it is open.
    var replacing by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.linkDocument(TRIP_DOCS, uri, replacing)
        replacing = null
    }
    removing?.let { doc ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove from this phone?") },
            text = { Text("${doc.fileName} will be deleted from the app. The original in your phone's files is not touched.") },
            confirmButton = { TextButton(onClick = { vm.removeDocument(doc.id); removing = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
    val passes = walletPasses(trip)
    val files = trip.files
    val ready = files.count { it.ready }
    val next = passes.firstOrNull { b ->
        val d = b.day ?: return@firstOrNull false
        val today = todayIso(zoneOrDefault(d.timezone))
        b.plan.isPending && (d.dateIso > today || (d.dateIso == today && (b.e.startMinute ?: 0) + b.e.durationMinutes > trip.dayOf(d.id)!!.nowMinute()))
    }
    val shown = passes.filter {
        when (filter) {
            WalletFilter.ALL -> true
            WalletFilter.FLIGHTS -> it.plan.kind == BlockKind.FLIGHT
            WalletFilter.TRAINS -> it.plan.kind == BlockKind.TRAIN || it.plan.kind == BlockKind.BUS
            WalletFilter.STAYS -> it.plan.kind == BlockKind.CHECK_IN
            WalletFilter.TOURS -> it.plan.kind.family == KindFamily.ACTIVITY || it.plan.kind == BlockKind.FOOD
            WalletFilter.DOCS -> false
        }
    }
    val unscheduledDocs = trip.unscheduled.filter { it.files.isNotEmpty() }
    val safety = safetyList(trip)
    val insurance = safety?.sections?.firstOrNull { it.heading?.contains("insurance", true) == true }

    LazyColumn(contentPadding = PaddingValues(FgDims.margin), verticalArrangement = Arrangement.spacedBy(FgDims.gap)) {
        item {
            if (files.isEmpty()) Callout(Tone.NEUTRAL, "No files attached yet", listOf("Tap \"Add document\" below, or open a plan and link its ticket. Files stay on this phone."))
            else if (ready == files.size) Callout(Tone.SUCCESS, "All ${files.size} files on this phone", icon = Icons.Rounded.CheckCircle)
            else Callout(Tone.WARNING, "$ready of ${files.size} files on this phone", listOf("Connect and pull down on Today to download the rest."), icon = Icons.Rounded.CloudDownload)
        }
        next?.let { b ->
            item { SectionTitle("Next up") }
            item { PassCard(b, highlight = true) { nav.modal = Modal.Counter(b.id) } }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(WalletFilter.entries.toList()) { f -> FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) }) }
            }
        }
        if (filter != WalletFilter.DOCS) {
            if (shown.isEmpty()) item { EmptyState("Nothing here", "No bookings of this kind.") }
            items(shown, key = { it.id }) { b -> PassCard(b) { nav.modal = Modal.Counter(b.id) } }
        }
        if (filter == WalletFilter.ALL || filter == WalletFilter.DOCS) {
            item { SectionTitle("Documents") }
            item {
                FgCard {
                    Text("Your documents", style = FgType.cardTitle, color = fg.text)
                    if (trip.walletDocs.isEmpty()) {
                        Text("Passport, visa, insurance or any other PDF or photo for this trip. Copied into the app, opens offline, never uploaded.", style = FgType.meta, color = fg.textSecondary)
                    }
                    trip.walletDocs.forEachIndexed { i, doc ->
                        WalletDocRow(
                            doc = doc,
                            onOpen = { nav.modal = Modal.Ticket(trip.walletDocs, ticketTitles(trip.walletDocs), i) },
                            onReplace = { replacing = doc.id; picker.launch(WALLET_DOC_TYPES) },
                            onRemove = { removing = doc },
                        )
                    }
                    OutlinedButton(onClick = { replacing = null; picker.launch(WALLET_DOC_TYPES) }, modifier = Modifier.fillMaxWidth().heightIn(min = FgDims.touch)) {
                        Icon(Icons.Rounded.AttachFile, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add document")
                    }
                }
            }
            if (insurance != null) {
                item {
                    FgCard(onClick = { nav.push(Route.Safety) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            KindBadgeIcon()
                            Spacer(Modifier.width(10.dp))
                            Text("Insurance", style = FgType.cardTitle, color = fg.text)
                        }
                        insurance.lines.forEach { Text(it.text, style = FgType.body, color = fg.text) }
                    }
                }
            }
            items(unscheduledDocs, key = { "doc-${it.id}" }) { b ->
                FgCard(onClick = { openTicket(nav, b) }) {
                    Text(b.plan.displayTitle, style = FgType.cardTitle, color = fg.text)
                    Text("${b.files.size} ${if (b.files.size == 1) "file" else "files"}", style = FgType.meta, color = fg.textSecondary)
                }
            }
            if (trip.orphanDocs.isNotEmpty()) {
                item {
                    FgCard {
                        Text("From removed plans", style = FgType.cardTitle, color = fg.text)
                        Text("You linked these on this phone. Their plan was removed in the planner.", style = FgType.meta, color = fg.textSecondary)
                        trip.orphanDocs.forEachIndexed { i, doc ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(doc.fileName, style = FgType.body, color = fg.text, maxLines = 2, modifier = Modifier.weight(1f))
                                TextButton(onClick = { nav.modal = Modal.Ticket(trip.orphanDocs, ticketTitles(trip.orphanDocs), i) }) { Text("Open") }
                                TextButton(onClick = { removing = doc }) { Text("Remove") }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val WALLET_DOC_TYPES = arrayOf("application/pdf", "image/*")

@Composable
private fun WalletDocRow(doc: FileUi, onOpen: () -> Unit, onReplace: () -> Unit, onRemove: () -> Unit) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min = FgDims.touch), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (doc.mimeType == "application/pdf") Icons.Rounded.Description else Icons.Rounded.Image, null, tint = fg.textSecondary)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(doc.fileName, style = FgType.body, color = fg.text, maxLines = 2)
            Text("${amount(doc.sizeBytes / 1024.0)} KB · only on this phone", style = FgType.meta, color = fg.textSecondary)
        }
        TextButton(onClick = onOpen) { Text("Open") }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More for ${doc.fileName}", tint = fg.text) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Open in another app") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) },
                    enabled = doc.ready,
                    onClick = { menu = false; openExternally(context, doc) },
                )
                DropdownMenuItem(text = { Text("Replace") }, leadingIcon = { Icon(Icons.Rounded.SwapHoriz, null) }, onClick = { menu = false; onReplace() })
                DropdownMenuItem(text = { Text("Remove from phone") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { menu = false; onRemove() })
            }
        }
    }
}

@Composable
private fun KindBadgeIcon() {
    androidx.compose.material3.Icon(Icons.Rounded.Shield, null, tint = fg.danger)
}

/** Airline-pass style card: type colour stripe, title, date, the key code large, then the other reference parts. */
@Composable
fun PassCard(b: BlockUi, highlight: Boolean = false, onClick: () -> Unit) {
    val facts = refFacts(b.e.bookingRef)
    val code = facts.firstOrNull { it.first.equals("PNR", true) || it.first.startsWith("Booking", true) || it.first.equals("Ref", true) }
    val rest = facts.filter { it != code }
    val color = kindColor(b.plan.kind)
    val spoken = listOfNotNull(
        b.plan.kind.label, b.plan.displayTitle, b.day?.let { dayLabel(it.dateIso) }, b.e.startMinute?.hhmm(),
        code?.let { "${it.first} ${it.second}" }, rest.joinToString(", ") { "${it.first} ${it.second}" }.ifEmpty { null },
        "${b.files.size} files", "Double tap to open the booking card",
    ).joinToString(". ")
    FgCard(onClick = onClick, border = if (highlight) color else null, modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = spoken }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KindBadge(b.plan.kind)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(b.plan.displayTitle, style = FgType.cardTitle, color = fg.text)
                Text(listOfNotNull(b.day?.let { dayLabel(it.dateIso) }, b.e.startMinute?.hhmm()).joinToString(" · "), style = FgType.meta, color = fg.textSecondary)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(fg.cardBorder))
        if (code != null) {
            Text(code.first, style = FgType.chip, color = fg.textSecondary)
            Text(code.second, style = FgType.code.copy(fontFamily = FontFamily.Monospace), color = fg.text)
        }
        rest.forEach { (label, value) ->
            Row {
                Text(label, style = FgType.meta, color = fg.textSecondary, modifier = Modifier.width(96.dp))
                Text(value, style = FgType.body, color = if (b.plan.flags.incomplete != null && value.contains("not", true)) fg.warning else fg.text)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 2.dp)) {
            if (b.files.isNotEmpty()) {
                val ready = b.files.count { it.ready }
                StatusChip("$ready of ${b.files.size} files on phone", if (ready == b.files.size) Tone.SUCCESS else Tone.WARNING)
            }
            if (b.plan.flags.incomplete != null) StatusChip("Incomplete", Tone.WARNING)
            if (b.plan.flags.cashOnly) StatusChip("Cash only", Tone.WARNING)
        }
    }
}
