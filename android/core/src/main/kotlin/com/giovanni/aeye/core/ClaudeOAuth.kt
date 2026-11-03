package com.giovanni.aeye.core

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64

/**
 * Claude Code's public OAuth client.
 *
 * Aeye needs the same `sk-ant-oat01-…` access token the Mac side reads out of the
 * `Claude Code-credentials` Keychain item — `api.anthropic.com/api/oauth/usage` accepts nothing
 * else. A claude.ai browser session is a different credential entirely.
 *
 * Constants below match the shipped CLI (`claude` 2.1.x), as on iOS.
 */
object ClaudeOAuth {
    const val CLIENT_ID = "9d1c250a-e61b-44d9-88ed-5944d1962f5e"

    /**
     * Subscription (claude.ai) authorize page. Console/API accounts would use
     * `https://platform.claude.com/oauth/authorize` instead.
     */
    const val AUTHORIZE_URL = "https://claude.com/cai/oauth/authorize"
    const val TOKEN_URL = "https://platform.claude.com/v1/oauth/token"

    /**
     * The only redirect URI this client registers. It renders `code#state` on screen for the user
     * to copy, which is why sign-in ends with a paste rather than a hand-back to the app.
     */
    const val REDIRECT_URI = "https://platform.claude.com/oauth/code/callback"

    const val SCOPES = "user:inference user:profile"

    /** Refresh this far ahead of the stated expiry. */
    const val REFRESH_LEEWAY_SECONDS: Long = 120

    /** One authorization attempt: the PKCE secrets plus the URL to open. */
    data class Attempt(val verifier: String, val state: String, val url: String)

    data class Tokens(
        val accessToken: String,
        val refreshToken: String? = null,
        val expiresAt: Instant? = null,
    )

    fun begin(): Attempt {
        val verifier = Pkce.randomUrlSafeString()
        val state = Pkce.randomUrlSafeString()
        val challenge = Pkce.challenge(verifier)
        val url = buildUrl(
            AUTHORIZE_URL,
            listOf(
                "code" to "true",
                "client_id" to CLIENT_ID,
                "response_type" to "code",
                "redirect_uri" to REDIRECT_URI,
                "scope" to SCOPES,
                "code_challenge" to challenge,
                "code_challenge_method" to "S256",
                "state" to state,
            ),
        )
        return Attempt(verifier, state, url)
    }

    /**
     * Exchanges what the user pasted back from the callback page. The page shows `code#state`; a
     * bare code (or a full callback URL) is accepted too.
     */
    suspend fun exchange(pasted: String, attempt: Attempt, transport: HttpTransport): Tokens {
        val (code, returnedState) = parse(pasted)
        if (returnedState != null && returnedState != attempt.state) {
            throw ClaudeOAuthError.StateMismatch()
        }
        return post(
            mapOf(
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to REDIRECT_URI,
                "client_id" to CLIENT_ID,
                "code_verifier" to attempt.verifier,
                "state" to attempt.state,
            ),
            transport,
        )
    }

    suspend fun refresh(refreshToken: String, transport: HttpTransport): Tokens {
        val tokens = post(
            mapOf(
                "grant_type" to "refresh_token",
                "refresh_token" to refreshToken,
                "client_id" to CLIENT_ID,
            ),
            transport,
        )
        // Some responses omit a rotated refresh token; keep the one we have.
        return if (tokens.refreshToken == null) tokens.copy(refreshToken = refreshToken) else tokens
    }

    /**
     * The stored access token, refreshed first if it is expired or about to be.
     *
     * Pasted tokens have no refresh token, so they are returned as-is until they 401.
     */
    suspend fun validAccessToken(
        credentials: CredentialsStore,
        transport: HttpTransport,
        forceRefresh: Boolean = false,
    ): String {
        val stored = credentials.claudeOAuthToken
        if (stored.isNullOrEmpty()) throw ClaudeApiError.MissingToken()
        val refreshToken = credentials.claudeRefreshToken ?: return stored

        val expired = credentials.claudeTokenExpiry?.let {
            !AeyeEnvironment.now().plusSeconds(REFRESH_LEEWAY_SECONDS).isBefore(it)
        } ?: false
        if (!forceRefresh && !expired) return stored

        val tokens = refresh(refreshToken, transport)
        credentials.saveClaudeTokens(tokens)
        return tokens.accessToken
    }

    // ---- Parsing --------------------------------------------------------------------------------

    fun parse(pasted: String): Pair<String, String?> {
        val raw = pasted.trim()
        if (raw.isEmpty()) throw ClaudeOAuthError.EmptyCode()

        // Accept the whole callback URL as well as the displayed `code#state`.
        if (raw.lowercase().startsWith("http")) {
            val items = queryItems(raw)
            if (items != null) {
                val code = items.firstOrNull { it.first == "code" }?.second ?: throw ClaudeOAuthError.EmptyCode()
                return code to items.firstOrNull { it.first == "state" }?.second
            }
        }

        val hash = raw.indexOf('#')
        if (hash >= 0) {
            val state = raw.substring(hash + 1)
            return raw.substring(0, hash) to state.ifEmpty { null }
        }
        return raw to null
    }

    /** `URLComponents(string:)?.queryItems` — null when the string has no query. */
    private fun queryItems(url: String): List<Pair<String, String?>>? {
        val query = try {
            URI(url).rawQuery
        } catch (_: Exception) {
            null
        } ?: return null
        return query.split('&').map { part ->
            val eq = part.indexOf('=')
            if (eq < 0) decode(part) to null
            else decode(part.substring(0, eq)) to decode(part.substring(eq + 1))
        }
    }

    private fun decode(value: String): String =
        try {
            java.net.URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
        } catch (_: Exception) {
            value
        }

    // ---- Transport ------------------------------------------------------------------------------

    private suspend fun post(body: Map<String, String>, transport: HttpTransport): Tokens {
        val json = buildJsonObject { body.forEach { (k, v) -> put(k, JsonPrimitive(v)) } }
        val response = transport.send(
            HttpRequest(
                url = TOKEN_URL,
                method = "POST",
                headers = mapOf(
                    "Content-Type" to "application/json",
                    "Accept" to "application/json",
                ),
                body = json.toString().toByteArray(Charsets.UTF_8),
            )
        )
        val payload = AeyeJson.parseObject(response.body) ?: throw ClaudeApiError.InvalidResponse()
        if (!response.isSuccess) {
            // OAuth errors come back flat (`error_description`); Anthropic's own gateway errors
            // (rate limits, outages) come back nested under `error`.
            val nested = AeyeJson.obj(payload["error"])
            val detail = AeyeJson.string(payload["error_description"])
                ?: AeyeJson.string(nested?.get("message"))
                ?: AeyeJson.string(payload["error"])
            throw ClaudeOAuthError.Server(response.status, detail)
        }
        val access = AeyeJson.string(payload["access_token"])
        if (access.isNullOrEmpty()) throw ClaudeApiError.InvalidResponse()
        val expiresAt = AeyeJson.double(payload["expires_in"])?.let {
            AeyeEnvironment.now().plusMillis((it * 1000).toLong())
        }
        return Tokens(
            accessToken = access,
            refreshToken = AeyeJson.string(payload["refresh_token"]),
            expiresAt = expiresAt,
        )
    }
}

sealed class ClaudeOAuthError(message: String) : AeyeError(message) {
    class EmptyCode : ClaudeOAuthError("No authorization code found in what was pasted.")
    class StateMismatch : ClaudeOAuthError("That code came from a different sign-in attempt. Start again.")
    class Server(val status: Int, val detail: String?) : ClaudeOAuthError(
        if (!detail.isNullOrEmpty()) "Claude sign-in failed ($status): $detail"
        else "Claude sign-in failed (HTTP $status)."
    )
}

/** PKCE helpers shared by the Claude and Cursor sign-in flows. */
internal object Pkce {
    private val random = SecureRandom()

    fun randomBytes(count: Int): ByteArray = ByteArray(count).also { random.nextBytes(it) }

    fun randomUrlSafeString(): String = base64Url(randomBytes(32))

    fun challenge(verifier: String): String =
        base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.UTF_8)))

    fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
