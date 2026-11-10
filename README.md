# Aeye 🦉
Keep an 👁️ on your **Cursor**, **Claude** (incl. Fable), and **Grok Bot** usage — right from [Alfred](https://www.alfredapp.com/).

<div align="center">
  <img src="src/icon.png" width="128" alt="Aeye icon">
</div>

<a href="https://github.com/giovannicoppola/alfred-aeye/releases/latest/">
<img alt="Downloads"
src="https://img.shields.io/github/downloads/giovannicoppola/alfred-aeye/total?color=purple&label=Downloads"><br/>
</a>

![Screenshot](docs/screenshot.png)

**On iPhone too** — [Aeye for iOS](ios/README.md) is a free companion app with the same rows, plus
Home Screen widgets and an Apple Watch app.

# Motivation ✅

- Quickly check how much of your AI quotas you’ve used without opening dashboards
- One keyword for Cursor, Claude, and Grok Bot
- Rows that match how the products report usage (Composer/Auto vs other models; Grok Bot weekly; Claude hourly vs weekly vs Fable weekly)

# Features ✨

- **Six-row overview** — Composer/Auto, Other models, Grok Bot, Hourly, Weekly, Fable with green → yellow → red circle meters
- **Cursor** — same split as the spending page; included spend vs limit; days until reset
- **Grok Bot** — Cursor’s separate weekly included allowance (same Cursor session; hidden if the account has no grant)
- **Claude** — hourly (5h) session limit + weekly limit + Fable weekly limit; Claude Code icon on the hourly row
- **Cached overview** (60s) — avoids re-hitting the APIs on every keystroke
- **Configurable** — keyword + checkboxes for which rows to show (all on by default)

# Setting up ⚙️

- macOS
- [Alfred](https://www.alfredapp.com/) 5 with Powerpack
- Python 3 (macOS `/usr/bin/python3` is fine; 3.9+)
- For Cursor rows: signed in to the **Cursor** app on this Mac
  (Cursor 3.x encrypts its session; macOS asks once to let Aeye read the “Cursor Safe Storage” Keychain item — click **Always Allow**)
- For Claude rows: **Claude Code** signed in (OAuth in Keychain, or `~/.claude/.credentials.json`)
- Uncheck unused rows in **Configure Workflow** so you don’t need (or hit) a platform you don’t use

## Installation

1. Download the latest `.alfredworkflow` from [Releases](https://github.com/giovannicoppola/alfred-aeye/releases/latest)
2. Double-click to import into Alfred
3. Optional: open **Configure Workflow** to change the keyword or which rows to show
4. Optional: assign a hotkey

# Usage 📖

1. Type the keyword (default: `aieye`)
2. You’ll see the enabled rows (all six on by default):

### Overview (example)

```
Composer / Auto   🟢⚪⚪⚪⚪⚪⚪⚪⚪⚪  13.0%  (🕐 68%, 11d, Tue Aug 21) 🐢
                  … · $39.85 / $20.00

Other models      ⚪⚪⚪⚪⚪⚪⚪⚪⚪⚪  0.0%  (🕐 68%, 11d, Tue Aug 21) 🐢
                  …

Grok Bot          🟢⚪⚪⚪⚪⚪⚪⚪⚪⚪  1.0%  (🕐 14%, 6d, Fri 12pm) 🐢
                  Pro · Grok Bot Plan · weekly included

Hourly            🟢⚪⚪⚪⚪⚪⚪⚪⚪⚪  6.0%  (🕐 40%, 5h, 11:30pm) 🐢
                  plan=… · experimental

Weekly            ⚪⚪⚪⚪⚪⚪⚪⚪⚪⚪  1.0%  (🕐 14%, 6d, Tue 8pm) 🎯
                  plan=… · experimental

Fable             🟢⚪⚪⚪⚪⚪⚪⚪⚪⚪  5.0%  (🕐 14%, 6d, Tue 8pm) 🎯
                  plan=… · Fable weekly limit · experimental
```

- **Composer / Auto** / **Other models** — Cursor’s spending-page buckets (`autoPercentUsed` / `apiPercentUsed`)
- **Grok Bot** — Cursor’s weekly included Bot allowance (`get-sand-usage-status`); chats draw from this pool, while heavy routines can still count against Other models
- `$39.85 / $20.00` — included **compute spend** this cycle vs plan **included spend** limit (not necessarily what you are billed)
- `(🕐 68%, 11d, …)` — **% of the period elapsed**, then time until reset (`d` / `h` / `m`)
- Pace emoji after the parenthesis — compares % spent vs % elapsed: 🐢 underspending · 🎯 on track · 🔥 overspending (±8pp band)
- **Hourly** — Claude 5-hour session limit (Claude Code icon); reset suffix only while a session is active
- **Weekly** — Claude weekly limit
- **Fable** — Claude’s separate weekly limit for Fable (a `weekly_scoped` row in the usage API); hidden when the plan has no Fable limit

### Actions

| Shortcut | Action |
|----------|--------|
| ⏎ | Copy the selected row |
| ⌘⏎ | Open the service dashboard in the browser |

# Packages used 📦

Bundled with the workflow (no separate `pip install` for end users):

| Package | Role |
|---------|------|
| [cursor-usage](https://github.com/javaisbetterthanpython/cursor-usage) | Cursor cycle limits via your local Cursor session (Keychain / `state.vscdb` → `cursor.com`) |
| [Claude-Code-Usage-Monitor](https://github.com/Maciek-roboblog/Claude-Code-Usage-Monitor) (`claude-monitor`) | Claude limits via local Claude Code data + opt-in Anthropic OAuth usage API |

Claude percentages are labeled **`experimental`** when they come from Anthropic’s OAuth usage API (not Claude Code’s official statusline). **`official`** would require a statusline capture; **`local_estimate`** comes from local JSONL logs.

# Known issues ⚠️

- Claude hourly/weekly/Fable % need Claude Code auth on this machine; without it, those rows may show `n/a`
- Claude % come from Anthropic’s undocumented OAuth usage API (`experimental`); they can change shape without notice
- Cursor uses Cursor’s undocumented dashboard API; it can change without notice
- Grok Bot % come from Cursor’s undocumented `get-sand-usage-status` endpoint; the row is omitted when the account has no included Bot allowance
- Overview cache lasts 60 seconds — force a refresh by waiting or clearing Alfred’s workflow cache

# Acknowledgments 😀

- [cursor-usage](https://github.com/javaisbetterthanpython/cursor-usage)
- [Claude-Code-Usage-Monitor](https://github.com/Maciek-roboblog/Claude-Code-Usage-Monitor)
- Brand marks via [Simple Icons](https://simpleicons.org/) (Cursor) and [Lobe Icons](https://github.com/lobehub/lobe-icons) (Claude, Claude Code)
- Owl workflow icon designed with [Google Gemini](https://gemini.google.com/)
- The [Alfred forum](https://www.alfredforum.com) community

## Security note

Both upstream packages are **vendored** in this repo (no surprise `pip install` at runtime). I reviewed them for unexpected behavior before bundling: they read local session/credentials and talk only to Cursor / Anthropic for usage data — no unrelated telemetry. Details: [SECURITY.md](SECURITY.md). That review is a snapshot in time; re-check if you update the vendored copies.

# Changelog 🧰

- 2026-11-10: version 0.3.0 — Fable weekly limit row (Claude; hidden when the plan has none)
- 2026-11-03: version 0.2.2 — Cursor rows work again with Cursor 3.x, which now encrypts its session token (“Could not find a usable Cursor session token”)
- 2026-09-22: version 0.2.1 — Claude reset times no longer show a minute or an hour early (API timestamps jitter by ~1s)
- 2026-09-12: version 0.2.0 — Grok Bot weekly included-usage row (Cursor session)
- 2026-08-18: version 0.1.0 — initial release (Cursor + Claude, four-row overview, 60s cache)

# Feedback 🧐

Feedback welcome — open an issue here or ping me on the [Alfred](https://www.alfredforum.com) forum.

# Development

For hacking on the workflow locally (symlink into Alfred’s workflows folder):

```bash
./scripts/bootstrap_lib.sh   # build src/lib from vendor/
ln -s "$(pwd)/src" \
  "$HOME/Library/Application Support/Alfred/Alfred.alfredpreferences/workflows/com.giovanni.alfred-aeye"
# or your synced Alfred.alfredpreferences/workflows path
```

Package a release artifact:

```bash
./scripts/build_workflow.sh   # → dist/Aeye.alfredworkflow
```
