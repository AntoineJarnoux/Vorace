package fr.vorace.api;

import fr.vorace.Vorace;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Accès simple aux objets de Vorace depuis un autre plugin.
 * <pre>
 * ItemStack epee = VoraceAPI.objet("epee_rubis", 1);
 * String id = VoraceAPI.id(joueur.getInventory().getItemInMainHand());
 * </pre>
 */
public final class VoraceAPI {

    private static Vorace plugin;

    private VoraceAPI() {}

    public static void initialiser(Vorace p) { plugin = p; }

    /** Fabrique un objet Vorace, ou null s'il n'existe pas. */
    public static ItemStack objet(String id, int quantite) {
        return plugin == null ? null : plugin.objets().fabriquer(id, quantite);
    }

    /** Identifiant Vorace de cet objet, ou null si ce n'est pas un objet Vorace. */
    public static String id(ItemStack objet) {
        return plugin == null ? null : plugin.objets().id(objet);
    }

    /** Tous les identifiants d'objets. */
    public static List<String> ids() {
        return plugin == null ? List.of() : plugin.objets().toutes().stream().map(d -> d.id()).toList();
    }
}
