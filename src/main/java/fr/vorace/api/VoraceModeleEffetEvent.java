package fr.vorace.api;

import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Une clé de la piste « Effets » d'une animation de Blockbench est atteinte (son, particule ou script « timeline »).
 * Annulable : le son ou la particule n'est alors pas joué. Les scripts « timeline » ne sont jamais exécutés par
 * Vorace : c'est à un plugin de les lire ici (par exemple « degats » pour infliger des dégâts au bon moment).
 */
public final class VoraceModeleEffetEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Entity base;
    private final String modele, animation, type, valeur;
    private boolean annule;

    public VoraceModeleEffetEvent(Entity base, String modele, String animation, String type, String valeur) {
        this.base = base;
        this.modele = modele;
        this.animation = animation;
        this.type = type;
        this.valeur = valeur;
    }

    /** Entité qui porte le modèle (base posée ou mob). */
    public Entity getBase() { return base; }
    public String getModele() { return modele; }
    public String getAnimation() { return animation; }
    /** « sound », « particle » ou « timeline ». */
    public String getType() { return type; }
    /** Nom du son ou de la particule, ou texte du script. */
    public String getValeur() { return valeur; }

    @Override public boolean isCancelled() { return annule; }
    @Override public void setCancelled(boolean c) { annule = c; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
