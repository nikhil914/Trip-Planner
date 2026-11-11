package app.tripplanner.fieldguide.logic

data class Slot(
    val id: String,
    val startMinute: Int,
    val durationMinutes: Int,
    val isFixed: Boolean,
    val isPending: Boolean,
)

/** Ids of every pending flexible block that overlaps a pending fixed booking, plus those bookings. */
fun collidingIds(slots: List<Slot>): Set<String> {
    val pending = slots.filter { it.isPending }
    val fixed = pending.filter { it.isFixed }
    val out = mutableSetOf<String>()
    for (b in pending) {
        if (b.isFixed) continue
        for (f in fixed) {
            val overlaps = b.startMinute < f.startMinute + f.durationMinutes && b.startMinute + b.durationMinutes > f.startMinute
            if (overlaps) {
                out += b.id
                out += f.id
            }
        }
    }
    return out
}
