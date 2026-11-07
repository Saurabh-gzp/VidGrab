package com.vidgrab.app.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Saved artwork belongs to the library, so it lives in filesDir, never cacheDir. */
object ThumbnailStore {
    suspend fun capture(ctx: Context, key: String, url: String?, mediaUri: android.net.Uri? = null, preferLocal: Boolean = false): String? =
        withContext(Dispatchers.IO) {
            val target = try { ArtworkFiles.target(ctx.filesDir, key) } catch (_: Exception) { return@withContext null }
            if (target.isFile && target.length() > 0) return@withContext target.absolutePath
            try {
                val bitmap = withTimeoutOrNull(12_000) {
                    if (url.isNullOrBlank() || (preferLocal && mediaUri != null)) null else {
                        val result = ctx.imageLoader.execute(ImageRequest.Builder(ctx)
                            .data(url).size(960, 540).allowHardware(false).build())
                        (result as? SuccessResult)?.drawable?.toBitmap()
                    }
                } ?: mediaUri?.let { uri ->
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(ctx, uri)
                        retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 960, 540)
                    } finally { retriever.release() }
                }
                if (bitmap == null) return@withContext null
                ArtworkFiles.publish(target) { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                target.absolutePath
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { null }
        }
}
