package com.vidgrab.app.util

import org.json.JSONObject
import kotlin.math.max

/** A transfer update contains measured bytes, never bytes reconstructed from a percentage. */
data class ProgressSnapshot(
    val downloadedBytes: Long,
    val totalBytes: Long,
    val totalIsApprox: Boolean,
    val speedBps: Long,
    val etaSec: Long,
    /** Null means the complete transfer size is unknown; the UI should stay indeterminate. */
    val progress: Float?,
    val started: Boolean
)

/**
 * Aggregates yt-dlp's structured progress by format ID. Metadata and diagnostic output are ignored.
 * A video reaching 100% does not complete a video+audio transfer, nor reset its byte counter when
 * the audio stream starts. The repository alone marks the job complete after saving the result.
 *
 * [expectedTotal] is the sum for the selected streams, not the final transcoded output size.
 */
class TransferProgress(
    private val expectedTotal: Long = 0,
    private val initialApprox: Boolean = true,
    expectedStreamIds: Set<String> = emptySet()
) {
    private data class Stream(
        var downloaded: Long = 0,
        var total: Long? = null,
        var approximate: Boolean = true
    )

    private val expectedIds = expectedStreamIds.filter { it.isNotBlank() }.toSet()
    private val streams = linkedMapOf<String, Stream>()
    private var highestProgress = 0f

    @Synchronized
    fun parse(line: String): ProgressSnapshot? {
        val clean = line.trimStart()
        if (!clean.startsWith(PREFIX)) return null
        val json = try { JSONObject(clean.removePrefix(PREFIX).trim()) } catch (_: Exception) { return null }
        val status = json.optString("status")
        if (status != "downloading" && status != "finished") return null
        val id = json.optString("format_id").takeUnless { it.isBlank() || it == "null" || it == "NA" }
            ?: if (expectedIds.size == 1) expectedIds.first() else DEFAULT_STREAM
        // An unexpected format makes the declared whole-job total unreliable. Keep the actual
        // counter but show an unknown total instead of silently pretending it belongs to a match.
        val stream = streams.getOrPut(id) { Stream() }
        val downloaded = json.nonNegativeLong("downloaded_bytes")
        if (downloaded != null) stream.downloaded = max(stream.downloaded, downloaded)
        val exactTotal = json.nonNegativeLong("total_bytes")?.takeIf { it > 0 }
        val estimatedTotal = json.nonNegativeLong("total_bytes_estimate")?.takeIf { it > 0 }
        when {
            status == "finished" && downloaded != null && downloaded > 0 -> {
                stream.total = stream.downloaded
                stream.approximate = false
            }
            exactTotal != null -> { stream.total = exactTotal; stream.approximate = false }
            estimatedTotal != null && stream.approximate -> { stream.total = estimatedTotal }
        }
        if ((stream.total ?: Long.MAX_VALUE) < stream.downloaded) {
            stream.total = null
            stream.approximate = true
        }

        val measuredBytes = sumSafely(streams.values.map { it.downloaded })
        val unexpectedStream = expectedIds.isNotEmpty() && streams.keys.any { it !in expectedIds }
        val allExpectedKnown = expectedIds.isNotEmpty() && expectedIds.all { streams[it]?.total != null }
        val allObservedKnown = streams.values.all { it.total != null }
        val (knownTotal, approximate) = when {
            unexpectedStream -> 0L to true
            allExpectedKnown -> sumSafely(expectedIds.map { streams.getValue(it).total!! }) to
                expectedIds.any { streams.getValue(it).approximate }
            expectedTotal > 0 -> expectedTotal to initialApprox
            expectedIds.isEmpty() && streams.size == 1 && allObservedKnown ->
                streams.values.first().total!! to streams.values.first().approximate
            else -> 0L to true
        }
        // A stale estimate must never become a made-up total smaller than measured bytes.
        val total = knownTotal.takeIf { it >= measuredBytes } ?: 0L
        val percent = if (total > 0) {
            val measured = (measuredBytes.toDouble() / total * 100).toFloat().coerceIn(0f, 99f)
            highestProgress = max(highestProgress, measured)
            highestProgress
        } else null
        val speed: Long = if (status == "finished") 0L else json.nonNegativeLong("speed") ?: 0L
        val eta = when {
            status == "finished" -> -1L
            total > 0 && speed > 0 -> {
                val remaining = total - measuredBytes
                remaining / speed + if (remaining % speed == 0L) 0 else 1
            }
            expectedIds.size <= 1 -> json.nonNegativeLong("eta") ?: -1L
            else -> -1L
        }
        return ProgressSnapshot(
            downloadedBytes = measuredBytes,
            totalBytes = total,
            totalIsApprox = approximate || total == 0L,
            speedBps = speed,
            // yt-dlp's ETA refers to the current stream. Use the aggregate transfer estimate
            // when it is available so finishing video does not promise zero seconds of audio.
            etaSec = eta,
            progress = percent,
            started = measuredBytes > 0
        )
    }

    private fun JSONObject.nonNegativeLong(key: String): Long? {
        val value = opt(key)
        if (value is Long) return value.takeIf { it >= 0 }
        if (value is Int) return value.toLong().takeIf { it >= 0 }
        val number = (value as? Number)?.toDouble() ?: (value as? String)?.toDoubleOrNull() ?: return null
        return number.takeIf { it.isFinite() && it >= 0 && it < Long.MAX_VALUE.toDouble() }?.toLong()
    }

    private fun sumSafely(values: Iterable<Long>): Long = values.fold(0L) { total, bytes ->
        if (Long.MAX_VALUE - total < bytes) Long.MAX_VALUE else total + bytes
    }

    companion object {
        const val PREFIX = "VIDGRAB_PROGRESS "
        private const val DEFAULT_STREAM = "__default"
        /** Use with --progress and --newline. Explicit null defaults keep missing measurements valid JSON. */
        const val TEMPLATE = "download:VIDGRAB_PROGRESS {\"format_id\":%(info.format_id|null)j," +
            "\"downloaded_bytes\":%(progress.downloaded_bytes|null)j," +
            "\"total_bytes\":%(progress.total_bytes|null)j," +
            "\"total_bytes_estimate\":%(progress.total_bytes_estimate|null)j," +
            "\"speed\":%(progress.speed|null)j,\"eta\":%(progress.eta|null)j,\"status\":%(progress.status|null)j}"
    }
}
