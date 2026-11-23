package app.tripplanner.fieldguide.logic

import java.util.Locale

/** The parts of a block the derived views need. Mapped from the Room row, so this file stays plain Kotlin. */
data class PlanBlock(
    val id: String,
    val dayId: String?,
    val dateIso: String?,
    val startMinute: Int?,
    val durationMinutes: Int,
    val isFixed: Boolean,
    val blockType: String,
    val title: String,
    val notes: String?,
    val bookingRef: String?,
    val costLocal: Double?,
    val status: String,
) {
    val kind: BlockKind = kindOf(blockType, title)
    val flags: BlockFlags = flagsOf(title, notes, bookingRef)
    val parsed: ParsedNotes by lazy { parseNotes(notes) }
    val displayTitle: String = cleanTitle(title)
    val endMinute: Int? get() = startMinute?.plus(durationMinutes)
    val isPending: Boolean get() = status == "PENDING"
    val isDone: Boolean get() = status == "DONE"
    val isSkipped: Boolean get() = status == "SKIPPED"
    val isScheduled: Boolean get() = dayId != null && startMinute != null
}

/** Name after "Check in:" or "Check out:". */
fun hotelName(title: String): String = title.substringAfter(':', title).trim()

data class Stay(
    val name: String,
    val checkIn: PlanBlock,
    val checkOut: PlanBlock?,
    val fromIso: String,
    val toIso: String?,
) {
    val nights: Int? get() = toIso?.let { daysBetween(fromIso, it) }
    fun covers(dateIso: String): Boolean = dateIso >= fromIso && (toIso == null || dateIso < toIso)
}

/** Pairs each check-in with the next check-out of the same hotel. Blocks must be sorted by date and time. */
fun stays(blocks: List<PlanBlock>): List<Stay> {
    val hotels = blocks.filter { it.dateIso != null && (it.kind == BlockKind.CHECK_IN || it.kind == BlockKind.CHECK_OUT) }
    val out = mutableListOf<Stay>()
    for (b in hotels.filter { it.kind == BlockKind.CHECK_IN }) {
        val name = hotelName(b.title)
        val out2 = hotels.firstOrNull {
            it.kind == BlockKind.CHECK_OUT && hotelName(it.title).equals(name, ignoreCase = true) && it.dateIso!! >= b.dateIso!!
        }
        out += Stay(name, b, out2, b.dateIso!!, out2?.dateIso)
    }
    return out
}

fun bedTonight(stays: List<Stay>, dateIso: String): Stay? = stays.lastOrNull { it.covers(dateIso) }

enum class Health { BOOKED, NOT_BOOKED, INCOMPLETE, NONE }

fun health(b: PlanBlock): Health = when {
    b.flags.incomplete != null -> Health.INCOMPLETE
    b.flags.notBooked -> Health.NOT_BOOKED
    b.isFixed && !b.bookingRef.isNullOrBlank() -> Health.BOOKED
    else -> Health.NONE
}

data class LeaveBy(val minute: Int, val block: PlanBlock, val beThereBy: Int?)

private val goWords = Regex("""^(bolt|taxi|leave|walk|metro|bus)\b""", RegexOption.IGNORE_CASE)

/**
 * When to leave for the first pending block that needs travel. Check-ins and check-outs are skipped.
 * An explicit step such as "07:10 Bolt from Atrium" wins; then "Arrive by"; then a default per type.
 */
fun leaveBy(dayBlocks: List<PlanBlock>, nowMinute: Int = 0): LeaveBy? {
    val first = dayBlocks
        .filter { it.isPending && it.startMinute != null && it.startMinute >= nowMinute }
        .filter { it.kind != BlockKind.CHECK_IN && it.kind != BlockKind.CHECK_OUT }
        .minByOrNull { it.startMinute!! } ?: return null
    val start = first.startMinute!!
    val step = first.parsed.steps.firstOrNull { s ->
        s.minute != null && s.minute < start && goWords.containsMatchIn(s.text)
    }
    val beThere = first.flags.beThereBy?.takeIf { it < start }
    val minute = step?.minute ?: when (first.kind) {
        BlockKind.FLIGHT -> (beThere ?: (start - 150)) - 45
        BlockKind.TRAIN -> (beThere ?: (start - 25)) - 20
        else -> beThere ?: start
    }
    return LeaveBy(minute, first, beThere ?: if (first.kind == BlockKind.FLIGHT) start - 150 else if (first.kind == BlockKind.TRAIN) start - 25 else null)
}

data class NowNext(val now: PlanBlock?, val next: PlanBlock?, val minutesLeft: Int?, val minutesUntilNext: Int?)

fun nowNext(dayBlocks: List<PlanBlock>, nowMinute: Int): NowNext {
    val live = dayBlocks.filter { !it.isSkipped && it.startMinute != null }.sortedBy { it.startMinute }
    val now = live.lastOrNull { it.startMinute!! <= nowMinute && it.endMinute!! > nowMinute && !it.isDone }
    val next = live.firstOrNull { it.isPending && it.startMinute!! > nowMinute }
    return NowNext(now, next, now?.let { it.endMinute!! - nowMinute }, next?.let { it.startMinute!! - nowMinute })
}

data class Gap(val afterId: String, val fromMinute: Int, val minutes: Int)

/** Free time between consecutive blocks, 15 minutes or more. */
fun gaps(dayBlocks: List<PlanBlock>): List<Gap> {
    val sorted = dayBlocks.filter { it.startMinute != null && !it.isSkipped }.sortedBy { it.startMinute }
    return sorted.zipWithNext().mapNotNull { (a, b) ->
        val free = b.startMinute!! - a.endMinute!!
        if (free >= 15) Gap(a.id, a.endMinute!!, free) else null
    }
}

/** Things to carry today, from "Pack ..." and "Bring ..." sentences plus travel documents. */
fun carryToday(dayBlocks: List<PlanBlock>, max: Int = 8): List<String> {
    val out = linkedSetOf<String>()
    if (dayBlocks.any { it.kind == BlockKind.FLIGHT || it.kind == BlockKind.TRAIN }) out += "Passports"
    if (dayBlocks.any { it.kind == BlockKind.TRAIN && it.notes.orEmpty().contains("QR", ignoreCase = false) }) out += "Train QR e-tickets"
    if (dayBlocks.any { it.flags.cashOnly } || dayBlocks.any { it.notes.orEmpty().contains(" cash", ignoreCase = true) }) out += "Cash"
    dayBlocks.forEach { b -> b.parsed.bring.forEach { out += it } }
    return out.distinctBy { it.lowercase(Locale.ROOT) }.take(max)
}

fun cashNeeded(dayBlocks: List<PlanBlock>): Double =
    dayBlocks.filter { it.flags.cashOnly && !it.isSkipped }.sumOf { it.costLocal ?: 0.0 }

fun warningsToday(dayBlocks: List<PlanBlock>, max: Int = 4): List<String> =
    dayBlocks.flatMap { it.parsed.warnings }
        .map { w -> w.split(Regex("""(?<=[.!?])\s+""")).first().take(140) }
        .distinct()
        .take(max)

/** Cost of the day: every block not skipped. */
fun daySpend(dayBlocks: List<PlanBlock>): Double = dayBlocks.filter { !it.isSkipped }.sumOf { it.costLocal ?: 0.0 }

data class RouteStop(val name: String, val via: BlockKind?)

private val arrow = Regex("""\s*(?:→|->|–>)\s*""")

/** "AUH ✈ TBS 🚆 Batumi …" from the flights and trains, in order. */
fun routeStrip(blocks: List<PlanBlock>): List<RouteStop> {
    val legs = blocks.filter { it.kind == BlockKind.FLIGHT || it.kind == BlockKind.TRAIN || it.kind == BlockKind.BUS }
        .mapNotNull { b ->
            val parts = b.title.split(arrow)
            if (parts.size < 2) return@mapNotNull null
            val from = parts[0].trim().split(' ').last()
            val to = parts[1].trim().split(' ').first()
            Triple(from, to, b.kind)
        }
    if (legs.isEmpty()) return emptyList()
    val out = mutableListOf(RouteStop(legs.first().first, null))
    legs.forEach { (from, to, kind) ->
        if (!out.last().name.equals(from, ignoreCase = true)) out += RouteStop(from, null)
        out += RouteStop(to, kind)
    }
    return out
}

/** Flexible blocks that a shift would move, and fixed ones that stay. */
data class ShiftPlan(val moves: List<Pair<PlanBlock, Int>>, val stays: List<PlanBlock>)

fun shiftPlan(dayBlocks: List<PlanBlock>, nowMinute: Int, delta: Int): ShiftPlan {
    val ahead = dayBlocks.filter { it.isPending && it.startMinute != null && it.endMinute!! > nowMinute }.sortedBy { it.startMinute }
    return ShiftPlan(
        moves = ahead.filter { !it.isFixed }.map { it to it.startMinute!! + delta },
        stays = ahead.filter { it.isFixed },
    )
}
