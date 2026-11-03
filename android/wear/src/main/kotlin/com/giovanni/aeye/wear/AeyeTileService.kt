package com.giovanni.aeye.wear

import androidx.concurrent.futures.ResolvableFuture
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.giovanni.aeye.core.AeyeFormatting
import com.giovanni.aeye.core.AeyeSnapshot
import com.google.common.util.concurrent.ListenableFuture

/**
 * The tile — the Smart Stack widget's counterpart on watchOS: "🦉 Aeye", the Sample chip, and the
 * visible rows with their percentages. Tapping opens the app. Like the complications it reads the
 * last snapshot the phone sent.
 */
class AeyeTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val snapshot = WatchStore.load(this) ?: AeyeSnapshot.empty
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            // Same cadence as the iOS timeline; pushes from the phone update it sooner.
            .setFreshnessIntervalMillis(15 * 60 * 1000L)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout(snapshot)))
            .build()
        return done(tile)
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        done(ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build())

    private fun layout(snapshot: AeyeSnapshot): LayoutElementBuilders.LayoutElement {
        val open = ModifiersBuilders.Modifiers.Builder()
            .setClickable(
                ModifiersBuilders.Clickable.Builder()
                    .setId("open")
                    .setOnClick(
                        ActionBuilders.LaunchAction.Builder()
                            .setAndroidActivity(
                                ActionBuilders.AndroidActivity.Builder()
                                    .setPackageName(packageName)
                                    .setClassName(WatchActivity::class.java.name)
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

        val header = LayoutElementBuilders.Row.Builder()
            .addContent(text("🦉 Aeye", 15f, WHITE, bold = true))
        if (snapshot.isMockData) {
            header.addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(6f)).build())
            header.addContent(text("Sample", 11f, ORANGE, bold = true))
        }

        val column = LayoutElementBuilders.Column.Builder()
            .setWidth(dp(150f))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)
            .setModifiers(open)
            .addContent(header.build())
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(6f)).build())

        val rows = snapshot.visibleRows
        if (rows.isEmpty()) {
            column.addContent(text("Open Aeye on your phone to sync", 12f, GREY, maxLines = 2))
        } else {
            rows.take(4).forEach { row ->
                column.addContent(
                    LayoutElementBuilders.Row.Builder()
                        .setWidth(expand())
                        .addContent(
                            LayoutElementBuilders.Box.Builder()
                                .setWidth(expand())
                                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)
                                .addContent(
                                    LayoutElementBuilders.Row.Builder()
                                        .addContent(text(row.id.provider, 12f, GREY))
                                        .addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(4f)).build())
                                        .addContent(text(row.id.shortLabel, 13f, WHITE, bold = true))
                                        .build()
                                )
                                .build()
                        )
                        .addContent(
                            text(
                                AeyeFormatting.watchComplicationPercent(row.percentUsed),
                                13f,
                                if (row.isError) RED else WHITE,
                                bold = true,
                            )
                        )
                        .build()
                )
            }
        }

        return LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .addContent(column.build())
            .build()
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false, maxLines: Int = 1): LayoutElementBuilders.Text =
        LayoutElementBuilders.Text.Builder()
            .setText(value)
            .setMaxLines(maxLines)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(size))
                    .setColor(argb(color))
                    .setWeight(if (bold) LayoutElementBuilders.FONT_WEIGHT_BOLD else LayoutElementBuilders.FONT_WEIGHT_NORMAL)
                    .build()
            )
            .build()

    private fun <T> done(value: T): ListenableFuture<T> = ResolvableFuture.create<T>().apply { set(value) }

    private companion object {
        const val RESOURCES_VERSION = "1"
        const val WHITE = 0xFFFFFFFF.toInt()
        const val GREY = 0xFF9B9BA1.toInt()
        const val ORANGE = 0xFFFF9F0A.toInt()
        const val RED = 0xFFFF453A.toInt()
    }
}
