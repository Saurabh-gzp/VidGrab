package com.vidgrab.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.vidgrab.app.core.*
import com.vidgrab.app.ui.components.VidIcons
import com.vidgrab.app.ui.components.pressScale
import com.vidgrab.app.ui.screens.*
import com.vidgrab.app.ui.theme.LocalAurora
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home), DOWNLOADS("Library", VidIcons.VideoLibrary), SETTINGS("Settings", Icons.Default.Settings)
}

@Composable
fun AppRoot(themeFlow: kotlinx.coroutines.flow.Flow<Prefs.ThemeMode>, incomingUrl: String?, onUrlConsumed: () -> Unit) {
    val a = LocalAurora.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm: HomeViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val active by DownloadRepository.active.collectAsState()
    val updating by UpdateManager.state.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    val pendingCount = active.count { it.status.isPending() }
    LaunchedEffect(Unit) { runCatching { DownloadRepository.resumePending() } }
    LaunchedEffect(incomingUrl) { if (!incomingUrl.isNullOrBlank()) tab = Tab.HOME }
    LaunchedEffect(updating) { if (updating is UpdateState.Done || updating is UpdateState.Failed) { delay(3000); UpdateManager.resetToIdle() } }
    Scaffold(containerColor = a.bg, contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            Column(Modifier.fillMaxWidth().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (updating is UpdateState.Updating || updating is UpdateState.Done || updating is UpdateState.Failed) UpdateOverlay(updating)
                Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp).widthIn(max = 560.dp).fillMaxWidth()
                    .shadow(18.dp, RoundedCornerShape(26.dp), ambientColor = Color.Black.copy(alpha = .2f), spotColor = Color.Black.copy(alpha = .2f))
                    .clip(RoundedCornerShape(26.dp)).background(a.surface)
                    .border(1.dp, a.glassBorder, RoundedCornerShape(26.dp)).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Tab.entries.forEach { item ->
                        val selected = item == tab
                        val color by animateColorAsState(if (selected) a.primary.copy(alpha = if (a.isDark) .17f else .1f) else Color.Transparent, label = "navigation")
                        val interaction = remember { MutableInteractionSource() }
                        Column(Modifier.weight(1f).heightIn(min = 55.dp).pressScale(interaction).clip(RoundedCornerShape(20.dp)).background(color)
                            .selectable(selected, role = Role.Tab, interactionSource = interaction, indication = ripple(), onClick = { tab = item }),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Box {
                                Icon(item.icon, null, Modifier.size(21.dp), tint = if (selected) a.primary else a.textDim)
                                if (item == Tab.DOWNLOADS && pendingCount > 0) Badge(Modifier.align(Alignment.TopEnd).offset(x = 11.dp, y = (-5).dp)) { Text(pendingCount.coerceAtMost(99).toString()) }
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(item.label, style = MaterialTheme.typography.labelSmall, color = if (selected) a.primary else a.textDim)
                        }
                    }
                }
            }
        }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AnimatedContent(targetState = tab, label = "studio screen", transitionSpec = {
                val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (fadeIn(tween(190)) + slideInHorizontally(tween(230)) { it / 16 * direction })
                    .togetherWith(fadeOut(tween(120)) + slideOutHorizontally(tween(190)) { -it / 24 * direction })
            }) { selected ->
                when (selected) {
                    Tab.HOME -> HomeScreen(vm, incomingUrl, onUrlConsumed) { tab = Tab.DOWNLOADS }
                    Tab.DOWNLOADS -> DownloadsScreen()
                    Tab.SETTINGS -> SettingsScreen(themeFlow) { mode -> scope.launch { Prefs.setTheme(ctx.applicationContext, mode) } }
                }
            }
        }
    }
}
