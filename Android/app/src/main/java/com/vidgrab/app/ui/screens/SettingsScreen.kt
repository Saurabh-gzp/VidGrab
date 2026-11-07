package com.vidgrab.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vidgrab.app.BuildConfig
import com.vidgrab.app.core.*
import com.vidgrab.app.ui.components.*
import com.vidgrab.app.ui.theme.LocalAurora
import com.vidgrab.app.util.MediaSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(themeFlow: kotlinx.coroutines.flow.Flow<Prefs.ThemeMode>, onTheme: (Prefs.ThemeMode) -> Unit) {
    val a = LocalAurora.current
    val ctx = LocalContext.current.applicationContext
    val theme by themeFlow.collectAsState(initial = Prefs.ThemeMode.System)
    val update by UpdateManager.state.collectAsState()
    var version by remember { mutableStateOf(Engine.BUNDLED_VERSION) }
    LaunchedEffect(update) { version = withContext(Dispatchers.IO) { Engine.engineVersion(ctx) } ?: Engine.BUNDLED_VERSION }
    val busy = update is UpdateState.Checking || update is UpdateState.Updating
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 680.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Column {
                Text("Settings", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(4.dp)); Text("Fine-tune your studio.", style = MaterialTheme.typography.bodySmall, color = a.textDim)
            }
            Surface(shape = RoundedCornerShape(24.dp), color = a.surface, border = BorderStroke(1.dp, a.glassBorder)) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    BrandMark(Modifier.size(58.dp)); Spacer(Modifier.width(15.dp))
                    Column(Modifier.weight(1f)) {
                        Text("VidGrab", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Your offline studio", style = MaterialTheme.typography.bodySmall, color = a.textDim)
                        Spacer(Modifier.height(7.dp))
                        Text("VERSION ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall, color = a.primary)
                    }
                }
            }
            Column {
                SectionHeading("Appearance")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(Prefs.ThemeMode.System, Prefs.ThemeMode.Light, Prefs.ThemeMode.Dark).forEach { mode ->
                        ThemeChoice(mode, theme == mode, Modifier.weight(1f)) { onTheme(mode) }
                    }
                }
            }
            SettingsPanel(VidIcons.Update, "Download engine", "Better support for your favorite platforms") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("yt-dlp $version", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(4.dp))
                        val label = when (update) {
                            is UpdateState.Checking -> "Checking for updates…"
                            is UpdateState.Updating -> "Updating in the background…"
                            is UpdateState.Done -> "You're up to date"
                            is UpdateState.Failed -> "Unavailable · try again online"
                            else -> "Automatic checks enabled"
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(5.dp).clip(CircleShape).background(if (update is UpdateState.Failed) a.danger else a.success))
                            Spacer(Modifier.width(6.dp)); Text(label, style = MaterialTheme.typography.bodySmall, color = a.textDim)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                GradientButton(if (busy) "Checking…" else "Check for updates", icon = VidIcons.Update, enabled = !busy, loading = busy) { UpdateManager.manualCheck(ctx) }
                Spacer(Modifier.height(9.dp)); Text("Updates the downloader engine. App updates are installed separately.", style = MaterialTheme.typography.bodySmall, color = a.textDim)
            }
            SettingsPanel(VidIcons.Download, "Download location", "Always saved on your device") {
                val direct = MediaSaver.hasAllFilesAccess(ctx)
                LocationRow("Video", if (direct) "VidGrab/Video" else "Movies/VidGrab/Video")
                HorizontalDivider(Modifier.padding(vertical = 10.dp), color = a.glassBorder)
                LocationRow("Audio", if (direct) "VidGrab/Audio" else "Music/VidGrab/Audio")
            }
            SettingsPanel(VidIcons.Shield, "Private by design", "No ads. No account. No tracking.") {
                Text("Media is downloaded directly from the sites you use. Saved thumbnails stay on your device for offline access. GitHub provides engine updates.", style = MaterialTheme.typography.bodySmall, color = a.textDim)
            }
            Text("Made with care, in India.", style = MaterialTheme.typography.bodySmall, color = a.textFaint, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun ThemeChoice(mode: Prefs.ThemeMode, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val a = LocalAurora.current
    val shape = RoundedCornerShape(17.dp)
    Column(modifier.clip(shape).background(if (selected) a.primary.copy(alpha = .1f) else a.surface)
        .border(if (selected) 1.5.dp else 1.dp, if (selected) a.primary else a.glassBorder, shape)
        .selectable(selected, role = Role.RadioButton, onClick = onClick).padding(12.dp)) {
        val dark = mode == Prefs.ThemeMode.Dark
        Box(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(9.dp))
            .background(if (mode == Prefs.ThemeMode.System) Brush.horizontalGradient(listOf(Color(0xFFF1EDFC), Color(0xFF252330))) else Brush.linearGradient(listOf(if (dark) Color(0xFF252330) else Color(0xFFF1EDFC), if (dark) Color(0xFF252330) else Color(0xFFF1EDFC))))) {
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.width(20.dp).height(4.dp).clip(CircleShape).background(Color(0xFF9778F0)))
                Box(Modifier.fillMaxWidth(.8f).height(4.dp).clip(CircleShape).background(if (dark) Color(0xFF555268) else Color(0xFFD8D1E9)))
                Box(Modifier.fillMaxWidth(.6f).height(4.dp).clip(CircleShape).background(if (dark) Color(0xFF555268) else Color(0xFFD8D1E9)))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(mode.label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (selected) Icon(Icons.Default.Check, null, Modifier.size(15.dp), tint = a.primary)
        }
    }
}

@Composable
private fun SettingsPanel(icon: ImageVector, title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    val a = LocalAurora.current
    Surface(shape = RoundedCornerShape(22.dp), color = a.surface, border = BorderStroke(1.dp, a.glassBorder)) {
        Column(Modifier.fillMaxWidth().padding(17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(a.primary.copy(alpha = .1f)), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(18.dp), tint = a.primary) }
                Spacer(Modifier.width(10.dp))
                Column { Text(title, style = MaterialTheme.typography.titleMedium); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = a.textDim) }
            }
            Spacer(Modifier.height(16.dp)); content()
        }
    }
}

@Composable
private fun LocationRow(title: String, path: String) {
    val a = LocalAurora.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(54.dp))
        Text(path, style = MaterialTheme.typography.bodySmall, color = a.textDim)
    }
}
