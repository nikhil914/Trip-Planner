package app.tripplanner.fieldguide.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Sample text uses placeholders only. No real booking numbers, names or phone numbers.
class NotesTest {

    private fun block(
        id: String, date: String?, start: Int?, minutes: Int, title: String, type: String = "ACTIVITY",
        notes: String? = null, ref: String? = null, fixed: Boolean = false, cost: Double? = null, status: String = "PENDING",
    ) = PlanBlock(id, date?.let { "day-$it" }, date, start, minutes, fixed, type, title, notes, ref, cost, status)

    @Test
    fun formatsDurationsAndDates() {
        assertEquals("4 h 1 min", humanDuration(241))
        assertEquals("45 min", humanDuration(45))
        assertEquals("2 h", humanDuration(120))
        assertEquals("4 hours 1 minute", spokenDuration(241))
        assertEquals("Mon 30 Nov", dayLabel("2026-11-30"))
        assertEquals("26 Nov – 3 Dec", dateRange("2026-11-26", "2026-12-03"))
        assertEquals("112 GEL (152 AED)", money(112.36, 1.35, "GEL", "AED"))
    }

    @Test
    fun calmsShoutedPhrasesButKeepsCodes() {
        assertEquals("Non-refundable.", calmCaps("NON-REFUNDABLE."))
        assertEquals("Pay at the property, cash only: about GEL 112", calmCaps("PAY AT THE PROPERTY, CASH ONLY: about GEL 112"))
        assertEquals("Rally day in town", calmCaps("RALLY DAY in town"))
        assertEquals("PNR [PNR] · Seats 9A, 9B", calmCaps("PNR [PNR] · Seats 9A, 9B"))
        assertEquals("policies POLICYCODE 123/26", calmCaps("policies POLICYCODE 123/26"))
    }

    @Test
    fun readsKindsFromTypeAndTitle() {
        assertEquals(BlockKind.FLIGHT, kindOf("TRANSIT", "Flight XX100 AAA → BBB"))
        assertEquals(BlockKind.TRAIN, kindOf("TRANSIT", "Train 802 Tbilisi → Batumi"))
        assertEquals(BlockKind.TAXI, kindOf("TRANSIT", "Bolt: airport → hotel"))
        assertEquals(BlockKind.CHECK_OUT, kindOf("HOTEL", "Check out: Hotel A"))
        assertEquals(BlockKind.CHECK_IN, kindOf("HOTEL", "Check in: Hotel A"))
        assertEquals(BlockKind.TOUR, kindOf("ACTIVITY", "Mountain day tour (NOT BOOKED)"))
        assertEquals("Mountain day tour", cleanTitle("Mountain day tour (NOT BOOKED)"))
    }

    @Test
    fun splitsNotesIntoSections() {
        val notes = """
            15:30 walk from the hotel to the park (~10 min).
            16:15 cable car up, ~2.5 GEL each way.
            Precautions: the cable car closes in strong wind, check before you go. If closed: taxi to the car park (8–15 GEL).
            Late landing: skip today and do it tomorrow.
            ~09:30 Reservoir · ~10:00 Fortress · ~11:30 Monument
            Pack gloves, beanie, water, snacks and power bank.
            Tell the hotel your arrival time. Ask for: pickup price, an early breakfast, and whether they hold bags.
            NON-REFUNDABLE.
            Free WiFi.
        """.trimIndent()
        val p = parseNotes(notes)
        assertEquals(listOf("15:30", "16:15", "~09:30", "~10:00", "~11:30"), p.steps.map { it.time })
        assertEquals(975, p.steps[1].minute)
        assertEquals(listOf("If closed", "Late landing"), p.planB.map { it.trigger })
        assertTrue(p.warnings.first().startsWith("The cable car closes"))
        assertEquals(listOf("Gloves", "Beanie", "Water", "Snacks", "Power bank"), p.bring)
        assertEquals(listOf("Pickup price", "An early breakfast", "Whether they hold bags"), p.ask)
        assertEquals(listOf("Non-refundable."), p.terms)
        assertEquals(listOf("Free WiFi."), p.info)
    }

    @Test
    fun flagsBookingState() {
        val tour = flagsOf("Wine tour (NOT BOOKED)", "BOOK BEFORE YOU FLY: group tour.", null)
        assertTrue(tour.notBooked)
        val train = flagsOf(
            "Train 802",
            "Traveller B's outbound ticket wasn't among the files shared; check it's booked. Arrive by 07:40.",
            "Carriage 1 · Traveller B: ticket not yet shared",
        )
        assertNotNull(train.incomplete)
        assertEquals(460, train.beThereBy)
        val hotel = flagsOf("Check in: Hotel C", "Free cancellation until 14:00 on 2 Dec; after that the full price.", "[Booking no.]")
        assertEquals("14:00 on 2 Dec", hotel.freeCancelUntil)
        assertFalse(hotel.nonRefundable)
        assertTrue(flagsOf("Check in: Hotel B", "PAY AT THE PROPERTY, CASH ONLY: about GEL 112.", null).cashOnly)
        assertEquals(
            listOf("Cathedral", "Funicular", "Market"),
            flagsOf("Pick one: Cathedral, Funicular, or Market", null, null).choices,
        )
    }

    @Test
    fun derivesStaysBedAndLeaveBy() {
        val blocks = listOf(
            block("in1", "2026-11-26", 840, 30, "Check in: Hotel A", "HOTEL", fixed = true),
            block("out1", "2026-11-30", 420, 15, "Check out: Hotel A", "HOTEL", fixed = true),
            block("train", "2026-11-30", 485, 241, "Train 802 Tbilisi → Batumi", "TRANSIT",
                notes = "07:10 Bolt from the hotel to the station. Arrive by 07:40.", fixed = true),
            block("in2", "2026-11-30", 765, 30, "Check in: Hotel B", "HOTEL", notes = "Cash only", fixed = true, cost = 112.0),
            block("out2", "2026-12-02", 420, 15, "Check out: Hotel B", "HOTEL", fixed = true),
        )
        val s = stays(blocks)
        assertEquals(2, s.size)
        assertEquals(4, s[0].nights)
        assertEquals("Hotel A", bedTonight(s, "2026-11-29")?.name)
        assertEquals("Hotel B", bedTonight(s, "2026-11-30")?.name)
        assertNull(bedTonight(s, "2026-12-02"))

        val day5 = blocks.filter { it.dateIso == "2026-11-30" }
        val leave = leaveBy(day5)!!
        assertEquals("train", leave.block.id)
        assertEquals(430, leave.minute)
        assertEquals(460, leave.beThereBy)
        assertEquals(112.0, cashNeeded(day5), 0.01)

        val nn = nowNext(day5, 650)
        assertEquals("train", nn.now?.id)
        assertEquals("in2", nn.next?.id)
        assertEquals(76, nn.minutesLeft)

        assertEquals(listOf("Tbilisi", "Batumi"), routeStrip(blocks).map { it.name })
    }

    @Test
    fun parsesChecklistsAndReferenceSheets() {
        val fly = parseList(
            "l1", "✅ Before you fly: book & confirm",
            "In this order:\n1. Trains: DONE for both.\n2. Mountain tour.\n3. Wine tour.\n4. Insurance: done. Save offline.",
        )
        assertEquals("✅", fly.emoji)
        assertEquals(ListKind.CHECKLIST, fly.kind)
        assertEquals("In this order", fly.intro)
        assertEquals(listOf(true, false, false, true), fly.items.map { it.doneInSource })

        val pack = parseList("l2", "🎒 Packing list", "Clothes: warm jacket, gloves (thin, warm), beanie.\nShoes: grippy sneakers.")
        assertEquals(listOf("Clothes", "Shoes"), pack.sections.map { it.heading })
        assertEquals(listOf("Warm jacket", "Gloves (thin, warm)", "Beanie"), pack.sections[0].lines.map { it.text })

        val safety = parseList(
            "l3", "🆘 Emergency, insurance and safety",
            "Emergency: 112.\nInsurance: policies [Policy no. A]. 24/7 medical hotline +995 00 000 000 (call first).\nSafety:\n• Taxi scams: only use the app.\n• Pickpockets: busy squares.",
        )
        assertEquals(ListKind.REFERENCE, safety.kind)
        assertEquals(listOf("Emergency", "Insurance", "Safety"), safety.sections.map { it.heading })
        assertEquals("Taxi scams", safety.sections[2].lines[0].label)
        val phones = phonesIn(safety.sections[1].lines[0].text)
        assertEquals("+995 00 000 000", phones.single().number)

        val phrases = phrasesIn("Phrases: gamarjoba (hello), madloba (thank you), ki / ara (yes / no)")
        assertEquals(listOf("gamarjoba", "madloba", "ki / ara"), phrases.map { it.local })
    }
}
