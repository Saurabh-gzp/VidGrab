package com.vidgrab.app.util

import java.io.File
import java.io.OutputStream
import java.security.MessageDigest

/** Artwork is library data: callers pass filesDir, never the disposable cache directory. */
object ArtworkFiles {
    fun target(filesDir: File, key: String): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val directory = File(filesDir, "thumbnails")
        check(directory.isDirectory || directory.mkdirs()) { "Artwork storage unavailable" }
        return File(directory, "$hash.jpg")
    }

    fun publish(target: File, write: (OutputStream) -> Boolean) {
        val temporary = File.createTempFile("art-", ".tmp", target.parentFile)
        try {
            temporary.outputStream().use { check(write(it)) { "Artwork encoding failed" } }
            check(temporary.length() > 0) { "Empty artwork" }
            check(temporary.renameTo(target)) { "Artwork could not be saved" }
        } finally { temporary.delete() }
    }
}
