package app.tripplanner.fieldguide.logic

import java.util.Locale

/** Visual sub-type, read from the block type and title. Drives icon and colour. */
enum class BlockKind { FLIGHT, TRAIN, TAXI, BUS, CABLE_CAR, CHECK_IN, CHECK_OUT, FOOD, TOUR, SIGHT }

/** Colour family of a kind. */
enum class KindFamily { TRANSIT, STAY, FOOD, ACTIVITY }

val BlockKind.family: KindFamily
    get() = when (this) {
        BlockKind.FLIGHT, BlockKind.TRAIN, BlockKind.TAXI, BlockKind.BUS, BlockKind.CABLE_CAR -> KindFamily.TRANSIT
        BlockKind.CHECK_IN, BlockKind.CHECK_OUT -> KindFamily.STAY
        BlockKind.FOOD -> KindFamily.FOOD
        BlockKind.TOUR, BlockKind.SIGHT -> KindFamily.ACTIVITY
    }

val BlockKind.label: String
    get() = when (this) {
        BlockKind.FLIGHT -> "Flight"
        BlockKind.TRAIN -> "Train"
        BlockKind.TAXI -> "Taxi"
        BlockKind.BUS -> "Bus"
        BlockKind.CABLE_CAR -> "Cable car"
        BlockKind.CHECK_IN -> "Check in"
        BlockKind.CHECK_OUT -> "Check out"
        BlockKind.FOOD -> "Food"
        BlockKind.TOUR -> "Tour"
        BlockKind.SIGHT -> "Sight"
    }

fun kindOf(blockType: String, title: String): BlockKind {
    val t = title.lowercase(Locale.ROOT)
    return when (blockType) {
        "HOTEL" -> if (t.startsWith("check out") || t.startsWith("check-out")) BlockKind.CHECK_OUT else BlockKind.CHECK_IN
        "FOOD_STOP" -> BlockKind.FOOD
        "TRANSIT" -> when {
            t.contains("flight") || t.contains("✈") -> BlockKind.FLIGHT
            t.contains("train") -> BlockKind.TRAIN
            t.contains("bus") || t.contains("coach") || t.contains("marshrutka") -> BlockKind.BUS
            t.contains("cable car") || t.contains("funicular") -> BlockKind.CABLE_CAR
            else -> BlockKind.TAXI
        }
        else -> when {
            t.contains("tour") || t.contains("day trip") -> BlockKind.TOUR
            else -> BlockKind.SIGHT
        }
    }
}

data class Step(val time: String, val minute: Int?, val text: String)

data class PlanB(val trigger: String, val text: String)

data class ParsedNotes(
    val steps: List<Step> = emptyList(),
    val warnings: List<String> = emptyList(),
    val planB: List<PlanB> = emptyList(),
    val bring: List<String> = emptyList(),
    val ask: List<String> = emptyList(),
    val terms: List<String> = emptyList(),
    val money: List<String> = emptyList(),
    val info: List<String> = emptyList(),
    /** "Book before you fly: ..." guidance for a block that is not booked yet. */
    val booking: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = steps.isEmpty() && warnings.isEmpty() && planB.isEmpty() && bring.isEmpty() &&
            ask.isEmpty() && terms.isEmpty() && money.isEmpty() && info.isEmpty() && booking.isEmpty()
}

data class BlockFlags(
    val notBooked: Boolean = false,
    /** A sentence saying what is missing, for example a second traveller's ticket. */
    val incomplete: String? = null,
    val nonRefundable: Boolean = false,
    val freeCancellation: Boolean = false,
    /** "14:00 on 2 Dec" when the notes give one. */
    val freeCancelUntil: String? = null,
    val cashOnly: Boolean = false,
    val dressCode: Boolean = false,
    /** Options of a "Pick one: A, B, or C" block. */
    val choices: List<String> = emptyList(),
    /** "Be at the airport by 11:30" or "Arrive by 07:40", as minutes. */
    val beThereBy: Int? = null,
)

private val timeLead = Regex("""^~?(\d{1,2}):(\d{2})(?:\s*[–-]\s*~?(\d{1,2}):(\d{2}))?\s+(.+)$""")
private val planBStart = Regex(
    """^(if [^:,]{1,40}[:,]|rain(?:y)?(?:[- ]day)?(?: plan| swap)?(?: for the day)?:|alternates?:|delayed or cancelled:|not drinking:|""" +
        """late landing:|fully booked:|no data[^:]{0,20}:|road closed|heavy snow[^:]{0,20}|clear and keen:|plan b:|backup:)""",
    RegexOption.IGNORE_CASE,
)
private val planBSplit = Regex(
    """(?<=[.!?])\s+(?=(?:If [a-z]|Rain(?:y)?[ :-]|Alternates?:|Delayed or cancelled:|Not drinking:|Late landing:|Fully booked:|No data|Backup:|Plan B:))""",
)
private val warningWords = Regex(
    """\b(scam|scams|pickpocket|pickpockets|protest|protests|protesters|rally|rallies|precautions?|warning|beware|""" +
        """touts?|icy|ice on|arrests?|don't watch|avoid|keep away|decline|watch for)\b""",
    RegexOption.IGNORE_CASE,
)
private val bringVerb = Regex("""(?:^|[.;]\s*)(?:Pack|Bring|Carry)\s+([^.;:]+)""", RegexOption.IGNORE_CASE)
private val askFor = Regex("""Ask for:\s*([^.]+)""", RegexOption.IGNORE_CASE)
private val sentenceEnd = Regex("""(?<=[.!?;])\s+""")
private val bookFirst = Regex("""^book before you fly""", RegexOption.IGNORE_CASE)
private val bringLine = Regex("""^(pack|bring|carry)\b[^.]*\.?$""", RegexOption.IGNORE_CASE)
private val askLine = Regex("""^(ask |tell the hotel|tell them)""", RegexOption.IGNORE_CASE)
private val termWords = Regex("""(non-refundable|free cancellation|refund|cancellation|changes free|no prepayment)""", RegexOption.IGNORE_CASE)
private val moneyWords = Regex("""(cash only|pay at the property|payment upon arrival|paid|total about|total)""", RegexOption.IGNORE_CASE)
private val bulletLead = Regex("""^[•·*\-–]\s*""")
private val stepSegments = Regex("""\s+·\s+(?=~?\d{1,2}:\d{2})""")

/** Splits a list like "gloves, beanie, water and snacks" into chips. Commas inside brackets are kept. */
fun splitList(text: String, splitAnd: Boolean = false): List<String> {
    val out = mutableListOf<String>()
    var depth = 0
    val cur = StringBuilder()
    for (c in text) {
        when {
            c == '(' -> { depth++; cur.append(c) }
            c == ')' -> { depth = (depth - 1).coerceAtLeast(0); cur.append(c) }
            c == ',' && depth == 0 -> { out += cur.toString(); cur.clear() }
            else -> cur.append(c)
        }
    }
    out += cur.toString()
    return out.flatMap { part ->
        val p = part.trim()
        val q = p.removePrefix("and ")
        if (splitAnd) q.split(" and ") else listOf(q)
    }.map { it.trim().trimEnd('.') }.filter { it.isNotEmpty() }
}

private fun timeToMinute(h: String, m: String): Int? {
    val hh = h.toIntOrNull() ?: return null
    val mm = m.toIntOrNull() ?: return null
    if (hh !in 0..23 || mm !in 0..59) return null
    return hh * 60 + mm
}

private fun capital(s: String): String = s.trim().replaceFirstChar { it.titlecase(Locale.ROOT) }

/**
 * Turns free-text notes into sections. Every line lands somewhere, so nothing is lost: lines that match
 * no pattern go to [ParsedNotes.info].
 */
fun parseNotes(raw: String?): ParsedNotes {
    if (raw.isNullOrBlank()) return ParsedNotes()
    val steps = mutableListOf<Step>()
    val warnings = mutableListOf<String>()
    val planB = mutableListOf<PlanB>()
    val bring = linkedSetOf<String>()
    val ask = mutableListOf<String>()
    val terms = mutableListOf<String>()
    val money = mutableListOf<String>()
    val info = mutableListOf<String>()
    val booking = mutableListOf<String>()

    val lines = raw.lines()
        .map { calmCaps(it.trim().replace(bulletLead, "")) }
        .filter { it.isNotEmpty() }
        .flatMap { it.split(planBSplit) }
        .flatMap { it.split(stepSegments) }
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    for (line in lines) {
        bringVerb.findAll(line).forEach { m ->
            splitList(m.groupValues[1], splitAnd = true).filter { it.length <= 40 }.forEach { bring += capital(it) }
        }
        askFor.find(line)?.let { m -> splitList(m.groupValues[1]).forEach { ask += capital(it) } }

        val pb = planBStart.find(line)
        val time = timeLead.find(line)
        when {
            bookFirst.containsMatchIn(line) -> booking += line
            pb != null && pb.range.first == 0 -> {
                val trigger = line.substring(0, pb.range.last + 1).trimEnd(':', ',', ' ')
                val body = line.substring(pb.range.last + 1).trim()
                planB += PlanB(capital(trigger), capital(body.ifEmpty { line }))
            }
            time != null -> {
                val g = time.groupValues
                val label = if (g[3].isNotEmpty()) "${g[1].padStart(2, '0')}:${g[2]}–${g[3].padStart(2, '0')}:${g[4]}" else "${g[1].padStart(2, '0')}:${g[2]}"
                val approx = line.startsWith("~")
                steps += Step(if (approx) "~$label" else label, timeToMinute(g[1], g[2]), capital(g[5]))
                g[5].split(sentenceEnd).filter { warningWords.containsMatchIn(it) }.forEach { warnings += capital(it) }
            }
            warningWords.containsMatchIn(line) -> warnings += line.removePrefix("Precautions:").removePrefix("Safety:").trim().let(::capital)
            bringLine.containsMatchIn(line) -> Unit
            askLine.containsMatchIn(line) -> if (askFor.find(line) == null) ask += line
            moneyWords.containsMatchIn(line) -> money += line
            termWords.containsMatchIn(line) -> terms += line
            else -> info += line
        }
    }
    return ParsedNotes(steps, warnings.distinct(), planB, bring.toList(), ask.distinct(), terms, money, info, booking)
}

private val notBookedRe = Regex("""not booked|book before you fly""", RegexOption.IGNORE_CASE)
private val incompleteRe = Regex(
    """[^.\n]*(not yet shared|wasn't among|was not shared|wasn't shared|ticket missing|seat missing|add the seat)[^.\n]*""",
    RegexOption.IGNORE_CASE,
)
private val freeUntilRe = Regex("""free cancellation until ([^;.\n]+)""", RegexOption.IGNORE_CASE)
private val beThereRe = Regex("""(?:be at the (?:airport|station) by|arrive by)\s+(\d{1,2}):(\d{2})""", RegexOption.IGNORE_CASE)
private val dressRe = Regex("""dress code|cover hair|headscarf""", RegexOption.IGNORE_CASE)
private val pickOneRe = Regex("""^pick one:\s*(.+)$""", RegexOption.IGNORE_CASE)
private val bookedTag = Regex("""\s*\((?:not booked|NOT BOOKED)\)\s*""", RegexOption.IGNORE_CASE)

fun flagsOf(title: String, notes: String?, bookingRef: String?): BlockFlags {
    val n = notes.orEmpty()
    val all = "$title\n$n\n${bookingRef.orEmpty()}"
    val incomplete = incompleteRe.find(n)?.value?.trim()
        ?: incompleteRe.find(bookingRef.orEmpty())?.value?.trim()
    val choices = pickOneRe.find(title.trim())?.groupValues?.get(1)?.let { list ->
        splitList(list.replace(Regex("""\s+or\s+""", RegexOption.IGNORE_CASE), ", "))
    }.orEmpty()
    val beThere = beThereRe.find(n)?.let { timeToMinute(it.groupValues[1], it.groupValues[2]) }
    return BlockFlags(
        notBooked = notBookedRe.containsMatchIn(title) || notBookedRe.containsMatchIn(n),
        incomplete = incomplete?.let { capital(calmCaps(it)) },
        nonRefundable = all.contains("non-refundable", ignoreCase = true),
        freeCancellation = all.contains("free cancellation", ignoreCase = true),
        freeCancelUntil = freeUntilRe.find(n)?.groupValues?.get(1)?.trim(),
        cashOnly = all.contains("cash only", ignoreCase = true),
        dressCode = dressRe.containsMatchIn(n),
        choices = choices,
        beThereBy = beThere,
    )
}

/** Title without the "(NOT BOOKED)" tag, which becomes a chip instead. */
fun cleanTitle(title: String): String = calmCaps(title.replace(bookedTag, " ").trim())
