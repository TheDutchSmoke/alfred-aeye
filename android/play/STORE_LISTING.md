# Google Play — Store listing copy (Aeye 1.0.1)

Copy and paste into Play Console ▸ **Grow users ▸ Store presence ▸ Main store listing**. Adapted
from the App Store copy (`../../ios/docs/app-store-metadata.md`); only what names an Apple thing
changes (iPhone → phone, Safari → your browser, Keychain → Android Keystore, Apple Watch → Wear OS
watch). Limits are checked by `tools/check_listing.py`.

**No third-party names in the app name or the short description.** The App Store rejected 1.0.1
(2) on 1 October 2026 (Guideline 4.1(c)) for "Cursor & Claude plan meters" in the subtitle, and
Play's impersonation and metadata policies read the same way. The names stay in the full
description as a factual statement of which accounts it reads, followed by the disclaimer.

## App name  (≤ 30)
```
Aeye — Usage Meter
```

## Short description  (≤ 80)
```
AI coding limits at a glance — on your phone, in a widget, and on your watch.
```

## Full description  (≤ 4000)
```
Aeye keeps an eye on how much of your AI coding plan you have left.

One screen shows every limit that matters: Cursor's Composer/Auto budget, your other-model spend, the weekly Grok Bot allowance, and Claude's 5-hour session and weekly limits. Each row is a circle meter with the percentage used and how long is left in the period, so you can tell at a glance whether you are pacing well or about to run dry mid-task.

It is the Android companion to the Aeye workflow for Alfred on the Mac, and it shows the same rows in the same layout.

HOME SCREEN WIDGET
Add the widget and see the same rows without opening anything: compact at 4×2, with the reset details at 4×4. The widget shows whichever rows you chose in Settings.

WEAR OS
A compact list on the wrist with the same meters and short labels, a tile, and complications for your watch face. Refresh from the watch and it asks the phone to fetch fresh numbers. Out of range, the watch keeps showing the last snapshot along with its age.

SIGN IN ONCE
Sign in to Claude and to Cursor in your browser, using each service's own login flow. Tokens are encrypted with a key held in the Android Keystore and refreshed automatically.

PRIVACY BY CONSTRUCTION
Aeye has no account and no server. Your credentials stay on your phone and are sent only to Cursor and Anthropic, as your own authorization, to read your own usage. Nothing is collected, nothing is uploaded, there is no analytics SDK and no tracking.

LOOK BEFORE YOU SIGN IN
Turn on sample data in Settings to see exactly how Aeye looks — app, widget, and watch — with no account connected. Sample numbers are labelled as such everywhere they appear.

PICK YOUR ROWS
Not everyone pays for everything. Turn off the rows you do not use and the app, the widget, and the watch all follow.

Requires an existing Cursor and/or Anthropic (Claude) account. Aeye is an independent companion app and is not affiliated with, endorsed by, or sponsored by Cursor, Anthropic, or Alfred.
```

## Release notes — 1.0.1  (≤ 500)
```
The first Android release.

• Cursor Composer/Auto, other models, and weekly Grok Bot allowance
• Claude 5-hour session and weekly limits
• Home Screen widget, 4×2 and 4×4
• Wear OS app with on-wrist refresh, a tile, and complications
• Sign in to Claude and Cursor in your browser — tokens stay encrypted on your phone
• Choose which rows to show
• Sample data mode — see the whole app before signing in
```

## Categorisation and contact
- **App or game:** App. **Free.**
- **Category:** Tools *(Play has no Developer Tools category; Productivity is the alternative)*
- **Tags:** pick the closest offered to *Developer tools*, *Widgets*
- **Contact email:** cimento.studio.info@gmail.com *(the Cimento Studio account's, as for its other apps)*
- **Website:** https://github.com/giovannicoppola/alfred-aeye
- **Privacy policy:** https://giovannicoppola.github.io/alfred-aeye/android/privacy.html *(from
  `../docs/privacy.html`; must be live on `main` before it is entered)*

## Graphics

| Asset | Spec | File |
|---|---|---|
| App icon | 512×512 PNG | `icon-512.png` — the iOS icon (`scripts/make_icons.py`) |
| Feature graphic | 1024×500 PNG | `feature-graphic-1024x500.png` (`tools/make_graphics.py`) |
| Phone screenshots | 2–8, ≤ 2:1, no alpha | `screenshots/01-overview` … `04-widget` (1080×2160) |
| Wear OS screenshots | 1–8, 1:1, ≥ 384 px | `screenshots-wear/01-app`, `02-rows`, `03-tile` (454×454) |

Order mirrors the App Store set: overview, overview in dark, Settings, then the 4×4 widget on a
home screen (the App Store shows the widget through its own preview). The overview and Settings
shots use the debug build's screenshot extras, so they carry no "Sample data" chip — the same as
the iOS screenshots; the widget and tile shots show the "Sample" chip, as anyone turning on sample
data would see.
