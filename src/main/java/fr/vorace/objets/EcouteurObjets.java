package fr.vorace.objets;

import fr.vorace.Messages;
import fr.vorace.Vorace;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Commandes au clic, mise à jour des objets des joueurs, clics dans le menu des objets. */
public final class EcouteurObjets implements Listener {

    private final Vorace plugin;
    private final Map<String, Long> delais = new ConcurrentHashMap<>();

    public EcouteurObjets(Vorace plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void clic(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return; // l'événement arrive aussi pour la main gauche
        ItemStack objet = e.getItem();
        String id = plugin.objets().id(objet);
        if (id == null) return;
        DefinitionObjet d = plugin.objets().definition(id);
        if (d == null || !d.aDesCommandes()) return;
        Action a = e.getAction();
        boolean droit = a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK;
        boolean gauche = a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK;
        List<String> commandes = droit ? d.commandesDroit() : gauche ? d.commandesGauche() : List.of();
        if (commandes.isEmpty()) return;
        e.setCancelled(true);

        Player p = e.getPlayer();
        if (d.delaiTicks() > 0) {
            String cle = p.getUniqueId() + ":" + id;
            long maintenant = System.currentTimeMillis();
            Long fin = delais.get(cle);
            if (fin != null && fin > maintenant) {
                if (d.messageDelai() != null) {
                    p.sendMessage(Messages.objet(d.messageDelai().replace("%secondes%", String.valueOf((fin - maintenant + 999) / 1000))));
                }
                return;
            }
            delais.put(cle, maintenant + d.delaiTicks() * 50L);
        }
        for (String c : commandes) executer(p, c);
        if (d.consommer()) objet.setAmount(objet.getAmount() - 1);
    }

    /** "console: give %joueur% diamond" ou "joueur: spawn" (par défaut : joueur). */
    public static void executer(Player p, String brut) {
        String c = brut.trim();
        boolean console = false;
        if (c.toLowerCase().startsWith("console:")) { console = true; c = c.substring(8).trim(); }
        else if (c.toLowerCase().startsWith("joueur:")) { c = c.substring(7).trim(); }
        if (c.startsWith("/")) c = c.substring(1);
        Location l = p.getLocation();
        c = c.replace("%joueur%", p.getName()).replace("%uuid%", p.getUniqueId().toString())
            .replace("%x%", String.valueOf(l.getBlockX())).replace("%y%", String.valueOf(l.getBlockY()))
            .replace("%z%", String.valueOf(l.getBlockZ())).replace("%monde%", l.getWorld() == null ? "world" : l.getWorld().getName());
        Bukkit.dispatchCommand(console ? Bukkit.getConsoleSender() : p, c);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void arrivee(PlayerJoinEvent e) {
        if (plugin.getConfig().getBoolean("objets.mise-a-jour-auto", true)) mettreAJour(e.getPlayer());
    }

    @EventHandler
    public void depart(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        delais.keySet().removeIf(k -> k.startsWith(id.toString()));
    }

    /** Remplace les objets Vorace périmés de l'inventaire par leur version actuelle. */
    public int mettreAJour(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] contenu = inv.getContents();
        int n = 0;
        for (int i = 0; i < contenu.length; i++) {
            ItemStack it = contenu[i];
            String id = plugin.objets().id(it);
            if (id == null) continue;
            DefinitionObjet d = plugin.objets().definition(id);
            if (d == null || !plugin.objets().perime(it, d)) continue;
            inv.setItem(i, plugin.objets().mettreAJour(it, d));
            n++;
        }
        if (n > 0) p.updateInventory();
        return n;
    }

    // ---------------------------------------------------------------- menu

    @EventHandler(priority = EventPriority.HIGH)
    public void clicMenu(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof MenuObjets menu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= e.getInventory().getSize()) return;
        menu.clic(p, slot, e.isShiftClick());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void glisserMenu(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof MenuObjets) e.setCancelled(true);
    }
}
