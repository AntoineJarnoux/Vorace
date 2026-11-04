package fr.vorace.mobs;

import java.util.Random;

/**
 * Vol d'un oiseau ou d'un papillon (sans Bukkit) : posé, s'envole quand on approche (ou de lui-même), vole de point
 * en point en virant doucement, puis se pose ailleurs, toujours dans sa laisse. Les papillons et les lucioles
 * volettent bas, en zigzag ; les oiseaux montent plus haut et vont plus vite.
 */
public final class Vol {

    public enum Etat { POSE, VOL, ATTERRISSAGE }

    /** Hauteur du sol (premier bloc libre) en (x, z) ; NaN si inconnue (chunk non chargé). */
    public interface Sol { double hauteur(double x, double z); }

    private final Random hasard;
    private final boolean papillon;
    private final double vitesse, altMin, altMax;
    double x, y, z, vx, vy, vz;
    private Etat etat = Etat.POSE;
    private double tx, ty, tz, temps, duree, phase;
    private double yaw;

    /**
     * @param vitesse blocs par seconde en vol de croisière
     * @param altMin  hauteur de vol minimale au-dessus du sol
     * @param altMax  hauteur de vol maximale au-dessus du sol
     */
    public Vol(long graine, boolean papillon, double vitesse, double altMin, double altMax, double x, double y, double z) {
        this.hasard = new Random(graine);
        this.papillon = papillon;
        this.vitesse = vitesse;
        this.altMin = altMin;
        this.altMax = Math.max(altMin, altMax);
        this.x = x; this.y = y; this.z = z;
        this.duree = papillon ? 2 + hasard.nextDouble() * 4 : 6 + hasard.nextDouble() * 20;
        this.phase = hasard.nextDouble() * Math.PI * 2;
    }

    public Etat etat() { return etat; }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    /** Orientation (degrés Minecraft) dans le sens du vol. */
    public float yaw() { return (float) yaw; }
    public boolean enVol() { return etat != Etat.POSE; }

    /** Un joueur approche : s'envoler à l'opposé. */
    public void effrayer(double menX, double menZ, Sol sol, double mx, double mz, double laisse) {
        if (etat != Etat.POSE) return;
        double ax = x - menX, az = z - menZ, n = Math.hypot(ax, az);
        if (n < 1e-6) { ax = 1; n = 1; }
        double d = papillon ? 4 : 10;
        double[] p = dansLaisse(x + ax / n * d, z + az / n * d, mx, mz, laisse);
        decoller(p[0], p[1], sol, true);
    }

    private void decoller(double px, double pz, Sol sol, boolean vite) {
        double h = sol.hauteur(px, pz);
        if (Double.isNaN(h)) h = y;
        etat = Etat.VOL;
        tx = px; tz = pz;
        ty = h + altMin + hasard.nextDouble() * (altMax - altMin);
        temps = 0;
        duree = papillon ? 6 + hasard.nextDouble() * 10 : (vite ? 8 : 6) + hasard.nextDouble() * 14;
        // impulsion de départ vers le haut
        vy = Math.max(vy, papillon ? 0.8 : 2.5);
    }

    private double[] dansLaisse(double px, double pz, double mx, double mz, double laisse) {
        double dx = px - mx, dz = pz - mz, d = Math.hypot(dx, dz);
        if (d <= laisse * 0.95 || d < 1e-6) return new double[]{px, pz};
        return new double[]{mx + dx / d * laisse * 0.9, mz + dz / d * laisse * 0.9};
    }

    private double[] pointAuHasard(double mx, double mz, double laisse) {
        double a = hasard.nextDouble() * Math.PI * 2;
        double d = papillon ? 2 + hasard.nextDouble() * 5 : 6 + hasard.nextDouble() * 14;
        return dansLaisse(x + Math.cos(a) * d, z + Math.sin(a) * d, mx, mz, laisse);
    }

    /** Avance de dt secondes. */
    public void pas(double dt, Sol sol, double mx, double mz, double laisse) {
        temps += dt;
        double sous = sol.hauteur(x, z);
        if (Double.isNaN(sous)) sous = y - altMin;
        switch (etat) {
            case POSE -> {
                vx = vy = vz = 0;
                if (temps >= duree) {
                    double[] p = pointAuHasard(mx, mz, laisse);
                    decoller(p[0], p[1], sol, false);
                }
                return;
            }
            case VOL -> {
                if (Math.hypot(tx - x, tz - z) < (papillon ? 1.0 : 2.5)) {
                    if (temps >= duree) {
                        // se poser : sur le sol (ou un toit, une branche) sous un point proche
                        double[] p = pointAuHasard(mx, mz, laisse);
                        double h = sol.hauteur(p[0], p[1]);
                        if (!Double.isNaN(h)) {
                            etat = Etat.ATTERRISSAGE;
                            tx = p[0]; tz = p[1]; ty = h;
                        }
                    } else {
                        double[] p = pointAuHasard(mx, mz, laisse);
                        double h = sol.hauteur(p[0], p[1]);
                        tx = p[0]; tz = p[1];
                        ty = (Double.isNaN(h) ? y : h) + altMin + hasard.nextDouble() * (altMax - altMin);
                    }
                }
            }
            case ATTERRISSAGE -> {
                double d = Math.sqrt((tx - x) * (tx - x) + (ty - y) * (ty - y) + (tz - z) * (tz - z));
                if (d < 0.35) {
                    x = tx; y = ty; z = tz;
                    vx = vy = vz = 0;
                    etat = Etat.POSE;
                    temps = 0;
                    duree = papillon ? 2 + hasard.nextDouble() * 6 : 8 + hasard.nextDouble() * 30;
                    return;
                }
            }
        }
        // vitesse voulue vers la cible, virage progressif
        double dx = tx - x, dy = ty - y, dz = tz - z;
        double n = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double v = vitesse * (etat == Etat.ATTERRISSAGE ? Math.max(0.25, Math.min(1, n / 4)) : 1);
        double wx = n > 1e-6 ? dx / n * v : 0, wy = n > 1e-6 ? dy / n * v : 0, wz = n > 1e-6 ? dz / n * v : 0;
        if (papillon) {
            phase += dt * 7;
            wy += Math.sin(phase) * 0.9;
            wx += Math.cos(phase * 0.7) * 0.6;
            wz += Math.sin(phase * 0.9) * 0.6;
        }
        double k = Math.min(1, dt * (papillon ? 4 : 2.2));
        vx += (wx - vx) * k; vy += (wy - vy) * k; vz += (wz - vz) * k;
        x += vx * dt; y += vy * dt; z += vz * dt;
        // jamais dans le sol (ou un toit) en vol
        double ici = sol.hauteur(x, z);
        if (Double.isNaN(ici)) ici = sous;
        if (etat == Etat.VOL && y < ici + 0.8) { y = ici + 0.8; vy = Math.max(vy, 0.5); }
        if (etat == Etat.ATTERRISSAGE && y < ici && Math.hypot(tx - x, tz - z) > 0.5) { y = ici; vy = Math.max(vy, 0); }
        if (Math.hypot(vx, vz) > 0.05) yaw = Math.toDegrees(Math.atan2(-vx, vz));
    }

    /** Place l'oiseau (rechargement, apparition). */
    public void placer(double nx, double ny, double nz) {
        x = nx; y = ny; z = nz;
        vx = vy = vz = 0;
        etat = Etat.POSE;
        temps = 0;
    }
}
