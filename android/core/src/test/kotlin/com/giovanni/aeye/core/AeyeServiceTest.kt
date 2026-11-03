package com.giovanni.aeye.core

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AeyeServiceTest : PinnedEnvironment() {
    private val secrets = MemorySecretStore()
    private val credentials = CredentialsStore(secrets)
    private val transport = FakeTransport()
    private val saved = mutableListOf<AeyeSnapshot>()
    private val service = AeyeService(credentials, transport) { saved += it }

    private fun signInEverywhere() {
        credentials.saveCursorSessionToken("u::t")
        credentials.saveClaudeTokens(ClaudeOAuth.Tokens("A", "R", now.plusSeconds(3600)))
        transport.on("https://cursor.com/api/auth/me", body = """{"email":"me@x.test"}""")
        transport.on(
            "https://cursor.com/api/dashboard/get-current-period-usage",
            body = """{"planUsage":{"autoPercentUsed":13,"apiPercentUsed":4,"totalSpend":260,"limit":2000},
                "autoModelSelectedDisplayMessage":"You've used 13% of your included total usage",
                "billingCycleStart":"${now.minusSeconds(20 * 86400)}","billingCycleEnd":"${now.plusSeconds(11 * 86400)}"}""",
        )
        transport.on(
            "https://cursor.com/api/dashboard/get-sand-usage-status",
            body = """{"hasNonZeroIncludedLimit":true,"usagePercent":1,"cursorPlanName":"Pro","grokPlanLabel":"Grok Bot Plan",
                "currentPeriodStart":"${now.minusSeconds(86400)}","nextResetTimestampUtc":"${now.plusSeconds(6 * 86400)}"}""",
        )
        transport.on(
            ClaudeClient.USAGE_URL,
            body = """{"plan":"pro","five_hour":{"utilization":42,"resets_at":"${now.plusSeconds(7200)}"},
                "seven_day":{"utilization":18,"resets_at":"${now.plusSeconds(6 * 86400)}"}}""",
        )
    }

    @Test
    fun buildsEveryRowInAlfredOrder() = runTest {
        signInEverywhere()
        val snapshot = service.fetchOverview(RowVisibility.allEnabled)
        assertEquals(
            listOf(AeyeRowID.COMPOSER_AUTO, AeyeRowID.OTHER_MODELS, AeyeRowID.GROK_BOT, AeyeRowID.HOURLY, AeyeRowID.WEEKLY),
            snapshot.rows.map { it.id },
        )
        val auto = snapshot.rows[0]
        assertEquals("You've used 13% of your included total usage · $2.60 / $20.00", auto.subtitle)
        assertEquals("  (🕐 65%, 11d, Mon Sep 21) 🐢", auto.suffix)
        assertEquals("Cursor Composer/Auto: 13.0%  (🕐 65%, 11d, Mon Sep 21) 🐢", auto.copyText)
        assertEquals(AeyeURLs.CURSOR_DASHBOARD, auto.dashboardURL)

        assertEquals("API / named models · me@x.test", snapshot.rows[1].subtitle)
        assertEquals("Pro · Grok Bot Plan · weekly included", snapshot.rows[2].subtitle)
        assertEquals("  (🕐 14%, 6d, Wed 2pm) 🐢", snapshot.rows[2].suffix)

        // No active session reported: the hourly row has no suffix and spells out the reset.
        val hourly = snapshot.rows[3]
        assertEquals("", hourly.suffix)
        assertEquals("plan=pro · experimental · 16:00 · (2h)", hourly.subtitle)
        assertEquals("Hourly  🟢🟢🟢🟢⚪⚪⚪⚪⚪⚪  42.0%", hourly.titleLine)

        val weekly = snapshot.rows[4]
        assertEquals("plan=pro · experimental", weekly.subtitle)
        assertEquals("  (🕐 14%, 6d, Wed 2pm) 🎯", weekly.suffix)

        assertEquals(listOf(snapshot), saved)
        assertFalse(snapshot.isMockData)
    }

    @Test
    fun hiddenRowsAreNotFetched() = runTest {
        signInEverywhere()
        val visibility = RowVisibility(cursorAuto = false, cursorOther = false, cursorGrokBot = false, claudeHourly = false, claudeWeekly = true)
        val snapshot = service.fetchOverview(visibility)
        assertEquals(listOf(AeyeRowID.WEEKLY), snapshot.rows.map { it.id })
        assertEquals(0, transport.count("https://cursor.com"))
    }

    @Test
    fun noRowsEnabledIsAnErrorRow() = runTest {
        val none = RowVisibility(cursorAuto = false, cursorOther = false, cursorGrokBot = false, claudeHourly = false, claudeWeekly = false)
        val row = service.fetchOverview(none).rows.single()
        assertTrue(row.isError)
        assertEquals("Aeye: no rows enabled", row.titleLine)
        assertEquals("Turn on at least one row in Settings", row.subtitle)
    }

    @Test
    fun missingCredentialsAreErrorRowsThatStayVisible() = runTest {
        val visibility = RowVisibility(cursorAuto = false, cursorOther = false, cursorGrokBot = true, claudeHourly = false, claudeWeekly = true)
        val snapshot = service.fetchOverview(visibility)
        assertEquals(listOf("Cursor: unavailable", "Claude: unavailable"), snapshot.rows.map { it.titleLine })
        assertEquals(listOf("Cursor session token not set", "Claude OAuth token not set"), snapshot.rows.map { it.subtitle })
        // Error rows show even though their ids (Composer / Auto, Hourly) are toggled off.
        assertEquals(2, snapshot.visibleRows.size)
    }

    @Test
    fun aGrokFailureDoesNotSinkTheOtherCursorRows() = runTest {
        signInEverywhere()
        transport.on("https://cursor.com/api/dashboard/get-sand-usage-status", status = 403, body = "")
        val rows = service.fetchOverview(RowVisibility.allEnabled).rows
        val grok = rows.first { it.id == AeyeRowID.GROK_BOT }
        assertTrue(grok.isError)
        assertEquals("Grok Bot: unavailable", grok.titleLine)
        assertEquals("Cursor HTTP 403 — session expired. Paste a new token in Settings.", grok.subtitle)
        assertFalse(rows.first().isError)
    }

    @Test
    fun aRejectedClaudeTokenIsRefreshedOnce() = runTest {
        signInEverywhere()
        var usageCalls = 0
        transport.onRequest(ClaudeClient.USAGE_URL) { request ->
            usageCalls += 1
            if (request.headers["Authorization"] == "Bearer A") HttpResponse(401, "{}".toByteArray())
            else HttpResponse(200, """{"five_hour":{"utilization":5}}""".toByteArray())
        }
        transport.on(ClaudeOAuth.TOKEN_URL, body = """{"access_token":"B","expires_in":3600}""")
        val hourly = service.fetchOverview(RowVisibility.allEnabled).rows.first { it.id == AeyeRowID.HOURLY }
        assertEquals(5.0, hourly.percentUsed)
        assertEquals(2, usageCalls)
        assertEquals("B", credentials.claudeOAuthToken)
    }

    @Test
    fun cachesForAMinuteUnlessForced() = runTest {
        signInEverywhere()
        val first = service.fetchOverview(RowVisibility.allEnabled)
        assertSame(first, service.fetchOverview(RowVisibility.allEnabled))
        now = now.plusSeconds(59)
        assertSame(first, service.fetchOverview(RowVisibility.allEnabled))
        val forced = service.fetchOverview(RowVisibility.allEnabled, forceRefresh = true)
        assertTrue(forced !== first)
        now = now.plusSeconds(61)
        assertTrue(service.fetchOverview(RowVisibility.allEnabled) !== forced)
        assertEquals(3, transport.count(ClaudeClient.USAGE_URL))
    }

    @Test
    fun aDifferentVisibilityMissesTheCache() = runTest {
        signInEverywhere()
        service.fetchOverview(RowVisibility.allEnabled)
        service.fetchOverview(RowVisibility.allEnabled.copy(cursorGrokBot = false))
        assertEquals(2, transport.count(ClaudeClient.USAGE_URL))
    }

    @Test
    fun concurrentCallersShareOneFetch() = runTest {
        signInEverywhere()
        val a = async { service.fetchOverview(RowVisibility.allEnabled) }
        val b = async { service.fetchOverview(RowVisibility.allEnabled) }
        assertSame(a.await(), b.await())
        assertEquals(1, transport.count(ClaudeClient.USAGE_URL))
    }

    @Test
    fun networkFailuresReadLikeIos() = runTest {
        credentials.saveCursorSessionToken("u::t")
        val offline = AeyeService(credentials, { throw java.net.UnknownHostException("cursor.com") })
        val row = offline.fetchOverview(RowVisibility.allEnabled.copy(claudeHourly = false, claudeWeekly = false, cursorGrokBot = false)).rows.single()
        assertEquals("The Internet connection appears to be offline.", row.subtitle)
    }
}

class SnapshotCodecTest : PinnedEnvironment() {
    @Test
    fun roundTrips() {
        val sample = MockSnapshot.sample()
        val decoded = SnapshotCodec.decode(SnapshotCodec.encode(sample))
        assertEquals(sample, decoded)
    }

    @Test
    fun readsWhatTheIosEncoderWrites() {
        // JSONEncoder(.iso8601, .sortedKeys) output for a one-row snapshot from an older build:
        // no `suffix`, no `percentUsed` (nil), no `cursorGrokBot`.
        val ios = """{"capturedAt":"2026-09-10T18:00:00Z","isMockData":false,"rows":[{"copyText":"x","id":"claude_hourly",
            "isError":true,"label":"Claude","subtitle":"Claude OAuth token not set","titleLine":"Claude: unavailable",
            "dashboardURL":"https:\/\/claude.ai\/settings\/usage"}],"visibility":{"claudeHourly":true,"claudeWeekly":true,
            "cursorAuto":true,"cursorOther":false}}"""
        val snapshot = SnapshotCodec.decode(ios)
        assertNotNull(snapshot)
        snapshot!!
        assertEquals(now, snapshot.capturedAt)
        assertEquals(AeyeRowID.HOURLY, snapshot.rows.single().id)
        assertEquals(AeyeURLs.CLAUDE_USAGE, snapshot.rows.single().dashboardURL)
        assertTrue(snapshot.visibility.cursorGrokBot)
        assertFalse(snapshot.visibility.cursorOther)
    }

    @Test
    fun emptyIsDistantPast() {
        val text = SnapshotCodec.encode(AeyeSnapshot.empty)
        assertTrue(text.contains("\"capturedAt\":\"0001-01-01T00:00:00Z\""))
        assertFalse(SnapshotCodec.decode(text)!!.hasCapture)
        assertTrue(MockSnapshot.preview.hasCapture)
    }

    @Test
    fun sampleHonoursVisibilityAndTheSampleFlag() {
        val sample = MockSnapshot.sample(RowVisibility.allEnabled.copy(cursorOther = false))
        assertTrue(sample.isMockData)
        assertEquals(4, sample.visibleRows.size)
        assertFalse(MockSnapshot.preview.isMockData)
        assertEquals("You've used 13% of your included total usage · $2.60 / $20.00", sample.rows[0].subtitle)
    }
}
