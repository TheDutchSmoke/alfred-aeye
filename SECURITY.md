# Security review of bundled packages

Reviewed: 2026-08-12  
Sources:

- `vendor/cursor-usage` ← https://github.com/javaisbetterthanpython/cursor-usage (v0.2.0)
- `vendor/claude-monitor` ← https://github.com/Maciek-roboblog/Claude-Code-Usage-Monitor (v4.0.0)

## Verdict

**Both packages are acceptable to bundle** for a local Alfred workflow. Neither performs unexpected destructive actions. Network use is limited to the vendor APIs needed for usage data; credentials stay on-device except as auth to those APIs.

## cursor-usage

| Area | Finding |
|------|---------|
| Network | Only `https://cursor.com` dashboard endpoints (including `POST /api/dashboard/get-sand-usage-status` for Grok Bot) |
| Credentials | Reads local Cursor session (`CURSOR_SESSION_TOKEN`, macOS Keychain `cursor-access-token`, optional keyring, or `state.vscdb`). Does not write/refresh tokens |
| Encrypted session (Aeye patch, 2026-11-03) | Cursor 3.x stores the `state.vscdb` token as `cursor-safe-storage:v1:…` (Electron `safeStorage`). On macOS the vendored `auth.py` reads the Keychain item `Cursor Safe Storage` (read-only; macOS asks once) and decrypts in-process via CommonCrypto (`ctypes`, no key on argv). The decrypted token is only sent to `cursor.com`, as before |
| Subprocess | macOS: `security find-generic-password` (read-only) |
| Filesystem | Read-only auth sources; optional CSV write only when `--csv` is passed (workflow does not use this) |
| Telemetry | None |
| Dependencies | None (stdlib only) |

## claude-monitor (Claude-Code-Usage-Monitor)

| Area | Finding |
|------|---------|
| Network | Default path is local-only (Claude Code JSONL). Opt-in `--api` calls `https://api.anthropic.com/api/oauth/usage` with a local OAuth token. Aeye enables `--api` for better limit %. |
| Credentials | Reads `CLAUDE_CODE_OAUTH_TOKEN` or `~/.claude/.credentials.json` only when `--api` is used |
| Fable weekly row (Aeye patch, 2026-11-10) | `output/api_usage.py` also reads the Fable `weekly_scoped` row from the same `/api/oauth/usage` reply. No new request, endpoint, or credential |
| Subprocess | WSL discovery on Windows; timezone helpers (`date`, `timedatectl`, etc.) — read-only |
| Filesystem | Reads `~/.claude/projects` JSONL; may write under `~/.claude-monitor/` (state/cache) when features are enabled |
| Telemetry | None observed |
| Dependencies | numpy, pydantic, rich, pyyaml, pytz, wcwidth (vendored into `lib/`) |

## Workflow safeguards

- Does not pass `--csv`, `--warehouse`, or arbitrary shell input from Alfred query text
- Bundled code is vendored under `vendor/` for diff/audit; runtime uses `lib/`
- Rebuild deps with `./scripts/bootstrap_lib.sh` after updating vendors
- Aeye additionally reads the macOS Keychain item `Claude Code-credentials` (read-only)
  so `--api` works when Claude Code stores OAuth there instead of
  `~/.claude/.credentials.json`; the token is only passed to Anthropic’s usage API
  via `CLAUDE_CODE_OAUTH_TOKEN` for that process
