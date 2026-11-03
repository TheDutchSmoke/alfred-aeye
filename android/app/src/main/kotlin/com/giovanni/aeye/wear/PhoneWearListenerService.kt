package com.giovanni.aeye.wear

import com.giovanni.aeye.AeyeApplication
import com.giovanni.aeye.core.SnapshotCodec
import com.giovanni.aeye.core.WearProtocol
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.launch

/**
 * Answers the watch. Play services starts this service even when Aeye is not running, which is
 * how a watch refresh wakes the app in the background on iOS.
 *
 * - A live request (`MessageClient.sendRequest`) is `sendMessage` with a reply handler: refresh,
 *   then reply with the snapshot.
 * - A queued request (a data item the watch left while the phone was away) is
 *   `transferUserInfo`: refresh, and let the published snapshot carry the answer back.
 */
class PhoneWearListenerService : WearableListenerService() {
    private val app get() = application as AeyeApplication

    override fun onRequest(nodeId: String, path: String, request: ByteArray): Task<ByteArray>? {
        if (path != WearProtocol.REFRESH_PATH) return null
        val reply = TaskCompletionSource<ByteArray>()
        app.scope.launch {
            try {
                // `refresh` answers with the sample while sample mode is on, so one path covers
                // both, as on iOS.
                app.model.refresh(force = true)
                reply.setResult(SnapshotCodec.encode(app.model.snapshot).toByteArray(Charsets.UTF_8))
            } catch (e: Exception) {
                reply.setException(e)
            }
        }
        return reply.task
    }

    override fun onDataChanged(events: DataEventBuffer) {
        val queued = events.any {
            it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path == WearProtocol.REFRESH_REQUEST_PATH
        }
        events.release()
        if (queued) app.model.refreshInBackground(force = true)
    }
}
