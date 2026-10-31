package fr.vorace.api;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Un joueur a fini de miner un bloc Vorace (annulable). Le butin peut être modifié. */
public final class VoraceBlocCasseEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Player joueur;
    private final String bloc;
    private final Block position;
    private final List<ItemStack> butin;
    private boolean annule;

    public VoraceBlocCasseEvent(Player joueur, String bloc, Block position, List<ItemStack> butin) {
        this.joueur = joueur;
        this.bloc = bloc;
        this.position = position;
        this.butin = new ArrayList<>(butin);
    }

    public Player getJoueur() { return joueur; }
    /** Identifiant de l'objet Vorace du bloc. */
    public String getBloc() { return bloc; }
    public Block getPosition() { return position; }
    /** Butin lâché (modifiable). */
    public List<ItemStack> getButin() { return butin; }

    @Override public boolean isCancelled() { return annule; }
    @Override public void setCancelled(boolean c) { annule = c; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
