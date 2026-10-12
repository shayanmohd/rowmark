# Draws the bundled sample lace chart (original work): a 16 stitch by 12 row grid with knit, yarn over,
# k2tog and ssk symbols, read from the bottom up, with row numbers on the right. Under 100 KB.
import sys
from PIL import Image, ImageDraw
cols, rows, cell = 16, 12, 44
margin = 40
w = margin * 2 + cols * cell + 50
h = margin * 2 + rows * cell
img = Image.new('RGB', (w, h), (251, 248, 250))
d = ImageDraw.Draw(img)
ink = (36, 26, 37)
grey = (98, 85, 101)
# Deterministic lace repeat: feather and fan style, varying per row so it reads as a real chart.
def symbol(r, c):
    if r % 2 == 1:
        return 'p' if (c + r // 2) % 7 == 0 else 'k'
    phase = (c + (r // 2) * 2) % 8
    if phase in (1, 5):
        return 'yo'
    if phase == 3:
        return 'k2tog'
    if phase == 7:
        return 'ssk'
    return 'k'
for r in range(rows):
    for c in range(cols):
        x0 = margin + c * cell
        y0 = margin + r * cell
        d.rectangle([x0, y0, x0 + cell, y0 + cell], outline=grey, width=1)
        s = symbol(rows - 1 - r, c)
        cx, cy = x0 + cell / 2, y0 + cell / 2
        if s == 'yo':
            d.ellipse([cx - 9, cy - 9, cx + 9, cy + 9], outline=ink, width=3)
        elif s == 'k2tog':
            d.line([cx - 11, cy + 11, cx + 11, cy - 11], fill=ink, width=3)
        elif s == 'ssk':
            d.line([cx - 11, cy - 11, cx + 11, cy + 11], fill=ink, width=3)
        elif s == 'p':
            d.ellipse([cx - 4, cy - 4, cx + 4, cy + 4], fill=ink)
    # Row numbers on the right for odd rows (right side rows), read bottom to top.
    rn = rows - r
    d.text((margin + cols * cell + 14, margin + r * cell + cell / 2 - 7), str(rn), fill=ink)
# Heavier line every 4 stitches for the repeat boundaries.
for c in range(0, cols + 1, 4):
    x = margin + c * cell
    d.line([x, margin, x, margin + rows * cell], fill=ink, width=2)
d.line([margin, margin, margin + cols * cell, margin], fill=ink, width=2)
d.line([margin, margin + rows * cell, margin + cols * cell, margin + rows * cell], fill=ink, width=2)
img = img.convert('P', palette=Image.ADAPTIVE, colors=16)
img.save(sys.argv[1], optimize=True)
print('wrote', sys.argv[1], w, h)
