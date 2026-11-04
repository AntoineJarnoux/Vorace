package fr.vorace.modeles;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.vorace.modeles.ModeleBB.Cube;
import fr.vorace.modeles.ModeleBB.Face;
import fr.vorace.modeles.ModeleBB.Partie;
import fr.vorace.objets.GenerateurPack;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

/** Fichiers du pack d'un modèle animé : un modèle d'objet par partie visible, et les textures (sans Bukkit). */
public final class GenerateurModeles {

    private GenerateurModeles() {}

    /** Identifiant de l'objet qui affiche une partie (« anime/golem/p3 »). */
    public static String idPartie(String modele, int index) {
        return "anime/" + modele + "/p" + index;
    }

    /**
     * Modèles des parties (assets/vorace/models/anime/...) et textures. Les textures vont dans textures/item/ :
     * le jeu ne met dans son atlas que les images des dossiers block/ et item/ (sinon : texture manquante).
     */
    public static Map<String, byte[]> generer(ModeleBB m) {
        Map<String, byte[]> sortie = new TreeMap<>();
        for (int t = 0; t < m.textures().size(); t++) {
            ModeleBB.Texture tex = m.textures().get(t);
            if (tex.png() == null) continue;
            String chemin = "assets/" + GenerateurPack.NS + "/textures/item/anime/" + m.id() + "/" + t + ".png";
            sortie.put(chemin, tex.png());
            JsonObject anim = animation(tex);
            if (anim != null) sortie.put(chemin + ".mcmeta", anim.toString().getBytes(StandardCharsets.UTF_8));
        }
        for (int i = 0; i < m.parties().size(); i++) {
            Partie p = m.parties().get(i);
            if (!p.visible()) continue;
            sortie.put("assets/" + GenerateurPack.NS + "/models/" + idPartie(m.id(), i) + ".json",
                modele(m, p).toString().getBytes(StandardCharsets.UTF_8));
        }
        return sortie;
    }

    static JsonObject modele(ModeleBB m, Partie p) {
        JsonObject j = new JsonObject();
        JsonObject textures = new JsonObject();
        for (int t = 0; t < m.textures().size(); t++) textures.addProperty(String.valueOf(t), GenerateurPack.NS + ":item/anime/" + m.id() + "/" + t);
        if (!m.textures().isEmpty()) textures.addProperty("particle", "#0");
        j.add("textures", textures);
        JsonArray elements = new JsonArray();
        double s = p.echelle();
        for (Cube c : p.cubes()) {
            JsonObject e = new JsonObject();
            e.add("from", vec(c.de(), p.pivot(), s));
            e.add("to", vec(c.a(), p.pivot(), s));
            if (c.axe() != null) {
                JsonObject r = new JsonObject();
                r.addProperty("angle", c.angle());
                r.addProperty("axis", c.axe());
                r.add("origin", vec(c.origine(), p.pivot(), s));
                e.add("rotation", r);
            }
            JsonObject faces = new JsonObject();
            for (Map.Entry<String, Face> f : c.faces().entrySet()) {
                Face face = f.getValue();
                ModeleBB.Texture tex = m.textures().get(face.texture());
                double ku = 16.0 / Math.max(1, tex.largeurUV()), kv = 16.0 / Math.max(1, tex.hauteurUV());
                JsonObject o = new JsonObject();
                JsonArray uv = new JsonArray();
                uv.add(arrondi(face.uv()[0] * ku));
                uv.add(arrondi(face.uv()[1] * kv));
                uv.add(arrondi(face.uv()[2] * ku));
                uv.add(arrondi(face.uv()[3] * kv));
                o.add("uv", uv);
                o.addProperty("texture", "#" + face.texture());
                if (face.rotation() != 0) o.addProperty("rotation", face.rotation());
                o.addProperty("tintindex", 0); // teinte rouge des dégâts (couleur du composant custom_model_data)
                faces.add(f.getKey(), o);
            }
            e.add("faces", faces);
            elements.add(e);
        }
        j.add("elements", elements);
        return j;
    }

    /**
     * Texture animée de Blockbench : les images sont empilées en hauteur, chacune a les proportions des UV
     * (uv_width x uv_height). Renvoie le .mcmeta à écrire, ou null pour une image fixe.
     */
    static JsonObject animation(ModeleBB.Texture tex) {
        int[] taille = taillePng(tex.png());
        if (taille == null || tex.largeurUV() <= 0 || tex.hauteurUV() <= 0) return null;
        int largeur = taille[0], hauteur = taille[1];
        double hauteurImage = largeur * (double) tex.hauteurUV() / tex.largeurUV();
        if (hauteurImage < 1) return null;
        double n = hauteur / hauteurImage;
        int images = (int) Math.round(n);
        if (images < 2 || Math.abs(n - images) > 1e-6 || Math.abs(hauteurImage - Math.round(hauteurImage)) > 1e-6) return null;
        JsonObject a = new JsonObject();
        a.addProperty("frametime", Math.max(1, tex.imageTics()));
        if (tex.interpolee()) a.addProperty("interpolate", true);
        if ((int) Math.round(hauteurImage) != largeur) {
            a.addProperty("width", largeur);
            a.addProperty("height", (int) Math.round(hauteurImage));
        }
        JsonObject j = new JsonObject();
        j.add("animation", a);
        return j;
    }

    /** Largeur et hauteur d'une image PNG (en-tête IHDR), ou null. */
    static int[] taillePng(byte[] png) {
        if (png == null || png.length < 24 || (png[0] & 0xFF) != 0x89 || png[1] != 'P' || png[2] != 'N' || png[3] != 'G') return null;
        int l = ((png[16] & 0xFF) << 24) | ((png[17] & 0xFF) << 16) | ((png[18] & 0xFF) << 8) | (png[19] & 0xFF);
        int h = ((png[20] & 0xFF) << 24) | ((png[21] & 0xFF) << 16) | ((png[22] & 0xFF) << 8) | (png[23] & 0xFF);
        return l > 0 && h > 0 ? new int[]{l, h} : null;
    }

    /** Coordonnée dans le modèle d'objet : centrée sur 8, réduite si la partie est trop grande. */
    private static JsonArray vec(double[] v, double[] pivot, double echelle) {
        JsonArray a = new JsonArray();
        for (int i = 0; i < 3; i++) a.add(arrondi((v[i] - pivot[i]) / echelle + 8));
        return a;
    }

    private static double arrondi(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
