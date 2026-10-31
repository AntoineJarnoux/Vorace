package fr.vorace.paquets;

/**
 * Reçoit les paquets écoutés. Appelé sur le fil réseau du joueur (pas le fil principal du serveur) :
 * pour utiliser l'API Bukkit depuis ici, passer par {@code Bukkit.getScheduler().runTask(...)}.
 */
@FunctionalInterface
public interface EcouteurPaquet {
    void paquet(Paquet paquet) throws Exception;
}
