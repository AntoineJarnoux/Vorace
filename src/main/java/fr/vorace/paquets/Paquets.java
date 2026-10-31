package fr.vorace.paquets;

import fr.vorace.Vorace;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.papermc.paper.network.ChannelInitializeListenerHolder;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Level;

/**
 * Couche paquets (rôle de ProtocolLib) : Vorace se glisse dans la connexion de chaque joueur et laisse les plugins
 * lire, modifier, annuler ou envoyer les paquets du jeu. Rien n'est fait tant que personne n'écoute (coût nul).
 */
public final class Paquets implements Listener {

    static final String NOM_GESTIONNAIRE = "vorace_paquets";
    private static final Key CLE = Key.key("vorace", "paquets");

    private final Vorace plugin;
    private final List<Abonnement> abonnements = new CopyOnWriteArrayList<>();
    private volatile Map<String, Abonnement[]> cacheSortant = new ConcurrentHashMap<>(), cacheEntrant = new ConcurrentHashMap<>();
    private final Set<Intercepteur> intercepteurs = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Intercepteur> parJoueur = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, LongAdder>> comptes = new ConcurrentHashMap<>();
    private final List<Journal> journaux = new CopyOnWriteArrayList<>();
    private volatile boolean actif;

    /** Texte avec codes § vers composant. */
    static net.kyori.adventure.text.Component t(String s) {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(s);
    }

    public Paquets(Vorace plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ démarrage

    public void demarrer() {
        ChannelInitializeListenerHolder.addListener(CLE, this::injecter);
        actif = true;
        for (Player p : Bukkit.getOnlinePlayers()) attacher(p);
    }

    public void arreter() {
        actif = false;
        try { ChannelInitializeListenerHolder.removeListener(CLE); } catch (Throwable ignore) { }
        for (Intercepteur i : new ArrayList<>(intercepteurs)) i.retirer();
        intercepteurs.clear();
        parJoueur.clear();
        abonnements.clear();
        vider();
    }

    /** Pour les tests : un gestionnaire sans serveur. */
    Intercepteur pourEssai(Channel canal) {
        actif = true;
        Intercepteur i = new Intercepteur(canal);
        intercepteurs.add(i);
        return i;
    }

    private void injecter(Channel canal) {
        if (!actif || canal.pipeline().get(NOM_GESTIONNAIRE) != null) return;
        Intercepteur i = new Intercepteur(canal);
        ChannelPipeline p = canal.pipeline();
        if (p.get("packet_handler") != null) p.addBefore("packet_handler", NOM_GESTIONNAIRE, i);
        else p.addLast(NOM_GESTIONNAIRE, i);
        intercepteurs.add(i);
    }

    /** Relie le joueur à sa connexion (à l'arrivée, ou au démarrage pour les joueurs déjà là). */
    private void attacher(Player p) {
        Intercepteur trouve = null;
        try {
            Channel c = Reflexion.canal(p);
            if (c.pipeline().get(NOM_GESTIONNAIRE) == null) c.eventLoop().submit(() -> injecter(c)).syncUninterruptibly();
            if (c.pipeline().get(NOM_GESTIONNAIRE) instanceof Intercepteur i) trouve = i;
        } catch (Throwable t) {
            // version du jeu inattendue : on retrouve la connexion par le pseudo annoncé
            for (Intercepteur i : intercepteurs) if (i.joueur == null && p.getName().equalsIgnoreCase(i.nomConnexion)) { trouve = i; break; }
        }
        if (trouve != null) {
            trouve.joueur = p;
            parJoueur.put(p.getUniqueId(), trouve);
        } else {
            plugin.getLogger().warning("Paquets : connexion de " + p.getName() + " introuvable, ses paquets ne seront pas vus");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void arrivee(PlayerJoinEvent e) {
        if (actif) attacher(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void depart(PlayerQuitEvent e) {
        Intercepteur i = parJoueur.remove(e.getPlayer().getUniqueId());
        if (i != null) intercepteurs.remove(i);
        comptes.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void pluginArrete(PluginDisableEvent e) {
        boolean change = abonnements.removeIf(a -> a.plugin == e.getPlugin());
        if (change) vider();
    }

    // ------------------------------------------------------------------ écoute

    /**
     * Écoute des paquets.
     * @param noms noms courts (« ClientboundSetTimePacket ») ; aucun = tous les paquets de ce sens
     */
    public Abonnement ecouter(Plugin p, Sens sens, EcouteurPaquet e, String... noms) {
        if (p == null || sens == null || e == null) throw new IllegalArgumentException("plugin, sens et écouteur obligatoires");
        Set<String> n = noms == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(noms)));
        Abonnement a = new Abonnement(this, p, sens, n, e);
        abonnements.add(a);
        vider();
        return a;
    }

    void retirer(Abonnement a) {
        if (abonnements.remove(a)) vider();
    }

    private void vider() {
        cacheSortant = new ConcurrentHashMap<>();
        cacheEntrant = new ConcurrentHashMap<>();
    }

    public List<Abonnement> abonnements() { return List.copyOf(abonnements); }
    public int connexions() { return intercepteurs.size(); }

    private Abonnement[] pour(Sens sens, String nom) {
        Map<String, Abonnement[]> cache = sens == Sens.SORTANT ? cacheSortant : cacheEntrant;
        Abonnement[] r = cache.get(nom);
        if (r == null) {
            r = abonnements.stream().filter(a -> a.sens == sens && a.concerne(nom)).toArray(Abonnement[]::new);
            cache.put(nom, r);
        }
        return r;
    }

    // ------------------------------------------------------------------ envoi

    /** Envoie un paquet du jeu au joueur. Avec ecouteurs = faux, les écoutes ne le voient pas. */
    public void envoyer(Player joueur, Object paquet, boolean ecouteurs) {
        Intercepteur i = intercepteur(joueur);
        i.canal.writeAndFlush(ecouteurs ? paquet : new Direct(paquet));
    }

    /** Fait comme si le joueur avait envoyé ce paquet au serveur. */
    public void recevoir(Player joueur, Object paquet, boolean ecouteurs) {
        Intercepteur i = intercepteur(joueur);
        i.canal.eventLoop().execute(() -> {
            ChannelHandlerContext ctx = i.canal.pipeline().context(NOM_GESTIONNAIRE);
            if (ctx == null) return;
            Object r = ecouteurs ? traiter(i, Sens.ENTRANT, paquet) : paquet;
            if (r != null) ctx.fireChannelRead(r);
        });
    }

    private Intercepteur intercepteur(Player joueur) {
        Intercepteur i = parJoueur.get(joueur.getUniqueId());
        if (i == null || !i.canal.isOpen()) throw new IllegalStateException(joueur.getName() + " n'est pas connecté (ou sa connexion est introuvable)");
        return i;
    }

    // ------------------------------------------------------------------ traitement

    /** Le paquet à transmettre (éventuellement changé), ou null s'il est annulé. */
    Object traiter(Intercepteur i, Sens sens, Object msg) {
        if (msg instanceof Direct d) return d.paquet;
        Class<?> c = msg.getClass();
        if (!c.getName().startsWith("net.minecraft.")) return msg;
        String nom = c.getSimpleName();
        if (i.joueur == null && i.nomConnexion == null && nom.equals("ServerboundHelloPacket")) {
            try { i.nomConnexion = String.valueOf(Reflexion.lire(msg, "name")); } catch (RuntimeException ignore) { }
        }
        if (sens == Sens.SORTANT && nom.equals("ClientboundBundlePacket")) return traiterLot(i, msg);
        noter(i, sens, msg, nom);
        Abonnement[] liste = pour(sens, nom);
        if (liste.length == 0) return msg;
        Paquet p = new Paquet(i.joueur, i.nomConnexion, sens, msg);
        for (Abonnement a : liste) {
            try {
                a.ecouteur.paquet(p);
            } catch (Throwable t) {
                if (!a.erreurSignalee) {
                    a.erreurSignalee = true;
                    if (plugin != null) plugin.getLogger().log(Level.WARNING, "Paquets : l'écoute de " + a.plugin.getName() + " a échoué sur " + nom + " (signalé une seule fois)", t);
                }
            }
        }
        return p.annule() ? null : p.brut();
    }

    /** Lot de paquets (le jeu en regroupe certains) : chaque paquet du lot passe par les écoutes. */
    private Object traiterLot(Intercepteur i, Object lot) {
        try {
            Method m = lot.getClass().getMethod("subPackets");
            Iterable<?> sous = (Iterable<?>) m.invoke(lot);
            List<Object> garde = new ArrayList<>();
            boolean change = false;
            for (Object s : sous) {
                Object r = traiter(i, Sens.SORTANT, s);
                if (r != s) change = true;
                if (r != null) garde.add(r);
            }
            if (!change) return lot;
            if (garde.isEmpty()) return null;
            for (Constructor<?> k : lot.getClass().getConstructors()) {
                if (k.getParameterCount() == 1 && k.getParameterTypes()[0].isAssignableFrom(List.class)) return k.newInstance(garde);
            }
            return lot;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return lot;
        }
    }

    // ------------------------------------------------------------------ statistiques et journal

    private void noter(Intercepteur i, Sens sens, Object msg, String nom) {
        Player j = i.joueur;
        if (j == null) return;
        Map<String, LongAdder> m = comptes.get(j.getUniqueId());
        if (m != null) m.computeIfAbsent((sens == Sens.SORTANT ? "→ " : "← ") + nom, k -> new LongAdder()).increment();
        if (journaux.isEmpty()) return;
        for (Journal jl : journaux) jl.noter(j, sens, nom, msg);
    }

    /** Compte les paquets d'un joueur pendant quelques secondes puis affiche les plus fréquents. */
    public void statistiques(CommandSender s, Player cible, int secondes) {
        Map<String, LongAdder> m = new ConcurrentHashMap<>();
        comptes.put(cible.getUniqueId(), m);
        s.sendMessage(Paquets.t("§7Comptage des paquets de §f" + cible.getName() + "§7 pendant " + secondes + " s..."));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            comptes.remove(cible.getUniqueId(), m);
            List<Map.Entry<String, LongAdder>> l = new ArrayList<>(m.entrySet());
            l.sort((a, b) -> Long.compare(b.getValue().sum(), a.getValue().sum()));
            long total = l.stream().mapToLong(e -> e.getValue().sum()).sum();
            s.sendMessage(Paquets.t("§7Paquets de §f" + cible.getName() + "§7 : §f" + total + "§7 en " + secondes + " s (→ vers le joueur, ← depuis le joueur)"));
            for (int k = 0; k < Math.min(15, l.size()); k++) {
                long n = l.get(k).getValue().sum();
                s.sendMessage(Paquets.t(String.format(java.util.Locale.ROOT, "§f %6.1f/s §7%s §8(%d)", n / (double) secondes, l.get(k).getKey(), n)));
            }
        }, secondes * 20L);
    }

    /** Affiche en direct les paquets d'un joueur dont le nom contient le filtre. */
    public void journal(CommandSender s, Player cible, String filtre, int secondes) {
        Journal j = new Journal(s, cible, filtre == null ? "" : filtre.toLowerCase(java.util.Locale.ROOT));
        journaux.add(j);
        s.sendMessage(Paquets.t("§7Journal des paquets de §f" + cible.getName() + "§7" + (j.filtre.isEmpty() ? "" : " contenant « " + filtre + " »")
            + " pendant " + secondes + " s (200 lignes au plus, aussi dans la console)"));
        org.bukkit.scheduler.BukkitTask tache = Bukkit.getScheduler().runTaskTimer(plugin, j::vider, 1L, 1L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            journaux.remove(j);
            tache.cancel();
            j.vider();
            s.sendMessage(Paquets.t("§7Fin du journal : " + j.lignes.get() + " paquet(s) affiché(s)."));
        }, secondes * 20L);
    }

    private final class Journal {
        final CommandSender lecteur;
        final Player cible;
        final String filtre;
        final AtomicInteger lignes = new AtomicInteger();
        final ConcurrentLinkedQueue<String> file = new ConcurrentLinkedQueue<>();

        Journal(CommandSender lecteur, Player cible, String filtre) {
            this.lecteur = lecteur;
            this.cible = cible;
            this.filtre = filtre;
        }

        void noter(Player j, Sens sens, String nom, Object msg) {
            if (!j.getUniqueId().equals(cible.getUniqueId())) return;
            if (!filtre.isEmpty() && !nom.toLowerCase(java.util.Locale.ROOT).contains(filtre)) return;
            // le journal lui-même s'affiche avec des paquets de chat : ne pas les journaliser chez le lecteur
            if (lecteur instanceof Player l && l.getUniqueId().equals(j.getUniqueId()) && nom.contains("Chat")) return;
            if (lignes.incrementAndGet() > 200) return;
            String detail;
            try {
                detail = Reflexion.champs(msg).toString();
            } catch (RuntimeException e) {
                detail = "";
            }
            if (detail.length() > 240) detail = detail.substring(0, 240) + "…";
            file.add((sens == Sens.SORTANT ? "→ " : "← ") + nom + " " + detail);
        }

        void vider() {
            String l;
            while ((l = file.poll()) != null) {
                plugin.getLogger().info("[paquets " + cible.getName() + "] " + l);
                if (!(lecteur instanceof org.bukkit.command.ConsoleCommandSender)) lecteur.sendMessage(Paquets.t("§8" + l));
            }
        }
    }

    // ------------------------------------------------------------------ connexion

    /** Paquet à transmettre sans passer par les écoutes. */
    private record Direct(Object paquet) {}

    /** Gestionnaire Netty placé dans la connexion de chaque joueur, juste avant celui du jeu. */
    final class Intercepteur extends ChannelDuplexHandler {
        final Channel canal;
        volatile Player joueur;
        volatile String nomConnexion;

        Intercepteur(Channel canal) {
            this.canal = canal;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            Object r = actif ? traiter(this, Sens.ENTRANT, msg) : msg;
            if (r != null) super.channelRead(ctx, r);
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            Object r = actif ? traiter(this, Sens.SORTANT, msg) : msg instanceof Direct d ? d.paquet : msg;
            if (r == null) {
                promise.trySuccess();
                return;
            }
            super.write(ctx, r, promise);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            intercepteurs.remove(this);
            super.channelInactive(ctx);
        }

        void retirer() {
            try {
                if (canal.isOpen()) canal.eventLoop().execute(() -> {
                    if (canal.pipeline().get(NOM_GESTIONNAIRE) == this) canal.pipeline().remove(this);
                });
            } catch (Throwable ignore) { }
        }
    }
}
