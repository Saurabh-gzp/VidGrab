package com.vidgrab.app.ui.screens

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vidgrab.app.core.*
import com.vidgrab.app.ui.components.*
import com.vidgrab.app.ui.theme.LocalAurora
import com.vidgrab.app.util.MediaSaver
import com.vidgrab.app.util.UrlUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DownloadsScreen() {
    val a = LocalAurora.current
    val active by DownloadRepository.active.collectAsState()
    val history by DownloadRepository.history.collectAsState()
    var filter by rememberSaveable { mutableStateOf("All") }
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(history, filter, query) { history.filter { entry ->
        (filter == "All" || (filter == "Audio") == entry.mime.startsWith("audio")) && entry.title.contains(query, ignoreCase = true)
    } }
    val running = remember(active, filter, query) { active.filter {
        (filter == "All" || (filter == "Audio") == (it.mimeHint == "audio")) && it.title.contains(query, ignoreCase = true)
    } }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 680.dp).fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item("heading") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Library", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(4.dp))
                        Text("All your favorites, always with you.", style = MaterialTheme.typography.bodySmall, color = a.textDim)
                    }
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(a.primary.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                        Icon(VidIcons.VideoLibrary, null, Modifier.size(22.dp), tint = a.primary)
                    }
                }
                Spacer(Modifier.height(20.dp))
                Surface(shape = RoundedCornerShape(21.dp), color = a.surface, border = androidx.compose.foundation.BorderStroke(1.dp, a.glassBorder)) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 17.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        LibraryStat(history.count { !it.mime.startsWith("audio") }.toString(), "Videos")
                        Box(Modifier.width(1.dp).height(35.dp).background(a.glassBorder))
                        LibraryStat(history.count { it.mime.startsWith("audio") }.toString(), "Audio")
                        Box(Modifier.width(1.dp).height(35.dp).background(a.glassBorder))
                        LibraryStat(if (history.isEmpty()) "0 MB" else UrlUtils.formatBytes(history.sumOf { it.sizeBytes }), "Saved")
                    }
                }
                Spacer(Modifier.height(18.dp))
                StudioInput(query, { query = it }, "Search your downloads", Icons.Default.Search)
                Spacer(Modifier.height(13.dp))
                StudioTabs(listOf("All files", "Video", "Audio"), listOf("All", "Video", "Audio").indexOf(filter), { filter = listOf("All", "Video", "Audio")[it] })
            }
            if (running.isNotEmpty()) {
                item("queue_label") { SectionHeading("Download queue") }
                items(running, key = { "active_${it.id}" }) { ActiveCard(it) }
            }
            if (shown.isNotEmpty()) {
                item("saved_label") {
                    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) { Text("Saved media", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); Text("${shown.size} files", style = MaterialTheme.typography.bodySmall, color = a.textDim) }
                }
                items(shown, key = { "history_${it.id}" }) { HistoryCard(it) }
            }
            if (shown.isEmpty() && running.isEmpty()) item("empty") {
                EmptyState(VidIcons.VideoLibrary,
                    if (query.isNotBlank()) "No matching downloads" else if (filter != "All") "No ${filter.lowercase()} saved yet" else "Keep something good.",
                    if (query.isNotBlank()) "Try another title or clear your search." else "Paste a link on Home. Your saved videos and audio will live here.")
            }
            item("bottom") { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun LibraryStat(value: String, label: String) {
    val a = LocalAurora.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = a.text)
        Spacer(Modifier.height(3.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = a.textDim)
    }
}

@Composable
private fun ActiveCard(item: DownloadItem) {
    val a = LocalAurora.current
    val pending = item.status.isPending()
    val progress by animateFloatAsState((item.progress / 100f).coerceIn(0f, 1f), tween(300), label = "download")
    Surface(shape = RoundedCornerShape(22.dp), color = a.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (item.status == DlStatus.FAILED) a.danger.copy(alpha = .4f) else a.glassBorder)) {
        Column(Modifier.fillMaxWidth().padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MediaArtwork(item.thumbnailPath, item.thumbnailUrl, item.title, Modifier.size(66.dp, 54.dp).clip(RoundedCornerShape(11.dp)), item.mimeHint == "audio")
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(item.formatLabel, style = MaterialTheme.typography.bodySmall, color = a.textDim)
                }
                IconButton(onClick = { if (pending) DownloadRepository.cancel(item.id) else DownloadRepository.dismiss(item.id) }) {
                    Icon(Icons.Default.Clear, if (pending) "Cancel download" else "Dismiss download", tint = a.textDim, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(14.dp))
            val status = when (item.status) {
                DlStatus.QUEUED -> "Waiting in queue"
                DlStatus.FETCHING -> "Connecting to the source…"
                DlStatus.DOWNLOADING -> if (item.downloadedBytes <= 0) "Connecting to the source…" else if (item.progressKnown) "${item.progress.toInt()}% · ${UrlUtils.formatBytes(item.downloadedBytes)} of ${if (item.totalIsApprox) "~" else ""}${UrlUtils.formatBytes(item.totalBytes)}" else "${UrlUtils.formatBytes(item.downloadedBytes)} received"
                DlStatus.MERGING -> "Finishing up and saving…"
                DlStatus.DONE -> "Saved to your device"
                DlStatus.FAILED -> item.error ?: "Download couldn't finish. Try again."
                DlStatus.CANCELLED -> "Cancelled"
            }
            Text(status, style = MaterialTheme.typography.bodySmall, color = if (item.status == DlStatus.FAILED) a.danger else a.textDim)
            if (pending) {
                Spacer(Modifier.height(9.dp))
                if (item.status == DlStatus.DOWNLOADING && item.downloadedBytes > 0 && item.progressKnown) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)), color = a.primary, trackColor = a.surfaceHigh)
                    Spacer(Modifier.height(7.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(UrlUtils.formatSpeed(item.speedBps), style = MaterialTheme.typography.labelSmall, color = a.textDim)
                        Text(UrlUtils.formatEta(item.etaSec), style = MaterialTheme.typography.labelSmall, color = a.textDim)
                    }
                } else LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp), color = a.primary, trackColor = a.surfaceHigh)
            }
            if (item.status == DlStatus.FAILED) {
                TextButton(onClick = { DownloadRepository.retry(item.id) }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                    Icon(VidIcons.Update, null, Modifier.size(16.dp)); Spacer(Modifier.width(7.dp)); Text("Try again")
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(entry: HistoryEntry) {
    val a = LocalAurora.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val date = remember(entry.completedAtMs) { SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(entry.completedAtMs)) }
    fun open(share: Boolean) { scope.launch { launchSaved(ctx, entry, share) } }
    Surface(onClick = { open(false) }, shape = RoundedCornerShape(20.dp), color = a.surface, border = androidx.compose.foundation.BorderStroke(1.dp, a.glassBorder.copy(alpha = .65f))) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(94.dp, 76.dp).clip(RoundedCornerShape(12.dp))) {
                MediaArtwork(entry.thumbnailPath, entry.thumbnailUrl, entry.title, Modifier.fillMaxSize(), entry.mime.startsWith("audio"))
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(5.dp))
                Text(entry.formatLabel.ifBlank { if (entry.mime.startsWith("audio")) "Audio" else "Video" }, color = a.primary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${UrlUtils.formatBytes(entry.sizeBytes)} · $date", color = a.textDim, style = MaterialTheme.typography.bodySmall)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More options for ${entry.title}", tint = a.textDim) }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Open") }, leadingIcon = { Icon(Icons.Default.PlayArrow, null) }, onClick = { menu = false; open(false) })
                    DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Default.Share, null) }, onClick = { menu = false; open(true) })
                    DropdownMenuItem(text = { Text("Delete", color = a.danger) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = a.danger) }, onClick = { menu = false; deleting = true })
                }
            }
        }
    }
    if (deleting) ConfirmDialog(title = "Delete this download?", body = "“${entry.title.take(80)}” will be deleted from your device.",
        onConfirm = { deleting = false; DownloadRepository.deleteHistory(entry) }, onDismiss = { deleting = false })
}

private suspend fun launchSaved(ctx: Context, entry: HistoryEntry, share: Boolean) {
    val uri = withContext(Dispatchers.IO) { MediaSaver.uriFor(ctx, entry.filePath, entry.contentUri) }
    if (uri == null) { Toast.makeText(ctx, "This file was moved or removed from your device.", Toast.LENGTH_LONG).show(); return }
    try {
        val intent = if (share) Intent(Intent.ACTION_SEND).apply {
            type = entry.mime; putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(entry.title, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, entry.mime); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        ctx.startActivity((if (share) Intent.createChooser(intent, "Share download") else intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) { Toast.makeText(ctx, if (share) "Couldn't share this file." else "No compatible player is installed for this file.", Toast.LENGTH_LONG).show() }
}
