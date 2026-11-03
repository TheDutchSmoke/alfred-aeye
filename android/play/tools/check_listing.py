#!/usr/bin/env python3
"""Check the Play listing copy and graphics against Play's limits.

    python3 android/play/tools/check_listing.py

Reads STORE_LISTING.md (the fenced block under each heading) and the images beside it. Exits
non-zero on any violation, so a listing edited later cannot drift past a limit unnoticed until
the Console refuses it. Also fails if a third-party name reaches the app name or the short
description: the App Store rejected 1.0.1 for exactly that (Guideline 4.1(c)), and Play's
impersonation policy reads the same way.
"""
import glob
import os
import re
import sys

from PIL import Image

PLAY = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LIMITS = {"App name": 30, "Short description": 80, "Full description": 4000, "Release notes": 500}
BRANDS = re.compile(r"cursor|claude|anthropic|grok|alfred|openai|google", re.I)

text = open(os.path.join(PLAY, "STORE_LISTING.md")).read()
failed = False


def report(ok, line):
    global failed
    failed |= not ok
    print(("ok   " if ok else "FAIL ") + line)


for heading, limit in LIMITS.items():
    m = re.search(r"^## " + re.escape(heading) + r".*?\n```\n(.*?)\n```", text, re.S | re.M)
    if not m:
        report(False, f"{heading}: section not found")
        continue
    body = m.group(1)
    report(len(body) <= limit, f"{heading}: {len(body)} / {limit}")
    if heading in ("App name", "Short description"):
        brand = BRANDS.search(body)
        report(brand is None, f"{heading}: no third-party names" + (f" (found {brand.group(0)!r})" if brand else ""))

for name, size in [("icon-512.png", (512, 512)), ("feature-graphic-1024x500.png", (1024, 500))]:
    im = Image.open(os.path.join(PLAY, name))
    report(im.size == size and im.mode in ("RGB", "RGBA"), f"{name}: {im.size} {im.mode}")

shots = sorted(glob.glob(os.path.join(PLAY, "screenshots", "*.png")))
report(2 <= len(shots) <= 8, f"phone screenshots: {len(shots)} (2–8)")
for path in shots:
    im = Image.open(path)
    short, long_ = sorted(im.size)
    ok = im.mode == "RGB" and short >= 320 and long_ <= 3840 and long_ <= 2 * short
    report(ok, f"screenshots/{os.path.basename(path)}: {im.size} {im.mode}")

wear = sorted(glob.glob(os.path.join(PLAY, "screenshots-wear", "*.png")))
report(1 <= len(wear) <= 8, f"Wear OS screenshots: {len(wear)} (1–8)")
for path in wear:
    im = Image.open(path)
    ok = im.mode == "RGB" and im.width == im.height and im.width >= 384
    report(ok, f"screenshots-wear/{os.path.basename(path)}: {im.size} {im.mode}")

sys.exit(1 if failed else 0)
