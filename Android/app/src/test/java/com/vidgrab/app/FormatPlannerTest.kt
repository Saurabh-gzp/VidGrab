package com.vidgrab.app

import com.vidgrab.app.core.VideoMeta
import com.vidgrab.app.util.FormatPlanner
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FormatPlannerTest {
    private val meta = VideoMeta("test", "Film", durationSec = 100,
        webpageUrl = "https://example.com/film", platform = "Web")
    private fun plan(json: String) = FormatPlanner.build(JSONObject(json), meta)

    @Test fun advertisedSizeUsesTheChosenCodecRatherThanAnotherSameResolutionCodec() {
        val choices = plan("""{"formats":[
            {"format_id":"a","vcodec":"none","acodec":"opus","filesize":1000000},
            {"format_id":"avc","width":1280,"height":720,"vcodec":"avc1","acodec":"none","tbr":9000,"filesize":84900000},
            {"format_id":"av1","width":1280,"height":720,"vcodec":"av01","acodec":"none","tbr":4000,"filesize":45700000}
        ]}""")
        val option = choices.first { it.id == "best" }
        assertEquals("av1+a", option.selector)
        assertEquals(46700000L, option.transferBytes)
        assertEquals(46700000L, option.estBytes)
        assertEquals(listOf(45700000L, 1000000L), option.selectedStreams.map { it.bytes })
    }

    @Test fun enginePreferenceWithinResolutionAndAudioLanguageArePreserved() {
        val choices = plan("""{"formats":[
            {"format_id":"dub","vcodec":"none","acodec":"aac","abr":256,"filesize":5000000},
            {"format_id":"original","vcodec":"none","acodec":"aac","abr":128,"filesize":2000000},
            {"format_id":"video","width":1280,"height":720,"vcodec":"av01","acodec":"none","filesize":10000000}
        ]}""")
        val option = choices.first { it.id == "best" }
        assertEquals("video+original", option.selector)
        assertEquals(12000000L, option.transferBytes)
    }

    @Test fun portraitResolutionUsesItsShortSideAndKeepsActualPixelDimensions() {
        val option = plan("""{"formats":[{"format_id":"portrait","width":1080,"height":1920,"vcodec":"avc1","acodec":"aac","filesize":10000}]}""")
            .first { it.id == "best" }
        assertEquals(1080, option.height)
        assertEquals(1080, option.pixelWidth)
        assertEquals(1920, option.pixelHeight)
        assertTrue(option.label.contains("1080p"))
        assertFalse(option.label.contains("1920p"))
        assertFalse(option.label.contains("2K"))
    }

    @Test fun missingAudioSizeMakesCombinedTransferSizeUnknown() {
        val option = plan("""{"formats":[
            {"format_id":"a","vcodec":"none","acodec":"opus"},
            {"format_id":"v","height":720,"vcodec":"avc1","acodec":"none","filesize":1000000}
        ]}""").first { it.id == "best" }
        assertEquals("v+a", option.selector)
        assertNull(option.transferBytes)
        assertNull(option.estBytes)
        assertTrue(option.transferIsApprox)
    }

    @Test fun approximateSourceSizeIsNeverMarkedExact() {
        val option = plan("""{"formats":[{"format_id":"v","height":720,"vcodec":"avc1","acodec":"aac","filesize_approx":1000000}]}""")
            .first { it.id == "best" }
        assertEquals(1000000L, option.transferBytes)
        assertTrue(option.transferIsApprox)
        assertTrue(option.isApprox)
    }

    @Test fun manifestPeakBitrateDoesNotBecomeAFakeFileSize() {
        val option = plan("""{"formats":[{"format_id":"hls","height":720,"vcodec":"avc1","acodec":"aac","tbr":10000,"protocol":"m3u8_native","manifest_url":"https://example.com/master.m3u8"}]}""")
            .first { it.id == "best" }
        assertNull(option.transferBytes)
        assertNull(option.estBytes)
    }

    @Test fun ordinaryStreamBitrateIsClearlyAnEstimate() {
        val option = plan("""{"formats":[{"format_id":"http","height":720,"vcodec":"avc1","acodec":"aac","tbr":800,"protocol":"https"}]}""")
            .first { it.id == "best" }
        assertEquals(10000000L, option.transferBytes)
        assertTrue(option.transferIsApprox)
        assertTrue(option.isApprox)
    }

    @Test fun m4aConversionShowsUnknownOutputInsteadOfUsingOpusTransferSize() {
        val option = plan("""{"formats":[{"format_id":"251","vcodec":"none","acodec":"opus","ext":"opus","filesize":1000000}]}""")
            .first { it.id == "m4a" }
        assertEquals(1000000L, option.transferBytes)
        assertFalse(option.transferIsApprox)
        assertNull(option.estBytes)
        assertTrue(option.requiresTranscode)
        assertTrue(option.detail.contains("Output size varies"))
    }

    @Test fun mp3ConversionUsesKnownTargetBitrateAndDurationForOutputOnly() {
        val option = plan("""{"formats":[{"format_id":"251","vcodec":"none","acodec":"opus","ext":"opus","filesize":1000000}]}""")
            .first { it.id == "mp3" }
        assertEquals(1000000L, option.transferBytes)
        assertEquals(2400000L, option.estBytes)
        assertTrue(option.isApprox)
        assertTrue(option.requiresTranscode)
        assertEquals("192K", option.postArgs.last())
    }

    @Test fun originalMp3KeepsItsRealSizeRatherThanPretendingToReencodeAt192K() {
        val option = plan("""{"formats":[{"format_id":"original","vcodec":"none","acodec":"mp3","ext":"mp3","filesize":1000000}]}""")
            .first { it.id == "mp3" }
        assertEquals(1000000L, option.estBytes)
        assertFalse(option.isApprox)
        assertFalse(option.requiresTranscode)
        assertTrue(option.detail.contains("Original MP3"))
    }

    @Test fun nativeM4aUsesItsExactSizeAndItsOwnConcreteStream() {
        val option = plan("""{"formats":[
            {"format_id":"140","vcodec":"none","acodec":"mp4a.40.2","ext":"m4a","filesize":1000000},
            {"format_id":"251","vcodec":"none","acodec":"opus","ext":"webm","filesize":2000000}
        ]}""").first { it.id == "m4a" }
        assertEquals("140", option.selector)
        assertEquals(1000000L, option.estBytes)
        assertFalse(option.isApprox)
        assertFalse(option.requiresTranscode)
    }

    @Test fun progressiveAudioExtractionAccountsForWholeVideoNetworkTransfer() {
        val choices = plan("""{"formats":[
            {"format_id":"small","width":640,"height":360,"vcodec":"avc1","acodec":"aac","ext":"mp4","filesize":10000000},
            {"format_id":"big","width":3840,"height":2160,"vcodec":"avc1","acodec":"aac","ext":"mp4","filesize":90000000}
        ]}""")
        val audio = choices.first { it.id == "m4a" }
        assertEquals("small", audio.selector)
        assertEquals(10000000L, audio.transferBytes)
        assertNull(audio.estBytes)
        assertTrue(audio.selectedStreams.single().isVideo)
    }

    @Test fun reservedFormatIdUsesAnExactFilterRatherThanAnExtensionOrBestSelector() {
        val option = plan("""{"formats":[{"format_id":"best","height":720,"vcodec":"avc1","acodec":"aac","filesize":10000}]}""")
            .first { it.id == "best" }
        assertEquals("best*[format_id=\"best\"]", option.selector)
        assertEquals("best", option.selectedStreams.single().formatId)
    }

    @Test fun unknownDurationDoesNotInventAnMp3OutputSize() {
        val info = JSONObject("""{"formats":[{"format_id":"a","vcodec":"none","acodec":"opus","ext":"opus","filesize":1000000}]}""")
        val option = FormatPlanner.build(info, meta.copy(durationSec = null)).first { it.id == "mp3" }
        assertNull(option.estBytes)
        assertEquals(1000000L, option.transferBytes)
    }

    @Test fun live720pRegressionUsesSelectedAv1AndOpusSizesInsteadOfLargerH264() {
        val fixture = javaClass.getResourceAsStream("/fixtures/same-resolution-codecs.json")!!
            .bufferedReader().use { it.readText() }
        val json = JSONObject(fixture)
        val option = FormatPlanner.build(json, meta.copy(durationSec = json.getLong("duration")))
            .first { it.id == "best" }
        assertEquals("398+251", option.selector)
        assertEquals(48960951L, option.transferBytes)
        assertEquals(48960951L, option.estBytes)
        assertFalse(option.transferIsApprox)
        assertTrue(option.isApprox) // Small container overhead remains an output estimate.
        assertNotEquals(83479456L + 5503142L, option.estBytes)
    }
}
