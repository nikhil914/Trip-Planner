package app.tripplanner.fieldguide.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.tripplanner.fieldguide.data.ExpenseEntity
import app.tripplanner.fieldguide.logic.MoneyKind
import app.tripplanner.fieldguide.logic.PayMethod
import app.tripplanner.fieldguide.logic.PlanSlot
import app.tripplanner.fieldguide.logic.SpendCategory
import app.tripplanner.fieldguide.logic.categoryFor
import app.tripplanner.fieldguide.logic.dccRisk
import app.tripplanner.fieldguide.logic.formatMinor
import app.tripplanner.fieldguide.logic.keypadInput
import app.tripplanner.fieldguide.logic.moneyLabel
import app.tripplanner.fieldguide.logic.parseAmount
import app.tripplanner.fieldguide.logic.suggestPlan
import app.tripplanner.fieldguide.logic.toHomeMinor

/** Keypad text for a saved amount: 4850 -> "48.5", 4800 -> "48". */
private fun amountText(minor: Long): String {
    val whole = minor / 100
    val cents = minor % 100
    return if (cents == 0L) "$whole" else "$whole.${cents.toString().padStart(2, '0').trimEnd('0')}"
}

/**
 * Log or edit one money movement in a few taps: amount on the keypad, the plan it paid for, cash or card.
 * Everything else sits under More. A spend gets today's day, or no day before the trip (counted as pre-paid).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(vm: AppViewModel, trip: TripUi, money: MoneyUi, request: QuickAddRequest, onDismiss: () -> Unit) {
    val t = trip.trip ?: return onDismiss()
    val editing = request.editId?.let { money.row(it) }
    val kind0 = editing?.kind ?: request.kind
    val isSpend = kind0 == MoneyKind.SPEND
    val today = trip.today()
    val phase = trip.phase()

    // The plan suggested when nothing was picked: the one running now, else the last finished one.
    val suggested = remember(request) {
        if (request.blockId != null || editing != null || !request.suggestPlan || today == null) null
        else suggestPlan(
            today.blocks.mapNotNull { b ->
                val start = b.e.startMinute ?: return@mapNotNull null
                PlanSlot(b.id, start, start + b.e.durationMinutes, b.e.status)
            },
            today.nowMinute(),
        )
    }

    var text by rememberSaveable(request) {
        mutableStateOf((editing?.amountMinor ?: request.amountMinor)?.let(::amountText) ?: "")
    }
    var inHome by rememberSaveable(request) { mutableStateOf(editing?.currency == t.homeCurrency && t.homeCurrency != t.localCurrency) }
    var kind by rememberSaveable(request) { mutableStateOf(kind0) }
    var blockId by rememberSaveable(request) { mutableStateOf(editing?.blockId ?: request.blockId ?: suggested) }
    var categoryTouched by rememberSaveable(request) { mutableStateOf(editing != null) }
    var category by rememberSaveable(request) {
        mutableStateOf(editing?.category ?: categoryFor(blockId?.let { trip.byId[it]?.plan?.kind }).name)
    }
    var method by rememberSaveable(request) { mutableStateOf(editing?.method ?: if (isSpend) PayMethod.CARD else PayMethod.CASH) }
    var note by rememberSaveable(request) { mutableStateOf(editing?.note ?: "") }
    var merchant by rememberSaveable(request) { mutableStateOf(editing?.merchant ?: "") }
    var prepaid by rememberSaveable(request) { mutableStateOf(editing?.prepaid ?: (phase == TripPhase.PRE)) }
    var billed by rememberSaveable(request) { mutableStateOf(editing?.billedHomeMinor?.let(::amountText) ?: "") }
    var more by rememberSaveable(request) { mutableStateOf(editing != null && (editing.note != null || editing.merchant != null || editing.billedHomeMinor != null)) }

    val currency = if (inHome) t.homeCurrency else t.localCurrency
    val amount = parseAmount(text)
    val rate = if (currency == t.homeCurrency) 1.0 else t.exchangeRate
    val homeMinor = amount?.let { toHomeMinor(it, currency, t.homeCurrency, rate) }
    val localMinor = amount?.let { if (inHome) Math.round(it / t.exchangeRate) else it }

    // Plans to pick from: the given or saved one, the suggestion, then today's plans in time order.
    val choices = buildList<BlockUi> {
        listOfNotNull(editing?.blockId, request.blockId, suggested).forEach { id -> trip.byId[id]?.let { if (none { b -> b.id == it.id }) add(it) } }
        today?.blocks?.filter { !it.plan.isSkipped }?.sortedBy { it.e.startMinute ?: 0 }?.forEach { b -> if (none { it.id == b.id }) add(b) }
    }

    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = fg.card) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = FgDims.margin).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                when {
                    editing != null -> "Edit"
                    isSpend -> "Log spend"
                    else -> "Add cash"
                },
                style = FgType.cardTitle, color = fg.text,
            )

            // Amount, with the other currency under it.
            Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                Text("${text.ifEmpty { "0" }} $currency", style = FgType.hero, color = fg.text)
                val other = if (inHome) localMinor?.let { moneyLabel(it, t.localCurrency) } else homeMinor?.let { moneyLabel(it, t.homeCurrency) }
                Text(other?.let { "≈ $it" } ?: " ", style = FgType.meta, color = fg.textSecondary)
            }
            if (t.homeCurrency != t.localCurrency) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !inHome, onClick = { inHome = false }, label = { Text(t.localCurrency) }, modifier = Modifier.heightIn(min = FgDims.touch))
                    FilterChip(selected = inHome, onClick = { inHome = true }, label = { Text(t.homeCurrency) }, modifier = Modifier.heightIn(min = FgDims.touch))
                }
            }

            if (!isSpend) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(MoneyKind.ATM to "ATM", MoneyKind.EXCHANGE to "Exchange", MoneyKind.OPENING_CASH to "Brought").forEach { (k, label) ->
                        FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(label) }, modifier = Modifier.heightIn(min = FgDims.touch))
                    }
                }
                Text("Cash you get is not spending. It fills the cash pouch.", style = FgType.meta, color = fg.textSecondary)
            } else {
                if (choices.isNotEmpty()) {
                    Text("For", style = FgType.meta, color = fg.textSecondary)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(choices, key = { it.id }) { b ->
                            FilterChip(
                                selected = blockId == b.id,
                                onClick = {
                                    blockId = b.id
                                    if (!categoryTouched) category = categoryFor(b.plan.kind).name
                                },
                                label = { Text(b.plan.displayTitle, maxLines = 1) },
                                modifier = Modifier.heightIn(min = FgDims.touch),
                            )
                        }
                        item {
                            FilterChip(selected = blockId == null, onClick = { blockId = null }, label = { Text("No plan") }, modifier = Modifier.heightIn(min = FgDims.touch))
                        }
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SpendCategory.entries.toList()) { c ->
                        FilterChip(
                            selected = category == c.name,
                            onClick = { category = c.name; categoryTouched = true },
                            label = { Text(c.label) },
                            modifier = Modifier.heightIn(min = FgDims.touch),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(PayMethod.CARD to "Card", PayMethod.CASH to "Cash").forEach { (k, label) ->
                        FilterChip(selected = method == k, onClick = { method = k }, label = { Text(label) }, modifier = Modifier.weight(1f).heightIn(min = FgDims.touch))
                    }
                }
                if (dccRisk(method, currency, t.homeCurrency, t.localCurrency)) {
                    Callout(Tone.WARNING, "Paying in ${t.homeCurrency}?", listOf("The till's conversion usually costs more. Choose ${t.localCurrency} on the card machine."), Icons.Rounded.Info)
                }
            }

            Keypad(onKey = { k -> text = keypadInput(text, k) })

            TextButton(onClick = { more = !more }, modifier = Modifier.heightIn(min = FgDims.touch)) {
                Text(if (more) "Less" else "More: note, place, pre-paid", style = FgType.chip, color = fg.primary)
            }
            if (more) {
                OutlinedTextField(value = note, onValueChange = { note = it.take(200) }, label = { Text("Note") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (isSpend) {
                    OutlinedTextField(value = merchant, onValueChange = { merchant = it.take(120) }, label = { Text("Where") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Pre-paid", style = FgType.body, color = fg.text)
                            Text("Paid before the trip. Counts in the budget, not in a day's pace.", style = FgType.meta, color = fg.textSecondary)
                        }
                        Switch(checked = prepaid, onCheckedChange = { prepaid = it })
                    }
                    if (method == PayMethod.CARD && currency != t.homeCurrency) {
                        OutlinedTextField(
                            value = billed,
                            onValueChange = { v -> if (v.length <= 10) billed = v.filter { it.isDigit() || it == '.' } },
                            label = { Text("Billed in ${t.homeCurrency} (from your bank)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Button(
                onClick = {
                    val a = amount ?: return@Button
                    val now = System.currentTimeMillis()
                    val block = blockId?.let { trip.byId[it] }
                    val pre = isSpend && prepaid
                    val dayId = when {
                        pre -> null
                        editing != null && editing.dayId != null && editing.blockId == blockId -> editing.dayId
                        block?.day != null -> block.day.id
                        today != null -> today.day.id
                        phase == TripPhase.POST -> trip.days.lastOrNull()?.day?.id
                        else -> null
                    }
                    vm.saveExpense(
                        ExpenseEntity(
                            id = editing?.id ?: vm.newExpenseId(),
                            tripId = t.id,
                            mine = true,
                            kind = if (isSpend) MoneyKind.SPEND else kind,
                            dayId = dayId,
                            blockId = if (isSpend) blockId else null,
                            occurredAt = editing?.occurredAt ?: now,
                            amountMinor = a,
                            currency = currency,
                            rateToHome = rate,
                            homeMinor = homeMinor ?: 0,
                            billedHomeMinor = if (isSpend && method == PayMethod.CARD && currency != t.homeCurrency) parseAmount(billed) else null,
                            method = if (isSpend) method else PayMethod.CASH,
                            category = if (isSpend) category else SpendCategory.OTHER.name,
                            merchant = merchant.trim().ifEmpty { null }.takeIf { isSpend },
                            note = note.trim().ifEmpty { null },
                            prepaid = pre,
                            paidBy = null,
                            updatedAt = now,
                        ),
                    )
                    onDismiss()
                },
                enabled = amount != null && amount > 0,
                colors = ButtonDefaults.buttonColors(containerColor = fg.primary, contentColor = fg.onPrimary),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Text(if (amount != null && amount > 0) "Save ${formatMinor(amount)} $currency" else "Enter an amount", style = FgType.cardTitle)
            }
        }
    }
}
