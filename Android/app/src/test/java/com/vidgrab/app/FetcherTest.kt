package com.vidgrab.app

import com.vidgrab.app.core.VideoMeta
import com.vidgrab.app.util.Fetcher
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FetcherTest {
    private val meta = VideoMeta(id = "test", title = "Film", durationSec = 60,
        webpageUrl = "https://example.com/film", platform = "Web")

    @Test fun bestQualityRetainsTheHighestResolutionAndExactStreamIds() {
        val info = JSONObject("""{"formats":[
            {"format_id":"audio","vcodec":"none","acodec":"mp4a","ext":"m4a","filesize":100000},
            {"format_id":"1080","height":1080,"vcodec":"avc1","acodec":"none","filesize":2000000},
            {"format_id":"2160","height":2160,"vcodec":"vp9","acodec":"none","filesize":4000000}
        ]}""")
        val best = Fetcher.buildFormats(info, meta).first { it.id == "best" }
        assertEquals(2160, best.height)
        assertEquals("2160+audio", best.selector)
        assertEquals(listOf("2160", "audio"), best.selectedStreams.map { it.formatId })
        assertEquals(4100000L, best.transferBytes)
        assertFalse(best.transferIsApprox)
        assertTrue(best.isApprox) // Final container overhead may change the saved size.
    }

    @Test fun qualityChoiceUsesOnlyItsAdvertisedConcreteStream() {
        val info = JSONObject("""{"formats":[
            {"format_id":"720","height":720,"vcodec":"h264","acodec":"aac","filesize":1000},
            {"format_id":"2160","height":2160,"vcodec":"vp9","acodec":"none","filesize":5000}
        ]}""")
        val chosen = Fetcher.buildFormats(info, meta).first { it.id == "h720" }
        assertEquals("720", chosen.selector)
        assertEquals(listOf("720"), chosen.selectedStreams.map { it.formatId })
        assertEquals(1000L, chosen.transferBytes)
        assertFalse(chosen.isApprox)
    }

    @Test fun audioOnlySourceHasNoMisleadingVideoChoice() {
        val info = JSONObject("""{"formats":[
            {"format_id":"251","vcodec":"none","acodec":"opus","filesize":100000,"ext":"opus"}
        ]}""")
        val choices = Fetcher.buildFormats(info, meta)
        assertTrue(choices.isNotEmpty())
        assertTrue(choices.all { it.isAudioOnly })
        assertTrue(choices.all { "--extract-audio" in it.postArgs })
        assertTrue(choices.all { it.selector == "251" })
    }

    @Test fun silentVideoHasNoMisleadingAudioChoice() {
        val info = JSONObject("""{"formats":[
            {"format_id":"137","height":1080,"vcodec":"avc1","acodec":"none","filesize":1000000,"ext":"mp4"}
        ]}""")
        val choices = Fetcher.buildFormats(info, meta)
        assertTrue(choices.isNotEmpty())
        assertTrue(choices.none { it.isAudioOnly })
        assertEquals("137", choices.first { it.id == "best" }.selector)
    }

    @Test fun drmAndStoryboardFormatsAreExcluded() {
        val info = JSONObject("""{"formats":[
            {"format_id":"drm","height":2160,"vcodec":"avc1","acodec":"none","has_drm":true},
            {"format_id":"story","height":1080,"vcodec":"none","acodec":"none","ext":"mhtml"},
            {"format_id":"720","height":720,"vcodec":"avc1","acodec":"aac","filesize":1000000}
        ]}""")
        val choices = Fetcher.buildFormats(info, meta)
        assertEquals(720, choices.first { it.id == "best" }.height)
        assertFalse(choices.any { it.id == "h2160" || it.id == "h1080" })
    }

    @Test fun muxedFileSizeDoesNotDoubleCountAudio() {
        val info = JSONObject("""{"formats":[
            {"format_id":"140","vcodec":"none","acodec":"mp4a","filesize":100000,"ext":"m4a"},
            {"format_id":"22","height":720,"vcodec":"avc1","acodec":"aac","filesize":1000000}
        ]}""")
        val choice = Fetcher.buildFormats(info, meta).first { it.id == "h720" }
        assertEquals(1000000L, choice.estBytes)
        assertEquals(1000000L, choice.transferBytes)
        assertEquals("22", choice.selector)
    }

    @Test fun directVideoWithoutFormatListStillHasDownloadChoices() {
        val info = JSONObject("""{"url":"https://example.com/film.mp4","ext":"mp4","title":"Film"}""")
        val choices = Fetcher.buildFormats(info, meta)
        val video = choices.firstOrNull { it.id == "best" }
        assertNotNull(video)
        assertEquals("0", video!!.selector)
        assertFalse(video.isAudioOnly)
        assertTrue(choices.any { it.isAudioOnly })
    }

    @Test fun directSilentVideoDoesNotOfferAudioExtraction() {
        val info = JSONObject("""{"url":"https://example.com/film.mp4","ext":"mp4","acodec":"none"}""")
        val choices = Fetcher.buildFormats(info, meta)
        assertTrue(choices.any { !it.isAudioOnly })
        assertTrue(choices.none { it.isAudioOnly })
    }

    @Test fun directDrmVideoIsNotOfferedForDownload() {
        val info = JSONObject("""{"url":"https://example.com/film.mp4","ext":"mp4","has_drm":true,"acodec":"none"}""")
        assertTrue(Fetcher.buildFormats(info, meta).isEmpty())
    }

    @Test fun fetchResultCarriesCompleteSnapshotAndTimestampForFastDownload() {
        val json = JSONObject("""{"id":"abc","title":"Film","webpage_url":"https://example.com/film","url":"https://cdn.example.com/film.mp4","ext":"mp4","http_headers":{"User-Agent":"Test"}}""")
        val before = System.currentTimeMillis()
        val result = Fetcher.parseMeta(meta.webpageUrl, json, null)
        assertTrue(result.fetchedAtMs >= before)
        assertEquals(result.fetchedAtMs, result.meta.fetchedAtMs)
        assertEquals(result.infoJson, result.meta.infoJson)
        assertEquals("https://cdn.example.com/film.mp4", JSONObject(result.infoJson!!).getString("url"))
        assertEquals("Test", JSONObject(result.infoJson).getJSONObject("http_headers").getString("User-Agent"))
    }
}
