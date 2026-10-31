# Génère les images des glyphes d'exemple : python3 outils/exemples-glyphes.py src/main/resources/exemples/glyphes
import os, sys
from PIL import Image, ImageDraw
OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)

def dessin(nom, lignes, couleurs):
    """Emoji 9x9 en pixel art : chaque lettre de « lignes » est une couleur de « couleurs », « . » = transparent."""
    im = Image.new("RGBA", (9, 9), (0, 0, 0, 0))
    for y, l in enumerate(lignes):
        for x, c in enumerate(l):
            if c != ".":
                im.putpixel((x, y), couleurs[c] + (255,))
    im.save(os.path.join(OUT, nom + ".png"))

dessin("coeur", [
    ".RR...RR.",
    "RLRR.RRRR",
    "RLRRRRRRR",
    "RRRRRRRRR",
    ".RRRRRRR.",
    "..RRRRR..",
    "...RRR...",
    "....R....",
    ".........",
], {"R": (226, 38, 64), "L": (255, 140, 160)})

dessin("etoile", [
    "....J....",
    "....J....",
    "...JJJ...",
    "JJJJBJJJJ",
    ".JJJJJJJ.",
    "..JJJJJ..",
    "..JJ.JJ..",
    ".JJ...JJ.",
    ".J.....J.",
], {"J": (255, 200, 40), "B": (255, 245, 170)})

dessin("sourire", [
    "..JJJJJ..",
    ".JJJJJJJ.",
    "JJNJJJNJJ",
    "JJNJJJNJJ",
    "JJJJJJJJJ",
    "JNJJJJJNJ",
    "JJNNNNNJJ",
    ".JJJJJJJ.",
    "..JJJJJ..",
], {"J": (255, 205, 60), "N": (90, 55, 20)})

dessin("feu", [
    "....R....",
    "...RR....",
    "...RRR.R.",
    "..RROR.R.",
    ".RROORRR.",
    ".ROOJORR.",
    "RROJJJORR",
    "RROJJJORR",
    ".RROOORR.",
], {"R": (220, 50, 20), "O": (255, 140, 20), "J": (255, 230, 90)})

# fond de menu pour un coffre de 3 lignes : 176 x 168 (même place des cases que le coffre du jeu)
L, H = 176, 168
im = Image.new("RGBA", (L, H), (0, 0, 0, 0))
d = ImageDraw.Draw(im)
fond, clair, sombre, bord = (222, 210, 238), (246, 240, 252), (120, 80, 160), (40, 20, 60)
d.rounded_rectangle([0, 0, L - 1, H - 1], radius=4, fill=bord)
d.rounded_rectangle([1, 1, L - 2, H - 2], radius=3, fill=fond)
d.line([(2, 2), (L - 4, 2)], fill=clair); d.line([(2, 2), (2, H - 4)], fill=clair)
d.line([(3, H - 2), (L - 2, H - 2)], fill=sombre); d.line([(L - 2, 3), (L - 2, H - 2)], fill=sombre)
# bandeau violet derrière les cases du coffre
d.rounded_rectangle([5, 15, L - 6, 15 + 3 * 18 + 5], radius=3, fill=(176, 140, 214))

def case(x, y):
    # case de 18x18 : coin haut-gauche (x-1, y-1), l'objet est en (x, y)
    d.rectangle([x - 1, y - 1, x + 16, y + 16], fill=(139, 110, 170))
    d.line([(x - 1, y - 1), (x + 15, y - 1)], fill=(80, 50, 110)); d.line([(x - 1, y - 1), (x - 1, y + 15)], fill=(80, 50, 110))
    d.line([(x, y + 16), (x + 16, y + 16)], fill=(250, 245, 255)); d.line([(x + 16, y), (x + 16, y + 16)], fill=(250, 245, 255))

for r in range(3):
    for c in range(9):
        case(8 + c * 18, 18 + r * 18)
for r in range(3):
    for c in range(9):
        case(8 + c * 18, 85 + r * 18)
for c in range(9):
    case(8 + c * 18, 143)
# petites gemmes décoratives dans les coins du bandeau
for x in (L - 14, L - 10):
    d.rectangle([x, 6, x + 2, 8], fill=(226, 38, 64))
im.save(os.path.join(OUT, "fond_coffre.png"))
