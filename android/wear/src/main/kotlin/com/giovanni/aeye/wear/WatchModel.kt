package com.giovanni.aeye.wear

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.giovanni.aeye.core.AeyeSnapshot
import com.giovanni.aeye.core.MockSnapshot
import com.giovanni.aeye.core.OverviewRow
import java.time.Duration
import java.time.Instant
import java.util.Date

/** `WatchModel.swift`. */
class WatchModel(private val context: Context, private val forcedSample: Boolean) {
    private val bridge = WatchBridge(context)

    var snapshot by mutableStateOf(AeyeSnapshot.empty)
        private set
    var statusLine by mutableStateOf("Waiting for phone…")
        private set
    var isRefreshing by mutableStateOf(false)
        private set

    init {
        if (forcedSample) {
            snapshot = MockSnapshot.preview
            statusLine = "Synced from phone"
        } else {
            val cached = WatchStore.load(context)
            if (cached != null) {
                snapshot = cached
                statusLine = "Cached from phone"
            } else {
                statusLine = "Open Aeye on your phone to sync"
            }
        }
    }

    val rows: List<OverviewRow> get() = snapshot.visibleRows

    val hasSnapshot: Boolean get() = snapshot.hasCapture

    /** A snapshot the phone pushed while the app is open — `onSnapshotReceived`. */
    fun received(snap: AeyeSnapshot) {
        if (forcedSample || snap == snapshot) return
        snapshot = snap
        statusLine = "Synced ${time(snap.capturedAt)}"
    }

    suspend fun start() {
        if (!forcedSample) bridge.syncLatest()
    }

    /** Pull automatically when the app comes forward on a snapshot that has gone stale. */
    suspend fun refreshIfStale(maxAgeSeconds: Long = 120) {
        if (hasSnapshot && Duration.between(snapshot.capturedAt, Instant.now()).seconds <= maxAgeSeconds) return
        refresh()
    }

    /** Ask the phone for a fresh overview instead of waiting for it to push one. */
    suspend fun refresh() {
        if (isRefreshing || forcedSample) return
        isRefreshing = true
        statusLine = "Asking phone…"
        try {
            when (val outcome = bridge.requestRefresh()) {
                is WatchBridge.RefreshOutcome.Updated -> {
                    snapshot = outcome.snapshot
                    statusLine = "Updated ${time(outcome.snapshot.capturedAt)}"
                }
                WatchBridge.RefreshOutcome.Queued -> statusLine = "Phone out of reach — will sync when connected"
                is WatchBridge.RefreshOutcome.Failed -> statusLine = outcome.message
            }
        } finally {
            isRefreshing = false
        }
    }

    private fun time(instant: Instant): String = DateFormat.getTimeFormat(context).format(Date.from(instant))
}
