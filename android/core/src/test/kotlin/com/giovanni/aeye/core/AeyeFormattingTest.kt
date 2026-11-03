package com.giovanni.aeye.core

import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** Pins the clock, zone and locale, as the iOS formatting would see them on a US iPhone. */
abstract class PinnedEnvironment {
    /** Thursday 2026-09-10 14:00 New York time. */
    protected var now: Instant = Instant.parse("2026-09-10T18:00:00Z")

    @BeforeEach
    fun pin() {
        AeyeEnvironment.now = { now }
        AeyeEnvironment.zone = { ZoneId.of("America/New_York") }
        AeyeEnvironment.locale = { Locale.US }
    }

    @AfterEach
    fun unpin() {
        AeyeEnvironment.now = { Instant.now() }
        AeyeEnvironment.zone = { ZoneId.systemDefault() }
        AeyeEnvironment.locale = { Locale.getDefault() }
    }

    protected fun hours(h: Double): Instant = now.plusMillis((h * 3_600_000).toLong())
    protected fun days(d: Double): Instant = now.plusMillis((d * 86_400_000).toLong())
}

class AeyeFormattingTest : PinnedEnvironment() {
    @Test
    fun barFillsAndColoursLikeAlfred() {
        assertEquals("⚪⚪⚪⚪⚪⚪⚪⚪⚪⚪", AeyeFormatting.bar(null))
        assertEquals("⚪⚪⚪⚪⚪⚪⚪⚪⚪⚪", AeyeFormatting.bar(0.0))
        assertEquals("🟢⚪⚪⚪⚪⚪⚪⚪⚪⚪", AeyeFormatting.bar(13.0))
        assertEquals("🟢🟢🟢🟢⚪⚪⚪⚪⚪⚪", AeyeFormatting.bar(42.0))
        // 45 % rounds half away from zero to five slots, as Swift's round() does.
        assertEquals("🟢🟢🟢🟢🟢⚪⚪⚪⚪⚪", AeyeFormatting.bar(45.0))
        assertEquals("🟢🟢🟢🟢🟢🟡🟡🟡🔴⚪", AeyeFormatting.bar(90.0))
        assertEquals("🟢🟢🟢🟢🟢🟡🟡🟡🔴🔴", AeyeFormatting.bar(250.0))
        assertEquals("⚪⚪⚪⚪⚪⚪⚪⚪⚪⚪", AeyeFormatting.bar(-5.0))
    }

    @Test
    fun percentUsesAPointAndPrintfRounding() {
        assertEquals("n/a", AeyeFormatting.percentString(null))
        assertEquals("13.0%", AeyeFormatting.percentString(13.0))
        assertEquals("0.0%", AeyeFormatting.percentString(0.0))
    }

    @Test
    fun percentMatchesCPrintf() {
        // C's printf rounds the exact binary value, ties to even: 4.25 → "4.2", 4.35 (really
        // 4.3499…) → "4.3", 4.15 (really 4.1500…02) → "4.2".
        assertEquals("4.2%", AeyeFormatting.percentString(4.25))
        assertEquals("4.3%", AeyeFormatting.percentString(4.35))
        assertEquals("4.2%", AeyeFormatting.percentString(4.15))
        assertEquals("100.0%", AeyeFormatting.percentString(100.0))
    }

    @Test
    fun percentIgnoresTheLocaleDecimalSeparator() {
        AeyeEnvironment.locale = { Locale.GERMANY }
        assertEquals("13.5%", AeyeFormatting.percentString(13.5))
    }

    @Test
    fun moneyIsUsdInTheUserLocale() {
        assertEquals("$2.60", AeyeFormatting.moneyCents(260.0))
        assertEquals("$20.00", AeyeFormatting.moneyCents(2000.0))
        assertEquals("n/a", AeyeFormatting.moneyCents(null))
    }

    @Test
    fun countdownSteps() {
        assertNull(AeyeFormatting.resetCountdown(null))
        assertEquals("now", AeyeFormatting.resetCountdown(now))
        assertEquals("now", AeyeFormatting.resetCountdown(hours(-1.0)))
        assertEquals("1m", AeyeFormatting.resetCountdown(now.plusSeconds(10)))
        assertEquals("45m", AeyeFormatting.resetCountdown(now.plusSeconds(45 * 60)))
        assertEquals("2h", AeyeFormatting.resetCountdown(hours(2.0)))
        assertEquals("3h", AeyeFormatting.resetCountdown(hours(2.5)))
        assertEquals("1d", AeyeFormatting.resetCountdown(hours(24.0)))
        assertEquals("11d", AeyeFormatting.resetCountdown(days(11.0)))
    }

    @Test
    fun resetDetailStyles() {
        val end = Instant.parse("2026-09-24T16:01:00Z") // Thu Sep 24, 12:01 pm New York
        assertEquals("Thu Sep 24", AeyeFormatting.resetDetail(end, AeyeFormatting.ResetDetailStyle.CALENDAR))
        assertEquals("Thu 12pm", AeyeFormatting.resetDetail(end, AeyeFormatting.ResetDetailStyle.WEEKDAY))
        assertEquals("12:01pm", AeyeFormatting.resetDetail(end, AeyeFormatting.ResetDetailStyle.CLOCK))
        val midnight = Instant.parse("2026-09-26T04:05:00Z") // Sat 12:05 am
        assertEquals("Sat 12am", AeyeFormatting.resetDetail(midnight, AeyeFormatting.ResetDetailStyle.WEEKDAY))
        assertEquals("12:05am", AeyeFormatting.resetDetail(midnight, AeyeFormatting.ResetDetailStyle.CLOCK))
    }

    @Test
    fun paceBands() {
        assertEquals("", AeyeFormatting.paceEmoji(null, 50.0))
        assertEquals("🐢", AeyeFormatting.paceEmoji(13.0, 65.0))
        assertEquals("🎯", AeyeFormatting.paceEmoji(18.0, 14.0))
        assertEquals("🔥", AeyeFormatting.paceEmoji(60.0, 40.0))
        assertEquals("🎯", AeyeFormatting.paceEmoji(48.0, 40.0)) // exactly the band
    }

    @Test
    fun periodSuffixLikeTheScreenshot() {
        // Composer / Auto: 20 days into a 31-day cycle → 65 %, 11 days left.
        val suffix = AeyeFormatting.periodSuffix(days(11.0), days(-20.0), 13.0, AeyeFormatting.ResetDetailStyle.CALENDAR)
        assertEquals("  (🕐 65%, 11d, Mon Sep 21) 🐢", suffix)
        assertEquals("🕐 65%, 11d, Mon Sep 21 🐢", AeyeFormatting.standaloneSuffix(suffix))
    }

    @Test
    fun periodSuffixWithoutStartOrEnd() {
        assertEquals("", AeyeFormatting.periodSuffix(null))
        assertEquals("  (2h, 4:00pm)", AeyeFormatting.periodSuffix(hours(2.0), style = AeyeFormatting.ResetDetailStyle.CLOCK))
        assertEquals("  (🕐 100%, now) 🎯", AeyeFormatting.periodSuffix(now, hours(-5.0), 95.0))
    }

    @Test
    fun titleLine() {
        assertEquals(
            "Weekly  🟢🟢⚪⚪⚪⚪⚪⚪⚪⚪  18.0%  (x)",
            AeyeFormatting.titleLine("Weekly", 18.0, "  (x)"),
        )
    }

    @Test
    fun standaloneSuffixEdgeCases() {
        assertEquals("", AeyeFormatting.standaloneSuffix(""))
        assertEquals("no parens", AeyeFormatting.standaloneSuffix("  no parens "))
        assertEquals("a, b", AeyeFormatting.standaloneSuffix("  (a, b)"))
    }

    @Test
    fun watchPercent() {
        assertEquals("—", AeyeFormatting.watchComplicationPercent(null))
        assertEquals("4.0%", AeyeFormatting.watchComplicationPercent(4.0))
        assertEquals("9.9%", AeyeFormatting.watchComplicationPercent(9.94))
        assertEquals("10%", AeyeFormatting.watchComplicationPercent(9.95))
        assertEquals("42%", AeyeFormatting.watchComplicationPercent(42.0))
        assertEquals("Auto  🟢⚪⚪⚪⚪⚪⚪⚪⚪⚪  13.0%", AeyeFormatting.watchRowLine(MockSnapshot.preview.rows[0]))
    }

    @Test
    fun parsesEveryResetShape() {
        val expected = Instant.parse("2026-09-24T16:00:00Z")
        assertEquals(expected, AeyeFormatting.parseResetDate(JsonPrimitive(expected.toEpochMilli())))
        assertEquals(expected, AeyeFormatting.parseResetDate(JsonPrimitive("${expected.toEpochMilli()}")))
        assertEquals(expected, AeyeFormatting.parseResetDate(JsonPrimitive("2026-09-24T16:00:00Z")))
        assertEquals(expected, AeyeFormatting.parseResetDate(JsonPrimitive("2026-09-24T16:00:00.000Z")))
        assertEquals(expected, AeyeFormatting.parseResetDate(JsonPrimitive("2026-09-24T12:00:00-04:00")))
        assertEquals(expected, AeyeFormatting.parseResetDate(null, epoch = expected.epochSecond))
        assertNull(AeyeFormatting.parseResetDate(JsonPrimitive("soon")))
        assertNull(AeyeFormatting.parseResetDate(JsonPrimitive(true)))
        assertNull(AeyeFormatting.parseResetDate(null))
    }
}
