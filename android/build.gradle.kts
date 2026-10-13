// Root build script. Plugin versions are declared here and applied per-module.
// Stack: Gradle 9.8 · AGP 9.4 (built-in Kotlin) · Kotlin 2.4.20 — runs on the Android Studio JBR.
plugins {
    kotlin("jvm") version "2.4.20" apply false
    kotlin("plugin.serialization") version "2.4.20" apply false
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}
