package fr.vorace;

import fr.vorace.envoi.Envoi;
import fr.vorace.pack.Constructeur;
import fr.vorace.pack.Journal;
import fr.vorace.pack.Recherche;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /vorace reload | envoyer [joueur|tous] | pack | info | sources */
public final class Commande implements CommandExecutor, TabCompleter {

    private final Vorace plugin;

    public Commande(Vorace plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        String sous = a.length == 0 ? "aide" : a[0].toLowerCase(Locale.ROOT);
        if (!sous.equals("pack") && !s.hasPermission("vorace.admin")) {
            s.sendMessage(Messages.brut("<red>Tu n'as pas la permission."));
            return true;
        }
        switch (sous) {
            case "reload", "recharger" -> {
                s.sendMessage(Messages.brut("<gray>Rechargement de la config et reconstruction du pack..."));
                plugin.rechargerConfig();
                plugin.reconstruire(res -> {
                    Journal j = res.journal();
                    s.sendMessage(Messages.brut("<green>Pack prêt</green> <gray>en " + res.dureeMs() + " ms : <white>" + res.nbFichiers()
                        + "</white> fichiers, <white>" + res.zip().length / 1024 + "</white> Ko, <white>" + j.compter(Journal.Niveau.FUSION)
                        + "</white> fusion(s), <" + (j.compter(Journal.Niveau.CONFLIT) > 0 ? "yellow" : "white") + ">" + j.compter(Journal.Niveau.CONFLIT)
                        + "</" + (j.compter(Journal.Niveau.CONFLIT) > 0 ? "yellow" : "white") + "> conflit(s)."));
                    s.sendMessage(Messages.brut("<gray>Pour l'envoyer à tout le monde : <white>/vorace envoyer tous"));
                });
            }
            case "envoyer", "send" -> {
                String cible = a.length > 1 ? a[1] : (s instanceof Player ? s.getName() : "tous");
                if (cible.equalsIgnoreCase("tous") || cible.equals("*") || cible.equalsIgnoreCase("all")) {
                    Bukkit.getOnlinePlayers().forEach(plugin.envoi()::envoyer);
                    s.sendMessage(Messages.brut("<green>Pack envoyé à " + Bukkit.getOnlinePlayers().size() + " joueur(s)."));
                } else {
                    Player p = Bukkit.getPlayerExact(cible);
                    if (p == null) { s.sendMessage(Messages.brut("<red>Joueur introuvable : " + Messages.echapper(cible))); return true; }
                    plugin.envoi().envoyer(p);
                    s.sendMessage(Messages.brut("<green>Pack envoyé à " + p.getName() + "."));
                }
            }
            case "pack" -> {
                if (!(s instanceof Player p)) { s.sendMessage(Messages.brut("<red>Commande réservée aux joueurs.")); return true; }
                plugin.envoi().envoyer(p);
            }
            case "info" -> info(s);
            case "sources" -> sources(s);
            default -> {
                s.sendMessage(Messages.brut("<white>Vorace " + plugin.getPluginMeta().getVersion() + "</white> <gray>- commandes :"));
                s.sendMessage(Messages.brut("<white>/vorace reload</white> <gray>recharge la config et reconstruit le pack"));
                s.sendMessage(Messages.brut("<white>/vorace envoyer [joueur|tous]</white> <gray>envoie le pack"));
                s.sendMessage(Messages.brut("<white>/vorace info</white> <gray>état du pack et de l'hébergement"));
                s.sendMessage(Messages.brut("<white>/vorace sources</white> <gray>packs trouvés et fusionnés"));
                s.sendMessage(Messages.brut("<white>/vorace pack</white> <gray>(joueurs) recevoir à nouveau le pack"));
            }
        }
        return true;
    }

    private void info(CommandSender s) {
        Constructeur.Resultat r = plugin.packs().actuel();
        s.sendMessage(Messages.brut("<white>Vorace " + plugin.getPluginMeta().getVersion()));
        if (r == null) {
            s.sendMessage(Messages.brut("<yellow>Pack pas encore construit."));
        } else {
            s.sendMessage(Messages.brut("<gray>Pack : <white>" + r.nbFichiers() + "</white> fichiers, <white>" + r.zip().length / 1024
                + "</white> Ko, format <white>" + plugin.packs().format().texte() + "</white>"));
            s.sendMessage(Messages.brut("<gray>SHA-1 : <white>" + r.sha1()));
        }
        s.sendMessage(Messages.brut("<gray>Hébergement : <white>" + Messages.echapper(plugin.hebergeur().description())
            + "</white>" + (plugin.testHebergement() == null ? "" : " <gray>(test : " + plugin.testHebergement() + "<gray>)")));
        if (s instanceof Player p && r != null) {
            String url = plugin.hebergeur().url(p, r.sha1());
            s.sendMessage(Messages.brut("<gray>Ton adresse de téléchargement : <white><click:open_url:'" + url + "'>" + Messages.echapper(url) + "</click>"));
            Envoi.Etat e = plugin.envoi().etat(p);
            s.sendMessage(Messages.brut("<gray>Ton état : <white>" + (e == null ? "rien reçu" : e.name().toLowerCase(Locale.ROOT).replace('_', ' '))));
        }
    }

    private void sources(CommandSender s) {
        List<Recherche.Trouve> l = plugin.packs().chercherSources(plugin.reglages());
        s.sendMessage(Messages.brut("<gray>Sources, de la moins prioritaire à la plus prioritaire :"));
        for (Recherche.Trouve t : l) {
            String nom = Messages.echapper(t.motif().endsWith("/*") && t.present() ? t.motif().replace("*", t.nom()) : t.motif());
            s.sendMessage(Messages.brut(t.present() ? "<green>✔</green> <white>" + nom : "<dark_gray>✘ " + nom + " (absent)"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        List<String> r = new ArrayList<>();
        if (a.length == 1) {
            for (String o : s.hasPermission("vorace.admin") ? List.of("reload", "envoyer", "info", "sources", "pack") : List.of("pack")) {
                if (o.startsWith(a[0].toLowerCase(Locale.ROOT))) r.add(o);
            }
        } else if (a.length == 2 && a[0].equalsIgnoreCase("envoyer") && s.hasPermission("vorace.admin")) {
            r.add("tous");
            Bukkit.getOnlinePlayers().forEach(p -> r.add(p.getName()));
            r.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(a[1].toLowerCase(Locale.ROOT)));
        }
        return r;
    }
}
