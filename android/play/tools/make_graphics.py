#!/usr/bin/env python3
"""Draw the Google Play feature graphic from the app's own artwork.

    python3 android/play/tools/make_graphics.py

Writes android/play/feature-graphic-1024x500.png: the iOS app icon, the name, the subtitle the
App Store accepted (no third-party names, as on iOS), and a meter at 42 % drawn exactly as the
app draws it (core/MeterStyle.kt). The store icon, play/icon-512.png, comes from
scripts/make_icons.py. Needs Pillow and Roboto in ~/Library/Fonts.
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

PLAY = Path(__file__).resolve().parents[1]
ANDROID = PLAY.parent
ICON = ANDROID.parent / "ios" / "Assets.xcassets" / "AppIcon.appiconset" / "icon.png"
FONTS = Path.home() / "Library" / "Fonts"

W, H = 1024, 500
# The icon's purple (#5F30B3), a little lighter at the top and darker at the bottom.
TOP, BOTTOM = (0x6C, 0x3D, 0xC2), (0x4C, 0x24, 0x96)
WHITE = (255, 255, 255)
LAVENDER = (0xE4, 0xDA, 0xFF)

# MeterStyle: rim, top highlight, body, bottom; gradient stops; inner disc and diameter ratios.
GREEN = ((0x01, 0xA0, 0x01), (0x85, 0xFF, 0x85), (0x03, 0xB4, 0x03), (0x01, 0x92, 0x01))
EMPTY = ((0xD3, 0xD3, 0xD3), (0xFF, 0xFF, 0xFF), (0xD9, 0xD9, 0xD9), (0xD5, 0xD5, 0xD5))
STOPS = (0.0, 0.12, 0.55, 1.0)
INNER_PER_RADIUS = 0.93
DIAMETER_PER_PITCH = 20.7 / 23


def lerp(a, b, t):
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))


def gradient_at(colors, t):
    stops = list(zip(STOPS, colors))
    for (t0, c0), (t1, c1) in zip(stops, stops[1:]):
        if t <= t1:
            return lerp(c0, c1, (t - t0) / (t1 - t0) if t1 > t0 else 0)
    return colors[-1]


def disc(pitch, palette, scale=4):
    """One meter slot, supersampled for clean edges."""
    size = pitch * scale
    r = size * DIAMETER_PER_PITCH / 2
    c = size / 2
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    draw.ellipse((c - r, c - r, c + r, c + r), fill=palette[0])
    inner = r * INNER_PER_RADIUS
    fill = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    fd = ImageDraw.Draw(fill)
    for y in range(size):
        t = min(max((y - (c - inner)) / (2 * inner), 0), 1)
        fd.line((0, y, size, y), fill=gradient_at(palette[1:], t))
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse((c - inner, c - inner, c + inner, c + inner), fill=255)
    img.paste(fill, (0, 0), mask)
    return img.resize((pitch, pitch), Image.Resampling.LANCZOS)


def rounded(img, radius):
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, *img.size), radius=radius, fill=255)
    img.putalpha(mask)
    return img


def feature_graphic():
    img = Image.new("RGB", (W, H))
    draw = ImageDraw.Draw(img)
    for y in range(H):
        draw.line((0, y, W, y), fill=lerp(TOP, BOTTOM, y / (H - 1)))

    icon = Image.open(ICON).convert("RGBA").resize((300, 300), Image.Resampling.LANCZOS)
    icon = rounded(icon, 66)
    img.paste(icon, (88, 100), icon)

    x = 452
    draw.text((x - 6, 92), "Aeye", font=ImageFont.truetype(str(FONTS / "Roboto-Bold.ttf"), 128), fill=WHITE)
    draw.text((x, 252), "AI coding limits at a glance",
              font=ImageFont.truetype(str(FONTS / "Roboto-Regular.ttf"), 40), fill=LAVENDER)

    pitch = 48
    for i in range(10):
        d = disc(pitch, GREEN if i < 4 else EMPTY)
        img.paste(d, (x + i * pitch, 332), d)

    img.save(PLAY / "feature-graphic-1024x500.png")


if __name__ == "__main__":
    feature_graphic()
    print("wrote feature-graphic-1024x500.png")
