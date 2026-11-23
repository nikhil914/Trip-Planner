package app.tripplanner.fieldguide.sync

import app.tripplanner.fieldguide.data.AttachmentEntity
import app.tripplanner.fieldguide.data.ContextualItemEntity
import app.tripplanner.fieldguide.data.ExpenseEntity
import app.tripplanner.fieldguide.data.TimelineBlockEntity
import app.tripplanner.fieldguide.data.TripDayEntity
import app.tripplanner.fieldguide.data.TripEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// Wire contracts from docs/architecture-blueprint.md, section 2.

@Serializable
data class DeviceInfo(
    @SerialName("install_id") val installId: String,
    val name: String,
    val platform: String = "android",
    @SerialName("app_version") val appVersion: String,
)

@Serializable
data class PairRequest(val code: String, val device: DeviceInfo)

@Serializable
data class PairResponse(
    @SerialName("trip_id") val tripId: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("sync_token") val syncToken: String,
)

@Serializable
data class ErrorBody(val error: String? = null)

@Serializable
data class TripDto(
    val id: String,
    val title: String,
    @SerialName("destination_timezone") val destinationTimezone: String,
    @SerialName("home_currency") val homeCurrency: String,
    @SerialName("local_currency") val localCurrency: String,
    @SerialName("exchange_rate") val exchangeRate: Double,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String,
    @SerialName("wake_minute") val wakeMinute: Int = 450,
    @SerialName("updated_at") val updatedAt: Long = 0,
    @SerialName("budget_minor") val budgetMinor: Long? = null,
    @SerialName("shared_ledger") val sharedLedger: Boolean = false,
) {
    fun toEntity() = TripEntity(
        id, title, destinationTimezone, homeCurrency, localCurrency, exchangeRate, startDate, endDate, wakeMinute, updatedAt,
        budgetMinor, sharedLedger,
    )
}

@Serializable
data class TripDayDto(
    val id: String,
    @SerialName("day_number") val dayNumber: Int,
    @SerialName("date_iso") val dateIso: String,
    val timezone: String,
    @SerialName("morning_prep_notes") val morningPrepNotes: JsonElement? = null,
    @SerialName("weather_summary_cache") val weatherSummaryCache: String? = null,
    @SerialName("updated_at") val updatedAt: Long = 0,
) {
    fun toEntity(tripId: String) =
        TripDayEntity(id, tripId, dayNumber, dateIso, timezone, morningPrepNotes?.toString(), weatherSummaryCache, updatedAt)
}

@Serializable
data class BlockDto(
    val id: String,
    @SerialName("day_id") val dayId: String? = null,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("start_minute") val startMinute: Int? = null,
    @SerialName("duration_minutes") val durationMinutes: Int = 60,
    @SerialName("is_fixed_time") val isFixedTime: Boolean = false,
    @SerialName("block_type") val blockType: String = "ACTIVITY",
    val title: String,
    @SerialName("subtitle_or_tip") val subtitleOrTip: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    @SerialName("google_place_id") val googlePlaceId: String? = null,
    @SerialName("google_maps_uri") val googleMapsUri: String? = null,
    @SerialName("estimated_cost_local") val estimatedCostLocal: Double? = null,
    @SerialName("booking_ref") val bookingRef: String? = null,
    @SerialName("opens_minute") val opensMinute: Int? = null,
    @SerialName("closes_minute") val closesMinute: Int? = null,
    @SerialName("end_timezone") val endTimezone: String? = null,
    @SerialName("local_script_name") val localScriptName: String? = null,
    @SerialName("phonetic_pronunciation") val phoneticPronunciation: String? = null,
    val status: String = "PENDING",
    @SerialName("updated_at") val updatedAt: Long = 0,
) {
    fun toEntity(tripId: String) = TimelineBlockEntity(
        id, tripId, dayId, sortOrder, startMinute, durationMinutes, isFixedTime, blockType, title, subtitleOrTip,
        lat, lng, googlePlaceId, googleMapsUri, estimatedCostLocal, bookingRef, opensMinute, closesMinute, endTimezone,
        localScriptName, phoneticPronunciation, status, updatedAt,
    )
}

@Serializable
data class ItemDto(
    val id: String,
    @SerialName("linked_block_id") val linkedBlockId: String? = null,
    @SerialName("day_id") val dayId: String? = null,
    val category: String,
    val title: String,
    @SerialName("shop_or_venue_name") val shopOrVenueName: String? = null,
    @SerialName("price_benchmark_note") val priceBenchmarkNote: String? = null,
    @SerialName("estimated_cost_local") val estimatedCostLocal: Double? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    @SerialName("google_place_id") val googlePlaceId: String? = null,
    @SerialName("distance_from_block_meters") val distanceFromBlockMeters: Int? = null,
    @SerialName("local_script_text") val localScriptText: String? = null,
    @SerialName("phonetic_pronunciation") val phoneticPronunciation: String? = null,
    @SerialName("is_checked") val isChecked: Boolean = false,
    @SerialName("updated_at") val updatedAt: Long = 0,
) {
    fun toEntity(tripId: String) = ContextualItemEntity(
        id, tripId, linkedBlockId, dayId, category, title, shopOrVenueName, priceBenchmarkNote, estimatedCostLocal,
        lat, lng, googlePlaceId, distanceFromBlockMeters, localScriptText, phoneticPronunciation, isChecked, updatedAt,
    )
}

@Serializable
data class AttachmentDto(
    val id: String,
    @SerialName("block_id") val blockId: String,
    @SerialName("file_name") val fileName: String,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("size_bytes") val sizeBytes: Long,
    val sha256: String,
    @SerialName("download_path") val downloadPath: String,
) {
    fun toEntity(tripId: String) = AttachmentEntity(id, tripId, blockId, fileName, mimeType, sizeBytes, sha256, downloadPath)
}

/**
 * One expense row on the wire. The phone pushes it whole (outbox field "row") and gets it back in a pull.
 * A row with [deletedAt] set was deleted on the phone that owns it.
 */
@Serializable
data class ExpenseDto(
    val id: String? = null,
    val mine: Boolean = true,
    val kind: String,
    @SerialName("day_id") val dayId: String? = null,
    @SerialName("block_id") val blockId: String? = null,
    @SerialName("occurred_at") val occurredAt: Long,
    @SerialName("amount_minor") val amountMinor: Long,
    val currency: String,
    @SerialName("rate_to_home") val rateToHome: Double,
    @SerialName("home_minor") val homeMinor: Long,
    @SerialName("billed_home_minor") val billedHomeMinor: Long? = null,
    val method: String,
    val category: String,
    val merchant: String? = null,
    val note: String? = null,
    val prepaid: Boolean = false,
    @SerialName("paid_by") val paidBy: String? = null,
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("deleted_at") val deletedAt: Long? = null,
) {
    fun toEntity(tripId: String, rowId: String = id ?: "") = ExpenseEntity(
        rowId, tripId, mine, kind, dayId, blockId, occurredAt, amountMinor, currency, rateToHome, homeMinor, billedHomeMinor,
        method, category, merchant, note, prepaid, paidBy, updatedAt,
    )

    companion object {
        fun of(e: ExpenseEntity, deletedAt: Long? = null) = ExpenseDto(
            id = null, kind = e.kind, dayId = e.dayId, blockId = e.blockId, occurredAt = e.occurredAt, amountMinor = e.amountMinor,
            currency = e.currency, rateToHome = e.rateToHome, homeMinor = e.homeMinor, billedHomeMinor = e.billedHomeMinor,
            method = e.method, category = e.category, merchant = e.merchant, note = e.note, prepaid = e.prepaid, paidBy = e.paidBy,
            updatedAt = e.updatedAt, deletedAt = deletedAt,
        )
    }
}

@Serializable
data class DeletedIds(
    @SerialName("trip_days") val tripDays: List<String> = emptyList(),
    @SerialName("timeline_blocks") val timelineBlocks: List<String> = emptyList(),
    @SerialName("contextual_items") val contextualItems: List<String> = emptyList(),
    val attachments: List<String> = emptyList(),
)

@Serializable
data class PullResponse(
    @SerialName("trip_id") val tripId: String,
    val rev: Long,
    val full: Boolean = false,
    val trip: TripDto? = null,
    @SerialName("trip_days") val tripDays: List<TripDayDto> = emptyList(),
    @SerialName("timeline_blocks") val timelineBlocks: List<BlockDto> = emptyList(),
    @SerialName("contextual_items") val contextualItems: List<ItemDto> = emptyList(),
    val attachments: List<AttachmentDto> = emptyList(),
    val expenses: List<ExpenseDto> = emptyList(),
    val deleted: DeletedIds = DeletedIds(),
)

@Serializable
data class Mutation(
    @SerialName("mutation_id") val mutationId: String,
    val entity: String,
    val id: String,
    val field: String,
    val value: JsonElement,
    val base: JsonElement? = null,
)

@Serializable
data class PushRequest(
    @SerialName("batch_id") val batchId: String,
    val mutations: List<Mutation>,
)

@Serializable
data class MutationResult(
    @SerialName("mutation_id") val mutationId: String,
    val status: String,
    @SerialName("server_value") val serverValue: JsonElement? = null,
)

@Serializable
data class PushResponse(val rev: Long = 0, val results: List<MutationResult> = emptyList())
