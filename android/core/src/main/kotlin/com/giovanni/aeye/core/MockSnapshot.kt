package com.giovanni.aeye.core

import java.time.Instant

/**
 * Sample data for previews, widget galleries, screenshot runs, and the user-facing "Use sample
 * data" mode.
 *
 * Rows are composed through [AeyeFormatting] rather than hardcoded strings so the sample always
 * matches the real bar width and title format. Periods are relative to *now*, so the reset
 * countdowns stay plausible however long the app has been installed.
 */
object MockSnapshot {
    /** Every row enabled, unbadged — previews and widget galleries. */
    val preview: AeyeSnapshot get() = sample(markAsSample = false)

    /**
     * Sample rows honouring the user's row choices, so toggling a row off in Settings changes the
     * sample view the same way it changes the real one.
     *
     * [markAsSample] flags the snapshot so every surface shows its "Sample data" chip. Screenshot
     * runs pass `false`: those images depict the app as a signed-in user sees it.
     */
    fun sample(visibility: RowVisibility = RowVisibility.allEnabled, markAsSample: Boolean = true): AeyeSnapshot {
        val calendar = AeyeFormatting.ResetDetailStyle.CALENDAR
        val weekday = AeyeFormatting.ResetDetailStyle.WEEKDAY
        return AeyeSnapshot(
            capturedAt = AeyeEnvironment.now(),
            rows = listOf(
                row(
                    AeyeRowID.COMPOSER_AUTO, 13.0,
                    AeyeFormatting.periodSuffix(date(days = 11), date(days = -20), 13.0, calendar),
                    "You've used 13% of your included total usage · $2.60 / $20.00",
                    "Cursor Composer/Auto", AeyeURLs.CURSOR_DASHBOARD,
                ),
                row(
                    AeyeRowID.OTHER_MODELS, 4.0,
                    AeyeFormatting.periodSuffix(date(days = 11), date(days = -20), 4.0, calendar),
                    "You've used 4% of your included API usage",
                    "Cursor other models", AeyeURLs.CURSOR_DASHBOARD,
                ),
                row(
                    AeyeRowID.GROK_BOT, 1.0,
                    AeyeFormatting.periodSuffix(date(days = 6), date(days = -1), 1.0, weekday),
                    "Pro · Grok Bot Plan · weekly included",
                    "Grok Bot", AeyeURLs.CURSOR_DASHBOARD,
                ),
                row(
                    AeyeRowID.HOURLY, 42.0,
                    AeyeFormatting.periodSuffix(date(hours = 2), date(hours = -3), 42.0, AeyeFormatting.ResetDetailStyle.CLOCK),
                    "plan=pro · experimental",
                    "Claude hourly", AeyeURLs.CLAUDE_USAGE,
                ),
                row(
                    AeyeRowID.WEEKLY, 18.0,
                    AeyeFormatting.periodSuffix(date(days = 6), date(days = -1), 18.0, weekday),
                    "plan=pro · experimental",
                    "Claude weekly", AeyeURLs.CLAUDE_USAGE,
                ),
            ),
            visibility = visibility,
            isMockData = markAsSample,
        )
    }

    private fun row(
        id: AeyeRowID,
        percent: Double,
        suffix: String,
        subtitle: String,
        copy: String,
        dashboard: String?,
    ) = OverviewRow(
        id = id,
        label = id.label,
        percentUsed = percent,
        titleLine = AeyeFormatting.titleLine(id.label, percent, suffix),
        subtitle = subtitle,
        copyText = "$copy: ${AeyeFormatting.percentString(percent)}$suffix",
        dashboardURL = dashboard,
        suffix = suffix,
    )

    private fun date(days: Long = 0, hours: Long = 0): Instant =
        AeyeEnvironment.now().plusSeconds(days * 86_400 + hours * 3_600)
}
