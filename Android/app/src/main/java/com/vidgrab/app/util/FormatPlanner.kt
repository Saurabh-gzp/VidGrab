package com.vidgrab.app.util

import com.vidgrab.app.core.FormatOption
import com.vidgrab.app.core.SelectedStream
import com.vidgrab.app.core.VideoMeta
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Plans concrete IDs from yt-dlp's processed formats, ordered worst to best by the engine.
 * The exact same streams supply both the selector and the advertised transfer size.
 * See yt-dlp FormatSorter and YoutubeDL._select_formats; resolution uses the short side.
 */
object FormatPlanner {
    private val videoContainers = setOf("mp4", "webm", "mkv", "mov", "m4v", "3gp", "avi", "flv", "ts")
    private val audioContainers = setOf("m4a", "mp3", "aac", "opus", "ogg", "wav", "flac", "weba")

    private data class Source(
        val id: String, val video: Boolean, val audio: Boolean,
        val width: Int?, val height: Int?, val fps: Double?, val ext: String,
        val acodec: String?, val bytes: Long?, val approximate: Boolean, val rank: Int
    ) {
        val resolution: Int? get() = listOfNotNull(width, height).minOrNull()
        fun selected() = SelectedStream(id, bytes, approximate, video, audio)
        val aac: Boolean get() = acodec?.let { it.startsWith("mp4a") || it.startsWith("aac") } == true
        val mp3: Boolean get() = acodec?.startsWith("mp3") == true
    }

    fun build(json: JSONObject, meta: VideoMeta): List<FormatOption> {
        if (json.optBoolean("has_drm", false)) return emptyList()
        val array = json.optJSONArray("formats")?.takeIf { it.length() > 0 }
            ?: if (json.cleanString("url") != null) JSONArray().put(json) else JSONArray()
        val sources = (0 until array.length()).mapNotNull { index ->
            val raw = array.optJSONObject(index) ?: return@mapNotNull null
            if (raw.optBoolean("has_drm", false)) return@mapNotNull null
            val ext = raw.cleanString("ext")?.lowercase(Locale.ROOT) ?: ""
            if (ext in setOf("mhtml", "html", "jpg", "jpeg", "png", "webp")) return@mapNotNull null
            val vcodec = raw.cleanString("vcodec")
            val acodec = raw.cleanString("acodec")
            val width = raw.optInt("width").takeIf { it > 0 }
            val height = raw.optInt("height").takeIf { it > 0 }
            val video = if (vcodec != null) vcodec != "none"
                else ext in videoContainers || (width != null && height != null)
            val audio = if (acodec != null) acodec != "none"
                else ext in audioContainers || (video && ext in videoContainers)
            if (!video && !audio) return@mapNotNull null
            val exact = raw.optLong("filesize").takeIf { it > 0 }
            val providedApprox = raw.optLong("filesize_approx").takeIf { it > 0 }
            // Manifest bitrates can be maximum rather than average; do not invent sizes from them.
            val protocol = raw.cleanString("protocol") ?: "https"
            val canEstimateBitrate = raw.cleanString("manifest_url") == null &&
                !protocol.contains("m3u8") && !protocol.contains("dash")
            val bitrate = raw.optDouble("tbr", 0.0).takeIf { it.isFinite() && it > 0 }
            val bitrateEstimate = if (canEstimateBitrate && bitrate != null && (meta.durationSec ?: 0) > 0)
                positiveBytes(bitrate * 1000.0 / 8 * meta.durationSec!!) else null
            Source(
                id = raw.cleanString("format_id") ?: index.toString(), video = video, audio = audio,
                width = width, height = height,
                fps = raw.optDouble("fps", 0.0).takeIf { it.isFinite() && it > 0 },
                ext = ext, acodec = acodec, bytes = exact ?: providedApprox ?: bitrateEstimate,
                approximate = exact == null, rank = index
            )
        }
        val videos = sources.filter { it.video }
        val audioOnly = sources.filter { it.audio && !it.video }
        val bestAudio = audioOnly.lastOrNull()
        val output = mutableListOf<FormatOption>()
        val byResolution = videos.filter { it.resolution != null }.groupBy { it.resolution!! }
        val qualities = byResolution.toSortedMap(compareByDescending { it }).map { (resolution, group) ->
            videoOption(group.maxBy { it.rank }, bestAudio, "h$resolution", qualityLabel(resolution))
        }
        val bestVideo = if (qualities.isNotEmpty())
            videos.filter { it.resolution == byResolution.keys.maxOrNull() }.maxBy { it.rank }
            else videos.lastOrNull()
        if (bestVideo != null) {
            output += videoOption(bestVideo, bestAudio, "best",
                "Best — ${bestVideo.resolution?.let(::qualityLabel) ?: "original video"}")
        }
        output += qualities

        // Prefer an audio stream. If only muxed files exist, avoid downloading an entire 4K
        // video just to extract its sound: choose the smallest declared source available.
        val muxedAudio = videos.filter { it.audio }.minWithOrNull(
            compareBy<Source> { it.bytes ?: Long.MAX_VALUE }.thenBy { it.resolution ?: Int.MAX_VALUE }.thenByDescending { it.rank }
        )
        val mp3Source = bestAudio ?: muxedAudio
        val m4aSource = audioOnly.lastOrNull { it.ext == "m4a" && it.aac }
            ?: audioOnly.lastOrNull { it.aac } ?: mp3Source
        if (m4aSource != null) output += audioOption(m4aSource, meta.durationSec, mp3 = false)
        if (mp3Source != null) output += audioOption(mp3Source, meta.durationSec, mp3 = true)
        return output
    }

    private fun videoOption(video: Source, audio: Source?, id: String, label: String): FormatOption {
        val streams = listOfNotNull(video, audio.takeUnless { video.audio })
        val selected = streams.map { it.selected() }
        val transfer = sumKnown(streams.map { it.bytes })
        val transferApprox = streams.any { it.approximate }
        val outputApprox = transferApprox || streams.size > 1
        val dimensions = listOfNotNull(video.width, video.height).takeIf { it.size == 2 }
            ?.joinToString(" × ") ?: video.resolution?.let { "${it}p" }
        val fps = video.fps?.let { if (it % 1.0 == 0.0) "${it.toInt()} fps" else "${"%.2f".format(Locale.US, it)} fps" }
        return FormatOption(
            id = id, label = label,
            detail = listOfNotNull(dimensions, fps, sizeLabel(transfer, outputApprox)).joinToString(" • "),
            selector = streams.joinToString("+") { exactSelector(it.id) },
            estBytes = transfer, isApprox = outputApprox, isAudioOnly = false,
            height = video.resolution, selectedStreams = selected,
            transferBytes = transfer, transferIsApprox = transferApprox,
            pixelWidth = video.width, pixelHeight = video.height
        )
    }

    private fun audioOption(source: Source, duration: Long?, mp3: Boolean): FormatOption {
        val unchanged = !source.video && if (mp3) source.ext == "mp3" && source.mp3 else source.ext == "m4a" && source.aac
        val transcode = if (mp3) !source.mp3 else !source.aac
        val estimate = when {
            unchanged -> source.bytes
            mp3 && transcode && (duration ?: 0) > 0 -> positiveBytes(duration!! * 192_000.0 / 8)
            else -> null
        }
        val approximate = !unchanged || source.approximate
        val target = if (mp3) "MP3" else "M4A"
        val description = when {
            unchanged -> "Original $target"
            mp3 && transcode -> "192 kbps"
            source.aac && !mp3 -> "AAC audio · no re-encoding"
            else -> "$target conversion"
        }
        val outputSize = if (estimate != null) sizeLabel(estimate, approximate) + if (unchanged) "" else " output"
            else "Output size varies"
        return FormatOption(
            id = if (mp3) "mp3" else "m4a", label = "Audio — $target",
            detail = "$description • $outputSize", selector = exactSelector(source.id),
            estBytes = estimate, isApprox = approximate, isAudioOnly = true,
            postArgs = listOf("--extract-audio", "--audio-format", target.lowercase(Locale.ROOT)) +
                if (mp3) listOf("--audio-quality", "192K") else emptyList(),
            selectedStreams = listOf(source.selected()), transferBytes = source.bytes,
            transferIsApprox = source.approximate, requiresTranscode = transcode
        )
    }

    private fun exactSelector(id: String): String {
        val reserved = id in videoContainers || id in audioContainers || id in setOf("all", "mergeall") ||
            Regex("(?:best|worst|b|w)(?:video|audio|v|a)?(?:\\.\\d+)?").matches(id)
        if (!reserved && Regex("[A-Za-z0-9_.-]+").matches(id)) return id
        val escaped = id.replace("\\", "\\\\").replace("\"", "\\\"")
        return "best*[format_id=\"$escaped\"]"
    }

    private fun qualityLabel(resolution: Int): String {
        val tag = UrlUtils.qualityLabel(resolution)
        return if (tag == "${resolution}p") tag else "$tag ${resolution}p"
    }
    private fun sizeLabel(bytes: Long?, approximate: Boolean) = if (bytes == null) "Size unavailable"
        else (if (approximate) "~" else "") + UrlUtils.formatBytes(bytes)
    private fun sumKnown(bytes: List<Long?>): Long? {
        if (bytes.any { it == null }) return null
        var sum = 0L
        for (value in bytes) { if (Long.MAX_VALUE - sum < value!!) return null; sum += value }
        return sum.takeIf { it > 0 }
    }
    private fun positiveBytes(value: Double): Long? = value.takeIf { it.isFinite() && it > 0 && it < Long.MAX_VALUE.toDouble() }?.toLong()?.takeIf { it > 0 }
    private fun JSONObject.cleanString(key: String) = optString(key).takeUnless { it.isBlank() || it == "null" }
}
