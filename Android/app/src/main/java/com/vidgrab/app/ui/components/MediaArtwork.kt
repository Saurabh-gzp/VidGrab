package com.vidgrab.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vidgrab.app.ui.theme.LocalAurora
import java.io.File

/** A permanent visual fallback prevents missing/expired artwork from becoming an empty cell. */
@Composable
fun MediaArtwork(
    path: String?,
    url: String?,
    title: String,
    modifier: Modifier = Modifier,
    audio: Boolean = false
) {
    val colors = LocalAurora.current
    var localFailed by remember(path) { mutableStateOf(false) }
    val localFile = remember(path) { path?.let(::File)?.takeIf { it.isFile && it.length() > 0 } }
    val model = if (localFailed) url else localFile ?: url
    Box(
        modifier.background(Brush.linearGradient(listOf(colors.surfaceHigh, colors.violet.copy(alpha = 0.14f))))
            .semantics { contentDescription = if (audio) "Artwork for $title" else "Thumbnail for $title" },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (audio) VidIcons.Download else VidIcons.VideoLibrary, contentDescription = null,
            modifier = Modifier.size(28.dp), tint = colors.violet.copy(alpha = 0.65f)
        )
        if (model != null) AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            onError = { if (model is File && !url.isNullOrBlank()) localFailed = true }
        )
    }
}
