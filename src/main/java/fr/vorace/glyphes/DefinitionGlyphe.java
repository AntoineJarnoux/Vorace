package fr.vorace.glyphes;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Un glyphe de plugins/Vorace/glyphes/*.yml.
 *
 * @param texture    image dans ressources/textures (sans .png)
 * @param hauteur    hauteur affichée en pixels, ou -1 pour la hauteur de l'image (fonds de menus)
 * @param montee     pixels au-dessus de la ligne d'écriture
 * @param raccourcis textes remplacés dans le chat et sur les panneaux (« :coeur: », « <3 »)
 * @param permission permission pour utiliser les raccourcis, ou null (tout le monde)
 * @param menu       vrai pour un fond de menu (placé tout seul en haut à gauche de la fenêtre)
 * @param liste      montré dans /emojis
 * @param lignes     fond de menu : nombre de lignes du coffre (pour l'aperçu /vorace glyphe menu)
 */
public record DefinitionGlyphe(String id, String fichier, String texture, int hauteur, int montee,
                               List<String> raccourcis, String permission, boolean menu, boolean liste, int lignes) {

    public static DefinitionGlyphe lire(String id, String fichier, ConfigurationSection s, List<String> problemes) {
        String texture = s.getString("texture", s.getString("image"));
        if (texture == null || texture.isBlank()) {
            problemes.add(fichier + " : glyphe « " + id + " » sans « texture »");
            texture = id;
        }
        texture = texture.trim().replace('\\', '/');
        if (texture.endsWith(".png")) texture = texture.substring(0, texture.length() - 4);
        while (texture.startsWith("/")) texture = texture.substring(1);

        boolean menu = s.getBoolean("fond-menu", s.getBoolean("menu", false));
        int hauteur = s.getInt("hauteur", menu ? -1 : 9);
        int montee = s.getInt("montee", s.getInt("montée", menu ? 13 : 8));

        List<String> raccourcis = new ArrayList<>();
        if (!menu) raccourcis.add(":" + id + ":");
        Object r = s.get("raccourcis", s.get("emoji"));
        if (r instanceof List<?> l) {
            for (Object o : l) if (o != null && !o.toString().isBlank() && !raccourcis.contains(o.toString())) raccourcis.add(o.toString());
        } else if (r != null && !(r instanceof Boolean) && !r.toString().isBlank() && !raccourcis.contains(r.toString())) {
            raccourcis.add(r.toString());
        }
        if (s.isBoolean("raccourcis") && !s.getBoolean("raccourcis")) raccourcis.clear();

        String permission = s.getString("permission");
        if (permission != null && permission.isBlank()) permission = null;
        return new DefinitionGlyphe(id.toLowerCase(Locale.ROOT), fichier, texture, hauteur, montee, List.copyOf(raccourcis),
            permission, menu, s.getBoolean("liste", !menu && !raccourcis.isEmpty()), Math.max(1, Math.min(6, s.getInt("lignes", 3))));
    }
}
