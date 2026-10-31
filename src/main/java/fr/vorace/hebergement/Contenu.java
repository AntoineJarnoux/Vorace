package fr.vorace.hebergement;

import fr.vorace.pack.Constructeur;

import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

/** Ce que les deux serveurs web (port du jeu ou port dédié) répondent, selon le chemin demandé. */
public final class Contenu {

    public static final String PREFIXE = "/vorace/";

    public record Reponse(int code, String type, byte[] corps) {}

    private final Supplier<Constructeur.Resultat> packActuel;

    public Contenu(Supplier<Constructeur.Resultat> packActuel) {
        this.packActuel = packActuel;
    }

    public Reponse repondre(String chemin) {
        int q = chemin.indexOf('?');
        if (q >= 0) chemin = chemin.substring(0, q);
        if (chemin.equals(PREFIXE + "ping")) {
            return new Reponse(200, "text/plain; charset=utf-8", "vorace".getBytes(StandardCharsets.UTF_8));
        }
        Constructeur.Resultat pack = packActuel.get();
        if (pack != null && chemin.startsWith(PREFIXE) && chemin.endsWith(".zip")) {
            String nom = chemin.substring(PREFIXE.length(), chemin.length() - 4);
            // l'empreinte actuelle, ou "pack" pour toujours avoir la dernière version
            if (nom.equals(pack.sha1()) || nom.equals("pack")) return new Reponse(200, "application/zip", pack.zip());
        }
        return new Reponse(404, "text/plain; charset=utf-8", "introuvable".getBytes(StandardCharsets.UTF_8));
    }

    /** Construit l'adresse http://hote:port/vorace/sha1.zip (hôte IPv6 entre crochets). */
    public static String url(String hote, int port, String sha1) {
        String h = hote.contains(":") && !hote.startsWith("[") ? "[" + hote + "]" : hote;
        return "http://" + h + ":" + port + PREFIXE + sha1 + ".zip";
    }

    /** Nettoie l'adresse tapée par le joueur (point final des enregistrements SRV, marqueurs de mods). */
    public static String nettoyerHote(String hote) {
        if (hote == null) return null;
        int zero = hote.indexOf('\0');
        if (zero >= 0) hote = hote.substring(0, zero);
        while (hote.endsWith(".")) hote = hote.substring(0, hote.length() - 1);
        return hote.isBlank() ? null : hote;
    }
}
