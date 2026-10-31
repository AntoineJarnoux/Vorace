package fr.vorace.conversion;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Écrit le résultat d'une conversion dans les dossiers de Vorace : fichiers d'objets et de glyphes, textures et
 * modèles recopiés (avec leurs références réécrites pour Vorace), et un rapport lisible.
 */
final class Ecrivain {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    /** Modèles parents du jeu de base : gardés tels quels. */
    private static final List<String> PARENTS_DU_JEU = List.of("item/", "block/", "builtin/");

    final String source;          // « nexo », « itemsadder », « modelengine »
    final Path vorace;            // plugins/Vorace
    private final Ressources ressources;
    private final Set<String> idsPris;
    final Map<String, Map<String, Object>> objets = new LinkedHashMap<>();
    final Map<String, Map<String, Object>> glyphes = new LinkedHashMap<>();
    /** Ancien identifiant (« nexo:rubis ») vers l'identifiant Vorace. */
    final Map<String, String> correspondances = new LinkedHashMap<>();
    final List<String> remarques = new ArrayList<>();
    private final Map<String, String> texturesCopiees = new HashMap<>();
    /** Glyphes qui existent déjà dans Vorace. */
    final Set<String> glyphesPris = new HashSet<>();
    /** Raccourcis de glyphes déjà utilisés (« :coeur: », « <3 »). */
    final Set<String> raccourcisPris = new HashSet<>();
    int textures, modeles, animes, meubles, blocs, recettes;

    Ecrivain(String source, Path vorace, Ressources ressources, Set<String> idsPris) {
        this.source = source;
        this.vorace = vorace;
        this.ressources = ressources;
        this.idsPris = new HashSet<>(idsPris);
    }

    void remarque(String s) {
        if (remarques.size() < 2000) remarques.add(s);
    }

    /** Identifiant Vorace libre pour un objet (le même que dans l'autre plugin si possible). */
    String id(String voulu, String prefixe) {
        String base = voulu.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_");
        String id = base;
        if (idsPris.contains(id) || objets.containsKey(id)) {
            id = prefixe + "_" + base;
            int n = 2;
            while (idsPris.contains(id) || objets.containsKey(id)) id = prefixe + "_" + base + "_" + (n++);
            remarque("« " + voulu + " » existe déjà dans Vorace : converti sous le nom « " + id + " »");
        }
        idsPris.add(id);
        return id;
    }

    /** Identifiant libre pour un glyphe. */
    String idGlyphe(String voulu, String prefixe) {
        String base = voulu.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        String id = base;
        for (int n = 2; glyphesPris.contains(id) || glyphes.containsKey(id); n++) id = prefixe + "_" + base + (n > 2 ? "_" + (n - 1) : "");
        if (!id.equals(base)) remarque("glyphe « " + voulu + " » existe déjà dans Vorace : converti sous le nom « " + id + " »");
        return id;
    }

    /** Garde les raccourcis libres (et « :id: », que Vorace ajoute tout seul, est réservé). */
    List<String> raccourcis(String id, List<String> voulus, String quoi) {
        raccourcisPris.add(":" + id + ":");
        List<String> r = new ArrayList<>();
        for (String x : voulus) {
            if (x.equals(":" + id + ":")) continue;
            if (!raccourcisPris.add(x)) { remarque(quoi + " : raccourci « " + x + " » déjà utilisé par un autre glyphe, pas repris"); continue; }
            r.add(x);
        }
        return r;
    }

    // ------------------------------------------------------------------ textures et modèles

    private Path dossierTextures() { return vorace.resolve("ressources/textures/converti").resolve(source); }
    private Path dossierModeles() { return vorace.resolve("ressources/modeles/converti").resolve(source); }

    /** Recopie une texture ; renvoie la référence Vorace (« converti/nexo/minecraft/default/rubis ») ou null. */
    String texture(String ref, String quoi) {
        if (ref == null || ref.isBlank()) return null;
        Ressources.Ref r = Ressources.Ref.lire(ref);
        String cle = r.toString().replaceAll("\\.png$", "");
        String deja = texturesCopiees.get(cle);
        if (deja != null) return deja;
        Path f = ressources.trouver("textures", ref, ".png");
        if (f == null) {
            remarque(quoi + " : texture « " + ref + " » introuvable");
            return null;
        }
        String chemin = r.chemin().replaceAll("\\.png$", "");
        String vers = "converti/" + source + "/" + r.espace() + "/" + chemin;
        try {
            Path cible = dossierTextures().resolve(r.espace()).resolve(chemin + ".png");
            Files.createDirectories(cible.getParent());
            Files.copy(f, cible, StandardCopyOption.REPLACE_EXISTING);
            Path anim = f.resolveSibling(f.getFileName() + ".mcmeta");
            if (Files.isRegularFile(anim)) Files.copy(anim, cible.resolveSibling(cible.getFileName() + ".mcmeta"), StandardCopyOption.REPLACE_EXISTING);
            textures++;
        } catch (IOException e) {
            remarque(quoi + " : copie de la texture « " + ref + " » impossible (" + e.getMessage() + ")");
            return null;
        }
        texturesCopiees.put(cle, vers);
        return vers;
    }

    /**
     * Recopie un modèle (.json Blockbench) : ses textures sont recopiées et réécrites, ses modèles parents qui ne
     * sont pas ceux du jeu sont fusionnés dedans (Vorace n'a pas besoin du pack d'origine).
     * @return la référence Vorace du modèle (« converti/nexo/... ») ou null
     */
    String modele(String ref, String quoi) {
        JsonObject m = lireModele(ref, quoi, 0);
        if (m == null) return null;
        return ecrireModele(m, Ressources.Ref.lire(ref).espace() + "/" + Ressources.Ref.lire(ref).chemin().replaceAll("\\.json$", ""), quoi);
    }

    /** Écrit un modèle fabriqué (parent + textures) ; textures = clé du modèle vers référence de texture d'origine. */
    String modeleGenere(String nom, String parent, Map<String, String> texturesModele, String quoi) {
        JsonObject m = new JsonObject();
        m.addProperty("parent", parent == null || parent.isBlank() ? "minecraft:item/generated" : parent);
        JsonObject t = new JsonObject();
        texturesModele.forEach(t::addProperty);
        m.add("textures", t);
        JsonObject plat = aplatir(m, quoi, 0);
        return plat == null ? null : ecrireModele(plat, "genere/" + nom, quoi);
    }

    private String ecrireModele(JsonObject m, String chemin, String quoi) {
        // références de textures -> textures recopiées dans ressources/textures (Vorace les relie à son pack)
        if (m.has("textures") && m.get("textures").isJsonObject()) {
            JsonObject t = m.getAsJsonObject("textures");
            for (String k : new ArrayList<>(t.keySet())) {
                JsonElement v = t.get(k);
                if (!v.isJsonPrimitive()) continue;
                String ref = v.getAsString();
                if (ref.startsWith("#")) continue;
                Ressources.Ref r = Ressources.Ref.lire(ref);
                if (r.espace().equals("minecraft") && ressources.trouver("textures", ref, ".png") == null) continue; // texture du jeu de base
                String copie = texture(ref, quoi);
                if (copie != null) t.addProperty(k, copie);
            }
        }
        try {
            Path cible = dossierModeles().resolve(chemin + ".json");
            Files.createDirectories(cible.getParent());
            Files.writeString(cible, GSON.toJson(m), StandardCharsets.UTF_8);
            modeles++;
        } catch (IOException e) {
            remarque(quoi + " : écriture du modèle impossible (" + e.getMessage() + ")");
            return null;
        }
        return "converti/" + source + "/" + chemin;
    }

    private JsonObject lireModele(String ref, String quoi, int profondeur) {
        Path f = ressources.trouver("models", ref, ".json");
        if (f == null) {
            remarque(quoi + " : modèle « " + ref + " » introuvable");
            return null;
        }
        try {
            JsonElement e = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
            if (!e.isJsonObject()) throw new IOException("pas un objet JSON");
            return aplatir(e.getAsJsonObject(), quoi, profondeur);
        } catch (IOException | RuntimeException ex) {
            remarque(quoi + " : modèle « " + ref + " » illisible (" + ex.getMessage() + ")");
            return null;
        }
    }

    /** Fusionne les parents qui ne sont pas des modèles du jeu (l'enfant gagne, comme dans le jeu). */
    private JsonObject aplatir(JsonObject m, String quoi, int profondeur) {
        if (!m.has("parent") || profondeur > 8) return m;
        String parent = m.get("parent").getAsString();
        Ressources.Ref r = Ressources.Ref.lire(parent);
        boolean duJeu = r.espace().equals("minecraft") && PARENTS_DU_JEU.stream().anyMatch(r.chemin()::startsWith)
            && ressources.trouver("models", parent, ".json") == null;
        if (duJeu) return m;
        JsonObject p = lireModele(parent, quoi, profondeur + 1);
        if (p == null) return m;
        JsonObject r2 = p.deepCopy();
        r2.remove("parent");
        if (p.has("parent")) r2.add("parent", p.get("parent"));
        else r2.remove("parent");
        for (String k : m.keySet()) {
            if (k.equals("parent")) continue;
            if ((k.equals("textures") || k.equals("display")) && r2.has(k) && r2.get(k).isJsonObject() && m.get(k).isJsonObject()) {
                JsonObject fusion = r2.getAsJsonObject(k).deepCopy();
                for (Map.Entry<String, JsonElement> e : m.getAsJsonObject(k).entrySet()) fusion.add(e.getKey(), e.getValue());
                r2.add(k, fusion);
            } else {
                r2.add(k, m.get(k));
            }
        }
        // les « #clé » des textures pointent maintenant vers des valeurs réelles
        return r2;
    }

    /** Ajoute une recette à un objet converti (une seule par objet dans Vorace : les suivantes sont signalées). */
    void recette(String id, Map<String, Object> recette, String quoi) {
        Map<String, Object> o = objets.get(id);
        if (o == null) { remarque(quoi + " : objet « " + id + " » introuvable"); return; }
        if (o.containsKey("recette")) { remarque(quoi + " : « " + id + " » a déjà une recette, celle-ci n'est pas gardée"); return; }
        o.put("recette", recette);
        recettes++;
    }

    /** Recopie un fichier tel quel (modèles animés .bbmodel...). */
    boolean copier(Path depuis, Path vers) {
        try {
            Files.createDirectories(vers.getParent());
            Files.copy(depuis, vers, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            remarque("copie impossible de " + depuis.getFileName() + " (" + e.getMessage() + ")");
            return false;
        }
    }

    // ------------------------------------------------------------------ écriture

    /** Écrit les fichiers .yml, la table des identifiants et le rapport. Renvoie le chemin du rapport. */
    Path terminer(String entete) throws IOException {
        DumperOptions o = new DumperOptions();
        o.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        o.setAllowUnicode(true);
        o.setIndent(2);
        o.setWidth(160);
        Yaml yaml = new Yaml(o);
        String avert = "# Converti automatiquement depuis " + source + " par /vorace convertir.\n"
            + "# Une nouvelle conversion remplace ce fichier : pour garder tes changements, renomme-le.\n";
        Path fo = vorace.resolve("objets/converti/" + source + ".yml");
        Files.createDirectories(fo.getParent());
        if (!objets.isEmpty()) Files.writeString(fo, avert + yaml.dump(objets), StandardCharsets.UTF_8);
        else Files.deleteIfExists(fo);
        Path fg = vorace.resolve("glyphes/converti/" + source + ".yml");
        Files.createDirectories(fg.getParent());
        if (!glyphes.isEmpty()) Files.writeString(fg, avert + yaml.dump(glyphes), StandardCharsets.UTF_8);
        else Files.deleteIfExists(fg);

        // table des anciens identifiants (objets déjà dans les inventaires)
        Path table = vorace.resolve("donnees/conversion-ids.yml");
        Map<String, Object> toutes = new LinkedHashMap<>();
        if (Files.isRegularFile(table)) {
            Object lu = new Yaml().load(Files.readString(table, StandardCharsets.UTF_8));
            if (lu instanceof Map<?, ?> mm) mm.forEach((k, v) -> toutes.put(String.valueOf(k), v));
        }
        toutes.putAll(correspondances);
        Files.createDirectories(table.getParent());
        Files.writeString(table, "# ancien objet -> objet Vorace (remplacement automatique dans les inventaires)\n" + yaml.dump(toutes), StandardCharsets.UTF_8);

        StringBuilder sb = new StringBuilder();
        sb.append(entete).append("\n\n");
        sb.append(objets.size()).append(" objet(s) dont ").append(meubles).append(" meuble(s) et ").append(blocs).append(" bloc(s), ")
            .append(glyphes.size()).append(" glyphe(s), ").append(recettes).append(" recette(s), ").append(animes).append(" modèle(s) animé(s), ")
            .append(textures).append(" texture(s) et ").append(modeles).append(" modèle(s) recopiés.\n\n");
        if (!objets.isEmpty()) sb.append("Objets : plugins/Vorace/objets/converti/").append(source).append(".yml\n");
        if (!glyphes.isEmpty()) sb.append("Glyphes : plugins/Vorace/glyphes/converti/").append(source).append(".yml\n");
        sb.append("\nÀ vérifier (").append(remarques.size()).append(") :\n");
        for (String r : remarques) sb.append("- ").append(r).append('\n');
        Path rapport = vorace.resolve("conversion/rapport-" + source + ".txt");
        Files.createDirectories(rapport.getParent());
        Files.writeString(rapport, sb.toString(), StandardCharsets.UTF_8);
        return rapport;
    }

    String resume() {
        return objets.size() + " objet(s) (" + meubles + " meuble(s), " + blocs + " bloc(s)), " + glyphes.size() + " glyphe(s), "
            + animes + " modèle(s) animé(s), " + textures + " texture(s), " + remarques.size() + " remarque(s)";
    }
}
