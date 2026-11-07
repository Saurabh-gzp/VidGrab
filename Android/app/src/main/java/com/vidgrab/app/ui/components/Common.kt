package com.vidgrab.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vidgrab.app.ui.theme.LocalAurora

/** Runs only while a control is pressed; rendering is idle after the spring settles. */
fun Modifier.pressScale(interaction: MutableInteractionSource): Modifier = composed {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "press"
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Compatibility name. A quiet, static accent avoids redrawing idle screens. */
@Composable
fun animatedAuroraBrush(): Brush {
    val colors = LocalAurora.current
    return remember(colors.violet, colors.cyan) { Brush.linearGradient(listOf(colors.violet, colors.cyan)) }
}

fun Modifier.glassCard(highlight: Boolean = false): Modifier = composed {
    val colors = LocalAurora.current
    val shape = RoundedCornerShape(22.dp)
    clip(shape).background(colors.surface)
        .border(1.dp, if (highlight) colors.violet.copy(alpha = 0.45f) else colors.glassBorder, shape)
}

/** Stable loading placeholder; the adjacent progress control indicates activity. */
@Composable
fun ShimmerBox(modifier: Modifier = Modifier, corner: Int = 18) {
    Box(modifier.clip(RoundedCornerShape(corner.dp)).background(LocalAurora.current.surfaceHigh))
}

@Composable
fun ProgressRing(
    progress: Float,
    sizeDp: Int = 46,
    stroke: Int = 5,
    indeterminate: Boolean = false,
    content: @Composable (() -> Unit)? = null
) {
    val colors = LocalAurora.current
    Box(Modifier.size(sizeDp.dp), contentAlignment = Alignment.Center) {
        if (indeterminate) {
            CircularProgressIndicator(
                modifier = Modifier.fillMaxSize(), color = colors.violet,
                trackColor = colors.glassBorder, strokeWidth = stroke.dp
            )
        } else {
            val target = if (progress.isFinite()) (progress / 100f).coerceIn(0f, 1f) else 0f
            val value by animateFloatAsState(targetValue = target, label = "download progress")
            CircularProgressIndicator(
                progress = { value }, modifier = Modifier.fillMaxSize(), color = colors.violet,
                trackColor = colors.glassBorder, strokeWidth = stroke.dp
            )
        }
        content?.invoke()
    }
}

@Composable
fun GradientButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit
) {
    val colors = LocalAurora.current
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(18.dp)
    val brush = if (enabled && !loading) Brush.linearGradient(listOf(Color(0xFF7956ED), Color(0xFF9B6AEE)))
        else Brush.linearGradient(listOf(colors.surfaceHigh, colors.surfaceHigh))
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp).pressScale(interaction)
        .clip(shape).background(brush)
        .border(1.dp, Color.White.copy(alpha = if (enabled) .14f else .04f), shape)
        .clickable(enabled = enabled && !loading, interactionSource = interaction, indication = ripple(), role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
        .padding(horizontal = 18.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        val foreground = if (enabled && !loading) Color.White else colors.textDim
        if (loading) { CircularProgressIndicator(Modifier.size(18.dp), color = foreground, strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)) }
        else if (icon != null) { Icon(icon, null, Modifier.size(20.dp), tint = foreground); Spacer(Modifier.width(9.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge, color = foreground, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String = "Delete",
    dismissLabel: String = "Cancel",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalAurora.current
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = colors.surface,
        titleContentColor = colors.text, textContentColor = colors.textDim,
        shape = RoundedCornerShape(28.dp),
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel, color = colors.danger, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel, color = colors.textDim) } }
    )
}

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String) {
    val colors = LocalAurora.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(92.dp, 70.dp).clip(RoundedCornerShape(19.dp)).background(colors.gradientSoft).border(1.dp, colors.violet.copy(alpha = .2f), RoundedCornerShape(19.dp)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, contentDescription = null, tint = colors.violet, modifier = Modifier.size(32.dp)) }
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = colors.text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.textDim, textAlign = TextAlign.Center)
    }
}
