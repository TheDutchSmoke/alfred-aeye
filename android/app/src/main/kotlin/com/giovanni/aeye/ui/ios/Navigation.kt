package com.giovanni.aeye.ui.ios

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * A `NavigationStack` screen: navigation bar with leading and trailing items, an optional large
 * title that collapses into the bar as the content scrolls, optional pull to refresh, and a
 * scrolling column of content.
 *
 * [insetStatusBar] is false inside a sheet, which already sits below the status bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IosScreen(
    title: String,
    modifier: Modifier = Modifier,
    background: Color = Ios.colors.background,
    largeTitle: Boolean = true,
    insetStatusBar: Boolean = true,
    scrollState: ScrollState = rememberScrollState(),
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onRefresh: (suspend () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = Ios.colors
    val density = LocalDensity.current
    // Collapsed once the large title has mostly scrolled under the bar.
    val collapsed by remember(largeTitle) {
        derivedStateOf {
            if (!largeTitle) scrollState.value > 0
            else scrollState.value > with(density) { 34.dp.toPx() }
        }
    }
    val inlineTitleAlpha by animateFloatAsState(if (!largeTitle || collapsed) 1f else 0f, tween(180), label = "title")
    val scrolled by remember { derivedStateOf { scrollState.value > 0 } }

    Column(modifier.fillMaxSize().background(background)) {
        // Navigation bar: transparent at rest, the bar material once content is under it.
        Column(Modifier.background(if (scrolled) colors.chrome else background)) {
            if (insetStatusBar) Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Box(Modifier.fillMaxWidth().height(IosMetrics.navBarHeight)) {
                Text(
                    title,
                    style = Ios.type.headline,
                    color = colors.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .widthIn(max = 220.dp)
                        .alpha(inlineTitleAlpha),
                )
                if (leading != null) {
                    Row(Modifier.align(Alignment.CenterStart).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) { leading() }
                }
                if (trailing != null) {
                    Row(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) { trailing() }
                }
            }
            if (scrolled) Hairline()
        }

        val body: @Composable () -> Unit = {
            Column(
                Modifier
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(scrollState)
            ) {
                if (largeTitle) {
                    Text(
                        title,
                        style = Ios.type.largeTitle,
                        color = colors.label,
                        maxLines = 1,
                        modifier = Modifier.padding(start = IosMetrics.barMargin, end = IosMetrics.barMargin, top = 2.dp, bottom = 8.dp),
                    )
                }
                content()
                Spacer(Modifier.height(32.dp))
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }

        if (onRefresh == null) {
            body()
        } else {
            val state = rememberPullToRefreshState()
            val scope = rememberCoroutineScope()
            var refreshing by remember { mutableStateOf(false) }
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    scope.launch {
                        refreshing = true
                        try {
                            onRefresh()
                        } finally {
                            refreshing = false
                        }
                    }
                },
                state = state,
                modifier = Modifier.fillMaxSize(),
                indicator = {
                    // The UIRefreshControl spinner, revealed in the gap the pull opens.
                    val fraction = state.distanceFraction
                    Box(Modifier.align(Alignment.TopCenter).padding(top = 14.dp)) {
                        if (refreshing || fraction > 0.05f) {
                            ActivityIndicator(
                                Modifier.graphicsLayer { alpha = if (refreshing) 1f else fraction.coerceIn(0f, 1f) },
                                size = 24.dp,
                            )
                        }
                    }
                },
            ) {
                Box(Modifier.graphicsLayer { translationY = state.distanceFraction.coerceIn(0f, 1.6f) * 52.dp.toPx() }) {
                    body()
                }
            }
        }
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(0.5.dp).background(Ios.colors.chromeHairline))
}

/** `Divider()` between rows of the overview. */
@Composable
fun IosDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(0.5.dp).background(Ios.colors.opaqueSeparator))
}
