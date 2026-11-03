# Publishing Aeye for Android to Google Play

**Status (16 October 2026): ready to upload.** Signed bundles of **1.0.1** for the phone
(versionCode 2) and Wear OS (versionCode 10002), store listing, graphics, phone and watch
screenshots, and the Android privacy page (live since 16 October 2026). Everything
left is in the Play Console, which only Giovanni can do. The iOS app (1.0.1) has been on the App
Store since its 2 October resubmission was approved.

## What's ready

| Asset | Path |
|---|---|
| Phone bundle (upload this) | `app/build/outputs/bundle/release/app-release.aab` (~4.9 MB) |
| Wear OS bundle (upload this) | `wear/build/outputs/bundle/release/wear-release.aab` (~3.8 MB) |
| Release APKs (sideload / testing) | `app/build/outputs/apk/release/app-release.apk`, `wear/build/outputs/apk/release/wear-release.apk` |
| Store listing copy + release notes | `play/STORE_LISTING.md` |
| Store icon 512×512 / feature graphic 1024×500 | `play/icon-512.png`, `play/feature-graphic-1024x500.png` |
| Phone screenshots (4, 1080×2160) | `play/screenshots/` |
| Wear OS screenshots (3, 454×454) | `play/screenshots-wear/` |
| Privacy policy | `docs/privacy.html` → `main:docs/android/privacy.html` — **live** (16 October 2026, `80bd7de`) |
| Upload keystore (gitignored — back it up!) | `keystore/upload-keystore.jks` + `keystore.properties` |

`python3 play/tools/check_listing.py` checks the copy and images against Play's limits (and keeps
third-party names out of the name and short description). `play/tools/make_graphics.py` redraws
the feature graphic; `play/tools/capture_screenshots.sh <serial>` retakes the phone screenshots.

### ⚠️ Keystore
`keystore/upload-keystore.jks` (alias `upload`, RSA 4096, valid to 2054) and its generated
password in `keystore.properties` are the **upload key** for both the phone and the watch bundle.
Both files are gitignored and exist only on this Mac — copy them into Bitwarden before the first
upload. With Play App Signing an upload key can be reset if lost, but that takes a support request
and days.

Upload-key certificate SHA-256:
`7D:B0:30:3E:76:98:43:B0:C9:18:F6:BE:7E:4F:91:AB:A1:AF:B5:8A:3F:9E:80:AA:86:12:7A:0F:A9:C6:06:D5`

The phone and watch apps must reach users signed with the **same** key, or the Data Layer will not
connect them. Play App Signing takes care of that as long as both bundles go into this one app.

### Rebuilding
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
cd android
./gradlew :core:test :app:bundleRelease :wear:bundleRelease
```
Bump `versionCode` (and usually `versionName`) in `app/build.gradle.kts` and `wear/build.gradle.kts`
for every upload. Version codes must be unique across the phone and the watch: the watch's is
offset by 10 000 (`10_002` for 1.0.1 (2)). Release builds use R8 — test a release APK after
dependency upgrades (the WorkManager keep rules in `app/proguard-rules.pro` exist because R8 once
broke the widget).

## Decide before uploading

1. **Application ID `studio.cimento.aeye`.** Set on 16 October 2026 to match the Cimento Studio
   account's other apps (`studio.cimento.whowaswhen`, `…inrange`, `…cantusfirmus`). It is
   permanent once the first bundle is uploaded. (The code's namespace stays `com.giovanni.aeye`;
   the iOS bundle id is unaffected.)
2. **Phone ↔ watch sync has not been run end to end.** Every surface was checked on emulators,
   and the watch's out-of-range path, but not the round trip through the Data Layer (the emulator
   pairing needs a Google account and the Wear OS companion app). An internal-testing release
   (step 3) on a real phone and watch is the cheapest way to close that before production.
3. **Third-party service access.** As on iOS (runbook §0.2), Aeye reads undocumented Cursor and
   Anthropic endpoints and signs in with Claude Code's own OAuth client. Play's review has not
   asked about it for this kind of app before, but if it does, the reply is the same: the app
   reads only the signed-in user's own usage, with their own credentials, and stores nothing off
   the device.

## Play Console steps (Giovanni)

1. **Account** — the **Cimento Studio** organization account, so the 14-day closed test with
   ≥ 12 testers that new *personal* accounts need does not apply.
2. **Create app** — name *Aeye — Usage Meter*, default language English (United States), App,
   Free. Accept the declarations.
3. **Internal testing first (recommended)** — Test and release ▸ Testing ▸ Internal testing ▸
   Create new release ▸ upload `app-release.aab`, keep **Play App Signing** with a
   Google-generated key. Add yourself as a tester, install from the opt-in link on a phone paired
   with a Wear OS watch, and check: phone refresh reaches the watch list, complication and tile;
   Refresh on the watch with the phone app closed; Refresh with the phone out of range, then
   delivered on reconnect. (Uninstall any sideloaded copy first — its signature differs.)
4. **Wear OS** — Test and release ▸ Advanced settings ▸ **Form factors** ▸ Add form factor ▸
   **Wear OS**; agree to the Wear OS review policy. Then release the watch bundle: in the track
   you are releasing to, pick the **Wear OS** form factor (or the "Wear OS only" track the console
   offers) and upload `wear-release.aab`. The watch app declares
   `com.google.android.wearable.standalone=false` — it needs the phone app, and the listing says so.
5. **Privacy policy — done.** Live at
   https://giovannicoppola.github.io/alfred-aeye/android/privacy.html (GitHub Pages serves
   `main:/docs`; published 16 October 2026). To change it, edit `docs/privacy.html` here and copy
   it to `docs/android/privacy.html` on `main`.
6. **Store listing** — paste from `play/STORE_LISTING.md`; upload the icon, the feature graphic,
   the four phone screenshots, and the three Wear OS screenshots (in the Wear OS section that
   appears after step 4). Category *Tools*; contact email `cimento.studio.info@gmail.com`.
7. **App content — done (16 October 2026)**, all ten declarations saved as below.
   - *Privacy policy:* `https://giovannicoppola.github.io/alfred-aeye/android/privacy.html`
   - *Sign in details* (formerly App access): **Yes, restricted** — live numbers need the user's
     own Cursor or Anthropic account. One entry, "Sample data (no account needed)", no username or
     password, the "full access" box ticked, Google's partner-device testing toggle left on. The
     field allows 500 characters; entered:
     > No account is needed to review Aeye. It shows the usage of the user's own Cursor and Claude
     > accounts; to see it fully populated, tap "See it with sample data" on the first screen, or
     > open Settings (gear, top right) > Sample data > "Use sample data". Every row then shows
     > representative numbers, labelled as a sample, and the same sample appears in the Home Screen
     > widget and on a paired Wear OS watch (app, tile, complications).
   - *Ads:* no. *Advertising ID:* no (neither APK declares `AD_ID`; check with
     `aapt2 dump permissions` after adding a dependency).
   - *Content rating (IARC):* All Other App Types, contact `cimento.studio.info@gmail.com`; every
     question No (no ratings-relevant content, user interaction, online content, age-restricted
     products, location sharing, purchases, rewards; not a browser, not news/education) →
     Everyone (ESRB), L / all ages (ClassInd); the other territories follow from the same answers.
   - *Target audience:* 18 and over — a developer tool; nothing is directed at children, and
     younger age groups bring in policies with no benefit here.
   - *Data safety:* **No data collected, no data shared.** No account, analytics, ads or server.
     Network use: the user's own credentials go only to Cursor and Anthropic, to read that
     user's usage (HTTPS). The phone sends the usage snapshot to the user's own watch through the
     Wearable Data Layer; when Google relays it, it is end-to-end encrypted ("All data transferred
     through Google Cloud is end-to-end encrypted" — developer.android.com, Data Layer overview),
     which Play does not count as collection. Encrypted in transit: yes. Account deletion: not
     applicable — there is no Aeye account. This matches the App Store's *Data Not Collected*.
   - *Government apps:* no. *Financial features:* none. *Health apps:* none.
8. **Pricing & countries** — Free, all countries. Production ▸ Create release ▸ add both bundles
   from the library (phone to the phone track, watch to the Wear OS track), release name
   `1.0.1 (2)`, release notes from `play/STORE_LISTING.md`. Publishing overview ▸ **Send changes
   for review**. The Wear OS review is separate and shows its own state (Pending / Approved / Not
   approved) under the Wear OS form factor.

## Review history

| Date | Version | Outcome |
|---|---|---|
| — | 1.0.1 (2) / (10002) | Not yet submitted. |
