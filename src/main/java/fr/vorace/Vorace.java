package fr.vorace;

import fr.vorace.api.VoraceAPI;
import fr.vorace.envoi.Envoi;
import fr.vorace.hebergement.Contenu;
import fr.vorace.hebergement.Hebergeur;
import fr.vorace.hebergement.HebergeurExterne;
import fr.vorace.hebergement.HebergeurMemePort;
import fr.vorace.hebergement.HebergeurPortDedie;
import fr.vorace.blocs.Blocs;
import fr.vorace.blocs.EcouteurBlocs;
import fr.vorace.glyphes.EcouteurGlyphes;
import fr.vorace.glyphes.Glyphes;
import fr.vorace.modeles.EcouteurModeles;
import fr.vorace.modeles.Modeles;
import fr.vorace.meubles.EcouteurMeubles;
import fr.vorace.meubles.Meubles;
import fr.vorace.objets.EcouteurObjets;
import fr.vorace.objets.Objets;
import fr.vorace.pack.Constructeur;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Vorace : plugin libre de packs de ressources, objets, meubles, blocs et glyphes.
 * Étape 1 : fusion des packs, hébergement et envoi aux joueurs.
 */
public final class Vorace extends JavaPlugin {

    private volatile Reglages reglages;
    private GestionPack packs;
    private Hebergeur hebergeur;
    private Envoi envoi;
    private Surveillance surveillance;
    private Objets objets;
    private EcouteurObjets ecouteurObjets;
    private Meubles meubles;
    private Blocs blocs;
    private Glyphes glyphes;
    private Modeles modeles;
    private fr.vorace.interfaces.Menus menus;
    private fr.vorace.paquets.Paquets paquets;
    private volatile String testHebergement;

    public Reglages reglages() { return reglages; }
    public GestionPack packs() { return packs; }
    public Hebergeur hebergeur() { return hebergeur; }
    public Envoi envoi() { return envoi; }
    public Objets objets() { return objets; }
    public EcouteurObjets ecouteurObjets() { return ecouteurObjets; }
    public Meubles meubles() { return meubles; }
    public Blocs blocs() { return blocs; }
    public Glyphes glyphes() { return glyphes; }
    public Modeles modeles() { return modeles; }
    public fr.vorace.interfaces.Menus menus() { return menus; }
    /** Couche paquets, ou null si désactivée (config « paquets.actif »). */
    public fr.vorace.paquets.Paquets paquets() { return paquets; }
    public String testHebergement() { return testHebergement; }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        preparerDossiers();
        reglages = Reglages.lire(getConfig());
        Messages.charger(getConfig());

        packs = new GestionPack(this);
        packs.preparerFormat(reglages);
        objets = new Objets(this);
        try {
            objets.charger(false); // les recettes attendent la fin du démarrage (voir plus bas)
        } catch (Throwable t) {
            getLogger().log(java.util.logging.Level.SEVERE, "Impossible de charger les objets (le pack fonctionne quand même)", t);
        }
        glyphes = new Glyphes(this);
        try {
            glyphes.charger();
        } catch (Throwable t) {
            getLogger().log(java.util.logging.Level.SEVERE, "Impossible de charger les glyphes", t);
        }
        try {
            modeles = new Modeles(this);
            modeles.charger();
        } catch (Throwable t) {
            modeles = null;
            getLogger().log(java.util.logging.Level.SEVERE, "Impossible de charger les modèles animés", t);
        }
        VoraceAPI.initialiser(this);
        demarrerHebergement();
        envoi = new Envoi(this);
        Bukkit.getPluginManager().registerEvents(envoi, this);
        menus = new fr.vorace.interfaces.Menus(this);
        Bukkit.getPluginManager().registerEvents(new fr.vorace.interfaces.EcouteurMenus(), this);
        try {
            ecouteurObjets = new EcouteurObjets(this);
            Bukkit.getPluginManager().registerEvents(ecouteurObjets, this);
        } catch (Throwable t) {
            getLogger().log(java.util.logging.Level.SEVERE, "Impossible d'activer les actions des objets", t);
        }
        try {
            meubles = new Meubles(this);
            Bukkit.getPluginManager().registerEvents(new EcouteurMeubles(this), this);
            Bukkit.getScheduler().runTaskTimer(this, meubles::nettoyerSieges, 100L, 100L);
        } catch (Throwable t) {
            getLogger().log(java.util.logging.Level.SEVERE, "Impossible d'activer les meubles", t);
        }
        try {
            blocs = new Blocs(this);
            Bukkit.getPluginManager().registerEvents(new EcouteurBlocs(this), this);
            blocs.demarrer();
            blocs.reglerPaper();
        } catch (Throwable t) {
            getLogger().log(java.util.logging.Level.SEVERE, "Impossible d'activer les blocs", t);
        }

        if (modeles != null) {
            try {
                Bukkit.getPluginManager().registerEvents(new EcouteurModeles(this), this);
                modeles.demarrer();
            } catch (Throwable t) {
                getLogger().log(java.util.logging.Level.SEVERE, "Impossible d'activer les modèles animés", t);
            }
        }
        if (getConfig().getBoolean("paquets.actif", true)) {
            try {
                paquets = new fr.vorace.paquets.Paquets(this);
                Bukkit.getPluginManager().registerEvents(paquets, this);
                paquets.demarrer();
            } catch (Throwable t) {
                paquets = null;
                getLogger().log(java.util.logging.Level.SEVERE, "Impossible d'activer la couche paquets", t);
            }
        }
        try {
            Bukkit.getPluginManager().registerEvents(new EcouteurGlyphes(this), this);
        } catch (Throwable t) {
            getLogger().log(java.util.logging.Level.SEVERE, "Impossible d'activer les emojis du chat", t);
        }

        Commande c = new Commande(this);
        for (String nom : List.of("vorace", "emojis")) {
            PluginCommand cmd = getCommand(nom);
            if (cmd != null) {
                cmd.setExecutor(c);
                cmd.setTabCompleter(c);
            }
        }

        surveillance = new Surveillance(this);
        reconstruire(null);
        if (reglages.surveillance()) surveillance.demarrer(reglages.intervalleSecondes());
        // une fois le serveur démarré : recettes (registres du jeu prêts), puis test de l'hébergement
        Bukkit.getScheduler().runTask(this, () -> {
            try {
                objets.enregistrerRecettes();
            } catch (Throwable t) {
                getLogger().log(java.util.logging.Level.SEVERE, "Impossible d'enregistrer les recettes", t);
            }
        });
        Bukkit.getScheduler().runTaskLater(this, this::testerHebergement, 40L);
        // PlaceholderAPI (facultatif) : %vorace_glyphe_<id>%, %vorace_decalage_<n>%
        Bukkit.getScheduler().runTask(this, () -> {
            if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return;
            try {
                if (new fr.vorace.glyphes.ExtensionPlaceholder(this).register()) getLogger().info("PlaceholderAPI : %vorace_glyphe_<id>% disponible");
            } catch (Throwable t) {
                getLogger().warning("PlaceholderAPI : extension non enregistrée (" + t + ")");
            }
        });
        getLogger().info("Vorace " + getPluginMeta().getVersion() + " activé");
    }

    @Override
    public void onDisable() {
        if (surveillance != null) surveillance.arreter();
        if (paquets != null) {
            try { paquets.arreter(); } catch (Throwable t) { getLogger().warning("Arrêt de la couche paquets : " + t); }
        }
        if (modeles != null) {
            try { modeles.arreter(); } catch (Throwable t) { getLogger().warning("Arrêt des modèles animés : " + t); }
        }
        if (blocs != null) {
            try { blocs.arreter(); } catch (Throwable t) { getLogger().warning("Arrêt des blocs : " + t); }
        }
        if (meubles != null) {
            try { meubles.arreter(); } catch (Throwable t) { getLogger().warning("Arrêt des meubles : " + t); }
        }
        if (hebergeur != null) hebergeur.arreter();
        if (packs != null) packs.arreter();
    }

    public void rechargerConfig() {
        reloadConfig();
        Reglages ancien = reglages;
        reglages = Reglages.lire(getConfig());
        Messages.charger(getConfig());
        objets.charger(true);
        glyphes.charger();
        if (modeles != null) modeles.charger();
        rafraichirMeubles();
        if (!ancien.mode().equals(reglages.mode()) || ancien.portDedie() != reglages.portDedie()
            || !ancien.adressePublique().equals(reglages.adressePublique()) || ancien.portPublic() != reglages.portPublic()
            || !ancien.urlExterne().equals(reglages.urlExterne())) {
            hebergeur.arreter();
            demarrerHebergement();
            Bukkit.getScheduler().runTaskLater(this, this::testerHebergement, 20L);
        }
        if (reglages.surveillance()) surveillance.demarrer(reglages.intervalleSecondes());
        else surveillance.arreter();
    }

    /** Met à jour l'apparence des meubles posés après un changement des objets. */
    public void rafraichirMeubles() {
        if (meubles == null) return;
        try {
            int n = meubles.rafraichirTout();
            if (n > 0) getLogger().info(n + " meuble(s) mis à jour");
        } catch (Throwable t) {
            getLogger().warning("Mise à jour des meubles : " + t);
        }
    }

    /** Reconstruit le pack puis l'envoie à ceux qui l'attendent (et à tous si la config le demande). */
    public void reconstruire(Consumer<Constructeur.Resultat> ensuite) {
        Constructeur.Resultat avant = packs.actuel();
        packs.construire(reglages, res -> {
            boolean change = avant == null || !avant.sha1().equals(res.sha1());
            envoi.packPret(change, reglages.renvoyerAuto() && avant != null);
            if (ensuite != null) ensuite.accept(res);
        }).thenRun(() -> { if (surveillance != null) surveillance.memoriser(); });
    }

    private void demarrerHebergement() {
        Contenu contenu = new Contenu(() -> packs.actuel());
        String mode = reglages.mode().toLowerCase(Locale.ROOT).trim();
        List<String> essais = switch (mode) {
            case "meme-port", "même-port" -> List.of("meme-port");
            case "port", "port-dedie" -> List.of("port");
            case "externe" -> List.of("externe");
            default -> List.of("meme-port", "port");
        };
        for (String essai : essais) {
            Hebergeur h = switch (essai) {
                case "meme-port" -> creerMemePort(contenu);
                case "port" -> new HebergeurPortDedie(contenu, reglages.portDedie(), reglages.adressePublique(), reglages.portPublic());
                default -> new HebergeurExterne(reglages.urlExterne());
            };
            if (h == null) continue;
            try {
                h.demarrer();
                hebergeur = h;
                getLogger().info("Hébergement du pack : " + h.description());
                return;
            } catch (Throwable t) {
                getLogger().warning("Hébergement « " + essai + " » impossible : " + t);
            }
        }
        // rien n'a marché : on garde un hébergeur externe vide pour que le plugin ne plante pas
        hebergeur = new HebergeurExterne("http://hebergement-indisponible/{sha1}.zip");
        getLogger().severe("Aucun hébergement du pack n'a pu démarrer : regarde la section « hebergement » de config.yml");
    }

    private Hebergeur creerMemePort(Contenu contenu) {
        try {
            Class.forName("io.papermc.paper.network.ChannelInitializeListenerHolder");
            return new HebergeurMemePort(contenu, reglages.adressePublique(), reglages.portPublic());
        } catch (Throwable t) {
            getLogger().info("Le serveur ne permet pas l'hébergement sur le port du jeu (pas Paper ?)");
            return null;
        }
    }

    /** Télécharge /vorace/ping depuis le serveur lui-même pour vérifier que l'hébergement répond. */
    private void testerHebergement() {
        if (!(hebergeur instanceof HebergeurMemePort) && !(hebergeur instanceof HebergeurPortDedie)) return;
        int port = hebergeur instanceof HebergeurMemePort ? Bukkit.getPort() : reglages.portDedie();
        String ip = Bukkit.getIp() == null || Bukkit.getIp().isBlank() ? "127.0.0.1" : Bukkit.getIp();
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(ip, port), 3000);
                s.setSoTimeout(3000);
                OutputStream out = s.getOutputStream();
                out.write(("GET " + Contenu.PREFIXE + "ping HTTP/1.1\r\nHost: test\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
                out.flush();
                InputStream in = s.getInputStream();
                String rep = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
                if (rep.startsWith("HTTP/1.1 200") && rep.endsWith("vorace")) {
                    testHebergement = "<green>OK</green>";
                    getLogger().info("Test de l'hébergement : OK (le pack est téléchargeable sur le port " + port + ")");
                } else {
                    testHebergement = "<red>réponse inattendue</red>";
                    getLogger().warning("Test de l'hébergement : réponse inattendue sur le port " + port);
                }
            } catch (IOException e) {
                testHebergement = "<red>échec</red>";
                getLogger().warning("Test de l'hébergement : échec sur " + ip + ":" + port + " (" + e.getMessage() + ")");
            }
        });
    }

    private void preparerDossiers() {
        try {
            Path sources = getDataFolder().toPath().resolve("pack").resolve("sources");
            Files.createDirectories(sources);
            Path lisezmoi = sources.resolve("LISEZMOI.txt");
            if (!Files.exists(lisezmoi)) {
                Files.writeString(lisezmoi, """
                    Dépose ici tes packs de ressources (dossiers ou fichiers .zip).
                    Vorace les fusionne tous, avec ceux de ModelEngine et MythicMobs, en un seul pack
                    envoyé automatiquement aux joueurs.

                    Ordre : par ordre alphabétique, le dernier gagne en cas de conflit.
                    Pour choisir l'ordre, préfixe les noms : 10-base.zip, 20-smashduels.zip, 90-retouches/ ...

                    Après un changement : /vorace reload (ou attends quelques secondes, Vorace le voit tout seul).
                    Le pack final et le rapport de fusion sont dans plugins/Vorace/pack/genere/.
                    """, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            getLogger().warning("Impossible de créer plugins/Vorace/pack/sources : " + e.getMessage());
        }
    }
}
