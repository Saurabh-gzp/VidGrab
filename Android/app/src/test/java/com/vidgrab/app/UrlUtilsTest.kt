package com.vidgrab.app

import com.vidgrab.app.util.UrlUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlUtilsTest {

    @Test
    fun extractUrl_fromSharedText() {
        val text = "Check this out https://youtu.be/GxCkuDpv9q0?si=abc123 amazing video"
        assertEquals("https://youtu.be/GxCkuDpv9q0?si=abc123", UrlUtils.extractUrl(text))
    }

    @Test
    fun extractUrl_nullWhenNoUrl() {
        assertNull(UrlUtils.extractUrl("no link here"))
    }

    @Test
    fun platformDetection() {
        assertEquals("YouTube", UrlUtils.platformOf("https://youtu.be/abc"))
        assertEquals("YouTube", UrlUtils.platformOf("https://www.youtube.com/watch?v=abc"))
        assertEquals("Instagram", UrlUtils.platformOf("https://instagram.com/reel/xyz/"))
        assertEquals("X", UrlUtils.platformOf("https://x.com/user/status/1"))
        assertEquals("Web", UrlUtils.platformOf("https://example.com/video"))
    }

    @Test
    fun normalize_addsScheme() {
        assertEquals("https://youtu.be/abc", UrlUtils.normalize("youtu.be/abc"))
        assertTrue(UrlUtils.normalize("  https://x.com/a  ").startsWith("https"))
    }

    @Test
    fun formatBytes_readable() {
        assertEquals("1.0 MB", UrlUtils.formatBytes(1024L * 1024L))
        assertEquals("—", UrlUtils.formatBytes(null))
        assertEquals("—", UrlUtils.formatBytes(0L))
    }

    @Test
    fun qualityLabels() {
        assertEquals("8K", UrlUtils.qualityLabel(7680))
        assertEquals("4K", UrlUtils.qualityLabel(2160))
        assertEquals("2K", UrlUtils.qualityLabel(1440))
        assertEquals("Full HD", UrlUtils.qualityLabel(1080))
        assertEquals("HD", UrlUtils.qualityLabel(720))
    }

    @Test
    fun isSupported_basic() {
        assertTrue(UrlUtils.isSupported("https://youtube.com/watch?v=x"))
        assertFalse(UrlUtils.isSupported("ftp://x.com"))
    }

    // ---------- v2.1: filename quality tag ----------

    @Test
    fun qualityTag_videoHeights() {
        assertEquals("1080p", UrlUtils.qualityTag(1080, false, "Full HD 1080p"))
        assertEquals("720p", UrlUtils.qualityTag(720, false, "HD 720p"))
        assertEquals("4K", UrlUtils.qualityTag(2160, false, "4K 2160p"))
        assertEquals("2K", UrlUtils.qualityTag(1440, false, "2K 1440p"))
        assertEquals("8K", UrlUtils.qualityTag(7680, false, "8K 4320p+"))
        assertEquals("Best", UrlUtils.qualityTag(null, false, "Best — Full HD"))
    }

    @Test
    fun qualityTag_audio() {
        assertEquals("M4A", UrlUtils.qualityTag(null, true, "Audio — M4A"))
        assertEquals("MP3", UrlUtils.qualityTag(null, true, "Audio — MP3"))
        assertEquals("Audio", UrlUtils.qualityTag(null, true, "Audio"))
    }

    @Test
    fun sanitizeFileName_stripsIllegalChars() {
        assertEquals("My_ Video_ 1080p", UrlUtils.sanitizeFileName("My:/ Video?* 1080p"))
        assertEquals("Video", UrlUtils.sanitizeFileName("   "))
        assertTrue(UrlUtils.sanitizeFileName("x".repeat(200)).length <= 80)
    }

    @Test
    fun friendlyEngineError_translates() {
        val bot = UrlUtils.friendlyEngineError("ERROR: Sign in to confirm you're not a bot")
        assertTrue(bot.contains("block"))
        val net = UrlUtils.friendlyEngineError("Unable to resolve host youtube.com")
        assertTrue(net.contains("Internet"))
        val raw = UrlUtils.friendlyEngineError("some weird error text")
        assertTrue(raw.contains("weird"))
    }

    @Test fun platformDetectionUsesTheHostRatherThanPathsOrLookalikes() {
        assertEquals("Web", UrlUtils.platformOf("https://youtube.com.evil.test/video"))
        assertEquals("Web", UrlUtils.platformOf("https://evil.test/?next=https://youtube.com/watch"))
        assertEquals("YouTube", UrlUtils.platformOf("https://m.youtube.com/watch?v=123"))
        assertEquals("Pinterest", UrlUtils.platformOf("https://pin.it/123"))
        assertEquals("Dailymotion", UrlUtils.platformOf("https://dai.ly/123"))
    }

    @Test fun normalizeExtractsSharedLinksAndStripsOnlyTheFragment() {
        assertEquals("https://youtu.be/abc?si=123", UrlUtils.normalize("Watch https://youtu.be/abc?si=123#time here"))
        assertEquals("https://example.com/film?a=1", UrlUtils.normalize("https://example.com/film?a=1#first#last"))
    }

    @Test fun normalizeRejectsUnsupportedSchemesAndCredentialUrls() {
        listOf("ftp://example.com/file", "file:///secret", "https://user:pass@example.com/file", "not a link").forEach {
            assertTrue("Accepted invalid URL: $it", runCatching { UrlUtils.normalize(it) }.isFailure)
        }
        assertFalse(UrlUtils.isSupported("https://user:pass@example.com/file"))
        assertFalse(UrlUtils.isSupported("http://"))
    }

    @Test fun eightKUsesVerticalResolution4320() {
        assertEquals("8K", UrlUtils.qualityLabel(4320))
        assertEquals("8K", UrlUtils.qualityTag(4320, false, "Best"))
        assertEquals("4K", UrlUtils.qualityLabel(2160))
    }
}
