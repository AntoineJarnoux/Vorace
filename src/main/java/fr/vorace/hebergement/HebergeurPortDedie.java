package fr.vorace.hebergement;

import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Petit serveur web sur un port à part (si l'hébergeur en ouvre un deuxième). */
public final class HebergeurPortDedie implements Hebergeur {

    private final Contenu contenu;
    private final int port;
    private final String adressePublique;
    private final int portPublic;
    private HttpServer serveur;
    private ExecutorService fils;

    public HebergeurPortDedie(Contenu contenu, int port, String adressePublique, int portPublic) {
        this.contenu = contenu;
        this.port = port;
        this.adressePublique = adressePublique;
        this.portPublic = portPublic;
    }

    @Override
    public void demarrer() throws Exception {
        serveur = HttpServer.create(new InetSocketAddress(port), 32);
        serveur.createContext("/", echange -> {
            try (echange) {
                Contenu.Reponse r = contenu.repondre(echange.getRequestURI().getPath());
                echange.getResponseHeaders().set("Content-Type", r.type());
                echange.getResponseHeaders().set("Cache-Control", "no-cache");
                boolean tete = echange.getRequestMethod().equalsIgnoreCase("HEAD");
                echange.sendResponseHeaders(r.code(), tete ? -1 : r.corps().length);
                if (!tete) {
                    try (OutputStream out = echange.getResponseBody()) { out.write(r.corps()); }
                }
            }
        });
        fils = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "Vorace-HTTP");
            t.setDaemon(true);
            return t;
        });
        serveur.setExecutor(fils);
        serveur.start();
    }

    @Override
    public void arreter() {
        if (serveur != null) serveur.stop(0);
        if (fils != null) fils.shutdownNow();
    }

    @Override
    public String url(Player joueur, String sha1) {
        String hote = adressePublique == null || adressePublique.isBlank() ? null : adressePublique.trim();
        InetSocketAddress virtuel = joueur.getVirtualHost();
        if (hote == null && virtuel != null) hote = Contenu.nettoyerHote(virtuel.getHostString());
        if (hote == null) hote = Bukkit.getIp() == null || Bukkit.getIp().isBlank() ? "localhost" : Bukkit.getIp();
        return Contenu.url(hote, portPublic > 0 ? portPublic : port, sha1);
    }

    @Override
    public String description() {
        return "port dédié " + port;
    }
}
