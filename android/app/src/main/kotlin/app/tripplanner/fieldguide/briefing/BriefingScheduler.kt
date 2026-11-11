package app.tripplanner.fieldguide.briefing

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.tripplanner.fieldguide.data.ItineraryDao
import app.tripplanner.fieldguide.logic.DayWake
import app.tripplanner.fieldguide.logic.nextWake
import java.time.Instant

/**
 * Keeps exactly one alarm: the next trip morning. The receiver arms the following day when it fires,
 * and this is called again on boot, clock or timezone change, app update, each pull and each app open.
 */
object BriefingScheduler {
    const val EXTRA_DAY_ID = "day_id"
    private const val REQUEST_CODE = 7301

    private fun pendingIntent(context: Context, dayId: String?): PendingIntent {
        val intent = Intent(context, WakeUpBriefingReceiver::class.java).putExtra(EXTRA_DAY_ID, dayId)
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun canScheduleExact(context: Context): Boolean {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
    }

    suspend fun armNext(context: Context, dao: ItineraryDao, tripId: String) {
        val trip = dao.trip(tripId)
        val next = trip?.let {
            nextWake(dao.days(tripId).map { d -> DayWake(d.id, d.dateIso, d.timezone) }, it.wakeMinute, Instant.now())
        }
        if (next == null) {
            cancel(context)
            return
        }
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(context, next.first.dayId)
        val at = next.second.toEpochMilli()
        try {
            if (canScheduleExact(context)) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } catch (_: SecurityException) {
            // The exact-alarm grant was withdrawn between the check and the call.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context, null))
    }
}
