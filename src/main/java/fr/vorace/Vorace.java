package fr.vorace;

import fr.vorace.envoi.Envoi;
import fr.vorace.hebergement.Contenu;
import fr.vorace.hebergement.Hebergeur;
import fr.vorace.hebergement.HebergeurExterne;
import fr.vorace.hebergement.HebergeurMemePort;
import fr.vorace.hebergement.HebergeurPortDedie;
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
    private volatile String testHebergement;

    public Reglages reglages() { return reglages; }
    public GestionPack packs() { return packs; }
    public Hebergeur hebergeur() { return hebergeur; }
    public Envoi envoi() { return envoi; }
    public String testHebergement() { return testHebergement; }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        preparerDossiers();
        reglages = Reglages.lire(getConfig());
        Messages.charger(getConfig());

        packs = new GestionPack(this);
        demarrerHebergement();
        envoi = new Envoi(this);
        Bukkit.getPluginManager().registerEvents(envoi, this);

        PluginCommand cmd = getCommand("vorace");
        if (cmd != null) {
            Commande c = new Commande(this);
            cmd.setExecutor(c);
            cmd.setTabCompleter(c);
        }

        surveillance = new Surveillance(this);
        reconstruire(null);
        if (reglages.surveillance()) surveillance.demarrer(reglages.intervalleSecondes());
        // le port du jeu n'est ouvert qu'une fois le serveur démarré : on teste au premier tick
        Bukkit.getScheduler().runTaskLater(this, this::testerHebergement, 40L);
    }

    @Override
    public void onDisable() {
        if (surveillance != null) surveillance.arreter();
        if (hebergeur != null) hebergeur.arreter();
        if (packs != null) packs.arreter();
    }

    public void rechargerConfig() {
        reloadConfig();
        Reglages ancien = reglages;
        reglages = Reglages.lire(getConfig());
        Messages.charger(getConfig());
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
