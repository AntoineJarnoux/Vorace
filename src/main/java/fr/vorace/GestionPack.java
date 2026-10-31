package fr.vorace;

import fr.vorace.api.VoracePackPretEvent;
import fr.vorace.pack.Constructeur;
import fr.vorace.pack.FormatPack;
import fr.vorace.pack.Journal;
import fr.vorace.pack.Recherche;
import fr.vorace.pack.Source;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Level;

/** Construit le pack (hors du fil principal) et garde le dernier résultat. */
public final class GestionPack {

    private final Vorace plugin;
    private final ExecutorService atelier = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Vorace-Pack");
        t.setDaemon(true);
        return t;
    });
    private volatile Constructeur.Resultat actuel;
    private volatile List<Recherche.Trouve> dernieresSources = List.of();
    private volatile FormatPack format;

    public GestionPack(Vorace plugin) {
        this.plugin = plugin;
    }

    public Constructeur.Resultat actuel() { return actuel; }
    public List<Recherche.Trouve> dernieresSources() { return dernieresSources; }
    public FormatPack format() { return format; }

    /** Détecte le format du pack tout de suite (les objets en ont besoin avant la première construction). */
    public void preparerFormat(Reglages r) {
        if (format == null) format = detecterFormat(r);
    }

    public Path racineServeur() {
        return Path.of("").toAbsolutePath();
    }

    public Path dossierGenere() {
        return plugin.getDataFolder().toPath().resolve("pack").resolve("genere");
    }

    public List<Recherche.Trouve> chercherSources(Reglages r) {
        return Recherche.trouver(racineServeur(), r.sources(), dossierGenere());
    }

    /** Lance une construction. La suite (envoi aux joueurs...) est appelée sur le fil principal. */
    public CompletableFuture<Constructeur.Resultat> construire(Reglages r, Consumer<Constructeur.Resultat> ensuite) {
        CompletableFuture<Constructeur.Resultat> f = new CompletableFuture<>();
        atelier.execute(() -> {
            try {
                Constructeur.Resultat res = construireMaintenant(r);
                Constructeur.Resultat ancien = actuel;
                actuel = res;
                boolean change = ancien == null || !ancien.sha1().equals(res.sha1());
                f.complete(res);
                if (plugin.isEnabled()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Bukkit.getPluginManager().callEvent(new VoracePackPretEvent(res.sha1(), change));
                        if (ensuite != null) ensuite.accept(res);
                    });
                }
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE, "Impossible de construire le pack", t);
                f.completeExceptionally(t);
            }
        });
        return f;
    }

    private Constructeur.Resultat construireMaintenant(Reglages r) throws IOException {
        Source.Filtre.ajouterMotifs(r.ignorer());
        if (format == null) format = detecterFormat(r);
        List<Recherche.Trouve> trouves = chercherSources(r);
        dernieresSources = trouves;
        List<Source> sources = new ArrayList<>();
        for (Recherche.Trouve t : trouves) {
            if (!t.present()) {
                if (r.debug()) plugin.getLogger().info("Source absente (ignorée) : " + t.motif());
                continue;
            }
            try {
                sources.add(Source.charger(t.nom(), t.chemin()));
            } catch (IOException | RuntimeException e) {
                plugin.getLogger().warning("Pack illisible, ignoré : " + t.chemin() + " (" + e.getMessage() + ")");
            }
        }
        if (plugin.objets() != null) {
            Journal journalObjets = new Journal();
            java.util.Map<String, byte[]> objets = plugin.objets().fichiersPack(format, journalObjets);
            if (!objets.isEmpty()) sources.add(Source.enMemoire("objets Vorace", objets));
            journalObjets.lignes().forEach(l -> plugin.getLogger().warning(l.message()));
        }
        byte[] icone = null;
        if (r.icone() != null && !r.icone().isBlank()) {
            Path p = plugin.getDataFolder().toPath().resolve(r.icone());
            if (Files.isRegularFile(p)) icone = Files.readAllBytes(p);
        }
        Constructeur.Resultat res = Constructeur.construire(sources,
            new Constructeur.Options(r.description(), format, r.minifier(), icone));

        Path dossier = dossierGenere();
        Files.createDirectories(dossier);
        Path tmp = dossier.resolve("pack.zip.tmp");
        Files.write(tmp, res.zip());
        Files.move(tmp, dossier.resolve("pack.zip"), StandardCopyOption.REPLACE_EXISTING);
        String entete = "Vorace - rapport de construction du " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")) + "\n"
            + "Sources (de la moins prioritaire à la plus prioritaire) :\n";
        StringBuilder sb = new StringBuilder(entete);
        for (Recherche.Trouve t : trouves) sb.append(t.present() ? "  [OK]     " : "  [ABSENT] ").append(t.motif()).append(t.present() && t.motif().endsWith("/*") ? " -> " + t.nom() : "").append('\n');
        sb.append('\n').append(res.journal().texte());
        Files.writeString(dossier.resolve("rapport.txt"), sb.toString(), StandardCharsets.UTF_8);

        Journal j = res.journal();
        plugin.getLogger().info("Pack construit en " + res.dureeMs() + " ms : " + sources.size() + " source(s), "
            + res.nbFichiers() + " fichiers, " + (res.zip().length / 1024) + " Ko, " + j.compter(Journal.Niveau.FUSION) + " fusion(s), "
            + j.compter(Journal.Niveau.CONFLIT) + " conflit(s), " + j.compter(Journal.Niveau.ERREUR) + " erreur(s). Détails : plugins/Vorace/pack/genere/rapport.txt");
        if (r.debug()) j.lignes().forEach(l -> plugin.getLogger().info(l.toString()));
        else j.lignes().stream().filter(l -> l.niveau() == Journal.Niveau.ERREUR).forEach(l -> plugin.getLogger().warning(l.message()));
        return res;
    }

    private FormatPack detecterFormat(Reglages r) {
        String force = r.formatForce() == null ? "auto" : r.formatForce().trim();
        if (!force.equalsIgnoreCase("auto") && !force.isEmpty()) {
            try {
                String[] p = force.split("\\.");
                return new FormatPack(Integer.parseInt(p[0]), p.length > 1 ? Integer.parseInt(p[1]) : 0, "config");
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("pack.format invalide (" + force + "), détection automatique");
            }
        }
        String version;
        try {
            version = Bukkit.getMinecraftVersion();
        } catch (Throwable t) {
            version = Bukkit.getBukkitVersion().split("-")[0];
        }
        FormatPack f = FormatPack.detecter(Bukkit.getServer().getClass().getClassLoader(), version);
        plugin.getLogger().info("Minecraft " + version + " : format de pack " + f.texte() + " (" + f.origine() + ")");
        return f;
    }

    public void arreter() {
        atelier.shutdownNow();
    }
}
