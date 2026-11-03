package com.giovanni.aeye

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.giovanni.aeye.ui.ContentScreen
import com.giovanni.aeye.ui.ios.IosTheme
import com.giovanni.aeye.ui.ios.SheetController
import com.giovanni.aeye.ui.ios.SheetHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as AeyeApplication
        val model = app.model

        if (BuildConfig.DEBUG && savedInstanceState == null) {
            // `--screenshot` / `--screenshot-settings`, as intent extras:
            //   adb shell am start -n studio.cimento.aeye/com.giovanni.aeye.MainActivity --ez screenshot true
            val settings = intent.getBooleanExtra("screenshot_settings", false)
            if (settings || intent.getBooleanExtra("screenshot", false)) {
                model.sampleData.isForcedByLaunchArgument = true
                model.sampleData.opensSettingsAtLaunch = settings
                model.applyLaunchArguments()
            }
        }

        // `scenePhase == .active` → refresh (the 60-second cache keeps this cheap).
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                model.refreshInBackground()
            }
        })

        setContent {
            val dark = isSystemInDarkTheme()
            val sheets = remember { SheetController() }
            val presenting = sheets.isPresenting
            LaunchedEffect(dark, presenting) {
                // iOS draws its bars over the content. With a sheet up, the status bar sits on
                // the black backdrop, so its icons turn light.
                val status = if (dark || presenting) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                val navigation = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = status, navigationBarStyle = navigation)
            }
            IosTheme(dark) {
                SheetHost(sheets) {
                    ContentScreen(model)
                }
            }
        }
    }
}
