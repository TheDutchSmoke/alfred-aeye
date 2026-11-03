package com.giovanni.aeye.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.giovanni.aeye.core.MockSnapshot

class WatchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // `--screenshot`, as a debug-only intent extra: the sample, as if synced.
        val forcedSample = BuildConfig.DEBUG && intent.getBooleanExtra("screenshot", false)
        // `--ez seed true` (debug only): store the labelled sample as if the phone had sent it,
        // so the complications and the tile can be checked without a paired phone.
        if (BuildConfig.DEBUG && intent.getBooleanExtra("seed", false)) {
            WatchStore.ingest(applicationContext, MockSnapshot.sample())
        }
        setContent {
            val model = remember { WatchModel(applicationContext, forcedSample) }
            val pushed by WatchStore.snapshot.collectAsState()
            LaunchedEffect(pushed) { pushed?.let { model.received(it) } }

            // `scenePhase == .active` → pull when the cached snapshot is stale.
            val lifecycle = LocalLifecycleOwner.current.lifecycle
            LaunchedEffect(Unit) {
                model.start()
                lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { model.refreshIfStale() }
            }

            WatchContentScreen(model)
        }
    }
}
