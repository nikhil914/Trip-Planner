package app.tripplanner.fieldguide.sync

import android.content.Context
import androidx.room.withTransaction
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.tripplanner.fieldguide.FieldGuideApp
import app.tripplanner.fieldguide.data.Entities
import app.tripplanner.fieldguide.logic.PendingChange
import app.tripplanner.fieldguide.logic.foldOutbox
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Pushes local edits in one batch. A batch keeps its id until the server answers, so a retry after a
 * timeout or a cancelled run resends the same mutation ids and the server answers "duplicate".
 */
class OutboxWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val graph = (applicationContext as FieldGuideApp).graph
        val dao = graph.db.itineraryDao()
        val session = graph.session.current
        if (!session.canSync) return Result.success()
        val tripId = session.tripId ?: return Result.success()

        while (true) {
            // Resend a batch that failed earlier before forming a new one.
            var batchId = dao.oldestBatchId()
            if (batchId == null) {
                batchId = UUID.randomUUID().toString()
                if (dao.stampBatch(batchId) == 0) return Result.success()
            }

            val folded = foldOutbox(
                batchId,
                dao.batch(batchId).map { PendingChange(it.entity, it.entityId, it.field, it.value, it.base) },
            )
            val mutations = folded.map {
                Mutation(
                    mutationId = it.mutationId,
                    entity = it.entity,
                    id = it.entityId,
                    field = it.field,
                    value = graph.api.json.parseToJsonElement(it.value),
                    base = it.base?.let(graph.api.json::parseToJsonElement),
                )
            }

            val response = try {
                graph.api.push(tripId, PushRequest(batchId, mutations))
            } catch (e: HttpStatusException) {
                return when {
                    e.code == 401 -> {
                        graph.session.markRevoked()
                        Result.failure()
                    }
                    e.code == 429 || e.code >= 500 -> Result.retry()
                    else -> {
                        // The server rejected the batch itself. Drop it so it cannot block later edits.
                        dao.deleteBatch(batchId)
                        Result.failure()
                    }
                }
            } catch (e: IOException) {
                return Result.retry()
            }

            val byId = mutations.associateBy { it.mutationId }
            val now = System.currentTimeMillis()
            graph.db.withTransaction {
                for (result in response.results) {
                    val mutation = byId[result.mutationId] ?: continue
                    when (result.status) {
                        "conflict" -> result.serverValue?.let { dao.applyField(mutation.id, mutation.field, it.toString(), now) }
                        "gone" ->
                            if (mutation.entity == Entities.BLOCK) dao.deleteBlocks(listOf(mutation.id))
                            else dao.deleteItems(listOf(mutation.id))
                    }
                }
                dao.deleteBatch(batchId)
            }
            if (response.results.any { it.status == "conflict" }) graph.notices.tryEmit("Schedule changed on web. Your times were updated.")
        }
    }

    companion object {
        private const val WORK_NAME = "outbox-push"

        /** One unique job with a debounce, so a burst of taps is a single radio wake-up. */
        fun enqueue(context: Context, wifiOnly: Boolean, delayMinutes: Long = 10) {
            val request = OneTimeWorkRequestBuilder<OutboxWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build(),
                )
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
