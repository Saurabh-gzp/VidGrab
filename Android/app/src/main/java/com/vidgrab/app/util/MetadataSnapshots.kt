package com.vidgrab.app.util

import android.content.Context
import com.vidgrab.app.core.VideoMeta
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Short-lived, app-private extractor snapshots avoid extracting again when Download is tapped. */
object MetadataSnapshots {
    const val MAX_AGE_MS = 15 * 60 * 1000L
    fun save(context: Context, meta: VideoMeta): String? {
        val payload = meta.infoJson ?: return null
        if (payload.length > 12_000_000) return null
        return runCatching {
            val directory = File(context.cacheDir, "metadata").apply { mkdirs() }
            val key = MessageDigest.getInstance("SHA-256").digest(meta.webpageUrl.toByteArray())
                .joinToString("") { "%02x".format(it) }
            val target = File(directory, "$key.json")
            val temporary = File.createTempFile("snapshot-", ".tmp", directory)
            try {
                temporary.writeText(downloadJson(payload))
                check(temporary.renameTo(target))
            } finally { temporary.delete() }
            directory.listFiles()?.filter { it.isFile && it.lastModified() < System.currentTimeMillis() - 24 * 60 * 60 * 1000L }?.forEach { it.delete() }
            target.absolutePath
        }.getOrNull()
    }

    internal fun downloadJson(payload: String): String = JSONObject(payload).apply {
        // yt-dlp otherwise silently re-extracts on DownloadError; retries are controlled by the repository.
        listOf("webpage_url", "original_url", "requested_formats", "requested_downloads", "_filename", "filename").forEach { remove(it) }
    }.toString()

    fun valid(path: String?, fetchedAtMs: Long, nowMs: Long = System.currentTimeMillis()): File? {
        if (nowMs - fetchedAtMs !in 0..MAX_AGE_MS) return null
        return path?.let(::File)?.takeIf { it.isFile && it.length() in 1..12_000_000L }
    }
}
