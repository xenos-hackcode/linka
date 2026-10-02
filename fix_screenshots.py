from PIL import Image
import glob
import os

BG = (5, 5, 5)  # matches the app's real dark background

src_dir = "C:/Users/WINDOWS11/Documents/Linka/screenshots"
out_dir = "C:/Users/WINDOWS11/Documents/Linka/screenshots_store"
os.makedirs(out_dir, exist_ok=True)

for path in sorted(glob.glob(f"{src_dir}/*.png")):
    im = Image.open(path)
    # Flatten alpha onto a solid background - store requires 24-bit PNG, no alpha.
    if im.mode in ("RGBA", "LA"):
        flat = Image.new("RGB", im.size, BG)
        flat.paste(im, mask=im.split()[-1])
        im = flat
    else:
        im = im.convert("RGB")

    w, h = im.size
    min_dim, max_dim = min(w, h), max(w, h)
    if max_dim > 2 * min_dim:
        # Pad the narrower side with solid background bars until the ratio
        # is within the store's 2:1 max - Linka's real 1080x2340 phone
        # capture is ~2.17:1, just over the limit.
        if w < h:
            needed_w = -(-h // 2)  # ceil(h/2)
            pad = needed_w - w
            left = pad // 2
            right = pad - left
            new_im = Image.new("RGB", (needed_w, h), BG)
            new_im.paste(im, (left, 0))
            im = new_im
        else:
            needed_h = -(-w // 2)
            pad = needed_h - h
            top = pad // 2
            bottom = pad - top
            new_im = Image.new("RGB", (w, needed_h), BG)
            new_im.paste(im, (0, top))
            im = new_im

    out_path = f"{out_dir}/{os.path.basename(path)}"
    im.save(out_path, "PNG")
    print(out_path, im.size, im.mode)
