package com.vidgrab.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vidgrab.app.ui.theme.LocalAurora

@Composable
fun StudioInput(value: String, onValueChange: (String) -> Unit, hint: String, icon: ImageVector,
    modifier: Modifier = Modifier, uri: Boolean = false, onGo: () -> Unit = {}) {
    val a = LocalAurora.current
    val shape = RoundedCornerShape(16.dp)
    BasicTextField(value = value, onValueChange = onValueChange, singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = a.text), cursorBrush = SolidColor(a.primary),
        keyboardOptions = KeyboardOptions(keyboardType = if (uri) KeyboardType.Uri else KeyboardType.Text, imeAction = if (uri) ImeAction.Go else ImeAction.Search),
        keyboardActions = KeyboardActions(onGo = { onGo() }, onSearch = { onGo() }),
        modifier = modifier.fillMaxWidth().heightIn(min = 58.dp).clip(shape).background(a.bg).border(1.dp, a.glassBorder, shape),
        decorationBox = { input ->
            Row(Modifier.padding(start = 15.dp, end = 5.dp).heightIn(min = 58.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(19.dp), tint = a.textFaint)
                Spacer(Modifier.width(11.dp))
                Box(Modifier.weight(1f).padding(vertical = 14.dp)) {
                    if (value.isEmpty()) Text(hint, color = a.textFaint, style = MaterialTheme.typography.bodyMedium)
                    input()
                }
                if (value.isNotEmpty()) IconButton(onClick = { onValueChange("") }) { Icon(Icons.Default.Clear, "Clear", Modifier.size(18.dp), tint = a.textDim) }
                else Spacer(Modifier.width(10.dp))
            }
        })
}

@Composable
fun StudioTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, enabled: List<Boolean> = labels.map { true }) {
    val a = LocalAurora.current
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(a.surfaceHigh).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        labels.forEachIndexed { index, label ->
            val active = selected == index
            val background by animateColorAsState(if (active) a.surface else Color.Transparent, label = "tab background")
            val interaction = remember { MutableInteractionSource() }
            Box(Modifier.weight(1f).heightIn(min = 42.dp).pressScale(interaction).clip(RoundedCornerShape(12.dp))
                .background(background).selectable(active, enabled = enabled.getOrElse(index) { true }, interactionSource = interaction, indication = ripple(), role = Role.Tab, onClick = { onSelect(index) }), contentAlignment = Alignment.Center) {
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    color = if (!enabled.getOrElse(index) { true }) a.textFaint.copy(alpha = .5f) else if (active) a.primary else a.textDim,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp))
            }
        }
    }
}

@Composable
fun SectionHeading(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction, contentPadding = PaddingValues(start = 12.dp)) { Text(action, style = MaterialTheme.typography.labelLarge) }
    }
}
