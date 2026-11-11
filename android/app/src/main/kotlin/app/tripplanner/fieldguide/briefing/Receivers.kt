package app.tripplanner.fieldguide.briefing

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import app.tripplanner.fieldguide.FieldGuideApp
import app.tripplanner.fieldguide.R
import app.tripplanner.fieldguide.logic.hhmm
import app.tripplanner.fieldguide.ui.MainActivity
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val CHANNEL_ID = "briefing"
private const val NOTIFICATION_ID = 1

/** Fires at the destination wake-up time, posts the day's briefing, then arms the next morning. */
class WakeUpBriefingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        val graph = (context.applicationContext as FieldGuideApp).graph
        val dayId = intent.getStringExtra(BriefingScheduler.EXTRA_DAY_ID)
        graph.scope.launch {
            try {
                val dao = graph.db.itineraryDao()
                val tripId = graph.session.current.tripId ?: return@launch
                val day = dayId?.let { dao.day(it) }
                val trip = dao.trip(tripId)
                if (day != null && trip != null) {
                    val departure = dao.observeFirstDeparture(day.id).first()
                    val spend = dao.observeDaySpend(day.id).first()
                    val prep = dao.openPrepCount(day.id)
                    val lines = listOfNotNull(
                        departure?.let { "Leave by ${it.hhmm()}" } ?: "Nothing scheduled",
                        if (prep > 0) "$prep prep ${if (prep == 1) "item" else "items"} to check" else null,
                        if (spend > 0) "About ${spend.roundToInt()} ${trip.localCurrency} today" else null,
                    )
                    post(context, "Day ${day.dayNumber}: ${trip.title}", lines.joinToString(" · "))
                }
                BriefingScheduler.armNext(context, dao, tripId)
            } finally {
                result.finish()
            }
        }
    }

    private fun post(context: Context, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.briefing_channel), NotificationManager.IMPORTANCE_HIGH),
        )
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_briefing)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }
}

/** Alarms do not survive a reboot, and a clock or timezone change moves the wake instant. */
class RearmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        val graph = (context.applicationContext as FieldGuideApp).graph
        graph.scope.launch {
            try {
                val tripId = graph.session.current.tripId ?: return@launch
                BriefingScheduler.armNext(context, graph.db.itineraryDao(), tripId)
            } finally {
                result.finish()
            }
        }
    }
}
