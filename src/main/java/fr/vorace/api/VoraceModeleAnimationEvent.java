package fr.vorace.api;

import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Une animation demandée va être jouée sur un modèle animé (commande, API, clic, dégâts, attaque, mort).
 * Annulable ; l'animation peut être changée. Les animations automatiques de déplacement (repos, marche, course)
 * ne passent pas par cet événement.
 */
public final class VoraceModeleAnimationEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Entity base;
    private final String modele;
    private String animation;
    private boolean boucle;
    private boolean annule;

    public VoraceModeleAnimationEvent(Entity base, String modele, String animation, boolean boucle) {
        this.base = base;
        this.modele = modele;
        this.animation = animation;
        this.boucle = boucle;
    }

    /** Entité qui porte le modèle (base posée ou mob). */
    public Entity getBase() { return base; }
    public String getModele() { return modele; }
    public String getAnimation() { return animation; }
    /** Change l'animation jouée (doit exister dans le modèle, sinon rien n'est joué). */
    public void setAnimation(String animation) { this.animation = animation; }
    public boolean isBoucle() { return boucle; }
    public void setBoucle(boolean boucle) { this.boucle = boucle; }

    @Override public boolean isCancelled() { return annule; }
    @Override public void setCancelled(boolean c) { annule = c; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
