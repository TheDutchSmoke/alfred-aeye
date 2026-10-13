package com.giovanni.aeye.ui.ios

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * `.sheet(isPresented:)` — the iOS page sheet: it rises from the bottom to just under the status
 * bar, and whatever it covers recedes into a smaller, dimmed card behind it. Sheets stack (Settings,
 * then a sign-in sheet over it), and system back or a downward drag on the sheet's bar dismisses
 * the top one.
 */
class SheetController {
    internal class Entry(val id: String, val onDismiss: () -> Unit, val content: @Composable () -> Unit) {
        /** Sliding away; removed once the animation ends. */
        var dismissing by mutableStateOf(false)
    }

    internal val stack = mutableStateListOf<Entry>()

    val isPresenting: Boolean get() = stack.any { !it.dismissing }

    fun present(id: String, onDismiss: () -> Unit = {}, content: @Composable () -> Unit) {
        if (stack.any { it.id == id }) return
        stack += Entry(id, onDismiss, content)
    }

    fun dismiss(id: String) {
        val entry = stack.firstOrNull { it.id == id && !it.dismissing } ?: return
        entry.dismissing = true
        entry.onDismiss()
    }

    internal fun remove(entry: Entry) {
        stack.remove(entry)
    }

    fun isPresented(id: String): Boolean = stack.any { it.id == id && !it.dismissing }
}

val LocalSheetController = staticCompositionLocalOf<SheetController> { error("No SheetHost") }

/** Dismisses the sheet this content was presented in — `@Environment(\.dismiss)`. */
val LocalDismiss = staticCompositionLocalOf<() -> Unit> { {} }

@Composable
fun SheetHost(controller: SheetController, base: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSheetController provides controller) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Ios.colors.sheetBackdrop)) {
            val screenHeight = with(LocalDensity.current) { maxHeight.toPx() }
            val live = controller.stack.filter { !it.dismissing }
            Receding(depth = live.size, isBase = true) { base() }
            controller.stack.forEach { entry ->
                key(entry.id) {
                    PresentedSheet(
                        depth = live.size - 1 - live.indexOf(entry).let { if (it < 0) live.size else it },
                        screenHeight = screenHeight,
                        dismissing = entry.dismissing,
                        onDismiss = { controller.dismiss(entry.id) },
                        onGone = { controller.remove(entry) },
                        isTop = entry === live.lastOrNull(),
                    ) {
                        CompositionLocalProvider(LocalDismiss provides { controller.dismiss(entry.id) }) {
                            ElevatedSurface { entry.content() }
                        }
                    }
                }
            }
        }
    }
}

/** A layer that shrinks and dims while [depth] sheets sit in front of it. */
@Composable
private fun Receding(depth: Int, isBase: Boolean, content: @Composable () -> Unit) {
    val covered by animateFloatAsState(if (depth > 0) 1f else 0f, tween(350), label = "recede")
    val statusBarTop = WindowInsets.statusBars
    val density = LocalDensity.current
    val topInset = statusBarTop.getTop(density).toFloat()
    val corner = 10.dp
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val scale = 1f - 0.085f * covered
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0.5f, 0f)
                // The base card's top settles at the status bar's bottom edge; a covered sheet
                // rises a little above the one in front of it.
                translationY = if (isBase) topInset * covered else -6.dp.toPx() * covered
                shape = RoundedCornerShape(corner * covered)
                clip = covered > 0f
            }
    ) {
        content()
        if (covered > 0f) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f * covered)))
        }
    }
}

@Composable
private fun PresentedSheet(
    depth: Int,
    screenHeight: Float,
    dismissing: Boolean,
    isTop: Boolean,
    onDismiss: () -> Unit,
    onGone: () -> Unit,
    content: @Composable () -> Unit,
) {
    val offset = remember { Animatable(screenHeight) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(dismissing) {
        if (dismissing) {
            offset.animateTo(screenHeight, tween(260))
            onGone()
        } else {
            offset.animateTo(0f, spring(dampingRatio = 1f, stiffness = 380f))
        }
    }

    if (isTop) {
        BackHandler { onDismiss() }
    }

    val dragState = rememberDraggableState { delta ->
        scope.launch { offset.snapTo((offset.value + delta).coerceAtLeast(0f)) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 10.dp)
            .graphicsLayer { translationY = offset.value }
    ) {
        Receding(depth = depth, isBase = false) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
            ) {
                content()
                // The grabber region: the sheet's navigation bar. Drag it down to dismiss.
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(0.55f)
                        .height(IosMetrics.navBarHeight)
                        .draggable(
                            dragState,
                            Orientation.Vertical,
                            enabled = isTop,
                            onDragStopped = { velocity ->
                                if (offset.value > screenHeight * 0.25f || velocity > 1500f) {
                                    onDismiss()
                                } else {
                                    offset.animateTo(0f, spring(dampingRatio = 1f, stiffness = 500f))
                                }
                            },
                        )
                )
            }
        }
    }
}
