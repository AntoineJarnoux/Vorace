package fr.vorace;

import fr.vorace.envoi.Envoi;
import fr.vorace.glyphes.Glyphes;
import fr.vorace.objets.DefinitionObjet;
import fr.vorace.objets.MenuObjets;
import fr.vorace.pack.Constructeur;
import fr.vorace.pack.Journal;
import fr.vorace.pack.Recherche;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

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
        if (c.getName().equalsIgnoreCase("emojis")) {
            emojis(s);
            return true;
        }
        String sous = a.length == 0 ? "aide" : a[0].toLowerCase(Locale.ROOT);
        if (!sous.equals("pack") && !sous.equals("emojis") && !s.hasPermission("vorace.admin")) {
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
            case "give", "donner" -> donner(s, a);
            case "objets", "items" -> {
                if (!(s instanceof Player p)) { s.sendMessage(Messages.brut("<red>Commande réservée aux joueurs.")); return true; }
                int page = 0;
                try { if (a.length > 1) page = Integer.parseInt(a[1]) - 1; } catch (NumberFormatException ignore) { }
                new MenuObjets(plugin, page).ouvrir(p);
            }
            case "maj" -> {
                int n = 0;
                for (Player p : Bukkit.getOnlinePlayers()) n += plugin.ecouteurObjets().mettreAJour(p);
                s.sendMessage(Messages.brut("<green>" + n + " objet(s) mis à jour dans les inventaires."));
            }
            case "meuble", "meubles" -> meuble(s, a);
            case "bloc", "blocs" -> bloc(s, a);
            case "glyphe", "glyphes" -> glyphe(s, a);
            case "emojis", "emoji" -> emojis(s);
            case "info" -> info(s);
            case "sources" -> sources(s);
            default -> {
                s.sendMessage(Messages.brut("<white>Vorace " + plugin.getPluginMeta().getVersion() + "</white> <gray>- commandes :"));
                s.sendMessage(Messages.brut("<white>/vorace reload</white> <gray>recharge la config et reconstruit le pack"));
                s.sendMessage(Messages.brut("<white>/vorace envoyer [joueur|tous]</white> <gray>envoie le pack"));
                s.sendMessage(Messages.brut("<white>/vorace info</white> <gray>état du pack et de l'hébergement"));
                s.sendMessage(Messages.brut("<white>/vorace sources</white> <gray>packs trouvés et fusionnés"));
                s.sendMessage(Messages.brut("<white>/vorace give <joueur> <objet> [quantité]</white> <gray>donne un objet"));
                s.sendMessage(Messages.brut("<white>/vorace objets</white> <gray>menu de tous les objets"));
                s.sendMessage(Messages.brut("<white>/vorace maj</white> <gray>met à jour les objets dans les inventaires"));
                s.sendMessage(Messages.brut("<white>/vorace meuble [liste|poser|info|retirer|rafraichir]</white> <gray>gestion des meubles"));
                s.sendMessage(Messages.brut("<white>/vorace bloc [liste|poser|casser|info]</white> <gray>blocs personnalisés"));
                s.sendMessage(Messages.brut("<white>/vorace glyphe [liste|menu <fond>]</white> <gray>glyphes et fonds de menus"));
                s.sendMessage(Messages.brut("<white>/emojis</white> <gray>(joueurs) liste des emojis du chat"));
                s.sendMessage(Messages.brut("<white>/vorace pack</white> <gray>(joueurs) recevoir à nouveau le pack"));
            }
        }
        return true;
    }

    private void donner(CommandSender s, String[] a) {
        if (a.length < 3) { s.sendMessage(Messages.brut("<red>Utilisation : /vorace give <joueur> <objet> [quantité]")); return; }
        Player p = Bukkit.getPlayerExact(a[1]);
        if (p == null) { s.sendMessage(Messages.brut("<red>Joueur introuvable : " + Messages.echapper(a[1]))); return; }
        String id = a[2].toLowerCase(Locale.ROOT).replace("vorace:", "");
        DefinitionObjet d = plugin.objets().definition(id);
        if (d == null) { s.sendMessage(Messages.brut("<red>Objet inconnu : " + Messages.echapper(id))); return; }
        int n = 1;
        try { if (a.length > 3) n = Math.max(1, Math.min(6400, Integer.parseInt(a[3]))); } catch (NumberFormatException ignore) { }
        int reste = n;
        while (reste > 0) {
            int lot = Math.min(64, reste);
            MenuObjets.donner(p, plugin.objets().fabriquer(d, lot));
            reste -= lot;
        }
        s.sendMessage(Messages.brut("<green>" + n + " × " + Messages.echapper(id) + " donné(s) à " + p.getName() + "."));
    }

    private void meuble(CommandSender s, String[] a) {
        String act = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "liste";
        switch (act) {
            case "rafraichir" -> s.sendMessage(Messages.brut("<green>" + plugin.meubles().rafraichirTout() + " meuble(s) mis à jour."));
            case "poser" -> {
                // pose sans passer par les protections (zones où les joueurs ne peuvent pas construire, comme un hub)
                if (!(s instanceof Player p)) { s.sendMessage(Messages.brut("<red>Commande réservée aux joueurs.")); return; }
                if (a.length < 3) { s.sendMessage(Messages.brut("<red>Utilisation : /vorace meuble poser <meuble>  (regarde le bloc où le poser)")); return; }
                DefinitionObjet d = plugin.objets().definition(a[2].toLowerCase(Locale.ROOT).replace("vorace:", ""));
                if (d == null || d.meuble() == null) { s.sendMessage(Messages.brut("<red>Meuble inconnu : " + Messages.echapper(a[2]))); return; }
                Block cible = p.getTargetBlockExact(6);
                BlockFace face = p.getTargetBlockFace(6);
                if (cible == null || face == null) { s.sendMessage(Messages.brut("<red>Regarde un bloc à moins de 6 blocs.")); return; }
                if (plugin.meubles().poser(p, null, d, cible, face, EquipmentSlot.HAND, false)) {
                    s.sendMessage(Messages.brut("<green>Meuble " + Messages.echapper(d.id()) + " posé. <gray>Pour le retirer : /vorace meuble retirer"));
                }
            }
            case "info", "retirer", "supprimer" -> {
                if (!(s instanceof Player p)) { s.sendMessage(Messages.brut("<red>Commande réservée aux joueurs.")); return; }
                double rayon = 5;
                try { if (a.length > 2) rayon = Math.max(1, Math.min(32, Double.parseDouble(a[2]))); } catch (NumberFormatException ignore) { }
                ItemDisplay m = plugin.meubles().plusProche(p.getLocation(), rayon);
                if (m == null) { s.sendMessage(Messages.brut("<yellow>Aucun meuble à moins de " + (int) rayon + " blocs.")); return; }
                String id = plugin.meubles().id(m);
                if (act.equals("info")) {
                    Block o = plugin.meubles().origine(m);
                    s.sendMessage(Messages.brut("<white>" + Messages.echapper(id) + "</white> <gray>en <white>" + (o == null ? "?" : o.getX() + " " + o.getY() + " " + o.getZ())
                        + "</white>" + (plugin.objets().definition(id) == null ? " <red>(objet supprimé de la config)" : "")));
                } else {
                    plugin.meubles().casser(null, m, false);
                    s.sendMessage(Messages.brut("<green>Meuble " + Messages.echapper(id) + " retiré."));
                }
            }
            default -> {
                List<String> ids = new ArrayList<>();
                for (DefinitionObjet d : plugin.objets().toutes()) if (d.meuble() != null) ids.add(d.id());
                s.sendMessage(Messages.brut("<gray>" + ids.size() + " meuble(s) : <white>" + Messages.echapper(String.join(", ", ids))));
                s.sendMessage(Messages.brut("<gray>/vorace meuble poser <meuble> : pose là où tu regardes, même en zone protégée"));
                s.sendMessage(Messages.brut("<gray>/vorace meuble info|retirer [rayon] : le meuble le plus proche ; /vorace meuble rafraichir"));
            }
        }
    }

    private void bloc(CommandSender s, String[] a) {
        String act = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "liste";
        switch (act) {
            case "poser" -> {
                // pose sans passer par les protections (zones où les joueurs ne peuvent pas construire)
                if (!(s instanceof Player p)) { s.sendMessage(Messages.brut("<red>Commande réservée aux joueurs.")); return; }
                if (a.length < 3) { s.sendMessage(Messages.brut("<red>Utilisation : /vorace bloc poser <bloc>  (regarde où le poser)")); return; }
                DefinitionObjet d = plugin.objets().definition(a[2].toLowerCase(Locale.ROOT).replace("vorace:", ""));
                if (d == null || d.bloc() == null) { s.sendMessage(Messages.brut("<red>Bloc inconnu : " + Messages.echapper(a[2]))); return; }
                Block cible = p.getTargetBlockExact(6);
                BlockFace face = p.getTargetBlockFace(6);
                if (cible == null || face == null) { s.sendMessage(Messages.brut("<red>Regarde un bloc à moins de 6 blocs.")); return; }
                Block ou = cible.isReplaceable() ? cible : cible.getRelative(face);
                if (!ou.isReplaceable()) { s.sendMessage(Messages.brut("<red>Pas de place ici.")); return; }
                plugin.blocs().placer(ou, d.id());
                s.sendMessage(Messages.brut("<green>Bloc " + Messages.echapper(d.id()) + " posé."));
            }
            case "casser" -> {
                // casse avec l'outil en main (butin, Fortune, Toucher de soie), même en zone protégée
                if (!(s instanceof Player p)) { s.sendMessage(Messages.brut("<red>Commande réservée aux joueurs.")); return; }
                Block cible = p.getTargetBlockExact(6);
                DefinitionObjet d = plugin.blocs().definition(cible);
                if (d == null) { s.sendMessage(Messages.brut("<yellow>Regarde un bloc Vorace à moins de 6 blocs.")); return; }
                plugin.blocs().casserPar(p, cible, d, false);
                s.sendMessage(Messages.brut("<green>Bloc " + Messages.echapper(d.id()) + " cassé avec l'objet en main."));
            }
            case "info" -> {
                if (!(s instanceof Player p)) { s.sendMessage(Messages.brut("<red>Commande réservée aux joueurs.")); return; }
                Block cible = p.getTargetBlockExact(6);
                String id = plugin.blocs().id(cible);
                s.sendMessage(Messages.brut(id == null ? "<yellow>Ce n'est pas un bloc Vorace." : "<gray>Bloc Vorace : <white>" + Messages.echapper(id)
                    + "</white> <gray>(état " + Messages.echapper(cible.getBlockData().getAsString()) + ")"));
            }
            default -> {
                List<String> ids = new ArrayList<>();
                for (DefinitionObjet d : plugin.objets().toutes()) if (d.bloc() != null) ids.add(d.id());
                s.sendMessage(Messages.brut("<gray>" + ids.size() + " bloc(s) : <white>" + Messages.echapper(String.join(", ", ids))));
                s.sendMessage(Messages.brut("<gray>/vorace bloc poser <bloc> / casser : là où tu regardes, même en zone protégée ; /vorace bloc info"));
            }
        }
    }

    private void emojis(CommandSender s) {
        Player p = s instanceof Player j ? j : null;
        List<Glyphes.Glyphe> liste = new ArrayList<>();
        for (Glyphes.Glyphe g : plugin.glyphes().tous()) if (g.def().liste() && Glyphes.autorise(p, g)) liste.add(g);
        if (liste.isEmpty()) { s.sendMessage(Messages.brut("<gray>Aucun emoji sur ce serveur.")); return; }
        s.sendMessage(Messages.brut("<gray>Emojis : écris le raccourci dans le chat (touche Tab pour les suggestions). Clique pour l'écrire :"));
        Component ligne = Component.empty();
        int n = 0;
        for (Glyphes.Glyphe g : liste) {
            String r = g.def().raccourcis().isEmpty() ? "" : g.def().raccourcis().get(0);
            Component e = Glyphes.composant(g)
                .hoverEvent(HoverEvent.showText(Component.text(String.join("  ", g.def().raccourcis()), NamedTextColor.YELLOW)))
                .clickEvent(ClickEvent.suggestCommand(r));
            ligne = ligne.append(e).append(Component.text("  "));
            if (++n % 12 == 0) { s.sendMessage(ligne); ligne = Component.empty(); }
        }
        if (n % 12 != 0) s.sendMessage(ligne);
    }

    private void glyphe(CommandSender s, String[] a) {
        String act = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "liste";
        if (act.equals("menu")) {
            if (!(s instanceof Player p)) { s.sendMessage(Messages.brut("<red>Commande réservée aux joueurs.")); return; }
            if (a.length < 3) { s.sendMessage(Messages.brut("<red>Utilisation : /vorace glyphe menu <fond>")); return; }
            Glyphes.Glyphe g = plugin.glyphes().glyphe(a[2]);
            if (g == null) { s.sendMessage(Messages.brut("<red>Glyphe inconnu : " + Messages.echapper(a[2]))); return; }
            int lignes = a.length > 3 ? Math.max(1, Math.min(6, parseOu(a[3], g.def().lignes()))) : g.def().lignes();
            p.openInventory(Bukkit.createInventory(null, lignes * 9,
                plugin.glyphes().titreMenu(g.def().id(), Component.text("Aperçu : " + g.def().id()))));
            return;
        }
        List<Glyphes.Glyphe> tous = new ArrayList<>(plugin.glyphes().tous());
        s.sendMessage(Messages.brut("<gray>" + tous.size() + " glyphe(s) :"));
        for (Glyphes.Glyphe g : tous) {
            Component l = Component.text(" ").append(Glyphes.composant(g)).append(Component.text(" " + g.def().id(), NamedTextColor.WHITE))
                .append(Component.text(g.def().menu() ? "  fond de menu (" + g.hauteur() + " px de haut) : /vorace glyphe menu " + g.def().id()
                    : "  " + String.join(" ", g.def().raccourcis()) + (g.def().permission() != null ? "  [" + g.def().permission() + "]" : ""), NamedTextColor.GRAY))
                .append(Component.text("  U+" + Integer.toHexString(g.code()).toUpperCase(Locale.ROOT), NamedTextColor.DARK_GRAY))
                .clickEvent(ClickEvent.copyToClipboard(g.texte()))
                .hoverEvent(HoverEvent.showText(Component.text("Clic : copier le caractère (à coller dans n'importe quel texte)")));
            s.sendMessage(l);
        }
        s.sendMessage(Messages.brut("<gray>Dans un nom d'objet ou un message : <white><glyphe:id></white>, décalage : <white><decalage:-8></white>"));
    }

    private static int parseOu(String t, int defaut) {
        try { return Integer.parseInt(t.trim()); } catch (NumberFormatException e) { return defaut; }
    }

    private void info(CommandSender s) {
        Constructeur.Resultat r = plugin.packs().actuel();
        s.sendMessage(Messages.brut("<white>Vorace " + plugin.getPluginMeta().getVersion() + " <gray>- " + plugin.objets().toutes().size() + " objet(s)"));
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
        if (c.getName().equalsIgnoreCase("emojis")) return r;
        if (a.length == 1) {
            for (String o : s.hasPermission("vorace.admin") ? List.of("reload", "give", "objets", "meuble", "bloc", "glyphe", "emojis", "envoyer", "maj", "info", "sources", "pack") : List.of("pack", "emojis")) {
                if (o.startsWith(a[0].toLowerCase(Locale.ROOT))) r.add(o);
            }
        } else if (a.length == 2 && a[0].equalsIgnoreCase("give") && s.hasPermission("vorace.admin")) {
            Bukkit.getOnlinePlayers().forEach(p -> r.add(p.getName()));
            r.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(a[1].toLowerCase(Locale.ROOT)));
        } else if (a.length == 3 && a[0].equalsIgnoreCase("give") && s.hasPermission("vorace.admin")) {
            for (DefinitionObjet d : plugin.objets().toutes()) if (d.id().startsWith(a[2].toLowerCase(Locale.ROOT))) r.add(d.id());
        } else if (a.length == 2 && a[0].equalsIgnoreCase("meuble") && s.hasPermission("vorace.admin")) {
            for (String o : List.of("liste", "poser", "info", "retirer", "rafraichir")) if (o.startsWith(a[1].toLowerCase(Locale.ROOT))) r.add(o);
        } else if (a.length == 3 && a[0].equalsIgnoreCase("meuble") && a[1].equalsIgnoreCase("poser") && s.hasPermission("vorace.admin")) {
            for (DefinitionObjet d : plugin.objets().toutes()) if (d.meuble() != null && d.id().startsWith(a[2].toLowerCase(Locale.ROOT))) r.add(d.id());
        } else if (a.length == 2 && a[0].equalsIgnoreCase("bloc") && s.hasPermission("vorace.admin")) {
            for (String o : List.of("liste", "poser", "casser", "info")) if (o.startsWith(a[1].toLowerCase(Locale.ROOT))) r.add(o);
        } else if (a.length == 3 && a[0].equalsIgnoreCase("bloc") && a[1].equalsIgnoreCase("poser") && s.hasPermission("vorace.admin")) {
            for (DefinitionObjet d : plugin.objets().toutes()) if (d.bloc() != null && d.id().startsWith(a[2].toLowerCase(Locale.ROOT))) r.add(d.id());
        } else if (a.length == 2 && a[0].equalsIgnoreCase("glyphe") && s.hasPermission("vorace.admin")) {
            for (String o : List.of("liste", "menu")) if (o.startsWith(a[1].toLowerCase(Locale.ROOT))) r.add(o);
        } else if (a.length == 3 && a[0].equalsIgnoreCase("glyphe") && a[1].equalsIgnoreCase("menu") && s.hasPermission("vorace.admin")) {
            for (Glyphes.Glyphe g : plugin.glyphes().tous()) if (g.def().menu() && g.def().id().startsWith(a[2].toLowerCase(Locale.ROOT))) r.add(g.def().id());
        } else if (a.length == 2 && a[0].equalsIgnoreCase("envoyer") && s.hasPermission("vorace.admin")) {
            r.add("tous");
            Bukkit.getOnlinePlayers().forEach(p -> r.add(p.getName()));
            r.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(a[1].toLowerCase(Locale.ROOT)));
        }
        return r;
    }
}
