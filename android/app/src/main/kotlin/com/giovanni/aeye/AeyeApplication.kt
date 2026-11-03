package com.giovanni.aeye

import android.app.Application
import com.giovanni.aeye.core.AeyeSnapshot
import com.giovanni.aeye.core.CredentialsStore
import com.giovanni.aeye.core.UrlConnectionTransport
import com.giovanni.aeye.wear.PhoneWearBridge
import com.giovanni.aeye.widget.AeyeWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/** Owns the one [AppModel], as `@State private var model` at the App level does on iOS. */
class AeyeApplication : Application() {
    lateinit var model: AppModel
        private set

    /**
     * Model work outlives the screen that started it, like an unstructured `Task` in SwiftUI: a
     * refresh keeps going if Settings closes mid-fetch.
     */
    val scope: CoroutineScope = MainScope()

    val transport = UrlConnectionTransport()

    override fun onCreate() {
        super.onCreate()
        val store = SnapshotStore(this)
        val bridge = PhoneWearBridge(this)
        model = AppModel(
            store = store,
            credentials = CredentialsStore(KeystoreSecretStore(this)),
            transport = transport,
            sampleData = SampleDataMode(store),
            scope = scope,
            publish = { snapshot -> publish(snapshot, bridge) },
        )
    }

    private fun publish(snapshot: AeyeSnapshot, bridge: PhoneWearBridge) {
        scope.launch { AeyeWidget.reload(this@AeyeApplication) }
        bridge.send(snapshot)
    }
}
