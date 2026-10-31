package fr.vorace.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Le pack vient d'être (re)construit. Appelé sur le fil principal du serveur. */
public final class VoracePackPretEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final String sha1;
    private final boolean change;

    public VoracePackPretEvent(String sha1, boolean change) {
        this.sha1 = sha1;
        this.change = change;
    }

    /** Empreinte SHA-1 du nouveau pack. */
    public String getSha1() { return sha1; }

    /** Vrai si le contenu a changé par rapport au pack précédent. */
    public boolean aChange() { return change; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
