package com.vidgrab.app.core

import android.content.Context
import android.util.Log
import com.vidgrab.app.service.DownloadService
import com.vidgrab.app.util.MediaSaver
import com.vidgrab.app.util.UrlUtils
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.vidgrab.app.util.ThumbnailStore
import com.vidgrab.app.util.DownloadFiles
import com.vidgrab.app.util.MetadataSnapshots
import com.vidgrab.app.util.MediaInspector
import com.vidgrab.app.util.Fetcher
import com.vidgrab.app.util.TransferProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runInterruptible
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Download brain:
 *  - Queue channel — DownloadService worker consume karta hai
 *  - Sab state StateFlow se — UI instantly refresh
 *  - Active queue persist — process kill/restart par resume
 *
 * v2.2.1 ROBUSTNESS (user feedback: "2 baar download + Finalizing stuck"):
 *  - Inactivity watchdog — 10 min tak koi progress line nahi → process kill → salvage
 *  - Salvage merge — yt-dlp ka merge fail/skip ho to AAPKA khud ka ffmpeg
 *    (libffmpeg.so) video+audio parts ko merge karta hai
 *  - No blind retry — download ke baad ka failure (merge etc.) kabhi poora
 *    download dobara NAHI karta; pehle already-downloaded file salvage karo
 *  - Cancel vs watchdog clearly flagged (message-string matching hataya)
 */
object DownloadRepository {

    private const val TAG = "DownloadRepo"

    /** Itni der tak ek bhi progress line nahi → process ko kill karke salvage karo */
    private const val INACTIVITY_TIMEOUT_MS = 10 * 60 * 1000L

    /** Apne ffmpeg salvage merge ka hard timeout */
    private const val SALVAGE_TIMEOUT_MS = 10 * 60 * 1000L

    /** yt-dlp part files: "Title [id].f137.mp4" style */
    private val PART_RX = Regex("""^(.+)\.f\d+\.[A-Za-z0-9]+$""")

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _active = MutableStateFlow<List<DownloadItem>>(emptyList())
    val active: StateFlow<List<DownloadItem>> = _active

    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val history: StateFlow<List<HistoryEntry>> = _history

    internal val queue = Channel<DownloadItem>(Channel.UNLIMITED)

    private lateinit var appCtx: android.app.Application
    private val initialized = AtomicBoolean(false)
    private val ready = CompletableDeferred<Unit>()
    private val persistMutex = Mutex()
    private var persistJob: kotlinx.coroutines.Job? = null

    /** Monotonic seq — same millisecond mein bhi id kabhi collide nahi kar sakta */
    private val idSeq = AtomicLong(0)

    // ----- v2.2.1 per-item runtime state -----
    private val lastActivityMs = ConcurrentHashMap<String, Long>()
    private val watchdogFired = ConcurrentHashMap<String, Boolean>()
    private val progressReaders = ConcurrentHashMap<String, TransferProgress>()
    private val runningJobs = ConcurrentHashMap<String, Job>()
    private val userCancelled = ConcurrentHashMap<String, Boolean>()

    fun initOnce(ctx: Context) {
        if (initialized.compareAndSet(false, true)) {
            appCtx = ctx.applicationContext as android.app.Application
            // DataStore load main-thread block na kare — background mein
            appScope.launch {
                try {
                    val stored = Prefs.loadDownloads(appCtx)
                    _history.value = stored.history.distinctBy { it.id }
                    _active.value = stored.active.distinctBy { it.id }.filter { it.status != DlStatus.DONE && it.status != DlStatus.CANCELLED }
                        .map { if (it.status.isPending()) it.copy(status = DlStatus.QUEUED, speedBps = 0, etaSec = -1) else it }
                    _active.value.filter { it.status == DlStatus.QUEUED }.forEach { queue.send(it) }
                    File(appCtx.filesDir, "downloads").mkdirs()
                } finally { ready.complete(Unit) }
                // Backfill old libraries without making startup wait for network requests.
                _history.value.forEach { entry ->
                    val uri = MediaSaver.uriFor(appCtx, entry.filePath, entry.contentUri)
                    if (!entry.metadataVerified && uri != null) {
                        val facts = MediaInspector.inspect(appCtx, uri, entry.mime.startsWith("video"))
                        if (facts != null) _history.update { all -> all.map { old ->
                            if (old.id != entry.id) old else old.copy(sizeBytes = facts.bytes ?: old.sizeBytes,
                                pixelWidth = facts.width, pixelHeight = facts.height,
                                formatLabel = MediaInspector.quality(facts.width, facts.height, old.formatLabel),
                                metadataVerified = true)
                        } }
                    }
                    if (entry.thumbnailPath?.let { File(it).isFile } != true) {
                        val art = ThumbnailStore.capture(appCtx, entry.id, entry.thumbnailUrl, uri, preferLocal = true)
                        if (art != null) _history.update { all -> all.map { if (it.id == entry.id) it.copy(thumbnailPath = art) else it } }
                    }
                }
                persist()
            }
        }
    }

    /** UI se entry point — service start + queue */
    suspend fun enqueue(
        url: String,
        meta: VideoMeta,
        format: FormatOption
    ) {
        ready.await()
        val snapshot = withContext(Dispatchers.IO) { MetadataSnapshots.save(appCtx, meta) }
        val item = DownloadItem(
            // CRASH FIX: seq counter ke sath id — do items ka key kabhi same nahi
            id = "dl_${System.currentTimeMillis()}_${idSeq.incrementAndGet()}",
            url = meta.webpageUrl.ifBlank { url },
            title = meta.title,
            thumbnailUrl = meta.thumbnailUrl,
            thumbnailPath = meta.thumbnailPath,
            status = DlStatus.QUEUED,
            formatLabel = format.label,
            selector = format.selector,
            postArgs = format.postArgs,
            clientArgs = meta.clientArgs,
            totalBytes = format.transferBytes ?: 0L,
            totalIsApprox = format.transferIsApprox,
            infoSnapshotPath = snapshot,
            fetchedAtMs = meta.fetchedAtMs,
            selectedOptionId = format.id,
            selectedStreams = format.selectedStreams,
            pixelWidth = format.pixelWidth, pixelHeight = format.pixelHeight,
            mimeHint = if (format.isAudioOnly) "audio" else "video",
            height = format.height
        )
        _active.update { it + item }
        try { persistNow() } catch (e: Exception) {
            _active.update { all -> all.filterNot { it.id == item.id } }
            throw e
        }
        queue.send(item)
        try { DownloadService.start(appCtx) }
        catch (e: Exception) { fail(item, "Could not start background download. Reopen the app and retry."); throw e }
    }

    suspend fun awaitReady() { ready.await() }

    suspend fun resumePending() {
        ready.await()
        if (_active.value.any { it.status.isPending() }) DownloadService.start(appCtx)
    }

    fun retry(id: String) {
        appScope.launch {
            ready.await()
            val old = _active.value.firstOrNull { it.id == id && it.status == DlStatus.FAILED } ?: return@launch
            userCancelled.remove(id)
            val item = old.copy(status = DlStatus.QUEUED, error = null, speedBps = 0, etaSec = -1)
            patch(id) { item }
            queue.send(item)
            try { DownloadService.start(appCtx) } catch (_: Exception) { fail(item, "Open the app and retry.") }
        }
    }

    fun cancel(id: String) {
        userCancelled[id] = true
        _active.update { all -> all.filterNot { it.id == id } }
        persist()
        val running = runningJobs[id]
        running?.cancel(CancellationException("Cancelled by user"))
        appScope.launch {
            runCatching { YoutubeDL.getInstance().destroyProcessById(id) }
            running?.join()
            File(File(appCtx.filesDir, "downloads"), id).deleteRecursively()
        }
    }

    /** Active failed/queued item hatao (swipe-dismiss style) */
    fun dismiss(id: String) {
        _active.update { all -> all.filterNot { it.id == id } }
        persist()
    }

    /** History se delete — file + MediaStore entry + instant UI refresh */
    fun deleteHistory(entry: HistoryEntry) {
        appScope.launch {
            val deleted = MediaSaver.deleteByPath(appCtx, entry.filePath, entry.contentUri)
            if (!deleted) return@launch
            _history.update { all -> all.filterNot { it.id == entry.id } }
            persist()
        }
    }

    fun clearHistory() {
        _history.value = emptyList()
        persist()
    }

    // ------------- worker (DownloadService se chalaya jata hai) -------------

    suspend fun executeOne(item: DownloadItem) {
        ready.await()
        if (_active.value.none { it.id == item.id && it.status.isPending() } || userCancelled[item.id] == true) return
        watchdogFired.remove(item.id)
        try {
            coroutineScope {
                runningJobs[item.id] = currentCoroutineContext().job
                Engine.withEngine {
                    if (userCancelled[item.id] != true && _active.value.any { it.id == item.id }) executeWithRetry(item, attempt = 1)
                }
            }
        } catch (e: CancellationException) {
            runCatching { YoutubeDL.getInstance().destroyProcessById(item.id) }
            if (userCancelled[item.id] != true) {
                withContext(NonCancellable) {
                    if (_history.value.any { it.id == item.id }) {
                        _active.update { all -> all.filterNot { it.id == item.id } }
                    } else if (_active.value.any { it.id == item.id }) {
                        patch(item.id) { it.copy(status = DlStatus.QUEUED) }
                        queue.trySend(item)
                    }
                    runCatching { persistNow() }
                }
                throw e
            }
        } finally {
            runningJobs.remove(item.id)
            progressReaders.remove(item.id)
            if (userCancelled.remove(item.id) == true) File(File(appCtx.filesDir, "downloads"), item.id).deleteRecursively()
            watchdogFired.remove(item.id)
        }
    }

    private suspend fun executeWithRetry(input: DownloadItem, attempt: Int) {
        if (userCancelled[input.id] == true || _active.value.none { it.id == input.id }) return
        val id = input.id
        if (!Engine.await()) {
            fail(input, UrlUtils.friendlyEngineError(Engine.lastError ?: "Engine not ready")); return
        }
        patch(id) { it.copy(status = DlStatus.FETCHING, progress = 0f, progressKnown = false, speedBps = 0) }
        lastActivityMs[id] = System.currentTimeMillis()
        val dir = File(File(appCtx.filesDir, "downloads"), id).apply { mkdirs() }
        DownloadFiles.completedOutput(dir, input.mimeHint == "audio", audioExtension(input))?.let { finalizeItem(input, it); return }
        val item = try {
            if (MetadataSnapshots.valid(input.infoSnapshotPath, input.fetchedAtMs) != null) input
            else refreshMetadata(input)
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            fail(input, UrlUtils.friendlyEngineError(e.message ?: "Could not refresh media link")); return
        }
        val before = dir.listFiles()?.map { it.absolutePath }?.toSet() ?: emptySet()
        val snapshot = MetadataSnapshots.valid(item.infoSnapshotPath, item.fetchedAtMs)
        val req = if (snapshot != null) YoutubeDLRequest(emptyList<String>()).apply {
            addOption("--load-info-json", snapshot.absolutePath)
        } else YoutubeDLRequest(item.url)
        req.addOption("--cache-dir", File(appCtx.cacheDir, "engine").apply { mkdirs() }.absolutePath)
        req.addOption("--progress")
        req.addOption("--progress-delta", "0.25")
        req.addOption("--progress-template", TransferProgress.TEMPLATE)
        progressReaders[id] = TransferProgress(item.totalBytes, item.totalIsApprox, item.selectedStreams.map { it.formatId }.toSet())
        req.addOption("-o", File(dir, "%(title).70s [%(id)s].%(ext)s").absolutePath)
        req.addOption("-f", item.selector)
        req.addOption("--no-playlist")
        req.addOption("--no-warnings")
        req.addOption("--no-mtime")
        req.addOption("--newline")
        req.addOption("--retries", "5")
        req.addOption("--fragment-retries", "10")
        req.addOption("--socket-timeout", "20")
        req.addOption("--concurrent-fragments", "4")
        if (item.mimeHint == "video") req.addOption("--merge-output-format", "mp4")
        // RETRY attempt par extractor-args hata do — fresh default client se try
        val useClientArgs = if (attempt > 1) null else item.clientArgs
        if (useClientArgs != null) req.addOption("--extractor-args", useClientArgs)
        // postArgs ko flag/value pairs mein todo
        var ai = 0
        val pa = item.postArgs
        while (ai < pa.size) {
            val a = pa[ai]
            if (a.startsWith("--") && ai + 1 < pa.size && !pa[ai + 1].startsWith("--")) {
                req.addOption(a, pa[ai + 1]); ai += 2
            } else {
                req.addOption(a); ai += 1
            }
        }

        val watchdog = startWatchdog(item.id)
        try {
            val resp = runInterruptible(Dispatchers.IO) {
                YoutubeDL.getInstance().execute(req, item.id) { progress: Float, etaSec: Long, line: String? ->
                    onUpdateLine(item.id, progress, etaSec, line ?: "")
                }
            }
            watchdog.cancel()

            if (resp.exitCode != 0) {
                // v2.2.1 FIX: exit-code!=0 par ANDHA retry NAHI — pehle dekho file
                // download hui hai (merge fail hua hoga). Mile to finalize karo,
                // poora re-download user ko kabhi na dikhe.
                val salvaged = salvageDeliverable(dir, item, before)
                if (salvaged != null) {
                    Log.w(TAG, "exit=${resp.exitCode} par deliverable mila — salvage finalize")
                    finalizeItem(item, salvaged)
                } else {
                    val err = resp.err?.takeIf { it.isNotBlank() } ?: resp.out ?: ""
                    handleFailure(item, err, attempt)
                }
                return
            }

            var file = findOutput(dir, item, before)
            if (file == null) {
                // yt-dlp "succeed" hai par merged file nahi — parts alag pade hain
                file = runInterruptible { salvageMerge(item, dir) }
            }
            if (file == null) {
                handleFailure(item, "Output file not found after download", attempt)
                return
            }
            finalizeItem(item, file)
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            watchdog.cancel()
            when {
                userCancelled[item.id] == true -> {
                    _active.update { all -> all.filterNot { it.id == item.id } }
                    persist()
                }
                // v2.2.1 FIX: library "Canceled" (one-L) message bhi bhej sakti hai —
                // string matching pe bharosa nahi, flags use karte hain
                watchdogFired[item.id] == true -> {
                    val salvaged = salvageDeliverable(dir, item, before)
                    if (salvaged != null) {
                        Log.w(TAG, "watchdog fired — salvaged deliverable, finalizing")
                        finalizeItem(item, salvaged)
                    } else {
                        fail(item, "Download stalled too long — please try again")
                    }
                }
                else -> {
                    val msg = e.message ?: ""
                    Log.e(TAG, "download failed: $msg")
                    handleFailure(item, msg, attempt)
                }
            }
        } finally {
            watchdog.cancel()
            lastActivityMs.remove(item.id)
            watchdogFired.remove(item.id)
        }
    }

    /** Success path ka last mile — rename + save + history + cleanup */
    private suspend fun finalizeItem(item: DownloadItem, rawFile: File) {
        if (userCancelled[item.id] == true || _active.value.none { it.id == item.id }) return
        patch(item.id) { it.copy(status = DlStatus.MERGING, progress = 99f) }
        val final = renameWithQuality(rawFile, item)
        val isAudio = item.mimeHint == "audio"
        val size = final.length()
        val entry = withContext(NonCancellable) {
            // Publishing and recording the result are one cancellation-safe operation.
            val saved = MediaSaver.save(appCtx, final, isAudio)
            if (saved == null) { fail(item, "Could not save the file. Free some storage and tap Retry."); return@withContext null }
            if (userCancelled[item.id] == true) {
                MediaSaver.deleteByPath(appCtx, saved.second, saved.first?.toString())
                return@withContext null
            }
            val facts = saved.first?.let { MediaInspector.inspect(appCtx, it, !isAudio) }
            val record = HistoryEntry(id = item.id, title = item.title, thumbnailUrl = item.thumbnailUrl,
                thumbnailPath = item.thumbnailPath?.takeIf { File(it).isFile },
                contentUri = saved.first?.toString(), filePath = saved.second,
                mime = MediaSaver.mimeOf(final.name, isAudio), sizeBytes = size,
                completedAtMs = System.currentTimeMillis(), url = item.url,
                pixelWidth = facts?.width ?: item.pixelWidth, pixelHeight = facts?.height ?: item.pixelHeight,
                metadataVerified = facts != null,
                formatLabel = MediaInspector.quality(facts?.width ?: item.pixelWidth, facts?.height ?: item.pixelHeight, item.formatLabel))
            _history.update { listOf(record) + it.filterNot { old -> old.id == item.id } }
            patch(item.id) { it.copy(status = DlStatus.DONE, progress = 100f, downloadedBytes = size, totalBytes = size, filePath = saved.second) }
            try { persistNow() } catch (e: Exception) {
                _history.update { all -> all.filterNot { it.id == item.id } }
                MediaSaver.deleteByPath(appCtx, saved.second, saved.first?.toString())
                fail(item, "Could not record the download. Free some storage and retry.")
                return@withContext null
            }
            // Delete private media only after the library record has reached persistent storage.
            final.parentFile?.deleteRecursively()
            _active.update { all -> all.filterNot { it.id == item.id } }
            runCatching { persistNow() }
            record
        } ?: return
        DownloadService.notifyResult(appCtx, true, item.title, "${item.formatLabel} · ${UrlUtils.formatBytes(size)} saved")
        if (entry.thumbnailPath == null) appScope.launch {
            val uri = if (isAudio) null else MediaSaver.uriFor(appCtx, entry.filePath, entry.contentUri)
            val art = ThumbnailStore.capture(appCtx, item.id, item.thumbnailUrl, uri)
            if (art != null) {
                _history.update { all -> all.map { if (it.id == item.id) it.copy(thumbnailPath = art) else it } }
                persist()
            }
        }
    }

    /** Transient error par EK automatic retry (clientArgs fresh), permanent par turant fail */
    private suspend fun handleFailure(item: DownloadItem, rawErr: String, attempt: Int) {
        val permanent = listOf(
            "does not exist", "private", "unsupported url", "video unavailable",
            "has been removed", "requested format", "copyright"
        ).any { rawErr.lowercase(Locale.ROOT).contains(it) }
        if (!permanent && attempt < 2) {
            Log.w(TAG, "retrying download (attempt 2): ${rawErr.takeLast(120)}")
            patch(item.id) { it.copy(status = DlStatus.QUEUED, progress = 0f, error = null) }
            delay(250)
            executeWithRetry(item.copy(clientArgs = null, infoSnapshotPath = null, fetchedAtMs = 0), attempt = 2)
            return
        }
        fail(item, UrlUtils.friendlyEngineError(rawErr))
    }

    /** Output ko "Title (Quality).ext" mein rename — MediaStore wahi naam dikhega */
    private fun renameWithQuality(src: File, item: DownloadItem): File {
        val ext = src.name.substringAfterLast('.', "mp4")
        val tag = UrlUtils.qualityTag(item.height, item.mimeHint == "audio", item.formatLabel)
        var base = UrlUtils.sanitizeFileName(item.title)
        var dest = File(src.parentFile, "$base ($tag).$ext")
        var n = 2
        while (dest.exists() && dest.absolutePath != src.absolutePath) {
            dest = File(src.parentFile, "$base ($tag) •$n.$ext")
            n++
        }
        return try {
            if (src.renameTo(dest)) dest else src
        } catch (_: Exception) {
            src
        }
    }

    private fun onUpdateLine(id: String, progress: Float, etaSec: Long, line: String) {
        lastActivityMs[id] = System.currentTimeMillis()
        val merging = line.contains("[Merger]", true) || line.contains("[ExtractAudio]", true) || line.contains("[Fixup", true)
        if (merging) { patch(id) { it.copy(status = DlStatus.MERGING, progressKnown = false) }; return }
        val update = progressReaders[id]?.parse(line) ?: return
        patch(id) {
            it.copy(status = if (update.started) DlStatus.DOWNLOADING else DlStatus.FETCHING,
                downloadedBytes = update.downloadedBytes, totalBytes = update.totalBytes,
                totalIsApprox = update.totalIsApprox, progress = update.progress ?: it.progress,
                progressKnown = update.progress != null, speedBps = update.speedBps, etaSec = update.etaSec)
        }
    }

    private suspend fun refreshMetadata(item: DownloadItem): DownloadItem {
        val fresh = Fetcher.fetchInfo(appCtx, item.url)
        val format = fresh.formats.firstOrNull { it.id == item.selectedOptionId }
            ?: fresh.formats.firstOrNull { it.isAudioOnly == (item.mimeHint == "audio") &&
                if (it.isAudioOnly) it.postArgs == item.postArgs else it.height == item.height }
            ?: error("Selected quality is no longer available. Fetch the link again.")
        val snapshot = MetadataSnapshots.save(appCtx, fresh.meta)
        val updated = item.copy(selector = format.selector, clientArgs = fresh.meta.clientArgs,
            selectedOptionId = format.id, selectedStreams = format.selectedStreams,
            infoSnapshotPath = snapshot, fetchedAtMs = fresh.meta.fetchedAtMs,
            totalBytes = format.transferBytes ?: 0, totalIsApprox = format.transferIsApprox,
            postArgs = format.postArgs, height = format.height, pixelWidth = format.pixelWidth,
            pixelHeight = format.pixelHeight, formatLabel = format.label)
        patch(item.id) { updated.copy(status = DlStatus.FETCHING, progressKnown = false) }
        return updated
    }

    private fun audioExtension(item: DownloadItem): String? {
        if (item.mimeHint != "audio") return null
        val index = item.postArgs.indexOf("--audio-format")
        return item.postArgs.getOrNull(index + 1).takeIf { index >= 0 && it != "best" }
    }

    private fun findOutput(dir: File, item: DownloadItem, before: Set<String>): File? =
        DownloadFiles.completedOutput(dir, item.mimeHint == "audio", audioExtension(item))

    // ================= v2.2.1 salvage layer =================

    /**
     * Download ke baad ka kuch bhi kharab ho to yahan se deliverable nikalo.
     * Order: (1) complete merged file snapshot-diff se, (2) parts ko khud merge.
     * Video request par audio-only file KABHI nahi (user ka original 2MB bug).
     */
    private suspend fun salvageDeliverable(dir: File, item: DownloadItem, before: Set<String>): File? {
        DownloadFiles.completedOutput(dir, item.mimeHint == "audio", audioExtension(item))?.let { return it }
        if (item.mimeHint == "audio") return null
        // Parts hain to khud merge karo
        return runInterruptible { salvageMerge(item, dir) }
    }

    /**
     * yt-dlp ka merge fail/skip ho gaya? Video-only + audio-only parts ko
     * AAPKE app ke libffmpeg.so se merge karo — timeout ke sath, full control.
     */
    private fun salvageMerge(item: DownloadItem, dir: File): File? {
        if (item.mimeHint != "video") return null
        if (Engine.ffmpegExecutable(appCtx) == null) return null

        val partFiles = dir.listFiles()
            ?.filter { it.isFile && it.length() > 0 && PART_RX.matches(it.name) }
            ?: return null
        if (partFiles.isEmpty()) return null

        // Newest group pehle — same video ke parts ka common base
        val newest = partFiles.maxByOrNull { it.lastModified() } ?: return null
        val base = PART_RX.find(newest.name)?.groupValues?.get(1) ?: return null
        val group = partFiles.filter { PART_RX.find(it.name)?.groupValues?.get(1) == base }
        if (group.size < 2) return null

        var video: File? = null
        var audio: File? = null
        var anyProbe = false
        for (f in group.sortedByDescending { it.length() }) {
            val s = probeStreams(f)
            if (s == null) continue
            anyProbe = true
            if (s.first && !s.second && video == null) video = f
            else if (s.second && !s.first && audio == null) audio = f
        }
        if (!anyProbe) {
            // ffprobe unavailable — size heuristic: badi = video, doosri = audio
            val sorted = group.sortedByDescending { it.length() }
            if (sorted.size >= 2) {
                video = sorted[0]
                audio = sorted[1]
            }
        }
        val v = video ?: return null
        val a = audio ?: return null

        // Out name: parts ka base — collision par suffix
        var out = File(dir, "$base.mp4")
        var n = 2
        while (out.exists()) { out = File(dir, "$base (merged $n).mp4"); n++ }

        val merged = runMerge(v, a, out, "mp4")
            ?: run {
                var out2 = File(dir, "$base.mkv")
                var k = 2
                while (out2.exists()) { out2 = File(dir, "$base (merged $k).mkv"); k++ }
                runMerge(v, a, out2, "mkv")
            }
            ?: return null

        // Sanity: merged file video part jitni to honi hi chahiye (+ audio)
        return if (merged.length() >= (v.length() * 0.9)) merged else {
            merged.delete()
            null
        }
    }

    /** ffmpeg stream-copy merge — (video, audio) -> container, timeout protected */
    private fun runMerge(v: File, a: File, out: File, format: String): File? {
        val args = mutableListOf(
            "-y", "-i", v.absolutePath, "-i", a.absolutePath,
            "-c", "copy", "-map", "0:v:0", "-map", "1:a:0"
        )
        if (format == "mp4") {
            args.add("-movflags")
            args.add("+faststart")
        }
        args.add("-f"); args.add(format)
        val temporary = File(out.parentFile, ".${out.name}.merging")
        args.add(temporary.absolutePath)
        val code = runNativeTool(args, SALVAGE_TIMEOUT_MS)
        return try {
            if (code == 0 && temporary.length() > 0 && temporary.renameTo(out)) out else null
        } finally { temporary.delete() }
    }

    /** ffprobe se pata karo file mein video/audio stream kaunsa hai — (hasVideo, hasAudio) */
    private fun probeStreams(f: File): Pair<Boolean, Boolean>? {
        val ffprobe = Engine.ffprobeExecutable(appCtx) ?: return null
        val out = StringBuilder()
        val code = runNativeToolCapturing(
            ffprobe,
            listOf(
                "-v", "error",
                "-show_entries", "stream=codec_type",
                "-of", "csv=p=0", f.absolutePath
            ), 30_000L, out
        ) ?: return null
        if (code != 0) return null
        val text = out.toString().lowercase(Locale.ROOT)
        return (text.contains("video") to text.contains("audio"))
    }

    /** Bootstrap ffmpeg chalao — timeout ke sath */
    private fun runNativeTool(args: List<String>, timeoutMs: Long): Int? {
        val ffmpeg = Engine.ffmpegExecutable(appCtx) ?: return null
        return runNativeToolCapturing(ffmpeg, args, timeoutMs, null)
    }

    private fun runNativeToolCapturing(exe: File, args: List<String>, timeoutMs: Long, capture: StringBuilder?): Int? {
        var running: Process? = null
        return try {
            val pb = ProcessBuilder(listOf(exe.absolutePath) + args)
                .redirectErrorStream(true)
            val env = pb.environment()
            env["LD_LIBRARY_PATH"] = Engine.ffmpegLdLibraryPath(appCtx)
            env["TMPDIR"] = appCtx.cacheDir.absolutePath
            val p = pb.start()
            running = p
            val reader = Thread {
                try {
                    p.inputStream.bufferedReader().forEachLine { line ->
                        if (capture != null && capture.length < 64_000) capture.appendLine(line)
                    }
                } catch (_: Exception) { }
            }
            reader.isDaemon = true
            reader.start()
            val done = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!done) {
                p.destroyForcibly()
                reader.join(2000)
                return null
            }
            reader.join(2000)
            p.exitValue()
        } catch (e: Exception) {
            Log.w(TAG, "native tool failed: ${e.message}")
            null
        } finally { running?.takeIf { it.isAlive }?.destroyForcibly() }
    }

    /** Success ke baad transient files clean karo — .fNNN parts, .part, .ytdl — 'keep' ko chhod ke */
    private fun cleanupParts(dir: File?, keep: File?) {
        if (dir == null) return
        dir.listFiles()?.forEach { f ->
            if (!f.isFile) return@forEach
            if (keep != null && f.absolutePath == keep.absolutePath) return@forEach
            val name = f.name
            val transient = PART_RX.matches(name) ||
                name.endsWith(".part") ||
                name.endsWith(".ytdl") ||
                name.contains(".part-Frag")
            if (transient) {
                try { f.delete() } catch (_: Exception) { }
            }
        }
    }

    /** 10 min se zyada koi line nahi aayi → process kill (catch side salvage karega) */
    private fun startWatchdog(id: String): Job = appScope.launch {
        try {
            while (isActive) {
                delay(30_000)
                val last = lastActivityMs[id] ?: return@launch
                if (System.currentTimeMillis() - last > INACTIVITY_TIMEOUT_MS) {
                    watchdogFired[id] = true
                    Log.w(TAG, "watchdog: ${INACTIVITY_TIMEOUT_MS / 60000}min inactivity — killing $id")
                    try { YoutubeDL.getInstance().destroyProcessById(id) } catch (_: Exception) { }
                    return@launch
                }
            }
        } catch (_: Exception) { }
    }

    private val VIDEO_EXTS = setOf("mp4", "mkv", "webm", "mov", "m4v", "3gp", "avi", "flv", "ts")
    private val AUDIO_EXTS = setOf("m4a", "mp3", "opus", "ogg", "wav", "aac", "flac")

    private fun fail(item: DownloadItem, msg: String) {
        patch(item.id) { it.copy(status = DlStatus.FAILED, error = msg) }
        // v2.2.2: "Download failed" dismissible notification
        if (::appCtx.isInitialized) {
            DownloadService.notifyResult(appCtx, success = false, title = item.title, detail = msg)
        }
        persist()
    }

    private fun patch(id: String, transform: (DownloadItem) -> DownloadItem) {
        _active.update { all -> all.map { if (it.id == id) transform(it) else it } }
        if (_active.value.none { it.id == id }) return
        persistThrottled()
    }

    private suspend fun persistNow() {
        persistMutex.withLock { Prefs.saveDownloads(appCtx, _active.value, _history.value) }
    }

    private fun persistThrottled() {
        synchronized(this) {
            if (persistJob?.isActive == true) return
            persistJob = appScope.launch {
                delay(1500)
                try { persistNow() } catch (e: Exception) { Log.w(TAG, "Could not persist progress", e) }
            }
        }
    }

    private fun persist() {
        if (!::appCtx.isInitialized) return
        appScope.launch {
            try { persistNow() } catch (e: Exception) { Log.w(TAG, "Could not persist downloads", e) }
        }
    }
}

fun DlStatus.isPending(): Boolean = this in setOf(DlStatus.QUEUED, DlStatus.FETCHING, DlStatus.DOWNLOADING, DlStatus.MERGING)
