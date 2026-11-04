package fr.vorace.mobs;

import fr.vorace.mobs.Cerveau.Action;
import fr.vorace.mobs.Cerveau.Activite;
import fr.vorace.mobs.Cerveau.Caractere;
import fr.vorace.mobs.Cerveau.Perception;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Cerveau des mobs (besoins, émotions, choix), géométrie de la laisse, zones et lecture des fichiers. */
public final class TestMobs {
    static int echecs = 0;
    static void verifier(boolean ok, String quoi) { System.out.println((ok ? "OK    " : "ECHEC ") + quoi); if (!ok) echecs++; }

    /** Situation de base : à la maison, seul, personne autour, de jour, rien à manger ni à boire. */
    static Perception calme() {
        return new Perception(2, 20, false, Double.NaN, 0, Double.NaN, false, false, 10, false, false, false, true);
    }

    static Perception avec(Perception p, double distMaison, boolean horsZone, double distTroupeau, double cohesion, double distMenace,
                           boolean rapide, boolean discrete, boolean nuit, boolean manger, boolean boire, boolean surPlace) {
        return new Perception(distMaison, p.laisse(), horsZone, distTroupeau, cohesion, distMenace, rapide, discrete, p.detection(), nuit, manger, boire, surPlace);
    }

    /** Fait penser n fois (0,5 s) dans la même situation ; renvoie les actions vues. */
    static Set<Action> vivre(Cerveau c, Perception p, int n, Caractere car, Activite a) {
        Set<Action> vues = EnumSet.noneOf(Action.class);
        for (int i = 0; i < n; i++) vues.add(c.penser(p, 0.5, car, a));
        return vues;
    }

    public static void main(String[] args) throws Exception {
        Perception p0 = calme();

        // repos et errance alternent
        Cerveau c = new Cerveau(1);
        Set<Action> vues = vivre(c, p0, 240, Caractere.PAISIBLE, Activite.TOUJOURS);
        verifier(vues.contains(Action.REPOS) && vues.contains(Action.ERRER) && vues.size() == 2, "calme : repos et errance alternent, rien d'autre " + vues);

        // faim : va manger, mange sur place, puis arrête
        c = new Cerveau(2);
        c.faim = 0.8;
        Perception aManger = avec(p0, 2, false, Double.NaN, 0, Double.NaN, false, false, false, true, false, false);
        verifier(c.penser(aManger, 0.5, Caractere.PAISIBLE, Activite.TOUJOURS) == Action.MANGER, "affamé avec de la nourriture : mange");
        Perception surPlace = avec(aManger, 2, false, Double.NaN, 0, Double.NaN, false, false, false, true, false, true);
        int n = 0;
        while (c.action() == Action.MANGER && n < 200) { c.penser(surPlace, 0.5, Caractere.PAISIBLE, Activite.TOUJOURS); n++; }
        verifier(c.faim() < 0.1 && c.action() != Action.MANGER && n < 60, "mange sur place puis passe à autre chose (" + n / 2 + " s)");
        Cerveau loin = new Cerveau(2);
        loin.faim = 0.8;
        loin.penser(aManger, 0.5, Caractere.PAISIBLE, Activite.TOUJOURS);
        for (int i = 0; i < 20; i++) loin.penser(aManger, 0.5, Caractere.PAISIBLE, Activite.TOUJOURS);
        verifier(loin.faim() > 0.75, "pas encore arrivé : la faim ne baisse pas");
        Cerveau sansNourriture = new Cerveau(2);
        sansNourriture.faim = 0.9;
        verifier(!vivre(sansNourriture, p0, 20, Caractere.PAISIBLE, Activite.TOUJOURS).contains(Action.MANGER), "affamé sans nourriture connue : ne reste pas planté à vouloir manger");

        // soif
        c = new Cerveau(3);
        c.soif = 0.9;
        verifier(c.penser(avec(p0, 2, false, Double.NaN, 0, Double.NaN, false, false, false, false, true, false), 0.5, Caractere.PAISIBLE, Activite.TOUJOURS) == Action.BOIRE,
            "assoiffé près de l'eau : boit");

        // besoins qui montent avec le temps
        c = new Cerveau(4);
        double f0 = c.faim(), s0 = c.soif();
        vivre(c, p0, 240, Caractere.PAISIBLE, Activite.TOUJOURS);
        verifier(Math.abs(c.faim() - f0 - 120 / Cerveau.FAIM) < 1e-6 && c.soif() > s0, "faim et soif montent avec le temps (2 min)");

        // sommeil
        c = new Cerveau(5);
        Perception nuit = avec(p0, 2, false, Double.NaN, 0, Double.NaN, false, false, true, false, false, true);
        verifier(c.penser(nuit, 0.5, Caractere.PAISIBLE, Activite.DIURNE) == Action.DORMIR, "diurne la nuit : dort");
        vivre(c, nuit, 400, Caractere.PAISIBLE, Activite.DIURNE);
        verifier(c.action() == Action.DORMIR, "dort toute la nuit");
        vivre(c, p0, 4, Caractere.PAISIBLE, Activite.DIURNE);
        verifier(c.action() != Action.DORMIR, "se réveille au jour, reposé");
        verifier(new Cerveau(5).penser(p0, 0.5, Caractere.PAISIBLE, Activite.NOCTURNE) == Action.DORMIR, "nocturne le jour : dort");
        Cerveau fatigue = new Cerveau(6);
        fatigue.fatigue = 1;
        verifier(fatigue.penser(p0, 0.5, Caractere.PAISIBLE, Activite.TOUJOURS) == Action.DORMIR, "épuisé : dort même de jour");

        // laisse et zone
        c = new Cerveau(7);
        Perception dehors = avec(p0, 25, false, Double.NaN, 0, Double.NaN, false, false, false, false, false, true);
        verifier(c.penser(dehors, 0.5, Caractere.PAISIBLE, Activite.TOUJOURS) == Action.RENTRER, "hors de la laisse : rentre");
        verifier(c.penser(avec(p0, 15, false, Double.NaN, 0, Double.NaN, false, false, false, false, false, true), 0.5, Caractere.PAISIBLE, Activite.TOUJOURS) == Action.RENTRER,
            "rentre jusqu'à être bien revenu (pas seulement à la limite)");
        c.penser(avec(p0, 10, false, Double.NaN, 0, Double.NaN, false, false, false, false, false, true), 0.5, Caractere.PAISIBLE, Activite.TOUJOURS);
        verifier(c.action() != Action.RENTRER, "revenu : passe à autre chose");
        verifier(new Cerveau(8).penser(avec(p0, 2, true, Double.NaN, 0, Double.NaN, false, false, false, false, false, true), 0.5, Caractere.PAISIBLE, Activite.TOUJOURS) == Action.RENTRER,
            "hors de sa zone : rentre");

        // troupeau
        c = new Cerveau(9);
        verifier(c.penser(avec(p0, 2, false, 12, 6, Double.NaN, false, false, false, false, false, true), 0.5, Caractere.PAISIBLE, Activite.TOUJOURS) == Action.REJOINDRE,
            "loin du troupeau : le rejoint");
        verifier(!vivre(new Cerveau(9), avec(p0, 2, false, 3, 6, Double.NaN, false, false, false, false, false, true), 40, Caractere.PAISIBLE, Activite.TOUJOURS).contains(Action.REJOINDRE),
            "près du troupeau : reste tranquille");

        // caractères
        Perception joueur5 = avec(p0, 2, false, Double.NaN, 0, 5, false, false, false, false, false, true);
        verifier(new Cerveau(10).penser(joueur5, 0.5, Caractere.CRAINTIF, Activite.TOUJOURS) == Action.FUIR, "craintif : fuit un joueur à 5 blocs");
        verifier(new Cerveau(10).penser(avec(p0, 2, false, Double.NaN, 0, 8, false, true, false, false, false, true), 0.5, Caractere.CRAINTIF, Activite.TOUJOURS) != Action.FUIR,
            "craintif : un joueur accroupi à 8 blocs passe inaperçu");
        verifier(new Cerveau(10).penser(joueur5, 0.5, Caractere.PAISIBLE, Activite.TOUJOURS) != Action.FUIR, "paisible : un joueur qui marche à côté ne l'effraie pas");
        verifier(new Cerveau(10).penser(avec(p0, 2, false, Double.NaN, 0, 2, true, false, false, false, false, true), 0.5, Caractere.PAISIBLE, Activite.TOUJOURS) == Action.FUIR,
            "paisible : un joueur qui fonce dessus le fait fuir");
        verifier(new Cerveau(10).penser(avec(p0, 2, false, Double.NaN, 0, 8, false, false, false, false, false, true), 0.5, Caractere.AGRESSIF, Activite.TOUJOURS) == Action.ATTAQUER,
            "agressif : attaque un joueur remarqué");
        Cerveau terr = new Cerveau(11);
        Perception tout = avec(p0, 2, false, Double.NaN, 0, 3, false, false, false, false, false, true);
        verifier(terr.penser(tout, 0.5, Caractere.TERRITORIAL, Activite.TOUJOURS) != Action.ATTAQUER, "territorial : n'attaque pas tout de suite");
        int t = 0;
        while (terr.action() != Action.ATTAQUER && t < 40) { terr.penser(tout, 0.5, Caractere.TERRITORIAL, Activite.TOUJOURS); t++; }
        verifier(terr.action() == Action.ATTAQUER && t > 3, "territorial : attaque si on reste trop près (" + t / 2.0 + " s)");
        Cerveau frappe = new Cerveau(12);
        frappe.blesser(Caractere.TERRITORIAL);
        verifier(frappe.penser(avec(p0, 2, false, Double.NaN, 0, 6, false, false, false, false, false, true), 0.5, Caractere.TERRITORIAL, Activite.TOUJOURS) == Action.ATTAQUER,
            "territorial frappé : riposte");
        Cerveau peureux = new Cerveau(13);
        peureux.blesser(Caractere.PAISIBLE);
        verifier(peureux.penser(avec(p0, 2, false, Double.NaN, 0, 6, false, false, false, false, false, true), 0.5, Caractere.PAISIBLE, Activite.TOUJOURS) == Action.FUIR,
            "paisible frappé : fuit");
        vivre(peureux, p0, 40, Caractere.PAISIBLE, Activite.TOUJOURS);
        verifier(peureux.action() != Action.FUIR && peureux.peur() < 0.1, "la peur retombe quand la menace est partie");
        Cerveau debandade = new Cerveau(14);
        debandade.effrayer(0.8);
        verifier(debandade.penser(p0, 0.5, Caractere.PAISIBLE, Activite.TOUJOURS) == Action.FUIR, "un compagnon effrayé fait fuir (débandade)");
        Cerveau affame = new Cerveau(15);
        affame.faim = 0.9;
        verifier(affame.penser(avec(p0, 2, false, Double.NaN, 0, 4, true, false, false, true, false, true), 0.5, Caractere.CRAINTIF, Activite.TOUJOURS) == Action.FUIR,
            "la peur passe avant la faim");
        Cerveau endormi = new Cerveau(16);
        endormi.penser(nuit, 0.5, Caractere.CRAINTIF, Activite.DIURNE);
        verifier(endormi.penser(avec(p0, 2, false, Double.NaN, 0, 4, true, false, true, false, false, true), 0.5, Caractere.CRAINTIF, Activite.DIURNE) == Action.FUIR,
            "réveillé par un joueur qui approche en courant : fuit");

        // sauvegarde
        Cerveau a = new Cerveau(20);
        a.faim = 0.25; a.soif = 0.5; a.fatigue = 0.75;
        Cerveau b = new Cerveau(21);
        b.relire(a.sauver());
        verifier(Math.abs(b.faim() - 0.25) < 1e-3 && Math.abs(b.soif() - 0.5) < 1e-3 && Math.abs(b.fatigue() - 0.75) < 1e-3, "besoins sauvés et relus");
        b.relire("n'importe quoi");
        verifier(Math.abs(b.faim() - 0.25) < 1e-3, "sauvegarde abîmée ignorée");

        // géométrie
        Random r = new Random(3);
        boolean dansLaisse = true;
        for (int i = 0; i < 200; i++) {
            double[] q = Geometrie.errance(r.nextDouble() * 30 - 15, r.nextDouble() * 30 - 15, 0, 0, 20, r, (x, z) -> true);
            if (Math.hypot(q[0], q[1]) > 20) dansLaisse = false;
        }
        verifier(dansLaisse, "errance : toujours dans la laisse");
        double[] q = Geometrie.errance(0, 0, 0, 0, 20, r, (x, z) -> x > 0);
        verifier(q[0] > 0, "errance : le filtre (zone, sol) est respecté");
        double[] f = Geometrie.fuite(0, 0, -5, 0, 0, 0, 30, 10);
        verifier(f[0] > 9 && Math.abs(f[1]) < 1e-6, "fuite : droit devant, à l'opposé du joueur");
        double[] bord = Geometrie.fuite(18, 0, 10, 0, 0, 0, 20, 10);
        verifier(Math.hypot(bord[0], bord[1]) <= 20 + 1e-6 && Geometrie.distance(bord[0], bord[1], 10, 0) > 8, "fuite au bord de la laisse : part de biais sans sortir");
        verifier(Geometrie.centre(new double[]{0, 4, 8}, new double[]{0, 0, 3}, 3)[0] == 4, "centre du troupeau");

        // zones
        YamlConfiguration zy = YamlConfiguration.loadConfiguration(new java.io.StringReader(String.join("\n",
            "pre: { monde: world, rectangle: [0, 0, 10, 20], points: [ { type: eau, position: [5, 64, 18] }, { type: eau, position: [1, 64, 1] } ] }",
            "rond: { cercle: [100, 100, 5] }",
            "triangle: { polygone: [[0, 0], [10, 0], [0, 10]] }",
            "faux: { carre: 3 }")));
        List<String> pb = new ArrayList<>();
        Zone pre = Zone.lire("pre", zy.getConfigurationSection("pre"), pb), rond = Zone.lire("rond", zy.getConfigurationSection("rond"), pb),
            tri = Zone.lire("triangle", zy.getConfigurationSection("triangle"), pb), faux = Zone.lire("faux", zy.getConfigurationSection("faux"), pb);
        verifier(pre.contient(5, 10) && !pre.contient(11, 10) && rond.contient(103, 100) && !rond.contient(106, 100), "zones : rectangle et cercle");
        verifier(tri.contient(2, 2) && !tri.contient(8, 8), "zones : polygone");
        verifier(faux == null && pb.size() == 1, "zone sans forme signalée");
        verifier(pre.plusProche("eau", 4, 15).y() == 64 && pre.plusProche("eau", 4, 15).z() == 18 && pre.plusProche("abri", 0, 0) == null, "point d'intérêt le plus proche");

        // fichiers
        String ex;
        try (InputStream in = TestMobs.class.getClassLoader().getResourceAsStream("exemples/mobs/exemple.yml")) {
            ex = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(new java.io.StringReader(ex));
        pb.clear();
        List<DefinitionMob> defs = new ArrayList<>();
        for (String k : y.getKeys(false)) defs.add(DefinitionMob.lire(k, y.getConfigurationSection(k), pb));
        pb.forEach(x -> System.out.println("  problème : " + x));
        DefinitionMob golem = defs.get(0), mouton = defs.get(1);
        verifier(pb.isEmpty() && defs.size() == 3, "exemple : 3 mobs lus sans problème");
        verifier(golem.type().equals("iron_golem") && golem.modeles().equals(List.of("golem_rubis")) && golem.tailleMin() == 0.9 && golem.tailleMax() == 1.1
            && golem.caractere() == Caractere.TERRITORIAL && golem.activite() == Activite.TOUJOURS && golem.laisse() == 12 && !golem.boit()
            && "salut".equals(golem.animation("alerte")) && golem.ennui().equals(List.of("salut")) && golem.cerveau(), "golem : tous les réglages lus");
        verifier(mouton.cohesion() == 6 && mouton.modeles().isEmpty() && mouton.nourriture().contains("short_grass"), "mouton : troupeau et nourriture");
        YamlConfiguration yf = YamlConfiguration.loadConfiguration(new java.io.StringReader(String.join("\n",
            "a: { type: decor }", "b: { caractere: furieux, ia: magique, troupeau: true }",
            "c: { type: decor, modele: oiseau, envol: 4, animations: { envol: fly } }",
            "p: { mob: b, centre: [1, 2, 3], nombre: 4, troupeau: true }", "q: { mob: b }")));
        pb.clear();
        verifier(DefinitionMob.lire("a", yf.getConfigurationSection("a"), pb) == null, "décor sans modèle refusé");
        DefinitionMob bb = DefinitionMob.lire("b", yf.getConfigurationSection("b"), pb);
        verifier(bb.caractere() == Caractere.PAISIBLE && bb.ia().equals("vorace") && bb.cohesion() == 6 && pb.size() == 3, "valeurs inconnues signalées, troupeau: true = 6 blocs");
        DefinitionMob oiseau = DefinitionMob.lire("c", yf.getConfigurationSection("c"), pb);
        verifier(oiseau.decor() && !oiseau.cerveau() && oiseau.envol() == 4 && "fly".equals(oiseau.animation("envol")), "décor qui s'envole");
        pb.clear();
        Apparition ap = Apparition.lire("p", yf.getConfigurationSection("p"), pb);
        verifier(ap != null && ap.nombre() == 4 && ap.troupeau() && ap.x() == 1 && ap.z() == 3 && ap.monde().equals("world") && ap.reapparition() == 300, "apparition lue");
        verifier(Apparition.lire("q", yf.getConfigurationSection("q"), pb) == null && pb.size() == 1, "apparition sans centre refusée");

        System.out.println(echecs == 0 ? "TOUS LES TESTS PASSENT" : echecs + " ECHEC(S)");
        System.exit(echecs == 0 ? 0 : 1);
    }
}
