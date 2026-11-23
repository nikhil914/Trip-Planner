package app.tripplanner.fieldguide.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {
    private val rate = 1.36 // AED per GEL

    private fun row(
        id: String,
        minor: Long,
        kind: String = MoneyKind.SPEND,
        method: String = PayMethod.CARD,
        day: String? = "d3",
        block: String? = null,
        prepaid: Boolean = false,
        currency: String = "GEL",
    ) = Spend(
        id = id, kind = kind, dayId = day, blockId = block, occurredAt = 0, amountMinor = minor, currency = currency,
        rateToHome = rate, homeMinor = toHomeMinor(minor, currency, "AED", rate), billedHomeMinor = null,
        method = method, category = "FOOD", merchant = null, note = null, prepaid = prepaid,
    )

    @Test
    fun atmCashIsNeverSpend() {
        val rows = listOf(
            row("open", 10_000, MoneyKind.OPENING_CASH, PayMethod.CASH),
            row("atm", 40_000, MoneyKind.ATM, PayMethod.CASH),
            row("fee", 1_000, method = PayMethod.CARD),
            row("lunch", 4_800, method = PayMethod.CASH),
            row("wine", 2_000, method = PayMethod.CASH),
        )
        assertEquals(43_200, cashPouch(rows, "GEL", rate))
        val s = budgetSummary(null, rows, emptyList(), "GEL", rate, setOf("d3"), "d3")
        assertEquals(7_800, s.spentMinor)
    }

    @Test
    fun countingCashLogsTheGap() {
        val rows = listOf(row("atm", 20_000, MoneyKind.ATM, PayMethod.CASH), row("count", -3_000, MoneyKind.CASH_COUNT, PayMethod.CASH))
        assertEquals(17_000, cashPouch(rows, "GEL", rate))
    }

    @Test
    fun safeToSpendMatchesTheSpecExample() {
        // Budget 3,200; pre-paid 1,260; spent on trip 697; still planned 685; 3 days left -> 186 a day.
        val rows = listOf(
            row("pre", 126_000, prepaid = true, day = null),
            row("trip", 69_700, day = "d2"),
        )
        val plans = listOf(PlannedCost("p1", "d4", 40_000, true), PlannedCost("p2", "d5", 28_500, true), PlannedCost("old", "d1", 9_900, false))
        val s = budgetSummary(320_000, rows, plans, "GEL", rate, setOf("d3", "d4", "d5"), "d3")
        assertEquals(68_500, s.plannedMinor)
        assertEquals(18_600L, s.safePerDayMinor)
        assertEquals(Pace.ON_PACE, s.pace)
    }

    @Test
    fun linkedSpendReducesWhatIsStillPlanned() {
        val rows = listOf(row("fee", 16_000, block = "tour", prepaid = true, day = null), row("lunch", 4_800, block = "tour"))
        val plans = listOf(PlannedCost("tour", "d3", 22_000, true))
        assertEquals(1_200, plannedNotSpent(plans, spendByBlock(rows, "GEL", rate)))
        val over = listOf(row("big", 30_000, block = "tour"))
        assertEquals(0, plannedNotSpent(plans, spendByBlock(over, "GEL", rate)))
    }

    @Test
    fun paceStates() {
        val base = BudgetSummary(100_000, 0, 0, 0, 2, 10_000)
        assertEquals(Pace.ON_PACE, base.pace)
        assertEquals(Pace.TIGHT, base.copy(onTripMinor = 85_000).pace)
        assertEquals(Pace.OVER, base.copy(onTripMinor = 120_000).pace)
        assertEquals(-10_000L, base.copy(onTripMinor = 120_000).safePerDayMinor)
        assertEquals(Pace.NO_BUDGET, base.copy(budgetMinor = null).pace)
        assertEquals(Pace.ENDED, base.copy(daysLeft = 0).pace)
    }

    @Test
    fun homeCurrencySpendConvertsBackToLocal() {
        val aed = row("aed", 13_600, currency = "AED")
        assertEquals(13_600, aed.homeMinor)
        assertEquals(10_000, aed.localMinor("GEL", rate))
        val billed = row("card", 10_000).copy(billedHomeMinor = 14_000)
        assertEquals(10_000, billed.localMinor("GEL", rate))
        assertEquals(14_000, billed.homeValue)
    }

    @Test
    fun keypadAndParsing() {
        var t = ""
        listOf("4", "8", ".", "5", "0", "9").forEach { t = keypadInput(t, it) }
        assertEquals("48.50", t)
        assertEquals(4_850L, parseAmount(t))
        assertEquals("0.", keypadInput("", "."))
        assertNull(parseAmount(""))
        assertNull(parseAmount("1.2.3"))
        assertEquals("1,957", formatMinor(195_700))
        assertEquals("48.50", formatMinor(4_850))
        assertEquals("−31", formatMinor(-3_100))
    }

    @Test
    fun suggestsThePlanBeingPaidFor() {
        val slots = listOf(PlanSlot("coffee", 480, 540, "DONE"), PlanSlot("tour", 600, 1020, "PENDING"), PlanSlot("dinner", 1200, 1320, "PENDING"))
        assertEquals("tour", suggestPlan(slots, 700))
        assertEquals("tour", suggestPlan(slots, 1070))
        assertEquals("dinner", suggestPlan(slots, 1180))
        assertEquals("tour", suggestPlan(slots, 1100))
        assertNull(suggestPlan(slots, 300))
    }

    @Test
    fun varianceAndDcc() {
        assertEquals(Variance.WITHIN, variance(22_000, 22_800))
        assertEquals(Variance.OVER, variance(15_000, 17_600))
        assertEquals(Variance.UNDER, variance(18_000, 9_100))
        assertEquals(Variance.NONE, variance(0, 0))
        assertTrue(dccRisk(PayMethod.CARD, "AED", "AED", "GEL"))
        assertFalse(dccRisk(PayMethod.CASH, "AED", "AED", "GEL"))
    }

    @Test
    fun csvQuotesAndOrders() {
        val rows = listOf(row("b", 2_000).copy(occurredAt = 2, merchant = "Wine, cellar"), row("a", 4_800).copy(occurredAt = 1))
        val csv = spendsCsv(rows, "AED", { it.occurredAt.toString() }, { null }).lines()
        assertTrue(csv[1].startsWith("1,SPEND"))
        assertTrue(csv[2].contains("\"Wine, cellar\""))
    }
}
