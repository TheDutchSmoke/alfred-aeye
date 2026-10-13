package com.giovanni.aeye.wear

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.giovanni.aeye.core.AeyeFormatting
import com.giovanni.aeye.core.AeyeSnapshot
import com.giovanni.aeye.core.MockSnapshot

/**
 * `AeyeWatchComplication.swift`. Each family shows the focus row — the first visible one, as on
 * watchOS:
 *
 * - SHORT_TEXT: the circular complication — short label over the percent.
 * - RANGED_VALUE: the corner complication — the percent as a gauge, labelled.
 * - LONG_TEXT: the rectangular and inline complications — "🦉 Aeye" over the first two rows.
 */
class AeyeComplicationService : SuspendingComplicationDataSourceService() {
    override fun getPreviewData(type: ComplicationType): ComplicationData? = build(type, MockSnapshot.preview)

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        build(request.complicationType, WatchStore.load(this) ?: AeyeSnapshot.empty)

    private fun build(type: ComplicationType, snapshot: AeyeSnapshot): ComplicationData? {
        val rows = snapshot.visibleRows
        val focus = rows.firstOrNull()
        val label = focus?.id?.shortLabel ?: "Aeye"
        val percent = AeyeFormatting.watchComplicationPercent(focus?.percentUsed)
        val description = PlainComplicationText.Builder(
            if (focus == null) "Aeye: open Aeye on your phone to sync" else "Aeye ${focus.id.provider} $label $percent"
        ).build()
        val tap = PendingIntent.getActivity(
            this,
            0,
            Intent(this, WatchActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(text(percent), description)
                .setTitle(text(label))
                .setTapAction(tap)
                .build()
            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                value = (focus?.percentUsed ?: 0.0).coerceIn(0.0, 100.0).toFloat(),
                min = 0f,
                max = 100f,
                contentDescription = description,
            )
                .setText(text(percent))
                .setTitle(text(label))
                .setTapAction(tap)
                .build()
            ComplicationType.LONG_TEXT -> {
                val body = if (rows.isEmpty()) "Open Aeye on your phone" else rows.take(2).joinToString("  ") {
                    "${it.id.shortLabel} ${AeyeFormatting.watchComplicationPercent(it.percentUsed)}"
                }
                LongTextComplicationData.Builder(text(body), description)
                    .setTitle(text(if (snapshot.isMockData) "🦉 Aeye · Sample" else "🦉 Aeye"))
                    .setTapAction(tap)
                    .build()
            }
            else -> null
        }
    }

    private fun text(value: String) = PlainComplicationText.Builder(value).build()
}
