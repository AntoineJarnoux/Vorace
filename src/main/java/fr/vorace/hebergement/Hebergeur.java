package fr.vorace.hebergement;

import org.bukkit.entity.Player;

/** Rend le pack téléchargeable par les joueurs. */
public interface Hebergeur {

    /** Démarre l'hébergement. Lève une exception si c'est impossible (Vorace essaie alors le mode suivant). */
    void demarrer() throws Exception;

    void arreter();

    /** Adresse à donner à ce joueur pour télécharger le pack d'empreinte sha1. */
    String url(Player joueur, String sha1);

    /** Courte description pour /vorace info. */
    String description();
}
