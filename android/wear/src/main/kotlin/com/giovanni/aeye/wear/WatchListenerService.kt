package com.giovanni.aeye.wear

import com.giovanni.aeye.core.SnapshotCodec
import com.giovanni.aeye.core.WearProtocol
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

/**
 * Receives the snapshots the phone publishes — `didReceiveApplicationContext` — whether or not the
 * watch app is open, so the complications and the tile stay current.
 */
class WatchListenerService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        val snapshots = events
            .filter { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path == WearProtocol.SNAPSHOT_PATH }
            .mapNotNull { event ->
                DataMapItem.fromDataItem(event.dataItem).dataMap.getString(WearProtocol.SNAPSHOT_KEY)?.let { SnapshotCodec.decode(it) }
            }
        events.release()
        snapshots.lastOrNull()?.let { WatchStore.ingest(this, it) }
    }
}
