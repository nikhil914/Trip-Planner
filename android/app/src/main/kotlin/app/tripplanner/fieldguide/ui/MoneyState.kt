package app.tripplanner.fieldguide.ui

import app.tripplanner.fieldguide.data.ExpenseEntity
import app.tripplanner.fieldguide.logic.BudgetSummary
import app.tripplanner.fieldguide.logic.PlannedCost
import app.tripplanner.fieldguide.logic.Spend
import app.tripplanner.fieldguide.logic.budgetSummary
import app.tripplanner.fieldguide.logic.cashNeeded
import app.tripplanner.fieldguide.logic.cashPouch
import app.tripplanner.fieldguide.logic.dayActualMinor
import app.tripplanner.fieldguide.logic.localMinor
import app.tripplanner.fieldguide.logic.daySpend
import app.tripplanner.fieldguide.logic.plannedMinor
import app.tripplanner.fieldguide.logic.spendByBlock
import app.tripplanner.fieldguide.logic.todayIso
import app.tripplanner.fieldguide.logic.zoneOrDefault

fun ExpenseEntity.toSpend() = Spend(
    id = id, kind = kind, dayId = dayId, blockId = blockId, occurredAt = occurredAt, amountMinor = amountMinor,
    currency = currency, rateToHome = rateToHome, homeMinor = homeMinor, billedHomeMinor = billedHomeMinor, method = method,
    category = category, merchant = merchant, note = note, prepaid = prepaid, mine = mine, paidBy = paidBy,
)

/** Planned and actual spend for one day, in local minor units. */
data class DayMoney(val dayId: String, val plannedMinor: Long, val actualMinor: Long, val cashMinor: Long, val cardMinor: Long)

/** Everything the money screens read. Amounts are minor units of the local currency unless named otherwise. */
data class MoneyUi(
    val loaded: Boolean = false,
    val local: String = "",
    val home: String = "",
    val rate: Double = 1.0,
    val rows: List<ExpenseEntity> = emptyList(),
    val spends: List<Spend> = emptyList(),
    val summary: BudgetSummary = BudgetSummary(null, 0, 0, 0, 0, 0),
    val pouchMinor: Long = 0,
    /** Cash the cash-only plans still need today and tomorrow. */
    val cashNeededMinor: Long = 0,
    val byBlock: Map<String, Long> = emptyMap(),
    val days: List<DayMoney> = emptyList(),
    val unsent: Set<String> = emptySet(),
    val shared: Boolean = false,
    val hasCash: Boolean = false,
) {
    fun row(id: String): ExpenseEntity? = rows.firstOrNull { it.id == id }
    fun forBlock(blockId: String): List<ExpenseEntity> = rows.filter { it.blockId == blockId && it.kind == "SPEND" }
    fun forDay(dayId: String?): List<ExpenseEntity> =
        if (dayId == null) rows.filter { it.dayId == null } else rows.filter { it.dayId == dayId }
    fun home(minor: Long): Long = Math.round(minor * rate)
}

fun buildMoney(trip: TripUi, rows: List<ExpenseEntity>, unsent: Set<String>): MoneyUi {
    val t = trip.trip ?: return MoneyUi(loaded = trip.loaded)
    val spends = rows.map { it.toSpend() }
    val remaining = trip.days.filter { it.day.dateIso >= todayIso(zoneOrDefault(it.day.timezone)) }
    val today = trip.today()
    val plans = trip.scheduled.filter { it.plan.costLocal != null }.map {
        PlannedCost(it.id, it.e.dayId, plannedMinor(it.plan.costLocal), open = it.plan.isPending)
    }
    val summary = budgetSummary(
        budgetMinor = t.budgetMinor,
        rows = spends,
        plans = plans,
        localCurrency = t.localCurrency,
        tripRate = t.exchangeRate,
        remainingDayIds = remaining.map { it.day.id }.toSet(),
        todayDayId = today?.day?.id,
    )
    val days = trip.days.map { d ->
        val ids = d.blocks.map { it.id }.toSet()
        val own = spends.filter { it.isSpend && it.dayId == d.day.id && !it.prepaid }
        DayMoney(
            dayId = d.day.id,
            plannedMinor = plannedMinor(daySpend(d.plans)),
            actualMinor = dayActualMinor(spends, d.day.id, ids, t.localCurrency, t.exchangeRate),
            cashMinor = own.filter { it.isCash }.sumOf { it.localMinor(t.localCurrency, t.exchangeRate) },
            cardMinor = own.filter { !it.isCash }.sumOf { it.localMinor(t.localCurrency, t.exchangeRate) },
        )
    }
    val nextTwo = remaining.take(2)
    val cashNeeded = nextTwo.sumOf { d -> cashNeeded(d.plans.filter { it.isPending }) }
    return MoneyUi(
        loaded = true,
        local = t.localCurrency,
        home = t.homeCurrency,
        rate = t.exchangeRate,
        rows = rows,
        spends = spends,
        summary = summary,
        pouchMinor = cashPouch(spends.filter { it.mine }, t.localCurrency, t.exchangeRate),
        cashNeededMinor = plannedMinor(cashNeeded),
        byBlock = spendByBlock(spends, t.localCurrency, t.exchangeRate),
        days = days,
        unsent = unsent,
        shared = t.sharedLedger,
        hasCash = spends.any { it.mine && it.kind != "SPEND" },
    )
}
