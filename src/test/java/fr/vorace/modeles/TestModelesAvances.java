package fr.vorace.modeles;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.vorace.objets.GenerateurPack;
import fr.vorace.objets.Visuel;
import fr.vorace.pack.FormatPack;
import fr.vorace.pack.Journal;
import org.joml.Vector3f;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Améliorations des modèles animés : Molang, textures animées (.mcmeta), teinte des parties, couches d'animation
 * et fondus, os de tête, os de nom, clés d'effet.
 */
public final class TestModelesAvances {
    static int echecs = 0;
    static void verifier(boolean ok, String quoi) { System.out.println((ok ? "OK    " : "ECHEC ") + quoi); if (!ok) echecs++; }
    static boolean proche(double a, double b) { return Math.abs(a - b) < 1e-3; }

    public static void main(String[] args) throws Exception {
        // ---------------------------------------------------------------- Molang
        verifier(proche(Molang.compiler("1 + 2 * 3").evaluer(0), 7) && Molang.compiler("1 + 2 * 3").constant(), "Molang : priorités, expression constante");
        verifier(proche(Molang.compiler("math.sin(90) * 10").evaluer(0), 10), "Molang : math.sin en degrés");
        verifier(proche(Molang.compiler("math.cos(query.anim_time * 360) * 5").evaluer(0.5), -5) && !Molang.compiler("q.anim_time").constant(),
            "Molang : temps de l'animation");
        verifier(proche(Molang.compiler("-(2 - 5) / 2").evaluer(0), 1.5) && proche(Molang.compiler("math.clamp(12, 0, 10)").evaluer(0), 10)
            && proche(Molang.compiler("math.lerp(0, 10, 0.25)").evaluer(0), 2.5), "Molang : moins, parenthèses, clamp, lerp");
        verifier(proche(Molang.compiler("q.anim_time > 1 ? 4 : 8").evaluer(2), 4) && proche(Molang.compiler("variable.inconnue + 3").evaluer(0), 3),
            "Molang : condition, variable inconnue = 0");
        boolean refuse = false;
        try { Molang.compiler("math.sin(("); } catch (IllegalArgumentException e) { refuse = true; }
        verifier(refuse, "Molang : expression incomplète refusée");

        // ---------------------------------------------------------------- modèle d'essai
        List<String> problemes = new ArrayList<>();
        ModeleBB m = LecteurBBModel.lire("essai", modeleEssai(), problemes);
        problemes.forEach(p -> System.out.println("  problème : " + p));
        verifier(problemes.isEmpty(), "modèle d'essai lu sans problème (Molang compris)");
        int corps = index(m, "corps"), tete = index(m, "h_tete"), oreille = index(m, "h_oreille"), tag = index(m, "tag_nom");
        verifier(m.parties().get(tete).tete() && m.parties().get(oreille).tete() && !m.parties().get(corps).tete(), "os de tête reconnus (h_)");
        verifier(m.parties().get(tag).etiquette() && !m.parties().get(tag).visible(), "os de nom reconnu (tag_), sans cube");
        boolean[] tetes = Animateur.tetes(m);
        verifier(tetes[tete] && !tetes[oreille], "seul l'os de tête le plus haut tourne (l'oreille suit la tête)");

        // texture animée
        ModeleBB.Texture tx = m.textures().get(0);
        verifier(tx.imageTics() == 3 && tx.interpolee(), "texture : frame_time et frame_interpolate lus");
        Map<String, byte[]> pack = GenerateurModeles.generer(m);
        byte[] meta = pack.get("assets/vorace/textures/item/anime/essai/0.png.mcmeta");
        JsonObject anim = meta == null ? null : JsonParser.parseString(new String(meta, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("animation");
        verifier(anim != null && anim.get("frametime").getAsInt() == 3 && anim.get("interpolate").getAsBoolean() && !anim.has("height"),
            "texture animée : .mcmeta écrit (4 images de 16 x 16)");
        verifier(GenerateurModeles.animation(new ModeleBB.Texture("fixe", png(16, 16), 16, 16)) == null, "image fixe : pas de .mcmeta");
        JsonObject large = GenerateurModeles.animation(new ModeleBB.Texture("l", png(32, 32), 32, 16, 2, false));
        verifier(large != null && large.getAsJsonObject("animation").get("height").getAsInt() == 16
            && large.getAsJsonObject("animation").get("width").getAsInt() == 32, "texture animée non carrée : largeur et hauteur des images");
        verifier(GenerateurModeles.animation(new ModeleBB.Texture("x", png(16, 24), 16, 16)) == null, "hauteur qui n'est pas un multiple : image fixe");
        JsonObject mt = JsonParser.parseString(new String(pack.get("assets/vorace/models/anime/essai/p" + corps + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject face = mt.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonObject("faces").getAsJsonObject("north");
        verifier(face.get("tintindex").getAsInt() == 0, "faces des parties teintables (tintindex 0)");
        JsonArray uv = face.getAsJsonArray("uv");
        verifier(uv.get(3).getAsDouble() == 16, "UV d'une texture animée : sur une seule image");

        // définition d'objet 1.21.4 : teinte custom_model_data blanche par défaut
        Path vide = Files.createTempDirectory("vorace-test");
        Map<String, byte[]> defs = GenerateurPack.generer(List.of(new Visuel("anime/essai/p1", "paper", null, null, false, 1001, null, "vorace:anime/essai/p1")),
            vide, new FormatPack(46, 0, "1.21.4"), new Journal());
        JsonObject def = JsonParser.parseString(new String(defs.get("assets/vorace/items/anime/essai/p1.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject tint = def.getAsJsonObject("model").getAsJsonArray("tints").get(0).getAsJsonObject();
        verifier(tint.get("type").getAsString().equals("minecraft:custom_model_data") && tint.get("default").getAsInt() == -1,
            "items/*.json : teinte custom_model_data, blanche par défaut");

        // ---------------------------------------------------------------- Molang dans une animation
        ModeleBB.Animation balance = m.animations().get("balance");
        Animateur.Pose b0 = Animateur.poses(m, balance, 0.25, 0f, 1f)[corps];
        Vector3f haut = new Vector3f(0, 1, 0).rotate(b0.rotation());
        verifier(proche(Math.toDegrees(Math.asin(Math.abs(haut.z))), 20), "clé Molang : rotation calculée au temps de l'animation (20° à 0,25 s)");

        // ---------------------------------------------------------------- couches et fondu
        ModeleBB.Animation marche = m.animations().get("marche"), attaque = m.animations().get("attaque");
        Animateur.Pose base = Animateur.poses(m, null, 0, 0f, 1f)[corps];
        Animateur.Pose pm = Animateur.poses(m, marche, 0, 0f, 1f)[corps];
        Animateur.Pose moitie = Animateur.poses(m, List.of(new Animateur.Couche(null, 0, 1f, false), new Animateur.Couche(marche, 0, 0.5f, false)), 0f, 1f, 0f, 0f)[corps];
        verifier(proche(moitie.translation().y, (base.translation().y + pm.translation().y) / 2), "fondu à 50 % : à mi-chemin entre la pose de base et la marche");
        Animateur.Pose[] dessus = Animateur.poses(m, List.of(new Animateur.Couche(marche, 0, 1f, false), new Animateur.Couche(attaque, 0.1, 1f, true)), 0f, 1f, 0f, 0f);
        verifier(proche(dessus[corps].translation().y, pm.translation().y), "couche par-dessus : le corps garde la marche (non animé par l'attaque)");
        Animateur.Pose teteAttaque = Animateur.poses(m, attaque, 0.1, 0f, 1f)[tete];
        verifier(proche(dessus[tete].rotation().x, teteAttaque.rotation().x) && proche(dessus[tete].rotation().w, teteAttaque.rotation().w),
            "couche par-dessus : la tête prend l'attaque");
        Animateur.Pose[] complete = Animateur.poses(m, List.of(new Animateur.Couche(marche, 0, 1f, false), new Animateur.Couche(attaque, 0.1, 1f, false)), 0f, 1f, 0f, 0f);
        verifier(proche(complete[corps].translation().y, base.translation().y), "couche complète : les os non animés reviennent à la pose de base");

        // ---------------------------------------------------------------- regard
        Animateur.Pose droit = Animateur.poses(m, List.of(), 0f, 1f, 0f, 0f)[tete];
        Animateur.Pose tourne = Animateur.poses(m, List.of(), 0f, 1f, 90f, 0f)[tete];
        Vector3f avant = new Vector3f(0, 0, -1).rotate(droit.rotation()), avant90 = new Vector3f(0, 0, -1).rotate(tourne.rotation());
        verifier(proche(avant.z, 1) && proche(avant90.x, -1), "regard : tête tournée de 90° vers l'ouest comme un yaw de 90°");
        Vector3f bas = new Vector3f(0, 0, -1).rotate(Animateur.poses(m, List.of(), 0f, 1f, 0f, 30f)[tete].rotation());
        verifier(proche(bas.y, -0.5), "regard : pitch 30 = tête penchée vers le bas");
        Animateur.Pose corpsTourne = Animateur.poses(m, List.of(), 0f, 1f, 90f, 0f)[corps];
        verifier(proche(corpsTourne.rotation().w, Animateur.poses(m, List.of(), 0f, 1f, 0f, 0f)[corps].rotation().w), "regard : le corps ne tourne pas");
        Animateur.Pose oreilleT = Animateur.poses(m, List.of(), 0f, 1f, 90f, 0f)[oreille];
        Vector3f avantOreille = new Vector3f(0, 0, -1).rotate(oreilleT.rotation());
        verifier(proche(avantOreille.x, -1), "regard : l'oreille (dans la tête) suit sans tourner deux fois");

        // ---------------------------------------------------------------- os de nom
        Animateur.Pose[] toutes = Animateur.posesCompletes(m, List.of(), 0f, 1f, 0f, 0f);
        verifier(Animateur.poses(m, null, 0, 0f, 1f)[tag] == null && toutes[tag] != null && proche(toutes[tag].translation().y, 30 / 16.0),
            "os de nom : position donnée par posesCompletes (30 pixels)");
        verifier(proche(Animateur.posesCompletes(m, List.of(), 0f, 2f, 0f, 0f)[tag].translation().y, 60 / 16.0), "taille x2 : os de nom deux fois plus haut");

        // ---------------------------------------------------------------- clés d'effet
        verifier(attaque.evenements().size() == 3, "clés d'effet lues (son, particule, script)");
        verifier(Animateur.evenements(attaque, -1e-9, 0.05, false).size() == 1
            && Animateur.evenements(attaque, -1e-9, 0.05, false).get(0).valeur().equals("entity.player.attack.sweep"), "effet au temps 0 joué dès le début");
        verifier(Animateur.evenements(attaque, 0.05, 0.3, false).stream().map(ModeleBB.Evenement::type).toList().equals(List.of("particle", "timeline")),
            "particule puis script entre 0,05 et 0,3 s");
        verifier(Animateur.evenements(attaque, 0.3, 5, false).isEmpty(), "animation unique : pas de répétition");
        List<ModeleBB.Evenement> tour = Animateur.evenements(attaque, 0.3, 1.05, true);
        verifier(tour.size() == 4 && tour.stream().filter(e -> e.type().equals("sound")).count() == 2, "boucle (0,5 s) : son à 0,5 et 1,0 s, particule et script une fois");
        verifier(Animateur.evenements(attaque, 0, 1000, true).size() <= 5 * 3, "boucle après un long arrêt : pas d'avalanche d'effets");

        // ---------------------------------------------------------------- l'exemple du plugin n'a pas bougé
        String json;
        try (InputStream in = TestModelesAvances.class.getClassLoader().getResourceAsStream("exemples/modeles/golem_rubis.bbmodel")) {
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        ModeleBB golem = LecteurBBModel.lire("golem_rubis", json, new ArrayList<>());
        Animateur.Pose[] ancien = Animateur.poses(golem, golem.animations().get("marche"), 0.3, 45f, 1f);
        Animateur.Pose[] nouveau = Animateur.poses(golem, List.of(new Animateur.Couche(golem.animations().get("marche"), 0.3, 1f, false)), 45f, 1f, 0f, 0f);
        boolean pareil = true;
        for (int i = 0; i < ancien.length; i++) {
            if ((ancien[i] == null) != (nouveau[i] == null)) pareil = false;
            else if (ancien[i] != null && (!ancien[i].translation().equals(nouveau[i].translation(), 1e-5f) || !ancien[i].rotation().equals(nouveau[i].rotation(), 1e-5f))) pareil = false;
        }
        verifier(pareil, "une seule couche à 100 % = même résultat qu'avant");
        verifier(GenerateurModeles.generer(golem).keySet().stream().noneMatch(k -> k.endsWith(".mcmeta")), "golem : texture fixe, pas de .mcmeta");

        System.out.println(echecs == 0 ? "TOUS LES TESTS PASSENT" : echecs + " ECHEC(S)");
        System.exit(echecs == 0 ? 0 : 1);
    }

    static int index(ModeleBB m, String nom) {
        for (int i = 0; i < m.parties().size(); i++) if (m.parties().get(i).nom().equals(nom)) return i;
        throw new IllegalStateException("os " + nom);
    }

    static byte[] png(int l, int h) throws Exception {
        BufferedImage im = new BufferedImage(l, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < l; x++) im.setRGB(x, y, 0xFF000000 | (y * 16 << 8) | x * 8);
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ImageIO.write(im, "png", o);
        return o.toByteArray();
    }

    /**
     * Petit modèle : un corps (pivot 0,8,0), une tête « h_tete » avec une oreille « h_oreille », un os de nom
     * « tag_nom » à 30 pixels ; texture de 16 x 64 (4 images) ; animations marche, attaque (avec effets), balance (Molang).
     */
    static String modeleEssai() throws Exception {
        JsonObject r = new JsonObject();
        JsonObject meta = new JsonObject();
        meta.addProperty("model_format", "free");
        r.add("meta", meta);
        JsonObject res = new JsonObject();
        res.addProperty("width", 16);
        res.addProperty("height", 16);
        r.add("resolution", res);
        JsonArray tex = new JsonArray();
        JsonObject t = new JsonObject();
        t.addProperty("name", "peau");
        t.addProperty("source", "data:image/png;base64," + Base64.getEncoder().encodeToString(png(16, 64)));
        t.addProperty("uv_width", 16);
        t.addProperty("uv_height", 16);
        t.addProperty("frame_time", 3);
        t.addProperty("frame_interpolate", true);
        tex.add(t);
        r.add("textures", tex);
        JsonArray els = new JsonArray();
        els.add(cube("c1", -4, 4, -3, 4, 12, 3));
        els.add(cube("c2", -3, 12, -3, 3, 18, 3));
        els.add(cube("c3", 2, 18, -1, 3, 21, 0));
        r.add("elements", els);
        JsonArray out = new JsonArray();
        JsonObject corps = os("corps", "u-corps", 0, 8, 0);
        corps.getAsJsonArray("children").add("c1");
        JsonObject tete = os("h_tete", "u-tete", 0, 12, 0);
        tete.getAsJsonArray("children").add("c2");
        JsonObject oreille = os("h_oreille", "u-oreille", 2, 18, 0);
        oreille.getAsJsonArray("children").add("c3");
        tete.getAsJsonArray("children").add(oreille);
        corps.getAsJsonArray("children").add(tete);
        JsonObject tag = os("tag_nom", "u-tag", 0, 30, 0);
        corps.getAsJsonArray("children").add(tag);
        out.add(corps);
        r.add("outliner", out);
        JsonArray anims = new JsonArray();
        anims.add(animation("marche", 1, "loop", "u-corps", "position", 0, 0, 2, 0));
        JsonObject att = animation("attaque", 0.5, "once", "u-tete", "rotation", 0, -40, 0, 0);
        JsonObject eff = new JsonObject();
        eff.addProperty("type", "effect");
        JsonArray kfs = new JsonArray();
        kfs.add(effet("sound", 0, "effect", "entity.player.attack.sweep"));
        kfs.add(effet("particle", 0.1, "effect", "crit"));
        kfs.add(effet("timeline", 0.2, "script", "degats"));
        eff.add("keyframes", kfs);
        att.getAsJsonObject("animators").add("effects", eff);
        anims.add(att);
        JsonObject bal = animation("balance", 1, "loop", "u-corps", "rotation", 0, 0, 0, 0);
        JsonObject pt = bal.getAsJsonObject("animators").getAsJsonObject("u-corps").getAsJsonArray("keyframes").get(0).getAsJsonObject()
            .getAsJsonArray("data_points").get(0).getAsJsonObject();
        pt.addProperty("x", "math.sin(query.anim_time * 360) * 20");
        anims.add(bal);
        r.add("animations", anims);
        return r.toString();
    }

    static JsonObject cube(String uuid, double x1, double y1, double z1, double x2, double y2, double z2) {
        JsonObject c = new JsonObject();
        c.addProperty("uuid", uuid);
        c.addProperty("name", uuid);
        c.addProperty("type", "cube");
        JsonArray de = new JsonArray(); de.add(x1); de.add(y1); de.add(z1);
        JsonArray a = new JsonArray(); a.add(x2); a.add(y2); a.add(z2);
        c.add("from", de);
        c.add("to", a);
        JsonObject faces = new JsonObject();
        for (String f : List.of("north", "south", "east", "west", "up", "down")) {
            JsonObject face = new JsonObject();
            JsonArray uv = new JsonArray(); uv.add(0); uv.add(0); uv.add(4); uv.add(16);
            face.add("uv", uv);
            face.addProperty("texture", 0);
            faces.add(f, face);
        }
        c.add("faces", faces);
        return c;
    }

    static JsonObject os(String nom, String uuid, double x, double y, double z) {
        JsonObject g = new JsonObject();
        g.addProperty("name", nom);
        g.addProperty("uuid", uuid);
        JsonArray o = new JsonArray(); o.add(x); o.add(y); o.add(z);
        g.add("origin", o);
        g.add("children", new JsonArray());
        return g;
    }

    /** Animation à deux clés : 0 à t=0, (x, y, z) à t=duree/2. */
    static JsonObject animation(String nom, double duree, String boucle, String os, String canal, double t0, double x, double y, double z) {
        JsonObject a = new JsonObject();
        a.addProperty("name", nom);
        a.addProperty("length", duree);
        a.addProperty("loop", boucle);
        JsonObject anims = new JsonObject();
        JsonObject an = new JsonObject();
        an.addProperty("type", "bone");
        JsonArray kfs = new JsonArray();
        kfs.add(cle(canal, t0, 0, 0, 0));
        if (x != 0 || y != 0 || z != 0) kfs.add(cle(canal, duree / 2, x, y, z));
        an.add("keyframes", kfs);
        anims.add(os, an);
        a.add("animators", anims);
        return a;
    }

    static JsonObject cle(String canal, double t, double x, double y, double z) {
        JsonObject k = new JsonObject();
        k.addProperty("channel", canal);
        k.addProperty("time", t);
        k.addProperty("interpolation", "linear");
        JsonArray pts = new JsonArray();
        JsonObject p = new JsonObject();
        p.addProperty("x", x); p.addProperty("y", y); p.addProperty("z", z);
        pts.add(p);
        k.add("data_points", pts);
        return k;
    }

    static JsonObject effet(String canal, double t, String champ, String valeur) {
        JsonObject k = new JsonObject();
        k.addProperty("channel", canal);
        k.addProperty("time", t);
        JsonArray pts = new JsonArray();
        JsonObject p = new JsonObject();
        p.addProperty(champ, valeur);
        pts.add(p);
        k.add("data_points", pts);
        return k;
    }
}
