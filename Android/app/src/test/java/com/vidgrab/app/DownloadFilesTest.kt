package com.vidgrab.app

import com.vidgrab.app.util.DownloadFiles
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DownloadFilesTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun file(dir: File, name: String, modified: Long = 1000) = File(dir, name).apply {
        writeText("completed test payload")
        setLastModified(modified)
    }

    @Test fun incompleteStreamsNeverBecomeCompletedDownloads() {
        val dir = temporary.newFolder()
        listOf("Video.f137.mp4", "Video.f248.webm", "Video.fhls-123.mp4", "Video.mp4.part",
            "Video.mp4.ytdl", "Video.mp4.part-Frag3", ".hidden.mp4").forEach { file(dir, it) }
        assertNull(DownloadFiles.completedOutput(dir, audio = false))
        assertNull(DownloadFiles.completedOutput(dir, audio = true))
    }

    @Test fun completedVideoWinsOverNewerRawPartsAndAudio() {
        val dir = temporary.newFolder()
        val completed = file(dir, "Video (1080p).mp4")
        file(dir, "Video.f137.mp4", 2000)
        file(dir, "Video.f140.m4a", 3000)
        file(dir, "Other.m4a", 4000)
        assertEquals(completed, DownloadFiles.completedOutput(dir, audio = false))
    }

    @Test fun audioRequestCannotDeliverAVideoFile() {
        val dir = temporary.newFolder()
        file(dir, "Video.mp4")
        assertNull(DownloadFiles.completedOutput(dir, audio = true))
        val audio = file(dir, "Audio (MP3).mp3")
        assertEquals(audio, DownloadFiles.completedOutput(dir, audio = true))
    }

    @Test fun zeroByteAndDirectoryOutputsAreRejected() {
        val dir = temporary.newFolder()
        File(dir, "empty.mp4").createNewFile()
        File(dir, "folder.mp4").mkdir()
        assertNull(DownloadFiles.completedOutput(dir, audio = false))
    }

    @Test fun retriesRecoverExistingCompletedOutputInTheirOwnJobDirectory() {
        val jobOne = temporary.newFolder("job-one")
        val jobTwo = temporary.newFolder("job-two")
        val first = file(jobOne, "Same title.mp4", 1000)
        val second = file(jobTwo, "Same title.mp4", 5000)
        assertEquals(first, DownloadFiles.completedOutput(jobOne, false))
        assertEquals(second, DownloadFiles.completedOutput(jobTwo, false))
    }

    @Test fun completedContainerAndFilenameCollisionSuffixAreAccepted() {
        val dir = temporary.newFolder()
        file(dir, "Film (4K).mkv", 1000)
        val current = file(dir, "Film (4K) (2).mkv", 2000)
        assertEquals(current, DownloadFiles.completedOutput(dir, false))
    }

    @Test fun interruptedMergeOutputCannotBeMistakenForACompletedVideo() {
        val dir = temporary.newFolder()
        file(dir, "Film.temp.mp4", 3000)
        file(dir, "Film.TEMP.mkv", 4000)
        file(dir, ".salvage-working.mp4", 5000)
        assertNull(DownloadFiles.completedOutput(dir, audio = false))
        val completed = file(dir, "Film.mp4", 1000)
        assertEquals(completed, DownloadFiles.completedOutput(dir, audio = false))
    }

    @Test fun requestedAudioConversionMustMatchItsFinalExtension() {
        val dir = temporary.newFolder()
        file(dir, "Song.m4a", 3000)
        file(dir, "Song.temp.mp3", 4000)
        assertNull(DownloadFiles.completedOutput(dir, audio = true, expectedExtension = "mp3"))
        val completed = file(dir, "Song.mp3", 1000)
        assertEquals(completed, DownloadFiles.completedOutput(dir, audio = true, expectedExtension = "mp3"))
    }
}
