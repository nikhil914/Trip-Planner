package app.tripplanner.fieldguide.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ItineraryDao {

    // ---- Reads ----

    @Query("SELECT * FROM trips WHERE id = :tripId")
    abstract fun observeTrip(tripId: String): Flow<TripEntity?>

    @Query("SELECT * FROM trips WHERE id = :tripId")
    abstract suspend fun trip(tripId: String): TripEntity?

    @Query("SELECT * FROM trip_days WHERE trip_id = :tripId ORDER BY day_number")
    abstract fun observeDays(tripId: String): Flow<List<TripDayEntity>>

    @Query("SELECT * FROM trip_days WHERE trip_id = :tripId ORDER BY day_number")
    abstract suspend fun days(tripId: String): List<TripDayEntity>

    @Query("SELECT * FROM trip_days WHERE id = :dayId")
    abstract suspend fun day(dayId: String): TripDayEntity?

    @Transaction
    @Query("SELECT * FROM timeline_blocks WHERE day_id = :dayId AND start_minute IS NOT NULL ORDER BY start_minute, sort_order")
    abstract fun observeAgenda(dayId: String): Flow<List<BlockWithContext>>

    @Query("SELECT * FROM contextual_items WHERE day_id = :dayId AND category = 'MORNING_PREP' ORDER BY title")
    abstract fun observePrep(dayId: String): Flow<List<ContextualItemEntity>>

    @Query(
        """
        SELECT MIN(start_minute) FROM timeline_blocks
        WHERE day_id = :dayId AND status = 'PENDING' AND block_type <> 'HOTEL'
        """,
    )
    abstract fun observeFirstDeparture(dayId: String): Flow<Int?>

    @Query(
        """
        SELECT
          COALESCE((SELECT SUM(estimated_cost_local) FROM timeline_blocks
                    WHERE day_id = :dayId AND status <> 'SKIPPED'), 0)
        + COALESCE((SELECT SUM(estimated_cost_local) FROM contextual_items
                    WHERE day_id = :dayId AND category <> 'MORNING_PREP'), 0)
        """,
    )
    abstract fun observeDaySpend(dayId: String): Flow<Double>

    @Query("SELECT COUNT(*) FROM contextual_items WHERE day_id = :dayId AND category = 'MORNING_PREP' AND is_checked = 0")
    abstract suspend fun openPrepCount(dayId: String): Int

    @Query("SELECT COUNT(*) FROM outbox")
    abstract fun observeOutboxSize(): Flow<Int>

    // ---- Cascade delay ----
    // A block is shiftable when it is flexible, still pending, and has not ended by :nowMinute.

    @Query(
        """
        SELECT id, start_minute FROM timeline_blocks
        WHERE day_id = :dayId AND is_fixed_time = 0 AND status = 'PENDING'
          AND start_minute IS NOT NULL AND start_minute + duration_minutes > :nowMinute
        """,
    )
    abstract suspend fun shiftable(dayId: String, nowMinute: Int): List<BlockStart>

    @Query(
        """
        UPDATE timeline_blocks
        SET start_minute = start_minute + :delta, updated_at = :now
        WHERE day_id = :dayId AND is_fixed_time = 0 AND status = 'PENDING'
          AND start_minute IS NOT NULL AND start_minute + duration_minutes > :nowMinute
        """,
    )
    abstract suspend fun applyShift(dayId: String, nowMinute: Int, delta: Int, now: Long): Int

    /** Flexible blocks that would overlap a fixed booking after moving by :delta. Pass 0 for the current state. */
    @Query(
        """
        SELECT f.id AS fixedId, f.title AS fixedTitle, f.start_minute AS fixedStart,
               b.id AS blockId, b.title AS blockTitle,
               MIN(b.start_minute + :delta + b.duration_minutes, f.start_minute + f.duration_minutes)
             - MAX(b.start_minute + :delta, f.start_minute) AS overlapMinutes
        FROM timeline_blocks f
        JOIN timeline_blocks b ON b.day_id = f.day_id
        WHERE f.day_id = :dayId
          AND f.is_fixed_time = 1 AND f.status = 'PENDING' AND f.start_minute IS NOT NULL
          AND b.is_fixed_time = 0 AND b.status = 'PENDING' AND b.start_minute IS NOT NULL
          AND b.start_minute + b.duration_minutes > :nowMinute
          AND b.start_minute + :delta < f.start_minute + f.duration_minutes
          AND b.start_minute + :delta + b.duration_minutes > f.start_minute
        ORDER BY f.start_minute, b.start_minute
        """,
    )
    abstract suspend fun collisions(dayId: String, nowMinute: Int, delta: Int): List<Collision>

    /** Blocks that would run past midnight or past the venue's closing time. */
    @Query(
        """
        SELECT id, title, start_minute + :delta + duration_minutes AS endMinute, closes_minute AS closesMinute
        FROM timeline_blocks
        WHERE day_id = :dayId AND is_fixed_time = 0 AND status = 'PENDING'
          AND start_minute IS NOT NULL AND start_minute + duration_minutes > :nowMinute
          AND (start_minute + :delta + duration_minutes > 1440
               OR (closes_minute IS NOT NULL AND start_minute + :delta + duration_minutes > closes_minute))
        ORDER BY start_minute
        """,
    )
    abstract suspend fun lateEnds(dayId: String, nowMinute: Int, delta: Int): List<LateEnd>

    /** Moves every shiftable block and records one outbox entry per block. Returns how many moved. */
    @Transaction
    open suspend fun cascadeShift(dayId: String, nowMinute: Int, delta: Int, now: Long): Int {
        val before = shiftable(dayId, nowMinute)
        if (before.isEmpty()) return 0
        applyShift(dayId, nowMinute, delta, now)
        appendOutbox(
            before.map {
                OutboxEntity(
                    entity = Entities.BLOCK,
                    entityId = it.id,
                    field = "start_minute",
                    value = (it.startMinute + delta).toString(),
                    base = it.startMinute.toString(),
                    createdAt = now,
                )
            },
        )
        return before.size
    }

    // ---- Check-offs and status ----

    @Query("UPDATE contextual_items SET is_checked = :checked, updated_at = :now WHERE id = :id")
    abstract suspend fun setChecked(id: String, checked: Boolean, now: Long)

    @Query("UPDATE timeline_blocks SET status = :status, updated_at = :now WHERE id = :id")
    abstract suspend fun setStatus(id: String, status: String, now: Long)

    @Query("UPDATE timeline_blocks SET start_minute = :minute, updated_at = :now WHERE id = :id")
    abstract suspend fun setStart(id: String, minute: Int, now: Long)

    @Transaction
    open suspend fun checkItem(id: String, checked: Boolean, now: Long) {
        setChecked(id, checked, now)
        appendOutbox(
            listOf(OutboxEntity(entity = Entities.ITEM, entityId = id, field = "is_checked", value = checked.toString(), createdAt = now)),
        )
    }

    @Transaction
    open suspend fun markBlock(id: String, status: String, now: Long) {
        setStatus(id, status, now)
        appendOutbox(
            listOf(OutboxEntity(entity = Entities.BLOCK, entityId = id, field = "status", value = "\"$status\"", createdAt = now)),
        )
    }

    /** Writes one field from a JSON literal: a server conflict value, or an outbox entry re-applied after a pull. */
    open suspend fun applyField(id: String, field: String, json: String, now: Long) {
        when (field) {
            "is_checked" -> setChecked(id, json == "true", now)
            "status" -> setStatus(id, json.trim('"'), now)
            "start_minute" -> json.toIntOrNull()?.let { setStart(id, it, now) }
        }
    }

    // ---- Outbox ----

    @Insert
    abstract suspend fun appendOutbox(rows: List<OutboxEntity>)

    @Query("SELECT batch_id FROM outbox WHERE batch_id IS NOT NULL ORDER BY seq LIMIT 1")
    abstract suspend fun oldestBatchId(): String?

    @Query("UPDATE outbox SET batch_id = :batchId WHERE batch_id IS NULL")
    abstract suspend fun stampBatch(batchId: String): Int

    @Query("SELECT * FROM outbox WHERE batch_id = :batchId ORDER BY seq")
    abstract suspend fun batch(batchId: String): List<OutboxEntity>

    @Query("SELECT * FROM outbox ORDER BY seq")
    abstract suspend fun pendingOutbox(): List<OutboxEntity>

    @Query("DELETE FROM outbox WHERE batch_id = :batchId")
    abstract suspend fun deleteBatch(batchId: String)

    // ---- Applying a pull ----

    @Upsert
    abstract suspend fun upsertTrip(row: TripEntity)

    @Upsert
    abstract suspend fun upsertDays(rows: List<TripDayEntity>)

    @Upsert
    abstract suspend fun upsertBlocks(rows: List<TimelineBlockEntity>)

    @Upsert
    abstract suspend fun upsertItems(rows: List<ContextualItemEntity>)

    @Upsert
    abstract suspend fun upsertAttachments(rows: List<AttachmentEntity>)

    @Query("DELETE FROM trip_days WHERE id IN (:ids)")
    abstract suspend fun deleteDays(ids: List<String>)

    @Query("DELETE FROM timeline_blocks WHERE id IN (:ids)")
    abstract suspend fun deleteBlocks(ids: List<String>)

    @Query("DELETE FROM contextual_items WHERE id IN (:ids)")
    abstract suspend fun deleteItems(ids: List<String>)

    @Query("DELETE FROM attachments WHERE id IN (:ids)")
    abstract suspend fun deleteAttachments(ids: List<String>)

    @Query("SELECT * FROM attachments WHERE trip_id = :tripId")
    abstract suspend fun attachments(tripId: String): List<AttachmentEntity>

    @Query("DELETE FROM trips")
    abstract suspend fun clearTrips()

    @Query("DELETE FROM trip_days")
    abstract suspend fun clearDays()

    @Query("DELETE FROM timeline_blocks")
    abstract suspend fun clearBlocks()

    @Query("DELETE FROM contextual_items")
    abstract suspend fun clearItems()

    @Query("DELETE FROM attachments")
    abstract suspend fun clearAttachments()

    @Query("DELETE FROM outbox")
    abstract suspend fun clearOutbox()

    /** Drops synced rows before a full snapshot. The outbox is kept so unsent edits survive. */
    @Transaction
    open suspend fun clearTripTables() {
        clearTrips()
        clearDays()
        clearBlocks()
        clearItems()
        clearAttachments()
    }
}
