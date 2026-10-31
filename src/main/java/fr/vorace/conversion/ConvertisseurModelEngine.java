package fr.vorace.conversion;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Reprend les modèles de ModelEngine (plugins/ModelEngine/blueprints/**.bbmodel) : ce sont des projets Blockbench,
 * que Vorace lit directement. Ils sont recopiés dans modeles-animes/modelengine/. Les os spéciaux de ModelEngine
 * (« hitbox », « shadow ») sont cachés par Vorace, et l'os « hitbox » donne la taille de la hitbox.
 */
final class ConvertisseurModelEngine {

    private final Ecrivain e;

    ConvertisseurModelEngine(Ecrivain e) {
        this.e = e;
    }

    void convertir(Path racine) {
        Path blueprints = racine.resolve("blueprints");
        if (!Files.isDirectory(blueprints)) { e.remarque("dossier introuvable : " + blueprints); return; }
        List<Path> fichiers;
        try (Stream<Path> s = Files.walk(blueprints)) {
            fichiers = s.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".bbmodel")).sorted().toList();
        } catch (IOException ex) {
            e.remarque("dossier illisible : " + blueprints + " (" + ex.getMessage() + ")");
            return;
        }
        Path cible = e.vorace.resolve("modeles-animes/modelengine");
        Set<String> pris = new LinkedHashSet<>();
        for (Path f : fichiers) {
            // ModelEngine nomme un modèle d'après son fichier, sans les dossiers
            String nom = f.getFileName().toString();
            nom = nom.substring(0, nom.length() - ".bbmodel".length()).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
            String id = nom;
            for (int n = 2; !pris.add(id); n++) id = nom + "_" + n;
            if (!id.equals(nom)) e.remarque("ModelEngine : deux modèles « " + nom + " », le second devient « modelengine/" + id + " »");
            if (e.copier(f, cible.resolve(id + ".bbmodel"))) {
                e.animes++;
                verifier(f, id);
            }
        }
    }

    /** Signale ce que Vorace ne fait pas encore dans un modèle (os à comportement spécial de ModelEngine). */
    private void verifier(Path f, String id) {
        try {
            JsonElement racine = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
            if (!racine.isJsonObject()) return;
            JsonObject o = racine.getAsJsonObject();
            List<String> speciaux = new ArrayList<>();
            if (o.has("outliner") && o.get("outliner").isJsonArray()) parcourir(o.getAsJsonArray("outliner"), speciaux);
            if (!speciaux.isEmpty())
                e.remarque("ModelEngine « " + id + " » : os à comportement spécial affichés comme des os normaux " + speciaux);
            boolean anime = o.has("animations") && o.get("animations").isJsonArray() && !o.getAsJsonArray("animations").isEmpty();
            if (!anime) e.remarque("ModelEngine « " + id + " » : aucune animation");
        } catch (IOException | RuntimeException ex) {
            e.remarque("ModelEngine « " + id + " » : fichier illisible (" + ex.getMessage() + ")");
        }
    }

    private static void parcourir(JsonArray liste, List<String> speciaux) {
        for (JsonElement x : liste) {
            if (!x.isJsonObject()) continue;
            JsonObject g = x.getAsJsonObject();
            String nom = g.has("name") ? g.get("name").getAsString() : "";
            String n = nom.toLowerCase(Locale.ROOT);
            // préfixes de ModelEngine : h_ tête qui suit le regard, i_ objet tenu, p_ / seat monture, tag_ nom, b_ / ob_ ...
            if (n.startsWith("h_") || n.startsWith("hi_") || n.startsWith("i_") || n.startsWith("il_") || n.startsWith("ir_")
                || n.startsWith("p_") || n.startsWith("ln_") || n.startsWith("tag_") || n.startsWith("seat") || n.startsWith("g_")) {
                if (speciaux.size() < 8) speciaux.add(nom);
            }
            if (g.has("children") && g.get("children").isJsonArray()) parcourir(g.getAsJsonArray("children"), speciaux);
        }
    }
}
