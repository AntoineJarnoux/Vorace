package fr.vorace.meubles;

import fr.vorace.meubles.Geometrie.Point;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Section « meuble: » d'un objet Vorace : l'objet devient un meuble posable.
 *
 * @param blocs        blocs solides (barrières) occupés, en coordonnées locales ; vide = pas de hitbox en blocs
 * @param largeur      hitbox « entité » (non solide) : largeur, 0 = aucune
 * @param hauteur      hitbox « entité » : hauteur
 * @param sieges       positions des sièges (hauteur = surface d'assise)
 * @param directions   4, 8, ou 0 (rotation libre)
 * @param sol          posable au sol
 * @param mur          posable contre un mur
 * @param plafond      posable au plafond
 * @param lumiere      niveau de lumière émis (0 à 15)
 * @param taille       échelle du modèle
 * @param decalage     décalage du modèle par rapport au centre du bloc
 * @param rotationModele rotation supplémentaire du modèle (si le modèle a été fait à l'envers)
 * @param luminosite   luminosité forcée du modèle (-1 = celle de l'endroit)
 * @param stockage     lignes de coffre (0 = pas de stockage)
 * @param titreStockage titre du coffre (MiniMessage)
 * @param sonPose      son joué à la pose
 * @param sonCasse     son joué au cassage
 * @param butin        rend l'objet quand on casse le meuble (hors créatif)
 * @param commandes    commandes exécutées au clic droit sur le meuble
 */
public record DefinitionMeuble(
    List<int[]> blocs, float largeur, float hauteur, List<Point> sieges, int directions,
    boolean sol, boolean mur, boolean plafond, int lumiere, float taille, Point decalage, float rotationModele,
    int luminosite, int stockage, String titreStockage, String sonPose, String sonCasse, boolean butin, List<String> commandes
) {

    public boolean aHitboxBlocs() { return !blocs.isEmpty(); }
    public boolean aHitboxEntite() { return largeur > 0 && hauteur > 0; }
    public boolean interactif() { return !sieges.isEmpty() || stockage > 0 || !commandes.isEmpty(); }

    public static DefinitionMeuble lire(String id, ConfigurationSection s, List<String> problemes) {
        List<int[]> blocs = new ArrayList<>();
        float largeur = 0, hauteur = 0;
        Object hb = s.get("hitbox");
        if (hb instanceof ConfigurationSection h) {
            // hitbox: { largeur: 1, hauteur: 1 } -> entité non solide
            largeur = (float) h.getDouble("largeur", 1);
            hauteur = (float) h.getDouble("hauteur", 1);
            for (String b : h.getStringList("blocs")) ajouterBloc(id, b, blocs, problemes);
        } else if (hb instanceof List<?> liste) {
            for (Object b : liste) ajouterBloc(id, String.valueOf(b), blocs, problemes);
        } else if (hb instanceof String t) {
            String v = t.trim().toLowerCase(Locale.ROOT);
            if (!v.equals("aucune") && !v.equals("non") && !v.equals("false")) ajouterBloc(id, t, blocs, problemes);
        } else if (hb == null) {
            blocs.add(new int[]{0, 0, 0}); // par défaut : un bloc solide
        }

        List<Point> sieges = new ArrayList<>();
        for (String p : s.getStringList("sieges")) {
            try {
                sieges.add(Point.lire(p));
            } catch (IllegalArgumentException e) {
                problemes.add(id + " : siège " + e.getMessage());
            }
        }

        String rot = s.getString("rotation", "8").trim().toLowerCase(Locale.ROOT);
        int directions = switch (rot) {
            case "libre", "0", "360" -> 0;
            case "4" -> 4;
            case "16" -> 16;
            default -> 8;
        };

        List<String> pose = s.getStringList("pose");
        if (pose.isEmpty() && s.getString("pose") != null) pose = List.of(s.getString("pose"));
        boolean sol = pose.isEmpty(), mur = false, plafond = false;
        for (String p : pose) {
            switch (p.trim().toLowerCase(Locale.ROOT)) {
                case "sol" -> sol = true;
                case "mur" -> mur = true;
                case "plafond" -> plafond = true;
                default -> problemes.add(id + " : pose inconnue « " + p + " » (sol, mur, plafond)");
            }
        }

        Point decalage = Point.ZERO;
        try {
            decalage = Point.lire(s.getString("decalage"));
        } catch (IllegalArgumentException e) {
            problemes.add(id + " : decalage " + e.getMessage());
        }

        int lumiere = Math.max(0, Math.min(15, s.getInt("lumiere", 0)));
        int luminosite = s.contains("luminosite") ? Math.max(0, Math.min(15, s.getInt("luminosite", 15))) : -1;
        int stockage = Math.max(0, Math.min(6, s.getInt("stockage", 0)));

        return new DefinitionMeuble(blocs, largeur, hauteur, sieges, directions, sol, mur, plafond, lumiere,
            (float) Math.max(0.01, s.getDouble("taille", 1.0)), decalage, (float) s.getDouble("rotation-modele", 0),
            luminosite, stockage, s.getString("titre-stockage"),
            s.getString("son-pose", "block.wood.place"), s.getString("son-casse", "block.wood.break"),
            s.getBoolean("butin", true), s.getStringList("commandes"));
    }

    private static void ajouterBloc(String id, String texte, List<int[]> blocs, List<String> problemes) {
        try {
            blocs.add(Point.lire(texte).bloc());
        } catch (IllegalArgumentException e) {
            problemes.add(id + " : hitbox " + e.getMessage());
        }
    }

    // les tableaux n'ont pas d'equals/hashCode utiles : on compare leur contenu (sert à détecter un meuble modifié)
    @Override
    public boolean equals(Object o) {
        return o instanceof DefinitionMeuble d && signature().equals(d.signature());
    }

    @Override
    public int hashCode() {
        return signature().hashCode();
    }

    private String signature() {
        StringBuilder sb = new StringBuilder();
        for (int[] b : blocs) sb.append(b[0]).append(',').append(b[1]).append(',').append(b[2]).append(';');
        return sb + "|" + largeur + "|" + hauteur + "|" + sieges + "|" + directions + "|" + sol + mur + plafond + "|" + lumiere
            + "|" + taille + "|" + decalage + "|" + rotationModele + "|" + luminosite + "|" + stockage + "|" + titreStockage
            + "|" + sonPose + "|" + sonCasse + "|" + butin + "|" + commandes;
    }
}
