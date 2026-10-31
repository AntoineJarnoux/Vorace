package fr.vorace.interfaces;

import fr.vorace.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Un menu de Vorace : un coffre dont chaque case est un bouton. Chaque bouton dit dans sa description ce que fait
 * chaque clic. La dernière ligne sert à la navigation (retour, pages, fermer).
 */
public class Menu implements InventoryHolder {

    /** Ce que fait un bouton. droit = clic droit, maj = clic avec Maj. */
    @FunctionalInterface
    public interface Action {
        void clic(Player p, boolean droit, boolean maj);
    }

    private final Inventory inventaire;
    private final Map<Integer, Action> actions = new HashMap<>();
    final int lignes;

    public Menu(String titre, int lignes) {
        this.lignes = Math.max(1, Math.min(6, lignes));
        this.inventaire = Bukkit.createInventory(this, this.lignes * 9, Messages.objet(titre));
    }

    @Override
    public Inventory getInventory() {
        return inventaire;
    }

    public int taille() { return lignes * 9; }

    /** Première case de la ligne du bas (navigation). */
    public int bas() { return (lignes - 1) * 9; }

    public Menu bouton(int slot, ItemStack icone, Action action) {
        if (slot < 0 || slot >= taille()) return this;
        inventaire.setItem(slot, icone);
        if (action != null) actions.put(slot, action);
        else actions.remove(slot);
        return this;
    }

    /** Ligne du bas en vitres grises, avec « Retour » (si parent) et « Fermer ». */
    public Menu navigation(Supplier<Menu> parent) {
        ItemStack vitre = icone("GRAY_STAINED_GLASS_PANE", " ");
        for (int i = bas(); i < taille(); i++) if (inventaire.getItem(i) == null) inventaire.setItem(i, vitre);
        if (parent != null) bouton(bas(), icone("ARROW", "<yellow>◀ Retour"), (p, d, m) -> parent.get().ouvrir(p));
        bouton(taille() - 1, icone("BARRIER", "<red>Fermer"), (p, d, m) -> p.closeInventory());
        return this;
    }

    public void ouvrir(Player p) {
        p.openInventory(inventaire);
    }

    void clic(Player p, int slot, boolean droit, boolean maj) {
        Action a = actions.get(slot);
        if (a == null) return;
        try {
            a.clic(p, droit, maj);
        } catch (RuntimeException e) {
            p.sendMessage(Messages.brut("<red>Erreur : " + Messages.echapper(String.valueOf(e.getMessage()))));
            Bukkit.getLogger().warning("[Vorace] menu : " + e);
        }
    }

    // ------------------------------------------------------------------ outils

    /** Matériau par son nom (ceux qui n'existent pas dans cette version deviennent du papier). */
    public static Material materiau(String nom) {
        Material m = Material.matchMaterial(nom);
        return m == null || m.isAir() || !m.isItem() ? Material.PAPER : m;
    }

    /** Icône : nom (MiniMessage) et lignes de description (« » = ligne vide). */
    public static ItemStack icone(String materiau, String nom, String... lignes) {
        return icone(new ItemStack(materiau(materiau), 1), nom, lignes);
    }

    /** Renomme un objet existant (copie) pour en faire une icône. */
    public static ItemStack icone(ItemStack base, String nom, String... lignes) {
        ItemStack it = base.clone();
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;
        if (nom != null) meta.displayName(Messages.objet(nom));
        List<Component> lore = new ArrayList<>();
        for (String l : lignes) lore.add(Messages.objet(l.isEmpty() ? " " : "<gray>" + l));
        if (!lore.isEmpty()) meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    /** Ferme le menu et lance une commande de Vorace comme si le joueur l'avait tapée. */
    public static void commande(Player p, String commande) {
        p.closeInventory();
        p.performCommand("vorace " + commande);
    }

    /** Ligne de description d'un clic : « ▶ Clic : poser ». */
    public static String clic(String quoi, String effet) {
        return "<yellow>▶ " + quoi + " : <white>" + effet;
    }

    static String id(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
