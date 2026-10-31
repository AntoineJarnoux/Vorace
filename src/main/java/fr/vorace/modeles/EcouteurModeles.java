package fr.vorace.modeles;

import fr.vorace.Vorace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;

/** Animations automatiques des modèles (dégâts, attaque, mort, clic) et rechargement avec les chunks. */
public final class EcouteurModeles implements Listener {

    private final Vorace plugin;

    public EcouteurModeles(Vorace plugin) {
        this.plugin = plugin;
    }

    private Modeles modeles() { return plugin.modeles(); }

    @EventHandler
    public void chargement(EntitiesLoadEvent e) {
        modeles().charger(e.getEntities());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void degats(EntityDamageEvent e) {
        Modeles.Instance i = modeles().instance(e.getEntity());
        if (i != null && i.surMob()) i.jouerAuto("degat");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void attaque(EntityDamageByEntityEvent e) {
        Entity source = e.getDamager();
        Modeles.Instance i = modeles().instance(source);
        if (i != null && i.surMob()) i.jouerAuto("attaque");
    }

    @EventHandler
    public void mort(EntityDeathEvent e) {
        Modeles.Instance i = modeles().instance(e.getEntity());
        if (i != null) i.mourir();
    }

    @EventHandler(ignoreCancelled = true)
    public void clic(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Interaction)) return;
        Modeles.Instance i = modeles().instance(e.getRightClicked());
        if (i != null) i.jouerAuto("clic");
    }
}
