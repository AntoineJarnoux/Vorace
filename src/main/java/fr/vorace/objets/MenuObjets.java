package fr.vorace.objets;

import fr.vorace.Messages;
import fr.vorace.Vorace;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Menu de tous les objets Vorace : clic = 1, clic avec Maj = une pile. */
public final class MenuObjets implements InventoryHolder {

    private static final int PAR_PAGE = 45;
    private final Vorace plugin;
    private final List<DefinitionObjet> liste;
    private final int page;
    private final Inventory inventaire;

    public MenuObjets(Vorace plugin, int page) {
        this.plugin = plugin;
        this.liste = new ArrayList<>(plugin.objets().toutes());
        int pages = Math.max(1, (liste.size() + PAR_PAGE - 1) / PAR_PAGE);
        this.page = Math.max(0, Math.min(page, pages - 1));
        this.inventaire = Bukkit.createInventory(this, 54,
            Messages.objet("<dark_gray>Objets Vorace <gray>(" + (this.page + 1) + "/" + pages + ")"));
        for (int i = 0; i < PAR_PAGE; i++) {
            int index = this.page * PAR_PAGE + i;
            if (index >= liste.size()) break;
            inventaire.setItem(i, plugin.objets().fabriquer(liste.get(index), 1));
        }
        ItemStack vitre = bouton(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 45; i < 54; i++) inventaire.setItem(i, vitre);
        if (this.page > 0) inventaire.setItem(45, bouton(Material.ARROW, "<yellow>« Page précédente"));
        if (this.page < pages - 1) inventaire.setItem(53, bouton(Material.ARROW, "<yellow>Page suivante »"));
        inventaire.setItem(49, bouton(Material.BOOK, "<white>" + liste.size() + " objet(s)"));
    }

    private static ItemStack bouton(Material m, String nom) {
        ItemStack b = new ItemStack(m, 1);
        ItemMeta meta = b.getItemMeta();
        if (meta != null) {
            meta.displayName(Messages.objet(nom));
            b.setItemMeta(meta);
        }
        return b;
    }

    @Override
    public Inventory getInventory() {
        return inventaire;
    }

    public void ouvrir(Player p) {
        p.openInventory(inventaire);
    }

    void clic(Player p, int slot, boolean pile) {
        if (slot == 45 && page > 0) { new MenuObjets(plugin, page - 1).ouvrir(p); return; }
        if (slot == 53) { new MenuObjets(plugin, page + 1).ouvrir(p); return; }
        if (slot >= PAR_PAGE) return;
        int index = page * PAR_PAGE + slot;
        if (index >= liste.size()) return;
        DefinitionObjet d = liste.get(index);
        ItemStack it = plugin.objets().fabriquer(d, pile ? 64 : 1);
        donner(p, it);
        p.sendMessage(Messages.brut("<gray>Reçu : <white>" + (pile ? "64 × " : "") + Messages.echapper(d.id())));
    }

    /** Donne l'objet, et le fait tomber aux pieds du joueur si l'inventaire est plein. */
    public static void donner(Player p, ItemStack it) {
        Map<Integer, ItemStack> reste = p.getInventory().addItem(it);
        for (ItemStack r : reste.values()) p.getWorld().dropItemNaturally(p.getLocation(), r);
    }

}
