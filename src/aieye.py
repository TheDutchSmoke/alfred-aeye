#!/usr/bin/env python3
"""Alfred Script Filter: Cursor + Claude + Grok Bot usage toward plan limits."""

from __future__ import annotations

import json
import os
import subprocess
import sys
import time
import traceback
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional


ROOT = Path(__file__).resolve().parent
LIB = ROOT / "lib"
if str(LIB) not in sys.path:
    sys.path.insert(0, str(LIB))

ICON_CURSOR = {"path": "icons/cursor.png"}
ICON_CLAUDE = {"path": "icons/claude.png"}
ICON_CLAUDE_CODE = {"path": "icons/claude-code.png"}
URL_CURSOR = "https://cursor.com/dashboard"
URL_CLAUDE = "https://claude.ai/settings/usage"

OVERVIEW_CACHE_TTL = 60  # seconds


def _cache_dir() -> Path:
    raw = os.environ.get("alfred_workflow_cache")
    path = Path(raw) if raw else (ROOT / ".cache")
    path.mkdir(parents=True, exist_ok=True)
    return path


def _overview_cache_path() -> Path:
    return _cache_dir() / "overview.json"


def _read_overview_cache() -> Optional[Dict[str, Any]]:
    path = _overview_cache_path()
    try:
        if not path.is_file():
            return None
        age = time.time() - path.stat().st_mtime
        if age > OVERVIEW_CACHE_TTL:
            return None
        payload = json.loads(path.read_text(encoding="utf-8"))
        if isinstance(payload, dict) and isinstance(payload.get("items"), list):
            return payload
    except (OSError, ValueError, TypeError):
        return None
    return None


def _write_overview_cache(payload: Dict[str, Any]) -> None:
    path = _overview_cache_path()
    tmp = path.with_suffix(f".{os.getpid()}.tmp")
    try:
        tmp.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
        os.replace(tmp, path)
    except OSError:
        try:
            tmp.unlink(missing_ok=True)
        except OSError:
            pass


def _bar(pct: Optional[float], width: int = 10) -> str:
    """Circle meter: filled slots tint green→yellow→red toward the limit."""
    palette = []
    for i in range(width):
        edge = (i + 1) / width * 100.0
        if edge <= 50:
            palette.append("🟢")
        elif edge <= 80:
            palette.append("🟡")
        else:
            palette.append("🔴")
    empty = "⚪"
    if pct is None:
        return empty * width
    pct = max(0.0, min(100.0, float(pct)))
    filled = int(round(pct / 100.0 * width))
    filled = max(0, min(width, filled))
    return "".join(palette[i] if i < filled else empty for i in range(width))


def _pct(value: Optional[float]) -> str:
    if value is None:
        return "n/a"
    return f"{float(value):.1f}%"


def _parse_reset_dt(raw: Any = None, *, epoch: Any = None) -> Optional[datetime]:
    if epoch is not None:
        try:
            return datetime.fromtimestamp(int(epoch), tz=timezone.utc)
        except (TypeError, ValueError, OSError, OverflowError):
            pass
    if raw is None:
        return None
    try:
        s = str(raw).strip()
        if s.isdigit():
            return datetime.fromtimestamp(int(s) / 1000, tz=timezone.utc)
        return datetime.fromisoformat(s.replace("Z", "+00:00"))
    except (TypeError, ValueError, OSError):
        return None


def _reset_countdown(dt: Optional[datetime]) -> Optional[str]:
    """Countdown token without parentheses, e.g. '3d', '5h', or '42m'."""
    if dt is None:
        return None
    now = datetime.now(timezone.utc)
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    seconds = (dt.astimezone(timezone.utc) - now).total_seconds()
    if seconds <= 0:
        return "now"
    if seconds < 3600:
        return f"{max(1, int(round(seconds / 60.0)))}m"
    hours = seconds / 3600.0
    if hours < 24:
        return f"{max(1, int(round(hours)))}h"
    return f"{max(1, int(round(seconds / 86400.0)))}d"


def _as_utc(dt: Optional[datetime]) -> Optional[datetime]:
    if dt is None:
        return None
    if dt.tzinfo is None:
        return dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(timezone.utc)


def _period_elapsed_pct(
    start: Optional[datetime], end: Optional[datetime]
) -> Optional[float]:
    """Percent of the billing/session window that has already elapsed (0–100)."""
    start_u = _as_utc(start)
    end_u = _as_utc(end)
    if start_u is None or end_u is None:
        return None
    total = (end_u - start_u).total_seconds()
    if total <= 0:
        return None
    now = datetime.now(timezone.utc)
    done = (now - start_u).total_seconds()
    return round(max(0.0, min(100.0, done / total * 100.0)), 0)


def _pace_emoji(
    spent_pct: Optional[float], elapsed_pct: Optional[float], *, band: float = 8.0
) -> str:
    """Compare % spent vs % elapsed: underspending / on track / overspending."""
    if spent_pct is None or elapsed_pct is None:
        return ""
    diff = float(spent_pct) - float(elapsed_pct)
    if diff < -band:
        return "🐢"  # underspending
    if diff > band:
        return "🔥"  # overspending
    return "🎯"  # on track


def _reset_detail(end: Optional[datetime], style: str) -> Optional[str]:
    """Trailing detail after the countdown: weekday/date, weekday/time, or clock."""
    if end is None:
        return None
    local = _as_utc(end)
    if local is None:
        return None
    local = local.astimezone()
    if style == "calendar":
        return f"{local.strftime('%a %b')} {local.day}"
    if style == "weekday":
        hour = local.strftime("%I").lstrip("0") or "12"
        ampm = local.strftime("%p").lower()
        return f"{local.strftime('%a')} {hour}{ampm}"
    if style == "clock":
        hour = local.strftime("%I").lstrip("0") or "12"
        minute = local.strftime("%M")
        ampm = local.strftime("%p").lower()
        return f"{hour}:{minute}{ampm}"
    return None


def _period_suffix(
    *,
    end: Optional[datetime],
    start: Optional[datetime] = None,
    spent_pct: Optional[float] = None,
    style: str = "calendar",
) -> str:
    """Title suffix: `` (45%, 10d, Sun Aug 23) 🐢`` — elapsed %, countdown, pace."""
    countdown = _reset_countdown(end)
    if countdown is None:
        return ""
    elapsed = _period_elapsed_pct(start, end)
    bits: List[str] = []
    if elapsed is not None:
        bits.append(f"🕐 {elapsed:.0f}%")
    bits.append(countdown)
    if countdown != "now":
        detail = _reset_detail(end, style)
        if detail:
            bits.append(detail)
    body = f"({', '.join(bits)})"
    emoji = _pace_emoji(spent_pct, elapsed)
    if emoji:
        return f"  {body} {emoji}"
    return f"  {body}"


def _money_cents(cents: Optional[float]) -> str:
    if cents is None:
        return "n/a"
    return f"${float(cents) / 100.0:,.2f}"


def _iso_ms(raw: Any) -> Optional[str]:
    dt = _parse_reset_dt(raw)
    if dt is None:
        return None
    return dt.strftime("%Y-%m-%d")


def _item(
    title: str,
    subtitle: str = "",
    arg: str = "",
    valid: bool = True,
    mods: Optional[Dict[str, Any]] = None,
    icon: Optional[Dict[str, str]] = None,
) -> Dict[str, Any]:
    item: Dict[str, Any] = {
        "title": title,
        "subtitle": subtitle,
        "arg": arg or subtitle or title,
        "valid": valid,
        "text": {"copy": arg or subtitle or title, "largetype": title},
    }
    if mods:
        item["mods"] = mods
    if icon:
        item["icon"] = icon
    return item


def _error_item(service: str, message: str, icon: Optional[Dict[str, str]] = None) -> Dict[str, Any]:
    return _item(
        title=f"{service}: unavailable",
        subtitle=message[:200],
        arg=message,
        valid=False,
        icon=icon,
    )


def _cmd_open(url: str, subtitle: str) -> Dict[str, Any]:
    return {
        "cmd": {
            "subtitle": subtitle,
            "arg": url,
            "valid": True,
        }
    }


# ---- Cursor -----------------------------------------------------------------

def _cursor_session() -> Dict[str, Any]:
    from cursor_usage.api import CursorClient
    from cursor_usage.auth import resolve_cookie_value

    cookie = resolve_cookie_value()
    client = CursorClient(cookie)
    me = client.me()
    email = me.get("email") or "signed-in user"
    return {"client": client, "email": email}


def _cursor_period_payload(client: Any) -> Dict[str, Any]:
    from cursor_usage.api import CursorAPIError

    payload = None
    last_err: Optional[Exception] = None
    for fn in (client.current_period_usage, client.usage_summary):
        try:
            payload = fn()
            if isinstance(payload, dict):
                break
        except (CursorAPIError, OSError, ValueError) as exc:
            last_err = exc
            payload = None
    if not isinstance(payload, dict):
        raise RuntimeError(
            f"Cycle limits unavailable: {last_err}" if last_err else "No limits data"
        )
    return payload


def _cursor_fields(data: Dict[str, Any]) -> Dict[str, Any]:
    payload = data["payload"]
    email = data["email"]
    plan = (
        payload.get("planUsage")
        or (payload.get("individualUsage") or {}).get("plan")
        or {}
    )
    auto_pct = plan.get("autoPercentUsed")
    api_pct = plan.get("apiPercentUsed")
    used = plan.get("totalSpend", plan.get("used"))
    limit = plan.get("limit")
    start_dt = _parse_reset_dt(payload.get("billingCycleStart"))
    reset_dt = _parse_reset_dt(payload.get("billingCycleEnd"))
    reset = reset_dt.strftime("%Y-%m-%d") if reset_dt else None
    auto_msg = payload.get("autoModelSelectedDisplayMessage")
    api_msg = payload.get("namedModelSelectedDisplayMessage")

    spend_bits: List[str] = []
    if used is not None and limit is not None:
        spend_bits.append(f"{_money_cents(used)} / {_money_cents(limit)}")
    if reset_dt is None and reset:
        spend_bits.append(f"resets {reset}")

    return {
        "email": email,
        "auto_pct": auto_pct,
        "api_pct": api_pct,
        "auto_msg": auto_msg,
        "api_msg": api_msg,
        "spend_bits": spend_bits,
        "start_dt": start_dt,
        "reset_dt": reset_dt,
    }


def _grok_bot_item(sand: Dict[str, Any], email: str) -> Optional[Dict[str, Any]]:
    """Weekly Grok Bot included-usage row, or None when the account has no grant."""
    if sand.get("hasNonZeroIncludedLimit") is not True:
        return None
    pct = sand.get("usagePercent")
    try:
        pct_f: Optional[float] = float(pct) if pct is not None else None
    except (TypeError, ValueError):
        pct_f = None
    start_dt = _parse_reset_dt(sand.get("currentPeriodStart"))
    reset_dt = _parse_reset_dt(sand.get("nextResetTimestampUtc"))
    suffix = _period_suffix(
        end=reset_dt,
        start=start_dt,
        spent_pct=pct_f,
        style="weekday",
    )
    bits: List[str] = []
    plan = sand.get("cursorPlanName")
    if plan:
        bits.append(str(plan))
    label = sand.get("grokPlanLabel")
    if label:
        bits.append(str(label))
    bits.append("weekly included")
    if sand.get("hasAvailableUsage") is False:
        bits.append("exhausted")
    return _item(
        title=f"Grok Bot  {_bar(pct_f)}  {_pct(pct_f)}{suffix}",
        subtitle=" · ".join(bits) or email,
        arg=f"Grok Bot: {_pct(pct_f)}{suffix}",
        icon=ICON_CURSOR,
        mods=_cmd_open(URL_CURSOR, "Open cursor.com/dashboard"),
    )


def cursor_overview_items(
    *,
    include_auto: bool = True,
    include_other: bool = True,
    include_grok: bool = True,
) -> List[Dict[str, Any]]:
    """Composer/Auto + Other models + Grok Bot weekly included usage."""
    if not include_auto and not include_other and not include_grok:
        return []
    try:
        session = _cursor_session()
    except Exception as exc:
        from cursor_usage.auth import SessionNotFound
        from cursor_usage.api import CursorAPIError

        if isinstance(exc, SessionNotFound):
            return [_error_item("Cursor", str(exc).split("\n")[0], ICON_CURSOR)]
        if isinstance(exc, CursorAPIError):
            return [_error_item("Cursor", f"HTTP {exc.status}", ICON_CURSOR)]
        return [_error_item("Cursor", str(exc), ICON_CURSOR)]

    client = session["client"]
    email = session["email"]
    items: List[Dict[str, Any]] = []

    if include_auto or include_other:
        try:
            fields = _cursor_fields(
                {"email": email, "payload": _cursor_period_payload(client)}
            )
            auto_suffix = _period_suffix(
                end=fields["reset_dt"],
                start=fields["start_dt"],
                spent_pct=fields["auto_pct"],
                style="calendar",
            )
            other_suffix = _period_suffix(
                end=fields["reset_dt"],
                start=fields["start_dt"],
                spent_pct=fields["api_pct"],
                style="calendar",
            )
            auto_sub = " · ".join(
                bit
                for bit in [fields["auto_msg"], *fields["spend_bits"]]
                if bit
            ) or fields["email"]
            other_sub = fields["api_msg"] or f"API / named models · {fields['email']}"
            if include_auto:
                items.append(
                    _item(
                        title=(
                            f"Composer / Auto  {_bar(fields['auto_pct'])}  "
                            f"{_pct(fields['auto_pct'])}{auto_suffix}"
                        ),
                        subtitle=auto_sub,
                        arg=f"Cursor Composer/Auto: {_pct(fields['auto_pct'])}{auto_suffix}",
                        icon=ICON_CURSOR,
                        mods=_cmd_open(URL_CURSOR, "Open cursor.com/dashboard"),
                    )
                )
            if include_other:
                items.append(
                    _item(
                        title=(
                            f"Other models  {_bar(fields['api_pct'])}  "
                            f"{_pct(fields['api_pct'])}{other_suffix}"
                        ),
                        subtitle=other_sub,
                        arg=f"Cursor other models: {_pct(fields['api_pct'])}{other_suffix}",
                        icon=ICON_CURSOR,
                        mods=_cmd_open(URL_CURSOR, "Open cursor.com/dashboard"),
                    )
                )
        except Exception as exc:
            from cursor_usage.api import CursorAPIError

            if isinstance(exc, CursorAPIError):
                items.append(_error_item("Cursor", f"HTTP {exc.status}", ICON_CURSOR))
            else:
                items.append(_error_item("Cursor", str(exc), ICON_CURSOR))

    if include_grok:
        try:
            row = _grok_bot_item(client.sand_usage_status(), email)
            if row:
                items.append(row)
        except Exception as exc:
            from cursor_usage.api import CursorAPIError

            if isinstance(exc, CursorAPIError):
                items.append(_error_item("Grok Bot", f"HTTP {exc.status}", ICON_CURSOR))
            else:
                items.append(_error_item("Grok Bot", str(exc), ICON_CURSOR))
    return items


# ---- Claude -----------------------------------------------------------------

def _token_from_payload(payload: Any) -> Optional[str]:
    if not isinstance(payload, dict):
        return None
    for key in ("accessToken", "access_token", "oauth_access_token"):
        value = payload.get(key)
        if isinstance(value, str) and value:
            return value
    for key in ("claudeAiOauth", "oauth"):
        nested = _token_from_payload(payload.get(key))
        if nested:
            return nested
    return None


def _resolve_claude_oauth_token() -> Optional[str]:
    env = os.environ.get("CLAUDE_CODE_OAUTH_TOKEN")
    if env:
        return env.strip() or None

    cred_path = Path.home() / ".claude" / ".credentials.json"
    try:
        token = _token_from_payload(json.loads(cred_path.read_text()))
        if token:
            return token
    except (OSError, ValueError):
        pass

    if sys.platform == "darwin":
        try:
            out = subprocess.run(
                [
                    "security",
                    "find-generic-password",
                    "-s",
                    "Claude Code-credentials",
                    "-w",
                ],
                capture_output=True,
                text=True,
                timeout=15,
            )
            raw = (out.stdout or "").strip()
            if raw:
                return _token_from_payload(json.loads(raw))
        except (OSError, ValueError, subprocess.SubprocessError):
            pass
    return None


def _claude_reset_dt(window: Dict[str, Any]) -> Optional[datetime]:
    """Reset time rounded to the minute: the API jitters by ~1s either side
    (e.g. 23:59:59.6 instead of 00:00:00), which would show 7pm for an 8pm reset."""
    dt = _parse_reset_dt(window.get("resets_at"), epoch=window.get("resets_at_epoch"))
    if dt is None:
        return None
    return (dt + timedelta(seconds=30)).replace(second=0, microsecond=0)


def _fmt_reset(window: Dict[str, Any], *, with_date: bool = False) -> Optional[str]:
    dt = _claude_reset_dt(window)
    if dt is None:
        return None
    local = dt.astimezone()
    if with_date:
        return local.strftime("%Y-%m-%d %H:%M")
    return local.strftime("%H:%M")


def _run_claude_once() -> Dict[str, Any]:
    env = os.environ.copy()
    env["PYTHONPATH"] = str(LIB) + (
        os.pathsep + env["PYTHONPATH"] if env.get("PYTHONPATH") else ""
    )
    token = _resolve_claude_oauth_token()
    if token and not env.get("CLAUDE_CODE_OAUTH_TOKEN"):
        env["CLAUDE_CODE_OAUTH_TOKEN"] = token

    cmd = [
        sys.executable,
        "-m",
        "claude_monitor",
        "--once",
        "--api",
        "--output",
        "json",
    ]
    proc = subprocess.run(
        cmd,
        capture_output=True,
        text=True,
        timeout=45,
        env=env,
        cwd=str(ROOT),
    )
    stdout = (proc.stdout or "").strip()
    if not stdout:
        err = (proc.stderr or "").strip() or f"exit {proc.returncode}"
        raise RuntimeError(err)
    start = stdout.find("{")
    end = stdout.rfind("}")
    if start < 0 or end < 0:
        raise RuntimeError("No JSON in claude-monitor output")
    return json.loads(stdout[start : end + 1])


def _claude_fields(snap: Dict[str, Any]) -> Dict[str, Any]:
    limits = snap.get("limits") or {}
    five = limits.get("five_hour") or {}
    seven = limits.get("seven_day") or {}
    local = snap.get("local") or {}
    status = snap.get("status") or {}
    plan = snap.get("plan") or "unknown"

    five_pct = five.get("used_percentage")
    seven_pct = seven.get("used_percentage")
    five_conf = five.get("confidence") or snap.get("confidence") or "unknown"
    seven_conf = seven.get("confidence") or five_conf
    five_reset = _fmt_reset(five)
    seven_reset = _fmt_reset(seven, with_date=True)
    five_reset_dt = _claude_reset_dt(five)
    seven_reset_dt = _claude_reset_dt(seven)
    session_active = bool(local.get("is_active"))

    # Window start must match Anthropic's rate-limit bucket (resets_at − duration),
    # not local JSONL session_start — those can diverge and skew % elapsed / pace.
    five_start = (
        _as_utc(five_reset_dt) - timedelta(hours=5) if five_reset_dt is not None else None
    )
    seven_start = (
        _as_utc(seven_reset_dt) - timedelta(days=7) if seven_reset_dt is not None else None
    )

    five_reset_label = (
        _period_suffix(
            end=five_reset_dt,
            start=five_start,
            spent_pct=five_pct,
            style="clock",
        )
        if session_active
        else ""
    )
    seven_reset_label = _period_suffix(
        end=seven_reset_dt,
        start=seven_start,
        spent_pct=seven_pct,
        style="weekday",
    )

    hourly_bits = [f"plan={plan}"]
    if five_conf and five_conf != "unknown":
        hourly_bits.append(str(five_conf))
    if not five_reset_label:
        countdown = _reset_countdown(five_reset_dt)
        if five_reset:
            hourly_bits.append(five_reset)
        if countdown:
            hourly_bits.append(f"({countdown})")
    if status.get("label") and status.get("label") != "ok":
        hourly_bits.append(str(status["label"]))
    cost = local.get("cost_usd")
    if cost:
        hourly_bits.append(f"session ${float(cost):,.2f}")

    weekly_bits = [f"plan={plan}"]
    if seven_conf and seven_conf != "unknown":
        weekly_bits.append(str(seven_conf))
    if not seven_reset_label and seven_reset:
        weekly_bits.append(seven_reset)

    # Separate weekly cap for Fable; the key is absent when the plan has none.
    fable = limits.get("seven_day_fable")
    fable_present = isinstance(fable, dict)
    fable = fable if fable_present else {}
    fable_pct = fable.get("used_percentage")
    fable_conf = fable.get("confidence") or seven_conf
    fable_reset = _fmt_reset(fable, with_date=True)
    fable_reset_dt = _claude_reset_dt(fable)
    fable_start = (
        _as_utc(fable_reset_dt) - timedelta(days=7) if fable_reset_dt is not None else None
    )
    fable_reset_label = _period_suffix(
        end=fable_reset_dt,
        start=fable_start,
        spent_pct=fable_pct,
        style="weekday",
    )
    fable_bits = [f"plan={plan}", "Fable weekly limit"]
    if fable_conf and fable_conf != "unknown":
        fable_bits.append(str(fable_conf))
    if not fable_reset_label and fable_reset:
        fable_bits.append(fable_reset)

    return {
        "five_pct": five_pct,
        "seven_pct": seven_pct,
        "five_conf": five_conf,
        "seven_conf": seven_conf,
        "five_reset": five_reset,
        "seven_reset": seven_reset,
        "five_reset_label": five_reset_label,
        "seven_reset_label": seven_reset_label,
        "session_active": session_active,
        "hourly_bits": hourly_bits,
        "weekly_bits": weekly_bits,
        "fable_present": fable_present,
        "fable_pct": fable_pct,
        "fable_reset_label": fable_reset_label,
        "fable_bits": fable_bits,
    }


def claude_overview_items(
    *,
    include_hourly: bool = True,
    include_weekly: bool = True,
    include_fable: bool = True,
) -> List[Dict[str, Any]]:
    """Hourly (5h) + weekly + Fable weekly limits — Claude Code icon on the hourly row."""
    if not include_hourly and not include_weekly and not include_fable:
        return []
    try:
        fields = _claude_fields(_run_claude_once())
    except Exception as exc:
        return [_error_item("Claude", str(exc), ICON_CLAUDE)]

    items: List[Dict[str, Any]] = []
    if include_hourly:
        items.append(
            _item(
                title=(
                    f"Hourly  {_bar(fields['five_pct'])}  {_pct(fields['five_pct'])}"
                    f"{fields['five_reset_label']}"
                ),
                subtitle=" · ".join(fields["hourly_bits"]),
                arg=f"Claude hourly: {_pct(fields['five_pct'])}"
                + (fields["five_reset_label"] or ""),
                icon=ICON_CLAUDE_CODE,
                mods=_cmd_open(URL_CLAUDE, "Open Claude usage"),
            )
        )
    if include_weekly:
        items.append(
            _item(
                title=(
                    f"Weekly  {_bar(fields['seven_pct'])}  {_pct(fields['seven_pct'])}"
                    f"{fields['seven_reset_label']}"
                ),
                subtitle=" · ".join(fields["weekly_bits"]),
                arg=f"Claude weekly: {_pct(fields['seven_pct'])}"
                + (fields["seven_reset_label"] or ""),
                icon=ICON_CLAUDE,
                mods=_cmd_open(URL_CLAUDE, "Open Claude usage"),
            )
        )
    if include_fable and fields["fable_present"]:
        items.append(
            _item(
                title=(
                    f"Fable  {_bar(fields['fable_pct'])}  {_pct(fields['fable_pct'])}"
                    f"{fields['fable_reset_label']}"
                ),
                subtitle=" · ".join(fields["fable_bits"]),
                arg=f"Claude Fable weekly: {_pct(fields['fable_pct'])}"
                + (fields["fable_reset_label"] or ""),
                icon=ICON_CLAUDE,
                mods=_cmd_open(URL_CLAUDE, "Open Claude usage"),
            )
        )
    return items


# ---- Alfred entry -----------------------------------------------------------

def _env_flag(name: str, default: bool = True) -> bool:
    """Alfred checkboxes export ``1`` / ``0``; missing → default (checked)."""
    raw = os.environ.get(name)
    if raw is None or raw == "":
        return default
    return raw.strip().lower() in ("1", "true", "yes", "on")


def _show_flags() -> Dict[str, bool]:
    return {
        "cursor_auto": _env_flag("show_cursor_auto"),
        "cursor_other": _env_flag("show_cursor_other"),
        "cursor_grok": _env_flag("show_cursor_grok"),
        "claude_hourly": _env_flag("show_claude_hourly"),
        "claude_weekly": _env_flag("show_claude_weekly"),
        "claude_fable": _env_flag("show_claude_fable"),
    }


def build_overview_payload() -> Dict[str, Any]:
    flags = _show_flags()
    cached = _read_overview_cache()
    if cached is not None and cached.get("show") == flags:
        cached["cache"] = {"seconds": OVERVIEW_CACHE_TTL}
        return cached

    items = cursor_overview_items(
        include_auto=flags["cursor_auto"],
        include_other=flags["cursor_other"],
        include_grok=flags["cursor_grok"],
    ) + claude_overview_items(
        include_hourly=flags["claude_hourly"],
        include_weekly=flags["claude_weekly"],
        include_fable=flags["claude_fable"],
    )
    if not items:
        items = [
            _item(
                title="Aeye: no rows enabled",
                subtitle="Turn on at least one row in Configure Workflow",
                arg="",
                valid=False,
            )
        ]

    payload = {
        "skipknowledge": True,
        "cache": {"seconds": OVERVIEW_CACHE_TTL},
        "show": flags,
        "items": items,
    }
    _write_overview_cache(payload)
    return payload


def main() -> int:
    try:
        print(json.dumps(build_overview_payload(), ensure_ascii=False))
        return 0
    except Exception:
        err = traceback.format_exc(limit=3)
        print(
            json.dumps(
                {
                    "skipknowledge": True,
                    "items": [
                        _error_item("Aeye", err.splitlines()[-1] if err else "error")
                    ],
                }
            )
        )
        return 0


if __name__ == "__main__":
    raise SystemExit(main())
