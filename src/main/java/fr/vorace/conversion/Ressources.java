package fr.vorace.conversion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Où trouver les fichiers d'un pack de ressources d'un autre plugin : une liste de dossiers « assets »
 * (assets/&lt;espace&gt;/textures/..., assets/&lt;espace&gt;/models/...), plus des dossiers propres à un espace de noms
 * (ancienne organisation d'ItemsAdder : contents/&lt;espace&gt;/textures/...).
 */
final class Ressources {

    private final List<Path> assets = new ArrayList<>();
    private final List<String[]> parEspace = new ArrayList<>(); // {espace, dossier}

    void ajouterAssets(Path dossier) {
        if (Files.isDirectory(dossier)) assets.add(dossier);
    }

    /** Dossier qui contient directement textures/, models/... d'un seul espace de noms. */
    void ajouterEspace(String espace, Path dossier) {
        if (Files.isDirectory(dossier)) parEspace.add(new String[]{espace, dossier.toString()});
    }

    /** Ajoute tous les « assets » trouvés sous ce dossier (packs imbriqués, contenus ItemsAdder...). */
    void chercherAssets(Path racine, int profondeur) {
        if (!Files.isDirectory(racine)) return;
        try (Stream<Path> s = Files.walk(racine, profondeur)) {
            s.filter(p -> Files.isDirectory(p) && p.getFileName() != null && p.getFileName().toString().equals("assets")).sorted().forEach(this::ajouterAssets);
        } catch (IOException ignore) {
            // dossier illisible : ignoré
        }
    }

    boolean vide() { return assets.isEmpty() && parEspace.isEmpty(); }

    /**
     * Fichier d'une ressource.
     * @param type  "textures", "models", "sounds"...
     * @param ref   référence du jeu (« espace:chemin » ou « chemin » = minecraft)
     * @param ext   extension (".png", ".json")
     */
    Path trouver(String type, String ref, String ext) {
        Ref r = Ref.lire(ref);
        String chemin = r.chemin().endsWith(ext) ? r.chemin() : r.chemin() + ext;
        for (Path a : assets) {
            Path f = a.resolve(r.espace()).resolve(type).resolve(chemin);
            if (Files.isRegularFile(f)) return f;
        }
        for (String[] e : parEspace) {
            if (!e[0].equals(r.espace()) && !r.espace().equals("minecraft")) continue;
            Path f = Path.of(e[1]).resolve(type).resolve(chemin);
            if (Files.isRegularFile(f)) return f;
        }
        return null;
    }

    /** Référence du jeu découpée. */
    record Ref(String espace, String chemin) {
        static Ref lire(String ref) {
            String s = ref.trim().replace('\\', '/');
            int i = s.indexOf(':');
            String espace = i < 0 ? "minecraft" : s.substring(0, i).toLowerCase(Locale.ROOT);
            String chemin = i < 0 ? s : s.substring(i + 1);
            while (chemin.startsWith("/")) chemin = chemin.substring(1);
            return new Ref(espace.isEmpty() ? "minecraft" : espace, chemin);
        }

        @Override
        public String toString() { return espace + ":" + chemin; }
    }
}
