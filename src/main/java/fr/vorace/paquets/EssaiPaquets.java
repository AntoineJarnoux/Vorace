package fr.vorace.paquets;

import fr.vorace.Vorace;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Essai de la couche paquets avec les joueurs connectés (sert à l'essai automatique sur un vrai serveur) :
 * l'heure envoyée est remplacée par midi, un message est bloqué, un autre passe, les paquets reçus sont comptés.
 */
public final class EssaiPaquets {

    private final Vorace plugin;
    private final Paquets paquets;

    public EssaiPaquets(Vorace plugin, Paquets paquets) {
        this.plugin = plugin;
        this.paquets = paquets;
    }

    public void lancer(CommandSender s) {
        AtomicInteger heures = new AtomicInteger(), bloques = new AtomicInteger(), recus = new AtomicInteger(), erreurs = new AtomicInteger();
        List<Abonnement> ab = List.of(
            paquets.ecouter(plugin, Sens.SORTANT, p -> {
                try {
                    p.ecrire("dayTime", 6000L);
                    heures.incrementAndGet();
                } catch (RuntimeException e) {
                    erreurs.incrementAndGet();
                    throw e;
                }
            }, "ClientboundSetTimePacket"),
            paquets.ecouter(plugin, Sens.SORTANT, p -> {
                String t = p.texte("content");
                if (t != null && t.contains("VORACE-BLOQUE")) {
                    p.annuler();
                    bloques.incrementAndGet();
                }
            }, "ClientboundSystemChatPacket"),
            paquets.ecouter(plugin, Sens.ENTRANT, p -> recus.incrementAndGet()));
        s.sendMessage(Paquets.t("Essai des paquets : " + Bukkit.getOnlinePlayers().size() + " joueur(s), " + paquets.connexions() + " connexion(s)"));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player j : Bukkit.getOnlinePlayers()) {
                j.sendMessage(Paquets.t("VORACE-BLOQUE ce message ne doit pas arriver"));
                j.sendMessage(Paquets.t("VORACE-LIBRE ce message doit arriver"));
            }
        }, 20L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            ab.forEach(Abonnement::annuler);
            s.sendMessage(Paquets.t("ESSAI PAQUETS heures-modifiees=" + heures.get() + " messages-bloques=" + bloques.get() + " paquets-recus=" + recus.get()
                + " erreurs=" + erreurs.get()));
        }, 100L);
    }
}
