package fr.vorace.objets;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.vorace.pack.FormatPack;
import fr.vorace.pack.Fusion;
import fr.vorace.pack.Journal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Écrit les fichiers du pack pour les objets de Vorace (espace de noms "vorace").
 * <ul>
 *   <li>1.21.4 et plus : assets/vorace/items/&lt;id&gt;.json + composant item_model "vorace:&lt;id&gt;" ;</li>
 *   <li>1.21.2 et 1.21.3 : composant item_model qui pointe directement vers assets/vorace/models/item/&lt;id&gt;.json ;</li>
 *   <li>1.21 et 1.21.1 : custom_model_data + "overrides" dans le modèle de l'objet de base.</li>
 * </ul>
 */
public final class GenerateurPack {

    public static final String NS = "vorace";

    /** Objets tenus en main comme un outil dans le jeu de base (pour 1.21 / 1.21.1). */
    private static final Set<String> OUTILS = Set.of("stick", "blaze_rod", "breeze_rod", "bone", "bamboo",
        "fishing_rod", "carrot_on_a_stick", "warped_fungus_on_a_stick", "mace", "trident", "debug_stick");

    private GenerateurPack() {}

    public static Map<String, byte[]> generer(List<Visuel> visuels, Path ressources, FormatPack format, Journal journal) {
        Map<String, byte[]> sortie = new TreeMap<>();
        Map<String, List<Visuel>> parMateriau = new TreeMap<>();
        Path textures = ressources.resolve("textures");
        Path modeles = ressources.resolve("modeles");

        for (Visuel v : visuels) {
            String chemin = "assets/" + NS + "/models/item/" + v.id() + ".json";
            JsonObject modele;
            if (v.modele() != null) {
                Path f = modeles.resolve(v.modele().endsWith(".json") ? v.modele() : v.modele() + ".json");
                JsonElement lu = lire(f);
                if (lu == null || !lu.isJsonObject()) {
                    journal.erreur("Objet " + v.id() + " : modèle introuvable ou illisible (" + relatif(ressources, f) + ")");
                    continue;
                }
                modele = lu.getAsJsonObject();
                remapperTextures(modele, textures, sortie, journal, v.id());
            } else if (v.texture() != null) {
                if (!copierTexture(v.texture(), textures, sortie)) {
                    journal.erreur("Objet " + v.id() + " : texture introuvable (ressources/textures/" + v.texture() + ".png)");
                    continue;
                }
                modele = new JsonObject();
                modele.addProperty("parent", v.enMain() ? "minecraft:item/handheld" : "minecraft:item/generated");
                JsonObject tex = new JsonObject();
                tex.addProperty("layer0", NS + ":item/" + v.texture());
                modele.add("textures", tex);
            } else {
                continue; // objet sans apparence propre : il garde celle de son objet de base
            }
            sortie.put(chemin, modele.toString().getBytes(StandardCharsets.UTF_8));

            if (format.majeur() >= 46) {
                JsonObject m = new JsonObject();
                m.addProperty("type", "minecraft:model");
                m.addProperty("model", NS + ":item/" + v.id());
                if (v.teinte() != null) {
                    JsonArray tints = new JsonArray();
                    JsonObject t = new JsonObject();
                    t.addProperty("type", "minecraft:dye");
                    t.addProperty("default", v.teinte());
                    tints.add(t);
                    m.add("tints", tints);
                }
                JsonObject def = new JsonObject();
                def.add("model", m);
                sortie.put("assets/" + NS + "/items/" + v.id() + ".json", def.toString().getBytes(StandardCharsets.UTF_8));
            } else if (format.majeur() < 42) {
                parMateriau.computeIfAbsent(v.materiau(), k -> new ArrayList<>()).add(v);
            }
        }

        // 1.21 / 1.21.1 : un modèle de base par objet Minecraft utilisé, avec ses "overrides"
        for (Map.Entry<String, List<Visuel>> e : parMateriau.entrySet()) {
            String mat = e.getKey();
            JsonObject base = new JsonObject();
            base.addProperty("parent", OUTILS.contains(mat) || mat.endsWith("_sword") || mat.endsWith("_axe")
                || mat.endsWith("_pickaxe") || mat.endsWith("_shovel") || mat.endsWith("_hoe") ? "minecraft:item/handheld" : "minecraft:item/generated");
            JsonObject tex = new JsonObject();
            tex.addProperty("layer0", "minecraft:item/" + mat);
            base.add("textures", tex);
            JsonArray overrides = new JsonArray();
            e.getValue().stream().sorted((a, b) -> Integer.compare(a.cmd(), b.cmd())).forEach(v -> {
                JsonObject o = new JsonObject();
                JsonObject p = new JsonObject();
                p.addProperty("custom_model_data", v.cmd());
                o.add("predicate", p);
                o.addProperty("model", NS + ":item/" + v.id());
                overrides.add(o);
            });
            base.add("overrides", overrides);
            sortie.put("assets/minecraft/models/item/" + mat + ".json", base.toString().getBytes(StandardCharsets.UTF_8));
        }
        return sortie;
    }

    /**
     * Les modèles exportés par Blockbench citent leurs textures de plusieurs façons ("epee", "item/epee",
     * "minecraft:item/epee"...). Si une texture du même nom existe dans ressources/textures, on la relie à
     * "vorace:item/..." et on la copie dans le pack. Les textures du jeu de base restent telles quelles.
     */
    static void remapperTextures(JsonObject modele, Path textures, Map<String, byte[]> sortie, Journal journal, String id) {
        if (!modele.has("textures") || !modele.get("textures").isJsonObject()) return;
        JsonObject t = modele.getAsJsonObject("textures");
        for (Map.Entry<String, JsonElement> e : new LinkedHashMap<>(asMap(t)).entrySet()) {
            if (!e.getValue().isJsonPrimitive()) continue;
            String valeur = e.getValue().getAsString();
            if (valeur.startsWith("#")) continue;
            String ns = null;
            String chemin = valeur;
            int deuxPoints = valeur.indexOf(':');
            if (deuxPoints >= 0) {
                ns = valeur.substring(0, deuxPoints);
                chemin = valeur.substring(deuxPoints + 1);
            }
            if (ns != null && !ns.equals("minecraft") && !ns.equals(NS)) continue; // texture d'un autre pack
            String trouve = null;
            List<String> candidats = new ArrayList<>(List.of(chemin));
            for (String prefixe : List.of("item/", "items/", "block/", "blocks/", "textures/")) {
                if (chemin.startsWith(prefixe)) candidats.add(chemin.substring(prefixe.length()));
            }
            candidats.add(chemin.substring(chemin.lastIndexOf('/') + 1));
            for (String c : candidats) {
                if (Files.isRegularFile(textures.resolve(c + ".png"))) { trouve = c; break; }
            }
            if (trouve != null) {
                copierTexture(trouve, textures, sortie);
                t.addProperty(e.getKey(), NS + ":item/" + trouve);
            } else if (ns == null || ns.equals(NS)) {
                journal.conflit("Objet " + id + " : texture « " + valeur + " » absente de ressources/textures (gardée telle quelle)");
            }
        }
    }

    private static Map<String, JsonElement> asMap(JsonObject o) {
        Map<String, JsonElement> m = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : o.entrySet()) m.put(e.getKey(), e.getValue());
        return m;
    }

    private static boolean copierTexture(String chemin, Path textures, Map<String, byte[]> sortie) {
        Path png = textures.resolve(chemin + ".png");
        if (!Files.isRegularFile(png)) return false;
        try {
            sortie.put("assets/" + NS + "/textures/item/" + chemin + ".png", Files.readAllBytes(png));
            Path anim = textures.resolve(chemin + ".png.mcmeta");
            if (Files.isRegularFile(anim)) sortie.put("assets/" + NS + "/textures/item/" + chemin + ".png.mcmeta", Files.readAllBytes(anim));
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    private static JsonElement lire(Path f) {
        try {
            return Files.isRegularFile(f) ? Fusion.lireJson(Files.readAllBytes(f)) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static String relatif(Path base, Path f) {
        try { return base.relativize(f).toString().replace('\\', '/'); } catch (Exception e) { return f.toString(); }
    }
}
