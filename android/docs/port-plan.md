# Aeye for Android — port plan

Goal: feature parity and appearance parity with the iOS 1.0.1 app in `../../ios`: the same rows,
wording, data rules, sign-in flows and sample mode, on the same surfaces (phone app, Home Screen
widget, watch app, watch complications). An Android phone and an iPhone should show the same
numbers the same way.

## Layout

```
android/
  core/   pure Kotlin (JVM). Method-for-method port of ios/Shared/ minus the platform bits:
          AeyeRowID, RowVisibility, OverviewRow, AeyeSnapshot, AeyeFormatting, AeyeJson,
          ClaudeClient, ClaudeOAuth, CursorClient, CursorAuth, CredentialsStore, AeyeService,
          MockSnapshot, SnapshotCodec. JUnit 5 tests in core/src/test.
  app/    phone: Compose UI wearing the iOS design (ui/ios/ kit, as in hubHub), Glance widget,
          Keystore-backed credentials, Wear Data Layer sender + refresh-request listener.
  wear/   Wear OS app (Compose for Wear OS), complication data source, tile.
```

`:core` sits behind three seams so the service and the sign-in flows are testable on the JVM:

| iOS | Seam in `:core` | Android implementation |
| --- | --- | --- |
| `URLSession` (ephemeral, 20 s / 30 s) | `HttpTransport` | `HttpURLConnection` on `Dispatchers.IO`, caches off |
| Keychain (`AfterFirstUnlockThisDeviceOnly`) | `SecretStore` | AES-GCM key in the Android Keystore; ciphertext in a prefs file excluded from backup and device transfer |
| App Group `UserDefaults` + snapshot file | `KeyValueStore` + snapshot file | `SharedPreferences` + `filesDir/Aeye/aeye-snapshot.json` (the widget runs in-process, so no group is needed) |

## Surface mapping

| iOS | Android |
| --- | --- |
| iPhone app (`ContentView`, `SettingsView` sheet) | Compose activity; Settings as a full-height sheet sliding over the overview |
| `ASWebAuthenticationSession` | Chrome Custom Tabs (shares the browser's cookies, so Google/SSO work) |
| `UIPasteboard` / context-menu Copy | `ClipboardManager`; long-press a row for the Copy menu |
| WidgetKit Medium / Large | Glance app widget, responsive: medium (4×2) and large (4×4) layouts |
| `WidgetCenter.reloadTimelines` | `AeyeWidget().updateAll()` after every refresh |
| watchOS app | Wear OS app, same list, refresh button, swipe-to-refresh equivalent (rotary/scroll + button) |
| WatchConnectivity `applicationContext` push | Data Layer `DataClient` item `/aeye/snapshot` |
| `sendMessage` refresh request with reply | `MessageClient.sendRequest` on `/aeye/refresh` (phone answers with the snapshot) |
| `transferUserInfo` queued request | Data Layer item `/aeye/refresh-request` (delivered on next connection) |
| Watch complications (circular, rectangular, inline, corner) | Complication data source: SHORT_TEXT, LONG_TEXT, RANGED_VALUE |
| Smart Stack widget | Tile (logo, sample chip, first rows) |
| `#if DEBUG --screenshot`, `--screenshot-settings` | Debug-only intent extras `screenshot`, `screenshot_settings` |

## Feature checklist

- [x] Five rows, same order, labels, short labels, providers, dashboard URLs
- [x] Circle meter (10 slots, green ≤50 %, yellow ≤80 %, red), percent `%.1f%%`, `n/a`
- [x] Period suffix `(🕐 elapsed%, countdown, detail) pace`, calendar / weekday / clock styles,
      pace band 8, Claude hourly suffix only when a session is marked active
- [x] Cursor: `/api/auth/me`, `get-current-period-usage` → fallback `usage-summary`, Grok Bot
      `get-sand-usage-status` (hidden when no grant), cookie normalisation from a bare JWT
- [x] Claude: `/api/oauth/usage`, `limits[]` or `rate_limits`, minute-rounded resets, expired
      window → n/a, refresh token ahead of expiry and once more on 401/403
- [x] Error rows (Cursor / Grok Bot / Claude unavailable), shown even when the row is toggled off
- [x] 60 s overview cache, in-flight request coalescing
- [x] Overview: header, sample chip, setup card (Open Settings / See it with sample data), rows
      with split title, meter, suffix line, subtitle, Open dashboard, Copy, footer (error,
      Updated time, widget hint / sample note), refresh button, pull to refresh, gear
- [x] Settings: intro, Cursor and Claude sections (status, sign in / again, remove), visible
      rows, sample data, Advanced paste-a-token disclosure, Save row choices, About (version,
      privacy, support, disclaimer)
- [x] Claude sign-in: step 1 open browser, step 2 paste code (+ Paste from clipboard), Finish,
      state check, errors
- [x] Cursor sign-in: open browser, poll with back-off, status line, cancel stops polling
- [x] Sample mode: persisted, labelled on every surface, turned off by signing in, re-seeded on
      refresh so countdowns advance
- [x] Refresh on foreground
- [x] Widget: 🦉 Aeye header, Sample chip, captured time, rows (compact on medium), empty text
- [x] Watch: header, sample chip, status + relative age, rows (provider, short label, percent,
      meter, compact subtitle), Refresh, explanatory line, auto-refresh when older than 2 min
- [x] Complications + tile follow the first visible row
- [x] Light and dark appearance

## Verification (13 October 2026)

- `:core` — 49 JUnit tests: meter, percent (C `printf` rounding, `.` decimal in every locale),
  money, countdown, reset styles, pace, suffixes, date parsing, Claude `limits[]` / `rate_limits`
  / expired windows, OAuth URL, paste parsing, state check, token refresh, Cursor cookie
  normalisation, period-usage fallback, Grok grant, poll back-off and give-up, the service's row
  assembly, error rows, 401 refresh, 60 s cache, coalescing, and the snapshot JSON (including a
  snapshot written by the iOS encoder).
- Phone (emulator, debug and R8 release): overview with sample data in light and dark, setup
  card, Settings sheet over the overview, Claude sign-in sheet stacked over Settings, Custom Tab
  reaching claude.ai's sign-in, state-mismatch error, long-press Copy (clipboard checked), sample
  mode surviving a relaunch, Home Screen widget placed from the picker.
- Wear OS (round emulator, debug and release): app with the sample, empty state, refresh with no
  phone (queued), tile, short-text and ranged-value complications on a watch face.
- **Not run:** the phone ↔ watch round trip over the Data Layer — pairing emulators needs the
  Wear OS companion app and a Google account. Check on real hardware.

## Appearance

The Android app wears the iOS design on purpose, as hubHub's port does, so both stores and the
README show one product: iOS system palette in light and dark, iOS type scale, inset-grouped
Settings, large title that collapses into the bar, iOS switches, bar buttons and activity
indicator, monospaced row labels and percentages.

The circle meter is drawn rather than typed. Android's emoji font draws 🟢🟡🔴⚪ as flat discs,
while the iPhone's are shaded spheres. The app, widget and watch draw shaded spheres that match the
iPhone's, so the meters look the same. Copy text still uses the emoji, as on iOS.

## Copy that changes on Android

Only where the iOS text names an Apple thing: "Keychain" → "Android Keystore", "Safari" →
"your browser", "iPhone" → "phone", "Apple Watch"/"Watch" → "watch". The privacy policy link
points at an Android page (`docs/privacy.html` here, to be published).

## Known differences

- Emoji in text (🕐 🐢 🔥 🎯 🦉 👁️) render in the system emoji font, not Apple Color Emoji.
- Wear OS Data Layer needs the phone and watch apps to share a package name and signing key;
  both are `com.giovanni.aeye`.
- Glance widgets refresh when the app writes a snapshot, plus at most every 30 minutes from the
  system (iOS asks for 15).
