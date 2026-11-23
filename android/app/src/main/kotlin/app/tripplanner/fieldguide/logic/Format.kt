package app.tripplanner.fieldguide.logic

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// Copy rules: 24-hour clock, dates as "Mon 30 Nov", durations as "4 h 1 min", local currency first.

/** "45 min", "2 h", "4 h 1 min". */
fun humanDuration(minutes: Int): String {
    val m = abs(minutes)
    val h = m / 60
    val rest = m % 60
    return when {
        h == 0 -> "$rest min"
        rest == 0 -> "$h h"
        else -> "$h h $rest min"
    }
}

/** Spoken form for TalkBack: "4 hours 1 minute". */
fun spokenDuration(minutes: Int): String {
    val m = abs(minutes)
    val h = m / 60
    val rest = m % 60
    val hours = if (h == 1) "1 hour" else "$h hours"
    val mins = if (rest == 1) "1 minute" else "$rest minutes"
    return when {
        h == 0 -> mins
        rest == 0 -> hours
        else -> "$hours $mins"
    }
}

private fun parseDate(dateIso: String): LocalDate? = runCatching { LocalDate.parse(dateIso) }.getOrNull()

/** "Mon 30 Nov". */
fun dayLabel(dateIso: String): String {
    val d = parseDate(dateIso) ?: return dateIso
    val dow = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
    val month = d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
    return "$dow ${d.dayOfMonth} $month"
}

/** "30 Nov". */
fun shortDate(dateIso: String): String {
    val d = parseDate(dateIso) ?: return dateIso
    return "${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}"
}

/** "26 Nov – 3 Dec". */
fun dateRange(fromIso: String, toIso: String): String = "${shortDate(fromIso)} – ${shortDate(toIso)}"

fun daysBetween(fromIso: String, toIso: String): Int {
    val a = parseDate(fromIso) ?: return 0
    val b = parseDate(toIso) ?: return 0
    return ChronoUnit.DAYS.between(a, b).toInt()
}

/** "112 GEL (151 AED)". Whole numbers unless the amount is small. */
fun money(local: Double, rate: Double, localCurrency: String, homeCurrency: String): String =
    "${amount(local)} $localCurrency (${amount(local * rate)} $homeCurrency)"

fun amount(value: Double): String {
    if (value >= 20 || value == value.roundToInt().toDouble()) return value.roundToInt().toString()
    return String.format(Locale.ROOT, "%.1f", value)
}

/** "UTC+4", "UTC−3:30". */
fun utcOffset(zone: ZoneId, dateIso: String): String {
    val d = parseDate(dateIso) ?: LocalDate.now(zone)
    val seconds = zone.rules.getOffset(d.atStartOfDay(zone).toInstant()).totalSeconds
    if (seconds == 0) return "UTC"
    val sign = if (seconds > 0) "+" else "−"
    val h = abs(seconds) / 3600
    val m = (abs(seconds) % 3600) / 60
    return if (m == 0) "UTC$sign$h" else String.format(Locale.ROOT, "UTC%s%d:%02d", sign, h, m)
}

/** "Asia/Tbilisi" -> "Tbilisi". */
fun zoneCity(zoneId: String): String = zoneId.substringAfterLast('/').replace('_', ' ')

private val shoutRun = Regex("""\b[A-Z][A-Z'-]+(?:[ ,]+[A-Z][A-Z'-]+)*\b""")
private val digitAfter = Regex("""^\s*\d""")

/**
 * Notes are often written with shouted phrases ("NON-REFUNDABLE", "PAY AT THE PROPERTY, CASH ONLY").
 * Runs of capitals with seven or more letters become sentence case. Short acronyms (PNR, GEL, QR) and
 * codes followed by digits (a policy or flight number) are kept.
 */
fun calmCaps(text: String): String = shoutRun.replace(text) { m ->
    val run = m.value
    val letters = run.count { it.isLetter() }
    val words = run.split(' ', ',').filter { it.isNotBlank() }
    val followedByDigit = digitAfter.containsMatchIn(text.substring(m.range.last + 1))
    val shout = letters >= 7 && (words.size >= 2 || run.contains('-')) && !followedByDigit
    if (!shout) return@replace run
    val lower = run.lowercase(Locale.ROOT)
    val start = m.range.first
    val before = text.substring(0, start).trimEnd()
    val sentenceStart = before.isEmpty() || before.last() in ".!?:(\n•"
    if (sentenceStart) lower.replaceFirstChar { it.titlecase(Locale.ROOT) } else lower
}
