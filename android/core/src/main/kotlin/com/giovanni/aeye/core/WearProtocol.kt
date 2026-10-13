package com.giovanni.aeye.core

/**
 * The phone ↔ watch contract over the Wear OS Data Layer — the `WatchBridge` keys on iOS.
 *
 * The watch never holds Cursor or Claude credentials. The phone publishes each snapshot as a data
 * item (WatchConnectivity's application context), answers a watch's refresh request with the fresh
 * snapshot (`sendMessage` with a reply), and treats a refresh-request data item as the queued
 * request a watch leaves when the phone is out of reach (`transferUserInfo`).
 */
object WearProtocol {
    /** Data item holding the latest snapshot JSON under [SNAPSHOT_KEY]. */
    const val SNAPSHOT_PATH = "/aeye/snapshot"
    const val SNAPSHOT_KEY = "aeyeSnapshotJSON"

    /** Message path for a refresh request; the phone's reply is the snapshot JSON (UTF-8). */
    const val REFRESH_PATH = "/aeye/refresh"

    /** Data item a watch writes when the phone is unreachable; delivered on the next connection. */
    const val REFRESH_REQUEST_PATH = "/aeye/refresh-request"
    const val REFRESH_REQUEST_KEY = "aeyeRefreshRequest"

    /** Capability the phone app advertises, so the watch can find it among connected nodes. */
    const val PHONE_CAPABILITY = "aeye_phone"
}
