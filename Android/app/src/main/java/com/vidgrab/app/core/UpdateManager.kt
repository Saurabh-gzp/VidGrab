package com.vidgrab.app.core

import android.content.Context
import android.content.Intent
import com.vidgrab.app.service.UpdateService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean

/** Updates only the extraction engine. The installed app itself is never replaced here. */
object UpdateManager {
    private const val RELEASE_API = "https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest"
    private const val RELEASE_PREFIX = "https://github.com/yt-dlp/yt-dlp/releases/download/"
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state
    private val checking = AtomicBoolean(false)
    private val serviceReserved = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    data class Release(val tag: String, val assetUrl: String, val assetSize: Long, val checksumUrl: String)

    fun isUpdating(): Boolean = serviceReserved.get() || _state.value is UpdateState.Updating
    fun setProgress(progress: Float, etaSec: Long, line: String) {
        _state.value = UpdateState.Updating(progress.coerceIn(0f, 100f), etaSec, line)
    }
    fun markDone() { _state.value = UpdateState.Done }
    fun markFailed() { _state.value = UpdateState.Failed }
    fun resetToIdle() { if (!serviceReserved.get()) _state.value = UpdateState.Idle }
    fun finishUpdate() { serviceReserved.set(false) }

    suspend fun checkOnLaunch(context: Context) = check(context.applicationContext, manual = false)

    fun manualCheck(context: Context) {
        scope.launch { check(context.applicationContext, manual = true) }
    }

    private suspend fun check(context: Context, manual: Boolean) {
        if (serviceReserved.get() || !checking.compareAndSet(false, true)) return
        try {
            _state.value = UpdateState.Checking
            val installed = withContext(Dispatchers.IO) {
                if (Engine.await()) Engine.engineVersion(context) else null
            } ?: error("Engine is not ready")
            val latest = runInterruptible(Dispatchers.IO) { fetchLatestRelease() }
                ?: error("Could not check for updates")
            if (!isNewer(latest.tag, installed)) {
                _state.value = if (manual) UpdateState.Done else UpdateState.Idle
                return
            }
            if (!serviceReserved.compareAndSet(false, true)) return
            setProgress(0f, -1, "Preparing engine update…")
            try {
                context.startForegroundService(Intent(context, UpdateService::class.java))
            } catch (e: Exception) {
                finishUpdate()
                throw e
            }
        } catch (e: CancellationException) {
            if (!serviceReserved.get()) _state.value = UpdateState.Idle
            throw e
        } catch (_: Exception) {
            if (!serviceReserved.get()) _state.value = if (manual) UpdateState.Failed else UpdateState.Idle
        } finally {
            checking.set(false)
        }
    }

    /** Both assets must originate from the official, tagged release. */
    fun fetchLatestRelease(): Release? = try {
        val json = JSONObject(readText(RELEASE_API, 1_000_000))
        val tag = json.optString("tag_name").takeIf { parseVersion(it) != null }
            ?: error("Invalid release version")
        var binaryUrl: String? = null
        var binarySize = 0L
        var checksumUrl: String? = null
        val assets = json.getJSONArray("assets")
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val url = asset.optString("browser_download_url")
            require(url.startsWith(RELEASE_PREFIX + tag + "/")) { "Unexpected asset origin" }
            when (asset.optString("name")) {
                "yt-dlp" -> { binaryUrl = url; binarySize = asset.optLong("size") }
                "SHA2-256SUMS" -> checksumUrl = url
            }
        }
        require(binarySize in 1_000_000L..30_000_000L) { "Invalid engine size" }
        Release(tag, requireNotNull(binaryUrl), binarySize, requireNotNull(checksumUrl))
    } catch (e: CancellationException) { throw e }
      catch (_: Exception) { null }

    fun expectedChecksum(release: Release): String? = try {
        checksumFor(readText(release.checksumUrl, 256_000), "yt-dlp")
    } catch (e: CancellationException) { throw e }
      catch (_: Exception) { null }

    /** Parse the published checksum list; a similarly named platform binary must not match. */
    internal fun checksumFor(text: String, fileName: String): String? = text.lineSequence().mapNotNull { line ->
        val match = Regex("^([A-Fa-f0-9]{64})\\s+\\*?(.+)$").matchEntire(line.trim())
        match?.takeIf { it.groupValues[2] == fileName }?.groupValues?.get(1)?.lowercase()
    }.singleOrNull()

    internal fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** A short response or checksum mismatch is a failure, never an installable update. */
    fun downloadAsset(release: Release, expectedSha256: String, dest: File,
                      onProgress: (bytesRead: Long, totalBytes: Long) -> Unit): Boolean {
        var connection: HttpURLConnection? = null
        var complete = false
        return try {
            require(Regex("[a-fA-F0-9]{64}").matches(expectedSha256))
            require(release.assetUrl.startsWith(RELEASE_PREFIX))
            connection = connect(release.assetUrl)
            require(connection.responseCode == HttpURLConnection.HTTP_OK)
            val headerSize = connection.contentLengthLong
            require(headerSize < 0 || headerSize == release.assetSize) { "Unexpected download length" }
            dest.parentFile?.mkdirs()
            var copied = 0L
            connection.inputStream.use { input ->
                dest.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (Thread.currentThread().isInterrupted) throw InterruptedException()
                        val count = input.read(buffer)
                        if (count < 0) break
                        copied += count
                        require(copied <= release.assetSize) { "Download exceeds release size" }
                        out.write(buffer, 0, count)
                        onProgress(copied, release.assetSize)
                    }
                }
            }
            require(copied == release.assetSize) { "Incomplete engine download" }
            complete = sha256(dest).equals(expectedSha256, ignoreCase = true)
            complete
        } catch (e: CancellationException) { throw e }
          catch (_: Exception) { false }
          finally {
              connection?.disconnect()
              if (!complete) dest.delete()
          }
    }

    private fun connect(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 20_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "VidGrab")
        setRequestProperty("Accept", if (url == RELEASE_API) "application/vnd.github+json" else "application/octet-stream")
        setRequestProperty("Accept-Encoding", "identity")
    }

    private fun readText(url: String, limit: Int): String {
        val connection = connect(url)
        try {
            require(connection.responseCode == HttpURLConnection.HTTP_OK)
            return connection.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    if (Thread.currentThread().isInterrupted) throw InterruptedException()
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(out.size() + count <= limit) { "Response too large" }
                    out.write(buffer, 0, count)
                }
                out.toString(Charsets.UTF_8.name())
            }
        } finally { connection.disconnect() }
    }

    fun isNewer(latest: String, installed: String): Boolean {
        val next = parseVersion(latest) ?: return false
        return next > (parseVersion(installed) ?: LocalDate.MIN)
    }

    private fun parseVersion(value: String): LocalDate? {
        val match = Regex("^v?(\\d{4})\\.(\\d{1,2})\\.(\\d{1,2})(?:[-+].*)?$").matchEntire(value.trim()) ?: return null
        return runCatching { LocalDate.of(match.groupValues[1].toInt(), match.groupValues[2].toInt(), match.groupValues[3].toInt()) }.getOrNull()
    }
}
