package io.papermc.paper.network;
/** Copie de compilation d'une API interne de Paper (voir LISEZMOI.md). */
public final class ChannelInitializeListenerHolder {
  public static boolean hasListener(net.kyori.adventure.key.Key k){return false;}
  public static void addListener(net.kyori.adventure.key.Key k, ChannelInitializeListener l){}
  public static ChannelInitializeListener removeListener(net.kyori.adventure.key.Key k){return null;}
}
