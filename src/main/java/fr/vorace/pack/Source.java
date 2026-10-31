package fr.vorace.pack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Un pack de ressources à fusionner : un dossier ou un fichier .zip.
 * Tout le contenu est chargé en mémoire, chemin relatif (séparateur "/") vers octets.
 */
public final class Source {

    private final String nom;
    private final Path chemin;
    private final Map<String, byte[]> fichiers;

    private Source(String nom, Path chemin, Map<String, byte[]> fichiers) {
        this.nom = nom;
        this.chemin = chemin;
        this.fichiers = fichiers;
    }

    public String nom() { return nom; }
    public Path chemin() { return chemin; }
    public Map<String, byte[]> fichiers() { return fichiers; }

    /** Source construite en mémoire (tests, packs générés par Vorace lui-même). */
    public static Source enMemoire(String nom, Map<String, byte[]> fichiers) {
        return new Source(nom, null, new LinkedHashMap<>(fichiers));
    }

    /** Charge un dossier ou un .zip. */
    public static Source charger(String nom, Path chemin) throws IOException {
        Map<String, byte[]> brut = new LinkedHashMap<>();
        if (Files.isDirectory(chemin)) {
            try (Stream<Path> flux = Files.walk(chemin)) {
                List<Path> liste = flux.filter(Files::isRegularFile).sorted().toList();
                for (Path p : liste) {
                    String rel = chemin.relativize(p).toString().replace('\\', '/');
                    if (!Filtre.ignore(rel)) brut.put(rel, Files.readAllBytes(p));
                }
            }
        } else {
            try (ZipFile zip = new ZipFile(chemin.toFile())) {
                Enumeration<? extends ZipEntry> it = zip.entries();
                while (it.hasMoreElements()) {
                    ZipEntry e = it.nextElement();
                    if (e.isDirectory()) continue;
                    String rel = e.getName().replace('\\', '/');
                    while (rel.startsWith("/")) rel = rel.substring(1);
                    if (rel.contains("../") || Filtre.ignore(rel)) continue;
                    try (InputStream in = zip.getInputStream(e)) {
                        brut.put(rel, in.readAllBytes());
                    }
                }
            }
        }
        return new Source(nom, chemin, retirerDossierRacine(brut));
    }

    /**
     * Beaucoup de packs sont zippés avec un dossier racine ("MonPack/assets/..."). Si rien
     * n'est à la racine mais qu'un seul dossier contient assets/ ou pack.mcmeta, on le retire.
     */
    static Map<String, byte[]> retirerDossierRacine(Map<String, byte[]> brut) {
        for (int garde = 0; garde < 3; garde++) {
            boolean aLaRacine = false;
            String prefixe = null;
            boolean unique = true;
            for (String k : brut.keySet()) {
                if (k.equals("pack.mcmeta") || k.startsWith("assets/")) { aLaRacine = true; break; }
                int i = k.indexOf('/');
                String tete = i < 0 ? "" : k.substring(0, i + 1);
                if (prefixe == null) prefixe = tete;
                else if (!prefixe.equals(tete)) unique = false;
            }
            if (aLaRacine || !unique || prefixe == null || prefixe.isEmpty()) return brut;
            Map<String, byte[]> sans = new LinkedHashMap<>();
            for (Map.Entry<String, byte[]> e : brut.entrySet()) sans.put(e.getKey().substring(prefixe.length()), e.getValue());
            brut = sans;
        }
        return brut;
    }

    /** Fichiers à ne jamais mettre dans le pack final. */
    public static final class Filtre {
        private static final List<String> MOTIFS = new ArrayList<>(List.of(
            ".ds_store", "thumbs.db", "desktop.ini", "__macosx/", ".git/", ".idea/", ".vscode/",
            "*.psd", "*.bbmodel", "*.xcf", "*.kra", "*.blend", "*.md", "*.txt.bak", "*.tmp"));

        private Filtre() {}

        public static void ajouterMotifs(List<String> motifs) {
            for (String m : motifs) if (m != null && !m.isBlank()) MOTIFS.add(m.toLowerCase(Locale.ROOT).trim());
        }

        public static boolean ignore(String chemin) {
            String c = chemin.toLowerCase(Locale.ROOT);
            String nomFichier = c.substring(c.lastIndexOf('/') + 1);
            for (String m : MOTIFS) {
                if (m.endsWith("/")) {
                    if (c.startsWith(m) || c.contains("/" + m)) return true;
                } else if (m.startsWith("*.")) {
                    if (nomFichier.endsWith(m.substring(1))) return true;
                } else if (nomFichier.equals(m) || c.equals(m)) {
                    return true;
                }
            }
            return false;
        }
    }
}
