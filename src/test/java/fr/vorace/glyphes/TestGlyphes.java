package fr.vorace.glyphes;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.vorace.pack.Journal;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Teste la police des glyphes, les décalages et le calcul de la largeur des images. */
public final class TestGlyphes {
    static int echecs = 0;
    static void verifier(boolean ok, String quoi) { System.out.println((ok ? "OK    " : "ECHEC ") + quoi); if (!ok) echecs++; }

    public static void main(String[] a) throws Exception {
        // décalages : somme exacte, peu de caractères
        Map<String, Integer> avances = new java.util.HashMap<>();
        Path r = Files.createTempDirectory("vorace-glyphes");
        for (String t : List.of("coeur", "etoile", "sourire", "feu", "fond_coffre")) copier("exemples/glyphes/" + t + ".png", r.resolve("textures/exemple/glyphes/" + t + ".png"));
        Journal j = new Journal();
        Map<String, byte[]> pack = GenerateurGlyphes.generer(List.of(
            new GenerateurGlyphes.Glyphe("coeur", 0xEA00, "exemple/glyphes/coeur", 9, 8),
            new GenerateurGlyphes.Glyphe("fond_coffre", 0xEA01, "exemple/glyphes/fond_coffre", 168, 13),
            new GenerateurGlyphes.Glyphe("trop_haut", 0xEA02, "exemple/glyphes/feu", 9, 12),
            new GenerateurGlyphes.Glyphe("absent", 0xEA03, "exemple/glyphes/absent", 9, 8)), r, j);
        JsonArray f = JsonParser.parseString(new String(pack.get("assets/minecraft/font/default.json"), StandardCharsets.UTF_8))
            .getAsJsonObject().getAsJsonArray("providers");
        JsonObject espaces = f.get(0).getAsJsonObject();
        verifier(espaces.get("type").getAsString().equals("space") && espaces.getAsJsonObject("advances").size() == 18, "18 espaces (-256 à +256)");
        espaces.getAsJsonObject("advances").entrySet().forEach(e -> avances.put(e.getKey(), e.getValue().getAsInt()));
        boolean sommes = true;
        for (int n : new int[]{-1, -8, -169, -300, 0, 5, 177, 600}) {
            String d = GenerateurGlyphes.decalage(n);
            int s = 0;
            for (int i = 0; i < d.length(); i++) s += avances.getOrDefault(String.valueOf(d.charAt(i)), 9999);
            if (s != n) { sommes = false; System.out.println("  décalage " + n + " donne " + s); }
        }
        verifier(sommes, "les décalages tombent juste");
        verifier(GenerateurGlyphes.decalage(-169).length() == 4, "décalage -169 en 4 caractères (128 + 32 + 8 + 1)");

        verifier(f.size() == 3, "2 glyphes valides dans la police (plus les espaces)");
        JsonObject coeur = f.get(1).getAsJsonObject();
        verifier(coeur.get("type").getAsString().equals("bitmap") && coeur.get("file").getAsString().equals("vorace:item/exemple/glyphes/coeur.png")
            && coeur.get("height").getAsInt() == 9 && coeur.get("ascent").getAsInt() == 8
            && coeur.getAsJsonArray("chars").get(0).getAsString().equals(""), "glyphe coeur : image, hauteur, montée, caractère");
        verifier(pack.containsKey("assets/vorace/textures/item/exemple/glyphes/coeur.png"), "image copiée dans le pack");
        verifier(j.compter(Journal.Niveau.ERREUR) == 2, "montée trop grande et image absente signalées");
        verifier(GenerateurGlyphes.generer(List.of(), r, new Journal()).containsKey("assets/minecraft/font/default.json"), "décalages présents même sans glyphe");

        // largeur à l'écran (pour revenir au bon endroit après un fond de menu)
        Path fond = r.resolve("textures/exemple/glyphes/fond_coffre.png");
        int[] t = GenerateurGlyphes.taille(fond);
        verifier(t != null && t[0] == 176 && t[1] == 168, "fond de coffre 176 x 168");
        verifier(GenerateurGlyphes.avance(fond, 168) == 177, "avance du fond : 176 + 1");
        verifier(GenerateurGlyphes.avance(r.resolve("textures/exemple/glyphes/coeur.png"), 9) == 10, "avance du coeur : 9 + 1");
        verifier(GenerateurGlyphes.avance(r.resolve("textures/exemple/glyphes/coeur.png"), 18) == 19, "image agrandie : 18 + 1");
        verifier(GenerateurGlyphes.avance(r.resolve("rien.png"), 9) == 0 && GenerateurGlyphes.taille(r.resolve("rien.png")) == null, "image absente");

        // fonds des menus de Vorace : taille du coffre, cases opaques, coins arrondis, avance exacte
        Path tx = Files.createTempDirectory("vorace-fonds");
        fr.vorace.interfaces.FondsMenus.installer(tx);
        boolean tailles = true, opaque = true, coins = true, avance = true;
        for (int n = 1; n <= 6; n++) {
            Path png = tx.resolve(fr.vorace.interfaces.FondsMenus.texture(n) + ".png");
            int[] tf = GenerateurGlyphes.taille(png);
            if (tf == null || tf[0] != 176 || tf[1] != 114 + n * 18) tailles = false;
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(png.toFile());
            for (int y = 3; y < img.getHeight() - 3; y++) for (int x = 3; x < 173; x++) if ((img.getRGB(x, y) >>> 24) != 255) opaque = false;
            if ((img.getRGB(0, 0) >>> 24) != 0 || (img.getRGB(175, img.getHeight() - 1) >>> 24) != 0) coins = false;
            if (GenerateurGlyphes.avance(png, tf == null ? 0 : tf[1]) != 177) avance = false;
        }
        verifier(tailles, "fonds des menus : 176 × (114 + lignes × 18)");
        verifier(opaque, "fonds des menus : opaques (le coffre du jeu ne se voit pas)");
        verifier(coins, "fonds des menus : coins arrondis transparents");
        verifier(avance, "fonds des menus : avance 177 (titre replacé à x = 8)");
        long avantDate = Files.getLastModifiedTime(tx.resolve(fr.vorace.interfaces.FondsMenus.texture(3) + ".png")).toMillis();
        Thread.sleep(20);
        fr.vorace.interfaces.FondsMenus.installer(tx);
        verifier(Files.getLastModifiedTime(tx.resolve(fr.vorace.interfaces.FondsMenus.texture(3) + ".png")).toMillis() == avantDate,
            "fonds des menus : pas réécrits s'ils n'ont pas changé");

        System.out.println(echecs == 0 ? "TOUS LES TESTS PASSENT" : echecs + " ECHEC(S)");
        System.exit(echecs == 0 ? 0 : 1);
    }

    static void copier(String ressource, Path cible) throws Exception {
        Files.createDirectories(cible.getParent());
        try (InputStream in = TestGlyphes.class.getClassLoader().getResourceAsStream(ressource)) {
            if (in == null) throw new IllegalStateException("ressource absente : " + ressource);
            Files.copy(in, cible);
        }
    }
}
