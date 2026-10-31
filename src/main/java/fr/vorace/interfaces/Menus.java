package fr.vorace.interfaces;

import fr.vorace.Messages;
import fr.vorace.Vorace;
import fr.vorace.conversion.Conversion;
import fr.vorace.glyphes.Glyphes;
import fr.vorace.modeles.ModeleBB;
import fr.vorace.modeles.Modeles;
import fr.vorace.objets.DefinitionObjet;
import fr.vorace.objets.MenuObjets;
import fr.vorace.pack.Constructeur;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Tous les menus de Vorace : /vorace ouvre l'accueil, et tout ce qui se fait en commandes se fait aussi en cliquant.
 * Les boutons qui agissent « là où tu regardes » ferment le menu et lancent la commande correspondante.
 */
public final class Menus {

    private final Vorace plugin;

    public Menus(Vorace plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ accueil

    public Menu accueil() {
        Menu m = new Menu("<dark_gray>Vorace <gray>" + plugin.getPluginMeta().getVersion(), 5);
        int nObjets = 0, nMeubles = 0, nBlocs = 0;
        DefinitionObjet unObjet = null, unMeuble = null, unBloc = null;
        for (DefinitionObjet d : plugin.objets().toutes()) {
            if (d.meuble() != null) { nMeubles++; if (unMeuble == null) unMeuble = d; }
            else if (d.bloc() != null) { nBlocs++; if (unBloc == null) unBloc = d; }
            else if (unObjet == null) unObjet = d;
            nObjets++;
        }
        int nGlyphes = plugin.glyphes() == null ? 0 : plugin.glyphes().tous().size();
        Modeles mod = plugin.modeles();
        int nModeles = mod == null ? 0 : mod.tous().size();
        Constructeur.Resultat r = plugin.packs().actuel();
        m.bouton(4, Menu.icone("NETHER_STAR", "<gradient:#7b2ff7:#f107a3><bold>Vorace</bold></gradient> <gray>" + plugin.getPluginMeta().getVersion(),
            nObjets + " objet(s) dont " + nMeubles + " meuble(s) et " + nBlocs + " bloc(s)",
            nGlyphes + " glyphe(s), " + nModeles + " modèle(s) animé(s)",
            r == null ? "Pack : pas encore construit" : "Pack : " + r.nbFichiers() + " fichiers, " + r.zip().length / 1024 + " Ko",
            "", "Clique sur un bouton pour commencer."), null);

        m.bouton(10, avec(unObjet, "DIAMOND", "<aqua><bold>Objets",
            "Tous les objets personnalisés.", "", Menu.clic("Clic", "en recevoir un"), Menu.clic("Maj + clic", "une pile"),
            Menu.clic("Clic droit", "le donner à un joueur")), (p, d, maj) -> objets(0).ouvrir(p));
        m.bouton(12, avec(unMeuble, "OAK_STAIRS", "<gold><bold>Meubles",
            "Chaises, tables, lampes, coffres...", "", Menu.clic("Clic sur un meuble", "le poser là où tu regardes"),
            "Retirer ou rafraîchir les meubles posés."), (p, d, maj) -> meubles(0).ouvrir(p));
        m.bouton(14, avec(unBloc, "NOTE_BLOCK", "<green><bold>Blocs",
            "Minerais et blocs de décoration.", "", Menu.clic("Clic sur un bloc", "le poser là où tu regardes"),
            "Casser ou identifier le bloc visé."), (p, d, maj) -> blocs(0).ouvrir(p));
        m.bouton(16, Menu.icone("PAINTING", "<yellow><bold>Glyphes et emojis",
            "Emojis du chat, icônes et fonds de menus.", "", Menu.clic("Clic sur un glyphe", "l'écrire dans le chat")),
            (p, d, maj) -> glyphes(0).ouvrir(p));
        m.bouton(28, Menu.icone("ARMOR_STAND", "<light_purple><bold>Modèles animés",
            "Modèles 3D de Blockbench qui bougent.", "", "Poser, mettre sur un mob, jouer", "une animation, retirer..."),
            (p, d, maj) -> modeles(0).ouvrir(p));
        m.bouton(30, Menu.icone("CHEST", "<white><bold>Pack et joueurs",
            "Le pack de ressources des joueurs.", "", "Reconstruire, envoyer, infos,", "mettre à jour les objets des joueurs."),
            (p, d, maj) -> pack().ouvrir(p));
        m.bouton(32, Menu.icone("HOPPER", "<blue><bold>Conversion",
            "Reprendre le contenu de Nexo, Oraxen,", "ItemsAdder ou ModelEngine."), (p, d, maj) -> conversion().ouvrir(p));
        m.bouton(34, Menu.icone("COMPARATOR", "<red><bold>Outils avancés",
            "Paquets du jeu, état des animations.", "Pour les créateurs de plugins."), (p, d, maj) -> outils().ouvrir(p));
        m.bouton(m.bas() + 4, Menu.icone("BOOK", "<white>Aide",
            "Toutes les commandes en texte.", "", Menu.clic("Clic", "les afficher dans le chat")), (p, d, maj) -> Menu.commande(p, "aide"));
        return m.navigation(null);
    }

    /** Menu des joueurs sans droits d'administration. */
    public Menu joueur() {
        Menu m = new Menu("<dark_gray>Vorace", 3);
        m.bouton(11, Menu.icone("CHEST", "<white><bold>Recevoir le pack",
            "Le pack de ressources du serveur", "(objets, menus, modèles 3D).", "", Menu.clic("Clic", "le recevoir à nouveau")),
            (p, d, maj) -> Menu.commande(p, "pack"));
        m.bouton(15, Menu.icone("PAINTING", "<yellow><bold>Emojis",
            "Les emojis à écrire dans le chat.", "", Menu.clic("Clic", "voir la liste")), (p, d, maj) -> { p.closeInventory(); p.performCommand("emojis"); });
        return m.navigation(null);
    }

    /** Icône d'accueil faite d'un vrai objet Vorace s'il y en a un. */
    private ItemStack avec(DefinitionObjet d, String defaut, String nom, String... lignes) {
        ItemStack base = d == null ? null : plugin.objets().fabriquer(d, 1);
        return base == null ? Menu.icone(defaut, nom, lignes) : Menu.icone(base, nom, lignes);
    }

    /** Ajoute des lignes sous la description d'un objet (sans la remplacer). */
    private static ItemStack lignes(ItemStack it, String... lignes) {
        ItemStack c = it.clone();
        ItemMeta meta = c.getItemMeta();
        if (meta == null) return c;
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Component.text(" "));
        for (String l : lignes) lore.add(Messages.objet(l));
        meta.lore(lore);
        c.setItemMeta(meta);
        return c;
    }

    // ------------------------------------------------------------------ objets, meubles, blocs

    public Menu objets(int page) {
        List<DefinitionObjet> liste = new ArrayList<>(plugin.objets().toutes());
        Menu m = MenuListe.creer("<dark_gray>Objets", liste, page,
            d -> lignes(plugin.objets().fabriquer(d, 1), "<dark_gray>" + d.id(), Menu.clic("Clic", "en recevoir un"), Menu.clic("Maj + clic", "une pile"),
                Menu.clic("Clic droit", "le donner à un joueur")),
            (p, d, droit, maj) -> {
                if (droit) {
                    joueurs("<dark_gray>Donner " + d.id() + " à...", j -> {
                        MenuObjets.donner(j, plugin.objets().fabriquer(d, maj ? 64 : 1));
                        p.sendMessage(Messages.brut("<green>" + (maj ? "64 × " : "") + Messages.echapper(d.id()) + " donné à " + j.getName() + "."));
                    }, () -> objets(page)).ouvrir(p);
                    return;
                }
                MenuObjets.donner(p, plugin.objets().fabriquer(d, maj ? 64 : 1));
                p.sendMessage(Messages.brut("<gray>Reçu : <white>" + (maj ? "64 × " : "") + Messages.echapper(d.id())));
            }, this::objets, this::accueil, "Aucun objet : ajoute-en dans plugins/Vorace/objets/");
        return m.navigation(this::accueil);
    }

    public Menu meubles(int page) {
        List<DefinitionObjet> liste = new ArrayList<>();
        for (DefinitionObjet d : plugin.objets().toutes()) if (d.meuble() != null) liste.add(d);
        Menu m = MenuListe.creer("<dark_gray>Meubles", liste, page,
            d -> lignes(plugin.objets().fabriquer(d, 1), "<dark_gray>" + d.id(), Menu.clic("Clic", "le poser là où tu regardes"),
                "<gray>(même en zone protégée)", Menu.clic("Maj + clic", "recevoir l'objet")),
            (p, d, droit, maj) -> {
                if (maj) { MenuObjets.donner(p, plugin.objets().fabriquer(d, 1)); return; }
                Menu.commande(p, "meuble poser " + d.id());
            }, this::meubles, this::accueil, "Aucun meuble : un objet devient un meuble avec « meuble: »");
        m.bouton(m.bas() + 1, Menu.icone("SPYGLASS", "<white>Meuble le plus proche", "Son nom et sa position.", "", Menu.clic("Clic", "afficher")),
            (p, d, maj) -> Menu.commande(p, "meuble info"));
        m.bouton(m.bas() + 2, Menu.icone("IRON_AXE", "<white>Retirer le meuble le plus proche", "À moins de 5 blocs.", "", Menu.clic("Clic", "retirer")),
            (p, d, maj) -> Menu.commande(p, "meuble retirer"));
        m.bouton(m.bas() + 6, Menu.icone("SUNFLOWER", "<white>Rafraîchir tous les meubles", "Après une modification de leurs fichiers.", "", Menu.clic("Clic", "rafraîchir")),
            (p, d, maj) -> Menu.commande(p, "meuble rafraichir"));
        return m.navigation(this::accueil);
    }

    public Menu blocs(int page) {
        List<DefinitionObjet> liste = new ArrayList<>();
        for (DefinitionObjet d : plugin.objets().toutes()) if (d.bloc() != null) liste.add(d);
        Menu m = MenuListe.creer("<dark_gray>Blocs", liste, page,
            d -> lignes(plugin.objets().fabriquer(d, 1), "<dark_gray>" + d.id(), Menu.clic("Clic", "le poser là où tu regardes"),
                "<gray>(même en zone protégée)", Menu.clic("Maj + clic", "en recevoir une pile")),
            (p, d, droit, maj) -> {
                if (maj) { MenuObjets.donner(p, plugin.objets().fabriquer(d, 64)); return; }
                Menu.commande(p, "bloc poser " + d.id());
            }, this::blocs, this::accueil, "Aucun bloc : un objet devient un bloc avec « bloc: »");
        m.bouton(m.bas() + 1, Menu.icone("SPYGLASS", "<white>Quel est ce bloc ?", "Le bloc que tu regardes.", "", Menu.clic("Clic", "afficher")),
            (p, d, maj) -> Menu.commande(p, "bloc info"));
        m.bouton(m.bas() + 2, Menu.icone("IRON_PICKAXE", "<white>Casser le bloc visé", "Avec l'objet que tu as en main", "(butin, Fortune, Toucher de soie),",
            "même en zone protégée.", "", Menu.clic("Clic", "casser")), (p, d, maj) -> Menu.commande(p, "bloc casser"));
        return m.navigation(this::accueil);
    }

    // ------------------------------------------------------------------ glyphes

    public Menu glyphes(int page) {
        List<Glyphes.Glyphe> liste = plugin.glyphes() == null ? List.of() : new ArrayList<>(plugin.glyphes().tous());
        Menu m = MenuListe.creer("<dark_gray>Glyphes et emojis", liste, page,
            g -> {
                List<String> l = new ArrayList<>();
                if (g.def().menu()) {
                    l.add("Fond de menu (" + g.hauteur() + " pixels de haut)");
                    l.add("");
                    l.add(Menu.clic("Clic", "voir l'aperçu"));
                } else {
                    if (!g.def().raccourcis().isEmpty()) l.add("Raccourcis : <white>" + Messages.echapper(String.join("  ", g.def().raccourcis())));
                    if (g.def().permission() != null) l.add("Permission : <white>" + Messages.echapper(g.def().permission()));
                    l.add("Dans un nom ou un message : <white>" + Messages.echapper("<glyphe:" + g.def().id() + ">"));
                    l.add("");
                    l.add(Menu.clic("Clic", "l'écrire dans le chat"));
                }
                return Menu.icone("PAPER", "<white><glyphe:" + g.def().id() + "> " + g.def().id(), l.toArray(new String[0]));
            },
            (p, g, droit, maj) -> {
                if (g.def().menu()) { Menu.commande(p, "glyphe menu " + g.def().id()); return; }
                p.closeInventory();
                String r = g.def().raccourcis().isEmpty() ? ":" + g.def().id() + ":" : g.def().raccourcis().get(0);
                p.sendMessage(Messages.brut("<gray>Clique ici pour écrire ").append(Glyphes.composant(g)
                    .clickEvent(ClickEvent.suggestCommand(r))
                    .hoverEvent(HoverEvent.showText(Component.text("Écrit " + r + " dans le chat", NamedTextColor.YELLOW))))
                    .append(Component.text("  (" + r + ")", NamedTextColor.DARK_GRAY)));
            }, this::glyphes, this::accueil, "Aucun glyphe : ajoute-en dans plugins/Vorace/glyphes/");
        m.bouton(m.bas() + 1, Menu.icone("WRITABLE_BOOK", "<white>Liste des emojis", "Comme /emojis : tous les emojis", "que les joueurs peuvent écrire.", "", Menu.clic("Clic", "afficher")),
            (p, d, maj) -> { p.closeInventory(); p.performCommand("emojis"); });
        return m.navigation(this::accueil);
    }

    // ------------------------------------------------------------------ modèles animés

    public Menu modeles(int page) {
        Modeles mod = plugin.modeles();
        List<ModeleBB> liste = mod == null ? List.of() : new ArrayList<>(mod.tous());
        Menu m = MenuListe.creer("<dark_gray>Modèles animés", liste, page,
            def -> Menu.icone("ARMOR_STAND", "<light_purple>" + def.id(),
                def.parties().stream().filter(ModeleBB.Partie::visible).count() + " partie(s)",
                "Animations : <white>" + (def.animations().isEmpty() ? "aucune" : Messages.echapper(String.join(", ", def.animations().keySet()))),
                "", Menu.clic("Clic", "poser, mettre sur un mob...")),
            (p, def, droit, maj) -> modele(def).ouvrir(p), this::modeles, this::accueil,
            mod == null ? "Les modèles animés n'ont pas pu démarrer (voir la console)" : "Aucun modèle : mets des .bbmodel dans plugins/Vorace/modeles-animes/");
        m.bouton(m.bas() + 1, Menu.icone("SPYGLASS", "<white>Régler le modèle le plus proche", "Animations, privé/public, retirer.", "", Menu.clic("Clic", "ouvrir")),
            (p, d, maj) -> proche(p).ouvrir(p));
        return m.navigation(this::accueil);
    }

    public Menu modele(ModeleBB def) {
        Menu m = new Menu("<dark_gray>Modèle " + def.id(), 4);
        m.bouton(4, Menu.icone("ARMOR_STAND", "<light_purple>" + def.id(),
            "Animations : <white>" + (def.animations().isEmpty() ? "aucune" : Messages.echapper(String.join(", ", def.animations().keySet())))), null);
        m.bouton(19, Menu.icone("GRASS_BLOCK", "<white><bold>Poser", "Là où tu regardes, tourné vers toi.", "", Menu.clic("Clic", "poser")),
            (p, d, maj) -> Menu.commande(p, "modele poser " + def.id()));
        m.bouton(21, Menu.icone("LEAD", "<white><bold>Sur le mob que tu regardes", "Le mob devient invisible et le modèle", "suit ses mouvements (marche, attaque...).",
            "", Menu.clic("Clic", "appliquer")), (p, d, maj) -> Menu.commande(p, "modele sur " + def.id()));
        m.bouton(23, Menu.icone("ZOMBIE_SPAWN_EGG", "<white><bold>Invoquer un mob avec ce modèle", "Tu choisis le mob.", "", Menu.clic("Clic", "choisir")),
            (p, d, maj) -> invoquer(def).ouvrir(p));
        m.bouton(25, Menu.icone("SPYGLASS", "<white><bold>Régler le modèle le plus proche", "Animations, privé/public, retirer.", "", Menu.clic("Clic", "ouvrir")),
            (p, d, maj) -> proche(p).ouvrir(p));
        return m.navigation(() -> modeles(0));
    }

    private Menu invoquer(ModeleBB def) {
        Menu m = new Menu("<dark_gray>Invoquer " + def.id() + " sur...", 3);
        String[][] mobs = {{"IRON_GOLEM", "Golem de fer"}, {"ZOMBIE", "Zombie"}, {"SKELETON", "Squelette"}, {"VILLAGER", "Villageois"},
            {"PIG", "Cochon"}, {"WOLF", "Loup"}, {"SPIDER", "Araignée"}, {"HUSK", "Zombie du désert"}, {"ALLAY", "Allay"}};
        for (int i = 0; i < mobs.length; i++) {
            String[] mob = mobs[i];
            m.bouton(i, Menu.icone(mob[0] + "_SPAWN_EGG", "<white>" + mob[1], "Le mob garde son comportement", "et porte le modèle.", "", Menu.clic("Clic", "invoquer à tes pieds")),
                (p, d, maj) -> Menu.commande(p, "modele invoquer " + def.id() + " " + mob[0].toLowerCase(Locale.ROOT)));
        }
        return m.navigation(() -> modele(def));
    }

    /** Réglages du modèle animé le plus proche du joueur (à moins de 16 blocs). */
    public Menu proche(Player joueur) {
        Modeles mod = plugin.modeles();
        Modeles.Instance i = mod == null ? null : mod.plusProche(joueur.getLocation(), 16);
        if (i == null) {
            Menu m = new Menu("<dark_gray>Modèle le plus proche", 3);
            m.bouton(13, Menu.icone("BARRIER", "<yellow>Aucun modèle à moins de 16 blocs", "Pose un modèle ou approche-toi."), null);
            return m.navigation(() -> modeles(0));
        }
        Menu m = new Menu("<dark_gray>Modèle : " + i.modele().id(), 6);
        List<String> anims = new ArrayList<>(i.modele().animations().keySet());
        for (int n = 0; n < Math.min(36, anims.size()); n++) {
            String a = anims.get(n);
            boolean enCours = a.equals(i.animation());
            m.bouton(n, Menu.icone(enCours ? "LIME_DYE" : "GRAY_DYE", (enCours ? "<green>▶ " : "<white>") + a,
                    enCours ? "En cours" : "", Menu.clic("Clic", "jouer une fois"), Menu.clic("Maj + clic", "jouer en boucle")),
                (p, d, maj) -> { i.jouer(a, maj); proche(p).ouvrir(p); });
        }
        if (anims.isEmpty()) m.bouton(13, Menu.icone("BARRIER", "<gray>Ce modèle n'a pas d'animation"), null);
        m.bouton(37, Menu.icone("RED_DYE", "<white>Animations automatiques", "Arrête l'animation demandée : le modèle", "reprend repos / marche / attaque tout seul.",
            "", Menu.clic("Clic", "arrêter")), (p, d, maj) -> { i.arreterAnimation(); proche(p).ouvrir(p); });
        m.bouton(39, Menu.icone("ENDER_EYE", i.prive() ? "<green>Privé (activé)" : "<white>Visible par moi seul", "Les autres joueurs ne le voient plus.",
            "", Menu.clic("Clic", "le rendre privé")), (p, d, maj) -> { i.visiblePour(List.of(p)); proche(p).ouvrir(p); });
        m.bouton(40, Menu.icone("ENDER_PEARL", i.prive() ? "<white>Visible par tout le monde" : "<green>Public (activé)", "Tout le monde le voit.",
            "", Menu.clic("Clic", "le rendre public")), (p, d, maj) -> { i.visiblePour(null); proche(p).ouvrir(p); });
        m.bouton(42, Menu.icone("BOOK", "<white>État", "Animation en cours, nombre de parties...", "", Menu.clic("Clic", "afficher dans le chat")),
            (p, d, maj) -> Menu.commande(p, "modele info"));
        m.bouton(43, Menu.icone("LAVA_BUCKET", "<red>Retirer ce modèle", i.surMob() ? "Le mob redevient visible." : "Il disparaît.", "", Menu.clic("Clic", "retirer")),
            (p, d, maj) -> { mod.retirer(i); p.closeInventory(); p.sendMessage(Messages.brut("<green>Modèle retiré.")); });
        return m.navigation(() -> modeles(0));
    }

    // ------------------------------------------------------------------ pack et joueurs

    public Menu pack() {
        Menu m = new Menu("<dark_gray>Pack et joueurs", 4);
        Constructeur.Resultat r = plugin.packs().actuel();
        m.bouton(4, Menu.icone("CHEST", "<white>Pack de ressources",
            r == null ? "Pas encore construit" : r.nbFichiers() + " fichiers, " + r.zip().length / 1024 + " Ko",
            "Format " + plugin.packs().format().texte()), null);
        m.bouton(19, Menu.icone("ANVIL", "<white><bold>Reconstruire", "Relit la config, les objets, les glyphes,", "les modèles, puis refait le pack.", "", Menu.clic("Clic", "reconstruire")),
            (p, d, maj) -> Menu.commande(p, "reload"));
        m.bouton(20, Menu.icone("BELL", "<white><bold>Envoyer à tout le monde", "Après une reconstruction.", "", Menu.clic("Clic", "envoyer")),
            (p, d, maj) -> Menu.commande(p, "envoyer tous"));
        m.bouton(21, Menu.icone("PLAYER_HEAD", "<white><bold>Envoyer à un joueur", "", Menu.clic("Clic", "choisir le joueur")),
            (p, d, maj) -> joueurs("<dark_gray>Envoyer le pack à...", j -> Menu.commande(p, "envoyer " + j.getName()), this::pack).ouvrir(p));
        m.bouton(22, Menu.icone("ENDER_CHEST", "<white><bold>Me renvoyer le pack", "", Menu.clic("Clic", "le recevoir")),
            (p, d, maj) -> Menu.commande(p, "pack"));
        m.bouton(23, Menu.icone("EXPERIENCE_BOTTLE", "<white><bold>Mettre à jour les objets", "Refait les objets Vorace des joueurs connectés", "avec leurs réglages actuels.", "", Menu.clic("Clic", "mettre à jour")),
            (p, d, maj) -> Menu.commande(p, "maj"));
        m.bouton(24, Menu.icone("COMPASS", "<white><bold>Infos", "Pack, hébergement, ton adresse", "de téléchargement.", "", Menu.clic("Clic", "afficher")),
            (p, d, maj) -> Menu.commande(p, "info"));
        m.bouton(25, Menu.icone("BOOKSHELF", "<white><bold>Sources", "Les packs fusionnés, dans l'ordre.", "", Menu.clic("Clic", "afficher")),
            (p, d, maj) -> Menu.commande(p, "sources"));
        return m.navigation(this::accueil);
    }

    /** Choix d'un joueur connecté (par sa tête). */
    public Menu joueurs(String titre, Consumer<Player> action, Supplier<Menu> parent) {
        List<Player> liste = new ArrayList<>(Bukkit.getOnlinePlayers());
        Menu m = MenuListe.creer(titre, liste, 0, j -> tete(j), (p, j, droit, maj) -> {
            if (!j.isOnline()) { p.sendMessage(Messages.brut("<red>" + j.getName() + " n'est plus connecté.")); return; }
            action.accept(j);
        }, page -> joueurs(titre, action, parent), parent, "Personne n'est connecté");
        return m.navigation(parent);
    }

    private static ItemStack tete(Player j) {
        ItemStack t = new ItemStack(Menu.materiau("PLAYER_HEAD"), 1);
        if (t.getItemMeta() instanceof SkullMeta s) {
            s.setOwningPlayer(j);
            t.setItemMeta(s);
        }
        return Menu.icone(t, "<white>" + j.getName(), Menu.clic("Clic", "choisir"));
    }

    // ------------------------------------------------------------------ conversion

    public Menu conversion() {
        Menu m = new Menu("<dark_gray>Conversion", 4);
        Path plugins = plugin.getDataFolder().toPath().getParent();
        m.bouton(4, Menu.icone("HOPPER", "<blue>Conversion vers Vorace",
            "Lit les fichiers de config et les images", "de ces plugins (jamais leur code) et en fait",
            "des objets, meubles, blocs, glyphes et", "modèles Vorace. Rien n'est modifié chez eux.",
            "", "Rapport : plugins/Vorace/conversion/"), null);
        int slot = 19;
        int trouves = 0;
        for (Conversion.Source s : Conversion.Source.values()) {
            boolean ok = Conversion.present(plugins, s);
            if (ok) trouves++;
            m.bouton(slot, Menu.icone(ok ? "LIME_WOOL" : "GRAY_WOOL", (ok ? "<green>" : "<gray>") + s.nom,
                    ok ? "Trouvé dans plugins/" + s.nom : "Dossier plugins/" + s.nom + " absent",
                    "", ok ? Menu.clic("Clic", "convertir") : "<dark_gray>Rien à convertir"),
                ok ? (p, d, maj) -> Menu.commande(p, "convertir " + s.id) : null);
            slot += 2;
        }
        if (trouves > 1) m.bouton(m.bas() + 4, Menu.icone("NETHER_STAR", "<yellow><bold>Tout convertir", trouves + " plugin(s) trouvé(s).", "", Menu.clic("Clic", "tout convertir")),
            (p, d, maj) -> Menu.commande(p, "convertir tout"));
        return m.navigation(this::accueil);
    }

    // ------------------------------------------------------------------ outils avancés

    public Menu outils() {
        Menu m = new Menu("<dark_gray>Outils avancés", 4);
        boolean pq = plugin.paquets() != null;
        String off = "<red>Couche paquets désactivée (config : paquets.actif)";
        m.bouton(10, Menu.icone("CLOCK", "<white><bold>Mes paquets", "Les paquets que tu reçois et envoies", "le plus, pendant 5 secondes.", "", pq ? Menu.clic("Clic", "mesurer") : off),
            pq ? (p, d, maj) -> Menu.commande(p, "paquets stats") : null);
        m.bouton(12, Menu.icone("WRITABLE_BOOK", "<white><bold>Journal en direct", "Les paquets avec tous leurs champs,", "pendant 10 secondes. Tu choisis lesquels.", "", pq ? Menu.clic("Clic", "choisir") : off),
            pq ? (p, d, maj) -> journal().ouvrir(p) : null);
        m.bouton(14, Menu.icone("OBSERVER", "<white><bold>Qui écoute quoi", "Les plugins qui utilisent la couche paquets.", "", pq ? Menu.clic("Clic", "afficher") : off),
            pq ? (p, d, maj) -> Menu.commande(p, "paquets ecoutes") : null);
        m.bouton(16, Menu.icone("TARGET", "<white><bold>Essai de la couche paquets", "Vérifie qu'elle fonctionne avec les joueurs", "connectés (heure, message bloqué).", "", pq ? Menu.clic("Clic", "lancer") : off),
            pq ? (p, d, maj) -> Menu.commande(p, "paquets essai") : null);
        m.bouton(22, Menu.icone("ARMOR_STAND", "<white><bold>État des animations", "Animations actives, modèle le plus proche.", "", Menu.clic("Clic", "afficher")),
            (p, d, maj) -> Menu.commande(p, "modele info"));
        return m.navigation(this::accueil);
    }

    private Menu journal() {
        Menu m = new Menu("<dark_gray>Journal : quels paquets ?", 3);
        String[][] filtres = {{"Packet", "Tous", "BOOK"}, {"Entity", "Entités", "ZOMBIE_HEAD"}, {"Chat", "Chat et messages", "OAK_SIGN"},
            {"Container", "Inventaires", "CHEST"}, {"Move", "Mouvements", "LEATHER_BOOTS"}, {"Block", "Blocs", "GRASS_BLOCK"},
            {"Sound", "Sons", "NOTE_BLOCK"}, {"Time", "Heure", "CLOCK"}, {"Particle", "Particules", "BLAZE_POWDER"}};
        for (int i = 0; i < filtres.length; i++) {
            String[] f = filtres[i];
            m.bouton(i, Menu.icone(f[2], "<white>" + f[1], "Paquets dont le nom contient « " + f[0] + " »", "", Menu.clic("Clic", "10 secondes de journal")),
                (p, d, maj) -> Menu.commande(p, "paquets journal " + p.getName() + " " + f[0] + " 10"));
        }
        return m.navigation(this::outils);
    }
}
