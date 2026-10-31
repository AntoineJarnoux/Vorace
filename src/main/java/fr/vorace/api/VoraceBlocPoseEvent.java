package fr.vorace.api;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Un joueur pose un bloc Vorace (annulable : le bloc d'avant revient). */
public final class VoraceBlocPoseEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Player joueur;
    private final String bloc;
    private final Block position;
    private boolean annule;

    public VoraceBlocPoseEvent(Player joueur, String bloc, Block position) {
        this.joueur = joueur;
        this.bloc = bloc;
        this.position = position;
    }

    public Player getJoueur() { return joueur; }
    /** Identifiant de l'objet Vorace du bloc. */
    public String getBloc() { return bloc; }
    public Block getPosition() { return position; }

    @Override public boolean isCancelled() { return annule; }
    @Override public void setCancelled(boolean c) { annule = c; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
