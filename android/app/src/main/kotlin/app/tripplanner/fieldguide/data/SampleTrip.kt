package app.tripplanner.fieldguide.data

import java.time.LocalDate
import java.time.ZoneId

/** A three-day demo trip starting today, for trying the app before a server exists. */
object SampleTrip {
    class Bundle(
        val trip: TripEntity,
        val days: List<TripDayEntity>,
        val blocks: List<TimelineBlockEntity>,
        val items: List<ContextualItemEntity>,
    )

    private const val TRIP = "demo-trip"

    private fun block(
        id: String, day: String?, start: Int?, minutes: Int, title: String,
        tip: String? = null, lat: Double? = null, lng: Double? = null, cost: Double? = null,
        fixed: Boolean = false, type: String = "ACTIVITY", ref: String? = null,
        closes: Int? = null, local: String? = null, phonetic: String? = null,
    ) = TimelineBlockEntity(
        id = id, tripId = TRIP, dayId = day, sortOrder = 0, startMinute = start, durationMinutes = minutes,
        isFixedTime = fixed, blockType = type, title = title, subtitleOrTip = tip, lat = lat, lng = lng,
        googlePlaceId = null, googleMapsUri = null, estimatedCostLocal = cost, bookingRef = ref,
        opensMinute = null, closesMinute = closes, endTimezone = null, localScriptName = local,
        phoneticPronunciation = phonetic, status = Status.PENDING, updatedAt = 0,
    )

    private fun item(
        id: String, category: String, title: String, day: String, block: String? = null, venue: String? = null,
        price: String? = null, cost: Double? = null, meters: Int? = null, local: String? = null, phonetic: String? = null,
    ) = ContextualItemEntity(
        id = id, tripId = TRIP, linkedBlockId = block, dayId = day, category = category, title = title,
        shopOrVenueName = venue, priceBenchmarkNote = price, estimatedCostLocal = cost, lat = null, lng = null,
        googlePlaceId = null, distanceFromBlockMeters = meters, localScriptText = local,
        phoneticPronunciation = phonetic, isChecked = false, updatedAt = 0,
    )

    fun build(zone: ZoneId = ZoneId.systemDefault()): Bundle {
        val start = LocalDate.now(zone)
        val days = (0..2).map { i ->
            TripDayEntity("demo-day-${i + 1}", TRIP, i + 1, start.plusDays(i.toLong()).toString(), zone.id, null, null, 0)
        }
        val (d1, d2, d3) = days.map { it.id }

        val trip = TripEntity(
            id = TRIP, title = "Tbilisi long weekend (demo)", destinationTimezone = zone.id, homeCurrency = "USD",
            localCurrency = "GEL", exchangeRate = 0.37, startDate = days.first().dateIso, endDate = days.last().dateIso,
            wakeMinute = 450, updatedAt = 0,
        )

        val blocks = listOf(
            block("demo-narikala", d1, 600, 90, "Narikala Fortress",
                "10:00 walk to Rike Park (~10 min).\n10:20 cable car up, ~2.5 GEL each way.\nPrecautions: the cable car closes in strong wind. If closed: taxi to the car park (8–15 GEL).",
                41.688, 44.8086, 5.0, local = "ნარიყალა", phonetic = "na-ri-KA-la"),
            block("demo-baths", d1, 720, 90, "Abanotubani sulphur baths",
                "Private room, booked for 12:00.\nBring flip-flops and a towel.\nNON-REFUNDABLE.\nMax 15 min in the hot water at a time.",
                lat = 41.6884, lng = 44.811, cost = 80.0, fixed = true, ref = "Ref [Booking no.] · Room 5",
                local = "აბანოთუბანი", phonetic = "a-ba-no-TU-ba-ni"),
            block("demo-funicular", d1, 900, 120, "Mtatsminda funicular", "Rain plan: the National Museum instead.", lat = 41.6946, lng = 44.7866, cost = 10.0),
            block("demo-checkin", d1, 1110, 30, "Check in: Demo Guesthouse",
                "[Street address], Tbilisi. Phone [Hotel phone].\nPAY AT THE PROPERTY, CASH ONLY: about GEL 60.\nAsk for: late breakfast, bag storage.",
                lat = 41.69, lng = 44.80, cost = 60.0, fixed = true, type = "HOTEL", ref = "Booking.com [Booking no.]"),
            block("demo-market", d2, 630, 75, "Dry Bridge Market", "Scam warning: agree prices before you pay.", lat = 41.7006, lng = 44.804, closes = 1020,
                local = "მშრალი ხიდი", phonetic = "MSHRA-li KHI-di"),
            block("demo-tour", d2, 780, 240, "Wine day tour (NOT BOOKED)",
                "BOOK BEFORE YOU FLY: group tour from USD 20 pp.\n~13:30 Sighnaghi walls · ~15:00 winery tasting\nNot drinking: pick a culture tour instead.", cost = 60.0),
            block("demo-checkout", d3, 420, 15, "Check out: Demo Guesthouse", "Take everything.", fixed = true, type = "HOTEL", ref = "Booking.com [Booking no.]"),
            block("demo-train", d3, 490, 330, "Train 800 Tbilisi → Kutaisi", "07:30 taxi to the station. Arrive by 08:00.\nShow the QR e-ticket and carry the passport used for booking.",
                cost = 25.0, fixed = true, type = "TRANSIT", ref = "Carriage 3 · Seat 14 (Traveller A) · Traveller B: ticket not yet shared"),
            block("demo-list-fly", null, null, 90, "✅ Before you fly",
                "1. Trains: done.\n2. Book the wine tour.\n3. Save hotel addresses offline.\n4. Online check-in."),
            block("demo-list-pack", null, null, 90, "🎒 Packing list", "Clothes: warm jacket, gloves, beanie.\nTech: power bank, plug adapter."),
            block("demo-list-sos", null, null, 90, "🆘 Emergency, insurance and safety",
                "Emergency: 112.\nInsurance: policy [Policy no. A]. 24/7 hotline +000 00 000 000 (placeholder).\nSafety:\n• Taxi scams: use an app taxi.\n• Pickpockets: busy squares and the metro."),
            block("demo-list-food", null, null, 90, "🍽️ Food and phrases", "Veg: khachapuri, lobio, badrijani.\nPhrases: gamarjoba (hello), madloba (thank you)."),
        )

        val items = listOf(
            item("demo-khachapuri", Category.EAT, "Adjaruli khachapuri", d1, "demo-baths", "Bakery near the baths",
                cost = 18.0, meters = 134, local = "ხაჭაპური", phonetic = "kha-cha-PU-ri"),
            item("demo-khinkali", Category.EAT, "Khinkali", d2, "demo-market", "Dumpling house by Dry Bridge",
                "Fair price: 1.5 to 2 GEL per piece", 25.0, 105, "ხინკალი", "khin-KA-li"),
            item("demo-churchkhela", Category.TO_BUY, "Churchkhela", d2, "demo-market", "Stalls on the bridge",
                "Fair price: 3 to 5 GEL each. Tourist stalls ask 8 or more", 12.0, 320, "ჩურჩხელა", "church-KHE-la"),
            item("demo-cash", Category.PREP, "Bring 150 GEL cash", d1),
            item("demo-towel", Category.PREP, "Pack a towel for the baths", d1),
            item("demo-passport", Category.PREP, "Carry physical passport", d3),
        )
        return Bundle(trip, days, blocks, items)
    }
}
