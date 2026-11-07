package com.vidgrab.app.util

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

object MediaSaver {
    const val ROOT_DIR = "VidGrab"
    fun hasAllFilesAccess(ctx: Context): Boolean = Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()

    /** Returns the actual saved URI and path, including any MediaStore collision suffix. */
    fun save(ctx: Context, src: File, isAudio: Boolean): Pair<Uri?, String>? {
        if (!src.isFile || src.length() == 0L) return null
        if (hasAllFilesAccess(ctx)) {
            val dir = File(Environment.getExternalStorageDirectory(), "$ROOT_DIR/${if (isAudio) "Audio" else "Video"}")
            var dest: File? = null
            try {
                check(dir.isDirectory || dir.mkdirs())
                var candidate = File(dir, src.name)
                var n = 2
                while (candidate.exists()) { candidate = File(dir, "${src.nameWithoutExtension} (${n++}).${src.extension}") }
                dest = candidate
                src.copyTo(candidate)
                MediaScannerConnection.scanFile(ctx, arrayOf(candidate.absolutePath), arrayOf(mimeOf(candidate.name, isAudio)), null)
                val path = candidate.relativeTo(Environment.getExternalStorageDirectory()).path
                return null to path
            } catch (_: Exception) { dest?.delete() }
        }
        val resolver = ctx.contentResolver
        val collection = collection(isAudio)
        val folder = if (isAudio) "Music/$ROOT_DIR/Audio/" else "Movies/$ROOT_DIR/Video/"
        var inserted: Uri? = null
        return try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, src.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeOf(src.name, isAudio))
                put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values) ?: return null
            inserted = uri
            val out = resolver.openOutputStream(uri) ?: error("Storage could not be opened")
            out.use { output -> src.inputStream().use { input -> check(input.copyTo(output) == src.length()) } }
            values.clear(); values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            check(resolver.update(uri, values, null, null) > 0)
            var actualName = src.name
            resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) actualName = it.getString(0)
            }
            uri to (folder + actualName)
        } catch (_: Exception) {
            inserted?.let { runCatching { resolver.delete(it, null, null) } }
            null
        }
    }

    private fun collection(audio: Boolean): Uri = if (audio)
        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    fun uriFor(ctx: Context, path: String, savedUri: String? = null): Uri? {
        if (!savedUri.isNullOrBlank()) return Uri.parse(savedUri)
        try {
            val source = collection(path.startsWith("Music/") || path.contains("/Audio/"))
            ctx.contentResolver.query(source, arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?",
                arrayOf(path.substringBeforeLast('/') + "/", path.substringAfterLast('/')), null)?.use {
                if (it.moveToFirst()) return ContentUris.withAppendedId(source, it.getLong(0))
            }
            val root = Environment.getExternalStorageDirectory()
            val file = File(root, path).canonicalFile
            if (file.path.startsWith(root.canonicalPath + File.separator) && file.isFile)
                return androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", file)
        } catch (_: Exception) { }
        return null
    }

    fun deleteByPath(ctx: Context, path: String, savedUri: String? = null): Boolean {
        val uri = uriFor(ctx, path, savedUri) ?: return true // Already removed outside the app.
        return try {
            if (uri.authority == "${ctx.packageName}.provider") {
                val root = Environment.getExternalStorageDirectory().canonicalFile
                val file = File(root, path).canonicalFile
                file.path.startsWith(root.path + File.separator) && (!file.exists() || file.delete())
            } else ctx.contentResolver.delete(uri, null, null) >= 0
        } catch (_: Exception) { false }
    }

    fun mimeOf(name: String, isAudio: Boolean): String = when (name.substringAfterLast('.').lowercase()) {
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "aac" -> "audio/aac"
        "opus" -> "audio/opus"
        "ogg" -> "audio/ogg"
        "wav" -> "audio/wav"
        "flac" -> "audio/flac"
        "webm" -> if (isAudio) "audio/webm" else "video/webm"
        "mkv" -> "video/x-matroska"
        "mov" -> "video/quicktime"
        "3gp" -> "video/3gpp"
        "ts" -> "video/mp2t"
        else -> if (isAudio) "audio/mp4" else "video/mp4"
    }
}
