package com.giovanni.aeye.wear

import android.content.Context
import android.net.Uri
import com.giovanni.aeye.core.AeyeSnapshot
import com.giovanni.aeye.core.SnapshotCodec
import com.giovanni.aeye.core.WearProtocol
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Watch half of `WatchBridge.swift`. The watch holds no credentials: it shows what the phone
 * publishes, and can ask the phone for a fresh snapshot — the request starts the phone app in the
 * background and the reply carries the new snapshot.
 */
class WatchBridge(context: Context) {
    private val context = context.applicationContext
    private val dataClient = Wearable.getDataClient(this.context)
    private val messageClient = Wearable.getMessageClient(this.context)
    private val capabilityClient = Wearable.getCapabilityClient(this.context)

    sealed class RefreshOutcome {
        /** The phone answered with a fresh snapshot. */
        data class Updated(val snapshot: AeyeSnapshot) : RefreshOutcome()

        /** The phone was out of reach; the request is queued for the next connection. */
        data object Queued : RefreshOutcome()

        data class Failed(val message: String) : RefreshOutcome()
    }

    /**
     * WatchConnectivity's `receivedApplicationContext`: the latest snapshot item the phone left,
     * picked up when the app starts in case the listener service missed it.
     */
    suspend fun syncLatest() {
        try {
            val uri = Uri.Builder().scheme("wear").path(WearProtocol.SNAPSHOT_PATH).build()
            val items = dataClient.getDataItems(uri).await()
            val newest = items.mapNotNull { item ->
                DataMapItem.fromDataItem(item).dataMap.getString(WearProtocol.SNAPSHOT_KEY)?.let { SnapshotCodec.decode(it) }
            }.maxByOrNull { it.capturedAt }
            items.release()
            val current = WatchStore.load(context)
            if (newest != null && newest != current) {
                WatchStore.ingest(context, newest)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // No phone paired, or no Play services on the watch: keep the cache.
        }
    }

    /** Ask the phone for a fresh overview. Wakes the phone app in the background when reachable. */
    suspend fun requestRefresh(): RefreshOutcome {
        val nodes = try {
            capabilityClient.getCapability(WearProtocol.PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE).await().nodes
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return RefreshOutcome.Failed("Watch link unavailable")
        }

        if (nodes.isEmpty()) {
            // `transferUserInfo`: a data item the phone receives when it is next connected.
            return try {
                val request = PutDataMapRequest.create(WearProtocol.REFRESH_REQUEST_PATH).apply {
                    dataMap.putLong(WearProtocol.REFRESH_REQUEST_KEY, System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()
                dataClient.putDataItem(request).await()
                RefreshOutcome.Queued
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                RefreshOutcome.Failed("Watch link not ready — try again")
            }
        }

        val node = nodes.firstOrNull { it.isNearby } ?: nodes.first()
        val reply = try {
            withTimeoutOrNull(40_000) {
                messageClient.sendRequest(node.id, WearProtocol.REFRESH_PATH, ByteArray(0)).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        val snapshot = reply?.let { SnapshotCodec.decode(it.toString(Charsets.UTF_8)) }
            ?: return RefreshOutcome.Failed("Phone didn't answer")
        WatchStore.ingest(context, snapshot)
        return RefreshOutcome.Updated(snapshot)
    }
}
