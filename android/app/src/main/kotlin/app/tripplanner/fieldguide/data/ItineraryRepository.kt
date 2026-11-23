package app.tripplanner.fieldguide.data

import android.content.Context
import androidx.room.withTransaction
import app.tripplanner.fieldguide.briefing.BriefingScheduler
import app.tripplanner.fieldguide.logic.minuteOfDay
import app.tripplanner.fieldguide.logic.zoneOrDefault
import app.tripplanner.fieldguide.sync.DeviceInfo
import app.tripplanner.fieldguide.sync.ExpenseDto
import app.tripplanner.fieldguide.sync.OutboxWorker
import app.tripplanner.fieldguide.sync.PairRequest
import app.tripplanner.fieldguide.sync.SyncApi

enum class PairPhase { CONNECTING, PLAN, FILES, READY }

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

    // ---- Money ----

    private fun rowJson(row: ExpenseEntity, deletedAt: Long? = null): String =
        api.json.encodeToString(ExpenseDto.serializer(), ExpenseDto.of(row, deletedAt))

    /** Saves a new or edited spend and queues the whole row for upload. */
    suspend fun saveExpense(row: ExpenseEntity) {
        val stamped = row.copy(updatedAt = System.currentTimeMillis())
        db.withTransaction {
            dao.upsertExpenses(listOf(stamped))
            dao.appendOutbox(listOf(OutboxEntity(entity = Entities.EXPENSE, entityId = row.id, field = "row", value = rowJson(stamped), createdAt = stamped.updatedAt)))
        }
        schedulePush()
    }

    suspend fun deleteExpense(row: ExpenseEntity) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            dao.deleteExpenses(listOf(row.id))
            dao.appendOutbox(
                listOf(OutboxEntity(entity = Entities.EXPENSE, entityId = row.id, field = "row", value = rowJson(row.copy(updatedAt = now), now), createdAt = now)),
            )
        }
        schedulePush()
    }

    suspend fun setBudget(tripId: String, minor: Long?) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            dao.setBudget(minor)
            dao.appendOutbox(listOf(OutboxEntity(entity = Entities.TRIP, entityId = tripId, field = "budget_minor", value = minor?.toString() ?: "null", createdAt = now)))
        }
        schedulePush()
    }

    suspend fun setSharedLedger(tripId: String, shared: Boolean) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            dao.setSharedLedger(shared)
            if (!shared) dao.deleteOthersExpenses()
            dao.appendOutbox(listOf(OutboxEntity(entity = Entities.TRIP, entityId = tripId, field = "shared_ledger", value = shared.toString(), createdAt = now)))
        }
        // Upload the switch at once: the other traveller's rows arrive with the next pull after it lands.
        val s = session.current
        if (s.canSync) OutboxWorker.enqueue(context, s.wifiOnly, delayMinutes = 0)
    }

    /** Re-applies one queued local edit on top of rows a pull just wrote. */
    private suspend fun reapply(change: OutboxEntity, tripId: String, now: Long) {
        if (change.entity == Entities.EXPENSE && change.field == "row") {
            val dto = runCatching { api.json.decodeFromString(ExpenseDto.serializer(), change.value) }.getOrNull() ?: return
            if (dto.deletedAt != null) dao.deleteExpenses(listOf(change.entityId))
            else dao.upsertExpenses(listOf(dto.toEntity(tripId, change.entityId)))
        } else {
            dao.applyField(change.entityId, change.field, change.value, now)
        }
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

    suspend fun pair(serverUrl: String, code: String, deviceName: String, appVersion: String, onPhase: (PairPhase) -> Unit = {}) {
        onPhase(PairPhase.CONNECTING)
        val response = api.pair(serverUrl, PairRequest(code, DeviceInfo(session.installId, deviceName, appVersion = appVersion)))
        db.withTransaction {
            dao.clearTripTables()
            dao.clearOutbox()
        }
        vault.clear()
        session.paired(serverUrl, response.tripId, response.syncToken)
        pull(onPhase)
        onPhase(PairPhase.READY)
    }

    /** Runs on app launch and on swipe-to-refresh only. Returns how many attachment downloads failed. */
    suspend fun pull(onPhase: (PairPhase) -> Unit = {}): Int {
        val s = session.current
        val tripId = s.tripId ?: return 0
        if (!s.canSync) return 0

        onPhase(PairPhase.PLAN)
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
            val (gone, live) = delta.expenses.partition { it.deletedAt != null }
            dao.deleteExpenses(gone.mapNotNull { it.id })
            dao.upsertExpenses(live.filter { it.id != null }.map { it.toEntity(tripId) })
            // Local edits the server has not acknowledged stay on top of the rows just written.
            dao.pendingOutbox().forEach { reapply(it, tripId, now) }
            if (dao.trip(tripId)?.sharedLedger != true) dao.deleteOthersExpenses()
        }
        session.setRev(delta.rev)

        onPhase(PairPhase.FILES)
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
            dao.upsertExpenses(sample.expenses)
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
