package com.vidgrab.app

import com.vidgrab.app.core.UpdateManager
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateManagerTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun checksumSelectsExactZipappInsteadOfSimilarPlatformBinary() {
        val expected = "a".repeat(64)
        val list = "${"b".repeat(64)}  yt-dlp_linux\n$expected  yt-dlp\n${"c".repeat(64)}  yt-dlp.exe"
        assertEquals(expected, UpdateManager.checksumFor(list, "yt-dlp"))
    }

    @Test fun checksumsRejectMissingMalformedAndDuplicateEntries() {
        val valid = "a".repeat(64) + "  yt-dlp"
        assertNull(UpdateManager.checksumFor("bad-hash  yt-dlp", "yt-dlp"))
        assertNull(UpdateManager.checksumFor("a".repeat(64) + "  yt-dlp_linux", "yt-dlp"))
        assertNull(UpdateManager.checksumFor("$valid\n$valid", "yt-dlp"))
    }

    @Test fun checksumSupportsBinaryMarkerAndUppercaseHashes() {
        assertEquals("ab".repeat(32), UpdateManager.checksumFor("${"AB".repeat(32)} *yt-dlp", "yt-dlp"))
    }

    @Test fun checksumHashesTheActualFileBytes() {
        val file = temporary.newFile().apply { writeText("abc") }
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            UpdateManager.sha256(file))
        file.appendText("d")
        assertNotEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            UpdateManager.sha256(file))
    }

    @Test fun dateVersionsCompareAcrossMonthsAndYearsWithoutDowngrades() {
        assertTrue(UpdateManager.isNewer("2026.11.01", "2026.08.19"))
        assertTrue(UpdateManager.isNewer("v2027.01.01", "2026.12.31"))
        assertFalse(UpdateManager.isNewer("2026.08.19", "2026.11.01"))
        assertFalse(UpdateManager.isNewer("v2026.11.01", "2026.11.01"))
        assertFalse(UpdateManager.isNewer("2026.99.01", "2026.11.01"))
        assertFalse(UpdateManager.isNewer("garbage", "2026.11.01"))
    }
}
