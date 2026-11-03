package com.giovanni.aeye.wear

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import com.giovanni.aeye.core.AeyeFormatting
import com.giovanni.aeye.core.MeterStyle
import com.giovanni.aeye.core.OverviewRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** watchOS colours: black, white, the secondary and tertiary greys, orange, red. */
private object WatchColors {
    val label = Color.White
    val secondary = Color(0xFF9B9BA1)
    val tertiary = Color(0xFF5E5E63)
    val orange = Color(0xFFFF9F0A)
    val red = Color(0xFFFF453A)
    val button = Color(0xFF222223)
    val divider = Color(0x2EFFFFFF)
}

/** watchOS text styles at the default size. */
private object WatchType {
    val headline = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    val caption = TextStyle(fontSize = 14.sp)
    val caption2 = TextStyle(fontSize = 12.5.sp)
}

/** `WatchContentView.swift`. */
@Composable
fun WatchContentScreen(model: WatchModel) {
    val listState = rememberScalingLazyListState()
    val scope = rememberCoroutineScope()
    val refresh: () -> Unit = { scope.launch { model.refresh() } }

    AppScaffold(timeText = { TimeText() }) {
        ScreenScaffold(scrollState = listState) { contentPadding ->
            ScalingLazyColumn(
                state = listState,
                contentPadding = contentPadding,
                modifier = Modifier.background(Color.Black),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "title") {
                    Text(
                        "Aeye",
                        style = WatchType.headline,
                        color = WatchColors.label,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item(key = "header") {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("🦉 Aeye", style = WatchType.headline, color = WatchColors.label)
                        Spacer(Modifier.weight(1f))
                        if (model.snapshot.isMockData) {
                            Text(
                                "Sample",
                                style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                                color = WatchColors.orange,
                                modifier = Modifier
                                    .background(WatchColors.orange.copy(alpha = 0.22f), CircleShape)
                                    .padding(horizontal = 5.dp, vertical = 1.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        // The toolbar's refresh button on watchOS.
                        RoundRefreshButton(enabled = !model.isRefreshing, onClick = refresh)
                    }
                }
                item(key = "status") { StatusRow(model) }

                val rows = model.rows
                if (rows.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            if (model.hasSnapshot) "No rows enabled on phone" else "Open Aeye on your phone to sync",
                            style = WatchType.caption,
                            color = WatchColors.secondary,
                            modifier = Modifier.padding(horizontal = 10.dp),
                        )
                    }
                } else {
                    rows.forEachIndexed { index, row ->
                        item(key = "row-$index") {
                            Column(Modifier.padding(horizontal = 10.dp)) {
                                WatchRowView(row)
                                if (row.id != rows.last().id) {
                                    Spacer(Modifier.height(8.dp))
                                    Box(Modifier.fillMaxWidth().height(1.dp).background(WatchColors.divider))
                                }
                            }
                        }
                    }
                }

                item(key = "refresh") {
                    RefreshButton(enabled = !model.isRefreshing, onClick = refresh)
                }
                item(key = "note") {
                    Text(
                        "Pulls fresh numbers from the phone — it answers even in your pocket.",
                        style = TextStyle(fontSize = 11.sp),
                        color = WatchColors.tertiary,
                        modifier = Modifier.padding(horizontal = 10.dp),
                    )
                }
            }
        }
    }
}

/** Status plus a live-updating age so a stale snapshot is obvious at a glance. */
@Composable
private fun StatusRow(model: WatchModel) {
    val age by produceState("", model.snapshot.capturedAt) {
        while (true) {
            value = relativeAge(model.snapshot.capturedAt)
            delay(1_000)
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (model.isRefreshing) {
            CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(4.dp))
        }
        val text = buildString {
            append(model.statusLine)
            if (model.hasSnapshot && !model.isRefreshing) append(" · ").append(age).append(" ago")
        }
        Text(text, style = WatchType.caption2, color = WatchColors.secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * `Text(date, style: .relative)` as watchOS draws it — compact, the two largest units: "5sec",
 * "2min 5sec", "1hr 3min".
 */
private fun relativeAge(from: Instant): String {
    val seconds = Duration.between(from, Instant.now()).seconds.coerceAtLeast(0)
    val days = seconds / 86_400
    val hours = (seconds % 86_400) / 3_600
    val minutes = (seconds % 3_600) / 60
    val secs = seconds % 60
    return when {
        days > 0 -> if (hours > 0) "${days}d ${hours}hr" else "${days}d"
        hours > 0 -> if (minutes > 0) "${hours}hr ${minutes}min" else "${hours}hr"
        minutes > 0 -> if (secs > 0) "${minutes}min ${secs}sec" else "${minutes}min"
        else -> "${secs}sec"
    }
}

@Composable
private fun WatchRowView(row: OverviewRow) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(row.id.provider, style = WatchType.caption2, color = WatchColors.secondary, maxLines = 1)
            Spacer(Modifier.width(4.dp))
            Text(row.id.shortLabel, style = WatchType.caption.copy(fontWeight = FontWeight.SemiBold), color = WatchColors.label, maxLines = 1)
            Spacer(Modifier.weight(1f).width(4.dp))
            Text(
                AeyeFormatting.watchComplicationPercent(row.percentUsed),
                style = WatchType.caption.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                color = if (row.isError) WatchColors.red else WatchColors.label,
                maxLines = 1,
            )
        }
        WatchMeter(row.percentUsed, pitch = 11.7.dp)
        if (row.subtitle.isNotEmpty()) {
            Text(
                compactSubtitle(row),
                style = TextStyle(fontSize = 10.sp, lineHeight = 12.sp),
                color = WatchColors.secondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Keep spend and pace-ish fragments; drop the verbose `plan=` prefix when space is tight. */
private fun compactSubtitle(row: OverviewRow): String {
    if (row.isError) return row.subtitle
    val parts = row.subtitle.split("·").map { it.trim() }.filter { !it.startsWith("plan=") }
    if (parts.isEmpty()) return row.subtitle
    return parts.joinToString(" · ")
}

@Composable
private fun WatchMeter(percent: Double?, pitch: Dp) {
    val slots = AeyeFormatting.meterSlots(percent, 10)
    Canvas(
        Modifier
            .width(pitch * 10)
            .height(pitch)
            .semantics { contentDescription = "${AeyeFormatting.percentString(percent)} used" }
    ) {
        val step = size.width / 10
        val radius = step * MeterStyle.DIAMETER_PER_PITCH / 2
        val s = MeterStyle.STOPS
        slots.forEachIndexed { i, slot ->
            val p = MeterStyle.palette(slot)
            val c = Offset(step * i + step / 2, size.height / 2)
            drawCircle(Color(p.rim), radius, c)
            val inner = radius * MeterStyle.INNER_PER_RADIUS
            drawCircle(
                Brush.verticalGradient(
                    s[0] to Color(p.top), s[1] to Color(p.top), s[2] to Color(p.body), s[3] to Color(p.bottom),
                    startY = c.y - inner, endY = c.y + inner,
                ),
                inner,
                c,
            )
        }
    }
}

/** `Button { Label("Refresh", systemImage: "arrow.clockwise") }` — a full-width grey capsule. */
@Composable
private fun RefreshButton(enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .alpha(if (!enabled) 0.45f else if (pressed) 0.7f else 1f)
            .background(WatchColors.button, RoundedCornerShape(50))
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArrowClockwise(WatchColors.label, 14.dp)
        Spacer(Modifier.width(6.dp))
        Text("Refresh", style = WatchType.caption, color = WatchColors.label)
    }
}

@Composable
private fun RoundRefreshButton(enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier
            .size(36.dp)
            .alpha(if (!enabled) 0.45f else if (pressed) 0.7f else 1f)
            .background(WatchColors.button, CircleShape)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Refresh from phone" },
        contentAlignment = Alignment.Center,
    ) {
        ArrowClockwise(WatchColors.secondary, 18.dp)
    }
}

/** `arrow.clockwise`. */
@Composable
private fun ArrowClockwise(color: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val stroke = this.size.minDimension * 0.1f
        val r = this.size.minDimension * 0.38f
        val c = center
        val head = this.size.minDimension * 0.17f
        // Open at the top right; the stroke runs clockwise and ends at 12 o'clock, where the
        // chevron points on, to the right.
        drawArc(color, -45f, 315f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(stroke, cap = StrokeCap.Round))
        val tip = Offset(c.x + head * 0.55f, c.y - r)
        val path = Path().apply {
            moveTo(tip.x - head, tip.y - head)
            lineTo(tip.x, tip.y)
            lineTo(tip.x - head, tip.y + head)
        }
        drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
