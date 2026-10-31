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
            if (tex.png() != null) sortie.put("assets/" + GenerateurPack.NS + "/textures/item/anime/" + m.id() + "/" + t + ".png", tex.png());
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
                faces.add(f.getKey(), o);
            }
            e.add("faces", faces);
            elements.add(e);
        }
        j.add("elements", elements);
        return j;
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
