package com.giovanni.aeye.core

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.time.Instant

sealed class ClaudeApiError(message: String) : AeyeError(message) {
    class MissingToken : ClaudeApiError(
        "Claude OAuth token not configured. Paste your Claude Code OAuth token in Settings."
    )

    class Http(val status: Int) : ClaudeApiError(
        if (status == 401 || status == 403) "Claude HTTP $status — OAuth token expired. Paste a new token in Settings."
        else "Claude HTTP $status"
    )

    class InvalidResponse : ClaudeApiError("Invalid Claude response")
}

data class ClaudeLimitWindow(
    val usedPercentage: Double? = null,
    val resetsAt: Instant? = null,
)

data class ClaudeUsageData(
    val plan: String,
    val confidence: String,
    val fiveHour: ClaudeLimitWindow,
    val sevenDay: ClaudeLimitWindow,
    val sessionActive: Boolean,
    val sessionCostUSD: Double?,
    val statusLabel: String?,
)

class ClaudeClient(oauthToken: String, private val transport: HttpTransport) {
    private val oauthToken = oauthToken.trim()

    suspend fun fetchUsage(): ClaudeUsageData {
        val response = transport.send(
            HttpRequest(
                url = USAGE_URL,
                headers = mapOf(
                    "Authorization" to "Bearer $oauthToken",
                    "anthropic-beta" to "oauth-2025-04-20",
                    "User-Agent" to "claude-code/aeye-android",
                    "Content-Type" to "application/json",
                ),
            )
        )
        if (!response.isSuccess) throw ClaudeApiError.Http(response.status)
        val json = AeyeJson.parseObject(response.body) ?: throw ClaudeApiError.InvalidResponse()
        return parse(json)
    }

    companion object {
        const val USAGE_URL = "https://api.anthropic.com/api/oauth/usage"

        fun parse(payload: JsonObject, now: Instant = AeyeEnvironment.now()): ClaudeUsageData {
            val fromLimits = parseLimitsArray(payload["limits"], now)
            val rateLimits = AeyeJson.obj(payload["rate_limits"]) ?: payload

            val five = fromLimits?.first ?: parseWindow(rateLimits["five_hour"], now)
            val seven = fromLimits?.second ?: parseWindow(rateLimits["seven_day"], now)

            return ClaudeUsageData(
                plan = AeyeJson.string(payload["plan"]) ?: "unknown",
                confidence = "experimental",
                fiveHour = five,
                sevenDay = seven,
                sessionActive = false,
                sessionCostUSD = null,
                statusLabel = null,
            )
        }

        private fun parseLimitsArray(raw: JsonElement?, now: Instant): Pair<ClaudeLimitWindow, ClaudeLimitWindow>? {
            val items = AeyeJson.objectArray(raw)?.takeIf { it.isNotEmpty() } ?: return null
            var five: ClaudeLimitWindow? = null
            var seven: ClaudeLimitWindow? = null
            for (item in items) {
                val kind = AeyeJson.string(item["kind"]) ?: continue
                val window = parseWindow(item, now)
                if (kind == "session") five = window
                if (kind == "weekly_all" || kind == "weekly") seven = window
            }
            if (five == null && seven == null) return null
            return (five ?: ClaudeLimitWindow()) to (seven ?: ClaudeLimitWindow())
        }

        private fun parseWindow(raw: JsonElement?, now: Instant): ClaudeLimitWindow {
            val dict = AeyeJson.obj(raw) ?: return ClaudeLimitWindow()
            val utilization = dict["utilization"] ?: dict["used_percentage"] ?: dict["percent"]
            var pct = utilizationToPercent(utilization)
            // Rounded to the minute: the API jitters by ~1s either side
            // (e.g. 23:59:59.6 instead of 00:00:00), which would show 7pm for an 8pm reset.
            val reset = AeyeFormatting.parseResetDate(
                raw = dict["resets_at"],
                epoch = AeyeJson.long(dict["resets_at_epoch"]),
            )?.let { roundToMinute(it) }
            if (reset != null && !now.isBefore(reset)) pct = null
            return ClaudeLimitWindow(usedPercentage = pct, resetsAt = reset)
        }

        /**
         * As `_utilization_to_pct` in the Python monitor: a percentage in [0, 100], 100–101
         * clamped, booleans rejected. Only real JSON booleans are rejected, so a utilization of
         * exactly 1 is 1 %.
         */
        internal fun utilizationToPercent(value: JsonElement?): Double? {
            if (value == null || AeyeJson.isBoolean(value)) return null
            val num = AeyeJson.double(value) ?: return null
            if (!num.isFinite() || num < 0) return null
            if (num > 100) return if (num <= 101) 100.0 else null
            return AeyeFormatting.roundHalfAway(num * 10) / 10
        }

        private fun roundToMinute(instant: Instant): Instant {
            val seconds = instant.epochSecond + instant.nano / 1_000_000_000.0
            return Instant.ofEpochSecond(AeyeFormatting.roundHalfAway(seconds / 60).toLong() * 60)
        }
    }
}
