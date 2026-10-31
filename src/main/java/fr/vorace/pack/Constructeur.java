package fr.vorace.pack;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Fusionne les sources, écrit pack.mcmeta et produit le .zip final avec son empreinte SHA-1. */
public final class Constructeur {

    /** Date fixe dans le zip : même contenu = même fichier = même SHA-1 (pas de re-téléchargement inutile). */
    private static final long DATE_FIXE = 1735689600000L; // 2025-01-01

    public record Options(String description, FormatPack format, boolean minifier, byte[] icone) {}

    public record Resultat(byte[] zip, String sha1, int nbFichiers, Journal journal, long dureeMs) {
        public byte[] sha1Octets() { return HexFormat.of().parseHex(sha1); }
    }

    private Constructeur() {}

    public static Resultat construire(List<Source> sources, Options options) throws IOException {
        long debut = System.currentTimeMillis();
        Journal journal = new Journal();
        Fusion fusion = new Fusion(journal);
        for (Source s : sources) fusion.ajouter(s);

        Map<String, byte[]> sortie = new LinkedHashMap<>();
        sortie.put("pack.mcmeta", ecrireMcmeta(fusion, options).getBytes(StandardCharsets.UTF_8));
        byte[] icone = options.icone();
        if (icone == null && !fusion.icones().isEmpty()) {
            icone = fusion.icones().values().stream().reduce((x, y) -> y).orElse(null); // la plus prioritaire
        }
        if (icone != null) sortie.put("pack.png", icone);

        int minifies = 0;
        for (Map.Entry<String, byte[]> e : fusion.fichiers().entrySet()) {
            byte[] contenu = e.getValue();
            if (options.minifier() && (e.getKey().endsWith(".json") || e.getKey().endsWith(".mcmeta"))) {
                JsonElement j = Fusion.lireJson(contenu);
                if (j != null) {
                    byte[] court = j.toString().getBytes(StandardCharsets.UTF_8);
                    if (court.length < contenu.length) { contenu = court; minifies++; }
                }
            }
            sortie.put(e.getKey(), contenu);
        }
        if (minifies > 0) journal.info(minifies + " fichiers JSON compactés");

        byte[] zip = zipper(sortie);
        String sha1 = sha1(zip);
        journal.info("Pack final : " + sortie.size() + " fichiers, " + (zip.length / 1024) + " Ko, SHA-1 " + sha1
            + ", format " + options.format().texte() + " (" + options.format().origine() + ")");
        return new Resultat(zip, sha1, sortie.size(), journal, System.currentTimeMillis() - debut);
    }

    static String ecrireMcmeta(Fusion fusion, Options options) {
        FormatPack f = options.format();
        JsonObject pack = new JsonObject();
        pack.add("description", new JsonPrimitive(options.description()));
        if (f.nouveauStyle()) {
            if (f.mineur() == 0) {
                pack.addProperty("min_format", f.majeur());
            } else {
                JsonArray min = new JsonArray();
                min.add(f.majeur());
                min.add(f.mineur());
                pack.add("min_format", min);
            }
            pack.addProperty("max_format", 999);
        } else {
            pack.addProperty("pack_format", f.majeur());
            JsonObject plage = new JsonObject();
            plage.addProperty("min_inclusive", f.majeur());
            plage.addProperty("max_inclusive", 999);
            pack.add("supported_formats", plage);
        }
        JsonObject racine = new JsonObject();
        racine.add("pack", pack);

        // overlays, langues et filtres des sources
        Map<String, JsonElement> overlays = new LinkedHashMap<>();
        JsonObject langues = new JsonObject();
        Map<String, JsonElement> filtres = new LinkedHashMap<>();
        for (JsonObject m : fusion.mcmetas()) {
            JsonElement ov = m.get("overlays");
            if (ov != null && ov.isJsonObject() && ov.getAsJsonObject().has("entries")) {
                for (JsonElement e : ov.getAsJsonObject().getAsJsonArray("entries")) {
                    String dir = e.isJsonObject() && e.getAsJsonObject().has("directory")
                        ? e.getAsJsonObject().get("directory").getAsString() : e.toString();
                    overlays.put(dir, e.deepCopy());
                }
            }
            JsonElement lg = m.get("language");
            if (lg != null && lg.isJsonObject()) lg.getAsJsonObject().entrySet().forEach(x -> langues.add(x.getKey(), x.getValue().deepCopy()));
            JsonElement fi = m.get("filter");
            if (fi != null && fi.isJsonObject() && fi.getAsJsonObject().has("block")) {
                for (JsonElement e : fi.getAsJsonObject().getAsJsonArray("block")) filtres.put(e.toString(), e.deepCopy());
            }
        }
        if (!overlays.isEmpty()) {
            JsonArray entries = new JsonArray();
            overlays.values().forEach(entries::add);
            JsonObject o = new JsonObject();
            o.add("entries", entries);
            racine.add("overlays", o);
            fusion.journal().info(overlays.size() + " overlay(s) conservé(s) : " + String.join(", ", overlays.keySet()));
        }
        if (!langues.isEmpty()) racine.add("language", langues);
        if (!filtres.isEmpty()) {
            JsonArray block = new JsonArray();
            filtres.values().forEach(block::add);
            JsonObject o = new JsonObject();
            o.add("block", block);
            racine.add("filter", o);
        }
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(racine);
    }

    static byte[] zipper(Map<String, byte[]> fichiers) throws IOException {
        ByteArrayOutputStream sortie = new ByteArrayOutputStream(1 << 20);
        try (ZipOutputStream zip = new ZipOutputStream(sortie, StandardCharsets.UTF_8)) {
            zip.setLevel(Deflater.BEST_COMPRESSION);
            for (Map.Entry<String, byte[]> e : fichiers.entrySet()) {
                ZipEntry entree = new ZipEntry(e.getKey());
                entree.setTime(DATE_FIXE);
                zip.putNextEntry(entree);
                zip.write(e.getValue());
                zip.closeEntry();
            }
        }
        return sortie.toByteArray();
    }

    public static String sha1(byte[] octets) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(octets));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
