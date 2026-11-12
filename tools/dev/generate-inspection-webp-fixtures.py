"""Regenerate tiny, synthetic WebP parser fixtures; Pillow 12.0.0, no network or customer images.
Install Pillow in an isolated environment, then run this script from any directory.
Production and Java tests consume checked-in bytes and do not require Python/Pillow.
"""
from pathlib import Path
from PIL import Image

destination = Path(__file__).resolve().parents[2] / "backend/src/test/resources/file-inspection"
destination.mkdir(parents=True, exist_ok=True)
image = Image.new("RGBA", (8, 6))
for y in range(6):
    for x in range(8):
        image.putpixel((x, y), (x * 30, y * 40, 100, 0 if x == 0 else 255))
image.save(destination / "synthetic-lossless.webp", lossless=True, exact=True)
image.convert("RGB").save(destination / "synthetic-lossy.webp", quality=85)
image.save(destination / "synthetic-alpha.webp", quality=85)
image.save(destination / "synthetic-animated.webp", lossless=True, save_all=True,
           append_images=[Image.new("RGBA", image.size, "red")], duration=100, loop=0)
