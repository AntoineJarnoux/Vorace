package fr.vorace.blocs;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Les blocs personnalisés pleins sont des blocs à note dans un état précis (instrument + note).
 * Chaque bloc personnalisé reçoit un numéro, qui donne un état. Les deux états « alimenté » et « non alimenté »
 * montrent le même modèle : un signal de redstone ne change donc pas l'apparence.
 *
 * Les instruments des têtes de mobs viennent en premier : un bloc à note normal ne les prend que si une tête
 * est posée dessus, donc les blocs à note déjà présents dans les mondes ne changent presque jamais d'apparence.
 */
public final class EtatsNoteBlock {

    /** Ordre d'attribution (les plus rares d'abord). La harpe avec la note 0 (état par défaut) n'est jamais utilisée. */
    public static final List<String> INSTRUMENTS = List.of(
        "custom_head", "piglin", "wither_skeleton", "dragon", "creeper", "skeleton", "zombie",
        "pling", "banjo", "bit", "didgeridoo", "cow_bell", "iron_xylophone", "xylophone", "chime",
        "guitar", "bell", "flute", "bass", "hat", "snare", "basedrum", "harp");

    public static final int NOTES = 25;
    /** Nombre de blocs personnalisés possibles. */
    public static final int CAPACITE = (INSTRUMENTS.size() - 1) * NOTES;

    private static final Pattern ETAT = Pattern.compile("instrument=([a-z_]+).*?note=(\\d+)");

    private EtatsNoteBlock() {}

    /** « instrument=banjo,note=3 » pour un numéro. */
    public static String etat(int numero) {
        if (numero < 0 || numero >= CAPACITE) throw new IllegalArgumentException("numéro de bloc hors limites : " + numero);
        return "instrument=" + INSTRUMENTS.get(numero / NOTES) + ",note=" + (numero % NOTES);
    }

    /** Données de bloc complètes, pour Bukkit.createBlockData. */
    public static String donnees(int numero) {
        return "minecraft:note_block[" + etat(numero) + ",powered=false]";
    }

    /** Clé « instrument=x,note=n » lue dans un texte de données de bloc, ou null. */
    public static String cle(String donneesBloc) {
        if (donneesBloc == null || !donneesBloc.contains("note_block")) return null;
        Matcher m = ETAT.matcher(donneesBloc);
        return m.find() ? "instrument=" + m.group(1) + ",note=" + Integer.parseInt(m.group(2)) : null;
    }
}
