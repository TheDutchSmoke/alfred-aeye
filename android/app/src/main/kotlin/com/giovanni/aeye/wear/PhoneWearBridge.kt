package com.giovanni.aeye.wear

import android.content.Context
import android.util.Log
import com.giovanni.aeye.core.AeyeSnapshot
import com.giovanni.aeye.core.SnapshotCodec
import com.giovanni.aeye.core.WearProtocol
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

/**
 * Phone half of `WatchBridge.swift`: publishes each snapshot to a paired Wear OS watch.
 *
 * The data item is WatchConnectivity's application context — the latest value wins, it is kept
 * while the watch is away, and it is delivered when the watch reconnects. Credentials never leave
 * the phone; only the snapshot does.
 */
class PhoneWearBridge(context: Context) {
    private val dataClient = Wearable.getDataClient(context.applicationContext)

    fun send(snapshot: AeyeSnapshot) {
        val request = PutDataMapRequest.create(WearProtocol.SNAPSHOT_PATH).apply {
            dataMap.putString(WearProtocol.SNAPSHOT_KEY, SnapshotCodec.encode(snapshot))
        }.asPutDataRequest().setUrgent()
        // Best-effort, as on iOS: no watch, or no Play services, just means nobody is listening.
        dataClient.putDataItem(request).addOnFailureListener {
            Log.d(TAG, "No watch to sync with: ${it.message}")
        }
    }

    private companion object {
        const val TAG = "AeyeWear"
    }
}
