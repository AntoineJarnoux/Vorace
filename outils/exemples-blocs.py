# Génère les textures des blocs d'exemple : python3 outils/exemples-blocs.py src/main/resources/exemples/blocs
import os, random, sys
from PIL import Image
OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
random.seed(11)

def bruit(base, amp):
    return tuple(max(0, min(255, c + random.randint(-amp, amp))) for c in base)

# minerai : pierre grise + éclats de rubis
im = Image.new("RGBA", (16, 16))
for y in range(16):
    for x in range(16):
        im.putpixel((x, y), bruit((125, 125, 125), 12) + (255,))
for cx, cy in [(3, 3), (10, 5), (5, 11), (12, 12)]:
    for dx, dy, c in [(0, 0, (230, 40, 70)), (1, 0, (200, 20, 50)), (0, 1, (200, 20, 50)), (1, 1, (150, 10, 35)), (-1, 0, (255, 110, 140))]:
        x, y = cx + dx, cy + dy
        if 0 <= x < 16 and 0 <= y < 16:
            im.putpixel((x, y), c + (255,))
im.save(os.path.join(OUT, "minerai_rubis.png"))

# bloc de rubis : rouge avec reflets et bordure
im = Image.new("RGBA", (16, 16))
for y in range(16):
    for x in range(16):
        if x in (0, 15) or y in (0, 15):
            c = (120, 10, 30)
        elif (x + y) % 7 == 0:
            c = (255, 120, 150)
        else:
            c = bruit((205, 25, 55), 10)
        im.putpixel((x, y), c + (255,))
im.save(os.path.join(OUT, "bloc_rubis.png"))
print(sorted(os.listdir(OUT)))
