pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "aeye-android"

include(":core") // pure Kotlin: models, formatting, Cursor/Claude clients + sign-in, service (port of ios/Shared)
include(":app") // Compose phone app + Home Screen widget + Wear Data Layer sender
include(":wear") // Wear OS app + complications + tile (port of ios/AeyeWatch + AeyeWatchWidgets)
