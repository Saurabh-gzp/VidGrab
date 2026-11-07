package com.vidgrab.app

import com.vidgrab.app.util.ArtworkFiles
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ArtworkFilesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun libraryArtworkSurvivesClearingTheAppImageCache() {
        val files = temporary.newFolder("files")
        val cache = temporary.newFolder("cache")
        File(cache, "old-image-cache").writeText("disposable")
        val artwork = ArtworkFiles.target(files, "https://example.com/video/123")
        ArtworkFiles.publish(artwork) { it.write(byteArrayOf(1, 2, 3, 4)); true }
        cache.deleteRecursively()
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), artwork.readBytes())
        assertEquals(artwork, ArtworkFiles.target(files, "https://example.com/video/123"))
    }

    @Test fun failedEncodingKeepsThePreviouslyPublishedArtworkAndCleansTemporaryFiles() {
        val files = temporary.newFolder("files")
        val artwork = ArtworkFiles.target(files, "one-video")
        ArtworkFiles.publish(artwork) { it.write(byteArrayOf(4, 5, 6)); true }
        assertTrue(runCatching { ArtworkFiles.publish(artwork) { it.write(1); false } }.isFailure)
        assertArrayEquals(byteArrayOf(4, 5, 6), artwork.readBytes())
        assertEquals(listOf(artwork.name), artwork.parentFile!!.list()!!.toList())
    }

    @Test fun emptyEncodingIsNotPublished() {
        val artwork = ArtworkFiles.target(temporary.newFolder("files"), "empty")
        assertTrue(runCatching { ArtworkFiles.publish(artwork) { true } }.isFailure)
        assertFalse(artwork.exists())
    }
}
