package fr.vorace.paquets;

import org.bukkit.plugin.Plugin;

import java.util.Set;

/** Une écoute de paquets. {@link #annuler()} l'arrête ; elle s'arrête aussi toute seule quand son plugin s'arrête. */
public final class Abonnement {

    final Plugin plugin;
    final Sens sens;
    /** Noms courts des paquets (« ClientboundSetTimePacket »), vide = tous. */
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

    boolean concerne(String nom) {
        return noms.isEmpty() || noms.contains(nom);
    }
}
