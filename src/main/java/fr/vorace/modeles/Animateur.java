package fr.vorace.modeles;

import fr.vorace.modeles.ModeleBB.Animation;
import fr.vorace.modeles.ModeleBB.Canaux;
import fr.vorace.modeles.ModeleBB.Cle;
import fr.vorace.modeles.ModeleBB.Partie;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * Calcule la position de chaque partie d'un modèle à un instant d'une animation, comme Blockbench l'affiche
 * (sans Bukkit). Le résultat est la transformation à donner à l'entité d'affichage de chaque partie.
 */
public final class Animateur {

    /**
     * Transformation d'une entité d'affichage (en blocs), à appliquer avec une rotation droite d'un demi-tour
     * (le jeu tourne les objets affichés d'un demi-tour : on l'annule).
     */
    public record Pose(Vector3f translation, Quaternionf rotation, Vector3f echelle) {}

    /** Demi-tour autour de l'axe vertical (rotation droite de chaque partie). */
    public static Quaternionf demiTour() {
        return new Quaternionf().rotateY((float) Math.PI);
    }

    private Animateur() {}

    /**
     * @param anim  animation, ou null pour la pose de base
     * @param temps secondes depuis le début de l'animation
     * @param yaw   orientation de l'entité (degrés Minecraft : 0 = sud)
     * @param taille agrandissement du modèle entier
     * @return une pose par partie (null pour une partie invisible)
     */
    public static Pose[] poses(ModeleBB m, Animation anim, double temps, float yaw, float taille) {
        int n = m.parties().size();
        Vector3f[] pos = new Vector3f[n];
        Quaternionf[] rot = new Quaternionf[n];
        Vector3f[] ech = new Vector3f[n];
        Pose[] res = new Pose[n];
        double t = temps(anim, temps);
        Quaternionf racine = new Quaternionf().rotateY((float) (Math.PI - Math.toRadians(yaw)));

        for (int i = 0; i < n; i++) {
            Partie p = m.parties().get(i);
            Canaux c = anim == null || p.uuid() == null ? null : anim.os().get(p.uuid());
            double[] dp = c == null ? new double[3] : echantillon(c.position(), t, 0);
            double[] dr = c == null ? new double[3] : echantillon(c.rotation(), t, 0);
            double[] ds = c == null ? new double[]{1, 1, 1} : echantillon(c.echelle(), t, 1);

            double[] pivotParent = p.parent() < 0 ? new double[3] : m.parties().get(p.parent()).pivot();
            // conventions de Blockbench pour les animations : x et y inversés en rotation, x inversé en position
            Vector3f local = new Vector3f((float) (p.pivot()[0] - pivotParent[0] - dp[0]), (float) (p.pivot()[1] - pivotParent[1] + dp[1]),
                (float) (p.pivot()[2] - pivotParent[2] + dp[2]));
            Quaternionf q = new Quaternionf()
                .rotateZ((float) Math.toRadians(p.rotation()[2] + dr[2]))
                .rotateY((float) Math.toRadians(p.rotation()[1] - dr[1]))
                .rotateX((float) Math.toRadians(p.rotation()[0] - dr[0]));
            Vector3f s = new Vector3f((float) ds[0], (float) ds[1], (float) ds[2]);

            if (p.parent() < 0) {
                pos[i] = local;
                rot[i] = q;
                ech[i] = s;
            } else {
                int k = p.parent();
                pos[i] = new Vector3f(local).mul(ech[k]).rotate(rot[k]).add(pos[k]);
                rot[i] = new Quaternionf(rot[k]).mul(q);
                ech[i] = new Vector3f(ech[k]).mul(s);
            }
            if (!p.visible()) continue;
            Vector3f translation = new Vector3f(pos[i]).mul(taille / 16f).rotate(racine);
            Quaternionf rotation = new Quaternionf(racine).mul(rot[i]);
            Vector3f echelle = new Vector3f(ech[i]).mul((float) (taille * p.echelle()));
            res[i] = new Pose(translation, rotation, echelle);
        }
        return res;
    }

    /** Temps ramené dans l'animation selon son mode de boucle. */
    static double temps(Animation a, double t) {
        if (a == null || a.duree() <= 0) return 0;
        if (a.boucle().equals("loop")) return t % a.duree();
        return Math.min(t, a.duree());
    }

    /** Vrai si l'animation est jouée une seule fois et qu'elle est finie. */
    public static boolean finie(Animation a, double t) {
        return a != null && a.boucle().equals("once") && t >= a.duree();
    }

    /** Valeur d'un canal à l'instant t (interpolation linéaire, Catmull-Rom ou en escalier, comme Blockbench). */
    static double[] echantillon(List<Cle> cles, double t, double defaut) {
        if (cles.isEmpty()) return new double[]{defaut, defaut, defaut};
        if (t <= cles.get(0).temps()) return val(cles.get(0));
        Cle derniere = cles.get(cles.size() - 1);
        if (t >= derniere.temps()) return val(derniere);
        int i = 0;
        while (i < cles.size() - 2 && cles.get(i + 1).temps() <= t) i++;
        Cle avant = cles.get(i), apres = cles.get(i + 1);
        double duree = apres.temps() - avant.temps();
        double f = duree <= 0 ? 1 : (t - avant.temps()) / duree;
        if (avant.interpolation().equals("step")) return val(avant);
        if (avant.interpolation().equals("catmullrom") || apres.interpolation().equals("catmullrom")) {
            double[] p0 = val(i > 0 ? cles.get(i - 1) : avant), p1 = val(avant), p2 = val(apres), p3 = val(i + 2 < cles.size() ? cles.get(i + 2) : apres);
            double[] r = new double[3];
            for (int k = 0; k < 3; k++) {
                r[k] = 0.5 * ((2 * p1[k]) + (-p0[k] + p2[k]) * f + (2 * p0[k] - 5 * p1[k] + 4 * p2[k] - p3[k]) * f * f
                    + (-p0[k] + 3 * p1[k] - 3 * p2[k] + p3[k]) * f * f * f);
            }
            return r;
        }
        double[] a = val(avant), b = val(apres);
        return new double[]{a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f};
    }

    private static double[] val(Cle c) {
        return new double[]{c.x(), c.y(), c.z()};
    }
}
