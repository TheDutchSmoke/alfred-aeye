"""Generates the Android launcher icons from the iOS app icon.

    python3 android/scripts/make_icons.py

Adaptive icon: the iOS artwork is the foreground at 80 of 108 dp, which covers the 72 dp the
launcher shows, so the mask never reveals its edge and the owl's eyes stay inside the safe zone.
The background layer is the artwork's purple, for the parallax margin. The monochrome layer (themed
icons, Android 13+) is the eyes and beak cut out of the purple. Writes into app/ and wear/.
"""
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT.parent / "ios" / "Assets.xcassets" / "AppIcon.appiconset" / "icon.png"
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
ART_DP = 80


def is_ink(r, g, b):
    """The eyes' lavender rims, yellow irises and the orange beak; not the face or the pupils."""
    lavender = g > 115 and b > 200
    yellow_or_orange = r > 200 and g > 110 and b < 90
    return lavender or yellow_or_orange


def monochrome(icon):
    """White where the artwork has ink, transparent elsewhere."""
    out = Image.new("RGBA", icon.size, (0, 0, 0, 0))
    src, dst = icon.load(), out.load()
    for y in range(icon.height):
        for x in range(icon.width):
            r, g, b, _ = src[x, y]
            if is_ink(r, g, b):
                dst[x, y] = (255, 255, 255, 255)
    return out


def place(art, px):
    canvas = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    size = round(px * ART_DP / 108)
    scaled = art.resize((size, size), Image.LANCZOS)
    offset = (px - size) // 2
    canvas.paste(scaled, (offset, offset), scaled)
    return canvas


def main():
    icon = Image.open(SOURCE).convert("RGBA")
    mono = monochrome(icon)
    for module in ("app", "wear"):
        res = ROOT / module / "src" / "main" / "res"
        for name, scale in DENSITIES.items():
            folder = res / f"mipmap-{name}"
            folder.mkdir(parents=True, exist_ok=True)
            px = round(108 * scale)
            place(icon, px).save(folder / "ic_launcher_foreground.png", optimize=True)
            place(mono, px).save(folder / "ic_launcher_monochrome.png", optimize=True)
    play = ROOT / "play"
    play.mkdir(exist_ok=True)
    icon.convert("RGB").resize((512, 512), Image.LANCZOS).save(play / "icon-512.png", optimize=True)
    # Background colour: the artwork's mid purple.
    print("background", "#%02X%02X%02X" % icon.getpixel((512, 900))[:3])


if __name__ == "__main__":
    main()
