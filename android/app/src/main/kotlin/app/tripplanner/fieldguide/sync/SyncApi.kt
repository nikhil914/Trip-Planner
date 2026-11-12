package app.tripplanner.fieldguide.sync

import app.tripplanner.fieldguide.data.Session
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class HttpStatusException(val code: Int, val error: String?) : IOException("HTTP $code${error?.let { " $it" } ?: ""}")

class NotPairedException : IOException("This phone is not paired with a server")

/** The three sync calls plus attachment download. Nothing here runs unless the user or the outbox asks. */
class SyncApi(private val session: Session) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    private val jsonType = "application/json".toMediaType()

    private fun authed(path: String): Request.Builder {
        val s = session.current
        val base = s.serverUrl ?: throw NotPairedException()
        val token = s.token ?: throw NotPairedException()
        return Request.Builder().url(base + path).header("Authorization", "Bearer $token")
    }

    private suspend fun send(request: Request): String = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val error = runCatching { json.decodeFromString(ErrorBody.serializer(), body).error }.getOrNull()
                throw HttpStatusException(response.code, error)
            }
            body
        }
    }

    suspend fun pair(serverUrl: String, request: PairRequest): PairResponse {
        val body = json.encodeToString(PairRequest.serializer(), request).toRequestBody(jsonType)
        val text = send(Request.Builder().url("$serverUrl/api/pair").post(body).build())
        return json.decodeFromString(PairResponse.serializer(), text)
    }

    suspend fun pull(tripId: String, since: Long): PullResponse {
        val text = send(authed("/api/trips/$tripId/sync?since=$since").get().build())
        return json.decodeFromString(PullResponse.serializer(), text)
    }

    suspend fun push(tripId: String, request: PushRequest): PushResponse {
        val body = json.encodeToString(PushRequest.serializer(), request).toRequestBody(jsonType)
        val text = send(authed("/api/trips/$tripId/sync/push").post(body).build())
        return json.decodeFromString(PushResponse.serializer(), text)
    }

    suspend fun download(path: String, into: File) = withContext(Dispatchers.IO) {
        client.newCall(authed(path).get().build()).execute().use { response ->
            if (!response.isSuccessful) throw HttpStatusException(response.code, null)
            val source = response.body ?: throw IOException("Empty response")
            into.outputStream().use { out -> source.byteStream().copyTo(out) }
        }
    }
}
