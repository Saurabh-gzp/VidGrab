package com.vidgrab.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidgrab.app.core.*
import com.vidgrab.app.ui.HomeViewModel
import com.vidgrab.app.ui.components.*
import com.vidgrab.app.ui.theme.LocalAurora
import com.vidgrab.app.util.UrlUtils

@Composable
fun HomeScreen(vm: HomeViewModel, incomingUrl: String?, onUrlConsumed: () -> Unit, onLibrary: () -> Unit = {}) {
    val a = LocalAurora.current
    val clipboard = LocalClipboardManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val history by DownloadRepository.history.collectAsState()
    LaunchedEffect(incomingUrl) { if (!incomingUrl.isNullOrBlank()) { vm.fetch(incomingUrl); onUrlConsumed() } }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 680.dp).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(Modifier.size(42.dp))
                Spacer(Modifier.width(11.dp))
                Column {
                    Text("VidGrab", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Your offline studio", style = MaterialTheme.typography.bodySmall, color = a.textDim)
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(44.dp).clip(CircleShape).background(a.surface).border(1.dp, a.glassBorder, CircleShape).clickable(onClick = onLibrary), contentAlignment = Alignment.Center) {
                    Icon(VidIcons.VideoLibrary, "Open library", Modifier.size(20.dp), tint = a.primary)
                }
            }
            Spacer(Modifier.height(20.dp))
            if (vm.meta == null && !vm.fetching) { StudioHero(); Spacer(Modifier.height(20.dp)) }
            if (vm.meta == null) {
                Surface(shape = RoundedCornerShape(24.dp), color = a.surface, border = androidx.compose.foundation.BorderStroke(1.dp, a.glassBorder)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("New download", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            TextButton(onClick = { clipboard.getText()?.text?.takeIf { it.isNotBlank() }?.let { vm.onUrlChange(UrlUtils.extractUrl(it) ?: it.trim()) } }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                Icon(VidIcons.Paste, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Paste link", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        StudioInput(vm.url, vm::onUrlChange, "Paste a video or audio link", VidIcons.Link, uri = true) { keyboard?.hide(); vm.fetch() }
                        Spacer(Modifier.height(12.dp))
                        GradientButton(if (vm.fetching) "Getting available formats…" else "Find my video", icon = Icons.Default.ArrowForward, enabled = vm.url.isNotBlank() && !vm.fetching, loading = vm.fetching) { keyboard?.hide(); vm.fetch() }
                        if (vm.fetching) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Connecting to the source", style = MaterialTheme.typography.bodySmall, color = a.textDim, modifier = Modifier.weight(1f))
                            TextButton(onClick = vm::cancelFetch) { Text("Cancel") }
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(a.surface).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(VidIcons.Link, null, Modifier.size(16.dp), tint = a.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(vm.url, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = a.textDim, modifier = Modifier.weight(1f))
                    TextButton(onClick = vm::clearAll) { Text("New link", style = MaterialTheme.typography.labelSmall) }
                }
            }
            vm.error?.let { error ->
                Spacer(Modifier.height(12.dp))
                Surface(color = a.danger.copy(alpha = .09f), shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.padding(15.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Info, null, Modifier.size(18.dp), tint = a.danger)
                        Column { Text("Couldn't open this link", style = MaterialTheme.typography.labelLarge, color = a.danger); Text(error, style = MaterialTheme.typography.bodySmall, color = a.text) }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            if (vm.fetching) {
                ShimmerBox(Modifier.fillMaxWidth().aspectRatio(16f / 9f), 22)
                Spacer(Modifier.height(12.dp)); ShimmerBox(Modifier.fillMaxWidth(.8f).height(16.dp), 7)
            } else if (vm.meta != null) ResultCard(vm, onLibrary)
            else {
                Text("From your favorite platforms", style = MaterialTheme.typography.bodySmall, color = a.textDim)
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("YouTube" to Color(0xFFFF5C69), "Instagram" to Color(0xFFE285CB), "TikTok" to Color(0xFF74D5E5), "Facebook" to Color(0xFF8CADFF), "X" to a.textDim)) { (name, color) ->
                        Row(Modifier.clip(RoundedCornerShape(11.dp)).background(a.surface).border(1.dp, a.glassBorder, RoundedCornerShape(11.dp)).padding(horizontal = 11.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(5.dp).clip(CircleShape).background(color)); Spacer(Modifier.width(7.dp)); Text(name, style = MaterialTheme.typography.labelSmall, color = a.text)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (history.isNotEmpty()) {
                    SectionHeading("Recently saved", "View all", onLibrary)
                    history.take(2).forEach { item ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onLibrary).padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            MediaArtwork(item.thumbnailPath, item.thumbnailUrl, item.title, Modifier.size(76.dp, 54.dp).clip(RoundedCornerShape(12.dp)), item.mime.startsWith("audio"))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(4.dp)); Text("${item.formatLabel} · ${UrlUtils.formatBytes(item.sizeBytes)}", style = MaterialTheme.typography.bodySmall, color = a.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(Icons.Default.KeyboardArrowRight, null, Modifier.size(20.dp), tint = a.textFaint)
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MiniFeature(VidIcons.VideoLibrary, "Video & audio", "Pick your perfect format", Modifier.weight(1f))
                        MiniFeature(VidIcons.Shield, "Just yours", "No ads. No account.", Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun StudioHero() {
    val largeText = LocalDensity.current.fontScale >= 1.25f
    Box(Modifier.fillMaxWidth().height(if (largeText) 188.dp else 158.dp).clip(RoundedCornerShape(26.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF352258), Color(0xFF222038), Color(0xFF202B43))))) {
        Canvas(Modifier.align(Alignment.CenterEnd).width(if (largeText) 96.dp else 150.dp).fillMaxHeight()) {
            scale(size.width / 160f, size.height / 160f, pivot = Offset.Zero) {
                drawCircle(Brush.radialGradient(listOf(Color(0xFFAC8DFF).copy(alpha = .3f), Color.Transparent), Offset(90f, 70f), 96f), 96f, Offset(90f, 70f))
                drawRoundRect(Color.White.copy(alpha = .08f), Offset(31f, 24f), Size(108f, 92f), CornerRadius(15f))
                drawRoundRect(Brush.linearGradient(listOf(Color(0xFFAC8BFC), Color(0xFF686FE3)), Offset(18f, 40f), Offset(130f, 126f)), Offset(18f, 40f), Size(111f, 84f), CornerRadius(15f))
                drawCircle(Color.White.copy(alpha = .22f), 19f, Offset(74f, 81f))
                drawPath(Path().apply { moveTo(69f, 70f); lineTo(85f, 81f); lineTo(69f, 92f); close() }, Color.White)
                drawRoundRect(Color(0xFF29253E), Offset(64f, 111f), Size(86f, 33f), CornerRadius(11f))
                for (i in 0..10) { val h = listOf(6, 12, 18, 10, 22, 15, 8, 19, 11, 17, 7)[i].toFloat(); drawRoundRect(Color(0xFFC1AFFD), Offset(74f + i * 6f, 127f - h / 2), Size(3f, h), CornerRadius(2f)) }
            }
        }
        Column(Modifier.padding(20.dp).widthIn(max = 210.dp)) {
            Text("THE DOWNLOAD STUDIO", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = Color(0xFFD5C7FF), letterSpacing = 1.sp)
            Spacer(Modifier.height(9.dp))
            Text("Your media.\nYour way.", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 29.sp, letterSpacing = (-.8).sp)
            Spacer(Modifier.height(8.dp))
            Text("Video. Audio. Beautifully saved.", color = Color(0xFFC5BDD7), fontSize = 10.sp)
        }
    }
}

@Composable
private fun MiniFeature(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String, modifier: Modifier) {
    val a = LocalAurora.current
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(a.surface).padding(14.dp)) {
        Icon(icon, null, Modifier.size(21.dp), tint = a.primary)
        Spacer(Modifier.height(9.dp)); Text(title, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(3.dp)); Text(detail, style = MaterialTheme.typography.bodySmall, color = a.textDim)
    }
}

@Composable
private fun ResultCard(vm: HomeViewModel, onLibrary: () -> Unit) {
    val a = LocalAurora.current
    val info = vm.meta ?: return
    val selected = vm.formats.firstOrNull { it.id == vm.selectedId }
    val audio = selected?.isAudioOnly ?: false
    var expanded by rememberSaveable(info.webpageUrl) { mutableStateOf(false) }
    val options = vm.formats.filter { it.isAudioOnly == audio }
    Column {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(24.dp))) {
            MediaArtwork(info.thumbnailPath, info.thumbnailUrl, info.title, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .7f)))))
            Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(7.dp), color = Color.White.copy(alpha = .16f)) { Text(info.platform, Modifier.padding(horizontal = 8.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = Color.White) }
                Spacer(Modifier.weight(1f))
                Text(UrlUtils.formatDuration(info.durationSec), color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.height(14.dp)); Text(info.title, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (!info.uploader.isNullOrBlank()) { Spacer(Modifier.height(4.dp)); Text(info.uploader, style = MaterialTheme.typography.bodySmall, color = a.textDim) }
        Spacer(Modifier.height(18.dp))
        StudioTabs(listOf("Video", "Audio only"), if (audio) 1 else 0, onSelect = { index -> vm.formats.firstOrNull { it.isAudioOnly == (index == 1) }?.let { vm.select(it.id) } }, enabled = listOf(vm.formats.any { !it.isAudioOnly }, vm.formats.any { it.isAudioOnly }))
        Spacer(Modifier.height(12.dp))
        SectionHeading("Choose quality", if (options.size > 4) if (expanded) "Less" else "All formats" else null) { expanded = !expanded }
        val visible = if (expanded) options else options.take(4).let { first -> if (selected != null && selected !in first) first.dropLast(1) + selected else first }
        visible.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { option -> QualityTile(option, option.id == selected?.id, Modifier.weight(1f)) { vm.select(option.id) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(6.dp))
        GradientButton(if (vm.addedToQueue) "View downloads" else if (audio) "Download audio" else "Download video", icon = if (vm.addedToQueue) Icons.Default.CheckCircle else VidIcons.Download,
            enabled = selected != null && !vm.enqueuing, loading = vm.enqueuing) { if (vm.addedToQueue) onLibrary() else vm.download() }
        Spacer(Modifier.height(9.dp))
        Text(if (vm.addedToQueue) "Added to your download queue" else "Saves to your device · works in the background", style = MaterialTheme.typography.bodySmall, color = a.textDim, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun QualityTile(option: FormatOption, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val a = LocalAurora.current
    val shape = RoundedCornerShape(17.dp)
    Column(modifier.clip(shape).background(if (selected) a.primary.copy(alpha = .12f) else a.surface)
        .border(if (selected) 1.5.dp else 1.dp, if (selected) a.primary else a.glassBorder, shape)
        .selectable(selected, role = Role.RadioButton, onClick = onClick).padding(13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(option.label.removePrefix("Best — "), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = if (selected) a.primary else a.text, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(4.dp))
            Box(Modifier.size(17.dp).clip(CircleShape).background(if (selected) a.primary else Color.Transparent).border(1.dp, if (selected) a.primary else a.textFaint, CircleShape), contentAlignment = Alignment.Center) {
                if (selected) Icon(Icons.Default.Check, null, Modifier.size(12.dp), tint = if (a.isDark) Color(0xFF241744) else Color.White)
            }
        }
        Spacer(Modifier.height(7.dp))
        val size = if (option.estBytes == null || option.estBytes <= 0) "Size unavailable" else "${if (option.isApprox) "~" else ""}${UrlUtils.formatBytes(option.estBytes)}${if (option.isApprox) " estimated" else ""}"
        Text(size, style = MaterialTheme.typography.bodySmall, color = a.textDim)
        if (option.id == "best") { Spacer(Modifier.height(3.dp)); Text("BEST AVAILABLE", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = a.primary) }
    }
}
