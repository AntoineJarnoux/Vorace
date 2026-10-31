package fr.vorace.conversion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Convertit le contenu d'autres plugins (Nexo, Oraxen, ItemsAdder, ModelEngine) en fichiers Vorace.
 * Ne lit que leurs fichiers de configuration et leurs ressources, jamais leur code. Sans Bukkit (testable).
 */
public final class Conversion {

    private Conversion() {}

    public enum Source {
        NEXO("nexo", "Nexo"),
        ORAXEN("oraxen", "Oraxen"),
        ITEMSADDER("itemsadder", "ItemsAdder"),
        MODELENGINE("modelengine", "ModelEngine");

        public final String id;
        public final String nom;

        Source(String id, String nom) {
            this.id = id;
            this.nom = nom;
        }

        public static Source lire(String s) {
            String n = s.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
            for (Source x : values()) if (x.id.equals(n)) return x;
            if (n.equals("ia")) return ITEMSADDER;
            if (n.equals("meg") || n.equals("me")) return MODELENGINE;
            return null;
        }
    }

    /**
     * @param remarques  les premières remarques (pour les afficher en jeu)
     * @param rapport    fichier du rapport complet
     */
    public record Resultat(Source source, String resume, int nbRemarques, List<String> remarques, Path rapport) {}

    /** Dossier du plugin dans plugins/ (le nom exact peut varier en majuscules). */
    public static Path dossier(Path plugins, Source s) {
        Path p = plugins.resolve(s.nom);
        if (Files.isDirectory(p)) return p;
        try (Stream<Path> l = Files.list(plugins)) {
            return l.filter(x -> Files.isDirectory(x) && x.getFileName().toString().equalsIgnoreCase(s.nom)).findFirst().orElse(p);
        } catch (IOException e) {
            return p;
        }
    }

    /** Vrai si le plugin a du contenu à convertir. */
    public static boolean present(Path plugins, Source s) {
        Path d = dossier(plugins, s);
        return switch (s) {
            case NEXO, ORAXEN -> Files.isDirectory(d.resolve("items")) || Files.isDirectory(d.resolve("glyphs"));
            case ITEMSADDER -> Files.isDirectory(d.resolve("contents")) || Files.isDirectory(d.resolve("data/items_packs"));
            case MODELENGINE -> Files.isDirectory(d.resolve("blueprints"));
        };
    }

    /**
     * Convertit un plugin. Les fichiers d'une conversion précédente de la même source sont remplacés ;
     * les objets écrits à la main dans Vorace ne sont jamais touchés.
     * @param plugins dossier plugins/ du serveur
     * @param vorace  plugins/Vorace
     */
    public static Resultat convertir(Path plugins, Path vorace, Source s) throws IOException {
        Path racine = dossier(plugins, s);
        supprimer(vorace.resolve("ressources/textures/converti").resolve(s.id));
        supprimer(vorace.resolve("ressources/modeles/converti").resolve(s.id));
        Ressources r;
        if (s == Source.ITEMSADDER) {
            r = ConvertisseurItemsAdder.ressources(racine);
        } else {
            r = new Ressources();
            r.ajouterAssets(racine.resolve("pack/assets"));
            r.chercherAssets(racine.resolve("pack"), 4);
            r.ajouterEspace("minecraft", racine.resolve("pack")); // ancienne organisation d'Oraxen : pack/textures, pack/models
        }
        Ecrivain e = new Ecrivain(s.id, vorace, r, idsExistants(vorace.resolve("objets"), s));
        e.glyphesPris.addAll(idsExistants(vorace.resolve("glyphes"), s));
        e.raccourcisPris.addAll(raccourcisExistants(vorace.resolve("glyphes"), s));
        int sons = 0;
        switch (s) {
            case NEXO, ORAXEN -> {
                new ConvertisseurNexo(e, s.id).convertir(racine);
                sons = copierSons(racine.resolve("pack/assets"), vorace.resolve("pack/sources/converti-" + s.id), e);
            }
            case ITEMSADDER -> new ConvertisseurItemsAdder(e).convertir(racine);
            case MODELENGINE -> {
                supprimer(vorace.resolve("modeles-animes/modelengine"));
                new ConvertisseurModelEngine(e).convertir(racine);
            }
        }
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        Path rapport = e.terminer("Conversion de " + s.nom + " vers Vorace (" + date + ")\nDepuis : " + racine
            + (sons > 0 ? "\n" + sons + " fichier(s) de sons recopiés dans plugins/Vorace/pack/sources/converti-" + s.id : ""));
        return new Resultat(s, e.resume(), e.remarques.size(), List.copyOf(e.remarques.subList(0, Math.min(5, e.remarques.size()))), rapport);
    }

    /** Identifiants déjà pris dans objets/ ou glyphes/ (hors fichier d'une conversion précédente de la même source). */
    static Set<String> idsExistants(Path dossier, Source s) {
        Set<String> ids = new HashSet<>();
        Path ancien = dossier.resolve("converti/" + s.id + ".yml");
        for (Path f : Yml.fichiers(dossier)) {
            if (f.equals(ancien)) continue;
            try {
                ids.addAll(Yml.lire(f).keySet());
            } catch (IOException | RuntimeException ignore) {
                // fichier invalide : Vorace le signale déjà au chargement
            }
        }
        return ids;
    }

    /** Raccourcis des glyphes déjà là (« :id: » compris). */
    static Set<String> raccourcisExistants(Path dossier, Source s) {
        Set<String> r = new HashSet<>();
        Path ancien = dossier.resolve("converti/" + s.id + ".yml");
        for (Path f : Yml.fichiers(dossier)) {
            if (f.equals(ancien)) continue;
            try {
                Yml.lire(f).forEach((id, v) -> {
                    r.add(":" + id + ":");
                    if (v instanceof java.util.Map<?, ?> m) r.addAll(Yml.liste(Yml.carte(m), "raccourcis"));
                });
            } catch (IOException | RuntimeException ignore) {
                // fichier invalide : signalé par Vorace
            }
        }
        return r;
    }

    /** Recopie les sons (assets/&lt;espace&gt;/sounds et sounds.json) dans un pack source de Vorace. */
    private static int copierSons(Path assets, Path pack, Ecrivain e) throws IOException {
        supprimer(pack);
        if (!Files.isDirectory(assets)) return 0;
        int n = 0;
        try (Stream<Path> espaces = Files.list(assets)) {
            for (Path ns : espaces.filter(Files::isDirectory).toList()) {
                Path json = ns.resolve("sounds.json");
                if (Files.isRegularFile(json) && e.copier(json, pack.resolve("assets").resolve(assets.relativize(json).toString()))) n++;
                Path sons = ns.resolve("sounds");
                if (!Files.isDirectory(sons)) continue;
                try (Stream<Path> w = Files.walk(sons)) {
                    for (Path f : w.filter(Files::isRegularFile).toList()) {
                        if (e.copier(f, pack.resolve("assets").resolve(assets.relativize(f).toString()))) n++;
                    }
                }
            }
        }
        if (n > 0) {
            Files.writeString(pack.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":46,\"description\":\"Sons convertis par Vorace\"}}", StandardCharsets.UTF_8);
        }
        return n;
    }

    private static void supprimer(Path d) throws IOException {
        if (!Files.exists(d)) return;
        try (Stream<Path> w = Files.walk(d)) {
            for (Path p : w.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        }
    }
}
