package fr.vorace;

import fr.vorace.pack.Recherche;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.stream.Stream;

/**
 * Regarde régulièrement si un pack source a changé (par exemple après un /meg reload de
 * ModelEngine) et reconstruit alors le pack tout seul. On attend que les fichiers ne bougent
 * plus pendant un tour pour ne pas construire au milieu d'une écriture.
 */
public final class Surveillance {

    private final Vorace plugin;
    private BukkitTask tache;
    private long derniereSignature = Long.MIN_VALUE;
    private long signatureVue = Long.MIN_VALUE;

    public Surveillance(Vorace plugin) {
        this.plugin = plugin;
    }

    public void demarrer(int secondes) {
        arreter();
        derniereSignature = signature();
        long t = secondes * 20L;
        tache = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::verifier, t, t);
    }

    public void arreter() {
        if (tache != null) tache.cancel();
        tache = null;
    }

    /** À appeler après une construction lancée à la main, pour ne pas reconstruire une 2e fois. */
    public void memoriser() {
        derniereSignature = signature();
        signatureVue = derniereSignature;
    }

    private void verifier() {
        long s = signature();
        if (s == derniereSignature) { signatureVue = s; return; }
        if (s != signatureVue) { signatureVue = s; return; } // ça bouge encore, on attend un tour
        derniereSignature = s;
        plugin.getLogger().info("Un pack source, un objet ou un glyphe a changé : reconstruction automatique");
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (plugin.objets() != null) { plugin.objets().charger(true); plugin.rafraichirMeubles(); }
            if (plugin.glyphes() != null) plugin.glyphes().charger();
            plugin.reconstruire(null);
        });
    }

    long signature() {
        List<Recherche.Trouve> sources = new java.util.ArrayList<>(plugin.packs().chercherSources(plugin.reglages()));
        if (plugin.objets() != null) {
            sources.add(new Recherche.Trouve("objets", plugin.objets().dossierObjets(), Files.isDirectory(plugin.objets().dossierObjets())));
            sources.add(new Recherche.Trouve("ressources", plugin.objets().dossierRessources(), Files.isDirectory(plugin.objets().dossierRessources())));
        }
        if (plugin.glyphes() != null) {
            sources.add(new Recherche.Trouve("glyphes", plugin.glyphes().dossier(), Files.isDirectory(plugin.glyphes().dossier())));
        }
        long h = 17;
        for (Recherche.Trouve t : sources) {
            h = h * 31 + t.chemin().toString().hashCode();
            if (!t.present()) continue;
            if (Files.isDirectory(t.chemin())) {
                try (Stream<Path> f = Files.walk(t.chemin())) {
                    for (Path p : (Iterable<Path>) f::iterator) {
                        BasicFileAttributes a = Files.readAttributes(p, BasicFileAttributes.class);
                        if (a.isRegularFile()) {
                            h = h * 31 + p.toString().hashCode();
                            h = h * 31 + a.size();
                            h = h * 31 + a.lastModifiedTime().toMillis();
                        }
                    }
                } catch (IOException e) {
                    h = h * 31 + 1;
                }
            } else {
                try {
                    h = h * 31 + Files.size(t.chemin());
                    h = h * 31 + Files.getLastModifiedTime(t.chemin()).toMillis();
                } catch (IOException e) {
                    h = h * 31 + 2;
                }
            }
        }
        return h;
    }
}
