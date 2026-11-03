package com.giovanni.aeye.wear

import android.content.ComponentName
import android.content.Context
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.giovanni.aeye.core.AeyeSnapshot
import com.giovanni.aeye.core.SnapshotCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * `WatchSnapshotCache.swift`: the last snapshot the phone sent, shared by the watch app, its
 * complications and its tile. Kept on disk for offline glances, and as a flow so an open app
 * updates the moment a new one lands.
 */
object WatchStore {
    private const val PREFS = "aeye_watch"
    private const val KEY = "aeye.watch.cachedSnapshot"

    private val _snapshot = MutableStateFlow<AeyeSnapshot?>(null)
    val snapshot: StateFlow<AeyeSnapshot?> = _snapshot.asStateFlow()

    @Volatile private var loaded = false

    fun load(context: Context): AeyeSnapshot? {
        if (!loaded) {
            val text = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            _snapshot.value = text?.let { SnapshotCodec.decode(it) }
            loaded = true
        }
        return _snapshot.value
    }

    /** Stores a snapshot from the phone and refreshes everything that shows it. */
    fun ingest(context: Context, snapshot: AeyeSnapshot) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, SnapshotCodec.encode(snapshot))
            .apply()
        loaded = true
        _snapshot.value = snapshot
        reloadSurfaces(context)
    }

    /** `WidgetCenter.reloadTimelines(ofKind:)` for the complications and the tile. */
    fun reloadSurfaces(context: Context) {
        ComplicationDataSourceUpdateRequester
            .create(context, ComponentName(context, AeyeComplicationService::class.java))
            .requestUpdateAll()
        TileService.getUpdater(context).requestUpdate(AeyeTileService::class.java)
    }
}
