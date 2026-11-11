package app.tripplanner.fieldguide.logic

data class PendingChange(
    val entity: String,
    val entityId: String,
    val field: String,
    val value: String,
    val base: String?,
)

data class FoldedChange(
    val mutationId: String,
    val entity: String,
    val entityId: String,
    val field: String,
    val value: String,
    val base: String?,
)

/**
 * Collapses a batch to one change per row and field. The first entry supplies the base the server
 * compares against, the last supplies the value. Ids are derived from the batch id, so a resend of
 * the same batch produces the same ids and the server can drop duplicates.
 */
fun foldOutbox(batchId: String, rows: List<PendingChange>): List<FoldedChange> =
    rows.groupBy { it.entityId to it.field }.map { (key, changes) ->
        FoldedChange(
            mutationId = "$batchId:${key.first}:${key.second}",
            entity = changes.first().entity,
            entityId = key.first,
            field = key.second,
            value = changes.last().value,
            base = changes.first().base,
        )
    }
