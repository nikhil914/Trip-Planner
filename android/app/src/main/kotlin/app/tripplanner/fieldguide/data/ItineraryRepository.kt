package app.tripplanner.fieldguide.data

import android.content.Context
import androidx.room.withTransaction
import app.tripplanner.fieldguide.briefing.BriefingScheduler
import app.tripplanner.fieldguide.logic.minuteOfDay
import app.tripplanner.fieldguide.logic.zoneOrDefault
import app.tripplanner.fieldguide.sync.DeviceInfo
import app.tripplanner.fieldguide.sync.OutboxWorker
import app.tripplanner.fieldguide.sync.PairRequest
import app.tripplanner.fieldguide.sync.SyncApi

/** Every write lands in Room and the outbox in one transaction. The UI reads Room only. */
class ItineraryRepository(
    private val context: Context,
    private val db: FieldDb,
    private val api: SyncApi,
    private val session: Session,
    private val vault: Vault,
) {
    private val dao = db.itineraryDao()

    private fun schedulePush() {
        val s = session.current
        if (s.canSync) OutboxWorker.enqueue(context, s.wifiOnly)
    }

    suspend fun checkItem(id: String, checked: Boolean) {
        dao.checkItem(id, checked, System.currentTimeMillis())
        schedulePush()
    }

    suspend fun markBlock(id: String, status: String) {
        dao.markBlock(id, status, System.currentTimeMillis())
        schedulePush()
    }

    private fun nowMinute(day: TripDayEntity): Int = minuteOfDay(day.dateIso, zoneOrDefault(day.timezone))

    suspend fun previewDelay(day: TripDayEntity, delta: Int): ShiftPreview {
        val now = nowMinute(day)
        return ShiftPreview(dao.collisions(day.id, now, delta), dao.lateEnds(day.id, now, delta))
    }

    suspend fun delay(day: TripDayEntity, delta: Int): Int {
        val moved = dao.cascadeShift(day.id, nowMinute(day), delta, System.currentTimeMillis())
        if (moved > 0) schedulePush()
        return moved
    }

    suspend fun pair(serverUrl: String, code: String, deviceName: String, appVersion: String) {
        val response = api.pair(serverUrl, PairRequest(code, DeviceInfo(session.installId, deviceName, appVersion = appVersion)))
        db.withTransaction {
            dao.clearTripTables()
            dao.clearOutbox()
        }
        vault.clear()
        session.paired(serverUrl, response.tripId, response.syncToken)
        pull()
    }

    /** Runs on app launch and on swipe-to-refresh only. Returns how many attachment downloads failed. */
    suspend fun pull(): Int {
        val s = session.current
        val tripId = s.tripId ?: return 0
        if (!s.canSync) return 0

        val delta = api.pull(tripId, s.rev)
        val now = System.currentTimeMillis()
        db.withTransaction {
            if (delta.full) dao.clearTripTables()
            delta.trip?.let { dao.upsertTrip(it.toEntity()) }
            dao.upsertDays(delta.tripDays.map { it.toEntity(tripId) })
            dao.upsertBlocks(delta.timelineBlocks.map { it.toEntity(tripId) })
            dao.upsertItems(delta.contextualItems.map { it.toEntity(tripId) })
            dao.upsertAttachments(delta.attachments.map { it.toEntity(tripId) })
            dao.deleteDays(delta.deleted.tripDays)
            dao.deleteBlocks(delta.deleted.timelineBlocks)
            dao.deleteItems(delta.deleted.contextualItems)
            dao.deleteAttachments(delta.deleted.attachments)
            // Local edits the server has not acknowledged stay on top of the rows just written.
            dao.pendingOutbox().forEach { dao.applyField(it.entityId, it.field, it.value, now) }
        }
        session.setRev(delta.rev)

        val failed = vault.sync(dao.attachments(tripId))
        BriefingScheduler.armNext(context, dao, tripId)
        if (dao.pendingOutbox().isNotEmpty()) OutboxWorker.enqueue(context, s.wifiOnly, delayMinutes = 0)
        return failed
    }

    suspend fun loadDemo() {
        val sample = SampleTrip.build()
        db.withTransaction {
            dao.clearTripTables()
            dao.clearOutbox()
            dao.upsertTrip(sample.trip)
            dao.upsertDays(sample.days)
            dao.upsertBlocks(sample.blocks)
            dao.upsertItems(sample.items)
        }
        vault.clear()
        session.demo(sample.trip.id)
        BriefingScheduler.armNext(context, dao, sample.trip.id)
    }

    suspend fun unpair() {
        OutboxWorker.cancel(context)
        BriefingScheduler.cancel(context)
        db.withTransaction {
            dao.clearTripTables()
            dao.clearOutbox()
        }
        vault.clear()
        session.clear()
    }
}
