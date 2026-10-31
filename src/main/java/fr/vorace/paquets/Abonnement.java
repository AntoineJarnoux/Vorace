package fr.vorace.paquets;

import org.bukkit.plugin.Plugin;

import java.util.Set;

/** Une écoute de paquets. {@link #annuler()} l'arrête ; elle s'arrête aussi toute seule quand son plugin s'arrête. */
public final class Abonnement {

    final Plugin plugin;
    final Sens sens;
    /** Noms des paquets (« ClientboundSetTimePacket »), vide = tous. */
    final Set<String> noms;
    final EcouteurPaquet ecouteur;
    private final Paquets gestion;
    volatile boolean erreurSignalee;

    Abonnement(Paquets gestion, Plugin plugin, Sens sens, Set<String> noms, EcouteurPaquet ecouteur) {
        this.gestion = gestion;
        this.plugin = plugin;
        this.sens = sens;
        this.noms = noms;
        this.ecouteur = ecouteur;
    }

    public Plugin plugin() { return plugin; }
    public Sens sens() { return sens; }
    public Set<String> noms() { return noms; }

    public void annuler() {
        gestion.retirer(this);
    }

    /** Une écoute de « ServerboundMovePlayerPacket » reçoit aussi ses variantes (« ServerboundMovePlayerPacket.Pos »...). */
    boolean concerne(String nom) {
        if (noms.isEmpty() || noms.contains(nom)) return true;
        int point = nom.indexOf('.');
        return point > 0 && noms.contains(nom.substring(0, point));
    }
}
