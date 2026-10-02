from PIL import Image, ImageDraw

SCALE = 512 / 108.0
SIZE = 512
BG = (5, 5, 5, 255)          # #050505
GREEN = (0, 229, 160, 255)   # #00E5A0
GREEN_28 = (0, 229, 160, 71)  # 0.28 alpha
GREEN_55 = (0, 229, 160, 140) # 0.55 alpha


def s(v):
    return v * SCALE


img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
draw = ImageDraw.Draw(img, "RGBA")

# Background rounded square (radius 24 in a 108 viewport)
draw.rounded_rectangle([0, 0, SIZE - 1, SIZE - 1], radius=s(24), fill=BG)


def dot(cx, cy, r, color):
    draw.ellipse([s(cx - r), s(cy - r), s(cx + r), s(cy + r)], fill=color)


def line(x1, y1, x2, y2, width, color):
    draw.line([s(x1), s(y1), s(x2), s(y2)], fill=color, width=max(1, int(round(width * SCALE))))


# Three "signal ping" marks (line + dot), 0.55 alpha
line(18, 52, 6, 52, 1.4, GREEN_55)
dot(4, 52, 1.6, GREEN_55)
line(90, 30, 102, 30, 1.4, GREEN_55)
dot(104, 30, 1.6, GREEN_55)
line(54, 94, 54, 104, 1.4, GREEN_55)
dot(54, 106, 1.6, GREEN_55)


def rounded_square(x1, y1, x2, y2, r, color):
    draw.rounded_rectangle([s(x1), s(y1), s(x2), s(y2)], radius=s(r), fill=color)


# Halo (scaled 1.18x around pivot 54,54), 0.28 alpha - approximated by
# scaling each shape's coordinates directly around (54,54).
def scale_around(x, y, factor, pivot=54):
    return pivot + (x - pivot) * factor, pivot + (y - pivot) * factor


def rounded_square_scaled(x1, y1, x2, y2, r, color, factor):
    p1 = scale_around(x1, y1, factor)
    p2 = scale_around(x2, y2, factor)
    draw.rounded_rectangle([s(p1[0]), s(p1[1]), s(p2[0]), s(p2[1])], radius=s(r * factor), fill=color)


def line_scaled(x1, y1, x2, y2, width, color, factor):
    p1 = scale_around(x1, y1, factor)
    p2 = scale_around(x2, y2, factor)
    w = width * factor
    draw.line([s(p1[0]), s(p1[1]), s(p2[0]), s(p2[1])], fill=color, width=max(1, int(round(w * SCALE))))
    dot(p1[0], p1[1], w / 2, color)
    dot(p2[0], p2[1], w / 2, color)


def line_capped(x1, y1, x2, y2, width, color):
    draw.line([s(x1), s(y1), s(x2), s(y2)], fill=color, width=max(1, int(round(width * SCALE))))
    dot(x1, y1, width / 2, color)
    dot(x2, y2, width / 2, color)


rounded_square_scaled(29, 60, 50, 90, 9, GREEN_28, 1.18)
rounded_square_scaled(67, 18, 88, 48, 9, GREEN_28, 1.18)
line_scaled(43, 65, 65, 43, 13, GREEN_28, 1.18)

# Real glyph on top, solid
rounded_square(29, 60, 50, 90, 9, GREEN)
rounded_square(67, 18, 88, 48, 9, GREEN)
line_capped(43, 65, 65, 43, 13, GREEN)

img.save("icon_512.png")
print("saved icon_512.png", img.size)
