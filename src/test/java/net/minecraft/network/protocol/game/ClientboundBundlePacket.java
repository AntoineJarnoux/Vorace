package net.minecraft.network.protocol.game;

import java.util.ArrayList;
import java.util.List;

/** Imitation (pour les tests) d'un lot de paquets. */
public final class ClientboundBundlePacket {
    private final List<Object> sous = new ArrayList<>();

    public ClientboundBundlePacket(Iterable<?> paquets) {
        paquets.forEach(sous::add);
    }

    public Iterable<Object> subPackets() { return sous; }
}
