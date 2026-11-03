#!/usr/bin/env python3
"""Upload docs/screenshots to the editable App Store version (en-US) on App Store Connect.

    ASC_KEY_ID=... ASC_ISSUER_ID=... ./scripts/asc_screenshots.py            # dry run
    ASC_KEY_ID=... ASC_ISSUER_ID=... ./scripts/asc_screenshots.py --apply    # upload

Idempotent: a file already in its set (same file name) is skipped. Order follows SETS.
"""
import hashlib
import os
import sys
import time
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from asc_metadata import BUNDLE_ID, EDITABLE, LOCALE, Client, token  # noqa: E402

SHOTS = Path(__file__).resolve().parent.parent / "docs" / "screenshots"
SETS = {
    # 6.9" (1320 × 2868) — also accepted for the 6.5" slot.
    "APP_IPHONE_67": ["iphone-overview.png", "iphone-overview-dark.png", "iphone-settings.png"],
    "APP_WATCH_ULTRA": ["watch-overview-ultra2.png", "watch-overview.png"],
}


def upload(api: Client, set_id: str, path: Path) -> None:
    data = path.read_bytes()
    shot = api.call("POST", "/appScreenshots", {
        "type": "appScreenshots",
        "attributes": {"fileName": path.name, "fileSize": len(data)},
        "relationships": {"appScreenshotSet": {"data": {"type": "appScreenshotSets",
                                                        "id": set_id}}}})["data"]
    for op in shot["attributes"]["uploadOperations"]:
        chunk = data[op["offset"]:op["offset"] + op["length"]]
        req = urllib.request.Request(op["url"], data=chunk, method=op["method"], headers={
            h["name"]: h["value"] for h in op.get("requestHeaders") or []})
        urllib.request.urlopen(req, timeout=60).read()
    api.call("PATCH", f"/appScreenshots/{shot['id']}", {
        "type": "appScreenshots", "id": shot["id"],
        "attributes": {"uploaded": True, "sourceFileChecksum": hashlib.md5(data).hexdigest()}})
    for _ in range(30):
        state = api.call("GET", f"/appScreenshots/{shot['id']}")["data"]["attributes"][
            "assetDeliveryState"]
        if state["state"] in {"COMPLETE", "FAILED"}:
            break
        time.sleep(2)
    errors = "; ".join(e.get("description", "") for e in state.get("errors") or [])
    print(f"    {path.name}: {state['state']}{' — ' + errors if errors else ''}")


def main() -> None:
    apply = "--apply" in sys.argv
    key_id, issuer = os.environ.get("ASC_KEY_ID"), os.environ.get("ASC_ISSUER_ID")
    if not key_id or not issuer:
        sys.exit("set ASC_KEY_ID and ASC_ISSUER_ID")
    api = Client(token(key_id, issuer))

    app_id = api.call("GET", f"/apps?filter[bundleId]={BUNDLE_ID}")["data"][0]["id"]
    versions = api.call("GET", f"/apps/{app_id}/appStoreVersions?limit=10")["data"]
    version = next((v for v in versions if v["attributes"]["appStoreState"] in EDITABLE), None)
    if version is None:
        sys.exit("no editable App Store version")
    locs = api.call("GET", f"/appStoreVersions/{version['id']}/appStoreVersionLocalizations")
    loc_id = next(l["id"] for l in locs["data"] if l["attributes"]["locale"] == LOCALE)
    existing = {s["attributes"]["screenshotDisplayType"]: s["id"] for s in api.call(
        "GET", f"/appStoreVersionLocalizations/{loc_id}/appScreenshotSets")["data"]}
    print(f"Version {version['attributes']['versionString']} ({LOCALE})")

    for display, files in SETS.items():
        set_id = existing.get(display)
        present = set()
        if set_id:
            present = {s["attributes"]["fileName"] for s in api.call(
                "GET", f"/appScreenshotSets/{set_id}/appScreenshots")["data"]}
        todo = [f for f in files if f not in present]
        print(f"  {display}: {len(present)} present, {len(todo)} to upload {todo}")
        if not apply or not todo:
            continue
        if set_id is None:
            set_id = api.call("POST", "/appScreenshotSets", {
                "type": "appScreenshotSets",
                "attributes": {"screenshotDisplayType": display},
                "relationships": {"appStoreVersionLocalization": {"data": {
                    "type": "appStoreVersionLocalizations", "id": loc_id}}}})["data"]["id"]
        for name in todo:
            upload(api, set_id, SHOTS / name)

    if not apply:
        print("\nDry run. Re-run with --apply.")


if __name__ == "__main__":
    main()
