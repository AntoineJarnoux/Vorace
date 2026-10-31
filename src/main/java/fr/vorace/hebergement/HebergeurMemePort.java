package fr.vorace.hebergement;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.papermc.paper.network.ChannelInitializeListenerHolder;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Sert le pack sur le port du jeu lui-même : aucun port à ouvrir chez l'hébergeur.
 * <p>
 * Vorace se place en tête de chaque nouvelle connexion. Si les premiers octets sont "GET " ou
 * "HEAD", c'est un téléchargement HTTP (un client Minecraft commence toujours par un paquet
 * binaire dont le 2e octet vaut 0) : Vorace répond et ferme. Sinon il se retire aussitôt et
 * la connexion Minecraft continue normalement.
 */
public final class HebergeurMemePort implements Hebergeur {

    private static final Key CLE = Key.key("vorace", "http");
    private static final int TAILLE_MAX_REQUETE = 8192;

    private final Contenu contenu;
    private final String adressePublique;
    private final int portPublic;

    public HebergeurMemePort(Contenu contenu, String adressePublique, int portPublic) {
        this.contenu = contenu;
        this.adressePublique = adressePublique;
        this.portPublic = portPublic;
    }

    @Override
    public void demarrer() {
        ChannelInitializeListenerHolder.addListener(CLE, this::preparer);
    }

    private void preparer(Channel canal) {
        canal.pipeline().addFirst("vorace_http", new Detecteur(contenu));
    }

    @Override
    public void arreter() {
        try {
            ChannelInitializeListenerHolder.removeListener(CLE);
        } catch (Throwable ignore) {
            // serveur en cours d'arrêt
        }
    }

    @Override
    public String url(Player joueur, String sha1) {
        String hote = adressePublique == null || adressePublique.isBlank() ? null : adressePublique.trim();
        int port = portPublic;
        InetSocketAddress virtuel = joueur.getVirtualHost();
        if (hote == null && virtuel != null) hote = Contenu.nettoyerHote(virtuel.getHostString());
        if (port <= 0 && virtuel != null && virtuel.getPort() > 0) port = virtuel.getPort();
        if (hote == null) hote = Bukkit.getIp() == null || Bukkit.getIp().isBlank() ? "localhost" : Bukkit.getIp();
        if (port <= 0) port = Bukkit.getPort();
        return Contenu.url(hote, port, sha1);
    }

    @Override
    public String description() {
        return "port du jeu (" + Bukkit.getPort() + ")";
    }

    /** Gestionnaire Netty placé en tête de chaque connexion. */
    static final class Detecteur extends ChannelInboundHandlerAdapter {
        private final Contenu contenu;
        private ByteBuf tampon;
        private boolean http;

        Detecteur(Contenu contenu) {
            this.contenu = contenu;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (!(msg instanceof ByteBuf recu)) {
                laisserPasser(ctx, msg);
                return;
            }
            if (tampon == null) tampon = ctx.alloc().buffer();
            tampon.writeBytes(recu);
            recu.release();

            if (!http) {
                if (tampon.readableBytes() < 4) {
                    if (!debutPossible(tampon)) laisserPasser(ctx, null);
                    return; // on attend la suite (très rare)
                }
                if (!commencePar(tampon, "GET ") && !commencePar(tampon, "HEAD")) {
                    laisserPasser(ctx, null);
                    return;
                }
                http = true;
                // pas de délai d'inactivité Minecraft pendant un long téléchargement
                if (ctx.pipeline().get("timeout") != null) ctx.pipeline().remove("timeout");
            }
            int fin = finEntetes(tampon);
            if (fin < 0) {
                if (tampon.readableBytes() > TAILLE_MAX_REQUETE) fermer(ctx, 400, "requete trop longue");
                return;
            }
            String requete = tampon.toString(tampon.readerIndex(), fin, StandardCharsets.ISO_8859_1);
            tampon.release();
            tampon = null;
            String premiere = requete.split("\r\n", 2)[0];
            String[] morceaux = premiere.split(" ");
            if (morceaux.length < 2) {
                fermer(ctx, 400, "requete invalide");
                return;
            }
            boolean tete = morceaux[0].equals("HEAD");
            Contenu.Reponse r = contenu.repondre(morceaux[1]);
            byte[] entetes = ("HTTP/1.1 " + r.code() + (r.code() == 200 ? " OK" : " Not Found") + "\r\n"
                + "Content-Type: " + r.type() + "\r\n"
                + "Content-Length: " + r.corps().length + "\r\n"
                + "Cache-Control: no-cache\r\n"
                + "Server: Vorace\r\n"
                + "Connection: close\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1);
            ByteBuf sortie = tete ? Unpooled.wrappedBuffer(entetes) : Unpooled.wrappedBuffer(entetes, r.corps());
            ctx.writeAndFlush(sortie).addListener(ChannelFutureListener.CLOSE);
        }

        private void laisserPasser(ChannelHandlerContext ctx, Object autre) {
            ByteBuf deja = tampon;
            tampon = null;
            ctx.pipeline().remove(this);
            if (deja != null) ctx.fireChannelRead(deja);
            if (autre != null) ctx.fireChannelRead(autre);
        }

        private void fermer(ChannelHandlerContext ctx, int code, String texte) {
            if (tampon != null) { tampon.release(); tampon = null; }
            byte[] corps = texte.getBytes(StandardCharsets.UTF_8);
            byte[] e = ("HTTP/1.1 " + code + " Bad Request\r\nContent-Length: " + corps.length + "\r\nConnection: close\r\n\r\n")
                .getBytes(StandardCharsets.ISO_8859_1);
            ctx.writeAndFlush(Unpooled.wrappedBuffer(e, corps)).addListener(ChannelFutureListener.CLOSE);
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) {
            if (tampon != null) { tampon.release(); tampon = null; }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            if (http) ctx.close();
            else ctx.fireExceptionCaught(cause);
        }

        private static boolean commencePar(ByteBuf b, String s) {
            if (b.readableBytes() < s.length()) return false;
            for (int i = 0; i < s.length(); i++) if (b.getByte(b.readerIndex() + i) != (byte) s.charAt(i)) return false;
            return true;
        }

        private static boolean debutPossible(ByteBuf b) {
            int n = b.readableBytes();
            String[] mots = {"GET ", "HEAD"};
            for (String m : mots) {
                boolean ok = true;
                for (int i = 0; i < n && i < 4; i++) if (b.getByte(b.readerIndex() + i) != (byte) m.charAt(i)) { ok = false; break; }
                if (ok) return true;
            }
            return false;
        }

        private static int finEntetes(ByteBuf b) {
            int debut = b.readerIndex();
            int fin = b.writerIndex();
            for (int i = debut; i + 3 < fin; i++) {
                if (b.getByte(i) == '\r' && b.getByte(i + 1) == '\n' && b.getByte(i + 2) == '\r' && b.getByte(i + 3) == '\n') return i - debut;
            }
            return -1;
        }
    }
}
