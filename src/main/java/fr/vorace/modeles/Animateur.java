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
     * Une animation jouée avec un poids. Une couche complète tire tous les os vers son animation (os non animés : pose
     * de base) ; une couche partielle ne change que les canaux que son animation anime (attaque par-dessus la marche).
     * Les couches s'appliquent dans l'ordre : chacune fond le résultat précédent vers elle selon son poids (0 à 1).
     * @param anim animation, ou null pour la pose de base
     */
    public record Couche(Animation anim, double temps, float poids, boolean partielle) {}

    /**
     * @param anim  animation, ou null pour la pose de base
     * @param temps secondes depuis le début de l'animation
     * @param yaw   orientation de l'entité (degrés Minecraft : 0 = sud)
     * @param taille agrandissement du modèle entier
     * @return une pose par partie (null pour une partie invisible)
     */
    public static Pose[] poses(ModeleBB m, Animation anim, double temps, float yaw, float taille) {
        return poses(m, anim == null ? List.of() : List.of(new Couche(anim, temps, 1f, false)), yaw, taille, 0f, 0f);
    }

    /**
     * Poses des parties visibles avec plusieurs couches d'animation et le regard de la tête.
     * @param teteYaw   rotation de la tête par rapport au corps (degrés, même sens que le yaw de Minecraft)
     * @param tetePitch inclinaison de la tête (degrés Minecraft, positif = regarde vers le bas)
     */
    public static Pose[] poses(ModeleBB m, List<Couche> couches, float yaw, float taille, float teteYaw, float tetePitch) {
        return calculer(m, couches, yaw, taille, teteYaw, tetePitch, false);
    }

    /** Comme poses(), mais aussi pour les os sans cube (os de nom « tag_ », repères...). */
    public static Pose[] posesCompletes(ModeleBB m, List<Couche> couches, float yaw, float taille, float teteYaw, float tetePitch) {
        return calculer(m, couches, yaw, taille, teteYaw, tetePitch, true);
    }

    /** Os de tête qui tournent avec le regard : les plus hauts dans la hiérarchie (un os de tête dans la tête suit déjà). */
    static boolean[] tetes(ModeleBB m) {
        int n = m.parties().size();
        boolean[] dansTete = new boolean[n], res = new boolean[n];
        for (int i = 0; i < n; i++) {
            Partie p = m.parties().get(i);
            boolean parentTete = p.parent() >= 0 && dansTete[p.parent()];
            res[i] = p.tete() && !parentTete;
            dansTete[i] = parentTete || p.tete();
        }
        return res;
    }

    private static Pose[] calculer(ModeleBB m, List<Couche> couches, float yaw, float taille, float teteYaw, float tetePitch, boolean toutes) {
        int n = m.parties().size();
        Vector3f[] pos = new Vector3f[n];
        Quaternionf[] rot = new Quaternionf[n];
        Vector3f[] ech = new Vector3f[n];
        Pose[] res = new Pose[n];
        double[] ts = new double[couches.size()];
        for (int c = 0; c < ts.length; c++) ts[c] = temps(couches.get(c).anim(), couches.get(c).temps());
        boolean[] tete = teteYaw != 0 || tetePitch != 0 ? tetes(m) : null;
        Quaternionf racine = new Quaternionf().rotateY((float) (Math.PI - Math.toRadians(yaw)));

        for (int i = 0; i < n; i++) {
            Partie p = m.parties().get(i);
            double[] dp = new double[3], dr = new double[3], ds = {1, 1, 1};
            for (int c = 0; c < ts.length; c++) {
                Couche co = couches.get(c);
                float w = Math.max(0f, Math.min(1f, co.poids()));
                if (w <= 0) continue;
                Canaux ca = co.anim() == null || p.uuid() == null ? null : co.anim().os().get(p.uuid());
                if (ca == null && co.partielle()) continue;
                melanger(dp, ca == null ? null : ca.position(), ts[c], 0, w, co.partielle());
                melanger(dr, ca == null ? null : ca.rotation(), ts[c], 0, w, co.partielle());
                melanger(ds, ca == null ? null : ca.echelle(), ts[c], 1, w, co.partielle());
            }
            if (tete != null && tete[i]) {
                dr[1] += teteYaw;
                dr[0] += tetePitch;
            }

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
            if (!p.visible() && !toutes) continue;
            Vector3f translation = new Vector3f(pos[i]).mul(taille / 16f).rotate(racine);
            Quaternionf rotation = new Quaternionf(racine).mul(rot[i]);
            Vector3f echelle = new Vector3f(ech[i]).mul((float) (taille * p.echelle()));
            res[i] = new Pose(translation, rotation, echelle);
        }
        return res;
    }

    /** Fond la valeur courante vers celle du canal (ou vers la valeur par défaut si le canal est absent d'une couche complète). */
    private static void melanger(double[] v, List<Cle> canal, double t, double defaut, float w, boolean partielle) {
        boolean vide = canal == null || canal.isEmpty();
        if (vide && partielle) return;
        double[] cible = vide ? new double[]{defaut, defaut, defaut} : echantillon(canal, t, defaut);
        for (int k = 0; k < 3; k++) v[k] += (cible[k] - v[k]) * w;
    }

    /**
     * Clés d'effet atteintes entre deux instants (temps écoulés depuis le début, sans boucle : la boucle est gérée ici).
     * Une clé est rendue quand son temps est dans ]avant, maintenant].
     */
    public static List<ModeleBB.Evenement> evenements(Animation a, double avant, double maintenant, boolean boucle) {
        if (a == null || a.evenements().isEmpty() || maintenant <= avant) return List.of();
        List<ModeleBB.Evenement> res = new java.util.ArrayList<>();
        boolean tourne = (boucle || a.boucle().equals("loop")) && a.duree() > 0;
        if (!tourne) {
            for (ModeleBB.Evenement e : a.evenements()) if (e.temps() > avant && e.temps() <= maintenant) res.add(e);
            return res;
        }
        double d = a.duree();
        long premier = (long) Math.floor(avant / d), dernier = (long) Math.floor(maintenant / d);
        if (dernier - premier > 4) premier = dernier - 4; // jamais une avalanche de sons après un ralentissement
        for (long k = premier; k <= dernier; k++) {
            for (ModeleBB.Evenement e : a.evenements()) {
                double te = e.temps() + k * d;
                if (e.temps() < d + 1e-9 && te > avant && te <= maintenant) res.add(e);
            }
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
        if (t <= cles.get(0).temps()) return val(cles.get(0), t);
        Cle derniere = cles.get(cles.size() - 1);
        if (t >= derniere.temps()) return val(derniere, t);
        int i = 0;
        while (i < cles.size() - 2 && cles.get(i + 1).temps() <= t) i++;
        Cle avant = cles.get(i), apres = cles.get(i + 1);
        double duree = apres.temps() - avant.temps();
        double f = duree <= 0 ? 1 : (t - avant.temps()) / duree;
        if (avant.interpolation().equals("step")) return val(avant, t);
        if (avant.interpolation().equals("catmullrom") || apres.interpolation().equals("catmullrom")) {
            double[] p0 = val(i > 0 ? cles.get(i - 1) : avant, t), p1 = val(avant, t), p2 = val(apres, t), p3 = val(i + 2 < cles.size() ? cles.get(i + 2) : apres, t);
            double[] r = new double[3];
            for (int k = 0; k < 3; k++) {
                r[k] = 0.5 * ((2 * p1[k]) + (-p0[k] + p2[k]) * f + (2 * p0[k] - 5 * p1[k] + 4 * p2[k] - p3[k]) * f * f
                    + (-p0[k] + 3 * p1[k] - 3 * p2[k] + p3[k]) * f * f * f);
            }
            return r;
        }
        double[] a = val(avant, t), b = val(apres, t);
        return new double[]{a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f};
    }

    /** Valeur d'une clé ; une expression Molang est calculée au temps de l'animation. */
    private static double[] val(Cle c, double t) {
        double[] v = {c.x(), c.y(), c.z()};
        Molang[] e = c.expressions();
        if (e != null) for (int k = 0; k < 3; k++) if (e[k] != null) v[k] = e[k].evaluer(t);
        return v;
    }
}
