"""
Generates build/icon.png (1024x1024) -- a wisp/smoke spiral mark on a dark
gradient, matching the overlay's dark-translucent aesthetic (see
Overlay.tsx's rgba(20,20,22,0.78) background). Run once; electron-builder
picks up build/icon.png and derives .ico/.icns from it automatically.

Not a build-time dependency -- this is a one-off asset generation script,
not run by any npm/build script. Re-run manually if the mark needs a redesign.
"""
import math

from PIL import Image, ImageDraw, ImageFilter

SIZE = 1024
img = Image.new("RGB", (SIZE, SIZE))
px = img.load()

# Vertical gradient background: deep indigo -> dark violet, evoking the
# overlay's dark glass look without being literally the same flat gray.
top = (24, 18, 38)
bottom = (46, 24, 74)
for y in range(SIZE):
    t = y / (SIZE - 1)
    r = int(top[0] + (bottom[0] - top[0]) * t)
    g = int(top[1] + (bottom[1] - top[1]) * t)
    b = int(top[2] + (bottom[2] - top[2]) * t)
    for x in range(SIZE):
        px[x, y] = (r, g, b)

# Rounded-square mask (macOS/most launchers apply their own mask, but this
# keeps the raw PNG itself looking intentional on Linux, which uses it as-is).
mask = Image.new("L", (SIZE, SIZE), 0)
ImageDraw.Draw(mask).rounded_rectangle([0, 0, SIZE, SIZE], radius=200, fill=255)

# The wisp mark: a tapering spiral drawn as a chain of circles shrinking in
# radius and fading in opacity toward the tail, suggesting a curl of smoke/
# light rather than a literal logo -- keeps it abstract and legible at
# tray-icon size (16-32px) where fine detail disappears.
mark = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
mdraw = ImageDraw.Draw(mark)

cx, cy = SIZE * 0.5, SIZE * 0.52
turns = 2.3
steps = 260
max_r = SIZE * 0.30
for i in range(steps):
    t = i / (steps - 1)  # 0 (tail, outer) -> 1 (head, center)
    angle = turns * 2 * math.pi * t
    radius = max_r * (1 - t) ** 0.85
    x = cx + radius * math.cos(angle)
    y = cy + radius * math.sin(angle) * 0.92  # slight ellipse, less static than a perfect circle
    dot_r = SIZE * (0.017 + 0.05 * t)  # thin tail, fat head
    alpha = int(70 + 185 * t)
    mdraw.ellipse(
        [x - dot_r, y - dot_r, x + dot_r, y + dot_r],
        fill=(245, 240, 255, alpha),
    )

mark = mark.filter(ImageFilter.GaussianBlur(radius=SIZE * 0.004))

img = img.convert("RGBA")
img.paste(mark, (0, 0), mark)
img.putalpha(mask)

# Composite onto opaque background for platforms/tools that mishandle alpha
# in a plain PNG icon source (electron-builder's icon pipeline expects this).
final = Image.new("RGB", (SIZE, SIZE), top)
final.paste(img, (0, 0), img)

import os

os.makedirs("build", exist_ok=True)
final.save("build/icon.png")
print("wrote build/icon.png")
