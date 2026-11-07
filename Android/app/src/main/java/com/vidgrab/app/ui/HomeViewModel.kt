package com.vidgrab.app.ui

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vidgrab.app.core.*
import com.vidgrab.app.util.*
import kotlinx.coroutines.*

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    var url by mutableStateOf(""); private set
    var fetching by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var meta by mutableStateOf<VideoMeta?>(null); private set
    var formats by mutableStateOf<List<FormatOption>>(emptyList()); private set
    var selectedId by mutableStateOf<String?>(null); private set
    var enqueuing by mutableStateOf(false); private set
    var addedToQueue by mutableStateOf(false); private set
    private var fetchJob: Job? = null
    private var generation = 0
    private val recent = object : LinkedHashMap<String, Fetcher.FetchResult>(8, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Fetcher.FetchResult>?) = size > 6
    }

    fun onUrlChange(value: String) {
        if (url == value) return
        generation++
        fetchJob?.cancel()
        url = value; error = null; meta = null; formats = emptyList()
        selectedId = null; fetching = false; addedToQueue = false
    }
    fun clearAll() = onUrlChange("")
    fun cancelFetch() { generation++; fetchJob?.cancel(); fetching = false }
    fun select(id: String) { selectedId = id; addedToQueue = false }

    fun fetch(rawUrl: String? = null) {
        val target = try { UrlUtils.normalize(rawUrl ?: url) } catch (e: Exception) {
            error = e.message; return
        }
        if (target.isBlank()) return
        onUrlChange(target)
        fetchJob?.cancel()
        val request = ++generation
        fetching = true; error = null; meta = null; formats = emptyList(); addedToQueue = false
        fetchJob = viewModelScope.launch {
            try {
                check(withContext(Dispatchers.IO) { Engine.await() }) { Engine.lastError ?: "Engine is not ready. Reopen the app." }
                val cached = recent[target]?.takeIf { System.currentTimeMillis() - it.fetchedAtMs in 0..5 * 60 * 1000L }
                val result = cached ?: Engine.withEngine { Fetcher.fetchInfo(getApplication(), target) }.also { recent[target] = it }
                ensureActive()
                if (request != generation) return@launch
                meta = result.meta; formats = result.formats
                selectedId = formats.firstOrNull { it.id == "best" }?.id ?: formats.firstOrNull()?.id
                fetching = false
                if (formats.isEmpty()) error = "No downloadable formats found"
                val artwork = ThumbnailStore.capture(getApplication(), result.meta.webpageUrl, result.meta.thumbnailUrl)
                if (request == generation && artwork != null) meta = result.meta.copy(thumbnailPath = artwork)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (request == generation) error = UrlUtils.friendlyEngineError(e.message ?: "Could not fetch this link") }
            finally { if (request == generation) fetching = false }
        }
    }
    fun download() {
        val info = meta ?: return
        val format = formats.firstOrNull { it.id == selectedId } ?: return
        if (enqueuing || addedToQueue) return
        enqueuing = true
        viewModelScope.launch {
            try {
                DownloadRepository.enqueue(info.webpageUrl, info, format)
                if (meta?.webpageUrl == info.webpageUrl) addedToQueue = true
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Could not start the download. Try again." }
            finally { enqueuing = false }
        }
    }
}
