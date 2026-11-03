package com.giovanni.aeye

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.giovanni.aeye.core.AeyeService
import com.giovanni.aeye.core.AeyeSnapshot
import com.giovanni.aeye.core.ClaudeOAuth
import com.giovanni.aeye.core.CredentialsStore
import com.giovanni.aeye.core.HttpTransport
import com.giovanni.aeye.core.MockSnapshot
import com.giovanni.aeye.core.RowVisibility
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * `AppModel.swift`: the one observable model the phone UI, the widget and the watch bridge share.
 * Owned by [AeyeApplication], so a watch's refresh request that wakes the app in the background
 * finds the same model the UI would.
 */
class AppModel(
    private val store: SnapshotStore,
    val credentials: CredentialsStore,
    /** The network, shared with the sign-in sheets. */
    val transport: HttpTransport,
    val sampleData: SampleDataMode,
    private val scope: CoroutineScope,
    /** Reload the widget and push to the watch — `WidgetCenter` + `WatchBridge.send` on iOS. */
    private val publish: (AeyeSnapshot) -> Unit,
) {
    var snapshot by mutableStateOf(store.load() ?: AeyeSnapshot.empty)
        private set
    var visibility by mutableStateOf(store.loadVisibility())
        private set
    var isRefreshing by mutableStateOf(false)
        private set
    var lastError by mutableStateOf<String?>(null)
        private set

    // Real credential state either way, so Settings never claims a sign-in that did not happen
    // just because the sample is on.
    var cursorConfigured by mutableStateOf(credentials.hasCursor)
        private set
    var claudeConfigured by mutableStateOf(credentials.hasClaude)
        private set

    /** Showing `MockSnapshot` instead of live numbers — see [SampleDataMode]. */
    var isSampleData by mutableStateOf(sampleData.isEnabled)
        private set

    private val service = AeyeService(credentials, transport) { built ->
        store.save(built)
        store.saveVisibility(built.visibility)
    }

    val needsSetup: Boolean get() = !isSampleData && !cursorConfigured && !claudeConfigured

    init {
        if (isSampleData) applySampleData()
    }

    /** Called once the debug screenshot extras have been read, before the first frame. */
    fun applyLaunchArguments() {
        isSampleData = sampleData.isEnabled
        if (isSampleData) applySampleData()
    }

    suspend fun refresh(force: Boolean = false) {
        if (isSampleData) {
            // Re-seed so the period countdowns advance with the clock.
            applySampleData()
            return
        }
        if (needsSetup) {
            snapshot = AeyeSnapshot.empty
            lastError = null
            return
        }

        isRefreshing = true
        lastError = null
        try {
            val result = service.fetchOverview(visibility, forceRefresh = force)
            snapshot = result
            if (lastError == null) {
                result.rows.firstOrNull { it.isError }?.let { lastError = it.subtitle }
            }
            publish(result)
        } finally {
            isRefreshing = false
        }
    }

    fun refreshInBackground(force: Boolean = false) {
        scope.launch { refresh(force) }
    }

    fun saveCursorToken(token: String) {
        credentials.saveCursorSessionToken(token)
        cursorConfigured = credentials.hasCursor
        // Signing in means you want your own numbers, not the demo. The caller refreshes, so
        // don't queue a second fetch.
        setSampleData(false, thenRefresh = false)
    }

    fun clearCursorToken() {
        credentials.saveCursorSessionToken("")
        cursorConfigured = credentials.hasCursor
    }

    fun saveClaudeToken(token: String) {
        credentials.saveClaudeOAuthToken(token)
        claudeConfigured = credentials.hasClaude
        setSampleData(false, thenRefresh = false)
    }

    fun saveClaudeTokens(tokens: ClaudeOAuth.Tokens) {
        credentials.saveClaudeTokens(tokens)
        claudeConfigured = credentials.hasClaude
        setSampleData(false, thenRefresh = false)
    }

    fun clearClaudeToken() {
        credentials.saveClaudeOAuthToken("")
        claudeConfigured = credentials.hasClaude
    }

    /** Turns the demo on or off, keeping the widget and the watch in step. */
    fun setSampleData(enabled: Boolean, thenRefresh: Boolean = true) {
        if (enabled == isSampleData) return
        sampleData.isEnabled = enabled
        isSampleData = enabled

        if (!enabled) {
            // Clear the sample immediately; a real refresh may have nothing to replace it with
            // (no credentials), and stale fake numbers must not linger on the Home Screen or the
            // wrist.
            snapshot = AeyeSnapshot.empty
            lastError = null
            store.save(AeyeSnapshot.empty)
            publish(AeyeSnapshot.empty)
            if (thenRefresh) refreshInBackground(force = true)
            return
        }
        applySampleData()
    }

    private fun applySampleData() {
        val sample = MockSnapshot.sample(
            visibility = visibility,
            markAsSample = !sampleData.isForcedByLaunchArgument,
        )
        snapshot = sample
        lastError = null
        store.save(sample)
        publish(sample)
    }

    fun updateVisibility(visibility: RowVisibility) {
        this.visibility = visibility
        store.saveVisibility(visibility)
        refreshInBackground(force = true)
    }
}
