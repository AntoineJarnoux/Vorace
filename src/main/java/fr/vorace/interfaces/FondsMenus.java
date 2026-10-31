package fr.vorace.interfaces;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Les fonds des menus de Vorace, dessinés par le plugin aux couleurs de Vorace (dégradé violet → rose) :
 * un cadre en dégradé, un bandeau de titre, des cases sombres pile sous les cases du coffre et de l'inventaire.
 * Ils deviennent des glyphes intégrés (vorace_fond_1 à vorace_fond_6) placés derrière le titre du menu.
 */
public final class FondsMenus {

    private FondsMenus() {}

    public static final String DOSSIER = "vorace-interface";
    public static final int LARGEUR = 176;

    public static final int VIOLET = 0x7b2ff7;
    public static final int ROSE = 0xf107a3;

    public static String id(int lignes) { return "vorace_fond_" + lignes; }

    /** Image dans ressources/textures (sans .png). */
    public static String texture(int lignes) { return DOSSIER + "/fond_" + lignes; }

    /** Hauteur de la fenêtre d'un coffre de ce nombre de lignes (comme le jeu). */
    public static int hauteur(int lignes) { return 114 + lignes * 18; }

    /**
     * Écrit les six fonds dans ressources/textures/vorace-interface/ (seulement s'ils ont changé, pour ne pas
     * relancer la surveillance des fichiers pour rien).
     */
    public static void installer(Path textures) throws IOException {
        for (int n = 1; n <= 6; n++) {
            byte[] png = png(dessiner(n));
            Path f = textures.resolve(texture(n) + ".png");
            if (Files.isRegularFile(f) && Arrays.equals(Files.readAllBytes(f), png)) continue;
            Files.createDirectories(f.getParent());
            Files.write(f, png);
        }
    }

    static byte[] png(BufferedImage img) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ImageIO.write(img, "png", o);
        return o.toByteArray();
    }

    // ------------------------------------------------------------------ dessin

    private static final int CONTOUR = 0x0b0612;
    private static final int FOND_HAUT = 0x22123a;
    private static final int FOND_BAS = 0x150a24;
    private static final int CASE_OMBRE = 0x0a0512;
    private static final int CASE_LUMIERE = 0x7046b0;
    private static final int CASE_INTERIEUR = 0x2c1a46;
    private static final int NAV_LUMIERE = 0xd8489e;
    private static final int NAV_INTERIEUR = 0x3a1a44;
    private static final int BANDE_GAUCHE = 0xd9c6ff;
    private static final int BANDE_DROITE = 0xffc6ec;

    /** Le fond d'un coffre de 1 à 6 lignes : 176 × (114 + lignes × 18) pixels, cases aux positions du jeu. */
    public static BufferedImage dessiner(int lignes) {
        int n = Math.max(1, Math.min(6, lignes));
        int w = LARGEUR, h = hauteur(n);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        // panneau et cadre en dégradé
        for (int y = 0; y < h; y++) {
            int fond = melange(FOND_HAUT, FOND_BAS, y / (double) (h - 1));
            for (int x = 0; x < w; x++) {
                int d = Math.min(Math.min(x, y), Math.min(w - 1 - x, h - 1 - y));
                int c;
                if (d == 0) c = CONTOUR;
                else if (d <= 2) {
                    c = degrade(x, y, w, h);
                    if (d == 1) c = melange(c, 0xffffff, 0.18);
                } else if (d == 3) c = melange(degrade(x, y, w, h), fond, 0.65);
                else c = fond;
                img.setRGB(x, y, 0xff000000 | c);
            }
        }

        // bandeau du titre (le titre s'écrit en blanc à x = 8, y = 6)
        bandeau(img, 4, 3, w - 5, 15);

        // cases du menu (la dernière ligne, celle de la navigation, en rose)
        for (int r = 0; r < n; r++) {
            boolean nav = r == n - 1;
            for (int c = 0; c < 9; c++) caseVide(img, 7 + c * 18, 17 + r * 18, nav);
        }

        // bande claire derrière « Inventaire » (écrit en gris foncé par le jeu)
        int yb = n * 18 + 18;
        for (int y = yb; y <= yb + 10; y++) {
            for (int x = 4; x <= w - 5; x++) {
                int c = melange(BANDE_GAUCHE, BANDE_DROITE, (x - 4) / (double) (w - 9));
                if (y == yb) c = melange(c, 0xffffff, 0.5);
                if (y == yb + 10) c = melange(c, VIOLET, 0.35);
                img.setRGB(x, y, 0xff000000 | c);
            }
        }

        // inventaire du joueur (3 lignes) et barre d'objets
        int yi = n * 18 + 30;
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) caseVide(img, 7 + c * 18, yi + r * 18, false);
        for (int c = 0; c < 9; c++) caseVide(img, 7 + c * 18, n * 18 + 88, false);

        // coins arrondis comme ceux du jeu
        int[][] coin = {{0, 0}, {1, 0}, {2, 0}, {0, 1}, {1, 1}, {0, 2}};
        for (int[] p : coin) {
            img.setRGB(p[0], p[1], 0);
            img.setRGB(w - 1 - p[0], p[1], 0);
            img.setRGB(p[0], h - 1 - p[1], 0);
            img.setRGB(w - 1 - p[0], h - 1 - p[1], 0);
        }
        int[][] arrondi = {{3, 0}, {2, 1}, {1, 2}, {0, 3}, {1, 1}};
        for (int[] p : arrondi) {
            if (p[0] == 1 && p[1] == 1) continue;
            img.setRGB(p[0], p[1], 0xff000000 | CONTOUR);
            img.setRGB(w - 1 - p[0], p[1], 0xff000000 | CONTOUR);
            img.setRGB(p[0], h - 1 - p[1], 0xff000000 | CONTOUR);
            img.setRGB(w - 1 - p[0], h - 1 - p[1], 0xff000000 | CONTOUR);
        }
        return img;
    }

    private static void bandeau(BufferedImage img, int x0, int y0, int x1, int y1) {
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                int c = melange(VIOLET, ROSE, (x - x0) / (double) (x1 - x0));
                if (y == y0) c = melange(c, 0xffffff, 0.40);
                else if (y == y0 + 1) c = melange(c, 0xffffff, 0.15);
                else if (y == y1) c = melange(c, 0x000000, 0.45);
                else if (y == y1 - 1) c = melange(c, 0x000000, 0.15);
                // petites rayures claires vers la droite, pour la texture
                if (y > y0 + 1 && y < y1 - 1 && x > x1 - 40 && ((x + y) % 6 == 0)) c = melange(c, 0xffffff, 0.12 * (x - (x1 - 40)) / 40.0);
                if (x == x0 || x == x1) c = melange(c, 0x000000, 0.35);
                img.setRGB(x, y, 0xff000000 | c);
            }
        }
    }

    /** Case de 18 × 18 pixels en creux : ombre en haut à gauche, lumière en bas à droite. */
    private static void caseVide(BufferedImage img, int x0, int y0, boolean nav) {
        int lumiere = nav ? NAV_LUMIERE : CASE_LUMIERE;
        int interieur = nav ? NAV_INTERIEUR : CASE_INTERIEUR;
        for (int dy = 0; dy < 18; dy++) {
            for (int dx = 0; dx < 18; dx++) {
                int x = x0 + dx, y = y0 + dy;
                if (x < 0 || y < 0 || x >= img.getWidth() || y >= img.getHeight()) continue;
                int c;
                if (dx == 0 || dy == 0) c = CASE_OMBRE;
                else if (dx == 17 || dy == 17) c = lumiere;
                else c = melange(interieur, 0x000000, 0.12 * (16 - dy) / 16.0);
                if ((dx == 0 && dy == 17) || (dx == 17 && dy == 0)) c = melange(CASE_OMBRE, lumiere, 0.5);
                img.setRGB(x, y, 0xff000000 | c);
            }
        }
    }

    /** Dégradé Vorace en diagonale. */
    private static int degrade(int x, int y, int w, int h) {
        return melange(VIOLET, ROSE, (x + y * 0.6) / (w - 1 + (h - 1) * 0.6));
    }

    static int melange(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
