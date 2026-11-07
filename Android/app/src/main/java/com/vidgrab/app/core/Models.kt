package com.vidgrab.app.core

import kotlinx.serialization.Serializable

/** Video metadata — extraction ke baad milti hai */
data class VideoMeta(
    val id: String,
    val title: String,
    val uploader: String? = null,
    val durationSec: Long? = null,
    val thumbnailUrl: String? = null,
    val thumbnailPath: String? = null,
    val webpageUrl: String,
    val platform: String,
    val extractor: String? = null,
    /** Kaunse extraction client args ne kaam kiya (retry ke liye yaad rakhte hain) */
    val clientArgs: String? = null,
    val bestAudioBytes: Long? = null,
    /** Private extraction metadata; persist only to app-private snapshot files. */
    val infoJson: String? = null,
    val fetchedAtMs: Long = 0L
)

/** One concrete stream selected from yt-dlp's already sorted metadata. */
@Serializable
data class SelectedStream(
    val formatId: String,
    val bytes: Long? = null,
    val isApprox: Boolean = true,
    val isVideo: Boolean = false,
    val isAudio: Boolean = false
)

/** A quality choice with separate network-transfer and final-output estimates. */
data class FormatOption(
    val id: String,
    val label: String,
    val detail: String,
    /** yt-dlp format selector string */
    val selector: String,
    /** Approx total size (video + audio ka sum) */
    val estBytes: Long?,
    val isApprox: Boolean,
    val isAudioOnly: Boolean,
    val height: Int? = null,
    /** Post-processor args (MP3 conversion etc.) */
    val postArgs: List<String> = emptyList(),
    val selectedStreams: List<SelectedStream> = emptyList(),
    val transferBytes: Long? = estBytes,
    val transferIsApprox: Boolean = isApprox,
    val pixelWidth: Int? = null,
    val pixelHeight: Int? = null,
    val requiresTranscode: Boolean = false
)

enum class DlStatus { QUEUED, FETCHING, DOWNLOADING, MERGING, DONE, FAILED, CANCELLED }

@Serializable
data class DownloadItem(
    val id: String,
    val url: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val thumbnailPath: String? = null,
    val status: DlStatus = DlStatus.QUEUED,
    val progress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBps: Long = 0L,
    val etaSec: Long = -1L,
    val formatLabel: String = "",
    val filePath: String? = null,
    val error: String? = null,
    val startedAtMs: Long = System.currentTimeMillis(),
    val clientArgs: String? = null,
    val postArgs: List<String> = emptyList(),
    val selector: String = "",
    val mimeHint: String = "video",
    /** Selected quality ki height — filename ke "Title (1080p)" tag ke liye */
    val height: Int? = null,
    val infoSnapshotPath: String? = null,
    val fetchedAtMs: Long = 0L,
    val selectedStreams: List<SelectedStream> = emptyList(),
    val totalIsApprox: Boolean = true,
    val pixelWidth: Int? = null,
    val pixelHeight: Int? = null,
    val progressKnown: Boolean = false,
    val selectedOptionId: String = ""
)

@Serializable
data class HistoryEntry(
    val id: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val thumbnailPath: String? = null,
    val filePath: String,
    val contentUri: String? = null,
    val mime: String,
    val sizeBytes: Long,
    val completedAtMs: Long,
    val url: String,
    val formatLabel: String = "",
    val pixelWidth: Int? = null,
    val pixelHeight: Int? = null,
    val metadataVerified: Boolean = false
)

/** Auto-update engine ke states — overlay sirf Updating par dikhta hai */
sealed class UpdateState {
    data object Idle : UpdateState()
    data object Checking : UpdateState()
    data class Updating(val progress: Float, val etaSec: Long, val line: String) : UpdateState()
    data object Done : UpdateState()
    data object Failed : UpdateState()
}
