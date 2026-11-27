package app.tripplanner.fieldguide.ui

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tripplanner.fieldguide.data.ExpenseEntity
import app.tripplanner.fieldguide.logic.MoneyKind
import app.tripplanner.fieldguide.logic.Pace
import app.tripplanner.fieldguide.logic.SpendCategory
import app.tripplanner.fieldguide.logic.Variance
import app.tripplanner.fieldguide.logic.dayLabel
import app.tripplanner.fieldguide.logic.formatMinor
import app.tripplanner.fieldguide.logic.keypadInput
import app.tripplanner.fieldguide.logic.moneyLabel
import app.tripplanner.fieldguide.logic.parseAmount
import app.tripplanner.fieldguide.logic.plannedMinor
import app.tripplanner.fieldguide.logic.variance
import app.tripplanner.fieldguide.logic.zoneOrDefault
import java.time.Instant
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.launch

// ---------- Shared bits ----------

/** "48 GEL" with the home value beside it: "65 AED". */
private fun pair(m: MoneyUi, localMinor: Long): Pair<String, String> = moneyLabel(localMinor, m.local) to moneyLabel(m.home(localMinor), m.home)

/** Words for TalkBack: "48 GEL, 65 AED". */
private fun spoken(m: MoneyUi, localMinor: Long): String = pair(m, localMinor).let { "${it.first}, ${it.second}" }

internal fun ExpenseEntity.timeLabel(zoneId: String?): String =
    Instant.ofEpochMilli(occurredAt).atZone(zoneOrDefault(zoneId)).toLocalTime().toString().take(5)

private fun methodLabel(method: String) = when (method) {
    "CASH" -> "Cash"
    "CARD" -> "Card"
    else -> "Other"
}

private fun ExpenseEntity.title(trip: TripUi): String = merchant?.takeIf { it.isNotBlank() }
    ?: blockId?.let { trip.byId[it]?.plan?.displayTitle }
    ?: when (kind) {
        MoneyKind.ATM -> "ATM withdrawal"
        MoneyKind.OPENING_CASH -> "Cash brought"
        MoneyKind.EXCHANGE -> "Currency exchange"
        MoneyKind.CASH_COUNT -> "Cash count"
        else -> SpendCategory.of(category).label
    }

@Composable
private fun paceLine(m: MoneyUi): Triple<String, Tone, androidx.compose.ui.graphics.vector.ImageVector> {
    val s = m.summary
    return when (s.pace) {
        Pace.ON_PACE -> Triple("On pace · ${s.daysLeft} ${if (s.daysLeft == 1) "day" else "days"} left", Tone.SUCCESS, Icons.Rounded.CheckCircle)
        Pace.TIGHT -> Triple("Tight: today's plans use most of it", Tone.WARNING, Icons.Rounded.Info)
        Pace.OVER -> Triple(
            "Over by ${moneyLabel(-(s.freeMinor ?: 0), m.local)}. ${moneyLabel(-(s.safePerDayMinor ?: 0), m.local)} a day less to catch up",
            Tone.DANGER, Icons.Rounded.Error,
        )
        Pace.ENDED -> Triple("Trip over", Tone.NEUTRAL, Icons.Rounded.Info)
        Pace.NO_BUDGET -> Triple("No budget set", Tone.NEUTRAL, Icons.Rounded.Info)
    }
}

/** Stacked bar: pre-paid, spent on the trip, still planned, free. Segments are told apart by shade and gaps, and the
 *  bar has a spoken summary, so colour is never the only cue. */
@Composable
private fun BudgetBar(m: MoneyUi) {
    val s = m.summary
    val budget = s.budgetMinor ?: return
    val total = maxOf(budget, s.spentMinor + s.plannedMinor).toFloat().coerceAtLeast(1f)
    val parts = listOf(
        s.prepaidMinor to fg.primary.copy(alpha = 0.55f),
        s.onTripMinor to fg.primary,
        s.plannedMinor to fg.warning.copy(alpha = 0.7f),
        (s.freeMinor ?: 0).coerceAtLeast(0) to fg.cardBorder,
    )
    val over = s.spentMinor + s.plannedMinor > budget
    val marker = fg.text
    val described = "Budget ${moneyLabel(budget, m.local)}. Pre-paid ${moneyLabel(s.prepaidMinor, m.local)}. " +
        "Spent on the trip ${moneyLabel(s.onTripMinor, m.local)}. Still planned ${moneyLabel(s.plannedMinor, m.local)}. " +
        if (over) "Over budget." else "Free ${moneyLabel(s.freeMinor ?: 0, m.local)}."
    Canvas(Modifier.fillMaxWidth().height(12.dp).semantics { contentDescription = described }) {
        var x = 0f
        val gap = 2.dp.toPx()
        parts.forEach { (v, c) ->
            if (v <= 0) return@forEach
            val w = size.width * (v / total)
            drawRoundRect(c, Offset(x, 0f), Size((w - gap).coerceAtLeast(1f), size.height), CornerRadius(4.dp.toPx()))
            x += w
        }
        if (over) {
            val bx = size.width * (budget / total)
            drawRect(marker, Offset(bx - 1.dp.toPx(), -2.dp.toPx()), Size(2.dp.toPx(), size.height + 4.dp.toPx()))
        }
    }
}

@Composable
private fun BudgetHeader(m: MoneyUi, onSetBudget: () -> Unit) {
    val s = m.summary
    FgCard {
        if (s.budgetMinor == null) {
            SectionTitle(if (m.shared) "Shared spend" else "Your spend")
            val (l, h) = pair(m, s.spentMinor)
            Text(l, style = FgType.hero, color = fg.text)
            Text(h, style = FgType.meta, color = fg.textSecondary)
            OutlinedButton(onClick = onSetBudget, modifier = Modifier.fillMaxWidth().heightIn(min = FgDims.touch)) { Text("Set a budget") }
            return@FgCard
        }
        val safe = s.safePerDayMinor
        if (safe != null && s.pace != Pace.ENDED) {
            SectionTitle("Safe to spend today")
            val (l, h) = pair(m, safe)
            Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Bottom) {
                Text(l, style = FgType.hero, color = fg.text, modifier = Modifier.weight(1f))
                Text(h, style = FgType.railTime, color = fg.textSecondary)
            }
        }
        val (text, tone, icon) = paceLine(m)
        StatusChip(text, tone, icon)
        BudgetBar(m)
        Text(
            "Spent ${moneyLabel(s.spentMinor, m.local)} of ${moneyLabel(s.budgetMinor, m.local)} · ${moneyLabel(m.home(s.spentMinor), m.home)}",
            style = FgType.body, color = fg.text,
        )
        Text(
            "Pre-paid ${formatMinor(s.prepaidMinor)} · On trip ${formatMinor(s.onTripMinor)} · Planned ${formatMinor(s.plannedMinor)}",
            style = FgType.meta, color = fg.textSecondary,
        )
    }
}

@Composable
private fun CashPouchStrip(m: MoneyUi, onAtm: () -> Unit, onCount: () -> Unit) {
    val short = m.cashNeededMinor - m.pouchMinor
    FgCard(border = if (short > 0) fg.warning else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Payments, null, tint = fg.text)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                Text("Cash pouch ${moneyLabel(m.pouchMinor, m.local)}", style = FgType.cardTitle, color = fg.text)
                when {
                    short > 0 -> Text("Short by ${moneyLabel(short, m.local)} for cash-only plans today and tomorrow", style = FgType.meta, color = fg.warning)
                    m.cashNeededMinor > 0 -> Text("Cash-only plans need about ${moneyLabel(m.cashNeededMinor, m.local)}", style = FgType.meta, color = fg.textSecondary)
                    !m.hasCash -> Text("Add the cash you carry and each ATM withdrawal.", style = FgType.meta, color = fg.textSecondary)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onAtm, modifier = Modifier.weight(1f).heightIn(min = FgDims.touch)) { Text("Add cash") }
            OutlinedButton(onClick = onCount, modifier = Modifier.weight(1f).heightIn(min = FgDims.touch)) { Text("Count cash") }
        }
    }
}

private enum class MoneyView(val label: String) { DAY("By day"), PLAN("By plan"), LEDGER("Ledger") }
private enum class MoneyFilter(val label: String) { ALL("All"), TRIP("On trip"), PREPAID("Pre-paid"), CASH("Cash"), CARD("Card") }

private fun ExpenseEntity.matches(f: MoneyFilter) = when (f) {
    MoneyFilter.ALL -> true
    MoneyFilter.TRIP -> !prepaid && dayId != null
    MoneyFilter.PREPAID -> prepaid || dayId == null
    MoneyFilter.CASH -> method == "CASH"
    MoneyFilter.CARD -> method == "CARD"
}

@Composable
private fun VarianceText(planned: Long, actual: Long, local: String) {
    val v = variance(planned, actual)
    val diff = abs(actual - planned)
    val (text, color) = when (v) {
        Variance.OVER -> "↑ ${formatMinor(diff)}" to fg.warning
        Variance.UNDER -> "↓ ${formatMinor(diff)}" to fg.success
        Variance.WITHIN -> "✓" to fg.success
        Variance.NONE -> "" to fg.textSecondary
    }
    val spokenText = when (v) {
        Variance.OVER -> "${moneyLabel(diff, local)} over plan"
        Variance.UNDER -> "${moneyLabel(diff, local)} under plan"
        Variance.WITHIN -> "on plan"
        Variance.NONE -> ""
    }
    Text(text, style = FgType.meta, color = color, textAlign = TextAlign.End,
        modifier = Modifier.width(64.dp).semantics { contentDescription = spokenText })
}

/** One ledger line: where and when on the left, amounts on the right. Swipe left to delete, with undo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LedgerRow(vm: AppViewModel, trip: TripUi, m: MoneyUi, row: ExpenseEntity) {
    val haptics = LocalHapticFeedback.current
    val zone = trip.dayOf(row.dayId)?.day?.timezone ?: trip.days.firstOrNull()?.day?.timezone
    val title = row.title(trip)
    val plan = row.blockId?.let { trip.byId[it]?.plan?.displayTitle }
    val category = SpendCategory.of(row.category).label
    val home = row.billedHomeMinor ?: row.homeMinor
    val sign = if (row.kind == MoneyKind.SPEND) "" else "+"
    val unsent = row.id in m.unsent
    val words = buildString {
        append("$title, ${row.amountMinor.let { moneyLabel(it, row.currency) }}, ${moneyLabel(home, m.home)}, ${methodLabel(row.method)}, $category, ${row.timeLabel(zone)}")
        plan?.let { append(", $it") }
        if (row.prepaid) append(", pre-paid")
        if (!row.mine) append(", logged on the other phone")
        if (unsent) append(", not uploaded yet")
    }
    val content: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().background(fg.card).heightIn(min = FgDims.touch)
                .clickable(enabled = row.mine) { vm.openQuickAdd(QuickAddRequest(editId = row.id)) }
                .padding(vertical = 8.dp)
                .clearAndSetSemantics {
                    contentDescription = words
                    if (row.mine) customActions = listOf(
                        CustomAccessibilityAction("Edit") { vm.openQuickAdd(QuickAddRequest(editId = row.id)); true },
                        CustomAccessibilityAction("Delete") { vm.deleteExpense(row.id); true },
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(row.timeLabel(zone), style = FgType.meta, color = fg.textSecondary, modifier = Modifier.width(48.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = FgType.body, color = fg.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(category.takeIf { row.kind == MoneyKind.SPEND }, methodLabel(row.method), plan?.takeIf { it != title }, "Pre-paid".takeIf { row.prepaid }, if (!row.mine) (row.paidBy ?: "Other phone") else null)
                        .joinToString(" · "),
                    style = FgType.meta, color = fg.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            if (unsent) Icon(Icons.Rounded.CloudOff, null, Modifier.size(14.dp), tint = fg.textSecondary)
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
                Text("$sign${moneyLabel(row.amountMinor, row.currency)}", style = FgType.body, color = fg.text)
                Text(moneyLabel(home, m.home), style = FgType.meta, color = fg.textSecondary)
            }
        }
    }
    if (!row.mine) return content()
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                vm.deleteExpense(row.id)
            }
            false
        },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(Modifier.fillMaxSize().background(fg.dangerBg).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterEnd) {
                Text("Delete", style = FgType.chip, color = fg.danger)
            }
        },
    ) { content() }
}

// ---------- S11 Money hub ----------

@Composable
fun MoneyScreen(vm: AppViewModel, trip: TripUi) {
    val t = trip.trip ?: return
    val m by vm.money.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var view by rememberSaveable { mutableStateOf(MoneyView.DAY) }
    var filter by rememberSaveable { mutableStateOf(MoneyFilter.ALL) }
    var budgetDialog by remember { mutableStateOf(false) }
    var countDialog by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = FgDims.margin, end = FgDims.margin, top = FgDims.margin, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(FgDims.gap),
        ) {
            item { BudgetHeader(m) { budgetDialog = true } }
            item {
                CashPouchStrip(
                    m,
                    onAtm = { vm.openQuickAdd(QuickAddRequest(kind = if (m.hasCash) MoneyKind.ATM else MoneyKind.OPENING_CASH, suggestPlan = false)) },
                    onCount = { countDialog = true },
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(MoneyView.entries.toList()) { v ->
                        FilterChip(selected = view == v, onClick = { view = v }, label = { Text(v.label) }, modifier = Modifier.heightIn(min = FgDims.touch))
                    }
                }
            }
            when (view) {
                MoneyView.DAY -> {
                    val today = trip.today()?.day?.id
                    items(m.days.reversed()) { d ->
                        val day = trip.dayOf(d.dayId)?.day ?: return@items
                        val label = dayLabel(day.dateIso) + if (d.dayId == today) " (today)" else ""
                        FgCard(onClick = { nav.push(Route.MoneyDay(d.dayId)) }) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {}) {
                                Text(label, style = FgType.body, color = fg.text, modifier = Modifier.weight(1f))
                                Text("${formatMinor(d.actualMinor)} / ${formatMinor(d.plannedMinor)}", style = FgType.body, color = fg.text)
                                VarianceText(d.plannedMinor, d.actualMinor, m.local)
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = fg.textSecondary)
                            }
                            if (d.actualMinor > 0) Text("Cash ${formatMinor(d.cashMinor)} · Card ${formatMinor(d.cardMinor)} ${m.local}", style = FgType.meta, color = fg.textSecondary)
                        }
                    }
                    val before = m.rows.filter { it.kind == MoneyKind.SPEND && (it.prepaid || it.dayId == null) }
                    if (before.isNotEmpty()) item {
                        FgCard(onClick = { nav.push(Route.MoneyDay(null)) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Pre-paid", style = FgType.body, color = fg.text, modifier = Modifier.weight(1f))
                                Text(moneyLabel(m.summary.prepaidMinor, m.local), style = FgType.body, color = fg.text)
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = fg.textSecondary)
                            }
                        }
                    }
                    if (m.rows.none { it.kind == MoneyKind.SPEND }) item {
                        EmptyState("Nothing logged yet", "Tap Log spend after you pay. Planned costs from the planner show here for comparison.")
                    }
                }
                MoneyView.PLAN -> {
                    val plans = trip.scheduled
                        .filter { it.plan.costLocal != null || (m.byBlock[it.id] ?: 0) > 0 }
                        .map { Triple(it, plannedMinor(it.plan.costLocal), m.byBlock[it.id] ?: 0L) }
                        .sortedByDescending { (_, planned, actual) -> actual - planned }
                    if (plans.isEmpty()) item { EmptyState("No planned costs", "Plans with a cost in the planner, or with a spend linked, show here.") }
                    items(plans, key = { it.first.id }) { (b, planned, actual) ->
                        FgCard(onClick = { nav.push(Route.Block(b.id)) }) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {}) {
                                Column(Modifier.weight(1f)) {
                                    Text(b.plan.displayTitle, style = FgType.body, color = fg.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    b.day?.let { Text(dayLabel(it.dateIso), style = FgType.meta, color = fg.textSecondary) }
                                }
                                Text("${formatMinor(actual)} / ${formatMinor(planned)}", style = FgType.body, color = fg.text)
                                VarianceText(planned, actual, m.local)
                            }
                        }
                    }
                }
                MoneyView.LEDGER -> {
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(MoneyFilter.entries.toList()) { f ->
                                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                            }
                        }
                    }
                    val rows = m.rows.filter { it.matches(filter) }
                    if (rows.isEmpty()) item { EmptyState("Nothing here yet", "Spends you log show here, newest first.") }
                    items(rows, key = { it.id }) { r -> LedgerRow(vm, trip, m, r) }
                }
            }
            item { ConverterCard(vm, t.localCurrency, t.homeCurrency, t.exchangeRate) }
            item {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val file = vm.exportCsv() ?: return@launch
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                            val send = Intent(Intent.ACTION_SEND).setType("text/csv").putExtra(Intent.EXTRA_STREAM, uri)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            context.startActivity(Intent.createChooser(send, "Export spends"))
                        }
                    },
                    enabled = m.rows.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = FgDims.touch),
                ) { Text("Export spends (CSV)") }
            }
            item { Callout(Tone.PRIMARY, "Pay in ${t.localCurrency}", listOf("When a card machine offers ${t.homeCurrency}, choose ${t.localCurrency}. The conversion offered at the till is worse.")) }
        }
        ExtendedFloatingActionButton(
            onClick = { vm.openQuickAdd() },
            icon = { Icon(Icons.Rounded.Add, null) },
            text = { Text("Log spend") },
            containerColor = fg.primary,
            contentColor = fg.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(FgDims.margin),
        )
    }

    if (budgetDialog) {
        AmountDialog(
            title = "Trip budget",
            body = "Everything counts: pre-paid bookings and spends on the trip. In ${t.localCurrency}.",
            initial = m.summary.budgetMinor,
            allowClear = m.summary.budgetMinor != null,
            onDone = { vm.setBudget(it); budgetDialog = false },
            onDismiss = { budgetDialog = false },
        )
    }
    if (countDialog) {
        AmountDialog(
            title = "Count cash",
            body = "How much ${t.localCurrency} is in your wallet now? The pouch shows ${moneyLabel(m.pouchMinor, t.localCurrency)}.",
            initial = null,
            allowClear = false,
            onDone = { it?.let(vm::countCash); countDialog = false },
            onDismiss = { countDialog = false },
        )
    }
}

@Composable
internal fun AmountDialog(title: String, body: String, initial: Long?, allowClear: Boolean, onDone: (Long?) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial?.let { formatMinor(it, false).replace(",", "") } ?: "") }
    val parsed = parseAmount(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(body, style = FgType.body)
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> if (v.length <= 12) text = v.filter { it.isDigit() || it == '.' } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = FgType.railTime,
                )
            }
        },
        confirmButton = { TextButton(enabled = parsed != null, onClick = { onDone(parsed) }) { Text("Save") } },
        dismissButton = {
            Row {
                if (allowClear) TextButton(onClick = { onDone(null) }) { Text("Remove") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun ConverterCard(vm: AppViewModel, local: String, home: String, rate: Double) {
    var input by rememberSaveable { mutableStateOf("") }
    var localToHome by rememberSaveable { mutableStateOf(true) }
    Expandable("Converter · 1 $local = ${String.format(Locale.ROOT, "%.2f", rate)} $home") {
        val value = input.toDoubleOrNull() ?: 0.0
        val converted = if (localToHome) value * rate else value / rate
        val from = if (localToHome) local else home
        val to = if (localToHome) home else local
        Text("${input.ifEmpty { "0" }} $from", style = FgType.hero, color = fg.text)
        Text("= ${String.format(Locale.ROOT, "%.2f", converted)} $to", style = FgType.railTime, color = fg.primary)
        Keypad(onKey = { k -> input = keypadInput(input, k) })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { localToHome = !localToHome }, modifier = Modifier.weight(1f).heightIn(min = FgDims.touch)) { Text("Swap") }
            OutlinedButton(
                onClick = {
                    val minor = parseAmount(input) ?: return@OutlinedButton
                    val localMinor = if (localToHome) minor else Math.round(minor / rate)
                    vm.openQuickAdd(QuickAddRequest(amountMinor = localMinor))
                },
                enabled = parseAmount(input) != null,
                modifier = Modifier.weight(1f).heightIn(min = FgDims.touch),
            ) { Text("Log this") }
        }
        Text("Works offline with the trip rate.", style = FgType.meta, color = fg.textSecondary)
    }
}

@Composable
internal fun Keypad(onKey: (String) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(".", "0", "⌫"))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { k ->
                    val label = when (k) {
                        "⌫" -> "Delete"
                        "." -> "Point"
                        else -> k
                    }
                    Box(
                        Modifier.weight(1f).heightIn(min = 60.dp).clip(RoundedCornerShape(12.dp))
                            .background(toneColors(Tone.NEUTRAL).second)
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onKey(k)
                            }
                            .clearAndSetSemantics { contentDescription = label },
                        contentAlignment = Alignment.Center,
                    ) { Text(k, style = FgType.railTime, color = fg.text) }
                }
            }
        }
    }
}

// ---------- Day drill-down ----------

@Composable
fun MoneyDayScreen(vm: AppViewModel, trip: TripUi, dayId: String?) {
    val m by vm.money.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val day = dayId?.let { trip.dayOf(it) }
    val dm = m.days.firstOrNull { it.dayId == dayId }
    val rows = if (dayId == null) m.rows.filter { it.kind == MoneyKind.SPEND && (it.prepaid || it.dayId == null) } else m.forDay(dayId, day?.blocks?.map { it.id }?.toSet().orEmpty())
    LazyColumn(contentPadding = PaddingValues(FgDims.margin), verticalArrangement = Arrangement.spacedBy(FgDims.gap)) {
        item {
            FgCard {
                if (day != null && dm != null) {
                    val (l, h) = pair(m, dm.actualMinor)
                    Text(l, style = FgType.hero, color = fg.text)
                    Text(h, style = FgType.meta, color = fg.textSecondary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Planned ${formatMinor(dm.plannedMinor)} · Actual ${formatMinor(dm.actualMinor)}", style = FgType.body, color = fg.text, modifier = Modifier.weight(1f))
                        VarianceText(dm.plannedMinor, dm.actualMinor, m.local)
                    }
                    Text("Cash ${formatMinor(dm.cashMinor)} · Card ${formatMinor(dm.cardMinor)} ${m.local}", style = FgType.meta, color = fg.textSecondary)
                } else {
                    SectionTitle("Pre-paid")
                    Text(moneyLabel(m.summary.prepaidMinor, m.local), style = FgType.hero, color = fg.text)
                    Text("Counts toward the trip budget, not toward each day's pace.", style = FgType.meta, color = fg.textSecondary)
                }
            }
        }
        if (day != null) {
            val plans = day.blocks.filter { it.plan.costLocal != null || (m.byBlock[it.id] ?: 0) > 0 }
            if (plans.isNotEmpty()) item {
                FgCard {
                    SectionTitle("Plans · planned / actual")
                    plans.forEach { b ->
                        val planned = plannedMinor(b.plan.costLocal)
                        val actual = m.byBlock[b.id] ?: 0
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = FgDims.touch).clickable { nav.push(Route.Block(b.id)) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(b.plan.displayTitle, style = FgType.body, color = fg.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${formatMinor(planned)} / ${formatMinor(actual)}", style = FgType.body, color = fg.text)
                            VarianceText(planned, actual, m.local)
                        }
                    }
                }
            }
        }
        item { SectionTitle("Ledger") }
        if (rows.isEmpty()) item { EmptyState("Nothing logged", "Spends for this day show here.") }
        items(rows, key = { it.id }) { r -> LedgerRow(vm, trip, m, r) }
        item {
            OutlinedButton(
                onClick = { vm.openQuickAdd(QuickAddRequest(suggestPlan = dayId == trip.today()?.day?.id, dayId = dayId, prepaid = dayId == null)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = FgDims.touch),
            ) { Text("Log spend") }
        }
    }
}

// ---------- Block detail hook ----------

@Composable
fun BlockSpendCard(vm: AppViewModel, block: BlockUi) {
    val m by vm.money.collectAsStateWithLifecycle()
    val rows = m.forBlock(block.id)
    val planned = plannedMinor(block.plan.costLocal)
    if (planned == 0L && rows.isEmpty()) {
        TextButton(onClick = { vm.openQuickAdd(QuickAddRequest(blockId = block.id)) }, modifier = Modifier.heightIn(min = FgDims.touch)) {
            Icon(Icons.Rounded.Add, null)
            Spacer(Modifier.width(4.dp))
            Text("Add spend")
        }
        return
    }
    val actual = m.byBlock[block.id] ?: 0
    FgCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {}) {
            Text("Spend", style = FgType.cardTitle, color = fg.text, modifier = Modifier.weight(1f))
            Text("Planned ${formatMinor(planned)} · Actual ${formatMinor(actual)} ${m.local}", style = FgType.meta, color = fg.text)
            VarianceText(planned, actual, m.local)
        }
        if (rows.isNotEmpty()) {
            Text(
                rows.sortedBy { it.occurredAt }.joinToString(" · ") { r ->
                    val name = r.note?.takeIf { it.isNotBlank() } ?: SpendCategory.of(r.category).label
                    "$name ${formatMinor(r.amountMinor)}" + if (r.prepaid) " (pre-paid)" else ""
                },
                style = FgType.meta, color = fg.textSecondary,
            )
        }
        OutlinedButton(onClick = { vm.openQuickAdd(QuickAddRequest(blockId = block.id)) }, modifier = Modifier.fillMaxWidth().heightIn(min = FgDims.touch)) {
            Icon(Icons.Rounded.Add, null)
            Spacer(Modifier.width(4.dp))
            Text("Add spend")
        }
    }
}

/** One line for the morning briefing: "Safe to spend today 186 GEL · Cash 140 GEL". */
@Composable
fun BudgetBriefingLine(vm: AppViewModel, onOpen: () -> Unit) {
    val m by vm.money.collectAsStateWithLifecycle()
    if (!m.loaded) return
    val safe = m.summary.safePerDayMinor
    val parts = listOfNotNull(
        safe?.takeIf { m.summary.pace != Pace.ENDED }?.let { "Safe to spend today ${moneyLabel(it, m.local)}" },
        if (m.hasCash) "Cash ${moneyLabel(m.pouchMinor, m.local)}" else null,
    )
    if (parts.isEmpty()) return
    val short = m.cashNeededMinor > m.pouchMinor && m.hasCash
    Row(
        Modifier.fillMaxWidth().heightIn(min = FgDims.touch).clip(RoundedCornerShape(8.dp)).clickable(onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Payments, null, Modifier.size(18.dp), tint = if (short) fg.warning else fg.textSecondary)
        Spacer(Modifier.width(8.dp))
        Text(
            parts.joinToString(" · ") + if (short) ". Cash is short for cash-only plans." else "",
            style = FgType.body, color = if (short) fg.warning else fg.text, modifier = Modifier.weight(1f),
        )
    }
}

/** End-of-day catch-up: done plans with a planned cost and nothing logged against them. */
@Composable
fun SpendCatchUp(vm: AppViewModel, day: DayUi) {
    val m by vm.money.collectAsStateWithLifecycle()
    val missing = day.blocks.filter { it.plan.isDone && it.plan.costLocal != null && (m.byBlock[it.id] ?: 0) == 0L }
    if (missing.isEmpty()) return
    val total = day.blocks.count { it.plan.isDone && it.plan.costLocal != null }
    FgCard {
        Text("${total - missing.size} of $total paid plans have a spend logged. Add the rest?", style = FgType.body, color = fg.text)
        missing.forEach { b ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = FgDims.touch).clickable {
                    vm.openQuickAdd(QuickAddRequest(blockId = b.id, amountMinor = plannedMinor(b.plan.costLocal)))
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(b.plan.displayTitle, style = FgType.body, color = fg.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("~${moneyLabel(plannedMinor(b.plan.costLocal), m.local)}", style = FgType.meta, color = fg.textSecondary)
                Icon(Icons.Rounded.Add, contentDescription = "Log", tint = fg.primary)
            }
        }
    }
}

