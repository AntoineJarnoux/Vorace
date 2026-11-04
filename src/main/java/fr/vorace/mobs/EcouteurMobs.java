package fr.vorace.mobs;

import fr.vorace.Vorace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;

/** Suivi des mobs de Vorace : chargement et déchargement des chunks, coups reçus, morts. */
public final class EcouteurMobs implements Listener {

    private final Vorace plugin;

    public EcouteurMobs(Vorace plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void chargement(EntitiesLoadEvent e) {
        plugin.mobs().charger(e.getEntities());
    }

    @EventHandler
    public void dechargement(EntitiesUnloadEvent e) {
        for (Entity x : e.getEntities()) plugin.mobs().decharger(x);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void coup(EntityDamageByEntityEvent e) {
        Entity source = e.getDamager();
        Player joueur = source instanceof Player p ? p
            : source instanceof Projectile pr && pr.getShooter() instanceof Player p ? p : null;
        plugin.mobs().blesse(e.getEntity(), joueur);
    }

    @EventHandler
    public void mort(EntityDeathEvent e) {
        plugin.mobs().mort(e.getEntity());
    }
}
