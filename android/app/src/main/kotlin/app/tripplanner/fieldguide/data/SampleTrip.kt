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
        id: String, day: String, start: Int, minutes: Int, title: String,
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
            block("demo-narikala", d1, 600, 90, "Narikala Fortress", "Take the cable car from Rike Park", 41.688, 44.8086, 5.0,
                local = "ნარიყალა", phonetic = "na-ri-KA-la"),
            block("demo-baths", d1, 720, 90, "Abanotubani sulphur baths", lat = 41.6884, lng = 44.811, cost = 80.0,
                fixed = true, ref = "Room 5, 12:00", local = "აბანოთუბანი", phonetic = "a-ba-no-TU-ba-ni"),
            block("demo-funicular", d1, 900, 120, "Mtatsminda funicular", lat = 41.6946, lng = 44.7866, cost = 10.0),
            block("demo-market", d2, 630, 75, "Dry Bridge Market", lat = 41.7006, lng = 44.804, closes = 1020,
                local = "მშრალი ხიდი", phonetic = "MSHRA-li KHI-di"),
            block("demo-sameba", d2, 780, 60, "Holy Trinity Cathedral", lat = 41.6977, lng = 44.8166),
            block("demo-train", d3, 490, 330, "Train to Kutaisi", cost = 25.0, fixed = true, type = "TRANSIT", ref = "Coach 3, seat 14"),
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
