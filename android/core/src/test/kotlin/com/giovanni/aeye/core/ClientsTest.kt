package com.giovanni.aeye.core

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.Base64

/** Answers requests by URL prefix, in order, and records what was asked. */
class FakeTransport : HttpTransport {
    private val routes = mutableListOf<Pair<String, (HttpRequest) -> HttpResponse>>()
    val requests = mutableListOf<HttpRequest>()

    fun on(prefix: String, status: Int = 200, body: String) {
        routes += prefix to { _ -> HttpResponse(status, body.toByteArray()) }
    }

    fun onRequest(prefix: String, handler: (HttpRequest) -> HttpResponse) {
        routes += prefix to handler
    }

    override suspend fun send(request: HttpRequest): HttpResponse {
        requests += request
        val route = routes.lastOrNull { request.url.startsWith(it.first) }
            ?: return HttpResponse(599, "no route for ${request.url}".toByteArray())
        return route.second(request)
    }

    fun count(prefix: String) = requests.count { it.url.startsWith(prefix) }
}

private fun obj(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

class ClaudeClientTest : PinnedEnvironment() {
    @Test
    fun parsesTheLimitsArray() {
        val data = ClaudeClient.parse(
            obj(
                """{"plan":"max","limits":[
                {"kind":"session","utilization":42.04,"resets_at":"2026-09-10T19:59:59.6Z"},
                {"kind":"weekly_all","utilization":18,"resets_at":"2026-09-16T14:00:00Z"}]}"""
            )
        )
        assertEquals("max", data.plan)
        assertEquals(42.0, data.fiveHour.usedPercentage)
        // Rounded to the minute so a reset a hair before 8pm still reads 8pm.
        assertEquals(Instant.parse("2026-09-10T20:00:00Z"), data.fiveHour.resetsAt)
        assertEquals(18.0, data.sevenDay.usedPercentage)
        assertEquals("experimental", data.confidence)
        assertFalse(data.sessionActive)
    }

    @Test
    fun fallsBackToRateLimitsAndTopLevelWindows() {
        val nested = ClaudeClient.parse(obj("""{"rate_limits":{"five_hour":{"used_percentage":7.5},"seven_day":{"percent":101}}}"""))
        assertEquals(7.5, nested.fiveHour.usedPercentage)
        assertEquals(100.0, nested.sevenDay.usedPercentage)
        assertEquals("unknown", nested.plan)

        val flat = ClaudeClient.parse(obj("""{"five_hour":{"utilization":3},"seven_day":{"utilization":250}}"""))
        assertEquals(3.0, flat.fiveHour.usedPercentage)
        assertNull(flat.sevenDay.usedPercentage)
    }

    @Test
    fun aLimitsArrayWithANonObjectIsIgnored() {
        val data = ClaudeClient.parse(obj("""{"limits":[1,{"kind":"session","utilization":5}],"five_hour":{"utilization":9}}"""))
        assertEquals(9.0, data.fiveHour.usedPercentage)
    }

    @Test
    fun anExpiredWindowHasNoPercentage() {
        val data = ClaudeClient.parse(obj("""{"five_hour":{"utilization":80,"resets_at_epoch":${now.epochSecond - 60}}}"""))
        assertNull(data.fiveHour.usedPercentage)
        assertNotNull(data.fiveHour.resetsAt)
    }

    @Test
    fun utilizationOfOneIsOnePercentButBooleansAreRejected() {
        assertEquals(1.0, ClaudeClient.utilizationToPercent(JsonPrimitive(1)))
        assertEquals(0.0, ClaudeClient.utilizationToPercent(JsonPrimitive(0)))
        assertNull(ClaudeClient.utilizationToPercent(JsonPrimitive(true)))
        assertNull(ClaudeClient.utilizationToPercent(JsonPrimitive(-1)))
        assertEquals(12.5, ClaudeClient.utilizationToPercent(JsonPrimitive("12.5")))
    }

    @Test
    fun fetchSendsTheOAuthHeaders() = runTest {
        val transport = FakeTransport()
        transport.on(ClaudeClient.USAGE_URL, body = """{"five_hour":{"utilization":1}}""")
        ClaudeClient("  sk-ant-oat01-x \n", transport).fetchUsage()
        val headers = transport.requests.single().headers
        assertEquals("Bearer sk-ant-oat01-x", headers["Authorization"])
        assertEquals("oauth-2025-04-20", headers["anthropic-beta"])
    }

    @Test
    fun httpErrorsSayWhatToDo() = runTest {
        val transport = FakeTransport()
        transport.on(ClaudeClient.USAGE_URL, status = 401, body = "{}")
        val error = assertThrows<ClaudeApiError.Http> { ClaudeClient("t", transport).fetchUsage() }
        assertEquals("Claude HTTP 401 — OAuth token expired. Paste a new token in Settings.", error.message)
    }
}

class ClaudeOAuthTest : PinnedEnvironment() {
    @Test
    fun beginBuildsAPkceUrl() {
        val attempt = ClaudeOAuth.begin()
        assertTrue(attempt.url.startsWith("https://claude.com/cai/oauth/authorize?code=true&client_id=9d1c250a"))
        assertTrue(attempt.url.contains("redirect_uri=https://platform.claude.com/oauth/code/callback"))
        assertTrue(attempt.url.contains("scope=user:inference%20user:profile"))
        assertTrue(attempt.url.contains("code_challenge_method=S256"))
        assertTrue(attempt.url.endsWith("state=${attempt.state}"))
        assertEquals(43, attempt.verifier.length)
    }

    @Test
    fun parsesEveryPasteShape() {
        assertEquals("abc" to "xyz", ClaudeOAuth.parse("  abc#xyz \n"))
        assertEquals("abc" to null, ClaudeOAuth.parse("abc"))
        assertEquals("abc" to null, ClaudeOAuth.parse("abc#"))
        assertEquals(
            "a+b" to "s1",
            ClaudeOAuth.parse("https://platform.claude.com/oauth/code/callback?code=a+b&state=s1"),
        )
        assertThrows<ClaudeOAuthError.EmptyCode> { ClaudeOAuth.parse("   ") }
        assertThrows<ClaudeOAuthError.EmptyCode> { ClaudeOAuth.parse("https://x.test/cb?state=1") }
    }

    @Test
    fun exchangeRejectsAStrangersState() = runTest {
        val attempt = ClaudeOAuth.begin()
        assertThrows<ClaudeOAuthError.StateMismatch> {
            ClaudeOAuth.exchange("code#other", attempt, FakeTransport())
        }
    }

    @Test
    fun exchangeStoresTokensWithExpiry() = runTest {
        val transport = FakeTransport()
        transport.on(ClaudeOAuth.TOKEN_URL, body = """{"access_token":"A","refresh_token":"R","expires_in":3600}""")
        val attempt = ClaudeOAuth.begin()
        val tokens = ClaudeOAuth.exchange("code#${attempt.state}", attempt, transport)
        assertEquals(ClaudeOAuth.Tokens("A", "R", now.plusSeconds(3600)), tokens)
        val body = Json.parseToJsonElement(String(transport.requests.single().body!!)).jsonObject
        assertEquals(JsonPrimitive("authorization_code"), body["grant_type"])
        assertEquals(JsonPrimitive(attempt.verifier), body["code_verifier"])
    }

    @Test
    fun serverErrorsCarryTheirDetail() = runTest {
        val transport = FakeTransport()
        transport.on(ClaudeOAuth.TOKEN_URL, status = 400, body = """{"error":"invalid_grant","error_description":"Code expired"}""")
        val flat = assertThrows<ClaudeOAuthError.Server> { ClaudeOAuth.refresh("r", transport) }
        assertEquals("Claude sign-in failed (400): Code expired", flat.message)

        transport.on(ClaudeOAuth.TOKEN_URL, status = 429, body = """{"error":{"type":"rate_limit","message":"Slow down"}}""")
        val nested = assertThrows<ClaudeOAuthError.Server> { ClaudeOAuth.refresh("r", transport) }
        assertEquals("Claude sign-in failed (429): Slow down", nested.message)

        transport.on(ClaudeOAuth.TOKEN_URL, status = 500, body = "{}")
        assertEquals("Claude sign-in failed (HTTP 500).", assertThrows<ClaudeOAuthError.Server> { ClaudeOAuth.refresh("r", transport) }.message)
    }

    @Test
    fun validAccessTokenRefreshesNearExpiryAndKeepsTheRefreshToken() = runTest {
        val credentials = CredentialsStore(MemorySecretStore())
        credentials.saveClaudeTokens(ClaudeOAuth.Tokens("old", "R", now.plusSeconds(60)))
        val transport = FakeTransport()
        transport.on(ClaudeOAuth.TOKEN_URL, body = """{"access_token":"new","expires_in":7200}""")

        assertEquals("new", ClaudeOAuth.validAccessToken(credentials, transport))
        assertEquals("R", credentials.claudeRefreshToken)
        assertEquals(now.plusSeconds(7200), credentials.claudeTokenExpiry)
        // Fresh now: no second refresh.
        assertEquals("new", ClaudeOAuth.validAccessToken(credentials, transport))
        assertEquals(1, transport.count(ClaudeOAuth.TOKEN_URL))
    }

    @Test
    fun pastedTokensAreUsedAsIs() = runTest {
        val credentials = CredentialsStore(MemorySecretStore())
        credentials.saveClaudeTokens(ClaudeOAuth.Tokens("old", "R", now))
        credentials.saveClaudeOAuthToken(" pasted ")
        assertNull(credentials.claudeRefreshToken)
        assertNull(credentials.claudeTokenExpiry)
        assertEquals("pasted", ClaudeOAuth.validAccessToken(credentials, FakeTransport(), forceRefresh = true))
        assertThrows<ClaudeApiError.MissingToken> {
            ClaudeOAuth.validAccessToken(CredentialsStore(MemorySecretStore()), FakeTransport())
        }
    }
}

class CursorTest : PinnedEnvironment() {
    private fun jwt(sub: String): String {
        val enc = Base64.getUrlEncoder().withoutPadding()
        return enc.encodeToString("""{"alg":"none"}""".toByteArray()) + "." +
            enc.encodeToString("""{"sub":"$sub"}""".toByteArray()) + ".sig"
    }

    @Test
    fun normalizesTheCookieFromABareJwt() {
        val token = jwt("auth0|user_123")
        assertEquals("user_123::$token", CursorClient.normalizeCookieValue("  $token\n"))
        assertEquals("u::t", CursorClient.normalizeCookieValue("u%3A%3At"))
        assertEquals("not-a-jwt", CursorClient.normalizeCookieValue("not-a-jwt"))
    }

    @Test
    fun fetchUsageReadsPlanUsageAndFallsBackToTheSummary() = runTest {
        val transport = FakeTransport()
        transport.on("https://cursor.com/api/auth/me", body = """{"email":"me@x.test"}""")
        transport.on("https://cursor.com/api/dashboard/get-current-period-usage", status = 500, body = "")
        transport.on(
            "https://cursor.com/api/usage-summary",
            body = """{"individualUsage":{"plan":{"autoPercentUsed":13,"apiPercentUsed":"4","used":260,"limit":2000}},
                "billingCycleStart":"2026-08-21T18:00:00Z","billingCycleEnd":"${now.plusSeconds(11 * 86400).toEpochMilli()}"}""",
        )
        val data = CursorClient("u::t", transport).fetchUsage()
        assertEquals("me@x.test", data.email)
        assertEquals(13.0, data.autoPercentUsed)
        assertEquals(4.0, data.apiPercentUsed)
        assertEquals(260.0, data.totalSpendCents)
        assertEquals(Instant.parse("2026-08-21T18:00:00Z"), data.billingStart)
        assertEquals("WorkosCursorSessionToken=u%3A%3At", transport.requests.first().headers["Cookie"])
        val post = transport.requests[1]
        assertEquals("POST", post.method)
        assertEquals("https://cursor.com", post.headers["Origin"])
        assertEquals("{}", String(post.body!!))
    }

    @Test
    fun grokBotIsNullWithoutAGrant() = runTest {
        val transport = FakeTransport()
        transport.on("https://cursor.com/api/dashboard/get-sand-usage-status", body = """{"hasNonZeroIncludedLimit":false}""")
        assertNull(CursorClient("u::t", transport).fetchGrokBotUsage())
        transport.on(
            "https://cursor.com/api/dashboard/get-sand-usage-status",
            body = """{"hasNonZeroIncludedLimit":true,"usagePercent":1,"hasAvailableUsage":false,"cursorPlanName":"Pro"}""",
        )
        val grok = CursorClient("u::t", transport).fetchGrokBotUsage()!!
        assertEquals(1.0, grok.usagePercent)
        assertEquals(false, grok.hasAvailableUsage)
        assertEquals("Pro", grok.cursorPlanName)
    }

    @Test
    fun authBeginsWithAPkceChallengeAndAnUppercaseUuid() {
        val attempt = CursorAuth.begin()
        assertTrue(attempt.url.startsWith("https://cursor.com/loginDeepControl?challenge="))
        assertTrue(attempt.url.contains("&uuid=${attempt.uuid}&mode=login&redirectTarget=cli"))
        assertEquals(attempt.uuid.uppercase(), attempt.uuid)
    }

    @Test
    fun pollWaitsThrough404sThenReturnsTheToken() = runTest {
        val transport = FakeTransport()
        var calls = 0
        transport.onRequest(CursorAuth.API_BASE) {
            calls += 1
            if (calls < 3) HttpResponse(404, ByteArray(0)) else HttpResponse(200, """{"accessToken":"jwt"}""".toByteArray())
        }
        val sleeps = mutableListOf<Long>()
        assertEquals("jwt", CursorAuth.awaitToken(CursorAuth.begin(), transport) { sleeps += it })
        assertEquals(listOf(1000L, 1200L), sleeps)
    }

    @Test
    fun pollGivesUpAfterThreeServerErrorsInARow() = runTest {
        val transport = FakeTransport()
        transport.on(CursorAuth.API_BASE, status = 500, body = "")
        val error = assertThrows<CursorAuthError.Server> { CursorAuth.awaitToken(CursorAuth.begin(), transport) {} }
        assertEquals("Cursor sign-in failed (HTTP 500).", error.message)
        assertEquals(3, transport.requests.size)
    }

    @Test
    fun pollTimesOut() = runTest {
        val transport = FakeTransport()
        transport.on(CursorAuth.API_BASE, status = 404, body = "")
        assertThrows<CursorAuthError.TimedOut> { CursorAuth.awaitToken(CursorAuth.begin(), transport) {} }
        assertEquals(CursorAuth.MAX_ATTEMPTS, transport.requests.size)
        assertEquals(10_000L, CursorAuth.backOffMillis(40))
    }
}
