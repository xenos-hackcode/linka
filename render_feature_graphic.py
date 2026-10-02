from PIL import Image, ImageDraw, ImageFont

W, H = 1024, 500
BG = (5, 5, 5, 255)
GREEN = (0, 229, 160, 255)
GREEN_28 = (0, 229, 160, 60)
MUTED = (150, 165, 158, 255)

img = Image.new("RGB", (W, H), BG[:3])
draw = ImageDraw.Draw(img, "RGBA")

# Faint ambient glow behind the mark (a few soft concentric circles)
glow_cx, glow_cy = 190, 250
for r, a in [(230, 14), (170, 20), (110, 28)]:
    draw.ellipse([glow_cx - r, glow_cy - r, glow_cx + r, glow_cy + r], fill=(0, 229, 160, a))

# --- Icon mark (same glyph as the app icon, scaled up) ---
def s(v, scale, off=0):
    return v * scale + off

scale = 1.85
ox, oy = 70, 145  # offset so the 108-unit glyph sits centered in the mark area


def pt(x, y):
    return (s(x, scale, ox), s(y, scale, oy))


def rounded_sq(x1, y1, x2, y2, r, color):
    draw.rounded_rectangle([*pt(x1, y1), *pt(x2, y2)], radius=r * scale, fill=color)


def thick_line(x1, y1, x2, y2, width, color):
    p1, p2 = pt(x1, y1), pt(x2, y2)
    w = width * scale
    draw.line([p1, p2], fill=color, width=int(w))
    draw.ellipse([p1[0] - w / 2, p1[1] - w / 2, p1[0] + w / 2, p1[1] + w / 2], fill=color)
    draw.ellipse([p2[0] - w / 2, p2[1] - w / 2, p2[0] + w / 2, p2[1] + w / 2], fill=color)


def dot(x, y, r, color):
    p = pt(x, y)
    draw.ellipse([p[0] - r * scale, p[1] - r * scale, p[0] + r * scale, p[1] + r * scale], fill=color)


def thin_line(x1, y1, x2, y2, color):
    draw.line([pt(x1, y1), pt(x2, y2)], fill=color, width=max(1, int(1.4 * scale)))


# ping marks
thin_line(18, 52, 6, 52, (0, 229, 160, 140))
dot(4, 52, 1.6, (0, 229, 160, 140))
thin_line(90, 30, 102, 30, (0, 229, 160, 140))
dot(104, 30, 1.6, (0, 229, 160, 140))
thin_line(54, 94, 54, 104, (0, 229, 160, 140))
dot(54, 106, 1.6, (0, 229, 160, 140))

# halo
rounded_sq(29 - 5, 60 - 5, 50 + 5, 90 + 5, 9, GREEN_28)
rounded_sq(67 - 5, 18 - 5, 88 + 5, 48 + 5, 9, GREEN_28)

# solid glyph
rounded_sq(29, 60, 50, 90, 9, GREEN)
rounded_sq(67, 18, 88, 48, 9, GREEN)
thick_line(43, 65, 65, 43, 13, GREEN)

# --- Wordmark + tagline ---
wordmark_font = ImageFont.truetype("C:/Windows/Fonts/consolab.ttf", 92)
tagline_font = ImageFont.truetype("C:/Windows/Fonts/segoeui.ttf", 30)
rule_font = ImageFont.truetype("C:/Windows/Fonts/consola.ttf", 20)

text_x = 380
draw.text((text_x, 150), "LINKA", font=wordmark_font, fill=GREEN)
draw.text((text_x + 4, 260), "Remote device control & support", font=tagline_font, fill=(230, 236, 233, 255))

# small technical rule under the tagline, matching the app's own hairline style
draw.line([(text_x + 4, 310), (text_x + 4 + 520, 310)], fill=(0, 229, 160, 70), width=1)
draw.text((text_x + 4, 325), "LINK  ·  REQUEST  ·  GLOBAL  ·  GROUPS", font=rule_font, fill=MUTED)

img.save("feature_graphic_1024x500.png")
print("saved", img.size)
