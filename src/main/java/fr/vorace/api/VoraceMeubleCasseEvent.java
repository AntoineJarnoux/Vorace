package fr.vorace.api;

import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Un meuble Vorace va être cassé (annulable). Le joueur est null si c'est une commande ou un plugin. */
public final class VoraceMeubleCasseEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Player joueur;
    private final String meuble;
    private final ItemDisplay entite;
    private boolean annule;

    public VoraceMeubleCasseEvent(Player joueur, String meuble, ItemDisplay entite) {
        this.joueur = joueur;
        this.meuble = meuble;
        this.entite = entite;
    }

    public Player getJoueur() { return joueur; }
    public String getMeuble() { return meuble; }
    /** L'entité qui affiche le meuble. */
    public ItemDisplay getEntite() { return entite; }

    @Override public boolean isCancelled() { return annule; }
    @Override public void setCancelled(boolean c) { annule = c; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
