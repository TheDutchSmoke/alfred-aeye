package com.giovanni.aeye.ui.ios

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

class ContextMenuItem(val title: String, val icon: @Composable (Color) -> Unit, val action: () -> Unit)

/**
 * `.contextMenu { … }`: a long press lifts the content slightly, buzzes, and opens an iOS menu
 * under the touch. Empty [items] means no menu, as an empty `contextMenu` builder does.
 */
@Composable
fun ContextMenuArea(items: List<ContextMenuItem>, content: @Composable () -> Unit) {
    var menuAt by remember { mutableStateOf<IntOffset?>(null) }
    val haptics = LocalHapticFeedback.current
    val lift by animateFloatAsState(if (menuAt != null) 1.02f else 1f, spring(dampingRatio = 0.7f), label = "lift")

    Box(
        Modifier
            .scale(lift)
            .then(
                if (items.isEmpty()) Modifier
                else Modifier
                    .pointerInput(items) {
                        detectTapGestures(onLongPress = { position ->
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuAt = IntOffset(position.x.toInt(), position.y.toInt())
                        })
                    }
                    .semantics {
                        customActions = items.map { item -> CustomAccessibilityAction(item.title) { item.action(); true } }
                    }
            )
    ) {
        content()
        val at = menuAt
        if (at != null) {
            Popup(
                offset = IntOffset(0, at.y + 12),
                onDismissRequest = { menuAt = null },
                properties = PopupProperties(focusable = true),
            ) {
                IosMenu(items, onPick = { menuAt = null })
            }
        }
    }
}

@Composable
private fun IosMenu(items: List<ContextMenuItem>, onPick: () -> Unit) {
    val colors = Ios.colors
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val grow by animateFloatAsState(if (appeared) 1f else 0.85f, spring(dampingRatio = 0.75f, stiffness = 500f), label = "menu")
    Column(
        Modifier
            .padding(start = 16.dp, top = 4.dp)
            .scale(grow)
            .width(250.dp)
            .shadow(24.dp, RoundedCornerShape(13.dp), ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
            .background(colors.menu, RoundedCornerShape(13.dp))
    ) {
        items.forEachIndexed { index, item ->
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (pressed) colors.menuSeparator else Color.Transparent)
                    .clickable(interaction, indication = null, role = Role.Button) {
                        item.action()
                        onPick()
                    }
                    .padding(horizontal = 16.dp, vertical = 11.dp),
            ) {
                Text(item.title, style = Ios.type.body, color = colors.label, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                item.icon(colors.label)
            }
            if (index != items.lastIndex) {
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.menuSeparator))
            }
        }
    }
}
