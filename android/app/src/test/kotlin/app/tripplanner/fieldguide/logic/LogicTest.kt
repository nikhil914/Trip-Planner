package app.tripplanner.fieldguide.logic

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LogicTest {

    @Test
    fun hhmmFormatsAndWraps() {
        assertEquals("07:30", 450.hhmm())
        assertEquals("00:00", 0.hhmm())
        assertEquals("00:15", 1455.hhmm())
        assertEquals("23:45", (-15).hhmm())
    }

    @Test
    fun minuteOfDayUsesTheDestinationZone() {
        // 20:30 UTC on 10 April is 00:30 on 11 April in Tbilisi (UTC+4).
        val now = Instant.parse("2027-04-10T20:30:00Z")
        val tbilisi = ZoneId.of("Asia/Tbilisi")
        assertEquals(30, minuteOfDay("2027-04-11", tbilisi, now))
        assertEquals(DAY_MINUTES, minuteOfDay("2027-04-10", tbilisi, now))
        assertEquals(0, minuteOfDay("2027-04-12", tbilisi, now))
        assertEquals("2027-04-11", todayIso(tbilisi, now))
    }

    @Test
    fun wakeInstantFollowsTheDayZone() {
        assertEquals(Instant.parse("2027-04-10T03:30:00Z"), wakeInstant("2027-04-10", "Asia/Tbilisi", 450))
        assertEquals(Instant.parse("2027-04-10T22:30:00Z"), wakeInstant("2027-04-11", "Asia/Tokyo", 450))
    }

    @Test
    fun wakeInsideDstGapMovesForward() {
        // Berlin skips 02:00 to 03:00 on 28 March 2027, so 02:30 becomes 03:30 local, 01:30 UTC.
        assertEquals(Instant.parse("2027-03-28T01:30:00Z"), wakeInstant("2027-03-28", "Europe/Berlin", 150))
    }

    @Test
    fun nextWakeSkipsMorningsAlreadyPast() {
        val days = listOf(
            DayWake("d1", "2027-04-10", "Asia/Tbilisi"),
            DayWake("d2", "2027-04-11", "Asia/Tbilisi"),
        )
        val afterFirst = Instant.parse("2027-04-10T05:00:00Z")
        assertEquals("d2", nextWake(days, 450, afterFirst)?.first?.dayId)
        assertEquals("d1", nextWake(days, 450, Instant.parse("2027-04-09T12:00:00Z"))?.first?.dayId)
        assertNull(nextWake(days, 450, Instant.parse("2027-04-12T00:00:00Z")))
    }

    @Test
    fun badTimezoneFallsBack() {
        assertEquals(ZoneId.of("UTC"), zoneOrDefault("Not/AZone", ZoneId.of("UTC")))
        assertEquals(ZoneId.of("UTC"), zoneOrDefault(null, ZoneId.of("UTC")))
    }

    @Test
    fun foldKeepsFirstBaseAndLastValue() {
        val rows = listOf(
            PendingChange("timeline_block", "b1", "start_minute", "630", "600"),
            PendingChange("contextual_item", "i1", "is_checked", "true", null),
            PendingChange("timeline_block", "b1", "start_minute", "690", "630"),
            PendingChange("contextual_item", "i1", "is_checked", "false", null),
            PendingChange("timeline_block", "b1", "status", "\"DONE\"", null),
        )
        val folded = foldOutbox("batch", rows).associateBy { it.mutationId }
        assertEquals(3, folded.size)
        assertEquals("690", folded.getValue("batch:b1:start_minute").value)
        assertEquals("600", folded.getValue("batch:b1:start_minute").base)
        assertEquals("false", folded.getValue("batch:i1:is_checked").value)
        assertEquals("\"DONE\"", folded.getValue("batch:b1:status").value)
        assertEquals(folded.keys, foldOutbox("batch", rows).map { it.mutationId }.toSet())
    }

    @Test
    fun collisionsFlagFlexibleBlocksOverFixedBookings() {
        val slots = listOf(
            Slot("walk", 600, 90, isFixed = false, isPending = true),
            Slot("train", 660, 120, isFixed = true, isPending = true),
            Slot("lunch", 780, 60, isFixed = false, isPending = true),
            Slot("done", 670, 30, isFixed = false, isPending = false),
        )
        assertEquals(setOf("walk", "train"), collidingIds(slots))
        assertEquals(emptySet<String>(), collidingIds(slots.filter { it.id != "walk" }))
    }
}
