package com.vidgrab.app.util

import android.content.Context
import com.vidgrab.app.core.FormatOption
import com.vidgrab.app.core.VideoMeta
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.UUID

/** Extract once, then retain the complete private info snapshot for an immediate download. */
object Fetcher {
    data class FetchResult(
        val meta: VideoMeta,
        val formats: List<FormatOption>,
        val infoJson: String? = null,
        val fetchedAtMs: Long = System.currentTimeMillis()
    )
    // Let the installed engine choose its maintained default first. A single alternate
    // client is attempted only for a known YouTube player-response failure.
    private val CLIENT_FALLBACKS = listOf(null, "youtube:player_client=android_vr")
    private val PLAYER_FAILURES = listOf(
        "player response", "failed to extract any player response", "unable to extract initial player",
        "sign in to confirm", "confirm you're not a bot", "not a bot"
    )

    suspend fun fetchInfo(appCtx: Context, rawUrl: String): FetchResult = withContext(Dispatchers.IO) {
        val url = UrlUtils.normalize(rawUrl)
        var lastError: Exception? = null
        for (clientArgs in CLIENT_FALLBACKS) {
            try {
                currentCoroutineContext().ensureActive()
                val json = runInterruptible { dumpJson(appCtx, url, clientArgs) }
                return@withContext parseMeta(url, json, clientArgs)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                lastError = e
                val message = e.message.orEmpty().lowercase(Locale.ROOT)
                if (UrlUtils.platformOf(url) != "YouTube" || PLAYER_FAILURES.none { it in message }) throw e
            }
        }
        throw lastError ?: IllegalStateException("Could not fetch video info")
    }

    fun dumpJson(appCtx: Context, url: String, clientArgs: String?): JSONObject {
        val request = YoutubeDLRequest(url)
        request.addOption("--dump-single-json")
        request.addOption("--no-playlist")
        request.addOption("--no-warnings")
        request.addOption("--ignore-config")
        request.addOption("--socket-timeout", "12")
        request.addOption("--retries", "1")
        request.addOption("--extractor-retries", "1")
        // The Android wrapper otherwise adds --no-cache-dir to every invocation,
        // repeatedly rebuilding extractor/signature caches between fetch and download.
        val engineCache = File(appCtx.cacheDir, "engine").apply { mkdirs() }
        request.addOption("--cache-dir", engineCache.absolutePath)
        if (clientArgs != null) request.addOption("--extractor-args", clientArgs)
        val processId = "fetch-${UUID.randomUUID()}"
        val response = try { YoutubeDL.getInstance().execute(request, processId, null) }
        finally { runCatching { YoutubeDL.getInstance().destroyProcessById(processId) } }
        if (response.exitCode != 0) {
            val detail = response.err?.takeIf { it.isNotBlank() } ?: response.out
            throw IllegalStateException(detail.takeLast(500).ifBlank { "Extraction failed (code ${response.exitCode})" })
        }
        val output = response.out.trim()
        val start = output.indexOf('{')
        check(start >= 0) { "Empty response from extractor" }
        return JSONObject(output.substring(start))
    }

    internal fun parseMeta(url: String, json: JSONObject, clientArgs: String?): FetchResult {
        val snapshot = json.toString()
        val fetchedAt = System.currentTimeMillis()
        fun text(key: String): String? = json.optString(key).takeUnless { it.isBlank() || it == "null" }
        val meta = VideoMeta(
            id = text("id").orEmpty(), title = text("title") ?: "Video",
            uploader = text("uploader") ?: text("channel"),
            durationSec = json.optDouble("duration", 0.0).takeIf { it.isFinite() && it > 0 }?.toLong(),
            thumbnailUrl = text("thumbnail"), webpageUrl = text("webpage_url") ?: url,
            platform = UrlUtils.platformOf(url), extractor = text("extractor_key"),
            clientArgs = clientArgs, infoJson = snapshot, fetchedAtMs = fetchedAt
        )
        return FetchResult(meta, buildFormats(json, meta), snapshot, fetchedAt)
    }

    fun buildFormats(json: JSONObject, meta: VideoMeta): List<FormatOption> = FormatPlanner.build(json, meta)
}
