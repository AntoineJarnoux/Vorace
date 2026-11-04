package fr.vorace;

import fr.vorace.envoi.Envoi;
import fr.vorace.conversion.Conversion;
import fr.vorace.glyphes.Glyphes;
import fr.vorace.modeles.ModeleBB;
import fr.vorace.modeles.Modeles;
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
        // /vorace tout seul : les menus (tout se fait en cliquant), le texte d'aide reste avec /vorace aide
        if ((a.length == 0 || a[0].equalsIgnoreCase("menu")) && s instanceof Player joueur) {
            if (s.hasPermission("vorace.admin")) plugin.menus().accueil().ouvrir(joueur);
            else plugin.menus().joueur().ouvrir(joueur);
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
                plugin.menus().objets(page).ouvrir(p);
            }
            case "maj" -> {
                int n = 0;
                for (Player p : Bukkit.getOnlinePlayers()) n += plugin.ecouteurObjets().mettreAJour(p);
                s.sendMessage(Messages.brut("<green>" + n + " objet(s) mis à jour dans les inventaires."));
            }
            case "meuble", "meubles" -> meuble(s, a);
            case "bloc", "blocs" -> bloc(s, a);
            case "glyphe", "glyphes" -> glyphe(s, a);
            case "modele", "modeles", "modèle" -> modele(s, a);
            case "mob", "mobs" -> mob(s, a);
            case "paquets", "paquet" -> paquets(s, a);
            case "convertir", "convert", "conversion" -> convertir(s, a);
            case "emojis", "emoji" -> emojis(s);
            case "info" -> info(s);
            case "sources" -> sources(s);
            default -> {
                s.sendMessage(Messages.brut("<white>Vorace " + plugin.getPluginMeta().getVersion() + "</white> <gray>- tape <white>/vorace</white> seul pour tout faire avec des menus. Commandes :"));
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
                s.sendMessage(Messages.brut("<white>/vorace modele [liste|poser|anim|stop|taille|info|prive|public|sur|invoquer|retirer]</white> <gray>modèles animés"));
                s.sendMessage(Messages.brut("<white>/vorace mob [liste|invoquer|info|apparitions|retirer|recharger]</white> <gray>mobs avec cerveau, troupeaux et apparitions"));
                s.sendMessage(Messages.brut("<white>/vorace paquets [stats|journal|ecoutes] [joueur]</white> <gray>paquets du jeu"));
                s.sendMessage(Messages.brut("<white>/vorace convertir [nexo|oraxen|itemsadder|modelengine|tout]</white> <gray>reprend le contenu de ces plugins"));
                s.sendMessage(Messages.brut("<white>/emojis</white> <gray>(joueurs) liste des emojis du chat"));
                s.sendMessage(Messages.brut("<white>/vorace pack</white> <gray>(joueurs) recevoir à nouveau le pack"));
            }
        }
        return true;
    }

    private volatile boolean conversionEnCours;

    /** /vorace convertir [nexo|oraxen|itemsadder|modelengine|tout] */
    private void convertir(CommandSender s, String[] a) {
        java.nio.file.Path plugins = plugin.getDataFolder().toPath().getParent();
        java.nio.file.Path vorace = plugin.getDataFolder().toPath();
        List<Conversion.Source> presentes = new ArrayList<>();
        for (Conversion.Source x : Conversion.Source.values()) if (Conversion.present(plugins, x)) presentes.add(x);
        if (a.length < 2) {
            s.sendMessage(Messages.brut("<gray>Reprend les objets, meubles, blocs, glyphes et modèles d'autres plugins (leurs fichiers de config, "
                + "pas leur code). Rien n'est modifié chez eux : tu peux les garder le temps de vérifier."));
            for (Conversion.Source x : Conversion.Source.values()) {
                boolean ok = presentes.contains(x);
                s.sendMessage(Messages.brut(ok
                    ? "<green>✔</green> <click:run_command:'/vorace convertir " + x.id + "'><hover:show_text:'Clique pour convertir'><white>" + x.nom + "</white> <gray>trouvé : <yellow>[convertir]</yellow></hover></click>"
                    : "<dark_gray>✘ " + x.nom + " (dossier absent)"));
            }
            if (presentes.size() > 1) s.sendMessage(Messages.brut("<click:run_command:'/vorace convertir tout'><yellow>[tout convertir]</yellow></click>"));
            return;
        }
        List<Conversion.Source> liste = new ArrayList<>();
        if (a[1].equalsIgnoreCase("tout") || a[1].equalsIgnoreCase("all")) {
            liste.addAll(presentes);
        } else {
            Conversion.Source x = Conversion.Source.lire(a[1]);
            if (x == null) { s.sendMessage(Messages.brut("<red>Inconnu : " + Messages.echapper(a[1]) + " <gray>(nexo, oraxen, itemsadder, modelengine ou tout)")); return; }
            if (!presentes.contains(x)) { s.sendMessage(Messages.brut("<red>Rien à convertir : dossier de " + x.nom + " introuvable dans plugins/.")); return; }
            liste.add(x);
        }
        if (liste.isEmpty()) { s.sendMessage(Messages.brut("<red>Aucun plugin à convertir trouvé dans plugins/.")); return; }
        if (conversionEnCours) { s.sendMessage(Messages.brut("<yellow>Une conversion est déjà en cours.")); return; }
        conversionEnCours = true;
        s.sendMessage(Messages.brut("<gray>Conversion en cours..."));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Object> resultats = new ArrayList<>();
            for (Conversion.Source x : liste) {
                try {
                    resultats.add(Conversion.convertir(plugins, vorace, x));
                } catch (Throwable t) {
                    plugin.getLogger().warning("Conversion de " + x.nom + " : " + t);
                    resultats.add(x.nom + " : " + t.getMessage());
                }
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                conversionEnCours = false;
                for (Object o : resultats) {
                    if (o instanceof Conversion.Resultat r) {
                        s.sendMessage(Messages.brut("<green>✔ " + r.source().nom + "</green> <gray>: " + Messages.echapper(r.resume())));
                        for (String m : r.remarques()) s.sendMessage(Messages.brut("<dark_gray>- <gray>" + Messages.echapper(m)));
                        if (r.nbRemarques() > r.remarques().size()) s.sendMessage(Messages.brut("<dark_gray>... et " + (r.nbRemarques() - r.remarques().size()) + " autre(s)"));
                        s.sendMessage(Messages.brut("<gray>Rapport complet : <white>plugins/Vorace/" + Messages.echapper(vorace.relativize(r.rapport()).toString().replace('\\', '/'))));
                    } else {
                        s.sendMessage(Messages.brut("<red>✘ " + Messages.echapper(String.valueOf(o))));
                    }
                }
                s.sendMessage(Messages.brut("<gray>Rechargement de Vorace..."));
                plugin.rechargerConfig();
                for (Player p : Bukkit.getOnlinePlayers()) plugin.ecouteurObjets().mettreAJour(p);
                plugin.reconstruire(res -> {
                    // les nouveaux objets n'ont pas d'image tant que les joueurs n'ont pas le nouveau pack : on l'envoie tout de suite
                    Bukkit.getOnlinePlayers().forEach(plugin.envoi()::envoyer);
                    s.sendMessage(Messages.brut("<green>Pack prêt et envoyé aux " + Bukkit.getOnlinePlayers().size()
                        + " joueur(s) connecté(s).</green> <gray>Les objets sont dans <white>/vorace objets</white>."));
                });
            });
        });
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
        tous.removeIf(Glyphes::integre);
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

    private void modele(CommandSender s, String[] a) {
        Modeles m = plugin.modeles();
        if (m == null) { s.sendMessage(Messages.brut("<red>Les modèles animés n'ont pas pu démarrer (voir la console).")); return; }
        String act = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "liste";
        Player p = s instanceof Player j ? j : null;
        // depuis la console : autour du point d'apparition du monde principal (essais automatiques, serveurs sans joueur)
        org.bukkit.Location ici = p != null ? p.getLocation() : org.bukkit.Bukkit.getWorlds().get(0).getSpawnLocation();
        if (p == null && List.of("sur", "invoquer").contains(act)) { s.sendMessage(Messages.brut("<red>Commande réservée aux joueurs.")); return; }
        switch (act) {
            case "poser" -> {
                if (a.length < 3) { s.sendMessage(Messages.brut("<red>Utilisation : /vorace modele poser <modèle> [animation]")); return; }
                ModeleBB def = m.modele(a[2]);
                if (def == null) { s.sendMessage(Messages.brut("<red>Modèle inconnu : " + Messages.echapper(a[2]))); return; }
                org.bukkit.Location ou;
                float yaw = 0;
                if (p != null) {
                    Block cible = p.getTargetBlockExact(8);
                    BlockFace face = p.getTargetBlockFace(8);
                    if (cible == null || face == null) { s.sendMessage(Messages.brut("<red>Regarde un bloc à moins de 8 blocs.")); return; }
                    ou = cible.getRelative(face).getLocation().add(0.5, 0, 0.5);
                    if (face != BlockFace.UP) ou = cible.getLocation().add(0.5, 1, 0.5);
                    yaw = p.getLocation().getYaw() + 180f;
                } else {
                    ou = new org.bukkit.Location(ici.getWorld(), ici.getBlockX() + 0.5, ici.getBlockY(), ici.getBlockZ() + 2.5);
                }
                Modeles.Instance i = m.poser(def.id(), ou, yaw, a.length > 3 ? a[3] : null);
                s.sendMessage(Messages.brut(i == null ? "<red>Impossible de poser ce modèle." : "<green>Modèle " + Messages.echapper(def.id()) + " posé. <gray>Animations : <white>"
                    + Messages.echapper(String.join(", ", def.animations().keySet()))));
            }
            case "anim", "animation", "jouer" -> {
                if (a.length < 3) { s.sendMessage(Messages.brut("<red>Utilisation : /vorace modele anim <animation> [boucle] [dessus]  (sur le modèle le plus proche)")); return; }
                Modeles.Instance i = m.plusProche(ici, 16);
                if (i == null) { s.sendMessage(Messages.brut("<yellow>Aucun modèle à moins de 16 blocs.")); return; }
                boolean boucle = false, dessus = false;
                for (int k = 3; k < a.length; k++) {
                    String o = a[k].toLowerCase(Locale.ROOT);
                    if (o.startsWith("b")) boucle = true;
                    if (o.startsWith("d") || o.startsWith("par")) dessus = true;
                }
                if (!(dessus ? i.jouerParDessus(a[2], boucle) : i.jouer(a[2], boucle))) {
                    s.sendMessage(Messages.brut("<red>Animation inconnue. <gray>Animations de " + Messages.echapper(i.modele().id()) + " : <white>"
                        + Messages.echapper(String.join(", ", i.modele().animations().keySet()))));
                    return;
                }
                s.sendMessage(Messages.brut("<green>Animation " + Messages.echapper(a[2]) + (dessus ? " par-dessus" : "") + (boucle ? " en boucle." : ".")));
            }
            case "info" -> {
                s.sendMessage(Messages.brut("<gray>Animations : <white>" + (m.actif() ? "actives" : "ARRÊTÉES") + "</white>, tic " + m.tics() + ", "
                    + m.instances().size() + " modèle(s) en jeu" + (m.derniereErreur() == null ? "" : ", dernière erreur : <red>" + Messages.echapper(m.derniereErreur()))));
                Modeles.Instance i = m.plusProche(ici, 16);
                if (i == null) { s.sendMessage(Messages.brut("<yellow>Aucun modèle à moins de 16 blocs.")); return; }
                s.sendMessage(Messages.brut("<gray>" + Messages.echapper(i.modele().id()) + " : <white>" + Messages.echapper(i.etat())));
            }
            case "prive", "privé", "public" -> {
                Modeles.Instance i = m.plusProche(ici, 16);
                if (i == null) { s.sendMessage(Messages.brut("<yellow>Aucun modèle à moins de 16 blocs.")); return; }
                if (act.equals("public")) {
                    i.visiblePour(null);
                    s.sendMessage(Messages.brut("<green>Tout le monde voit de nouveau ce modèle."));
                } else {
                    Player cible = a.length > 2 ? Bukkit.getPlayerExact(a[2]) : p;
                    if (cible == null) { s.sendMessage(Messages.brut("<red>Joueur introuvable.")); return; }
                    i.visiblePour(List.of(cible));
                    s.sendMessage(Messages.brut("<green>Seul " + Messages.echapper(cible.getName()) + " voit ce modèle maintenant. <gray>/vorace modele public pour annuler"));
                }
            }
            case "taille" -> {
                Modeles.Instance i = m.plusProche(ici, 16);
                if (i == null) { s.sendMessage(Messages.brut("<yellow>Aucun modèle à moins de 16 blocs.")); return; }
                float t;
                try { t = a.length > 2 ? Float.parseFloat(a[2].replace(',', '.')) : Float.NaN; } catch (NumberFormatException ex) { t = Float.NaN; }
                if (Float.isNaN(t) || t <= 0) { s.sendMessage(Messages.brut("<red>Utilisation : /vorace modele taille <facteur>  (1 = normal, 2 = deux fois plus grand)")); return; }
                i.taille(t);
                s.sendMessage(Messages.brut("<green>Taille du modèle : x" + Messages.echapper(String.format(Locale.ROOT, "%.2f", i.taille())) + "."));
            }
            case "stop" -> {
                Modeles.Instance i = m.plusProche(ici, 16);
                if (i == null) { s.sendMessage(Messages.brut("<yellow>Aucun modèle à moins de 16 blocs.")); return; }
                i.arreterAnimation();
                s.sendMessage(Messages.brut("<green>Retour aux animations automatiques."));
            }
            case "sur" -> {
                if (a.length < 3) { s.sendMessage(Messages.brut("<red>Utilisation : /vorace modele sur <modèle>  (regarde un mob)")); return; }
                ModeleBB def = m.modele(a[2]);
                if (def == null) { s.sendMessage(Messages.brut("<red>Modèle inconnu : " + Messages.echapper(a[2]))); return; }
                org.bukkit.entity.Entity e = p.getTargetEntity(10);
                if (!(e instanceof org.bukkit.entity.LivingEntity le) || e instanceof Player) { s.sendMessage(Messages.brut("<red>Regarde un mob à moins de 10 blocs.")); return; }
                m.attacher(le, def.id());
                s.sendMessage(Messages.brut("<green>Le mob porte maintenant le modèle " + Messages.echapper(def.id()) + "."));
            }
            case "invoquer" -> {
                if (a.length < 3) { s.sendMessage(Messages.brut("<red>Utilisation : /vorace modele invoquer <modèle> [mob]  (golem de fer par défaut)")); return; }
                ModeleBB def = m.modele(a[2]);
                if (def == null) { s.sendMessage(Messages.brut("<red>Modèle inconnu : " + Messages.echapper(a[2]))); return; }
                org.bukkit.entity.EntityType type = org.bukkit.entity.EntityType.IRON_GOLEM;
                if (a.length > 3) {
                    try { type = org.bukkit.entity.EntityType.valueOf(a[3].toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException ex) {
                        s.sendMessage(Messages.brut("<red>Mob inconnu : " + Messages.echapper(a[3]))); return;
                    }
                }
                org.bukkit.entity.Entity e = p.getWorld().spawnEntity(p.getLocation(), type);
                if (!(e instanceof org.bukkit.entity.LivingEntity le)) { e.remove(); s.sendMessage(Messages.brut("<red>Ce n'est pas un mob.")); return; }
                if (le instanceof org.bukkit.entity.Mob mob) mob.getEquipment().clear();
                m.attacher(le, def.id());
                s.sendMessage(Messages.brut("<green>Mob invoqué avec le modèle " + Messages.echapper(def.id()) + "."));
            }
            case "retirer" -> {
                double rayon = 8;
                try { if (a.length > 2) rayon = Double.parseDouble(a[2]); } catch (NumberFormatException ignore) { }
                Modeles.Instance i = m.plusProche(ici, rayon);
                if (i == null) { s.sendMessage(Messages.brut("<yellow>Aucun modèle à moins de " + (int) rayon + " blocs.")); return; }
                m.retirer(i);
                s.sendMessage(Messages.brut("<green>Modèle retiré" + (i.surMob() ? " (le mob est redevenu visible)." : ".")));
            }
            default -> {
                List<String> l = new ArrayList<>();
                for (ModeleBB def : m.tous()) l.add(def.id() + " (" + def.parties().stream().filter(ModeleBB.Partie::visible).count() + " parties, animations : "
                    + (def.animations().isEmpty() ? "aucune" : String.join(", ", def.animations().keySet())) + ")");
                s.sendMessage(Messages.brut("<gray>" + l.size() + " modèle(s) animé(s), " + m.instances().size() + " en jeu :"));
                for (String x : l) s.sendMessage(Messages.brut("<white> " + Messages.echapper(x)));
                s.sendMessage(Messages.brut("<gray>/vorace modele poser <modèle> [animation] | anim <animation> [boucle] [dessus] | stop | taille <facteur> | info | prive [joueur] | public | sur <modèle> | invoquer <modèle> [mob] | retirer"));
            }
        }
    }

    /** /vorace mob [liste|invoquer <mob> [nombre]|info|apparitions|retirer [rayon]|recharger] */
    private void mob(CommandSender s, String[] a) {
        fr.vorace.mobs.Mobs m = plugin.mobs();
        if (m == null) { s.sendMessage(Messages.brut("<red>Les mobs sont désactivés (config : mobs.actif) ou n'ont pas pu démarrer.")); return; }
        String act = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "liste";
        Player p = s instanceof Player j ? j : null;
        org.bukkit.Location ici = p != null ? p.getLocation() : org.bukkit.Bukkit.getWorlds().get(0).getSpawnLocation();
        switch (act) {
            case "invoquer" -> {
                if (a.length < 3) { s.sendMessage(Messages.brut("<red>Utilisation : /vorace mob invoquer <mob> [nombre]")); return; }
                fr.vorace.mobs.DefinitionMob d = m.definition(a[2]);
                if (d == null) { s.sendMessage(Messages.brut("<red>Mob inconnu : " + Messages.echapper(a[2]))); return; }
                int n = 1;
                try { if (a.length > 3) n = Math.max(1, Math.min(32, Integer.parseInt(a[3]))); } catch (NumberFormatException ignore) { }
                org.bukkit.Location ou = p != null ? p.getLocation() : new org.bukkit.Location(ici.getWorld(), ici.getBlockX() + 0.5, ici.getBlockY(), ici.getBlockZ() - 8.5);
                String troupeau = n > 1 && d.cohesion() > 0 ? "invoques-" + Long.toString(System.nanoTime(), 36) : null;
                int faits = 0;
                for (int k = 0; k < n; k++) if (m.invoquer(d.id(), ou.clone().add((k % 3) - 1, 0, (k / 3) - 1), null, troupeau) != null) faits++;
                s.sendMessage(Messages.brut("<green>" + faits + " " + Messages.echapper(d.id()) + " invoqué(s)" + (troupeau != null ? " en troupeau." : ".")));
            }
            case "info" -> {
                s.sendMessage(Messages.brut("<gray>" + m.suivis().size() + " mob(s) de Vorace chargé(s)"
                    + (m.derniereErreur() == null ? "" : ", dernière erreur : <red>" + Messages.echapper(m.derniereErreur()))));
                String filtre = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : null;
                List<fr.vorace.mobs.Mobs.Suivi> proches = filtre == null ? m.proches(ici, 32, 16)
                    : m.proches(ici, 48, 1000).stream().filter(x -> x.definition().id().startsWith(filtre)).limit(48).toList();
                if (proches.isEmpty()) { s.sendMessage(Messages.brut("<yellow>Aucun mob de Vorace à moins de 32 blocs.")); return; }
                for (fr.vorace.mobs.Mobs.Suivi x : proches) s.sendMessage(Messages.brut("<white>" + Messages.echapper(x.etat())));
            }
            case "apparitions" -> {
                for (fr.vorace.mobs.Apparition ap : m.apparitions()) s.sendMessage(Messages.brut("<white> " + Messages.echapper(ap.id()) + " <gray>: "
                    + m.membres(ap.id()) + "/" + ap.nombre() + " " + Messages.echapper(String.join(", ", ap.mobs())) + " à "
                    + (int) ap.x() + " " + (int) ap.y() + " " + (int) ap.z() + " (" + Messages.echapper(ap.monde()) + ")"));
                s.sendMessage(Messages.brut("<gray>" + m.apparitions().size() + " point(s) d'apparition."));
            }
            case "retirer" -> {
                double rayon = 8;
                try { if (a.length > 2) rayon = Double.parseDouble(a[2]); } catch (NumberFormatException ignore) { }
                fr.vorace.mobs.Mobs.Suivi x = m.plusProche(ici, rayon);
                if (x == null) { s.sendMessage(Messages.brut("<yellow>Aucun mob de Vorace à moins de " + (int) rayon + " blocs.")); return; }
                org.bukkit.entity.Entity e = x.entite();
                m.mort(e);
                if (plugin.modeles() != null && plugin.modeles().instance(e) != null) plugin.modeles().retirer(plugin.modeles().instance(e));
                if (e.isValid()) e.remove();
                s.sendMessage(Messages.brut("<green>Mob retiré."));
            }
            case "recharger" -> {
                m.charger();
                s.sendMessage(Messages.brut("<green>Mobs, zones et apparitions relus."));
            }
            default -> {
                s.sendMessage(Messages.brut("<gray>" + m.definitions().size() + " mob(s), " + m.zones().size() + " zone(s), " + m.apparitions().size()
                    + " point(s) d'apparition, " + m.suivis().size() + " mob(s) chargé(s) :"));
                for (fr.vorace.mobs.DefinitionMob d : m.definitions()) s.sendMessage(Messages.brut("<white> " + Messages.echapper(d.id()) + " <gray>("
                    + Messages.echapper(d.type()) + ", " + d.caractere().name().toLowerCase(Locale.ROOT) + (d.modeles().isEmpty() ? "" : ", modèle " + Messages.echapper(String.join("/", d.modeles()))) + ")"));
                s.sendMessage(Messages.brut("<gray>/vorace mob invoquer <mob> [nombre] | info [début du nom] | apparitions | retirer [rayon] | recharger"));
            }
        }
    }

    private void paquets(CommandSender s, String[] a) {
        fr.vorace.paquets.Paquets pq = plugin.paquets();
        if (pq == null) { s.sendMessage(Messages.brut("<red>La couche paquets est désactivée (config : paquets.actif) ou n'a pas pu démarrer.")); return; }
        String act = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
        switch (act) {
            case "stats", "journal" -> {
                Player cible = a.length > 2 ? Bukkit.getPlayerExact(a[2]) : s instanceof Player j ? j : null;
                if (cible == null) { s.sendMessage(Messages.brut("<red>Joueur introuvable. <gray>/vorace paquets " + act + " <joueur>" + (act.equals("journal") ? " [filtre] [secondes]" : " [secondes]"))); return; }
                if (act.equals("stats")) {
                    pq.statistiques(s, cible, Math.max(1, Math.min(60, a.length > 3 ? parseOu(a[3], 5) : 5)));
                } else {
                    String filtre = a.length > 3 ? a[3] : "";
                    pq.journal(s, cible, filtre, Math.max(1, Math.min(120, a.length > 4 ? parseOu(a[4], 10) : 10)));
                }
            }
            case "ecoutes" -> {
                s.sendMessage(Messages.brut("<gray>Couche paquets : <white>" + pq.connexions() + "</white> connexion(s) suivie(s), <white>"
                    + pq.abonnements().size() + "</white> écoute(s) :"));
                for (fr.vorace.paquets.Abonnement ab : pq.abonnements()) {
                    s.sendMessage(Messages.brut("<white> " + Messages.echapper(ab.plugin().getName()) + "</white> <gray>" + ab.sens() + " "
                        + Messages.echapper(ab.noms().isEmpty() ? "(tous)" : String.join(", ", ab.noms()))));
                }
            }
            case "essai" -> new fr.vorace.paquets.EssaiPaquets(plugin, pq).lancer(s);
            default -> {
                s.sendMessage(Messages.brut("<white>/vorace paquets stats [joueur] [secondes]</white> <gray>les paquets les plus fréquents"));
                s.sendMessage(Messages.brut("<white>/vorace paquets journal <joueur> [filtre] [secondes]</white> <gray>les paquets en direct, avec leurs champs"));
                s.sendMessage(Messages.brut("<white>/vorace paquets ecoutes</white> <gray>quels plugins écoutent quels paquets"));
            }
        }
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
            for (String o : s.hasPermission("vorace.admin") ? List.of("menu", "aide", "reload", "give", "objets", "meuble", "bloc", "glyphe", "modele", "mob", "paquets", "convertir", "emojis", "envoyer", "maj", "info", "sources", "pack") : List.of("pack", "emojis")) {
                if (o.startsWith(a[0].toLowerCase(Locale.ROOT))) r.add(o);
            }
        } else if (a.length == 2 && a[0].equalsIgnoreCase("convertir") && s.hasPermission("vorace.admin")) {
            for (String o : List.of("nexo", "oraxen", "itemsadder", "modelengine", "tout")) if (o.startsWith(a[1].toLowerCase(Locale.ROOT))) r.add(o);
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
        } else if (a.length == 2 && a[0].equalsIgnoreCase("modele") && s.hasPermission("vorace.admin")) {
            for (String o : List.of("liste", "poser", "anim", "stop", "taille", "info", "prive", "public", "sur", "invoquer", "retirer")) if (o.startsWith(a[1].toLowerCase(Locale.ROOT))) r.add(o);
        } else if (a.length == 3 && a[0].equalsIgnoreCase("modele") && List.of("poser", "sur", "invoquer").contains(a[1].toLowerCase(Locale.ROOT))
            && s.hasPermission("vorace.admin") && plugin.modeles() != null) {
            for (ModeleBB def : plugin.modeles().tous()) if (def.id().startsWith(a[2].toLowerCase(Locale.ROOT))) r.add(def.id());
        } else if (a.length == 3 && a[0].equalsIgnoreCase("modele") && a[1].equalsIgnoreCase("anim") && s.hasPermission("vorace.admin")
            && plugin.modeles() != null && s instanceof Player p) {
            Modeles.Instance i = plugin.modeles().plusProche(p.getLocation(), 16);
            if (i != null) for (String n : i.modele().animations().keySet()) if (n.startsWith(a[2].toLowerCase(Locale.ROOT))) r.add(n);
        } else if (a.length >= 4 && a[0].equalsIgnoreCase("modele") && a[1].equalsIgnoreCase("anim") && s.hasPermission("vorace.admin")) {
            for (String o : List.of("boucle", "dessus")) if (o.startsWith(a[a.length - 1].toLowerCase(Locale.ROOT))) r.add(o);
        } else if (a.length == 2 && a[0].equalsIgnoreCase("mob") && s.hasPermission("vorace.admin")) {
            for (String o : List.of("liste", "invoquer", "info", "apparitions", "retirer", "recharger")) if (o.startsWith(a[1].toLowerCase(Locale.ROOT))) r.add(o);
        } else if (a.length == 3 && a[0].equalsIgnoreCase("mob") && a[1].equalsIgnoreCase("invoquer") && s.hasPermission("vorace.admin") && plugin.mobs() != null) {
            for (fr.vorace.mobs.DefinitionMob d : plugin.mobs().definitions()) if (d.id().startsWith(a[2].toLowerCase(Locale.ROOT))) r.add(d.id());
        } else if (a.length == 2 && a[0].equalsIgnoreCase("paquets") && s.hasPermission("vorace.admin")) {
            for (String o : List.of("stats", "journal", "ecoutes")) if (o.startsWith(a[1].toLowerCase(Locale.ROOT))) r.add(o);
        } else if (a.length == 3 && a[0].equalsIgnoreCase("paquets") && s.hasPermission("vorace.admin")) {
            Bukkit.getOnlinePlayers().forEach(p -> r.add(p.getName()));
            r.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(a[2].toLowerCase(Locale.ROOT)));
        } else if (a.length == 2 && a[0].equalsIgnoreCase("envoyer") && s.hasPermission("vorace.admin")) {
            r.add("tous");
            Bukkit.getOnlinePlayers().forEach(p -> r.add(p.getName()));
            r.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(a[1].toLowerCase(Locale.ROOT)));
        }
        return r;
    }
}
