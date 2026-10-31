package fr.vorace.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Un joueur va poser un meuble Vorace (annulable). */
public final class VoraceMeublePoseEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Player joueur;
    private final String meuble;
    private final Location position;
    private boolean annule;

    public VoraceMeublePoseEvent(Player joueur, String meuble, Location position) {
        this.joueur = joueur;
        this.meuble = meuble;
        this.position = position;
    }

    public Player getJoueur() { return joueur; }
    /** Identifiant de l'objet Vorace du meuble. */
    public String getMeuble() { return meuble; }
    public Location getPosition() { return position; }

    @Override public boolean isCancelled() { return annule; }
    @Override public void setCancelled(boolean c) { annule = c; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
