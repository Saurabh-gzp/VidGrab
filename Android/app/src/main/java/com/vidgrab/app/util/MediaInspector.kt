package com.vidgrab.app.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore

object MediaInspector {
    data class Facts(val width: Int?, val height: Int?, val bytes: Long?)
    fun inspect(context: Context, uri: Uri, video: Boolean): Facts? = runCatching {
        val bytes = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { fd ->
            fd.length.takeIf { it > 0 } ?: fd.parcelFileDescriptor.statSize.takeIf { it > 0 }
        }
        var width: Int? = null
        var height: Int? = null
        if (video) {
            val metadata = MediaMetadataRetriever()
            try {
                metadata.setDataSource(context, uri)
                width = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()?.takeIf { it > 0 }
                height = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()?.takeIf { it > 0 }
            } finally { metadata.release() }
        }
        Facts(width, height, bytes)
    }.getOrNull()

    fun quality(width: Int?, height: Int?, previous: String): String {
        val shortSide = if (width != null && height != null) minOf(width, height) else return previous
        return "${shortSide}p · ${width}×${height}"
    }
}
