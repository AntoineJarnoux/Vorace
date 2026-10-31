package fr.vorace.meubles;

import java.util.Locale;

/**
 * Calculs de position des meubles (sans Bukkit, pour être testés sans serveur).
 *
 * Les positions d'un meuble (hitbox, sièges, décalage) sont données en blocs dans le repère du modèle Blockbench :
 * x et z comme dans Blockbench, y vers le haut. L'avant du meuble est la face « north » du modèle (z négatif).
 *
 * L'orientation (yaw) est celle de l'ItemDisplay. Le jeu dessine un ItemDisplay tourné de -yaw puis d'un demi-tour,
 * donc la face north du modèle regarde dans la direction (-sin yaw, cos yaw), l'axe x du modèle part vers
 * (-cos yaw, -sin yaw) et l'axe z vers (sin yaw, -cos yaw).
 */
public final class Geometrie {

    private Geometrie() {}

    /** Un décalage en blocs (x, y, z), lu depuis "x, y, z". */
    public record Point(double x, double y, double z) {
        public static final Point ZERO = new Point(0, 0, 0);

        public static Point lire(String texte) {
            if (texte == null || texte.isBlank()) return ZERO;
            String[] p = texte.trim().split("\\s*[,; ]\\s*");
            if (p.length != 3) throw new IllegalArgumentException("position invalide « " + texte + " » (format « x, y, z »)");
            try {
                return new Point(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("position invalide « " + texte + " » (format « x, y, z »)");
            }
        }

        /** Coordonnées entières (pour les blocs de hitbox). */
        public int[] bloc() {
            return new int[]{(int) Math.round(x), (int) Math.round(y), (int) Math.round(z)};
        }
    }

    /** Ramène un angle dans [-180, 180[. */
    public static float normaliser(float yaw) {
        float y = yaw % 360f;
        if (y >= 180f) y -= 360f;
        if (y < -180f) y += 360f;
        return y;
    }

    /**
     * Orientation du meuble à partir du regard du joueur : le meuble se tourne vers lui.
     * @param directions 4, 8, ou 0 pour une rotation libre
     */
    public static float orientation(float yawJoueur, int directions) {
        float y = yawJoueur + 180f;
        if (directions > 0) {
            float pas = 360f / directions;
            y = Math.round(y / pas) * pas;
        }
        return normaliser(y);
    }

    /** Nombre de quarts de tour (0 à 3) le plus proche d'une orientation, pour faire tourner la hitbox en blocs. */
    public static int quarts(float yaw) {
        return Math.floorMod(Math.round(normaliser(yaw) / 90f), 4);
    }

    /** Orientation d'un meuble posé contre un mur : il regarde dans la direction de la face cliquée. */
    public static float orientationMur(int dx, int dz) {
        return normaliser((float) Math.toDegrees(Math.atan2(-dx, dz)));
    }

    /** Décalage du repère du modèle -> monde pour une orientation quelconque. */
    public static Point tourner(Point p, float yaw) {
        double r = Math.toRadians(yaw);
        double c = Math.cos(r), s = Math.sin(r);
        double x = -p.x() * c + p.z() * s;
        double z = -p.x() * s - p.z() * c;
        return new Point(arrondir(x), p.y(), arrondir(z));
    }

    /** Décalage du repère du modèle -> monde pour un bloc de hitbox (rotation par quarts de tour exacts). */
    public static int[] tournerBloc(int[] b, int quarts) {
        int x = b[0], z = b[2];
        return switch (Math.floorMod(quarts, 4)) {
            case 1 -> new int[]{z, b[1], -x};
            case 2 -> new int[]{x, b[1], z};
            case 3 -> new int[]{-z, b[1], x};
            default -> new int[]{-x, b[1], -z};
        };
    }

    private static double arrondir(double v) {
        double r = Math.round(v * 1e6) / 1e6;
        return r == -0.0 ? 0.0 : r;
    }

    // ------------------------------------------------- blocs enregistrés dans le meuble

    public static String ecrireBlocs(java.util.List<int[]> blocs) {
        StringBuilder sb = new StringBuilder();
        for (int[] b : blocs) {
            if (!sb.isEmpty()) sb.append(';');
            sb.append(b[0]).append(',').append(b[1]).append(',').append(b[2]);
        }
        return sb.toString();
    }

    public static java.util.List<int[]> lireBlocs(String texte) {
        java.util.List<int[]> l = new java.util.ArrayList<>();
        if (texte == null || texte.isBlank()) return l;
        for (String part : texte.split(";")) {
            String[] p = part.split(",");
            if (p.length != 3) continue;
            try {
                l.add(new int[]{Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim())});
            } catch (NumberFormatException ignore) {
                // morceau abîmé : ignoré
            }
        }
        return l;
    }

    public static String texte(Point p) {
        return String.format(Locale.ROOT, "%s, %s, %s", p.x(), p.y(), p.z());
    }
}
