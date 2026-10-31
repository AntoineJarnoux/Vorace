package fr.vorace.hebergement;

import fr.vorace.pack.Constructeur;
import fr.vorace.pack.FormatPack;
import fr.vorace.pack.Source;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Teste le détecteur HTTP du port du jeu sans serveur Minecraft. */
public final class TestHttp {
    static int echecs = 0;
    static void verifier(boolean ok, String quoi) { System.out.println((ok ? "OK    " : "ECHEC ") + quoi); if (!ok) echecs++; }

    static final class Capture extends ChannelInboundHandlerAdapter {
        final List<byte[]> recu = new ArrayList<>();
        @Override public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf b = (ByteBuf) msg; byte[] o = new byte[b.readableBytes()]; b.readBytes(o); b.release(); recu.add(o);
        }
    }

    static EmbeddedChannel canal(Contenu c, Capture cap) {
        EmbeddedChannel ch = new EmbeddedChannel();
        ch.pipeline().addLast("timeout", new ChannelInboundHandlerAdapter());
        ch.pipeline().addLast("packet_handler", cap);
        ch.pipeline().addFirst("vorace_http", new HebergeurMemePort.Detecteur(c));
        return ch;
    }

    static String sortie(EmbeddedChannel ch) {
        StringBuilder sb = new StringBuilder(); ByteBuf b;
        while ((b = ch.readOutbound()) != null) { sb.append(b.toString(StandardCharsets.ISO_8859_1)); b.release(); }
        return sb.toString();
    }

    public static void main(String[] a) throws Exception {
        Constructeur.Resultat pack = Constructeur.construire(List.of(Source.enMemoire("t", Map.of("assets/x/a.json", "{}".getBytes()))),
            new Constructeur.Options("t", new FormatPack(46, 0, "t"), true, null));
        Contenu c = new Contenu(() -> pack);

        Capture cap = new Capture();
        EmbeddedChannel ch = canal(c, cap);
        ch.writeInbound(Unpooled.copiedBuffer("GET /vorace/" + pack.sha1() + ".zip HTTP/1.1\r\nHost: crafty.dev.pandoramc.fr:25566\r\nUser-Agent: Minecraft Java/1.21.4\r\n\r\n", StandardCharsets.ISO_8859_1));
        String rep = sortie(ch);
        verifier(rep.startsWith("HTTP/1.1 200 OK") && rep.contains("Content-Length: " + pack.zip().length), "GET du pack : 200 + bonne taille");
        verifier(rep.length() - rep.indexOf("\r\n\r\n") - 4 == pack.zip().length, "corps = le zip complet");
        verifier(!ch.isOpen(), "connexion fermée après envoi");
        verifier(ch.pipeline().get("timeout") == null, "délai d'inactivité Minecraft retiré pendant le téléchargement");
        verifier(cap.recu.isEmpty(), "rien transmis au code Minecraft");

        // requête coupée en deux morceaux
        cap = new Capture(); ch = canal(c, cap);
        ch.writeInbound(Unpooled.copiedBuffer("GE", StandardCharsets.ISO_8859_1));
        ch.writeInbound(Unpooled.copiedBuffer("T /vorace/pack.zip HTTP/1.1\r\nHost: x\r\n", StandardCharsets.ISO_8859_1));
        verifier(sortie(ch).isEmpty() && ch.isOpen(), "requête incomplète : on attend la suite");
        ch.writeInbound(Unpooled.copiedBuffer("\r\n", StandardCharsets.ISO_8859_1));
        verifier(sortie(ch).startsWith("HTTP/1.1 200"), "requête en plusieurs morceaux : 200 (alias pack.zip)");

        // mauvaise empreinte
        cap = new Capture(); ch = canal(c, cap);
        ch.writeInbound(Unpooled.copiedBuffer("GET /vorace/0000.zip HTTP/1.1\r\n\r\n", StandardCharsets.ISO_8859_1));
        verifier(sortie(ch).startsWith("HTTP/1.1 404"), "ancienne empreinte : 404");

        // HEAD
        cap = new Capture(); ch = canal(c, cap);
        ch.writeInbound(Unpooled.copiedBuffer("HEAD /vorace/pack.zip HTTP/1.1\r\n\r\n", StandardCharsets.ISO_8859_1));
        String h = sortie(ch);
        verifier(h.startsWith("HTTP/1.1 200") && h.endsWith("\r\n\r\n"), "HEAD : en-têtes seulement");

        // connexion Minecraft : paquet de poignée de main (longueur, id 0x00, protocole 769, adresse, port, état 2)
        byte[] hs = {0x10, 0x00, (byte) 0x81, 0x06, 0x09, 'l', 'o', 'c', 'a', 'l', 'h', 'o', 's', 't', 0x63, (byte) 0xDD, 0x02};
        cap = new Capture(); ch = canal(c, cap);
        ch.writeInbound(Unpooled.wrappedBuffer(hs));
        ch.writeInbound(Unpooled.wrappedBuffer(new byte[]{0x05, 0x00, 0x01, 0x02, 0x03, 0x04}));
        verifier(cap.recu.size() == 2 && java.util.Arrays.equals(cap.recu.get(0), hs), "Minecraft : octets transmis intacts");
        verifier(ch.pipeline().get("vorace_http") == null && ch.isOpen() && ch.pipeline().get("timeout") != null, "Minecraft : Vorace se retire, connexion normale");

        // ping de la liste des serveurs (ancien format 0xFE) et paquet court
        cap = new Capture(); ch = canal(c, cap);
        ch.writeInbound(Unpooled.wrappedBuffer(new byte[]{(byte) 0xFE, 0x01}));
        verifier(cap.recu.size() == 1 && ch.pipeline().get("vorace_http") == null, "ancien ping 0xFE transmis");

        verifier(Contenu.url("2001:db8::1", 25566, "ab").equals("http://[2001:db8::1]:25566/vorace/ab.zip"), "adresse IPv6 entre crochets");
        verifier("crafty.dev.pandoramc.fr".equals(Contenu.nettoyerHote("crafty.dev.pandoramc.fr.\0FML3\0")), "adresse nettoyée (point SRV, marqueur Forge)");
        System.out.println(echecs == 0 ? "TOUS LES TESTS PASSENT" : echecs + " ECHEC(S)");
        System.exit(echecs == 0 ? 0 : 1);
    }
}
