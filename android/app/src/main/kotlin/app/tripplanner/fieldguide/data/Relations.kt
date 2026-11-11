package app.tripplanner.fieldguide.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Relation

data class BlockWithContext(
    @Embedded val block: TimelineBlockEntity,
    /** Nearby errands and food, linked on the server at save time. */
    @Relation(parentColumn = "id", entityColumn = "linked_block_id")
    val items: List<ContextualItemEntity>,
    @Relation(parentColumn = "id", entityColumn = "block_id")
    val attachments: List<AttachmentEntity>,
)

data class BlockStart(
    val id: String,
    @ColumnInfo(name = "start_minute") val startMinute: Int,
)

data class Collision(
    val fixedId: String,
    val fixedTitle: String,
    val fixedStart: Int,
    val blockId: String,
    val blockTitle: String,
    val overlapMinutes: Int,
)

data class LateEnd(
    val id: String,
    val title: String,
    val endMinute: Int,
    val closesMinute: Int?,
)

data class ShiftPreview(val collisions: List<Collision>, val lateEnds: List<LateEnd>) {
    val isClean: Boolean get() = collisions.isEmpty() && lateEnds.isEmpty()
}
