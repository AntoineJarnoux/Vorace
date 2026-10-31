package fr.vorace;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;

/** Messages de config.yml (section "messages"), au format MiniMessage : <red>, <gradient:...>, <bold>... */
public final class Messages {

    private static final Map<String, String> DEFAUTS = Map.of(
        "prefixe", "<gradient:#7b2ff7:#f107a3><bold>Vorace</bold></gradient> <dark_gray>»</dark_gray> ",
        "invite", "<gray>Ce serveur utilise un pack de ressources pour ses objets, menus et modèles 3D.",
        "refuse", "<yellow>Tu as refusé le pack : certains objets et menus s'afficheront mal. <gray>Tape <white>/vorace pack</white> pour le recevoir.",
        "echec", "<red>Le pack n'a pas pu être téléchargé. <gray>Tape <white>/vorace pack</white> pour réessayer.",
        "expulsion-refus", "<red>Le pack de ressources est obligatoire sur ce serveur.\n<gray>Accepte-le en te reconnectant (ou active-le dans Multijoueur > Modifier > Packs de ressources)."
    );

    private static final Map<String, String> textes = new HashMap<>(DEFAUTS);
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Messages() {}

    public static void charger(FileConfiguration c) {
        textes.clear();
        textes.putAll(DEFAUTS);
        for (String cle : DEFAUTS.keySet()) {
            String v = c.getString("messages." + cle);
            if (v != null) textes.put(cle, v);
        }
    }

    public static Component composant(String cle) {
        return MM.deserialize(textes.getOrDefault(cle, cle));
    }

    public static Component prefixe(String cle) {
        return MM.deserialize(textes.get("prefixe") + textes.getOrDefault(cle, cle));
    }

    /** Texte libre (MiniMessage) précédé du préfixe. */
    public static Component brut(String miniMessage) {
        return MM.deserialize(textes.get("prefixe") + miniMessage);
    }

    /** Texte d'objet ou de menu : MiniMessage, sans l'italique que le jeu ajoute aux noms. */
    public static Component objet(String miniMessage) {
        return MM.deserialize(miniMessage).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static String echapper(String s) {
        return MM.escapeTags(s);
    }
}
