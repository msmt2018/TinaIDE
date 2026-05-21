"""Generate the neutral placeholder icon used by APK export templates.

Run from the repo root::

    python tools/gen_template_icon.py

The output is written to every template's ``mipmap-xxhdpi/ic_launcher.webp``.
The intent is a visibly generic icon so users are not misled into thinking the
exported APK is the TinaIDE app itself. Replace at build-time via
``TemplateIconPatcher`` when the user supplies a custom icon.
"""
from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

SIZE = 144
BACKGROUND = (69, 90, 100, 255)        # Material Blue Grey 700
ACCENT = (144, 164, 174, 255)          # Material Blue Grey 300
TEXT_COLOR = (236, 239, 241, 255)      # Material Blue Grey 50

TARGETS = [
    "tools/template-native-activity/src/main/res/mipmap-xxhdpi/ic_launcher.webp",
    "tools/template-sdl3/src/main/res/mipmap-xxhdpi/ic_launcher.webp",
]


def _load_font(size: int) -> ImageFont.ImageFont:
    candidates = [
        "C:/Windows/Fonts/arialbd.ttf",
        "C:/Windows/Fonts/arial.ttf",
        "/System/Library/Fonts/Helvetica.ttc",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    ]
    for path in candidates:
        try:
            return ImageFont.truetype(path, size)
        except OSError:
            continue
    return ImageFont.load_default()


def build_icon() -> Image.Image:
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    radius = 24
    draw.rounded_rectangle(
        (0, 0, SIZE - 1, SIZE - 1),
        radius=radius,
        fill=BACKGROUND,
    )

    box_left, box_top = 30, 36
    box_right, box_bottom = SIZE - 30, SIZE - 36
    draw.rectangle((box_left, box_top, box_right, box_bottom), outline=ACCENT, width=3)
    mid_y = (box_top + box_bottom) // 2
    draw.line((box_left, mid_y, box_right, mid_y), fill=ACCENT, width=3)
    tab_half = 10
    draw.line(
        (SIZE // 2 - tab_half, box_top, SIZE // 2 + tab_half, box_top),
        fill=ACCENT,
        width=5,
    )

    font = _load_font(30)
    text = "APK"
    left, top, right, bottom = draw.textbbox((0, 0), text, font=font)
    tx = (SIZE - (right - left)) // 2 - left
    ty = mid_y + 6
    draw.text((tx, ty), text, font=font, fill=TEXT_COLOR)

    return img


def main() -> None:
    repo_root = Path(__file__).resolve().parent.parent
    icon = build_icon()
    for rel in TARGETS:
        target = repo_root / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        icon.save(target, format="WEBP", lossless=True, quality=100, method=6)
        print(f"wrote {target} ({target.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
