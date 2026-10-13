package com.giovanni.aeye.core

import kotlinx.serialization.json.JsonElement
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Currency
import java.util.Locale
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * The clock, time zone and locale the formatting reads — `Date()`, `Calendar.current` and
 * `Locale.current` on iOS. Tests pin them; the app leaves the defaults.
 */
object AeyeEnvironment {
    @Volatile var now: () -> Instant = { Instant.now() }
    @Volatile var zone: () -> ZoneId = { ZoneId.systemDefault() }
    @Volatile var locale: () -> Locale = { Locale.getDefault() }
}

/** One slot of the circle meter, so it can be drawn as well as typed. */
enum class MeterSlot(val emoji: String) {
    GREEN("🟢"),
    YELLOW("🟡"),
    RED("🔴"),
    EMPTY("⚪"),
}

/** Presentation helpers ported from `src/aieye.py` by way of `AeyeFormatting.swift`. */
object AeyeFormatting {
    const val OVERVIEW_CACHE_TTL_SECONDS: Long = 60
    const val PACE_BAND: Double = 8.0

    // ---- Circle meter --------------------------------------------------------------------------

    fun meterSlots(percent: Double?, width: Int = 10): List<MeterSlot> {
        if (percent == null) return List(width) { MeterSlot.EMPTY }
        val clamped = max(0.0, min(100.0, percent))
        val filled = roundHalfAway(clamped / 100.0 * width).toInt().coerceIn(0, width)
        return (0 until width).map { i -> if (i < filled) slotColor(i, width) else MeterSlot.EMPTY }
    }

    fun bar(percent: Double?, width: Int = 10): String =
        meterSlots(percent, width).joinToString("") { it.emoji }

    private fun slotColor(index: Int, width: Int): MeterSlot {
        val edge = (index + 1).toDouble() / width * 100.0
        if (edge <= 50) return MeterSlot.GREEN
        if (edge <= 80) return MeterSlot.YELLOW
        return MeterSlot.RED
    }

    // ---- Percent & money -----------------------------------------------------------------------

    fun percentString(value: Double?): String {
        if (value == null) return "n/a"
        return fixed(value, 1) + "%"
    }

    fun moneyCents(cents: Double?): String {
        if (cents == null) return "n/a"
        val dollars = cents / 100.0
        val formatter = NumberFormat.getCurrencyInstance(AeyeEnvironment.locale())
        formatter.currency = Currency.getInstance("USD")
        formatter.minimumFractionDigits = 2
        formatter.maximumFractionDigits = 2
        return formatter.format(dollars)
    }

    // ---- Reset countdown & detail --------------------------------------------------------------

    fun resetCountdown(end: Instant?): String? {
        if (end == null) return null
        val seconds = secondsBetween(AeyeEnvironment.now(), end)
        if (seconds <= 0) return "now"
        if (seconds < 3600) {
            return "${max(1L, roundHalfAway(seconds / 60.0).toLong())}m"
        }
        val hours = seconds / 3600.0
        if (hours < 24) {
            return "${max(1L, roundHalfAway(hours).toLong())}h"
        }
        return "${max(1L, roundHalfAway(seconds / 86400.0).toLong())}d"
    }

    enum class ResetDetailStyle { CALENDAR, WEEKDAY, CLOCK }

    fun resetDetail(end: Instant?, style: ResetDetailStyle): String? {
        if (end == null) return null
        val local = end.atZone(AeyeEnvironment.zone())
        val locale = AeyeEnvironment.locale()
        val weekday = local.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
        return when (style) {
            ResetDetailStyle.CALENDAR -> {
                val month = local.month.getDisplayName(TextStyle.SHORT_STANDALONE, locale)
                "$weekday $month ${local.dayOfMonth}"
            }
            ResetDetailStyle.WEEKDAY -> {
                val (h12, ampm) = twelveHour(local.hour)
                "$weekday $h12$ampm"
            }
            ResetDetailStyle.CLOCK -> {
                val (h12, ampm) = twelveHour(local.hour)
                "$h12:${"%02d".format(local.minute)}$ampm"
            }
        }
    }

    private fun twelveHour(hour: Int): Pair<String, String> {
        var h = hour % 12
        if (h == 0) h = 12
        return "$h" to if (hour < 12) "am" else "pm"
    }

    // ---- Period elapsed & pace -----------------------------------------------------------------

    fun periodElapsedPercent(start: Instant?, end: Instant?): Double? {
        if (start == null || end == null) return null
        val total = secondsBetween(start, end)
        if (total <= 0) return null
        val done = secondsBetween(start, AeyeEnvironment.now())
        val pct = done / total * 100.0
        return roundHalfAway(max(0.0, min(100.0, pct)))
    }

    fun paceEmoji(spent: Double?, elapsed: Double?, band: Double = PACE_BAND): String {
        if (spent == null || elapsed == null) return ""
        val diff = spent - elapsed
        if (diff < -band) return "🐢"
        if (diff > band) return "🔥"
        return "🎯"
    }

    fun periodSuffix(
        end: Instant?,
        start: Instant? = null,
        spentPercent: Double? = null,
        style: ResetDetailStyle = ResetDetailStyle.CALENDAR,
    ): String {
        val countdown = resetCountdown(end) ?: return ""
        val elapsed = periodElapsedPercent(start, end)
        val bits = mutableListOf<String>()
        if (elapsed != null) bits += "🕐 ${elapsed.toInt()}%"
        bits += countdown
        if (countdown != "now") resetDetail(end, style)?.let { bits += it }
        val body = "(${bits.joinToString(", ")})"
        val emoji = paceEmoji(spentPercent, elapsed)
        return if (emoji.isEmpty()) "  $body" else "  $body $emoji"
    }

    // ---- Title assembly (matches Alfred `title` field) ----------------------------------------

    fun titleLine(label: String, percent: Double?, suffix: String): String =
        "$label  ${bar(percent)}  ${percentString(percent)}$suffix"

    /**
     * The period suffix without its wrapping parentheses, for layouts that give it a line of its
     * own rather than appending it to the title.
     */
    fun standaloneSuffix(suffix: String): String {
        var text = suffix.trim()
        val close = text.indexOf(')')
        if (!text.startsWith("(") || close < 0) return text
        text = text.removeRange(close, close + 1).substring(1)
        return text.trim()
    }

    // ---- Watch compact presentation ------------------------------------------------------------

    /**
     * Watch row meter — same width as the phone and Alfred, so a row reads the same everywhere.
     */
    fun compactBar(percent: Double?): String = bar(percent, 10)

    fun watchRowLine(row: OverviewRow): String =
        "${row.id.shortLabel}  ${compactBar(row.percentUsed)}  ${percentString(row.percentUsed)}"

    fun watchComplicationPercent(value: Double?): String {
        if (value == null) return "—"
        if (value >= 9.95) return fixed(value, 0) + "%"
        return fixed(value, 1) + "%"
    }

    // ---- Date parsing (Cursor billing / Claude resets) -----------------------------------------

    fun parseResetDate(raw: JsonElement?, epoch: Long? = null): Instant? {
        if (epoch != null) return Instant.ofEpochSecond(epoch)
        if (raw == null) return null
        AeyeJson.double(raw)?.let { ms -> return instantFromSeconds(ms / 1000.0) }
        val str = AeyeJson.string(raw) ?: return null
        val trimmed = str.trim()
        if (trimmed.isNotEmpty() && trimmed.all { it.isDigit() }) {
            trimmed.toDoubleOrNull()?.let { return instantFromSeconds(it / 1000.0) }
        }
        val iso = trimmed.replace("Z", "+00:00")
        return try {
            OffsetDateTime.parse(iso, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant()
        } catch (_: Exception) {
            null
        }
    }

    // ---- Helpers ------------------------------------------------------------------------------

    /** Swift's `round()` / `.rounded()`: halves go away from zero (Kotlin's `round` goes to even). */
    fun roundHalfAway(value: Double): Double =
        if (value >= 0) floor(value + 0.5) else -floor(-value + 0.5)

    /**
     * `String(format: "%.Nf")`: always a `.` decimal point, and the exact binary value rounded
     * half-to-even as C's printf does (Java's `%f` rounds the decimal form half-up instead).
     */
    fun fixed(value: Double, digits: Int): String {
        if (!value.isFinite()) return if (value.isNaN()) "nan" else if (value > 0) "inf" else "-inf"
        return BigDecimal(value).setScale(digits, RoundingMode.HALF_EVEN).toPlainString()
    }

    internal fun secondsBetween(from: Instant, to: Instant): Double {
        val duration = Duration.between(from, to)
        return duration.seconds + duration.nano / 1_000_000_000.0
    }

    internal fun instantFromSeconds(seconds: Double): Instant {
        val whole = floor(seconds)
        return Instant.ofEpochSecond(whole.toLong(), ((seconds - whole) * 1_000_000_000).toLong())
    }
}
