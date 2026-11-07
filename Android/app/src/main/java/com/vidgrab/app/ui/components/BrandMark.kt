package com.vidgrab.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** The folded V points down like a saved download, with a forward motion cut. */
@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    Canvas(modifier.semantics { contentDescription = "VidGrab logo" }) {
        drawRoundRect(Color(0xFF342557), cornerRadius = CornerRadius(size.minDimension * 0.28f))
        scale(size.width / 64f, size.height / 64f, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            drawPath(Path().apply {
                moveTo(15f, 17f); lineTo(27f, 17f); lineTo(40f, 45f)
                lineTo(33f, 54f); close()
            }, Color(0xFFC4AEFF))
            drawPath(Path().apply {
                moveTo(41f, 17f); lineTo(53f, 17f); lineTo(33f, 54f)
                lineTo(27f, 41f); close()
            }, Color(0xFFFFFFFF))
        }
    }
}
