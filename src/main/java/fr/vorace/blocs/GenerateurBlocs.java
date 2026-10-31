package fr.vorace.blocs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.vorace.objets.GenerateurPack;
import fr.vorace.pack.Journal;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Fichiers du pack pour les blocs personnalisés (sans Bukkit, testable sans serveur). */
public final class GenerateurBlocs {

    /**
     * Apparence d'un bloc.
     * @param faces  textures : « tout », ou « haut », « bas », « cotes », « nord », « sud », « est », « ouest »
     * @param modele modèle Blockbench (ressources/modeles), ou null
     */
    public record Apparence(String id, int numero, Map<String, String> faces, String modele) {}

    private GenerateurBlocs() {}

    public static Map<String, byte[]> generer(List<Apparence> blocs, Path ressources, Journal journal) {
        Map<String, byte[]> sortie = new TreeMap<>();
        if (blocs.isEmpty()) return sortie;
        Path textures = ressources.resolve("textures");
        Map<String, String> modeleParEtat = new HashMap<>();

        for (Apparence b : blocs) {
            JsonObject modele;
            if (b.modele() != null) {
                Path f = ressources.resolve("modeles").resolve(b.modele() + ".json");
                JsonElement lu = GenerateurPack.lire(f);
                if (lu == null || !lu.isJsonObject()) {
                    journal.erreur("Bloc " + b.id() + " : modèle introuvable ou illisible (modeles/" + b.modele() + ".json)");
                    continue;
                }
                modele = lu.getAsJsonObject();
                GenerateurPack.remapperTextures(modele, textures, sortie, journal, b.id());
            } else {
                modele = modeleCube(b, textures, sortie, journal);
                if (modele == null) continue;
            }
            sortie.put("assets/" + GenerateurPack.NS + "/models/block/" + b.id() + ".json", modele.toString().getBytes(StandardCharsets.UTF_8));
            modeleParEtat.put(EtatsNoteBlock.etat(b.numero()), GenerateurPack.NS + ":block/" + b.id());
        }

        // tous les états du bloc à note : ceux qui ne sont pas à nous gardent le modèle du jeu
        JsonObject variantes = new JsonObject();
        for (String instrument : EtatsNoteBlock.INSTRUMENTS) {
            for (int note = 0; note < EtatsNoteBlock.NOTES; note++) {
                String cle = "instrument=" + instrument + ",note=" + note;
                String m = modeleParEtat.getOrDefault(cle, "minecraft:block/note_block");
                for (String alim : new String[]{"false", "true"}) {
                    JsonObject v = new JsonObject();
                    v.addProperty("model", m);
                    variantes.add(cle + ",powered=" + alim, v);
                }
            }
        }
        JsonObject etats = new JsonObject();
        etats.add("variants", variantes);
        sortie.put("assets/minecraft/blockstates/note_block.json", etats.toString().getBytes(StandardCharsets.UTF_8));
        return sortie;
    }

    private static JsonObject modeleCube(Apparence b, Path textures, Map<String, byte[]> sortie, Journal journal) {
        Map<String, String> f = new LinkedHashMap<>(b.faces());
        if (f.isEmpty()) {
            journal.erreur("Bloc " + b.id() + " : ni « textures » ni « modele » dans la section bloc");
            return null;
        }
        for (String tex : f.values()) {
            if (!GenerateurPack.copierTexture(tex, textures, sortie)) {
                journal.erreur("Bloc " + b.id() + " : texture introuvable (ressources/textures/" + tex + ".png)");
                return null;
            }
        }
        JsonObject m = new JsonObject();
        JsonObject t = new JsonObject();
        String tout = f.get("tout");
        if (tout != null && f.size() == 1) {
            m.addProperty("parent", "minecraft:block/cube_all");
            t.addProperty("all", ref(tout));
        } else {
            String cotes = f.getOrDefault("cotes", tout != null ? tout : f.values().iterator().next());
            m.addProperty("parent", "minecraft:block/cube");
            t.addProperty("up", ref(f.getOrDefault("haut", cotes)));
            t.addProperty("down", ref(f.getOrDefault("bas", f.getOrDefault("haut", cotes))));
            t.addProperty("north", ref(f.getOrDefault("nord", cotes)));
            t.addProperty("south", ref(f.getOrDefault("sud", cotes)));
            t.addProperty("east", ref(f.getOrDefault("est", cotes)));
            t.addProperty("west", ref(f.getOrDefault("ouest", cotes)));
            t.addProperty("particle", ref(cotes));
        }
        m.add("textures", t);
        return m;
    }

    private static String ref(String texture) {
        return GenerateurPack.NS + ":item/" + texture;
    }
}
