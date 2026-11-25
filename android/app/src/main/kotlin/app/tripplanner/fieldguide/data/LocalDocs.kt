package app.tripplanner.fieldguide.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A ticket or document the traveller linked to a plan from their own phone. It never leaves the phone. */
@Serializable
data class LocalDoc(
    val id: String,
    @SerialName("trip_id") val tripId: String,
    @SerialName("block_id") val blockId: String,
    @SerialName("file_name") val fileName: String,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("size_bytes") val sizeBytes: Long,
    @SerialName("added_at") val addedAt: Long,
)

/** [LocalDoc.blockId] for documents added in the Wallet that belong to the whole trip, not one plan. */
const val TRIP_DOCS = "trip"

class LocalDocException(message: String) : IOException(message)

/**
 * Original tickets and documents picked with the system file picker. Each file is copied into
 * app-private storage (filesDir/local-docs/<id>) so it opens offline and keeps working after the
 * original is moved or deleted. The index is a small JSON file next to the documents.
 *
 * Nothing here is synced or uploaded: these files hold passport numbers and other personal data.
 * The planner's own attachments live in [Vault] and are managed by sync; the two never mix.
 */
class LocalDocs(private val context: Context) {
    private val dir = File(context.filesDir, "local-docs").apply { mkdirs() }
    private val index = File(dir, INDEX)
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    private val listSerializer = ListSerializer(LocalDoc.serializer())

    private val _docs = MutableStateFlow(read())
    val docs: StateFlow<List<LocalDoc>> = _docs.asStateFlow()

    fun file(id: String): File = File(dir, id)

    fun isReady(doc: LocalDoc): Boolean = file(doc.id).length() == doc.sizeBytes

    /**
     * Copies the picked file into app storage and links it to [blockId]. With [replacing], the old
     * document is removed only after the new one is safely stored.
     */
    suspend fun add(tripId: String, blockId: String, uri: Uri, replacing: String? = null): LocalDoc = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var name: String? = null
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val nameAt = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeAt = c.getColumnIndex(OpenableColumns.SIZE)
                if (nameAt >= 0 && !c.isNull(nameAt)) name = c.getString(nameAt)
                if (sizeAt >= 0 && !c.isNull(sizeAt) && c.getLong(sizeAt) > MAX_BYTES) throw tooBig()
            }
        }

        val id = UUID.randomUUID().toString()
        val part = File(dir, "$id.part")
        val target = file(id)
        val mime: String
        try {
            val input = resolver.openInputStream(uri) ?: throw IOException("Could not read the file")
            var copied = 0L
            input.use { src ->
                part.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = src.read(buffer)
                        if (read < 0) break
                        copied += read
                        if (copied > MAX_BYTES) throw tooBig()
                        out.write(buffer, 0, read)
                    }
                }
            }
            if (copied == 0L) throw LocalDocException("That file is empty.")
            mime = sniff(part) ?: throw LocalDocException("Pick a PDF or a photo (JPEG, PNG or WebP).")
            if (!part.renameTo(target)) throw IOException("Could not store the file")
        } catch (e: Exception) {
            part.delete()
            throw e
        }

        val doc = LocalDoc(
            id = id,
            tripId = tripId,
            blockId = blockId,
            fileName = cleanName(name, mime),
            mimeType = mime,
            sizeBytes = target.length(),
            addedAt = System.currentTimeMillis(),
        )
        lock.withLock {
            write(_docs.value.filterNot { it.id == replacing } + doc)
        }
        replacing?.let { file(it).delete() }
        doc
    }

    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        lock.withLock { write(_docs.value.filterNot { it.id == id }) }
        file(id).delete()
    }

    /** Deletes every linked document. Used when the phone is unpaired. */
    suspend fun clear() = withContext(Dispatchers.IO) {
        lock.withLock {
            dir.listFiles()?.forEach { it.delete() }
            _docs.value = emptyList()
        }
    }

    private fun read(): List<LocalDoc> = runCatching {
        if (!index.exists()) return@runCatching emptyList<LocalDoc>()
        json.decodeFromString(listSerializer, index.readText()).filter { file(it.id).exists() }
    }.getOrDefault(emptyList())

    private fun write(next: List<LocalDoc>) {
        val tmp = File(dir, "$INDEX.tmp")
        tmp.writeText(json.encodeToString(listSerializer, next))
        if (!tmp.renameTo(index)) {
            index.delete()
            if (!tmp.renameTo(index)) throw IOException("Could not save the document list")
        }
        _docs.value = next
    }

    private fun tooBig() = LocalDocException("That file is over ${MAX_BYTES / (1024 * 1024)} MB.")

    private companion object {
        const val INDEX = "index.json"
        const val MAX_BYTES = 50L * 1024 * 1024

        /** Trusts the bytes, not the name or the type the picker reports. */
        fun sniff(file: File): String? {
            val head = ByteArray(12)
            val n = file.inputStream().use { it.read(head) }
            fun at(i: Int, vararg bytes: Int) = n >= i + bytes.size && bytes.indices.all { head[i + it] == bytes[it].toByte() }
            return when {
                at(0, 0x25, 0x50, 0x44, 0x46) -> "application/pdf" // %PDF
                at(0, 0x89, 0x50, 0x4E, 0x47) -> "image/png"
                at(0, 0xFF, 0xD8, 0xFF) -> "image/jpeg"
                at(0, 0x52, 0x49, 0x46, 0x46) && at(8, 0x57, 0x45, 0x42, 0x50) -> "image/webp" // RIFF....WEBP
                else -> null
            }
        }

        fun cleanName(name: String?, mime: String): String {
            val base = name?.replace(Regex("""[\u0000-\u001f/\\]"""), "")?.trim()?.take(120)
            if (!base.isNullOrBlank()) return base
            return when (mime) {
                "application/pdf" -> "Document.pdf"
                else -> "Photo." + mime.substringAfter('/')
            }
        }
    }
}
