package fr.vorace.modeles;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.vorace.modeles.ModeleBB.Animation;
import fr.vorace.modeles.ModeleBB.Canaux;
import fr.vorace.modeles.ModeleBB.Cle;
import fr.vorace.modeles.ModeleBB.Cube;
import fr.vorace.modeles.ModeleBB.Face;
import fr.vorace.modeles.ModeleBB.Partie;
import fr.vorace.modeles.ModeleBB.Texture;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Lit un fichier .bbmodel (projet Blockbench, formats « Modèle générique », Java ou Bedrock). */
public final class LecteurBBModel {

    /** Un modèle d'objet n'accepte que des coordonnées de -16 à 32 : 24 pixels de chaque côté du centre. */
    static final double LIMITE = 24.0;
    private static final List<String> FACES = List.of("north", "south", "east", "west", "up", "down");

    private LecteurBBModel() {}

    public static ModeleBB lire(String id, String json, List<String> problemes) {
        JsonObject racine = JsonParser.parseString(json).getAsJsonObject();
        JsonObject meta = objet(racine, "meta");
        String format = meta == null ? "free" : texte(meta, "model_format", "free");
        // format « bloc Java » : l'origine est le coin du bloc, on la ramène au centre
        double[] decalage = format.startsWith("java") ? new double[]{-8, 0, -8} : new double[]{0, 0, 0};

        JsonObject resolution = objet(racine, "resolution");
        int resL = resolution == null ? 16 : (int) nombre(resolution, "width", 16);
        int resH = resolution == null ? 16 : (int) nombre(resolution, "height", 16);

        // textures
        List<Texture> textures = new ArrayList<>();
        JsonArray tex = tableau(racine, "textures");
        if (tex != null) {
            for (JsonElement e : tex) {
                JsonObject t = e.getAsJsonObject();
                String nom = texte(t, "name", "texture" + textures.size());
                String source = texte(t, "source", "");
                byte[] png = null;
                int virgule = source.indexOf(',');
                if (source.startsWith("data:image/png") && virgule > 0) {
                    try { png = Base64.getDecoder().decode(source.substring(virgule + 1)); } catch (IllegalArgumentException ignore) { }
                }
                if (png == null) problemes.add(id + " : la texture « " + nom + " » n'est pas enregistrée dans le .bbmodel (dans Blockbench : clic droit sur la texture > Enregistrer dans le projet, ou « Fichier > Enregistrer le projet » avec les textures intégrées)");
                textures.add(new Texture(nom, png, (int) nombre(t, "uv_width", resL), (int) nombre(t, "uv_height", resH)));
            }
        }

        // cubes
        Map<String, JsonObject> elements = new HashMap<>();
        JsonArray els = tableau(racine, "elements");
        if (els != null) {
            for (JsonElement e : els) {
                JsonObject c = e.getAsJsonObject();
                String type = texte(c, "type", "cube");
                if (!type.equals("cube")) {
                    if (!type.equals("locator") && !type.equals("null_object")) problemes.add(id + " : élément « " + texte(c, "name", "?") + " » de type " + type + " ignoré (seuls les cubes sont pris en charge)");
                    continue;
                }
                elements.put(texte(c, "uuid", ""), c);
            }
        }

        List<Partie> parties = new ArrayList<>();
        List<Object> hors = new ArrayList<>();
        JsonArray outliner = tableau(racine, "outliner");
        if (outliner != null) {
            for (JsonElement e : outliner) {
                if (e.isJsonObject()) lireOs(e.getAsJsonObject(), -1, new double[3], elements, decalage, textures, parties, problemes, id);
                else hors.add(e.getAsString());
            }
        }
        if (!hors.isEmpty()) {
            // cubes posés hors de tout groupe : dans un os racine invisible
            List<Cube> cubes = new ArrayList<>();
            List<Partie> detaches = new ArrayList<>();
            int index = parties.size();
            for (Object u : hors) {
                JsonObject c = elements.get((String) u);
                if (c != null && visible(c)) ajouterCube(c, decalage, textures, cubes, detaches, index, new double[3], problemes, id);
            }
            parties.add(new Partie("racine", null, -1, new double[3], new double[3], cubes, 1));
            parties.addAll(detaches);
        }
        List<Partie> finales = new ArrayList<>();
        for (Partie p : parties) finales.add(new Partie(p.nom(), p.uuid(), p.parent(), p.pivot(), p.rotation(), p.cubes(), echelle(p)));

        // animations
        Map<String, Animation> animations = new LinkedHashMap<>();
        JsonArray anims = tableau(racine, "animations");
        if (anims != null) {
            for (JsonElement e : anims) {
                JsonObject a = e.getAsJsonObject();
                String nom = texte(a, "name", "animation").toLowerCase(Locale.ROOT);
                if (nom.startsWith("animation.")) nom = nom.substring(nom.lastIndexOf('.') + 1); // « animation.modele.marche »
                Map<String, Canaux> os = new HashMap<>();
                JsonObject animateurs = objet(a, "animators");
                if (animateurs != null) {
                    for (Map.Entry<String, JsonElement> an : animateurs.entrySet()) {
                        JsonObject o = an.getValue().getAsJsonObject();
                        if (!texte(o, "type", "bone").equals("bone")) continue;
                        List<Cle> pos = new ArrayList<>(), rot = new ArrayList<>(), ech = new ArrayList<>();
                        JsonArray cles = tableau(o, "keyframes");
                        if (cles != null) {
                            for (JsonElement k : cles) {
                                JsonObject kf = k.getAsJsonObject();
                                JsonArray pts = tableau(kf, "data_points");
                                if (pts == null || pts.isEmpty()) continue;
                                JsonObject pt = pts.get(0).getAsJsonObject();
                                String canal = texte(kf, "channel", "rotation");
                                double defaut = canal.equals("scale") ? 1 : 0;
                                Cle cle = new Cle(nombre(kf, "time", 0), valeur(pt, "x", defaut, problemes, id, nom), valeur(pt, "y", defaut, problemes, id, nom),
                                    valeur(pt, "z", defaut, problemes, id, nom), texte(kf, "interpolation", "linear"));
                                switch (canal) {
                                    case "position" -> pos.add(cle);
                                    case "scale" -> ech.add(cle);
                                    case "rotation" -> rot.add(cle);
                                    default -> { }
                                }
                            }
                        }
                        for (List<Cle> l : List.of(pos, rot, ech)) l.sort((x, y) -> Double.compare(x.temps(), y.temps()));
                        os.put(an.getKey(), new Canaux(pos, rot, ech));
                    }
                }
                String boucle = texte(a, "loop", "once");
                animations.put(nom, new Animation(nom, nombre(a, "length", 0), boucle, os));
            }
        }

        // taille au sol, pour la hitbox
        double minX = 0, maxX = 0, minY = 0, maxY = 0, minZ = 0, maxZ = 0;
        boolean premier = true;
        for (JsonObject c : elements.values()) {
            double[] de = vecteur(c, "from"), a = vecteur(c, "to");
            for (int i = 0; i < 3; i++) { de[i] += decalage[i]; a[i] += decalage[i]; }
            if (premier) {
                minX = Math.min(de[0], a[0]); maxX = Math.max(de[0], a[0]); minY = Math.min(de[1], a[1]); maxY = Math.max(de[1], a[1]);
                minZ = Math.min(de[2], a[2]); maxZ = Math.max(de[2], a[2]);
                premier = false;
            } else {
                minX = Math.min(minX, Math.min(de[0], a[0])); maxX = Math.max(maxX, Math.max(de[0], a[0]));
                minY = Math.min(minY, Math.min(de[1], a[1])); maxY = Math.max(maxY, Math.max(de[1], a[1]));
                minZ = Math.min(minZ, Math.min(de[2], a[2])); maxZ = Math.max(maxZ, Math.max(de[2], a[2]));
            }
        }
        double largeur = 2 * Math.max(Math.max(Math.abs(minX), Math.abs(maxX)), Math.max(Math.abs(minZ), Math.abs(maxZ)));
        return new ModeleBB(id, finales, textures, animations, largeur, Math.max(0, maxY));
    }

    private static void lireOs(JsonObject g, int parent, double[] pivotParent, Map<String, JsonObject> elements, double[] decalage,
                               List<Texture> textures, List<Partie> parties, List<String> problemes, String id) {
        if (g.has("visibility") && !g.get("visibility").getAsBoolean()) return;
        double[] pivot = vecteur(g, "origin");
        for (int i = 0; i < 3; i++) pivot[i] += decalage[i];
        double[] rotation = g.has("rotation") ? vecteur(g, "rotation") : new double[3];
        int index = parties.size();
        parties.add(null); // place réservée : un parent passe avant ses enfants
        List<Cube> cubes = new ArrayList<>();
        List<Partie> detaches = new ArrayList<>();
        List<JsonObject> sousGroupes = new ArrayList<>();
        JsonArray enfants = tableau(g, "children");
        if (enfants != null) {
            for (JsonElement e : enfants) {
                if (e.isJsonObject()) sousGroupes.add(e.getAsJsonObject());
                else {
                    JsonObject c = elements.get(e.getAsString());
                    if (c != null && visible(c)) ajouterCube(c, decalage, textures, cubes, detaches, index, pivot, problemes, id);
                }
            }
        }
        parties.set(index, new Partie(texte(g, "name", "os"), texte(g, "uuid", null), parent, pivot, rotation, cubes, 1));
        parties.addAll(detaches);
        for (JsonObject s : sousGroupes) lireOs(s, index, pivot, elements, decalage, textures, parties, problemes, id);
    }

    private static boolean visible(JsonObject c) {
        return !c.has("visibility") || c.get("visibility").getAsBoolean();
    }

    /**
     * Ajoute un cube à son os. Un modèle d'objet n'accepte qu'une rotation sur un seul axe, de -45 à 45° par pas de 22.5 :
     * un cube tourné autrement devient une partie à lui seul, tournée par l'entité d'affichage.
     */
    private static void ajouterCube(JsonObject c, double[] decalage, List<Texture> textures, List<Cube> cubes,
                                    List<Partie> detaches, int parent, double[] pivotParent, List<String> problemes, String id) {
        double[] de = vecteur(c, "from"), a = vecteur(c, "to");
        double gonfle = nombre(c, "inflate", 0);
        for (int i = 0; i < 3; i++) {
            double lo = Math.min(de[i], a[i]), hi = Math.max(de[i], a[i]);
            de[i] = lo - gonfle + decalage[i];
            a[i] = hi + gonfle + decalage[i];
        }
        double[] origine = c.has("origin") ? vecteur(c, "origin") : new double[3];
        for (int i = 0; i < 3; i++) origine[i] += decalage[i];
        double[] rot = c.has("rotation") ? vecteur(c, "rotation") : new double[3];

        Map<String, Face> faces = new LinkedHashMap<>();
        JsonObject fs = objet(c, "faces");
        if (fs != null) {
            for (String f : FACES) {
                JsonObject face = objet(fs, f);
                if (face == null || !face.has("texture") || face.get("texture").isJsonNull()) continue;
                int t;
                try { t = face.get("texture").getAsInt(); } catch (RuntimeException e) { continue; }
                if (t < 0 || t >= textures.size()) continue;
                double[] uv = face.has("uv") ? vecteur4(face.getAsJsonArray("uv")) : new double[]{0, 0, 0, 0};
                faces.put(f, new Face(uv, t, (int) nombre(face, "rotation", 0)));
            }
        }
        if (faces.isEmpty()) return;

        int axes = 0;
        String axe = null;
        double angle = 0;
        for (int i = 0; i < 3; i++) {
            if (Math.abs(rot[i]) > 1e-6) { axes++; axe = "xyz".substring(i, i + 1); angle = rot[i]; }
        }
        boolean permis = axes == 0 || (axes == 1 && Math.abs(angle) <= 45 + 1e-6 && Math.abs(angle / 22.5 - Math.round(angle / 22.5)) < 1e-6);
        if (permis) {
            cubes.add(new Cube(de, a, faces, axes == 0 ? null : axe, axes == 0 ? 0 : angle, origine));
        } else {
            detaches.add(new Partie(texte(c, "name", "cube"), null, parent, origine, rot, List.of(new Cube(de, a, faces, null, 0, origine)), 1));
        }
    }

    /** Réduction nécessaire pour que tous les cubes tiennent à moins de 24 pixels du pivot. */
    static double echelle(Partie p) {
        double max = 0;
        for (Cube c : p.cubes()) {
            for (int i = 0; i < 3; i++) {
                max = Math.max(max, Math.abs(c.de()[i] - p.pivot()[i]));
                max = Math.max(max, Math.abs(c.a()[i] - p.pivot()[i]));
                max = Math.max(max, Math.abs(c.origine()[i] - p.pivot()[i]));
            }
        }
        return max <= LIMITE ? 1 : max / LIMITE;
    }

    // ------------------------------------------------------------------ lecture JSON

    private static double valeur(JsonObject pt, String cle, double defaut, List<String> problemes, String id, String anim) {
        if (!pt.has(cle)) return defaut;
        JsonElement e = pt.get(cle);
        try {
            if (e.getAsJsonPrimitive().isNumber()) return e.getAsDouble();
            String t = e.getAsString().trim();
            if (t.isEmpty()) return defaut;
            return Double.parseDouble(t.replace(',', '.'));
        } catch (RuntimeException ex) {
            String msg = id + " : animation « " + anim + " » : expression « " + e + " » non prise en charge (nombres seulement), 0 utilisé";
            if (!problemes.contains(msg)) problemes.add(msg);
            return defaut;
        }
    }

    private static JsonObject objet(JsonObject o, String cle) {
        return o.has(cle) && o.get(cle).isJsonObject() ? o.getAsJsonObject(cle) : null;
    }

    private static JsonArray tableau(JsonObject o, String cle) {
        return o.has(cle) && o.get(cle).isJsonArray() ? o.getAsJsonArray(cle) : null;
    }

    private static String texte(JsonObject o, String cle, String defaut) {
        return o.has(cle) && o.get(cle).isJsonPrimitive() ? o.get(cle).getAsString() : defaut;
    }

    private static double nombre(JsonObject o, String cle, double defaut) {
        try {
            return o.has(cle) && o.get(cle).isJsonPrimitive() ? o.get(cle).getAsDouble() : defaut;
        } catch (RuntimeException e) {
            return defaut;
        }
    }

    private static double[] vecteur(JsonObject o, String cle) {
        double[] v = new double[3];
        JsonArray a = tableau(o, cle);
        if (a != null) for (int i = 0; i < 3 && i < a.size(); i++) v[i] = a.get(i).getAsDouble();
        return v;
    }

    private static double[] vecteur4(JsonArray a) {
        double[] v = new double[4];
        for (int i = 0; i < 4 && i < a.size(); i++) v[i] = a.get(i).getAsDouble();
        return v;
    }
}
