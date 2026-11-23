package app.tripplanner.fieldguide.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.tripplanner.fieldguide.data.Category
import app.tripplanner.fieldguide.data.Status
import app.tripplanner.fieldguide.logic.BlockKind
import app.tripplanner.fieldguide.logic.amount
import app.tripplanner.fieldguide.logic.calmCaps
import app.tripplanner.fieldguide.logic.dayLabel
import app.tripplanner.fieldguide.logic.hhmm
import app.tripplanner.fieldguide.logic.humanDuration
import app.tripplanner.fieldguide.logic.label
import app.tripplanner.fieldguide.logic.money
import app.tripplanner.fieldguide.logic.zoneCity

private val refSplit = Regex("""\s*·\s*""")

/** Booking reference segments as label/value facts: "PNR [PNR]" -> ("PNR", "[PNR]"). */
fun refFacts(ref: String?): List<Pair<String, String>> {
    if (ref.isNullOrBlank()) return emptyList()
    return ref.split(refSplit).filter { it.isNotBlank() }.map { seg ->
        val s = calmCaps(seg.trim())
        val code = Regex("""^(PNR|Booking\.com|Booking|Ref|Confirmation|Voucher)\s+(.+)$""", RegexOption.IGNORE_CASE).find(s)
        when {
            code != null -> code.groupValues[1] to code.groupValues[2]
            s.startsWith("Seat", ignoreCase = true) -> "Seats" to s.substringAfter(' ')
            s.startsWith("Carriage", ignoreCase = true) -> "Carriage" to s.substringAfter(' ')
            s.contains(':') -> s.substringBefore(':').trim() to s.substringAfter(':').trim()
            else -> "Booking" to s
        }
    }
}

@Composable
fun BlockDetailScreen(vm: AppViewModel, trip: TripUi, id: String) {
    val b = trip.byId[id] ?: run { EmptyState("Not on this phone", "This plan was removed in the planner."); return }
    val nav = LocalNav.current
    val context = LocalContext.current
    val t = trip.trip
    val p = b.plan
    val n = p.parsed
    val f = p.flags
    var menu by remember { mutableStateOf(false) }
    val overlaps = id in trip.colliding
    // Saveable: the picker can outlive this screen if the system reclaims the app while it is open.
    var replacing by rememberSaveable { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf<FileUi?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.linkDocument(id, uri, replacing)
        replacing = null
    }

    removing?.let { file ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove from this phone?") },
            text = { Text("${file.fileName} will be deleted from the app. The original in your phone's files is not touched.") },
            confirmButton = { TextButton(onClick = { vm.removeDocument(file.id); removing = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }

    LazyColumn(contentPadding = PaddingValues(FgDims.margin), verticalArrangement = Arrangement.spacedBy(FgDims.gap)) {
        // 1. Header
        item {
            Row(verticalAlignment = Alignment.Top) {
                KindBadge(p.kind, size = 48)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(p.kind.label, style = FgType.chip, color = kindColor(p.kind))
                    Text(p.displayTitle, style = FgType.hero.copy(fontSize = FgType.hero.fontSize * 0.8f), color = fg.text)
                    val when_ = listOfNotNull(
                        b.day?.let { dayLabel(it.dateIso) },
                        b.e.startMinute?.let { "${it.hhmm()} – ${(it + b.e.durationMinutes).hhmm()}" },
                        humanDuration(b.e.durationMinutes),
                    ).joinToString(" · ")
                    Text(when_, style = FgType.body, color = fg.textSecondary)
                    b.e.endTimezone?.let { Text("Arrives on ${zoneCity(it)} time", style = FgType.meta, color = fg.textSecondary) }
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More actions", tint = fg.text) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        b.e.bookingRef?.let { ref ->
                            DropdownMenuItem(text = { Text("Copy booking details") }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) }, onClick = { menu = false; copyText(context, "Booking", ref) })
                        }
                        if (b.e.status == Status.PENDING) {
                            DropdownMenuItem(text = { Text("Mark done") }, onClick = { menu = false; vm.markBlock(id, Status.DONE, true) })
                            DropdownMenuItem(text = { Text("Skip") }, onClick = { menu = false; vm.markBlock(id, Status.SKIPPED, true) })
                        } else {
                            DropdownMenuItem(text = { Text("Reopen") }, onClick = { menu = false; vm.markBlock(id, Status.PENDING, true) })
                        }
                        DropdownMenuItem(text = { Text("Link ticket or document") }, leadingIcon = { Icon(Icons.Rounded.AttachFile, null) }, onClick = { menu = false; replacing = null; picker.launch(DOC_TYPES) })
                        DropdownMenuItem(text = { Text("Copy notes") }, onClick = { menu = false; copyText(context, p.displayTitle, "${p.displayTitle}\n${b.e.bookingRef.orEmpty()}\n${b.e.subtitleOrTip.orEmpty()}".trim()) })
                    }
                }
            }
        }
        item { BlockChips(b, overlaps, compact = false) }

        // 2. Status banner
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (f.notBooked) Callout(Tone.WARNING, "Not booked", n.booking.ifEmpty { listOf("Book it, then add the booking in the web planner.") })
                f.incomplete?.let { Callout(Tone.WARNING, "Incomplete", listOf(it, "Edit on the web planner.")) }
                if (overlaps) Callout(Tone.DANGER, "Overlaps a fixed booking", listOf("Running late moved this into a fixed booking. Skip it or shift again."))
                if (b.e.status != Status.PENDING) {
                    Callout(if (b.e.status == Status.DONE) Tone.SUCCESS else Tone.NEUTRAL, if (b.e.status == Status.DONE) "Done" else "Skipped") {
                        TextButton(onClick = { vm.markBlock(id, Status.PENDING, true) }) { Text("Reopen") }
                    }
                }
                if (f.choices.isNotEmpty()) {
                    Callout(Tone.PRIMARY, "Pick one", f.choices.map { "• $it" })
                }
            }
        }

        // 3. Primary actions, at most three
        item {
            val actions = buildList<QuickAction> {
                if (b.files.isNotEmpty()) add(QuickAction(if (p.kind == BlockKind.FLIGHT) "Boarding pass" else "Show ticket", Icons.Rounded.ConfirmationNumber) { openTicket(nav, b) })
                if (b.hasPin) add(QuickAction("Navigate", Icons.Rounded.Directions) { navigateTo(context, b) })
                add(QuickAction("Show to local", Icons.Rounded.Translate) { nav.modal = Modal.ShowLocal(localCards(b)) })
                if ((p.kind == BlockKind.CHECK_IN || p.kind == BlockKind.TRAIN || p.kind == BlockKind.FLIGHT) && !b.e.bookingRef.isNullOrBlank()) {
                    add(QuickAction("Booking card", Icons.Rounded.ConfirmationNumber) { nav.modal = Modal.Counter(id) })
                }
            }.take(3)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                actions.forEachIndexed { i, a -> BigAction(a.label, a.icon, Modifier.fillMaxWidth(), primary = i == 0, onClick = a.run) }
            }
        }

        // 4. Key facts
        item {
            val facts = buildList<Pair<String, String>> {
                addAll(refFacts(b.e.bookingRef))
                if (p.kind == BlockKind.FLIGHT || p.kind == BlockKind.TRAIN) {
                    val start = b.e.startMinute
                    f.beThereBy?.let { add("Be there by" to it.hhmm()) } ?: start?.let {
                        add("Be there by" to (it - if (p.kind == BlockKind.FLIGHT) 150 else 25).hhmm())
                    }
                }
                if (t != null) b.e.estimatedCostLocal?.takeIf { it > 0 }?.let { add("Cost" to money(it, t.exchangeRate, t.localCurrency, t.homeCurrency)) }
                if (f.cashOnly) add("Payment" to "Cash only")
                f.freeCancelUntil?.let { add("Free cancellation" to "Until $it") }
                if (f.nonRefundable) add("Cancellation" to "Non-refundable")
                b.e.opensMinute?.let { o -> add("Hours" to "${o.hhmm()} – ${b.e.closesMinute?.hhmm() ?: "?"}") }
                hotelAddress(b)?.let { add("Address" to it) }
            }
            if (facts.isNotEmpty()) FgCard { KeyFacts(facts) }
        }
        item { BlockSpendCard(vm, b) }

        // 5. Warnings
        if (n.warnings.isNotEmpty()) item { Callout(Tone.WARNING, "Warnings", n.warnings, icon = Icons.Rounded.Shield) }

        // 6. Steps
        if (n.steps.isNotEmpty()) item {
            FgCard {
                SectionTitle("Steps")
                n.steps.forEach { s ->
                    Row {
                        Text(s.time, style = FgType.body.copy(fontWeight = FgType.railTime.fontWeight), color = fg.text, modifier = Modifier.width(92.dp))
                        Text(s.text, style = FgType.body, color = fg.text)
                    }
                }
            }
        }

        // 7. Money and terms
        if (n.money.isNotEmpty() || n.terms.isNotEmpty()) item {
            FgCard {
                SectionTitle("Money and booking terms")
                (n.money + n.terms).forEach { Bullet(it) }
            }
        }

        // 8. Bring
        if (n.bring.isNotEmpty()) item {
            FgCard {
                SectionTitle("Bring")
                ChipRow { n.bring.forEach { StatusChip(it) } }
            }
        }

        // 9. Ask at the desk
        if (n.ask.isNotEmpty()) item {
            FgCard {
                SectionTitle("Ask at the desk")
                n.ask.forEach { Bullet(it) }
            }
        }

        // 10. Other notes
        if (n.info.isNotEmpty()) item {
            FgCard {
                SectionTitle("Good to know")
                n.info.forEach { Bullet(it) }
            }
        }

        // 11. Plan B, collapsed and labelled by trigger
        if (n.planB.isNotEmpty()) item {
            FgCard {
                SectionTitle("Plan B")
                n.planB.forEach { pb -> Expandable(pb.trigger, tone = Tone.PRIMARY) { Text(pb.text, style = FgType.body, color = fg.text) } }
            }
        }

        // 12. Linked items
        if (b.items.isNotEmpty()) item {
            FgCard {
                SectionTitle("Nearby")
                b.items.sortedBy { it.distanceFromBlockMeters ?: Int.MAX_VALUE }.forEach { item ->
                    Row(Modifier.fillMaxWidth().heightIn(min = FgDims.touch), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = item.isChecked, onCheckedChange = { vm.checkItem(item.id, it) })
                        Column(Modifier.weight(1f)) {
                            Text(
                                (if (item.category == Category.TO_BUY) "Buy: " else if (item.category == Category.EAT) "Eat: " else "") + item.title,
                                style = FgType.body, color = fg.text, textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
                            )
                            val where = listOfNotNull(item.shopOrVenueName, item.distanceFromBlockMeters?.let { "$it m straight line" }).joinToString(" · ")
                            if (where.isNotEmpty()) Text(where, style = FgType.meta, color = fg.textSecondary)
                            item.priceBenchmarkNote?.let { Text(it, style = FgType.meta, color = fg.warning) }
                        }
                        if (item.localScriptText != null) {
                            IconButton(onClick = { nav.modal = Modal.ShowLocal(listOf(LocalCard(item.localScriptText, item.phoneticPronunciation, item.title, item.shopOrVenueName))) }) {
                                Icon(Icons.Rounded.Translate, "Show to local")
                            }
                        }
                    }
                }
            }
        }

        // 13. Tickets and documents: planner files plus originals linked on this phone
        item {
            FgCard {
                SectionTitle("Tickets and documents")
                if (b.files.isEmpty()) {
                    Text("Nothing linked yet. Link the original PDF or photo from this phone, or add one in the planner.", style = FgType.body, color = fg.textSecondary)
                }
                b.files.forEachIndexed { i, file ->
                    FileRow(
                        file = file,
                        onOpen = { openTicket(nav, b, i) },
                        onOpenElsewhere = { openExternally(context, file) },
                        onReplace = { replacing = file.id; picker.launch(DOC_TYPES) },
                        onRemove = { removing = file },
                    )
                }
                OutlinedButton(onClick = { replacing = null; picker.launch(DOC_TYPES) }, modifier = Modifier.fillMaxWidth().heightIn(min = FgDims.touch)) {
                    Icon(Icons.Rounded.AttachFile, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Link original ticket or document")
                }
                Text("Linked files are copied into the app and stay on this phone only. They are not uploaded.", style = FgType.meta, color = fg.textSecondary)
            }
        }

        // 14. Original notes
        if (!b.e.subtitleOrTip.isNullOrBlank()) item {
            FgCard {
                Expandable("Original notes") {
                    SelectionContainer { Text(b.e.subtitleOrTip.orEmpty(), style = FgType.body, color = fg.text) }
                }
            }
        }
        item { Text("To change this plan, edit it on the web planner.", style = FgType.meta, color = fg.textSecondary, modifier = Modifier.padding(bottom = 24.dp)) }
    }
}

/** What the system picker offers when linking a document. The bytes are checked again after copying. */
private val DOC_TYPES = arrayOf("application/pdf", "image/*")

@Composable
private fun FileRow(file: FileUi, onOpen: () -> Unit, onOpenElsewhere: () -> Unit, onReplace: () -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min = FgDims.touch), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (file.mimeType == "application/pdf") Icons.Rounded.Description else Icons.Rounded.Image, null, tint = fg.textSecondary)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(file.fileName, style = FgType.body, color = fg.text, maxLines = 2)
            Text("${amount(file.sizeBytes / 1024.0)} KB", style = FgType.meta, color = fg.textSecondary)
            when {
                file.localOnly -> StatusChip("Only on this phone", Tone.PRIMARY, Icons.Rounded.PhoneAndroid)
                file.ready -> StatusChip("From planner · on phone", Tone.SUCCESS, Icons.Rounded.CheckCircle)
                else -> StatusChip("From planner · not downloaded", Tone.WARNING, Icons.Rounded.CloudDownload)
            }
        }
        TextButton(onClick = onOpen) { Text("Open") }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More for ${file.fileName}", tint = fg.text) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Open in another app") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) },
                    enabled = file.ready,
                    onClick = { menu = false; onOpenElsewhere() },
                )
                if (file.localOnly) {
                    DropdownMenuItem(text = { Text("Replace") }, leadingIcon = { Icon(Icons.Rounded.SwapHoriz, null) }, onClick = { menu = false; onReplace() })
                    DropdownMenuItem(text = { Text("Remove from phone") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { menu = false; onRemove() })
                }
            }
        }
    }
}
