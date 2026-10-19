#!/usr/bin/env python3
"""Generate AniSteam's branded Android launcher icons at each density.

The old committed .b64 files were the default green Android placeholder. This
script creates the actual AniSteam play-mark and writes the launcher resources
directly after Flutter's Android platform is generated in CI.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path("android/app/src/main/res")
S = 512


def build_master() -> Image.Image:
    # Deep navy gradient with a restrained blue glow near the top-right.
    pixels = Image.new("RGBA", (S, S))
    data = []
    for y in range(S):
        for x in range(S):
            t = y / (S - 1)
            glow = max(0.0, 1.0 - (((x - 360) ** 2 + (y - 115) ** 2) ** 0.5) / 420)
            r = int(15 + 8 * (1 - t) + 4 * glow)
            g = int(18 + 9 * (1 - t) + 10 * glow)
            b = int(28 + 17 * (1 - t) + 24 * glow)
            data.append((r, g, b, 255))
    pixels.putdata(data)

    # Rounded-square silhouette, with transparent corners for launchers that
    # do not apply an icon mask themselves.
    mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).rounded_rectangle((10, 10, S - 11, S - 11), radius=112, fill=255)
    pixels.putalpha(mask)

    # Soft orange glow beneath the mark.
    glow_layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow_layer)
    gd.ellipse((115, 105, 420, 405), fill=(255, 104, 25, 90))
    glow_layer = glow_layer.filter(ImageFilter.GaussianBlur(42))
    pixels = Image.alpha_composite(pixels, glow_layer)

    d = ImageDraw.Draw(pixels)
    # Fine inset border and a lime orbit give the mark the app's signature look.
    d.rounded_rectangle((22, 22, S - 23, S - 23), radius=98,
                        outline=(184, 243, 90, 95), width=3)
    d.arc((75, 62, 438, 425), start=205, end=330, fill=(184, 243, 90, 220), width=11)
    d.arc((88, 75, 425, 412), start=22, end=90, fill=(184, 243, 90, 90), width=5)

    # A bold, clean streaming play mark with a small flame-like tail.
    d.polygon([(165, 130), (222, 177), (220, 335), (164, 383), (183, 315), (181, 214)],
              fill=(255, 122, 24, 255))
    d.polygon([(204, 145), (383, 256), (204, 367)], fill=(255, 132, 36, 255))
    # A narrow highlight on the play mark.
    d.polygon([(222, 169), (350, 252), (222, 331), (242, 256)],
              fill=(255, 170, 73, 255))
    # A lime sweep at the lower edge, echoing AniSteam's in-app logo.
    d.line([(148, 385), (219, 414), (284, 396)], fill=(184, 243, 90, 235), width=9)
    d.ellipse((349, 105, 372, 128), fill=(255, 255, 255, 245))
    d.polygon([(394, 137), (402, 157), (422, 165), (402, 173), (394, 193),
               (386, 173), (366, 165), (386, 157)], fill=(255, 255, 255, 220))
    return pixels


def main() -> None:
    master = build_master()
    sizes = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for density, size in sizes.items():
        folder = ROOT / f"mipmap-{density}"
        folder.mkdir(parents=True, exist_ok=True)
        icon = master.resize((size, size), Image.Resampling.LANCZOS)
        for name in ("ic_launcher.webp", "ic_launcher_round.webp"):
            icon.save(folder / name, format="WEBP", quality=96, method=6)
            stale_png = folder / name.replace(".webp", ".png")
            if stale_png.exists():
                stale_png.unlink()

    # Do not let an adaptive-icon XML resource silently override the branded
    # raster icons. The launcher will mask the icon according to device style.
    for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
        adaptive = ROOT / "mipmap-anydpi-v26" / name
        if adaptive.exists():
            adaptive.unlink()
    print("Generated AniSteam play-mark launcher icons for all Android densities.")


if __name__ == "__main__":
    main()
