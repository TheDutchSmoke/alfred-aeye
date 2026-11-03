package com.giovanni.aeye.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.util.UUID
import kotlin.math.min
import kotlin.math.pow

/**
 * Cursor's browser login, the flow `cursor-agent` uses for `cursor login`.
 *
 * The app opens `loginDeepControl` in a real browser and polls `/auth/poll` until the user
 * finishes; the poll returns the same session JWT the IDE keeps in the `cursor-access-token`
 * Keychain item, which is what the dashboard cookie is built from. Nothing to paste, and SSO works
 * because the sign-in happens outside the app.
 *
 * Constants match `cursor-agent` 2026.07.01, as on iOS.
 */
object CursorAuth {
    const val WEBSITE_BASE = "https://cursor.com"
    const val API_BASE = "https://api2.cursor.sh"

    /** The CLI polls for ~25 minutes; a phone sheet does not need that long. */
    const val MAX_ATTEMPTS = 90
    const val BASE_POLL_INTERVAL = 1.0
    const val MAX_POLL_INTERVAL = 10.0
    const val MAX_CONSECUTIVE_FAILURES = 3

    data class Attempt(val uuid: String, val verifier: String, val url: String)

    fun begin(): Attempt {
        val verifier = Pkce.base64Url(Pkce.randomBytes(32))
        val challenge = Pkce.challenge(verifier)
        // `UUID().uuidString` is upper case.
        val uuid = UUID.randomUUID().toString().uppercase()
        val url = buildUrl(
            "$WEBSITE_BASE/loginDeepControl",
            listOf(
                "challenge" to challenge,
                "uuid" to uuid,
                "mode" to "login",
                "redirectTarget" to "cli",
            ),
        )
        return Attempt(uuid, verifier, url)
    }

    /**
     * Waits for the browser half to finish and returns the session JWT.
     *
     * `404` means "not authorized yet" — the normal answer for most of this loop, not an error.
     */
    suspend fun awaitToken(
        attempt: Attempt,
        transport: HttpTransport,
        sleep: suspend (Long) -> Unit = { delay(it) },
    ): String {
        val request = HttpRequest(
            url = buildUrl("$API_BASE/auth/poll", listOf("uuid" to attempt.uuid, "verifier" to attempt.verifier)),
            headers = mapOf("Content-Type" to "application/json"),
        )
        var consecutiveFailures = 0

        for (attemptIndex in 0 until MAX_ATTEMPTS) {
            val response = try {
                transport.send(request)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                consecutiveFailures += 1
                if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) throw e
                sleep(backOffMillis(attemptIndex))
                continue
            }

            if (response.status == 404) {
                consecutiveFailures = 0
                sleep(backOffMillis(attemptIndex))
                continue
            }
            if (!response.isSuccess) {
                consecutiveFailures += 1
                if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    throw CursorAuthError.Server(response.status)
                }
                sleep(backOffMillis(attemptIndex))
                continue
            }

            val json = AeyeJson.parseObject(response.body)
            val token = AeyeJson.string(json?.get("accessToken"))
            if (token.isNullOrEmpty()) throw CursorAuthError.InvalidResponse()
            return token
        }

        throw CursorAuthError.TimedOut()
    }

    internal fun backOffMillis(attemptIndex: Int): Long {
        val delay = min(BASE_POLL_INTERVAL * 1.2.pow(attemptIndex), MAX_POLL_INTERVAL)
        return (delay * 1000).toLong()
    }
}

sealed class CursorAuthError(message: String) : AeyeError(message) {
    class InvalidResponse : CursorAuthError("Cursor returned a sign-in response Aeye could not read.")
    class Server(val status: Int) : CursorAuthError("Cursor sign-in failed (HTTP $status).")
    class TimedOut : CursorAuthError("Timed out waiting for Cursor sign-in. Tap to try again.")
}
