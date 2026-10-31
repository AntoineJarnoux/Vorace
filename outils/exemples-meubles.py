# Génère les modèles et textures des meubles d'exemple : python3 outils/exemples-meubles.py src/main/resources/exemples/meubles
import json, os, random, sys
from PIL import Image
OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
random.seed(7)

def png(nom, f):
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            im.putpixel((x, y), f(x, y))
    im.save(os.path.join(OUT, nom + ".png"))

def bois(x, y):
    base = [(156, 110, 66), (143, 99, 58), (166, 120, 74)][(y // 4) % 3]
    if y % 4 == 3: return (96, 64, 36, 255)
    n = random.randint(-8, 8)
    if (x * 7 + y * 3) % 11 == 0: n -= 14
    return tuple(max(0, min(255, c + n)) for c in base) + (255,)
png("bois", bois)

def lampe(x, y):
    if y < 10:  # abat-jour
        n = random.randint(-6, 6)
        return (255, 226 + n // 2, 150 + n, 255) if 1 <= x <= 14 else (235, 200, 120, 255)
    n = random.randint(-5, 5)
    return (60 + n, 52 + n, 46 + n, 255)  # pied
png("lampe", lampe)

FACES = ["north", "south", "east", "west", "up", "down"]
def uv_auto(a, b, f):
    # UV toujours dans l'image (0 à 16), même pour un élément plus grand qu'un bloc
    dx, dy, dz = (b[0] - a[0], b[1] - a[1], b[2] - a[2])
    w, h = {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy), "up": (dx, dz), "down": (dx, dz)}[f]
    return [0, 0, min(w, 16), min(h, 16)]

def cube(a, b, tex="#0", uvs=None):
    faces = {}
    for f in FACES:
        faces[f] = {"texture": tex, "uv": uvs[f] if uvs and f in uvs else uv_auto(a, b, f)}
    return {"from": a, "to": b, "faces": faces}

DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, -1, 0], "scale": [0.5, 0.5, 0.5]},
    "ground": {"translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
    "fixed": {"scale": [0.5, 0.5, 0.5]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
    "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.4, 0.4, 0.4]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "scale": [0.4, 0.4, 0.4]},
    "head": {"translation": [0, 14, 0]},
}
def modele(nom, tex, elements, display=None):
    d = dict(DISPLAY)
    if display: d.update(display)
    m = {"credit": "Vorace (exemple 2)", "textures": {"0": tex, "particle": tex}, "elements": elements, "display": d}
    with open(os.path.join(OUT, nom + ".json"), "w") as f:
        json.dump(m, f, indent=2)

# chaise : l'avant est la face north (z petit), le dossier est au fond (z grand)
pieds = [cube([x, 0, z], [x + 2, 8, z + 2]) for x in (2, 12) for z in (2, 12)]
modele("chaise", "exemple/bois", pieds + [cube([2, 8, 2], [14, 10, 14]), cube([2, 10, 12], [14, 22, 14])])

# lampe
modele("lampe", "exemple/lampe", [
    cube([5, 0, 5], [11, 1, 11], uvs={f: [0, 10, 16, 16] for f in FACES}),
    cube([7, 1, 7], [9, 10, 9], uvs={f: [0, 10, 16, 16] for f in FACES}),
    cube([4, 10, 4], [12, 15, 12], uvs={f: [0, 0, 16, 10] for f in FACES}),
])

# caisse de rangement
modele("caisse", "exemple/bois", [cube([1, 0, 1], [15, 14, 15])])

# table sur deux blocs : le 2e bloc est vers x positif du modèle
pieds = [cube([x, 0, z], [x + 2, 14, z + 2]) for x in (2, 28) for z in (2, 12)]
modele("table", "exemple/bois", pieds + [cube([1, 14, 1], [31, 16, 15])],
       display={"gui": {"rotation": [30, 225, 0], "translation": [-3, -1, 0], "scale": [0.35, 0.35, 0.35]}})
print(sorted(os.listdir(OUT)))
