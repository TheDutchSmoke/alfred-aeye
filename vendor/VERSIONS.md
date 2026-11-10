# Vendored package pins

| Package | Upstream | Tag | Commit |
|---------|----------|-----|--------|
| cursor-usage | https://github.com/javaisbetterthanpython/cursor-usage | v0.2.0 | `9238796932e8cc1a6f91bf45d20feb374b77846a` |
| claude-monitor | https://github.com/Maciek-roboblog/Claude-Code-Usage-Monitor | v4.0.0 | `c59a83bf943f329f0e61f1a29c760353ee1860a5` |

Local patches on top of the pin:

- `claude-monitor` `output/api_usage.py`: treat OAuth `utilization` as a 0–100 percentage (not a 0–1 fraction), and prefer the structured `limits[].percent` fields when present. Without this, weekly `utilization: 1.0` (1%) was shown as 100%.
- `claude-monitor` `output/api_usage.py` + `output/snapshots.py`: read the `weekly_scoped` row whose `scope.model.display_name` is Fable from `limits[]` and pass it through as `limits.seven_day_fable` in the `--once` JSON (key absent when the plan has no Fable limit). Same API response, no extra request.
- `cursor-usage` `api.py`: add `sand_usage_status()` (`POST /api/dashboard/get-sand-usage-status`) for Grok Bot weekly included usage.
- `cursor-usage` `auth.py`: skip expired local session JWTs so a stale Keychain token does not hide a still-valid Cursor app session.

Refresh:

```bash
# re-clone into /tmp, rsync into vendor/, then:
./scripts/bootstrap_lib.sh
./scripts/build_workflow.sh
```
