package fr.vorace.envoi;

import fr.vorace.Messages;
import fr.vorace.Reglages;
import fr.vorace.Vorace;
import fr.vorace.api.VoracePackChargeEvent;
import fr.vorace.pack.Constructeur;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Envoie le pack aux joueurs et suit leur réponse (accepté, refusé, chargé, échec). */
public final class Envoi implements Listener {

    /** Identifiant fixe du pack de Vorace : un nouvel envoi remplace l'ancien chez le joueur. */
    public static final UUID ID_PACK = UUID.nameUUIDFromBytes("vorace:pack".getBytes(StandardCharsets.UTF_8));

    public enum Etat { EN_ATTENTE, ENVOYE, ACCEPTE, TELECHARGE, CHARGE, REFUSE, ECHEC }

    private final Vorace plugin;
    private final Set<UUID> enAttente = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Etat> etats = new ConcurrentHashMap<>();
    private final Map<UUID, String> urls = new ConcurrentHashMap<>();

    public Envoi(Vorace plugin) {
        this.plugin = plugin;
    }

    public Etat etat(Player p) { return etats.get(p.getUniqueId()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void arrivee(PlayerJoinEvent e) {
        Reglages r = plugin.reglages();
        if (!r.envoiActif()) return;
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) envoyer(p);
        }, r.delaiTicks());
    }

    @EventHandler
    public void depart(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        enAttente.remove(id);
        etats.remove(id);
        urls.remove(id);
    }

    /** Envoie le pack actuel au joueur (ou le met en attente s'il n'est pas encore construit). */
    public void envoyer(Player p) {
        Constructeur.Resultat pack = plugin.packs().actuel();
        if (pack == null) {
            enAttente.add(p.getUniqueId());
            etats.put(p.getUniqueId(), Etat.EN_ATTENTE);
            return;
        }
        Reglages r = plugin.reglages();
        String url = plugin.hebergeur().url(p, pack.sha1());
        urls.put(p.getUniqueId(), url);
        ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(ID_PACK, URI.create(url), pack.sha1());
        ResourcePackRequest demande = ResourcePackRequest.resourcePackRequest()
            .packs(info)
            .required(r.obligatoire())
            .replace(false)
            .prompt(Messages.composant("invite"))
            .build();
        p.sendResourcePacks(demande);
        etats.put(p.getUniqueId(), Etat.ENVOYE);
        if (r.debug()) plugin.getLogger().info("Pack envoyé à " + p.getName() + " : " + url);
    }

    /** Le pack vient d'être construit : on sert ceux qui attendaient, et tout le monde si demandé. */
    public void packPret(boolean change, boolean renvoyerATous) {
        for (UUID id : Set.copyOf(enAttente)) {
            enAttente.remove(id);
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) envoyer(p);
        }
        if (change && renvoyerATous) Bukkit.getOnlinePlayers().forEach(this::envoyer);
    }

    @EventHandler
    public void statut(PlayerResourcePackStatusEvent e) {
        if (!ID_PACK.equals(e.getID())) return; // pack d'un autre plugin
        Player p = e.getPlayer();
        Reglages r = plugin.reglages();
        PlayerResourcePackStatusEvent.Status s = e.getStatus();
        String nom = s.name();
        if (r.debug()) plugin.getLogger().info(p.getName() + " : " + nom);
        switch (nom) {
            case "ACCEPTED" -> etats.put(p.getUniqueId(), Etat.ACCEPTE);
            case "DOWNLOADED" -> etats.put(p.getUniqueId(), Etat.TELECHARGE);
            case "SUCCESSFULLY_LOADED" -> {
                etats.put(p.getUniqueId(), Etat.CHARGE);
                Bukkit.getPluginManager().callEvent(new VoracePackChargeEvent(p));
            }
            case "DECLINED" -> {
                etats.put(p.getUniqueId(), Etat.REFUSE);
                if (r.expulserSiRefus()) p.kick(Messages.composant("expulsion-refus"));
                else p.sendMessage(Messages.prefixe("refuse"));
            }
            case "FAILED_DOWNLOAD", "INVALID_URL", "FAILED_RELOAD" -> {
                etats.put(p.getUniqueId(), Etat.ECHEC);
                plugin.getLogger().warning(p.getName() + " n'a pas pu charger le pack (" + nom + ") depuis "
                    + urls.getOrDefault(p.getUniqueId(), "?") + ". Voir /vorace info et la section Dépannage du guide.");
                p.sendMessage(Messages.prefixe("echec"));
            }
            default -> { /* DISCARDED, etc. */ }
        }
    }
}
