package net.minecraft.network.protocol.game;

/** Imitation (pour les tests) d'un paquet classique (classe avec champs finals). */
public final class ServerboundChatPacket {
    private final String message;
    private final long timeStamp;

    public ServerboundChatPacket(String message, long timeStamp) {
        this.message = message;
        this.timeStamp = timeStamp;
    }

    public String message() { return message; }
    public long timeStamp() { return timeStamp; }
}
