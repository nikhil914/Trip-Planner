package app.tripplanner.fieldguide.logic

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

const val DAY_MINUTES = 1440

/** Minutes from local midnight as "HH:MM". Values past midnight wrap for display only. */
fun Int.hhmm(): String {
    val m = ((this % DAY_MINUTES) + DAY_MINUTES) % DAY_MINUTES
    return String.format(Locale.ROOT, "%02d:%02d", m / 60, m % 60)
}

fun zoneOrDefault(id: String?, fallback: ZoneId = ZoneId.systemDefault()): ZoneId =
    try {
        if (id.isNullOrBlank()) fallback else ZoneId.of(id)
    } catch (_: DateTimeException) {
        fallback
    }

fun todayIso(zone: ZoneId, now: Instant = Instant.now()): String = now.atZone(zone).toLocalDate().toString()

/** Current wall-clock minute in [zone] if [dateIso] is today there. 0 before that day, 1440 after it. */
fun minuteOfDay(dateIso: String, zone: ZoneId, now: Instant = Instant.now()): Int {
    val local = now.atZone(zone)
    val today = local.toLocalDate().toString()
    return when {
        today == dateIso -> local.hour * 60 + local.minute
        today < dateIso -> 0
        else -> DAY_MINUTES
    }
}

data class DayWake(val dayId: String, val dateIso: String, val timezone: String)

/** The day's own zone decides the instant. A wake time inside a DST gap moves forward. */
fun wakeInstant(dateIso: String, timezone: String, wakeMinute: Int): Instant {
    val minute = wakeMinute.coerceIn(0, DAY_MINUTES - 1)
    return ZonedDateTime.of(LocalDate.parse(dateIso), LocalTime.of(minute / 60, minute % 60), zoneOrDefault(timezone)).toInstant()
}

/** First trip morning whose wake time is still ahead of [now]. */
fun nextWake(days: List<DayWake>, wakeMinute: Int, now: Instant): Pair<DayWake, Instant>? =
    days.map { it to wakeInstant(it.dateIso, it.timezone, wakeMinute) }
        .filter { (_, at) -> at.isAfter(now) }
        .minByOrNull { (_, at) -> at }
