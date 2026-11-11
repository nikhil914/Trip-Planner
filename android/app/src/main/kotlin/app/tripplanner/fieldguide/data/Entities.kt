package app.tripplanner.fieldguide.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Tables carry indices but no foreign keys, so a sync delta can be applied in any row order.

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "destination_timezone") val destinationTimezone: String,
    @ColumnInfo(name = "home_currency") val homeCurrency: String,
    @ColumnInfo(name = "local_currency") val localCurrency: String,
    @ColumnInfo(name = "exchange_rate") val exchangeRate: Double,
    @ColumnInfo(name = "start_date") val startDate: String,
    @ColumnInfo(name = "end_date") val endDate: String,
    @ColumnInfo(name = "wake_minute") val wakeMinute: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(tableName = "trip_days", indices = [Index("trip_id", "date_iso")])
data class TripDayEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "trip_id") val tripId: String,
    @ColumnInfo(name = "day_number") val dayNumber: Int,
    @ColumnInfo(name = "date_iso") val dateIso: String,
    val timezone: String,
    @ColumnInfo(name = "morning_prep_notes") val morningPrepNotes: String?,
    @ColumnInfo(name = "weather_summary_cache") val weatherSummaryCache: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(tableName = "timeline_blocks", indices = [Index("trip_id"), Index("day_id", "start_minute")])
data class TimelineBlockEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "trip_id") val tripId: String,
    @ColumnInfo(name = "day_id") val dayId: String?,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "start_minute") val startMinute: Int?,
    @ColumnInfo(name = "duration_minutes") val durationMinutes: Int,
    @ColumnInfo(name = "is_fixed_time") val isFixedTime: Boolean,
    @ColumnInfo(name = "block_type") val blockType: String,
    val title: String,
    @ColumnInfo(name = "subtitle_or_tip") val subtitleOrTip: String?,
    val lat: Double?,
    val lng: Double?,
    @ColumnInfo(name = "google_place_id") val googlePlaceId: String?,
    @ColumnInfo(name = "google_maps_uri") val googleMapsUri: String?,
    @ColumnInfo(name = "estimated_cost_local") val estimatedCostLocal: Double?,
    @ColumnInfo(name = "booking_ref") val bookingRef: String?,
    @ColumnInfo(name = "opens_minute") val opensMinute: Int?,
    @ColumnInfo(name = "closes_minute") val closesMinute: Int?,
    @ColumnInfo(name = "end_timezone") val endTimezone: String?,
    @ColumnInfo(name = "local_script_name") val localScriptName: String?,
    @ColumnInfo(name = "phonetic_pronunciation") val phoneticPronunciation: String?,
    val status: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "contextual_items",
    indices = [Index("trip_id"), Index("linked_block_id"), Index("day_id", "category")],
)
data class ContextualItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "trip_id") val tripId: String,
    @ColumnInfo(name = "linked_block_id") val linkedBlockId: String?,
    @ColumnInfo(name = "day_id") val dayId: String?,
    val category: String,
    val title: String,
    @ColumnInfo(name = "shop_or_venue_name") val shopOrVenueName: String?,
    @ColumnInfo(name = "price_benchmark_note") val priceBenchmarkNote: String?,
    @ColumnInfo(name = "estimated_cost_local") val estimatedCostLocal: Double?,
    val lat: Double?,
    val lng: Double?,
    @ColumnInfo(name = "google_place_id") val googlePlaceId: String?,
    @ColumnInfo(name = "distance_from_block_meters") val distanceFromBlockMeters: Int?,
    @ColumnInfo(name = "local_script_text") val localScriptText: String?,
    @ColumnInfo(name = "phonetic_pronunciation") val phoneticPronunciation: String?,
    @ColumnInfo(name = "is_checked") val isChecked: Boolean,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** The file itself lives at filesDir/vault/<id>, so no path is stored. */
@Entity(tableName = "attachments", indices = [Index("block_id")])
data class AttachmentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "trip_id") val tripId: String,
    @ColumnInfo(name = "block_id") val blockId: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    val sha256: String,
    @ColumnInfo(name = "download_path") val downloadPath: String,
)

/** Append-only log of local edits waiting to be pushed. */
@Entity(tableName = "outbox", indices = [Index("batch_id")])
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    @ColumnInfo(name = "batch_id") val batchId: String? = null,
    val entity: String,
    @ColumnInfo(name = "entity_id") val entityId: String,
    val field: String,
    /** JSON literal: 870, true, "DONE". */
    val value: String,
    /** Previous value, sent with start_minute so the server can compare-and-swap. */
    val base: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

object Entities {
    const val BLOCK = "timeline_block"
    const val ITEM = "contextual_item"
}

object Status {
    const val PENDING = "PENDING"
    const val DONE = "DONE"
    const val SKIPPED = "SKIPPED"
}

object Category {
    const val TO_BUY = "TO_BUY"
    const val EAT = "EAT_SUGGESTION"
    const val PREP = "MORNING_PREP"
}
