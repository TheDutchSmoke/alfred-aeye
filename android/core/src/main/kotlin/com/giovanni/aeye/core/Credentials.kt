package com.giovanni.aeye.core

import java.time.Instant

/** The Keychain's role: a small, device-only secret store. */
interface SecretStore {
    fun load(key: String): String?

    /** Throws [AeyeError] when the value could not be stored. */
    fun save(key: String, value: String)

    fun delete(key: String)
}

/** Same account names as the iOS Keychain items. */
object SecretKeys {
    const val CURSOR_SESSION_TOKEN = "aeye.cursor.session"
    const val CLAUDE_OAUTH_TOKEN = "aeye.claude.oauth"
    const val CLAUDE_REFRESH_TOKEN = "aeye.claude.refresh"
    const val CLAUDE_TOKEN_EXPIRY = "aeye.claude.expiry"
}

class CredentialsStore(private val secrets: SecretStore) {
    val cursorSessionToken: String? get() = secrets.load(SecretKeys.CURSOR_SESSION_TOKEN)

    val claudeOAuthToken: String? get() = secrets.load(SecretKeys.CLAUDE_OAUTH_TOKEN)

    fun saveCursorSessionToken(token: String) {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) {
            secrets.delete(SecretKeys.CURSOR_SESSION_TOKEN)
        } else {
            secrets.save(SecretKeys.CURSOR_SESSION_TOKEN, trimmed)
        }
    }

    val claudeRefreshToken: String?
        get() = secrets.load(SecretKeys.CLAUDE_REFRESH_TOKEN)?.takeIf { it.isNotBlank() }

    val claudeTokenExpiry: Instant?
        get() {
            val seconds = secrets.load(SecretKeys.CLAUDE_TOKEN_EXPIRY)?.toDoubleOrNull() ?: return null
            return AeyeFormatting.instantFromSeconds(seconds)
        }

    /** Pasting a token clears any refresh token alongside it — the two are only valid as a pair. */
    fun saveClaudeOAuthToken(token: String) {
        val trimmed = token.trim()
        secrets.delete(SecretKeys.CLAUDE_REFRESH_TOKEN)
        secrets.delete(SecretKeys.CLAUDE_TOKEN_EXPIRY)
        if (trimmed.isEmpty()) {
            secrets.delete(SecretKeys.CLAUDE_OAUTH_TOKEN)
        } else {
            secrets.save(SecretKeys.CLAUDE_OAUTH_TOKEN, trimmed)
        }
    }

    fun saveClaudeTokens(tokens: ClaudeOAuth.Tokens) {
        secrets.save(SecretKeys.CLAUDE_OAUTH_TOKEN, tokens.accessToken.trim())
        val refresh = tokens.refreshToken
        if (!refresh.isNullOrEmpty()) {
            secrets.save(SecretKeys.CLAUDE_REFRESH_TOKEN, refresh)
        } else {
            secrets.delete(SecretKeys.CLAUDE_REFRESH_TOKEN)
        }
        val expiresAt = tokens.expiresAt
        if (expiresAt != null) {
            val seconds = expiresAt.epochSecond + expiresAt.nano / 1_000_000_000.0
            secrets.save(SecretKeys.CLAUDE_TOKEN_EXPIRY, seconds.toString())
        } else {
            secrets.delete(SecretKeys.CLAUDE_TOKEN_EXPIRY)
        }
    }

    val hasCursor: Boolean get() = !cursorSessionToken.isNullOrBlank()

    val hasClaude: Boolean get() = !claudeOAuthToken.isNullOrBlank()
}

/** An in-memory [SecretStore], for tests and previews. */
class MemorySecretStore : SecretStore {
    private val values = mutableMapOf<String, String>()
    override fun load(key: String): String? = synchronized(values) { values[key] }
    override fun save(key: String, value: String) {
        synchronized(values) { values[key] = value }
    }
    override fun delete(key: String) {
        synchronized(values) { values.remove(key) }
    }
}
