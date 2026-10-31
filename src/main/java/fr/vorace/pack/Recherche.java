package fr.vorace.pack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Trouve les packs à fusionner à partir des chemins de la config (relatifs au dossier du serveur).
 * Un chemin qui finit par "/*" désigne chaque dossier et chaque .zip qu'il contient, par ordre alphabétique.
 */
public final class Recherche {

    /** Un chemin de la config et ce qu'il désigne vraiment. */
    public record Trouve(String motif, Path chemin, boolean present) {
        public String nom() {
            return chemin.getFileName() == null ? motif : chemin.getFileName().toString();
        }
    }

    private Recherche() {}

    public static List<Trouve> trouver(Path racine, List<String> motifs, Path aExclure) {
        List<Trouve> r = new ArrayList<>();
        for (String brut : motifs) {
            if (brut == null || brut.isBlank()) continue;
            String motif = brut.trim().replace('\\', '/');
            if (motif.endsWith("/*")) {
                Path dossier = racine.resolve(motif.substring(0, motif.length() - 2)).normalize();
                if (!Files.isDirectory(dossier)) {
                    r.add(new Trouve(motif, dossier, false));
                    continue;
                }
                try (Stream<Path> enfants = Files.list(dossier)) {
                    for (Path p : enfants.sorted().toList()) {
                        if (aExclure != null && p.normalize().startsWith(aExclure.normalize())) continue;
                        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        if (n.startsWith(".")) continue;
                        if (Files.isDirectory(p) || n.endsWith(".zip")) r.add(new Trouve(motif, p, true));
                    }
                } catch (IOException e) {
                    r.add(new Trouve(motif, dossier, false));
                }
            } else {
                Path p = racine.resolve(motif).normalize();
                boolean ok = Files.isDirectory(p) || (Files.isRegularFile(p) && p.toString().toLowerCase(Locale.ROOT).endsWith(".zip"));
                r.add(new Trouve(motif, p, ok));
            }
        }
        // le même pack cité deux fois n'est chargé qu'une fois (à la position la plus prioritaire)
        List<Trouve> sansDoublon = new ArrayList<>();
        for (int i = 0; i < r.size(); i++) {
            Trouve t = r.get(i);
            boolean plusLoin = false;
            for (int j = i + 1; j < r.size(); j++) {
                if (r.get(j).present() && t.present() && r.get(j).chemin().equals(t.chemin())) { plusLoin = true; break; }
            }
            if (!plusLoin) sansDoublon.add(t);
        }
        return sansDoublon;
    }
}
