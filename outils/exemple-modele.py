# Génère le modèle animé d'exemple (projet Blockbench) :
#   python3 outils/exemple-modele.py src/main/resources/exemples/modeles/golem_rubis.bbmodel
# Le fichier s'ouvre dans Blockbench (format « Modèle générique ») pour le modifier.
import base64, io, json, random, sys, uuid
from PIL import Image

random.seed(7)
OUT = sys.argv[1]

def uid():
    return str(uuid.UUID(int=random.getrandbits(128)))

# ---------------------------------------------------------------- texture 64x64
T = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
def bruit(c, a):
    return tuple(max(0, min(255, v + random.randint(-a, a))) for v in c) + (255,)
for y in range(16):          # A : pierre avec veines de rubis
    for x in range(16):
        T.putpixel((x, y), bruit((118, 112, 120), 10))
for (x, y) in [(2, 3), (3, 4), (4, 4), (5, 5), (10, 2), (11, 3), (11, 4), (12, 9), (13, 10), (6, 12), (7, 12), (8, 13), (1, 9), (14, 14)]:
    T.putpixel((x, y), (214, 30, 60, 255))
for y in range(16, 32):      # D : pierre plus sombre (jambes)
    for x in range(16):
        T.putpixel((x, y), bruit((86, 80, 90), 9))
for y in range(8):           # B : visage 8x8
    for x in range(16, 24):
        T.putpixel((x, y), bruit((118, 112, 120), 8))
for (x, y) in [(17, 3), (18, 3), (21, 3), (22, 3)]:
    T.putpixel((x, y), (255, 60, 90, 255))
for (x, y) in [(17, 2), (22, 2)]:
    T.putpixel((x, y), (70, 64, 72, 255))
for x in range(18, 22):
    T.putpixel((x, 6), (60, 52, 62, 255))
for y in range(8):           # C : rubis
    for x in range(32, 40):
        r = 160 + 12 * (7 - y)
        T.putpixel((x, y), (min(255, r), 20 + 6 * (7 - y), 50 + 5 * (7 - y), 255))
T.putpixel((33, 1), (255, 200, 210, 255)); T.putpixel((34, 2), (255, 160, 180, 255))
buf = io.BytesIO(); T.save(buf, "PNG")
SOURCE = "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()

# ---------------------------------------------------------------- cubes
elements = []
def cube(nom, de, a, zone="pierre", face_avant=None, rotation=None, origine=None):
    faces = {}
    dx, dy, dz = a[0] - de[0], a[1] - de[1], a[2] - de[2]
    tailles = {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy), "up": (dx, dz), "down": (dx, dz)}
    base = {"pierre": (0, 0), "sombre": (0, 16), "rubis": (32, 0)}[zone]
    for f, (w, h) in tailles.items():
        u0, v0 = base
        uv = [u0, v0, u0 + min(w, 16 if zone != "rubis" else 8), v0 + min(h, 16 if zone != "rubis" else 8)]
        if f == "north" and face_avant:
            uv = face_avant
        faces[f] = {"uv": uv, "texture": 0}
    e = {"name": nom, "box_uv": False, "rescale": False, "locked": False, "render_order": "default", "allow_mirror_modeling": True,
         "from": de, "to": a, "autouv": 0, "color": 0, "origin": origine or [(de[0] + a[0]) / 2, (de[1] + a[1]) / 2, (de[2] + a[2]) / 2],
         "faces": faces, "type": "cube", "uuid": uid()}
    if rotation:
        e["rotation"] = rotation
    elements.append(e)
    return e["uuid"]

def groupe(nom, origine, enfants):
    return {"name": nom, "origin": origine, "color": 0, "uuid": uid(), "export": True, "mirror_uv": False,
            "isOpen": True, "locked": False, "visibility": True, "autouv": 0, "children": enfants}

tete = groupe("tete", [0, 22, 0], [
    cube("tete", [-4, 22, -4], [4, 30, 4], face_avant=[16, 0, 24, 8]),
    cube("cristal", [-1.5, 30, -1.5], [1.5, 35, 1.5], "rubis", rotation=[0, 45, 0], origine=[0, 30, 0]),
])
bras_d = groupe("bras_droit", [6, 21, 0], [cube("bras_droit", [5, 9, -2], [9, 22, 2])])
bras_g = groupe("bras_gauche", [-6, 21, 0], [cube("bras_gauche", [-9, 9, -2], [-5, 22, 2])])
corps = groupe("corps", [0, 11, 0], [
    cube("corps", [-5, 10, -3], [5, 22, 3]),
    cube("eclat_dos", [-1, 14, 3], [2, 20, 5], "rubis", rotation=[20, 0, 15], origine=[0, 17, 3]),
    tete, bras_d, bras_g])
jambe_d = groupe("jambe_droite", [2.5, 10, 0], [cube("jambe_droite", [0.5, 0, -2], [4.5, 10, 2], "sombre")])
jambe_g = groupe("jambe_gauche", [-2.5, 10, 0], [cube("jambe_gauche", [-4.5, 0, -2], [-0.5, 10, 2], "sombre")])
racine = groupe("racine", [0, 0, 0], [corps, jambe_d, jambe_g])

# ---------------------------------------------------------------- animations
def cle(canal, t, x, y, z, interp="linear"):
    return {"channel": canal, "data_points": [{"x": x, "y": y, "z": z}], "uuid": uid(), "time": t, "color": -1, "interpolation": interp}

def anim(nom, duree, boucle, pistes):
    animateurs = {}
    for g, cles in pistes:
        animateurs[g["uuid"]] = {"name": g["name"], "type": "bone", "keyframes": cles}
    return {"uuid": uid(), "name": nom, "loop": boucle, "override": False, "length": duree, "snapping": 20,
            "selected": False, "anim_time_update": "", "blend_weight": "", "start_delay": "", "loop_delay": "", "animators": animateurs}

animations = [
    anim("repos", 2.0, "loop", [
        (corps, [cle("rotation", 0, 0, 0, 0, "catmullrom"), cle("rotation", 1, 3, 0, 0, "catmullrom"), cle("rotation", 2, 0, 0, 0, "catmullrom")]),
        (tete, [cle("rotation", 0, 0, 0, 0, "catmullrom"), cle("rotation", 0.5, 0, 10, 0, "catmullrom"), cle("rotation", 1.5, 0, -10, 0, "catmullrom"), cle("rotation", 2, 0, 0, 0, "catmullrom")]),
        (bras_d, [cle("rotation", 0, 0, 0, 3, "catmullrom"), cle("rotation", 1, -5, 0, 6, "catmullrom"), cle("rotation", 2, 0, 0, 3, "catmullrom")]),
        (bras_g, [cle("rotation", 0, 0, 0, -3, "catmullrom"), cle("rotation", 1, -5, 0, -6, "catmullrom"), cle("rotation", 2, 0, 0, -3, "catmullrom")]),
    ]),
    anim("marche", 1.0, "loop", [
        (jambe_d, [cle("rotation", 0, 25, 0, 0), cle("rotation", 0.5, -25, 0, 0), cle("rotation", 1, 25, 0, 0)]),
        (jambe_g, [cle("rotation", 0, -25, 0, 0), cle("rotation", 0.5, 25, 0, 0), cle("rotation", 1, -25, 0, 0)]),
        (bras_d, [cle("rotation", 0, -20, 0, 0), cle("rotation", 0.5, 20, 0, 0), cle("rotation", 1, -20, 0, 0)]),
        (bras_g, [cle("rotation", 0, 20, 0, 0), cle("rotation", 0.5, -20, 0, 0), cle("rotation", 1, 20, 0, 0)]),
        (corps, [cle("position", 0, 0, 0, 0), cle("position", 0.25, 0, 1, 0), cle("position", 0.5, 0, 0, 0), cle("position", 0.75, 0, 1, 0), cle("position", 1, 0, 0, 0)]),
    ]),
    anim("attaque", 0.6, "once", [
        (bras_d, [cle("rotation", 0, 0, 0, 0), cle("rotation", 0.2, -110, 0, 0), cle("rotation", 0.35, -60, 0, 0), cle("rotation", 0.6, 0, 0, 0)]),
        (bras_g, [cle("rotation", 0, 0, 0, 0), cle("rotation", 0.2, -110, 0, 0), cle("rotation", 0.35, -60, 0, 0), cle("rotation", 0.6, 0, 0, 0)]),
        (corps, [cle("rotation", 0, 0, 0, 0), cle("rotation", 0.2, 12, 0, 0), cle("rotation", 0.6, 0, 0, 0)]),
    ]),
    anim("salut", 1.6, "once", [
        (bras_d, [cle("rotation", 0, 0, 0, 0), cle("rotation", 0.3, 0, 0, 150), cle("rotation", 0.55, 0, 0, 120), cle("rotation", 0.8, 0, 0, 150),
                 cle("rotation", 1.05, 0, 0, 120), cle("rotation", 1.3, 0, 0, 150), cle("rotation", 1.6, 0, 0, 0)]),
        (tete, [cle("rotation", 0, 0, 0, 0), cle("rotation", 0.3, 0, 0, -8), cle("rotation", 1.3, 0, 0, -8), cle("rotation", 1.6, 0, 0, 0)]),
    ]),
    anim("degat", 0.4, "once", [
        (corps, [cle("rotation", 0, 0, 0, 0), cle("rotation", 0.1, 15, 0, 0), cle("rotation", 0.4, 0, 0, 0)]),
    ]),
    anim("mort", 1.2, "hold", [
        (racine, [cle("rotation", 0, 0, 0, 0), cle("rotation", 0.8, 90, 0, 0, "catmullrom"), cle("rotation", 1.2, 90, 0, 0)]),
        (tete, [cle("rotation", 0, 0, 0, 0), cle("rotation", 0.8, 0, 0, 20)]),
    ]),
]

projet = {
    "meta": {"format_version": "4.10", "model_format": "free", "box_uv": False},
    "name": "golem_rubis", "model_identifier": "", "visible_box": [1, 1, 0], "variable_placeholders": "", "variable_placeholder_buttons": [],
    "timeline_setups": [], "unhandled_root_fields": {},
    "resolution": {"width": 64, "height": 64},
    "elements": elements,
    "outliner": [racine],
    "textures": [{"path": "", "name": "golem_rubis.png", "folder": "", "namespace": "", "id": "0", "width": 64, "height": 64,
                  "uv_width": 64, "uv_height": 64, "particle": False, "use_as_default": False, "layers_enabled": False, "sync_to_project": "",
                  "render_mode": "default", "render_sides": "auto", "frame_time": 1, "frame_order_type": "loop", "frame_order": "",
                  "frame_interpolate": False, "visible": True, "internal": True, "saved": False, "uuid": uid(), "source": SOURCE}],
    "animations": animations,
}
with open(OUT, "w") as f:
    json.dump(projet, f, indent=1)
print("ok", OUT)
