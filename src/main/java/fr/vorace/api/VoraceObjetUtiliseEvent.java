package fr.vorace.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Un joueur utilise un objet Vorace qui a des commandes (clic droit ou gauche). Annulable, commandes modifiables. */
public final class VoraceObjetUtiliseEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Player joueur;
    private final String objet;
    private final ItemStack item;
    private final boolean clicDroit;
    private final List<String> commandes;
    private boolean annule;

    public VoraceObjetUtiliseEvent(Player joueur, String objet, ItemStack item, boolean clicDroit, List<String> commandes) {
        this.joueur = joueur;
        this.objet = objet;
        this.item = item;
        this.clicDroit = clicDroit;
        this.commandes = new ArrayList<>(commandes);
    }

    public Player getJoueur() { return joueur; }
    /** Identifiant Vorace de l'objet. */
    public String getObjet() { return objet; }
    public ItemStack getItem() { return item; }
    public boolean isClicDroit() { return clicDroit; }
    /** Commandes qui vont être lancées (modifiable : « console: ... » ou « joueur: ... »). */
    public List<String> getCommandes() { return commandes; }

    @Override public boolean isCancelled() { return annule; }
    @Override public void setCancelled(boolean c) { annule = c; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
