package fr.vorace.pack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.InputStream;
import java.net.URL;

/**
 * Numéro de format des packs de ressources ("pack_format") de la version du serveur.
 * <p>
 * Vorace lit d'abord le fichier version.json du jeu (toujours juste, même pour une version
 * sortie après le plugin), puis se replie sur une table connue.
 */
public record FormatPack(int majeur, int mineur, String origine) {

    /** Depuis 1.21.9 (format 65+), pack.mcmeta utilise min_format / max_format. */
    public static final int PREMIER_FORMAT_MIN_MAX = 65;

    private static final Object[][] TABLE = {
        // version (préfixe), majeur, mineur — du plus récent au plus ancien
        {"1.21.11", 75, 0},
        {"1.21.10", 69, 0}, {"1.21.9", 69, 0},
        {"1.21.8", 64, 0}, {"1.21.7", 64, 0}, {"1.21.6", 63, 0}, {"1.21.5", 55, 0},
        {"1.21.4", 46, 0}, {"1.21.3", 42, 0}, {"1.21.2", 42, 0},
        {"1.21.1", 34, 0}, {"1.21", 34, 0},
        {"1.20.6", 32, 0}, {"1.20.5", 32, 0},
    };

    public boolean nouveauStyle() { return majeur >= PREMIER_FORMAT_MIN_MAX; }

    public String texte() { return nouveauStyle() ? majeur + "." + mineur : String.valueOf(majeur); }

    /**
     * @param chargeur chargeur de classes du serveur (celui qui contient version.json)
     * @param versionMinecraft par exemple "1.21.4" ou "26.2"
     */
    public static FormatPack detecter(ClassLoader chargeur, String versionMinecraft) {
        FormatPack lu = lireVersionJson(chargeur);
        if (lu != null) return lu;
        for (Object[] ligne : TABLE) {
            String v = (String) ligne[0];
            if (versionMinecraft.equals(v) || versionMinecraft.startsWith(v + ".") || versionMinecraft.startsWith(v + "-")) {
                return new FormatPack((Integer) ligne[1], (Integer) ligne[2], "table (" + v + ")");
            }
        }
        // Version plus récente que la table : on prend le plus grand format connu. Comme Vorace
        // déclare une plage ouverte vers le haut (max_format 999), le jeu accepte quand même le pack.
        return new FormatPack((Integer) TABLE[0][1], 0, "plus récent connu (version " + versionMinecraft + " inconnue)");
    }

    static FormatPack lireVersionJson(ClassLoader chargeur) {
        if (chargeur == null) return null;
        try {
            URL url = chargeur.getResource("version.json");
            if (url == null) return null;
            byte[] octets;
            try (InputStream in = url.openStream()) { octets = in.readAllBytes(); }
            JsonElement racine = Fusion.lireJson(octets);
            if (racine == null || !racine.isJsonObject()) return null;
            JsonElement pv = racine.getAsJsonObject().get("pack_version");
            if (pv == null) return null;
            if (pv.isJsonPrimitive()) return new FormatPack(pv.getAsInt(), 0, "version.json");
            JsonObject o = pv.getAsJsonObject();
            if (o.has("resource_major")) {
                return new FormatPack(o.get("resource_major").getAsInt(),
                    o.has("resource_minor") ? o.get("resource_minor").getAsInt() : 0, "version.json");
            }
            if (o.has("resource")) {
                JsonElement r = o.get("resource");
                if (r.isJsonPrimitive()) return new FormatPack(r.getAsInt(), 0, "version.json");
                if (r.isJsonArray() && r.getAsJsonArray().size() >= 1) {
                    int mi = r.getAsJsonArray().size() > 1 ? r.getAsJsonArray().get(1).getAsInt() : 0;
                    return new FormatPack(r.getAsJsonArray().get(0).getAsInt(), mi, "version.json");
                }
            }
        } catch (Exception ignore) {
            // on se replie sur la table
        }
        return null;
    }
}
