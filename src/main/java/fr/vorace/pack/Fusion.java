package fr.vorace.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Fusionne plusieurs packs de ressources en un seul.
 * <p>
 * Les sources sont ajoutées de la moins prioritaire à la plus prioritaire. Quand deux packs
 * contiennent le même fichier :
 * <ul>
 *   <li>sounds.json, lang/*.json : les clés sont réunies (la source prioritaire gagne sur une même clé) ;</li>
 *   <li>font/*.json : les "providers" sont réunis (ceux de la source prioritaire passent devant) ;</li>
 *   <li>atlases/*.json : les "sources" sont réunies ;</li>
 *   <li>models/**.json avec "overrides" (format avant 1.21.4) : les overrides sont réunis et triés par custom_model_data ;</li>
 *   <li>items/*.json (format 1.21.4+) : les "range_dispatch" sur custom_model_data et les "select" sont réunis ;</li>
 *   <li>pack.mcmeta : les overlays, langues et filtres sont réunis (le pack.mcmeta final est écrit par {@link Constructeur}) ;</li>
 *   <li>tout le reste : la source prioritaire remplace l'autre, et le conflit est noté dans le journal.</li>
 * </ul>
 */
public final class Fusion {

    private final Map<String, byte[]> fichiers = new TreeMap<>();
    private final Map<String, String> origine = new HashMap<>();
    private final List<JsonObject> mcmetas = new ArrayList<>();
    private final Map<String, byte[]> icones = new LinkedHashMap<>();
    private final Journal journal;

    public Fusion(Journal journal) {
        this.journal = journal;
    }

    public Journal journal() { return journal; }
    public Map<String, byte[]> fichiers() { return fichiers; }
    public List<JsonObject> mcmetas() { return mcmetas; }
    /** pack.png de chaque source, dans l'ordre d'ajout. */
    public Map<String, byte[]> icones() { return icones; }
    public String origine(String chemin) { return origine.get(chemin); }

    public void ajouter(Source source) {
        int avant = fichiers.size();
        for (Map.Entry<String, byte[]> e : source.fichiers().entrySet()) {
            ajouterFichier(source.nom(), e.getKey(), e.getValue());
        }
        journal.info("Source « " + source.nom() + " » : " + source.fichiers().size() + " fichiers ("
            + (fichiers.size() - avant) + " nouveaux)");
    }

    public void ajouterFichier(String nomSource, String chemin, byte[] contenu) {
        if (chemin.equals("pack.mcmeta")) {
            JsonElement j = lireJson(contenu);
            if (j != null && j.isJsonObject()) mcmetas.add(j.getAsJsonObject());
            else journal.erreur(nomSource + " : pack.mcmeta illisible, ignoré");
            return;
        }
        if (chemin.equals("pack.png")) {
            icones.put(nomSource, contenu);
            return;
        }
        byte[] ancien = fichiers.get(chemin);
        if (ancien == null) {
            fichiers.put(chemin, contenu);
            origine.put(chemin, nomSource);
            return;
        }
        if (Arrays.equals(ancien, contenu)) return;

        String ancienneSource = origine.get(chemin);
        if (chemin.endsWith(".json")) {
            JsonElement a = lireJson(ancien);
            JsonElement b = lireJson(contenu);
            if (a != null && b != null) {
                JsonElement fusionne = fusionnerJson(chemin, a, b);
                if (fusionne != null) {
                    fichiers.put(chemin, fusionne.toString().getBytes(StandardCharsets.UTF_8));
                    origine.put(chemin, ancienneSource + " + " + nomSource);
                    journal.fusion(chemin + " : fusionné (" + ancienneSource + " + " + nomSource + ")");
                    return;
                }
            } else if (b == null) {
                journal.erreur(nomSource + " : " + chemin + " n'est pas un JSON valide");
            }
        }
        fichiers.put(chemin, contenu);
        origine.put(chemin, nomSource);
        journal.conflit(chemin + " : « " + nomSource + " » remplace « " + ancienneSource + " »");
    }

    // ------------------------------------------------------------------ JSON

    public static JsonElement lireJson(byte[] octets) {
        try {
            String s = new String(octets, StandardCharsets.UTF_8);
            if (!s.isEmpty() && s.charAt(0) == '﻿') s = s.substring(1);
            JsonReader r = new JsonReader(new StringReader(s));
            return JsonParser.parseReader(r); // lecture tolérante, comme le jeu
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** Retourne la fusion de a (moins prioritaire) et b (prioritaire), ou null si on ne sait pas fusionner. */
    static JsonElement fusionnerJson(String chemin, JsonElement a, JsonElement b) {
        if (!a.isJsonObject() || !b.isJsonObject()) return null;
        JsonObject oa = a.getAsJsonObject();
        JsonObject ob = b.getAsJsonObject();
        String[] parties = chemin.split("/");
        // assets/<namespace>/<type>/...
        String type = parties.length >= 3 && parties[0].equals("assets") ? parties[2] : "";

        if (parties.length == 3 && type.equals("sounds.json")) return fusionnerCles(oa, ob);
        if (type.equals("lang")) return fusionnerCles(oa, ob);
        if (type.equals("font")) return fusionnerListe(oa, ob, "providers", true);
        if (type.equals("atlases")) return fusionnerListe(oa, ob, "sources", false);
        if (type.equals("items")) return fusionnerDefinitionItem(oa, ob);
        if (type.equals("models") && (oa.has("overrides") || ob.has("overrides"))) return fusionnerOverrides(oa, ob);
        return null;
    }

    private static JsonObject fusionnerCles(JsonObject a, JsonObject b) {
        JsonObject r = a.deepCopy();
        for (Map.Entry<String, JsonElement> e : b.entrySet()) r.add(e.getKey(), e.getValue().deepCopy());
        return r;
    }

    private static JsonObject fusionnerListe(JsonObject a, JsonObject b, String cle, boolean prioritaireDevant) {
        JsonArray la = a.has(cle) && a.get(cle).isJsonArray() ? a.getAsJsonArray(cle) : new JsonArray();
        JsonArray lb = b.has(cle) && b.get(cle).isJsonArray() ? b.getAsJsonArray(cle) : new JsonArray();
        Set<String> vus = new LinkedHashSet<>();
        JsonArray r = new JsonArray();
        for (JsonArray l : prioritaireDevant ? List.of(lb, la) : List.of(la, lb)) {
            for (JsonElement e : l) if (vus.add(e.toString())) r.add(e.deepCopy());
        }
        JsonObject o = fusionnerCles(a, b);
        o.add(cle, r);
        return o;
    }

    /** Modèles d'objets avant 1.21.4 : réunion des "overrides", triés par custom_model_data. */
    private static JsonObject fusionnerOverrides(JsonObject a, JsonObject b) {
        Map<String, JsonElement> parPredicat = new LinkedHashMap<>();
        for (JsonObject o : List.of(a, b)) {
            if (!o.has("overrides") || !o.get("overrides").isJsonArray()) continue;
            for (JsonElement e : o.getAsJsonArray("overrides")) {
                String cle = e.isJsonObject() && e.getAsJsonObject().has("predicate")
                    ? canonique(e.getAsJsonObject().get("predicate")) : e.toString();
                parPredicat.remove(cle); // la source prioritaire reprend la place
                parPredicat.put(cle, e.deepCopy());
            }
        }
        List<JsonElement> liste = new ArrayList<>(parPredicat.values());
        liste.sort(Comparator.comparingDouble(Fusion::cmd)); // tri stable
        JsonArray r = new JsonArray();
        liste.forEach(r::add);
        JsonObject o = b.deepCopy();
        for (Map.Entry<String, JsonElement> e : a.entrySet()) if (!o.has(e.getKey())) o.add(e.getKey(), e.getValue().deepCopy());
        o.add("overrides", r);
        return o;
    }

    private static double cmd(JsonElement override) {
        try {
            JsonObject p = override.getAsJsonObject().getAsJsonObject("predicate");
            return p != null && p.has("custom_model_data") ? p.get("custom_model_data").getAsDouble() : 0;
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    /** Définitions d'objets 1.21.4+ (assets/x/items/*.json). */
    private static JsonObject fusionnerDefinitionItem(JsonObject a, JsonObject b) {
        if (!a.has("model") || !b.has("model") || !a.get("model").isJsonObject() || !b.get("model").isJsonObject()) return null;
        JsonObject m = fusionnerModele(a.getAsJsonObject("model"), b.getAsJsonObject("model"));
        if (m == null) return null;
        JsonObject r = fusionnerCles(a, b);
        r.add("model", m);
        return r;
    }

    static JsonObject fusionnerModele(JsonObject a, JsonObject b) {
        String ta = typeModele(a);
        String tb = typeModele(b);
        if (ta.equals("range_dispatch") && tb.equals("range_dispatch") && memesCles(a, b, "property", "index", "scale")) {
            Map<String, JsonElement> parSeuil = new LinkedHashMap<>();
            for (JsonObject o : List.of(a, b)) {
                if (!o.has("entries")) continue;
                for (JsonElement e : o.getAsJsonArray("entries")) {
                    String cle = e.isJsonObject() && e.getAsJsonObject().has("threshold")
                        ? String.valueOf(e.getAsJsonObject().get("threshold").getAsDouble()) : e.toString();
                    parSeuil.put(cle, e.deepCopy());
                }
            }
            List<JsonElement> liste = new ArrayList<>(parSeuil.values());
            liste.sort(Comparator.comparingDouble(e -> e.isJsonObject() && e.getAsJsonObject().has("threshold")
                ? e.getAsJsonObject().get("threshold").getAsDouble() : 0));
            JsonArray entries = new JsonArray();
            liste.forEach(entries::add);
            JsonObject r = b.deepCopy();
            r.add("entries", entries);
            r.add("fallback", choisirRepli(a, b));
            if (r.get("fallback") == null || r.get("fallback").isJsonNull()) r.remove("fallback");
            return r;
        }
        if (ta.equals("select") && tb.equals("select")
            && memesCles(a, b, "property", "component", "block_state_property", "pattern", "locale", "time_zone")) {
            Map<String, JsonElement> parCas = new LinkedHashMap<>();
            for (JsonObject o : List.of(a, b)) {
                if (!o.has("cases")) continue;
                for (JsonElement e : o.getAsJsonArray("cases")) {
                    String cle = e.isJsonObject() && e.getAsJsonObject().has("when")
                        ? canonique(e.getAsJsonObject().get("when")) : e.toString();
                    parCas.remove(cle);
                    parCas.put(cle, e.deepCopy());
                }
            }
            JsonArray cases = new JsonArray();
            parCas.values().forEach(cases::add);
            JsonObject r = b.deepCopy();
            r.add("cases", cases);
            r.add("fallback", choisirRepli(a, b));
            if (r.get("fallback") == null || r.get("fallback").isJsonNull()) r.remove("fallback");
            return r;
        }
        boolean aSimple = ta.equals("model");
        boolean bSimple = tb.equals("model");
        if (aSimple && !bSimple && estAiguillage(tb)) {
            // b ajoute des variantes à l'objet de base : le modèle simple devient le repli
            JsonObject r = b.deepCopy();
            if (!r.has("fallback")) r.add("fallback", a.deepCopy());
            return r;
        }
        if (bSimple && !aSimple && estAiguillage(ta)) {
            // b change l'apparence de base de l'objet : on garde les variantes de a
            JsonObject r = a.deepCopy();
            r.add("fallback", b.deepCopy());
            return r;
        }
        return null;
    }

    private static JsonElement choisirRepli(JsonObject a, JsonObject b) {
        JsonElement fa = a.get("fallback");
        JsonElement fb = b.get("fallback");
        if (fa != null && fb != null && fa.isJsonObject() && fb.isJsonObject()) {
            JsonObject f = fusionnerModele(fa.getAsJsonObject(), fb.getAsJsonObject());
            return f != null ? f : fb.deepCopy();
        }
        if (fb != null) return fb.deepCopy();
        return fa != null ? fa.deepCopy() : null;
    }

    private static boolean estAiguillage(String t) {
        return t.equals("range_dispatch") || t.equals("select") || t.equals("condition");
    }

    private static String typeModele(JsonObject o) {
        if (!o.has("type")) return "";
        String t = o.get("type").getAsString();
        return t.startsWith("minecraft:") ? t.substring(10) : t;
    }

    private static boolean memesCles(JsonObject a, JsonObject b, String... cles) {
        for (String c : cles) {
            JsonElement x = a.get(c);
            JsonElement y = b.get(c);
            String sx = x == null ? "" : sansNamespace(x);
            String sy = y == null ? "" : sansNamespace(y);
            if (!sx.equals(sy)) return false;
        }
        return true;
    }

    private static String sansNamespace(JsonElement e) {
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
            String s = e.getAsString();
            return s.startsWith("minecraft:") ? s.substring(10) : s;
        }
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) return String.valueOf(e.getAsDouble());
        return e.toString();
    }

    /** Représentation stable d'un JSON (clés triées) pour comparer des prédicats ou des "when". */
    static String canonique(JsonElement e) {
        if (e == null || e.isJsonNull()) return "null";
        if (e.isJsonPrimitive()) {
            JsonPrimitive p = e.getAsJsonPrimitive();
            return p.isNumber() ? String.valueOf(p.getAsDouble()) : p.toString();
        }
        if (e.isJsonArray()) {
            StringBuilder sb = new StringBuilder("[");
            for (JsonElement x : e.getAsJsonArray()) sb.append(canonique(x)).append(',');
            return sb.append(']').toString();
        }
        TreeMap<String, JsonElement> tri = new TreeMap<>();
        for (Map.Entry<String, JsonElement> x : e.getAsJsonObject().entrySet()) tri.put(x.getKey(), x.getValue());
        StringBuilder sb = new StringBuilder("{");
        tri.forEach((k, v) -> sb.append(k).append(':').append(canonique(v)).append(','));
        return sb.append('}').toString();
    }
}
