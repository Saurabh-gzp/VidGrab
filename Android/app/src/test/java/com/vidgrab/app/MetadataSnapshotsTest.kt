package com.vidgrab.app

import com.vidgrab.app.util.MetadataSnapshots
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MetadataSnapshotsTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun snapshotRetainsFormatsAndHeadersButCannotSilentlyRepeatExtraction() {
        val input = """{"id":"clip","webpage_url":"https://page.test/1","original_url":"https://page.test/1","formats":[{"format_id":"720","url":"https://media.test/a"}],"http_headers":{"Referer":"https://page.test"},"requested_formats":[{}]}"""
        val result = JSONObject(MetadataSnapshots.downloadJson(input))
        assertEquals("720",result.getJSONArray("formats").getJSONObject(0).getString("format_id"))
        assertEquals("https://page.test",result.getJSONObject("http_headers").getString("Referer"))
        assertFalse(result.has("webpage_url"));assertFalse(result.has("original_url"));assertFalse(result.has("requested_formats"))
    }
    @Test fun snapshotExpiresAndMissingCacheFallsBackSafely() {
        val file = temporary.newFile("snapshot.json").apply { writeText("{}") }
        assertEquals(file,MetadataSnapshots.valid(file.path,100,101))
        assertNull(MetadataSnapshots.valid(file.path,100,100+MetadataSnapshots.MAX_AGE_MS+1))
        assertNull(MetadataSnapshots.valid(file.path,100,99))
        file.delete()
        assertNull(MetadataSnapshots.valid(file.path,100,101))
    }
}
