package fr.vorace.paquets;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Teste la couche paquets dans une connexion Netty simulée (paquets imités, mêmes formes que ceux du jeu). */
public final class TestPaquets {
    static int echecs = 0;
    static void verifier(boolean ok, String quoi) { System.out.println((ok ? "OK    " : "ECHEC ") + quoi); if (!ok) echecs++; }

    public static void main(String[] args) throws Exception {
        // champs et records
        ClientboundSetTimePacket t = new ClientboundSetTimePacket(10, 1000, true);
        verifier(Long.valueOf(1000).equals(Reflexion.lire(t, "dayTime")), "lecture d'un champ de record");
        Object t2 = Reflexion.avec(t, "dayTime", 6000L);
        verifier(t2 instanceof ClientboundSetTimePacket r && r.dayTime() == 6000 && r.gameTime() == 10 && r.tickDayTime(), "record recréé avec le champ changé");
        ServerboundChatPacket c = new ServerboundChatPacket("salut", 5);
        verifier(Reflexion.avec(c, "message", "bonjour") == c && c.message().equals("bonjour"), "champ final d'une classe modifié sur place");
        Map<String, Object> champs = Reflexion.champs(t);
        verifier(List.copyOf(champs.keySet()).equals(List.of("gameTime", "dayTime", "tickDayTime")), "liste des champs dans l'ordre");
        boolean erreur = false;
        try { Reflexion.lire(t, "inconnu"); } catch (IllegalArgumentException e) { erreur = e.getMessage().contains("dayTime"); }
        verifier(erreur, "champ inconnu : erreur qui liste les champs existants");

        // connexion simulée
        Plugin faux = (Plugin) Proxy.newProxyInstance(TestPaquets.class.getClassLoader(), new Class<?>[]{Plugin.class},
            (o, m, a) -> m.getName().equals("getName") ? "Essai" : null);
        Paquets pq = new Paquets(null);
        EmbeddedChannel ch = new EmbeddedChannel();
        ch.pipeline().addLast(Paquets.NOM_GESTIONNAIRE, pq.pourEssai(ch));

        ch.writeOutbound(new ClientboundSetTimePacket(1, 2, false));
        verifier(ch.readOutbound() instanceof ClientboundSetTimePacket r && r.dayTime() == 2, "sans écoute : paquet inchangé");

        AtomicInteger vus = new AtomicInteger();
        Abonnement heure = pq.ecouter(faux, Sens.SORTANT, p -> { vus.incrementAndGet(); p.ecrire("dayTime", 6000L); }, "ClientboundSetTimePacket");
        ch.writeOutbound(new ClientboundSetTimePacket(1, 2, false));
        verifier(ch.readOutbound() instanceof ClientboundSetTimePacket r && r.dayTime() == 6000 && vus.get() == 1, "paquet sortant modifié");

        pq.ecouter(faux, Sens.ENTRANT, p -> { if ("secret".equals(p.lire("message"))) p.annuler(); }, "ServerboundChatPacket");
        ch.writeInbound(new ServerboundChatPacket("secret", 1));
        verifier(ch.readInbound() == null, "paquet entrant annulé");
        ch.writeInbound(new ServerboundChatPacket("bonjour", 1));
        verifier(ch.readInbound() instanceof ServerboundChatPacket r && r.message().equals("bonjour"), "autre paquet entrant laissé passer");

        // lot : le paquet d'heure à l'intérieur est modifié, le reste gardé
        List<Object> sous = new ArrayList<>(List.of(new ClientboundSetTimePacket(1, 2, false), new ServerboundChatPacket("x", 0)));
        ch.writeOutbound(new ClientboundBundlePacket(sous));
        Object lot = ch.readOutbound();
        List<Object> apres = new ArrayList<>();
        if (lot instanceof ClientboundBundlePacket b) b.subPackets().forEach(apres::add);
        verifier(apres.size() == 2 && apres.get(0) instanceof ClientboundSetTimePacket r && r.dayTime() == 6000, "paquet modifié à l'intérieur d'un lot");

        // une écoute qui plante ne bloque pas le paquet
        pq.ecouter(faux, Sens.SORTANT, p -> { throw new IllegalStateException("bug d'un plugin"); }, "ClientboundSetTimePacket");
        ch.writeOutbound(new ClientboundSetTimePacket(1, 2, false));
        verifier(ch.readOutbound() instanceof ClientboundSetTimePacket r && r.dayTime() == 6000, "une écoute en erreur ne bloque ni le paquet ni les autres écoutes");

        // variantes : nom complet, et l'écoute du paquet parent les reçoit
        verifier(Reflexion.nom(net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos.class).equals("ServerboundMovePlayerPacket.Pos"), "nom complet d'une variante");
        AtomicInteger mouvements = new AtomicInteger();
        Abonnement mv = pq.ecouter(faux, Sens.ENTRANT, p -> { mouvements.incrementAndGet(); p.ecrire("x", 5.0); }, "ServerboundMovePlayerPacket");
        ch.writeInbound(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos(1.0));
        Object mvt = ch.readInbound();
        verifier(mouvements.get() == 1 && Double.valueOf(5.0).equals(Reflexion.lire(mvt, "x")), "l'écoute du paquet parent reçoit et modifie la variante (champ du parent)");
        mv.annuler();

        heure.annuler();
        verifier(pq.abonnements().size() == 2, "abonnement annulé");
        ch.writeOutbound("pas un paquet");
        verifier("pas un paquet".equals(ch.readOutbound()), "objets qui ne sont pas des paquets laissés tels quels");

        System.out.println(echecs == 0 ? "TOUS LES TESTS PASSENT" : echecs + " ECHEC(S)");
        System.exit(echecs == 0 ? 0 : 1);
    }
}
