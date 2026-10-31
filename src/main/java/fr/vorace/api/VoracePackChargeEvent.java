package fr.vorace.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Un joueur a fini de charger le pack de Vorace. Utile pour un autre plugin qui veut attendre
 * que les textures soient là (ouvrir un menu, lancer une cinématique, etc.).
 */
public final class VoracePackChargeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Player joueur;

    public VoracePackChargeEvent(Player joueur) {
        this.joueur = joueur;
    }

    public Player getJoueur() { return joueur; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
