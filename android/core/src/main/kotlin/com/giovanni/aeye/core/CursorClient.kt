package com.giovanni.aeye.core

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.util.Base64

sealed class CursorApiError(message: String) : AeyeError(message) {
    class MissingSession : CursorApiError(
        "Cursor session token not configured. Paste your WorkosCursorSessionToken in Settings."
    )

    class Http(val status: Int, val body: String) : CursorApiError(
        if (status == 401 || status == 403) "Cursor HTTP $status — session expired. Paste a new token in Settings."
        else "Cursor HTTP $status"
    )

    class InvalidResponse : CursorApiError("Invalid Cursor response")
    class NoLimitsData : CursorApiError("Cycle limits unavailable")
}

data class CursorUsageData(
    val email: String,
    val autoPercentUsed: Double?,
    val apiPercentUsed: Double?,
    val totalSpendCents: Double?,
    val limitCents: Double?,
    val autoMessage: String?,
    val apiMessage: String?,
    val billingStart: Instant?,
    val billingEnd: Instant?,
)

data class GrokBotUsage(
    val usagePercent: Double?,
    val periodStart: Instant?,
    val periodEnd: Instant?,
    val hasAvailableUsage: Boolean?,
    val cursorPlanName: String?,
    val grokPlanLabel: String?,
)

class CursorClient(cookieValue: String, private val transport: HttpTransport) {
    private val cookieValue = cookieValue.trim().replace("%3A%3A", "::")

    suspend fun fetchUsage(): CursorUsageData {
        val me = request("/api/auth/me")
        val email = AeyeJson.string(me["email"]) ?: "signed-in user"

        val payload = fetchPeriodUsage() ?: throw CursorApiError.NoLimitsData()

        val plan = AeyeJson.obj(payload["planUsage"])
            ?: AeyeJson.obj(AeyeJson.obj(payload["individualUsage"])?.get("plan"))
            ?: JsonObject(emptyMap())

        return CursorUsageData(
            email = email,
            autoPercentUsed = AeyeJson.double(plan["autoPercentUsed"]),
            apiPercentUsed = AeyeJson.double(plan["apiPercentUsed"]),
            totalSpendCents = AeyeJson.double(plan["totalSpend"]) ?: AeyeJson.double(plan["used"]),
            limitCents = AeyeJson.double(plan["limit"]),
            autoMessage = AeyeJson.string(payload["autoModelSelectedDisplayMessage"]),
            apiMessage = AeyeJson.string(payload["namedModelSelectedDisplayMessage"]),
            billingStart = AeyeFormatting.parseResetDate(payload["billingCycleStart"]),
            billingEnd = AeyeFormatting.parseResetDate(payload["billingCycleEnd"]),
        )
    }

    /** The current billing period, from the dashboard endpoint or, failing that, the summary. */
    private suspend fun fetchPeriodUsage(): JsonObject? {
        try {
            return request("/api/dashboard/get-current-period-usage", method = "POST", body = true)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
        return try {
            request("/api/usage-summary")
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /** Weekly Grok Bot included usage. Returns `null` when the account has no grant. */
    suspend fun fetchGrokBotUsage(): GrokBotUsage? {
        val payload = request("/api/dashboard/get-sand-usage-status", method = "POST", body = true)
        if (AeyeJson.bool(payload["hasNonZeroIncludedLimit"]) != true) return null
        return GrokBotUsage(
            usagePercent = AeyeJson.double(payload["usagePercent"]),
            periodStart = AeyeFormatting.parseResetDate(payload["currentPeriodStart"]),
            periodEnd = AeyeFormatting.parseResetDate(payload["nextResetTimestampUtc"]),
            hasAvailableUsage = AeyeJson.bool(payload["hasAvailableUsage"]),
            cursorPlanName = AeyeJson.string(payload["cursorPlanName"]),
            grokPlanLabel = AeyeJson.string(payload["grokPlanLabel"]),
        )
    }

    private suspend fun request(path: String, method: String = "GET", body: Boolean = false): JsonObject {
        val headers = buildMap {
            put("Accept", "application/json")
            put("User-Agent", "cursor-usage/aeye-android")
            put("Cookie", "WorkosCursorSessionToken=${cookieValue.replace("::", "%3A%3A")}")
            if (body) {
                put("Content-Type", "application/json")
                put("Origin", "https://cursor.com")
            }
        }
        val response = transport.send(
            HttpRequest(
                url = BASE + path,
                method = method,
                headers = headers,
                body = if (body) "{}".toByteArray() else null,
            )
        )
        if (!response.isSuccess) throw CursorApiError.Http(response.status, response.text)
        return AeyeJson.parseObject(response.body) ?: throw CursorApiError.InvalidResponse()
    }

    companion object {
        const val BASE = "https://cursor.com"

        fun normalizeCookieValue(raw: String): String {
            val trimmed = raw.trim().replace("%3A%3A", "::")
            if (trimmed.contains("::")) return trimmed
            val sub = jwtSub(trimmed) ?: return trimmed
            return "$sub::$trimmed"
        }

        private fun jwtSub(token: String): String? {
            val parts = token.split('.')
            if (parts.size < 2) return null
            return try {
                val bytes = Base64.getUrlDecoder().decode(parts[1].trimEnd('='))
                val json = AeyeJson.parseObject(bytes) ?: return null
                AeyeJson.string(json["sub"])?.split('|')?.last()
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}
