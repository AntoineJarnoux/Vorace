package net.minecraft.network.protocol.game;

/** Imitation (pour les tests) d'un paquet « record » du jeu. */
public record ClientboundSetTimePacket(long gameTime, long dayTime, boolean tickDayTime) {}
