package fr.vorace.meubles;

import fr.vorace.Vorace;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import fr.vorace.objets.DefinitionObjet;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Pose, cassage, sièges et coffres des meubles. */
public final class EcouteurMeubles implements Listener {

    private final Vorace plugin;
    /** Dernière action par joueur (le jeu envoie souvent le même clic pour les deux mains). */
    private final Map<UUID, Long> derniereAction = new ConcurrentHashMap<>();

    public EcouteurMeubles(Vorace plugin) {
        this.plugin = plugin;
    }

    private Meubles meubles() { return plugin.meubles(); }

    private boolean dejaFait(Player p) {
        long t = System.currentTimeMillis();
        Long avant = derniereAction.put(p.getUniqueId(), t);
        return avant != null && t - avant < 120;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void clic(PlayerInteractEvent e) {
        Block b = e.getClickedBlock();
        if (b == null) return;
        Player p = e.getPlayer();
        Action a = e.getAction();

        if (a == Action.LEFT_CLICK_BLOCK) {
            ItemDisplay m = meubles().depuisBloc(b);
            if (m == null) return;
            e.setCancelled(true);
            if (!dejaFait(p)) meubles().casser(p, m, true);
            return;
        }
        if (a != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack objet = e.getItem();
        DefinitionObjet d = objet == null ? null : plugin.objets().definition(plugin.objets().id(objet));
        boolean tientMeuble = d != null && d.meuble() != null;

        // clic sur la hitbox d'un meuble
        ItemDisplay m = meubles().depuisBloc(b);
        if (m != null && !(p.isSneaking() && objet != null && !objet.getType().isAir())) {
            DefinitionObjet dm = meubles().definition(m);
            if (dm != null && dm.meuble() != null && dm.meuble().interactif()) {
                e.setCancelled(true);
                if (!dejaFait(p)) meubles().interagir(p, m, e.getInteractionPoint());
                return;
            }
        }

        if (!tientMeuble) return;
        if (e.useItemInHand() == Event.Result.DENY && e.useInteractedBlock() == Event.Result.DENY) return;
        e.setCancelled(true);
        if (dejaFait(p)) return;
        EquipmentSlot main = e.getHand() == null ? EquipmentSlot.HAND : e.getHand();
        meubles().poser(p, objet, d, b, e.getBlockFace(), main, true);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void clicEntite(PlayerInteractEntityEvent e) {
        ItemDisplay m = meubles().depuisEntite(e.getRightClicked());
        if (m == null) return;
        e.setCancelled(true);
        if (e.getHand() == EquipmentSlot.OFF_HAND || dejaFait(e.getPlayer())) return;
        meubles().interagir(e.getPlayer(), m, e.getRightClicked().getLocation());
    }

    /** Coup sur la hitbox « entité » d'un meuble (Paper prévient avant même le calcul des dégâts). */
    @EventHandler(priority = EventPriority.NORMAL)
    public void attaque(PrePlayerAttackEntityEvent e) {
        ItemDisplay m = meubles().depuisEntite(e.getAttacked());
        if (m == null) return;
        e.setCancelled(true);
        if (!dejaFait(e.getPlayer())) meubles().casser(e.getPlayer(), m, true);
    }

    /** Coup sur la hitbox « entité » d'un meuble (autres cas). */
    @EventHandler(priority = EventPriority.NORMAL)
    public void coup(EntityDamageByEntityEvent e) {
        ItemDisplay m = meubles().depuisEntite(e.getEntity());
        if (m == null) return;
        e.setCancelled(true);
        if (e.getDamager() instanceof Player p && !dejaFait(p)) meubles().casser(p, m, true);
    }

    /** En créatif, la barrière est cassée directement : on casse le meuble à la place. */
    @EventHandler(priority = EventPriority.LOW)
    public void cassageBloc(BlockBreakEvent e) {
        if (meubles().simulation) return;
        ItemDisplay m = meubles().depuisBloc(e.getBlock());
        if (m == null) return;
        e.setCancelled(true);
        if (!dejaFait(e.getPlayer())) meubles().casser(e.getPlayer(), m, true);
    }

    @EventHandler
    public void descente(EntityDismountEvent e) {
        meubles().descendre(e.getEntity(), e.getDismounted());
    }

    @EventHandler
    public void chargement(EntitiesLoadEvent e) {
        for (Entity x : e.getEntities()) {
            if (x instanceof ItemDisplay d && meubles().id(d) != null) meubles().rafraichir(d);
        }
    }

    @EventHandler
    public void fermeture(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof StockageMeuble s) {
            meubles().fermerStockage(s, e.getInventory(), e.getInventory().getViewers().size() - 1);
        }
    }
}
