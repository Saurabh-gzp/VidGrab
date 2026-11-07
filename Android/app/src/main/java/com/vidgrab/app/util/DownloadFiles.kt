package com.vidgrab.app.util

import java.io.File

/** Only complete outputs from this job may be delivered. Parts never count as success. */
object DownloadFiles {
    private val parts = Regex(""".+\.f[^.]+\.[^.]+$""")
    private val merging = Regex(""".+\.temp\.[^.]+$""", RegexOption.IGNORE_CASE)
    fun completedOutput(dir: File, audio: Boolean, expectedExtension: String? = null): File? {
        val extensions = if (audio) setOf("m4a", "mp3", "opus", "ogg", "wav", "aac", "flac")
            else setOf("mp4", "mkv", "webm", "mov", "m4v", "3gp", "avi", "flv", "ts")
        return dir.listFiles()?.filter { it.isFile && it.length() > 0 &&
            it.extension.lowercase() in extensions && (expectedExtension == null || it.extension.equals(expectedExtension, true)) && !parts.matches(it.name) && !merging.matches(it.name) &&
            !it.name.startsWith(".") }?.maxByOrNull { it.lastModified() }
    }
}
