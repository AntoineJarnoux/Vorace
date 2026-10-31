package fr.vorace.meubles;

import fr.vorace.meubles.Geometrie.Point;
import fr.vorace.objets.GenerateurPack;
import fr.vorace.objets.Visuel;
import fr.vorace.pack.FormatPack;
import fr.vorace.pack.Journal;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Teste les calculs de position des meubles et la génération des modèles d'exemple. */
public final class TestMeubles {
    static int echecs = 0;
    static void verifier(boolean ok, String quoi) { System.out.println((ok ? "OK    " : "ECHEC ") + quoi); if (!ok) echecs++; }
    static boolean proche(double a, double b) { return Math.abs(a - b) < 1e-6; }

    /** Direction regardée par la face north du modèle pour un yaw (règle de rendu des ItemDisplay). */
    static double[] avant(float yaw) {
        double r = Math.toRadians(yaw);
        return new double[]{-Math.sin(r), Math.cos(r)};
    }

    public static void main(String[] a) throws Exception {
        // le meuble se tourne vers le joueur
        verifier(Geometrie.orientation(180f, 8) == 0f, "joueur regardant le nord : meuble tourné vers le sud (yaw 0)");
        verifier(Geometrie.orientation(0f, 4) == -180f, "joueur regardant le sud : yaw -180");
        verifier(Geometrie.orientation(-90f, 4) == 90f, "joueur regardant l'est : yaw 90");
        verifier(Geometrie.orientation(22f, 8) == -180f && Geometrie.orientation(23f, 8) == -135f, "8 directions : arrondi au 45° le plus proche");
        verifier(proche(Geometrie.orientation(17f, 0), -163f), "rotation libre");
        for (float yJoueur : new float[]{0, 45, 90, 135, 180, -45, -90, -135, 33}) {
            float y = Geometrie.orientation(yJoueur, 0);
            double[] f = avant(y);
            double rj = Math.toRadians(yJoueur);
            double[] regard = {-Math.sin(rj), Math.cos(rj)};
            if (!(proche(f[0], -regard[0]) && proche(f[1], -regard[1]))) verifier(false, "avant du meuble face au joueur pour yaw " + yJoueur);
        }
        verifier(true, "avant du meuble face au joueur pour tous les angles");

        // contre un mur : l'avant part dans la direction de la face cliquée
        for (int[] face : new int[][]{{0, -1}, {0, 1}, {1, 0}, {-1, 0}}) {
            double[] f = avant(Geometrie.orientationMur(face[0], face[1]));
            if (!(proche(f[0], face[0]) && proche(f[1], face[1]))) verifier(false, "mur " + Arrays.toString(face));
        }
        verifier(true, "contre un mur : le meuble regarde hors du mur");

        // repère du modèle : z du modèle = vers l'arrière ; tourner() et tournerBloc() sont d'accord
        for (float y : new float[]{0, 90, 180, -90}) {
            double[] f = avant(y);
            Point arriere = Geometrie.tourner(new Point(0, 0, 1), y);
            if (!(proche(arriere.x(), -f[0]) && proche(arriere.z(), -f[1]))) verifier(false, "z du modèle vers l'arrière, yaw " + y);
            int q = Geometrie.quarts(y);
            for (int[] b : new int[][]{{1, 0, 0}, {0, 0, 1}, {2, 1, -1}}) {
                Point p = Geometrie.tourner(new Point(b[0], b[1], b[2]), y);
                int[] r = Geometrie.tournerBloc(b, q);
                if (!(proche(p.x(), r[0]) && proche(p.y(), r[1]) && proche(p.z(), r[2]))) verifier(false, "tournerBloc = tourner pour " + Arrays.toString(b) + " yaw " + y);
            }
        }
        verifier(true, "hitbox en blocs et positions tournent pareil");
        // vérification concrète : yaw 0, l'avant regarde le sud, le 2e bloc d'une table (x+1 du modèle) part vers l'ouest
        verifier(Arrays.equals(Geometrie.tournerBloc(new int[]{1, 0, 0}, 0), new int[]{-1, 0, 0}), "yaw 0 : x du modèle vers l'ouest (rendu retourné d'un demi-tour)");
        verifier(Geometrie.quarts(-180f) == 2 && Geometrie.quarts(135f) == 2 && Geometrie.quarts(-50f) == 3, "quarts de tour");

        // positions lues dans la config
        verifier(Point.lire("0, 0.6, 0").equals(new Point(0, 0.6, 0)) && Point.lire("1 0 -1").equals(new Point(1, 0, -1)), "lecture « x, y, z »");
        boolean erreur = false;
        try { Point.lire("1,2"); } catch (IllegalArgumentException e) { erreur = true; }
        verifier(erreur, "position incomplète refusée");
        List<int[]> bl = Geometrie.lireBlocs(Geometrie.ecrireBlocs(List.of(new int[]{1, -64, 3}, new int[]{-2, 70, 0})));
        verifier(bl.size() == 2 && Arrays.equals(bl.get(0), new int[]{1, -64, 3}) && Arrays.equals(bl.get(1), new int[]{-2, 70, 0}), "blocs enregistrés relus");

        // les modèles d'exemple passent dans le générateur de pack
        Path r = Files.createTempDirectory("vorace-meubles");
        for (String m : List.of("chaise", "lampe", "caisse", "table")) copier("exemples/meubles/" + m + ".json", r.resolve("modeles/exemple/" + m + ".json"));
        for (String t : List.of("bois", "lampe")) copier("exemples/meubles/" + t + ".png", r.resolve("textures/exemple/" + t + ".png"));
        List<Visuel> v = List.of(new Visuel("chaise", "paper", null, "exemple/chaise", false, 1101, null),
            new Visuel("table", "paper", null, "exemple/table", false, 1102, null));
        Journal j = new Journal();
        Map<String, byte[]> pack = GenerateurPack.generer(v, r, new FormatPack(46, 0, "t"), j);
        String chaise = new String(pack.get("assets/vorace/models/item/chaise.json"), StandardCharsets.UTF_8);
        verifier(chaise.contains("vorace:item/exemple/bois") && pack.containsKey("assets/vorace/textures/item/exemple/bois.png")
            && j.compter(Journal.Niveau.ERREUR) == 0 && j.compter(Journal.Niveau.CONFLIT) == 0, "modèles d'exemple : textures reliées, aucune erreur");
        System.out.println(echecs == 0 ? "TOUS LES TESTS PASSENT" : echecs + " ECHEC(S)");
        System.exit(echecs == 0 ? 0 : 1);
    }

    static void copier(String ressource, Path cible) throws Exception {
        Files.createDirectories(cible.getParent());
        try (InputStream in = TestMeubles.class.getClassLoader().getResourceAsStream(ressource)) {
            if (in == null) throw new IllegalStateException("ressource absente : " + ressource);
            Files.copy(in, cible);
        }
    }
}
