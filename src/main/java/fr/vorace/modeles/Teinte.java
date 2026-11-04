package fr.vorace.modeles;

import org.bukkit.Color;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Teinte d'un objet par la couleur du composant custom_model_data (1.21.4 et plus). Le pack donne à chaque partie
 * de modèle une teinte « custom_model_data » blanche par défaut : changer la couleur colore la partie.
 * Par réflexion, car l'API de compilation (1.21.1) ne connaît pas encore ces méthodes.
 */
final class Teinte {

    private static Method lire, ecrire, couleurs;
    private static boolean indisponible;

    private Teinte() {}

    /** Copie teintée de l'objet, ou null si le serveur ne le permet pas (avant 1.21.4). */
    static ItemStack teinter(ItemStack objet, int rgb) {
        if (indisponible || objet == null) return null;
        try {
            if (lire == null) {
                Class<?> composant = Class.forName("org.bukkit.inventory.meta.components.CustomModelDataComponent");
                lire = ItemMeta.class.getMethod("getCustomModelDataComponent");
                ecrire = ItemMeta.class.getMethod("setCustomModelDataComponent", composant);
                couleurs = composant.getMethod("setColors", List.class);
            }
            ItemStack copie = objet.clone();
            ItemMeta meta = copie.getItemMeta();
            if (meta == null) return null;
            Object c = lire.invoke(meta);
            couleurs.invoke(c, List.of(Color.fromRGB(rgb & 0xFFFFFF)));
            ecrire.invoke(meta, c);
            copie.setItemMeta(meta);
            return copie;
        } catch (Throwable t) {
            indisponible = true;
            return null;
        }
    }
}
