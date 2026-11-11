package app.tripplanner.fieldguide.data

import android.content.Context
import app.tripplanner.fieldguide.sync.SyncApi
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Tickets and vouchers, copied into app storage during a pull so they open with no signal. */
class Vault(context: Context, private val api: SyncApi) {
    private val dir = File(context.filesDir, "vault").apply { mkdirs() }

    fun file(attachmentId: String): File = File(dir, attachmentId)

    fun isReady(attachment: AttachmentEntity): Boolean = file(attachment.id).length() == attachment.sizeBytes

    /** Downloads what is missing and removes files no longer referenced. Returns how many downloads failed. */
    suspend fun sync(attachments: List<AttachmentEntity>): Int {
        val wanted = attachments.map { it.id }.toSet()
        dir.listFiles()?.forEach { if (it.name.removeSuffix(".part") !in wanted) it.delete() }

        var failed = 0
        for (attachment in attachments) {
            val target = file(attachment.id)
            if (target.exists() && sha256(target).equals(attachment.sha256, ignoreCase = true)) continue
            val part = File(dir, "${attachment.id}.part")
            try {
                api.download(attachment.downloadPath, part)
                if (!sha256(part).equals(attachment.sha256, ignoreCase = true)) throw IOException("Checksum mismatch")
                if (!part.renameTo(target)) throw IOException("Could not store file")
            } catch (_: IOException) {
                part.delete()
                failed++
            }
        }
        return failed
    }

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
