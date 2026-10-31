package fr.vorace.api;

import fr.vorace.Vorace;
import fr.vorace.objets.DefinitionObjet;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Accès simple aux objets de Vorace depuis un autre plugin.
 * <pre>
 * ItemStack epee = VoraceAPI.objet("epee_rubis", 1);
 * String id = VoraceAPI.id(joueur.getInventory().getItemInMainHand());
 * ItemDisplay chaise = VoraceAPI.poserMeuble("chaise", bloc, 90f);
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

    /** Identifiant du meuble Vorace lié à cette entité (le meuble ou sa hitbox), ou null. */
    public static String meuble(Entity e) {
        if (plugin == null || plugin.meubles() == null) return null;
        ItemDisplay d = plugin.meubles().depuisEntite(e);
        return d == null ? null : plugin.meubles().id(d);
    }

    /** Pose un meuble sans vérification (place, protections). Renvoie son entité, ou null si ce n'est pas un meuble. */
    public static ItemDisplay poserMeuble(String id, Block origine, float yaw) {
        if (plugin == null || plugin.meubles() == null) return null;
        DefinitionObjet d = plugin.objets().definition(id);
        return d == null || d.meuble() == null ? null : plugin.meubles().creer(d, origine, yaw);
    }

    /** Casse un meuble (sans le rendre). */
    public static boolean retirerMeuble(Entity e) {
        if (plugin == null || plugin.meubles() == null) return false;
        ItemDisplay d = plugin.meubles().depuisEntite(e);
        return d != null && plugin.meubles().casser(null, d, false);
    }
}
