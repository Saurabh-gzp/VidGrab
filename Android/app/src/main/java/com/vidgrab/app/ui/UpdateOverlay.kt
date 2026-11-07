package com.vidgrab.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vidgrab.app.core.UpdateState
import com.vidgrab.app.ui.theme.LocalAurora

/** A quiet status strip keeps the rest of the app available while an engine update runs. */
@Composable
fun UpdateOverlay(state: UpdateState) {
    val a = LocalAurora.current
    Surface(color = a.surfaceHigh) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
            val text = when (state) {
                is UpdateState.Updating -> "Updating download engine · ${state.progress.toInt()}%"
                is UpdateState.Failed -> "Update unavailable. Your current engine is ready."
                else -> "Download engine is up to date"
            }
            Text(text, style = MaterialTheme.typography.bodySmall, color = a.text)
            if (state is UpdateState.Updating) {
                Spacer(Modifier.height(7.dp))
                LinearProgressIndicator(progress = { (state.progress / 100).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = a.primary, trackColor = a.glassBorder)
            }
        }
    }
}
