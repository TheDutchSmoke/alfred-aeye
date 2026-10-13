package com.giovanni.aeye.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.giovanni.aeye.MainActivity
import com.giovanni.aeye.R
import com.giovanni.aeye.SnapshotStore
import com.giovanni.aeye.core.AeyeFormatting
import com.giovanni.aeye.core.AeyeSnapshot
import com.giovanni.aeye.core.MockSnapshot
import com.giovanni.aeye.core.OverviewRow
import com.giovanni.aeye.ui.meterBitmap
import com.giovanni.aeye.ui.shortTime

/**
 * The Home Screen widget — `AeyeWidget.swift`. Medium shows each visible row's label, percent
 * and meter; Large adds the reset detail and the subtitle. Like the iOS widget it never calls
 * Cursor or Claude: it shows the snapshot the app last wrote.
 */
class AeyeWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = SnapshotStore(context).load() ?: AeyeSnapshot.empty
        provideContent { WidgetContent(snapshot) }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        // The widget gallery shows the sample, unbadged — `context.isPreview` on iOS.
        provideContent { WidgetContent(MockSnapshot.preview) }
    }

    companion object {
        val MEDIUM = DpSize(250.dp, 110.dp)
        val LARGE = DpSize(250.dp, 260.dp)

        /** `WidgetCenter.shared.reloadTimelines(ofKind:)`. */
        suspend fun reload(context: Context) {
            try {
                AeyeWidget().updateAll(context)
            } catch (_: Exception) {
                // No widget placed, or the host is busy; the next refresh will try again.
            }
        }
    }
}

class AeyeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = AeyeWidget()
}

private val Label = ColorProvider(day = Color.Black, night = Color.White)
private val Secondary = ColorProvider(day = Color(0x993C3C43), night = Color(0x99EBEBF5))
private val Red = ColorProvider(day = Color(0xFFFF3B30), night = Color(0xFFFF453A))
private val Orange = ColorProvider(day = Color(0xFFFF9500), night = Color(0xFFFF9F0A))
private val OrangeWash = ColorProvider(day = Color(0x2EFF9500), night = Color(0x2EFF9F0A))

@Composable
private fun WidgetContent(snapshot: AeyeSnapshot) {
    val context = LocalContext.current
    val large = LocalSize.current.height >= AeyeWidget.LARGE.height
    val compact = !large
    Column(
        GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_background))
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("🦉 Aeye", style = TextStyle(color = Label, fontSize = 12.sp, fontWeight = FontWeight.Bold))
            if (snapshot.isMockData) {
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    "Sample",
                    style = TextStyle(color = Orange, fontSize = 9.sp, fontWeight = FontWeight.Medium),
                    modifier = GlanceModifier.background(OrangeWash).cornerRadius(8.dp).padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
            Spacer(GlanceModifier.defaultWeight())
            if (snapshot.hasCapture) {
                Text(shortTime(context, snapshot.capturedAt), style = TextStyle(color = Secondary, fontSize = 11.sp))
            }
        }

        val rows = snapshot.visibleRows
        if (rows.isEmpty()) {
            Spacer(GlanceModifier.height(5.dp))
            Text("Open Aeye on your phone to refresh", style = TextStyle(color = Secondary, fontSize = 12.sp))
        } else {
            rows.forEach { row ->
                Spacer(GlanceModifier.height(if (large) 8.dp else 5.dp))
                WidgetRow(row, compact)
            }
        }
    }
}

@Composable
private fun WidgetRow(row: OverviewRow, compact: Boolean) {
    val textSize: TextUnit = if (compact) 10.sp else 11.sp
    Column(GlanceModifier.fillMaxWidth()) {
        if (row.isError) {
            Text(
                row.titleLine,
                style = TextStyle(color = Red, fontSize = textSize, fontFamily = FontFamily.Monospace),
                maxLines = 2,
            )
        } else {
            // Same split as the app: one wrapping line cannot hold the label, ten meter slots,
            // the percent and the reset detail.
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(
                    row.label,
                    style = TextStyle(color = Label, fontSize = textSize, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                    maxLines = 1,
                )
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    AeyeFormatting.percentString(row.percentUsed),
                    style = TextStyle(color = Label, fontSize = textSize, fontFamily = FontFamily.Monospace),
                    maxLines = 1,
                )
            }
            // Ten slots at the emoji's size for this text size: 1.4375 × the font size apiece.
            val meterHeight = ((textSize.value + 1) * 1.4375f).dp
            Image(
                provider = ImageProvider(meterBitmap(row.percentUsed, heightPx = 72)),
                contentDescription = "${AeyeFormatting.percentString(row.percentUsed)} used",
                contentScale = ContentScale.Fit,
                modifier = GlanceModifier.height(meterHeight).width(meterHeight * 10),
            )
            if (!compact) {
                val detail = row.suffix?.let { AeyeFormatting.standaloneSuffix(it) }.orEmpty()
                if (detail.isNotEmpty()) {
                    Text(detail, style = TextStyle(color = Secondary, fontSize = 9.sp), maxLines = 1)
                }
            }
        }
        if (!compact && row.subtitle.isNotEmpty()) {
            Text(row.subtitle, style = TextStyle(color = Secondary, fontSize = 9.sp), maxLines = 1)
        }
    }
}
