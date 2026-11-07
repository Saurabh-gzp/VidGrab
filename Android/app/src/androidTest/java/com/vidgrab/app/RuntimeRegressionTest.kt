package com.vidgrab.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil.imageLoader
import com.vidgrab.app.core.DlStatus
import com.vidgrab.app.core.DownloadRepository
import com.vidgrab.app.core.Engine
import com.vidgrab.app.core.HistoryEntry
import com.vidgrab.app.util.Fetcher
import com.vidgrab.app.util.MediaInspector
import com.vidgrab.app.util.MediaSaver
import com.vidgrab.app.util.ThumbnailStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import java.util.UUID

/**
 * Exercises Android storage, decoding, the bundled extraction engine and foreground worker.
 * Run against the host's five-second portrait fixture, or pass fixture_url to the runner.
 * All storage cleanup is limited to items created by this test run.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class RuntimeRegressionTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private val arguments get() = InstrumentationRegistry.getArguments()

    @Test fun test01MediaStoreCollisionKeepsBothFilesReadable() {
        val runId = UUID.randomUUID().toString()
        val directory = File(context.filesDir, "runtime-test-$runId").apply { check(mkdirs()) }
        val source = File(directory, "VidGrab collision $runId.mp4")
        val payload = ByteArray(24_123) { index -> (index % 251).toByte() }
        source.writeBytes(payload)
        val saved = mutableListOf<Pair<Uri?, String>>()
        try {
            repeat(2) {
                saved += requireNotNull(MediaSaver.save(context, source, isAudio = false)) {
                    "MediaSaver.save failed on collision attempt $it"
                }
            }
            assertNotEquals("Collision must produce a distinct actual path", saved[0].second, saved[1].second)
            val resolved = saved.map { (uri, path) -> requireNotNull(MediaSaver.uriFor(context, path, uri?.toString())) }
            assertNotEquals("Both saves need independently addressable URIs", resolved[0], resolved[1])
            resolved.forEach { uri ->
                val actual = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                assertArrayEquals("Saved content changed at $uri", payload, actual)
            }
            recordMetrics("media_store_collision", JSONObject()
                .put("distinct_paths", true).put("readable_files", 2).put("bytes_each", payload.size))
        } finally {
            saved.forEach { (uri, path) ->
                assertTrue("Could not remove this test's saved file", MediaSaver.deleteByPath(context, path, uri?.toString()))
            }
            directory.deleteRecursively()
        }
    }

    @Test fun test02ArtworkSurvivesImageCacheAndSourceDeletion() = runBlocking {
        val runId = UUID.randomUUID().toString()
        val directory = File(context.filesDir, "runtime-test-$runId").apply { check(mkdirs()) }
        val source = File(directory, "artwork.png")
        val bitmap = Bitmap.createBitmap(96, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(26, 179, 137)) }
        source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        var artwork: File? = null
        try {
            val key = "instrumentation-artwork-$runId"
            val path = withTimeout(30_000) { ThumbnailStore.capture(context, key, Uri.fromFile(source).toString()) }
            assertNotNull("Artwork capture from a real PNG should succeed", path)
            artwork = File(path!!)
            assertTrue("Artwork must live in durable private files", artwork.canonicalPath.startsWith(context.filesDir.canonicalPath + File.separator))
            assertTrue("Artwork must be nonempty", artwork.length() > 0)
            assertTrue(source.delete())
            // Clear only Coil's image cache. Engine caches and production files are untouched.
            withContext(Dispatchers.IO) {
                context.imageLoader.memoryCache?.clear()
                File(context.cacheDir, "image_cache").deleteRecursively()
            }
            val decoded = BitmapFactory.decodeFile(artwork.path)
            assertNotNull("Clearing the image cache must not remove saved artwork", decoded)
            assertEquals(96, decoded!!.width)
            assertEquals(64, decoded.height)
            decoded.recycle()
            val reused = ThumbnailStore.capture(context, key, Uri.fromFile(source).toString())
            assertEquals("Existing artwork should be reused even after its source is gone", path, reused)
            recordMetrics("durable_artwork", JSONObject()
                .put("cache_deleted", true).put("source_deleted", true).put("decoded_width", 96).put("decoded_height", 64))
        } finally {
            artwork?.delete()
            directory.deleteRecursively()
        }
    }

    @Test fun test03NativeSnapshotVideoDownloadAndPortraitHistory() = runBlocking {
        runDownload(audio = false)
    }

    @Test fun test04NativeMp3ConversionWhenRequested() = runBlocking {
        assumeTrue("Pass -e include_audio true to include native MP3 conversion", arguments.getString("include_audio") == "true")
        runDownload(audio = true)
    }

    private suspend fun runDownload(audio: Boolean) {
        val runId = UUID.randomUUID().toString()
        val fixtureUrl = arguments.getString("fixture_url") ?: "http://10.0.2.2:8765/sample.mp4"
        var createdId: String? = null
        var createdEntry: HistoryEntry? = null
        ActivityScenario.launch(MainActivity::class.java).use {
            try {
                assertTrue("Native engine did not become ready: ${Engine.lastError}", withContext(Dispatchers.IO) { Engine.await() })
                DownloadRepository.awaitReady()
                val beforeIds = DownloadRepository.active.value.map { it.id }.toSet() +
                    DownloadRepository.history.value.map { it.id }.toSet()
                val fetchStarted = SystemClock.elapsedRealtime()
                val result = withTimeout(120_000) { Engine.withEngine { Fetcher.fetchInfo(context, fixtureUrl) } }
                val fetchMillis = SystemClock.elapsedRealtime() - fetchStarted
                assertFalse("Fixture should expose downloadable formats", result.formats.isEmpty())
                assertFalse("Metadata snapshot must be retained", result.infoJson.isNullOrBlank())
                assertTrue(result.fetchedAtMs > 0)
                val format = result.formats.first { if (audio) it.id == "mp3" else it.id == "best" }
                assertTrue("Selected stream identities must be resolved", format.selectedStreams.isNotEmpty())
                val enqueueStarted = SystemClock.elapsedRealtime()
                DownloadRepository.enqueue(fixtureUrl,
                    result.meta.copy(title = "VidGrab runtime $runId", thumbnailUrl = null, thumbnailPath = null), format)
                createdId = DownloadRepository.active.value.firstOrNull { item -> item.id !in beforeIds }?.id
                    ?: DownloadRepository.history.value.firstOrNull { entry -> entry.id !in beforeIds }?.id
                assertNotNull("Enqueue did not create a download", createdId)
                val id = createdId!!
                val finished = withTimeout(120_000) {
                    combine(DownloadRepository.active, DownloadRepository.history) { active, history -> active to history }
                        .first { (active, history) ->
                            history.any { entry -> entry.id == id } || active.any { item -> item.id == id && item.status == DlStatus.FAILED }
                        }
                }
                val failure = finished.first.firstOrNull { item -> item.id == id && item.status == DlStatus.FAILED }
                assertNull("Native download failed: ${failure?.error}", failure)
                val entry = finished.second.first { saved -> saved.id == id }
                createdEntry = entry
                assertTrue("Saved history must report real bytes", entry.sizeBytes > 0)
                val uri = requireNotNull(MediaSaver.uriFor(context, entry.filePath, entry.contentUri))
                val actualBytes = withContext(Dispatchers.IO) { readableByteCount(uri) }
                assertEquals("History must use the actual saved media size", actualBytes, entry.sizeBytes)
                val facts = withContext(Dispatchers.IO) { MediaInspector.inspect(context, uri, video = !audio) }
                facts?.bytes?.let { assertEquals(actualBytes, it) }
                if (!audio && facts?.width != null && facts.height != null) {
                    assertEquals("Expected the portrait fixture's width", 720, facts.width)
                    assertEquals("Expected the portrait fixture's height", 1280, facts.height)
                    assertTrue("Portrait history should use the shorter dimension: ${entry.formatLabel}", entry.formatLabel.contains("720p"))
                    assertFalse(entry.formatLabel.contains("1280p"))
                }
                if (audio) {
                    assertEquals("audio/mpeg", entry.mime)
                    assertTrue(entry.filePath.endsWith(".mp3"))
                }
                recordMetrics(if (audio) "native_mp3" else "native_video", JSONObject()
                    .put("fetch_ms", fetchMillis)
                    .put("enqueue_to_saved_ms", SystemClock.elapsedRealtime() - enqueueStarted)
                    .put("selector", format.selector)
                    .put("saved_bytes", actualBytes)
                    .put("pixel_width", facts?.width)
                    .put("pixel_height", facts?.height)
                    .put("snapshot_present", true))
            } finally {
                createdEntry?.let { entry ->
                    DownloadRepository.deleteHistory(entry)
                    withTimeout(15_000) { DownloadRepository.history.first { history -> history.none { it.id == entry.id } } }
                }
                if (createdEntry == null) createdId?.let { DownloadRepository.cancel(it) }
            }
        }
    }

    private fun readableByteCount(uri: Uri): Long = context.contentResolver.openInputStream(uri)!!.use { input ->
        val buffer = ByteArray(32 * 1024)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
        }
        total
    }

    private fun recordMetrics(name: String, values: JSONObject) {
        val message = "VIDGRAB_METRICS " + values.put("test", name).toString()
        Log.i("VidGrabRuntimeTest", message)
        instrumentation.sendStatus(2, Bundle().apply { putString("stream", "$message\n") })
    }
}
