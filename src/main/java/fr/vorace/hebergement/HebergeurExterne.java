package fr.vorace.hebergement;

import org.bukkit.entity.Player;

/**
 * Le pack est mis en ligne ailleurs (site web, stockage, etc.) : Vorace donne juste l'adresse.
 * Dans l'adresse, {sha1} est remplacé par l'empreinte du pack.
 */
public final class HebergeurExterne implements Hebergeur {

    private final String modele;

    public HebergeurExterne(String modele) {
        this.modele = modele;
    }

    @Override
    public void demarrer() {
        if (modele == null || modele.isBlank()) throw new IllegalStateException("hebergement.url-externe est vide");
    }

    @Override
    public void arreter() {}

    @Override
    public String url(Player joueur, String sha1) {
        return modele.replace("{sha1}", sha1);
    }

    @Override
    public String description() {
        return "adresse externe " + modele;
    }
}
