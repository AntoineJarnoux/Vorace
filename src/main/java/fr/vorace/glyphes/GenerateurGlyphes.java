package fr.vorace.glyphes;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.vorace.objets.GenerateurPack;
import fr.vorace.pack.Journal;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Police du pack pour les glyphes (images dans le texte) et les décalages (espaces négatifs ou positifs).
 * Tout est ajouté à la police par défaut du jeu, avec des caractères de la zone privée d'Unicode :
 * les glyphes marchent donc partout (chat, noms d'objets, Tab, tableau de scores, titres, menus),
 * même dans les plugins qui n'utilisent que du texte simple.
 */
public final class GenerateurGlyphes {

    /** Premier caractère réservé aux glyphes (U+EA00 à U+F6FF). */
    public static final int PREMIER = 0xEA00;
    public static final int DERNIER = 0xF6FF;
    /** Espaces : U+F701.. pour -1, -2, -4 ... -256 et U+F711.. pour +1, +2 ... +256. */
    private static final int ESPACE_NEG = 0xF701, ESPACE_POS = 0xF711, PUISSANCES = 9;

    /**
     * Un glyphe à mettre dans la police.
     * @param texture chemin dans ressources/textures (sans .png)
     * @param hauteur hauteur affichée en pixels (9 = hauteur d'une lettre)
     * @param montee  pixels au-dessus de la ligne d'écriture (8 = aligné sur les lettres)
     */
    public record Glyphe(String id, int code, String texture, int hauteur, int montee) {}

    private GenerateurGlyphes() {}

    public static Map<String, byte[]> generer(List<Glyphe> glyphes, Path ressources, Journal journal) {
        Map<String, byte[]> sortie = new TreeMap<>();
        Path textures = ressources.resolve("textures");
        JsonArray fournisseurs = new JsonArray();

        JsonObject espaces = new JsonObject();
        espaces.addProperty("type", "space");
        JsonObject avances = new JsonObject();
        for (int i = 0; i < PUISSANCES; i++) {
            avances.addProperty(new String(Character.toChars(ESPACE_NEG + i)), -(1 << i));
            avances.addProperty(new String(Character.toChars(ESPACE_POS + i)), 1 << i);
        }
        espaces.add("advances", avances);
        fournisseurs.add(espaces);

        for (Glyphe g : glyphes) {
            if (g.montee() > g.hauteur()) {
                journal.erreur("Glyphe " + g.id() + " : la montée (" + g.montee() + ") ne peut pas dépasser la hauteur (" + g.hauteur() + ")");
                continue;
            }
            if (!GenerateurPack.copierTexture(g.texture(), textures, sortie)) {
                journal.erreur("Glyphe " + g.id() + " : image introuvable (ressources/textures/" + g.texture() + ".png)");
                continue;
            }
            JsonObject f = new JsonObject();
            f.addProperty("type", "bitmap");
            f.addProperty("file", GenerateurPack.NS + ":item/" + g.texture() + ".png");
            f.addProperty("height", g.hauteur());
            f.addProperty("ascent", g.montee());
            JsonArray chars = new JsonArray();
            chars.add(new String(Character.toChars(g.code())));
            f.add("chars", chars);
            fournisseurs.add(f);
        }
        JsonObject police = new JsonObject();
        police.add("providers", fournisseurs);
        sortie.put("assets/minecraft/font/default.json", police.toString().getBytes(StandardCharsets.UTF_8));
        return sortie;
    }

    /** Texte qui décale la suite de n pixels (négatif = vers la gauche). */
    public static String decalage(int pixels) {
        StringBuilder sb = new StringBuilder();
        int base = pixels < 0 ? ESPACE_NEG : ESPACE_POS;
        int reste = Math.abs(pixels);
        for (int i = PUISSANCES - 1; i >= 0 && reste > 0; i--) {
            int v = 1 << i;
            while (reste >= v) {
                sb.appendCodePoint(base + i);
                reste -= v;
            }
        }
        return sb.toString();
    }

    /** Taille d'une image : {largeur, hauteur}, ou null si illisible. */
    public static int[] taille(Path png) {
        try {
            BufferedImage img = ImageIO.read(png.toFile());
            return img == null ? null : new int[]{img.getWidth(), img.getHeight()};
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * Avance d'un glyphe à l'écran, calculée comme le jeu : dernière colonne non transparente de l'image,
     * mise à l'échelle de la hauteur affichée, plus 1 pixel d'espacement. 0 si l'image est illisible.
     */
    public static int avance(Path png, int hauteur) {
        try {
            BufferedImage img = ImageIO.read(png.toFile());
            if (img == null) return 0;
            int largeur = 0;
            for (int x = img.getWidth() - 1; x >= 0 && largeur == 0; x--) {
                for (int y = 0; y < img.getHeight(); y++) {
                    if ((img.getRGB(x, y) >>> 24) != 0) { largeur = x + 1; break; }
                }
            }
            double echelle = (double) hauteur / img.getHeight();
            return (int) (0.5 + largeur * echelle) + 1;
        } catch (IOException | RuntimeException e) {
            return 0;
        }
    }
}
