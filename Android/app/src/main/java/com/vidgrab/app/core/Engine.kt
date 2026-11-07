package com.vidgrab.app.core

import android.content.Context
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Extraction engine — one-time init, saare calls se pehle await().
 *
 * v2.1 FIX (download error root cause):
 *  - AAR ke andar bundled yt-dlp ~9+ mahine purana hota hai; YouTube us par
 *    extraction fail karta hai. Isliye APK ke assets mein LATEST yt-dlp zipapp
 *    ship karte hain aur init ke turant baad usko installed binary ke upar
 *    swap kar dete hain (yahi kaam library ka updateYoutubeDL network se karta
 *    hai — hum offline, instant, guaranteed karte hain).
 *  - Init errors ab chhupate nahi — await() false + lastError mein real reason.
 */
object Engine {
    private val gate = EngineGate()
    suspend fun <T> withEngine(block: suspend () -> T): T = gate.use(block)
    suspend fun <T> withExclusiveEngine(block: suspend () -> T): T = gate.replace(block)
    private const val TAG = "VidGrabEngine"

    /** Asset-bundled yt-dlp ka version — build time par latest release daala jata hai */
    const val BUNDLED_VERSION = "2026.10.26"

    private val latch = CountDownLatch(1)
    private val inited = AtomicBoolean(false)

    @Volatile
    var lastError: String? = null
        private set

    fun init(appCtx: Context) {
        if (!inited.compareAndSet(false, true)) {
            // pehla init ab bhi chal/finish chuka — latch pe await karo
            return
        }
        try {
            YoutubeDL.getInstance().init(appCtx)
        } catch (e: Exception) {
            lastError = "Engine init failed: ${e.message ?: e.javaClass.simpleName}"
            Log.e(TAG, lastError ?: "")
            latch.countDown()
            return
        }
        // v2.2 CRITICAL FIX: FFmpeg module init — iske bina merge/select-audio
        // chupchap fail hota hai: yt-dlp sirf WARNING deta hai ("formats won't
        // be merged") aur exit 0 ke sath video+audio ALAG-ALAG chhod deta hai.
        // Library har request mein --ffmpeg-location <nativeLib>/libffmpeg.so
        // deti hai, par ye bootstrap binary tabhi chalti hai jab
        // FFmpeg.init() ne libffmpeg.zip.so ke andar ki real libs extract ki hon.
        try {
            FFmpeg.getInstance().init(appCtx)
        } catch (e: Exception) {
            // Non-fatal — downloads phir bhi chalenge (sirf merge/MP3 weak hoga)
            Log.e(TAG, "FFmpeg init failed: ${e.message}")
        }
        // Bundled latest binary swap (best-effort — fail ho to purana chalta rahega,
        // par phir UpdateManager launch par network se update try karega)
        try {
            applyBundledBinary(appCtx)
        } catch (e: Exception) {
            Log.w(TAG, "bundled swap skipped: ${e.message}")
        }
        latch.countDown()
    }

    /**
     * Kisi bhi yt-dlp zipapp ko installed binary ke roop mein install karo
     * (tmp+rename atomic, chmod +x, marker update). Engine init aur
     * UpdateService dono isi se karte hain.
     */
    fun installBinary(ctx: Context, src: File, version: String): Boolean {
        val target = findYtDlpBinary(ctx) ?: return false
        if (!src.exists() || src.length() < 1_000_000L) return false
        // Sanity: zipapp ya shebang hona chahiye
        val head = ByteArray(2)
        src.inputStream().use { it.read(head, 0, 2) }
        val magic = String(head, Charsets.ISO_8859_1)
        if (magic != "PK" && !magic.startsWith("#")) return false

        val tmp = File(target.parentFile, "yt-dlp.new")
        try {
            src.inputStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
            tmp.setExecutable(true, false)
            if (!tmp.renameTo(target)) { tmp.delete(); return false }
        } catch (e: Exception) {
            tmp.delete()
            return false
        }
        File(target.parentFile, "yt-dlp.vidgrabver").writeText(version)
        Log.i(TAG, "yt-dlp $version installed (${target.length()} bytes)")
        return true
    }

    /**
     * Assets/ytdlp_bundle -> <noBackupFilesDir>/youtubedl-android/packages/yt-dlp
     * Marker file se pata hota hai ki ye version already applied hai.
     */
    private fun applyBundledBinary(ctx: Context) {
        val target = findYtDlpBinary(ctx) ?: return
        val marker = File(target.parentFile, "yt-dlp.vidgrabver")
        if (marker.exists() && !UpdateManager.isNewer(BUNDLED_VERSION, marker.readText().trim()) &&
            target.length() > 1_000_000L
        ) return // already applied

        val tmp = File(ctx.cacheDir, "ytdlp_bundle.bin")
        try {
            ctx.assets.open("ytdlp_bundle").use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            }
        } catch (_: Exception) {
            return
        }
        installBinary(ctx, tmp, BUNDLED_VERSION)
        tmp.delete()
    }

    /** Library ka installed yt-dlp binary dhundo (known path + fallback search) */
    fun findYtDlpBinary(ctx: Context): File? {
        val known = File(
            File(File(ctx.noBackupFilesDir, "youtubedl-android"), "packages"),
            "yt-dlp"
        )
        if (known.exists()) return known
        // fallback: noBackupFilesDir mein kahin "yt-dlp" naam ka >1MB file
        val found = walkForBinary(ctx.noBackupFilesDir, 0)
        return found
    }

    private fun walkForBinary(dir: File, depth: Int): File? {
        if (depth > 4) return null
        val kids = dir.listFiles() ?: return null
        for (k in kids) {
            if (k.isFile && k.name == "yt-dlp" && k.length() > 1_000_000L) return k
            if (k.isDirectory) walkForBinary(k, depth + 1)?.let { return it }
        }
        return null
    }

    fun await(): Boolean = try {
        val ok = latch.await(120, TimeUnit.SECONDS)
        ok && lastError == null
    } catch (_: InterruptedException) {
        false
    }

    // ---------- v2.2.1: salvage-merge support (apna ffmpeg directly chalana) ----------

    /** Bootstrap ffmpeg binary — nativeLibDir mein library ne ship kiya hai */
    fun ffmpegExecutable(ctx: Context): File? =
        try {
            File(ctx.applicationInfo.nativeLibraryDir, "libffmpeg.so").takeIf { it.exists() }
        } catch (_: Exception) {
            null
        }

    /** ffprobe — stream detection ke liye (salvage merge ka input classify karne mein) */
    fun ffprobeExecutable(ctx: Context): File? =
        try {
            File(ctx.applicationInfo.nativeLibraryDir, "libffprobe.so").takeIf { it.exists() }
        } catch (_: Exception) {
            null
        }

    /**
     * Library ke ProcessBuilder wala EXACT LD_LIBRARY_PATH formula —
     * bootstrap libffmpeg.so inhi paths se real libs (libavcodec.so.61 etc.) uthata hai.
     */
    fun ffmpegLdLibraryPath(ctx: Context): String = try {
        val packages = File(File(ctx.noBackupFilesDir, "youtubedl-android"), "packages")
        listOf("python", "ffmpeg", "aria2c").joinToString(":") { dir ->
            File(packages, "$dir/usr/lib").absolutePath
        }
    } catch (_: Exception) {
        ""
    }

    /** Engine ready hai ya nahi, bina block kiye (UI hints ke liye) */
    fun isReady(): Boolean = inited.get() && latch.count == 0L && lastError == null

    fun engineVersion(appCtx: Context): String? = try {
        val target = findYtDlpBinary(appCtx)
        val marker = target?.parentFile?.let { File(it, "yt-dlp.vidgrabver") }
        if (marker?.isFile == true && target?.isFile == true && target.length() > 1_000_000L)
            marker.readText().trim().takeIf { it.isNotBlank() }
        else YoutubeDL.getInstance().version(appCtx)
    } catch (_: Exception) { null }
}
