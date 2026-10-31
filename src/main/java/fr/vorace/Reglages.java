package fr.vorace;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/** Lecture de config.yml (toutes les valeurs ont un défaut, une config incomplète marche quand même). */
public record Reglages(
    // pack
    String description, List<String> sources, List<String> ignorer, boolean minifier, String formatForce, String icone,
    // envoi
    boolean envoiActif, int delaiTicks, boolean obligatoire, boolean expulserSiRefus, boolean renvoyerAuto,
    // hébergement
    String mode, String adressePublique, int portPublic, int portDedie, String urlExterne,
    // surveillance
    boolean surveillance, int intervalleSecondes,
    boolean debug
) {

    public static Reglages lire(FileConfiguration c) {
        return new Reglages(
            c.getString("pack.description", "Pack du serveur"),
            liste(c, "pack.sources", List.of("plugins/Vorace/pack/sources/*")),
            liste(c, "pack.ignorer", List.of()),
            c.getBoolean("pack.compacter-json", true),
            c.getString("pack.format", "auto"),
            c.getString("pack.icone", ""),
            c.getBoolean("envoi.actif", true),
            Math.max(0, c.getInt("envoi.delai-ticks", 20)),
            c.getBoolean("envoi.obligatoire", true),
            c.getBoolean("envoi.expulser-si-refus", false),
            c.getBoolean("envoi.renvoyer-apres-modification", false),
            c.getString("hebergement.mode", "auto"),
            c.getString("hebergement.adresse-publique", ""),
            c.getInt("hebergement.port-public", 0),
            c.getInt("hebergement.port-dedie", 8163),
            c.getString("hebergement.url-externe", ""),
            c.getBoolean("surveillance.actif", true),
            Math.max(2, c.getInt("surveillance.intervalle-secondes", 5)),
            c.getBoolean("debug", false)
        );
    }

    private static List<String> liste(FileConfiguration c, String cle, List<String> defaut) {
        List<String> l = c.getStringList(cle);
        return l == null || l.isEmpty() ? defaut : l;
    }
}
