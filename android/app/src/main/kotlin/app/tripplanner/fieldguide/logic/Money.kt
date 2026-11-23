package app.tripplanner.fieldguide.logic

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

// Money is held in minor units (4800 = 48.00) so totals never drift. Every currency is treated as two decimals.

object MoneyKind {
    const val SPEND = "SPEND"
    const val ATM = "ATM"
    const val EXCHANGE = "EXCHANGE"
    const val OPENING_CASH = "OPENING_CASH"
    const val CASH_COUNT = "CASH_COUNT"
}

object PayMethod {
    const val CASH = "CASH"
    const val CARD = "CARD"
    const val OTHER = "OTHER"
}

enum class SpendCategory(val label: String) {
    FOOD("Food"), TRANSPORT("Transport"), TICKETS("Tickets"), STAY("Stay"), SHOPPING("Shopping"), FEES("Fees"), OTHER("Other");

    companion object {
        fun of(name: String?): SpendCategory = entries.firstOrNull { it.name == name } ?: OTHER
    }
}

/** A money movement as the logic needs it. `amountMinor` is in `currency`; `homeMinor` in the trip's home currency. */
data class Spend(
    val id: String,
    val kind: String,
    val dayId: String?,
    val blockId: String?,
    val occurredAt: Long,
    val amountMinor: Long,
    val currency: String,
    val rateToHome: Double,
    val homeMinor: Long,
    val billedHomeMinor: Long?,
    val method: String,
    val category: String,
    val merchant: String?,
    val note: String?,
    val prepaid: Boolean,
    val mine: Boolean = true,
    val paidBy: String? = null,
) {
    val isSpend: Boolean get() = kind == MoneyKind.SPEND
    val isCash: Boolean get() = method == PayMethod.CASH

    /** What it cost in home currency: the bank's billed amount when known. */
    val homeValue: Long get() = billedHomeMinor ?: homeMinor
}

/** Local-currency value of a row. Spends in the local currency keep their own amount; others go through home currency. */
fun Spend.localMinor(localCurrency: String, tripRate: Double): Long =
    if (currency == localCurrency) amountMinor else if (tripRate > 0) (homeValue / tripRate).roundToLong() else 0

/** Home value of a typed amount. `rate` is home per one unit of `currency`: the trip rate, or a typed one. */
fun toHomeMinor(amountMinor: Long, currency: String, homeCurrency: String, rate: Double): Long =
    if (currency == homeCurrency) amountMinor else (amountMinor * rate).roundToLong()

/** "48", "48.50", "1,957". Whole numbers drop the decimals. */
fun formatMinor(minor: Long, decimalsWhenWhole: Boolean = false): String {
    val sign = if (minor < 0) "−" else ""
    val a = abs(minor)
    val whole = String.format(Locale.US, "%,d", a / 100)
    val cents = a % 100
    return if (cents == 0L && !decimalsWhenWhole) "$sign$whole" else "$sign$whole.${cents.toString().padStart(2, '0')}"
}

/** "48 GEL". Large amounts round to whole units. */
fun moneyLabel(minor: Long, currency: String): String {
    val shown = if (abs(minor) >= 100_00) (minor / 100.0).roundToLong() * 100 else minor
    return "${formatMinor(shown)} $currency"
}

/** Keypad text ("48.5") to minor units, or null when empty or invalid. */
fun parseAmount(text: String): Long? {
    val t = text.trim()
    if (t.isEmpty() || t == ".") return null
    if (!Regex("""\d{0,9}(\.\d{0,2})?""").matches(t)) return null
    val parts = t.split('.')
    val whole = parts[0].ifEmpty { "0" }.toLong()
    val cents = parts.getOrNull(1)?.padEnd(2, '0')?.toLong() ?: 0
    return whole * 100 + cents
}

/** One keypad press applied to the amount text. Caps at 7 digits and 2 decimals. */
fun keypadInput(current: String, key: String): String = when (key) {
    "⌫" -> current.dropLast(1)
    "C" -> ""
    "." -> if (current.contains('.')) current else current.ifEmpty { "0" } + "."
    else -> {
        val next = if (current == "0") key else current + key
        val parts = next.split('.')
        if (parts[0].length > 7 || (parts.size > 1 && parts[1].length > 2)) current else next
    }
}

// ---------- Cash pouch ----------

/** Cash in hand in local minor units. A withdrawal fills it, a cash spend empties it, and neither is counted twice. */
fun cashPouch(rows: List<Spend>, localCurrency: String, tripRate: Double): Long = rows.sumOf { r ->
    when (r.kind) {
        MoneyKind.OPENING_CASH, MoneyKind.ATM, MoneyKind.EXCHANGE, MoneyKind.CASH_COUNT -> r.localMinor(localCurrency, tripRate)
        MoneyKind.SPEND -> if (r.isCash) -r.localMinor(localCurrency, tripRate) else 0
        else -> 0
    }
}

// ---------- Budget ----------

enum class Pace { NO_BUDGET, ON_PACE, TIGHT, OVER, ENDED }

data class BudgetSummary(
    val budgetMinor: Long?,
    val prepaidMinor: Long,
    val onTripMinor: Long,
    val plannedMinor: Long,
    val daysLeft: Int,
    val todayPlannedMinor: Long,
) {
    val spentMinor: Long get() = prepaidMinor + onTripMinor
    val freeMinor: Long? get() = budgetMinor?.let { it - spentMinor - plannedMinor }

    /** What can be spent each remaining day, beyond what is already planned. */
    val safePerDayMinor: Long? get() = freeMinor?.let { free -> if (daysLeft > 0) Math.floorDiv(free, daysLeft.toLong()) else null }

    val pace: Pace
        get() {
            val safe = safePerDayMinor
            return when {
                budgetMinor == null -> Pace.NO_BUDGET
                daysLeft <= 0 -> Pace.ENDED
                safe == null -> Pace.ENDED
                safe < 0 -> Pace.OVER
                safe < todayPlannedMinor -> Pace.TIGHT
                else -> Pace.ON_PACE
            }
        }
}

/** A plan as the budget needs it: its planned cost and whether it can still cost money. */
data class PlannedCost(val blockId: String, val dayId: String?, val costMinor: Long, val open: Boolean)

/**
 * Planned money not yet spent: for each plan that is still open, its planned cost minus spend already linked to it,
 * never below zero. A finished or skipped plan counts nothing more.
 */
fun plannedNotSpent(plans: List<PlannedCost>, spendByBlock: Map<String, Long>, dayIds: Set<String>? = null): Long =
    plans.filter { it.open && (dayIds == null || it.dayId in dayIds) }
        .sumOf { (it.costMinor - (spendByBlock[it.blockId] ?: 0)).coerceAtLeast(0) }

fun budgetSummary(
    budgetMinor: Long?,
    rows: List<Spend>,
    plans: List<PlannedCost>,
    localCurrency: String,
    tripRate: Double,
    remainingDayIds: Set<String>,
    todayDayId: String?,
): BudgetSummary {
    val spends = rows.filter { it.isSpend }
    val byBlock = spendByBlock(spends, localCurrency, tripRate)
    val prepaid = spends.filter { it.prepaid || it.dayId == null }.sumOf { it.localMinor(localCurrency, tripRate) }
    val onTrip = spends.filter { !it.prepaid && it.dayId != null }.sumOf { it.localMinor(localCurrency, tripRate) }
    return BudgetSummary(
        budgetMinor = budgetMinor,
        prepaidMinor = prepaid,
        onTripMinor = onTrip,
        plannedMinor = plannedNotSpent(plans, byBlock, remainingDayIds),
        daysLeft = remainingDayIds.size,
        todayPlannedMinor = todayDayId?.let { plannedNotSpent(plans, byBlock, setOf(it)) } ?: 0,
    )
}

fun spendByBlock(rows: List<Spend>, localCurrency: String, tripRate: Double): Map<String, Long> =
    rows.filter { it.isSpend && it.blockId != null }
        .groupBy { it.blockId!! }
        .mapValues { (_, list) -> list.sumOf { it.localMinor(localCurrency, tripRate) } }

/** Pre-paid rows count in the trip total but are kept out of a day's pace. */
fun daySpentMinor(rows: List<Spend>, dayId: String, localCurrency: String, tripRate: Double): Long =
    rows.filter { it.isSpend && it.dayId == dayId && !it.prepaid }.sumOf { it.localMinor(localCurrency, tripRate) }

/**
 * What a day actually cost for a planned-vs-actual comparison: its own spends plus pre-paid spends linked to its plans,
 * so a pre-paid tour fee is set against the tour's planned cost.
 */
fun dayActualMinor(rows: List<Spend>, dayId: String, dayBlockIds: Set<String>, localCurrency: String, tripRate: Double): Long =
    rows.filter { it.isSpend && (it.dayId == dayId || (it.prepaid && it.blockId in dayBlockIds)) }
        .distinctBy { it.id }
        .sumOf { it.localMinor(localCurrency, tripRate) }

/** Planned cost in minor units from the planner's estimate in local currency. */
fun plannedMinor(costLocal: Double?): Long = costLocal?.let { (it * 100).roundToLong() } ?: 0

// ---------- Variance ----------

enum class Variance { UNDER, WITHIN, OVER, NONE }

/** Within 10% (or 5 units) of the plan reads as on plan. */
fun variance(plannedMinor: Long, actualMinor: Long): Variance {
    if (plannedMinor <= 0 && actualMinor <= 0) return Variance.NONE
    val diff = actualMinor - plannedMinor
    val slack = maxOf(plannedMinor / 10, 5_00)
    return when {
        abs(diff) <= slack -> Variance.WITHIN
        diff > 0 -> Variance.OVER
        else -> Variance.UNDER
    }
}

// ---------- Suggestions ----------

/** A plan on today's timeline, for the quick-add chip. */
data class PlanSlot(val blockId: String, val startMinute: Int, val endMinute: Int, val status: String)

/**
 * The plan most likely being paid for now, with no location: one running now (from 30 min before it starts to
 * 60 min after it ends), else the last one that finished, else none.
 */
fun suggestPlan(slots: List<PlanSlot>, nowMinute: Int): String? {
    val live = slots.filter { it.status != "SKIPPED" }.sortedBy { it.startMinute }
    live.lastOrNull { nowMinute >= it.startMinute - 30 && nowMinute <= it.endMinute + 60 }?.let { return it.blockId }
    return live.lastOrNull { it.endMinute <= nowMinute }?.blockId
}

fun categoryFor(kind: BlockKind?): SpendCategory = when (kind) {
    BlockKind.FLIGHT, BlockKind.TRAIN, BlockKind.TAXI, BlockKind.BUS, BlockKind.CABLE_CAR -> SpendCategory.TRANSPORT
    BlockKind.CHECK_IN, BlockKind.CHECK_OUT -> SpendCategory.STAY
    BlockKind.FOOD -> SpendCategory.FOOD
    BlockKind.TOUR, BlockKind.SIGHT -> SpendCategory.TICKETS
    null -> SpendCategory.OTHER
}

/** True when paying by card in the home currency, where the machine's own rate usually costs more. */
fun dccRisk(method: String, currency: String, homeCurrency: String, localCurrency: String): Boolean =
    method == PayMethod.CARD && currency == homeCurrency && homeCurrency != localCurrency

// ---------- Export ----------

private fun csvCell(value: String?): String {
    val v = value ?: ""
    return if (v.any { it == ',' || it == '"' || it == '\n' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
}

/** One row per spend, oldest first. `place` names the linked plan; `time` formats the moment in the day's zone. */
fun spendsCsv(
    rows: List<Spend>,
    homeCurrency: String,
    time: (Spend) -> String,
    place: (Spend) -> String?,
): String = buildString {
    append("date_time,kind,where,description,category,method,amount,currency,home_amount,home_currency,prepaid,plan\n")
    rows.sortedBy { it.occurredAt }.forEach { r ->
        append(
            listOf(
                time(r), r.kind, r.merchant, r.note, SpendCategory.of(r.category).label, r.method,
                formatMinor(r.amountMinor, true), r.currency, formatMinor(r.homeValue, true), homeCurrency,
                if (r.prepaid) "yes" else "no", place(r),
            ).joinToString(",") { csvCell(it) },
        )
        append('\n')
    }
}
