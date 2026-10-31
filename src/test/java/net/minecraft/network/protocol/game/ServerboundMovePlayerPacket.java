package net.minecraft.network.protocol.game;

/** Imitation (pour les tests) d'un paquet à variantes (classes internes). */
public abstract class ServerboundMovePlayerPacket {
    protected final double x;

    protected ServerboundMovePlayerPacket(double x) {
        this.x = x;
    }

    public static final class Pos extends ServerboundMovePlayerPacket {
        public Pos(double x) {
            super(x);
        }
    }
}
