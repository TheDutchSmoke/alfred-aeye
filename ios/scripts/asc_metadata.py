#!/usr/bin/env python3
"""Push the App Store listing copy in docs/app-store-metadata.md to App Store Connect.

    ASC_KEY_ID=... ASC_ISSUER_ID=... ./scripts/asc_metadata.py            # dry run: show state + diff
    ASC_KEY_ID=... ASC_ISSUER_ID=... ./scripts/asc_metadata.py --apply    # write the changes

Sets the app-level info (name, subtitle, privacy URL, categories) on the editable app info,
and the version-level copy (description, keywords, promotional text, what's new, support and
marketing URLs, copyright) on the newest editable App Store version (en-US).
Private key: ~/.appstoreconnect/private_keys/AuthKey_$ASC_KEY_ID.p8
"""
import json
import os
import re
import sys
import time
import base64
import urllib.error
import urllib.request
from pathlib import Path

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.asymmetric.utils import decode_dss_signature

BUNDLE_ID = "com.giovanni.aeye"
LOCALE = "en-US"
API = "https://api.appstoreconnect.apple.com/v1"
DOC = Path(__file__).resolve().parent.parent / "docs" / "app-store-metadata.md"
# States in which Apple still accepts edits to the version copy.
EDITABLE = {
    "PREPARE_FOR_SUBMISSION", "DEVELOPER_REJECTED", "REJECTED",
    "METADATA_REJECTED", "INVALID_BINARY",
}


def b64(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def token(key_id: str, issuer: str) -> str:
    key_path = Path.home() / ".appstoreconnect" / "private_keys" / f"AuthKey_{key_id}.p8"
    key = serialization.load_pem_private_key(key_path.read_bytes(), password=None)
    head = b64(json.dumps({"alg": "ES256", "kid": key_id, "typ": "JWT"}).encode())
    now = int(time.time())
    body = b64(json.dumps({"iss": issuer, "iat": now, "exp": now + 1200,
                           "aud": "appstoreconnect-v1"}).encode())
    der = key.sign(f"{head}.{body}".encode(), ec.ECDSA(hashes.SHA256()))
    r, s = decode_dss_signature(der)
    return f"{head}.{body}.{b64(r.to_bytes(32, 'big') + s.to_bytes(32, 'big'))}"


class Client:
    def __init__(self, jwt: str):
        self.jwt = jwt

    def call(self, method: str, path: str, data=None):
        url = path if path.startswith("http") else API + path
        req = urllib.request.Request(url, method=method, headers={
            "Authorization": f"Bearer {self.jwt}", "Content-Type": "application/json"})
        if data is not None:
            req.data = json.dumps({"data": data}).encode()
        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                raw = resp.read()
                return json.loads(raw) if raw else {}
        except urllib.error.HTTPError as err:
            sys.exit(f"{method} {path} -> {err.code}\n{err.read().decode()}")


def parse_doc() -> dict:
    text = DOC.read_text()
    sections = {}
    for m in re.finditer(r"^## (.+?)\n(.*?)(?=^## |\Z)", text, re.S | re.M):
        block = re.search(r"```\n(.*?)\n```", m.group(2), re.S)
        sections[re.sub(r"\s*\(\d+.*\)$", "", m.group(1)).strip()] = (
            block.group(1) if block else m.group(2))
    urls = dict(re.findall(r"^\| (Privacy policy|Support|Marketing) \| `([^`]+)` \|$",
                           text, re.M))
    copyright_ = re.search(r"\*\*Copyright\*\* — `([^`]+)`", text).group(1)
    return {
        "name": sections["Name"], "subtitle": sections["Subtitle"],
        "promotionalText": sections["Promotional text"],
        "description": sections["Description"], "keywords": sections["Keywords"],
        "whatsNew": sections["What's New"], "privacyPolicyUrl": urls["Privacy policy"],
        "supportUrl": urls["Support"], "marketingUrl": urls["Marketing"],
        "copyright": copyright_,
    }


def diff(label: str, current: dict, wanted: dict) -> dict:
    changes = {k: v for k, v in wanted.items() if (current.get(k) or "") != v}
    for k in wanted:
        mark = "CHANGE" if k in changes else "ok    "
        shown = (wanted[k] or "").replace("\n", " ")
        print(f"  {mark} {label}.{k}: {shown[:70]}{'…' if len(shown) > 70 else ''}")
    return changes


def main() -> None:
    apply = "--apply" in sys.argv
    key_id, issuer = os.environ.get("ASC_KEY_ID"), os.environ.get("ASC_ISSUER_ID")
    if not key_id or not issuer:
        sys.exit("set ASC_KEY_ID and ASC_ISSUER_ID")
    meta = parse_doc()
    api = Client(token(key_id, issuer))

    apps = api.call("GET", f"/apps?filter[bundleId]={BUNDLE_ID}")["data"]
    if not apps:
        sys.exit(f"no App Store Connect app for {BUNDLE_ID}")
    app_id = apps[0]["id"]

    versions = api.call("GET", f"/apps/{app_id}/appStoreVersions?limit=10")["data"]
    print("Versions:")
    for v in versions:
        print(f"  {v['attributes']['versionString']}: {v['attributes']['appStoreState']}")
    version = next((v for v in versions if v["attributes"]["appStoreState"] in EDITABLE), None)

    infos = api.call("GET", f"/apps/{app_id}/appInfos")["data"]
    info = next((i for i in infos if i["attributes"].get("appStoreState") in EDITABLE
                 or i["attributes"].get("state") in {"PREPARE_FOR_SUBMISSION", "READY_FOR_DISTRIBUTION"}
                 and len(infos) == 1), None)

    patches = []
    if info is None:
        print("App info: no editable app info (locked while a version is in review)")
    else:
        locs = api.call("GET", f"/appInfos/{info['id']}/appInfoLocalizations")["data"]
        loc = next(l for l in locs if l["attributes"]["locale"] == LOCALE)
        print(f"App info ({LOCALE}):")
        ch = diff("appInfo", loc["attributes"], {k: meta[k] for k in
                  ("name", "subtitle", "privacyPolicyUrl")})
        if ch:
            patches.append((f"/appInfoLocalizations/{loc['id']}",
                            {"type": "appInfoLocalizations", "id": loc["id"], "attributes": ch}))
        patches.append((f"/appInfos/{info['id']}", {
            "type": "appInfos", "id": info["id"], "relationships": {
                "primaryCategory": {"data": {"type": "appCategories", "id": "DEVELOPER_TOOLS"}},
                "secondaryCategory": {"data": {"type": "appCategories", "id": "UTILITIES"}}}}))
        print("  set    appInfo.categories: DEVELOPER_TOOLS / UTILITIES")

    if version is None:
        print("Version copy: no editable version — create one in App Store Connect first")
    else:
        vs = version["attributes"]["versionString"]
        print(f"Version {vs}:")
        marketing = re.search(r'MARKETING_VERSION: "([^"]+)"',
                              (DOC.parent.parent / "project.yml").read_text()).group(1)
        ch = diff("version", version["attributes"],
                  {"versionString": marketing, "copyright": meta["copyright"]})
        if ch:
            patches.append((f"/appStoreVersions/{version['id']}",
                            {"type": "appStoreVersions", "id": version["id"], "attributes": ch}))
        locs = api.call("GET", f"/appStoreVersions/{version['id']}"
                               "/appStoreVersionLocalizations")["data"]
        loc = next(l for l in locs if l["attributes"]["locale"] == LOCALE)
        fields = ("description", "keywords", "promotionalText", "supportUrl", "marketingUrl",
                  "whatsNew")
        wanted = {k: meta[k] for k in fields}
        if len(versions) == 1:
            wanted.pop("whatsNew")  # Apple rejects What's New on a first release
        ch = diff("versionLocalization", loc["attributes"], wanted)
        if ch:
            patches.append((f"/appStoreVersionLocalizations/{loc['id']}",
                            {"type": "appStoreVersionLocalizations", "id": loc["id"],
                             "attributes": ch}))

    if not apply:
        print(f"\nDry run: {len(patches)} request(s) would be sent. Re-run with --apply.")
        return
    for path, data in patches:
        api.call("PATCH", path, data)
        print(f"PATCH {path}: ok")


if __name__ == "__main__":
    main()
