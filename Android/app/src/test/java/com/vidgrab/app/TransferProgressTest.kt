package com.vidgrab.app

import com.vidgrab.app.util.TransferProgress
import org.junit.Assert.*
import org.junit.Test

class TransferProgressTest {
    private fun update(id: String, bytes: Long, total: Long? = null, estimate: Long? = null,
                       status: String = "downloading", speed: String = "2048", eta: String = "3") =
        TransferProgress.PREFIX + """{"format_id":"$id","downloaded_bytes":$bytes,"total_bytes":${total ?: "null"},"total_bytes_estimate":${estimate ?: "null"},"status":"$status","speed":$speed,"eta":$eta}"""

    @Test fun metadataAndOrdinaryLibraryPercentCallbacksDoNotStartTheDownload() {
        val transfer = TransferProgress(100, false, setOf("v"))
        assertNull(transfer.parse("[youtube] Extracting URL: https://youtu.be/test"))
        assertNull(transfer.parse("[download] Destination: Video.mp4"))
        assertNull(transfer.parse("[download] 0.0% of 100MiB at Unknown speed ETA Unknown"))
        assertFalse(transfer.parse(update("v", 0, 100))!!.started)
        assertTrue(transfer.parse(update("v", 1, 100))!!.started)
    }

    @Test fun videoAndAudioBytesAccumulateAndPartialTotalDoesNotReplaceTheWholeJob() {
        val transfer = TransferProgress(120, false, setOf("v", "a"))
        val video = transfer.parse(update("v", 50, 100))!!
        assertEquals(50L, video.downloadedBytes)
        assertEquals(120L, video.totalBytes)
        assertEquals(41.666668f, video.progress!!, .001f)
        val finishedVideo = transfer.parse(update("v", 100, 100, status = "finished"))!!
        assertEquals(83.333336f, finishedVideo.progress!!, .001f)
        val audio = transfer.parse(update("a", 10, 20))!!
        assertEquals(110L, audio.downloadedBytes)
        assertEquals(120L, audio.totalBytes)
        assertTrue(audio.progress!! > finishedVideo.progress!!)
        assertFalse(audio.totalIsApprox)
        assertEquals(99f, transfer.parse(update("a", 20, 20, status = "finished"))!!.progress!!, .01f)
    }

    @Test fun unknownAudioSizeDoesNotTurnTheVideoSizeIntoAnExactJobTotal() {
        val transfer = TransferProgress(0, true, setOf("v", "a"))
        val video = transfer.parse(update("v", 80, 100))!!
        assertEquals(0L, video.totalBytes)
        assertNull(video.progress)
        val audioUnknown = transfer.parse(update("a", 3))!!
        assertEquals(83L, audioUnknown.downloadedBytes)
        assertEquals(0L, audioUnknown.totalBytes)
        assertTrue(audioUnknown.totalIsApprox)
        assertNull(audioUnknown.progress)
    }

    @Test fun streamHeadersReplaceAStaleEstimateWithTheMeasuredMatchingSize() {
        val transfer = TransferProgress(849, true, setOf("muxed"))
        val read = transfer.parse(update("muxed", 100, total = 467))!!
        assertEquals(467L, read.totalBytes)
        assertFalse(read.totalIsApprox)
        assertEquals(100L, read.downloadedBytes)
        val finished = transfer.parse(update("muxed", 466, total = 467, status = "finished"))!!
        assertEquals(466L, finished.totalBytes)
        assertFalse(finished.totalIsApprox)
    }

    @Test fun estimatedHlsSizeStaysApproximateUntilTheStreamFinishes() {
        val transfer = TransferProgress(expectedStreamIds = setOf("hls"))
        val estimate = transfer.parse(update("hls", 20, estimate = 100))!!
        assertTrue(estimate.totalIsApprox)
        assertEquals(20L, estimate.downloadedBytes)
        val finished = transfer.parse(update("hls", 90, estimate = 100, status = "finished"))!!
        assertEquals(90L, finished.totalBytes)
        assertFalse(finished.totalIsApprox)
    }

    @Test fun malformedOrNonFiniteMeasurementsNeverProduceBogusValues() {
        val transfer = TransferProgress(expectedStreamIds = setOf("v"))
        assertNull(transfer.parse(TransferProgress.PREFIX + "not-json"))
        assertNull(transfer.parse(TransferProgress.PREFIX + """{"status":"extracting"}"""))
        val result = transfer.parse(TransferProgress.PREFIX + """{"status":"downloading","format_id":"v","downloaded_bytes":"NA","total_bytes":"NA","speed":"NaN","eta":-4}""")!!
        assertEquals(0L, result.downloadedBytes)
        assertEquals(0L, result.totalBytes)
        assertEquals(0L, result.speedBps)
        assertEquals(-1L, result.etaSec)
        assertFalse(result.started)
        assertNull(result.progress)
    }

    @Test fun repeatedOrOutOfOrderUpdatesDoNotDoubleCountOrRewindMeasuredBytes() {
        val transfer = TransferProgress(100, false, setOf("v"))
        val first = transfer.parse(update("v", 60, 100))!!
        val repeat = transfer.parse(update("v", 60, 100))!!
        val old = transfer.parse(update("v", 20, 100))!!
        assertEquals(60L, repeat.downloadedBytes)
        assertEquals(60L, old.downloadedBytes)
        assertEquals(first.progress, old.progress)
    }

    @Test fun unexpectedStreamInvalidatesADeclaredTotalRatherThanFakingACompleteTransfer() {
        val transfer = TransferProgress(100, false, setOf("selected"))
        val other = transfer.parse(update("unexpected", 10, 30))!!
        assertEquals(10L, other.downloadedBytes)
        assertEquals(0L, other.totalBytes)
        assertTrue(other.totalIsApprox)
        assertNull(other.progress)
    }

    @Test fun missingFormatIdCanUseTheOnlySelectedStream() {
        val transfer = TransferProgress(100, false, setOf("selected"))
        val update = transfer.parse(TransferProgress.PREFIX + """{"status":"downloading","downloaded_bytes":10,"total_bytes":100,"format_id":"NA"}""")!!
        assertEquals(10L, update.downloadedBytes)
        assertEquals(100L, update.totalBytes)
        assertFalse(update.totalIsApprox)
        assertEquals(10f, update.progress!!, .001f)
    }

    @Test fun aStaleTotalSmallerThanMeasuredBytesBecomesUnknown() {
        val transfer = TransferProgress(20, true, setOf("v", "a"))
        val measured = transfer.parse(update("v", 30))!!
        assertEquals(30L, measured.downloadedBytes)
        assertEquals(0L, measured.totalBytes)
        assertTrue(measured.totalIsApprox)
        assertNull(measured.progress)
    }
    @Test fun realBundledEngineInitialOutputKeepsUnknownMeasurementsIndeterminate() {
        // Captured from bundled yt-dlp 2026.10.26's evaluate_outtmpl with TEMPLATE.
        val line = TransferProgress.PREFIX + """{"format_id":"137","downloaded_bytes":0,"total_bytes":null,"total_bytes_estimate":null,"speed":null,"eta":null,"status":"downloading"}"""
        val initial = TransferProgress(expectedStreamIds = setOf("137")).parse(line)!!
        assertFalse(initial.started)
        assertNull(initial.progress)
        assertEquals(0L, initial.totalBytes)
        assertEquals(-1L, initial.etaSec)
    }

}
