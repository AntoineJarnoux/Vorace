package fr.vorace.modeles;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Vector3f;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Teste la lecture d'un .bbmodel, les modèles générés et le calcul des poses animées. */
public final class TestModeles {
    static int echecs = 0;
    static void verifier(boolean ok, String quoi) { System.out.println((ok ? "OK    " : "ECHEC ") + quoi); if (!ok) echecs++; }
    static boolean proche(double a, double b) { return Math.abs(a - b) < 1e-3; }

    public static void main(String[] args) throws Exception {
        String json;
        try (InputStream in = TestModeles.class.getClassLoader().getResourceAsStream("exemples/modeles/golem_rubis.bbmodel")) {
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        List<String> problemes = new ArrayList<>();
        ModeleBB m = LecteurBBModel.lire("golem_rubis", json, problemes);
        problemes.forEach(p -> System.out.println("  problème : " + p));
        verifier(problemes.isEmpty(), "exemple lu sans problème");
        long visibles = m.parties().stream().filter(ModeleBB.Partie::visible).count();
        verifier(m.parties().size() == 8 && visibles == 7, "8 parties dont 7 visibles (l'éclat tourné sur 2 axes est détaché)");
        verifier(m.animations().keySet().containsAll(List.of("repos", "marche", "attaque", "salut", "degat", "mort")), "6 animations");
        verifier(proche(m.largeur(), 18) && proche(m.hauteur(), 35), "taille du modèle : 18 x 35 pixels");
        for (int i = 0; i < m.parties().size(); i++) {
            ModeleBB.Partie p = m.parties().get(i);
            if (p.parent() >= i) verifier(false, "parent avant l'enfant : " + p.nom());
        }
        ModeleBB.Partie eclat = trouver(m, "eclat_dos");
        verifier(eclat != null && eclat.uuid() == null && proche(eclat.rotation()[0], 20) && proche(eclat.rotation()[2], 15)
            && m.parties().get(eclat.parent()).nom().equals("corps"), "éclat détaché : rotation libre gardée, rattaché au corps");

        // pack
        Map<String, byte[]> pack = GenerateurModeles.generer(m);
        verifier(pack.containsKey("assets/vorace/textures/item/anime/golem_rubis/0.png"), "texture extraite du .bbmodel");
        int tete = m.parties().indexOf(trouver(m, "tete"));
        JsonObject mt = JsonParser.parseString(new String(pack.get("assets/vorace/models/anime/golem_rubis/p" + tete + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray els = mt.getAsJsonArray("elements");
        JsonObject cube = els.get(0).getAsJsonObject();
        verifier(cube.getAsJsonArray("from").get(1).getAsDouble() == 8 && cube.getAsJsonArray("to").get(1).getAsDouble() == 16, "tête centrée sur son pivot (8 + décalage)");
        JsonArray uv = cube.getAsJsonObject("faces").getAsJsonObject("north").getAsJsonArray("uv");
        verifier(uv.get(0).getAsDouble() == 4 && uv.get(2).getAsDouble() == 6, "UV ramenées de 64 à 16 pixels");
        JsonObject cristal = els.get(1).getAsJsonObject().getAsJsonObject("rotation");
        verifier(cristal != null && cristal.get("axis").getAsString().equals("y") && cristal.get("angle").getAsDouble() == 45, "cristal tourné de 45° gardé dans le modèle");
        verifier(pack.keySet().stream().filter(k -> k.contains("/models/")).count() == 7, "un modèle par partie visible");
        verifier(GenerateurModeles.idPartie("golem_rubis", 3).equals("anime/golem_rubis/p3"), "identifiant d'une partie");

        // poses
        Animateur.Pose[] repos = Animateur.poses(m, null, 0, 0f, 1f);
        verifier(repos[0] == null, "partie racine (sans cube) non affichée");
        verifier(proche(repos[tete].translation().y, 22 / 16.0) && proche(repos[tete].translation().x, 0), "tête à 22 pixels de haut");
        Vector3f avant = new Vector3f(0, 0, -1).rotate(repos[tete].rotation());
        verifier(proche(avant.z, 1), "yaw 0 : l'avant du modèle (nord) regarde le sud comme l'entité");
        Vector3f avant90 = new Vector3f(0, 0, -1).rotate(Animateur.poses(m, null, 0, 90f, 1f)[tete].rotation());
        verifier(proche(avant90.x, -1), "yaw 90 : le modèle regarde l'ouest");
        int jambe = m.parties().indexOf(trouver(m, "jambe_droite"));
        Animateur.Pose pj = Animateur.poses(m, m.animations().get("marche"), 0, 0f, 1f)[jambe];
        Vector3f pied = new Vector3f(0, -10, 0).rotate(pj.rotation()).div(16).add(pj.translation());
        verifier(proche(pied.y, (10 - 10 * Math.cos(Math.toRadians(25))) / 16) && proche(Math.abs(pied.z), 10 * Math.sin(Math.toRadians(25)) / 16), "marche : jambe tournée de 25°");
        Animateur.Pose pc = Animateur.poses(m, m.animations().get("marche"), 0.25, 0f, 1f)[m.parties().indexOf(trouver(m, "corps"))];
        verifier(proche(pc.translation().y, 12 / 16.0), "marche : le corps monte d'un pixel");
        verifier(proche(Animateur.poses(m, null, 0, 0f, 2f)[tete].echelle().x, 2), "modèle agrandi");
        verifier(proche(Animateur.temps(m.animations().get("marche"), 2.25), 0.25) && proche(Animateur.temps(m.animations().get("attaque"), 5), 0.6), "boucle et fin d'animation");
        verifier(Animateur.finie(m.animations().get("attaque"), 0.7) && !Animateur.finie(m.animations().get("marche"), 9), "animation unique finie");

        // interpolation
        List<ModeleBB.Cle> cles = List.of(new ModeleBB.Cle(0, 0, 0, 0, "linear"), new ModeleBB.Cle(1, 10, 0, 0, "step"), new ModeleBB.Cle(2, 20, 0, 0, "linear"));
        verifier(proche(Animateur.echantillon(cles, 0.5, 0)[0], 5) && proche(Animateur.echantillon(cles, 1.5, 0)[0], 10) && proche(Animateur.echantillon(cles, 3, 0)[0], 20), "linéaire, escalier, après la fin");
        List<ModeleBB.Cle> cr = List.of(new ModeleBB.Cle(0, 0, 0, 0, "catmullrom"), new ModeleBB.Cle(1, 10, 0, 0, "catmullrom"), new ModeleBB.Cle(2, 0, 0, 0, "catmullrom"));
        verifier(Animateur.echantillon(cr, 0.75, 0)[0] > 7.5, "Catmull-Rom arrondit la courbe");

        // grand modèle réduit pour tenir dans un modèle d'objet
        ModeleBB.Partie grande = new ModeleBB.Partie("g", "u", -1, new double[3], new double[3],
            List.of(new ModeleBB.Cube(new double[]{-30, 0, -10}, new double[]{30, 60, 10}, Map.of(), null, 0, new double[3])), 1);
        verifier(proche(LecteurBBModel.echelle(grande), 2.5), "partie de 60 pixels réduite de 2,5 fois");

        System.out.println(echecs == 0 ? "TOUS LES TESTS PASSENT" : echecs + " ECHEC(S)");
        System.exit(echecs == 0 ? 0 : 1);
    }

    static ModeleBB.Partie trouver(ModeleBB m, String nom) {
        for (ModeleBB.Partie p : m.parties()) if (p.nom().equals(nom)) return p;
        return null;
    }
}
