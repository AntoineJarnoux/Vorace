package fr.vorace.paquets;

/** Sens d'un paquet. */
public enum Sens {
    /** Du serveur vers le joueur (« Clientbound... »). */
    SORTANT,
    /** Du joueur vers le serveur (« Serverbound... »). */
    ENTRANT
}
