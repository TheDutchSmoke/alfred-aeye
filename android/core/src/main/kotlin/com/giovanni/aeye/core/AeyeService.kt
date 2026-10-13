package com.giovanni.aeye.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Builds the overview — logic ported from `src/aieye.py` by way of `AeyeService.swift`.
 *
 * [onSnapshotBuilt] is `SnapshotStore.save` + `saveVisibility` on iOS: every freshly built
 * snapshot is persisted for the widget and the watch.
 */
class AeyeService(
    private val credentials: CredentialsStore,
    private val transport: HttpTransport,
    private val onSnapshotBuilt: (AeyeSnapshot) -> Unit = {},
) {
    private val lock = Mutex()
    private var cachedSnapshot: AeyeSnapshot? = null
    private var cacheTimestamp: Instant? = null
    private var inFlight: CompletableDeferred<AeyeSnapshot>? = null

    suspend fun fetchOverview(visibility: RowVisibility, forceRefresh: Boolean = false): AeyeSnapshot {
        val pending = lock.withLock {
            val cached = cachedSnapshot
            val stamp = cacheTimestamp
            if (!forceRefresh && cached != null && stamp != null && cached.visibility == visibility &&
                AeyeFormatting.secondsBetween(stamp, AeyeEnvironment.now()) < AeyeFormatting.OVERVIEW_CACHE_TTL_SECONDS
            ) {
                return cached
            }
            inFlight
        }

        if (pending != null) {
            val snapshot = pending.await()
            if (!forceRefresh) return snapshot
        }

        val deferred = CompletableDeferred<AeyeSnapshot>()
        lock.withLock { inFlight = deferred }
        try {
            val snapshot = buildSnapshot(visibility)
            deferred.complete(snapshot)
            return snapshot
        } catch (e: Throwable) {
            deferred.completeExceptionally(e)
            throw e
        } finally {
            lock.withLock { if (inFlight === deferred) inFlight = null }
        }
    }

    /** Drops the 60-second cache, as a fresh `AeyeService` would. */
    suspend fun invalidate() {
        lock.withLock {
            cachedSnapshot = null
            cacheTimestamp = null
        }
    }

    private suspend fun buildSnapshot(visibility: RowVisibility): AeyeSnapshot {
        var rows = coroutineScope {
            val cursor = async { cursorRowsIfNeeded(visibility) }
            val claude = async { claudeRowsIfNeeded(visibility) }
            cursor.await() + claude.await()
        }

        if (rows.isEmpty()) {
            rows = listOf(
                OverviewRow(
                    id = AeyeRowID.COMPOSER_AUTO,
                    label = "Aeye",
                    percentUsed = null,
                    titleLine = "Aeye: no rows enabled",
                    subtitle = "Turn on at least one row in Settings",
                    copyText = "",
                    isError = true,
                )
            )
        }

        val snapshot = AeyeSnapshot(
            capturedAt = AeyeEnvironment.now(),
            rows = rows,
            visibility = visibility,
        )
        lock.withLock {
            cachedSnapshot = snapshot
            cacheTimestamp = AeyeEnvironment.now()
        }
        onSnapshotBuilt(snapshot)
        return snapshot
    }

    // ---- Cursor rows ----------------------------------------------------------------------------

    private suspend fun cursorRowsIfNeeded(visibility: RowVisibility): List<OverviewRow> {
        if (!(visibility.cursorAuto || visibility.cursorOther || visibility.cursorGrokBot)) return emptyList()
        return cursorRows(visibility)
    }

    private suspend fun claudeRowsIfNeeded(visibility: RowVisibility): List<OverviewRow> {
        if (!(visibility.claudeHourly || visibility.claudeWeekly)) return emptyList()
        return claudeRows(visibility)
    }

    private suspend fun cursorRows(visibility: RowVisibility): List<OverviewRow> {
        val raw = credentials.cursorSessionToken
        if (!credentials.hasCursor || raw == null) return cursorError("Cursor session token not set")

        val client = CursorClient(CursorClient.normalizeCookieValue(raw), transport)
        val rows = mutableListOf<OverviewRow>()

        if (visibility.cursorAuto || visibility.cursorOther) {
            try {
                rows += buildCursorRows(client.fetchUsage(), visibility)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                rows += cursorError(errorMessage(e))
            }
        }

        if (visibility.cursorGrokBot) {
            try {
                client.fetchGrokBotUsage()?.let { rows += buildGrokBotRow(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                rows += grokBotError(errorMessage(e))
            }
        }
        return rows
    }

    private fun buildCursorRows(data: CursorUsageData, visibility: RowVisibility): List<OverviewRow> {
        val autoSuffix = AeyeFormatting.periodSuffix(
            end = data.billingEnd,
            start = data.billingStart,
            spentPercent = data.autoPercentUsed,
            style = AeyeFormatting.ResetDetailStyle.CALENDAR,
        )
        val otherSuffix = AeyeFormatting.periodSuffix(
            end = data.billingEnd,
            start = data.billingStart,
            spentPercent = data.apiPercentUsed,
            style = AeyeFormatting.ResetDetailStyle.CALENDAR,
        )

        val spendBits = mutableListOf<String>()
        if (data.totalSpendCents != null && data.limitCents != null) {
            spendBits += "${AeyeFormatting.moneyCents(data.totalSpendCents)} / ${AeyeFormatting.moneyCents(data.limitCents)}"
        }

        val autoSubParts = mutableListOf<String>()
        data.autoMessage?.let { autoSubParts += it }
        autoSubParts += spendBits
        val autoSub = if (autoSubParts.isEmpty()) data.email else autoSubParts.joinToString(" · ")

        val otherSub = data.apiMessage ?: "API / named models · ${data.email}"

        val rows = mutableListOf<OverviewRow>()
        if (visibility.cursorAuto) {
            rows += OverviewRow(
                id = AeyeRowID.COMPOSER_AUTO,
                label = AeyeRowID.COMPOSER_AUTO.label,
                percentUsed = data.autoPercentUsed,
                titleLine = AeyeFormatting.titleLine(AeyeRowID.COMPOSER_AUTO.label, data.autoPercentUsed, autoSuffix),
                subtitle = autoSub,
                copyText = "Cursor Composer/Auto: ${AeyeFormatting.percentString(data.autoPercentUsed)}$autoSuffix",
                dashboardURL = AeyeURLs.CURSOR_DASHBOARD,
                suffix = autoSuffix,
            )
        }
        if (visibility.cursorOther) {
            rows += OverviewRow(
                id = AeyeRowID.OTHER_MODELS,
                label = AeyeRowID.OTHER_MODELS.label,
                percentUsed = data.apiPercentUsed,
                titleLine = AeyeFormatting.titleLine(AeyeRowID.OTHER_MODELS.label, data.apiPercentUsed, otherSuffix),
                subtitle = otherSub,
                copyText = "Cursor other models: ${AeyeFormatting.percentString(data.apiPercentUsed)}$otherSuffix",
                dashboardURL = AeyeURLs.CURSOR_DASHBOARD,
                suffix = otherSuffix,
            )
        }
        return rows
    }

    private fun buildGrokBotRow(data: GrokBotUsage): OverviewRow {
        val suffix = AeyeFormatting.periodSuffix(
            end = data.periodEnd,
            start = data.periodStart,
            spentPercent = data.usagePercent,
            style = AeyeFormatting.ResetDetailStyle.WEEKDAY,
        )
        val bits = mutableListOf<String>()
        data.cursorPlanName?.takeIf { it.isNotEmpty() }?.let { bits += it }
        data.grokPlanLabel?.takeIf { it.isNotEmpty() }?.let { bits += it }
        bits += "weekly included"
        if (data.hasAvailableUsage == false) bits += "exhausted"

        return OverviewRow(
            id = AeyeRowID.GROK_BOT,
            label = AeyeRowID.GROK_BOT.label,
            percentUsed = data.usagePercent,
            titleLine = AeyeFormatting.titleLine(AeyeRowID.GROK_BOT.label, data.usagePercent, suffix),
            subtitle = bits.joinToString(" · "),
            copyText = "Grok Bot: ${AeyeFormatting.percentString(data.usagePercent)}$suffix",
            dashboardURL = AeyeURLs.CURSOR_DASHBOARD,
            suffix = suffix,
        )
    }

    private fun cursorError(message: String): List<OverviewRow> = listOf(
        OverviewRow(
            id = AeyeRowID.COMPOSER_AUTO,
            label = "Cursor",
            percentUsed = null,
            titleLine = "Cursor: unavailable",
            subtitle = message.prefixCodePoints(200),
            copyText = message,
            isError = true,
            dashboardURL = AeyeURLs.CURSOR_DASHBOARD,
        )
    )

    private fun grokBotError(message: String): OverviewRow = OverviewRow(
        id = AeyeRowID.GROK_BOT,
        label = "Grok Bot",
        percentUsed = null,
        titleLine = "Grok Bot: unavailable",
        subtitle = message.prefixCodePoints(200),
        copyText = message,
        isError = true,
        dashboardURL = AeyeURLs.CURSOR_DASHBOARD,
    )

    // ---- Claude rows ----------------------------------------------------------------------------

    private suspend fun claudeRows(visibility: RowVisibility): List<OverviewRow> {
        if (!credentials.hasClaude) return claudeError("Claude OAuth token not set")
        return try {
            buildClaudeRows(fetchClaudeUsage(), visibility)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            claudeError(errorMessage(e))
        }
    }

    /**
     * Access tokens last hours, so refresh before spending a request — and once more if the server
     * rejects a token we thought was still good.
     */
    private suspend fun fetchClaudeUsage(): ClaudeUsageData {
        val token = ClaudeOAuth.validAccessToken(credentials, transport)
        return try {
            ClaudeClient(token, transport).fetchUsage()
        } catch (e: ClaudeApiError.Http) {
            if (e.status != 401 && e.status != 403) throw e
            if (credentials.claudeRefreshToken == null) throw e
            val refreshed = ClaudeOAuth.validAccessToken(credentials, transport, forceRefresh = true)
            ClaudeClient(refreshed, transport).fetchUsage()
        }
    }

    private fun buildClaudeRows(data: ClaudeUsageData, visibility: RowVisibility): List<OverviewRow> {
        val fiveEnd = data.fiveHour.resetsAt
        val sevenEnd = data.sevenDay.resetsAt
        val fiveStart = fiveEnd?.minusSeconds(5 * 3600)
        val sevenStart = sevenEnd?.minusSeconds(7 * 86400)

        val fiveSuffix = if (data.sessionActive) {
            AeyeFormatting.periodSuffix(
                end = fiveEnd,
                start = fiveStart,
                spentPercent = data.fiveHour.usedPercentage,
                style = AeyeFormatting.ResetDetailStyle.CLOCK,
            )
        } else {
            ""
        }

        val sevenSuffix = AeyeFormatting.periodSuffix(
            end = sevenEnd,
            start = sevenStart,
            spentPercent = data.sevenDay.usedPercentage,
            style = AeyeFormatting.ResetDetailStyle.WEEKDAY,
        )

        val hourlyBits = mutableListOf("plan=${data.plan}")
        if (data.confidence != "unknown") hourlyBits += data.confidence
        if (!data.sessionActive) {
            formatResetTime(fiveEnd)?.let { hourlyBits += it }
            AeyeFormatting.resetCountdown(fiveEnd)?.let { hourlyBits += "($it)" }
        }
        data.statusLabel?.takeIf { it != "ok" }?.let { hourlyBits += it }
        data.sessionCostUSD?.let { hourlyBits += "session $${AeyeFormatting.fixed(it, 2)}" }

        val weeklyBits = mutableListOf("plan=${data.plan}")
        if (data.confidence != "unknown") weeklyBits += data.confidence
        if (sevenSuffix.isEmpty()) formatResetDateTime(sevenEnd)?.let { weeklyBits += it }

        val rows = mutableListOf<OverviewRow>()
        if (visibility.claudeHourly) {
            rows += OverviewRow(
                id = AeyeRowID.HOURLY,
                label = AeyeRowID.HOURLY.label,
                percentUsed = data.fiveHour.usedPercentage,
                titleLine = AeyeFormatting.titleLine(AeyeRowID.HOURLY.label, data.fiveHour.usedPercentage, fiveSuffix),
                subtitle = hourlyBits.joinToString(" · "),
                copyText = "Claude hourly: ${AeyeFormatting.percentString(data.fiveHour.usedPercentage)}$fiveSuffix",
                dashboardURL = AeyeURLs.CLAUDE_USAGE,
                suffix = fiveSuffix,
            )
        }
        if (visibility.claudeWeekly) {
            rows += OverviewRow(
                id = AeyeRowID.WEEKLY,
                label = AeyeRowID.WEEKLY.label,
                percentUsed = data.sevenDay.usedPercentage,
                titleLine = AeyeFormatting.titleLine(AeyeRowID.WEEKLY.label, data.sevenDay.usedPercentage, sevenSuffix),
                subtitle = weeklyBits.joinToString(" · "),
                copyText = "Claude weekly: ${AeyeFormatting.percentString(data.sevenDay.usedPercentage)}$sevenSuffix",
                dashboardURL = AeyeURLs.CLAUDE_USAGE,
                suffix = sevenSuffix,
            )
        }
        return rows
    }

    private fun claudeError(message: String): List<OverviewRow> = listOf(
        OverviewRow(
            id = AeyeRowID.HOURLY,
            label = "Claude",
            percentUsed = null,
            titleLine = "Claude: unavailable",
            subtitle = message.prefixCodePoints(200),
            copyText = message,
            isError = true,
            dashboardURL = AeyeURLs.CLAUDE_USAGE,
        )
    )

    private fun formatResetTime(instant: Instant?): String? =
        instant?.let { DateTimeFormatter.ofPattern("HH:mm").withZone(AeyeEnvironment.zone()).format(it) }

    private fun formatResetDateTime(instant: Instant?): String? =
        instant?.let { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(AeyeEnvironment.zone()).format(it) }
}
