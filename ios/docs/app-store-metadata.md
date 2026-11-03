# Aeye — App Store listing copy

Paste-ready text for App Store Connect. Character limits in parentheses are Apple's.
Process and checklist live in [`app-store-submission.md`](app-store-submission.md).

## Name (30)

```
Aeye — Usage Meter
```

Plain `Aeye` was taken, so the registered name is the fallback (18 characters). The app's own
display name on the Home Screen is still just **Aeye** (`INFOPLIST_KEY_CFBundleDisplayName`), which
is allowed — the listing name and the icon label do not have to match.

## Subtitle (30)

```
AI coding limits at a glance
```

(28 characters.) **No third-party names here.** 1.0.1 (2) was rejected on 1 October 2026 under
Guideline 4.1(c) (Copycats) because the subtitle was "Cursor & Claude plan meters": the name, the
subtitle, and the icon cannot contain another developer's brand or product name without their
approval. The service names stay in the description (as a factual compatibility statement) and in
the keywords.

## Promotional text (170)

Editable without a new build — use it for status notes.

```
Your Cursor and Claude plan limits on the Home Screen, in a widget, and on your wrist. No account, no backend — your tokens never leave your iPhone.
```

## Description (4000)

```
Aeye keeps an eye on how much of your AI coding plan you have left.

One screen shows every limit that matters: Cursor's Composer/Auto budget, your other-model spend, the weekly Grok Bot allowance, and Claude's 5-hour session and weekly limits. Each row is a circle meter with the percentage used and how long is left in the period, so you can tell at a glance whether you are pacing well or about to run dry mid-task.

It is the iPhone companion to the Aeye workflow for Alfred on the Mac, and it shows the same rows in the same layout.

HOME SCREEN WIDGETS
Add a medium or large widget and see the same rows without opening anything. The widget shows whichever rows you chose in Settings.

APPLE WATCH
A compact list on the wrist with five-dot meters and short labels, plus complications for circular, rectangular, inline, and corner slots. Refresh from the Watch and it wakes the iPhone app in the background to fetch fresh numbers. Out of range, the Watch keeps showing the last snapshot along with its age.

SIGN IN ONCE
Sign in to Claude and to Cursor in Safari, using each service's own login flow. Tokens are stored in the iPhone Keychain and refreshed automatically.

PRIVACY BY CONSTRUCTION
Aeye has no account and no server. Your credentials stay in the Keychain on your iPhone and are sent only to Cursor and Anthropic, as your own authorization, to read your own usage. Nothing is collected, nothing is uploaded, there is no analytics SDK and no tracking.

LOOK BEFORE YOU SIGN IN
Turn on sample data in Settings to see exactly how Aeye looks — app, widget, and Watch — with no account connected. Sample numbers are labelled as such everywhere they appear.

PICK YOUR ROWS
Not everyone pays for everything. Turn off the rows you do not use and the app, the widget, and the Watch all follow.

Requires an existing Cursor and/or Anthropic (Claude) account. Aeye is an independent companion app and is not affiliated with, endorsed by, or sponsored by Cursor, Anthropic, or Alfred.
```

## Keywords (100, comma-separated, no spaces)

```
cursor,claude,usage,limits,quota,ai,coding,tokens,widget,developer,monitor,meter,anthropic,rate
```

(95 characters.)

## What's New (4000)

For 1.0.0:

```
First release.

• Cursor Composer/Auto, other models, and weekly Grok Bot allowance
• Claude 5-hour session and weekly limits
• Medium and large Home Screen widgets
• Apple Watch app with on-wrist refresh, plus complications
• Sign in to Claude and Cursor in Safari — tokens stay in the iPhone Keychain
• Choose which rows to show
• Sample data mode — see the whole app before signing in
```

## URLs

| Field | Value |
|---|---|
| Privacy policy | `https://giovannicoppola.github.io/alfred-aeye/ios-landing/privacy.html` |
| Support | `https://github.com/giovannicoppola/alfred-aeye/issues` |
| Marketing | `https://giovannicoppola.github.io/alfred-aeye/ios-landing/` |

## Also set

- **Category** — Developer Tools (primary), Utilities (secondary)
- **Price** — Free
- **Age rating** — 4+
- **Copyright** — `2026 Giovanni Coppola`
- **App Privacy** — "No, we do not collect data from this app"
