package fr.vorace.blocs;

/** Outils du jeu : type, vitesse de minage et niveau, d'après le nom de l'objet (sans Bukkit). */
public final class Outils {

    /**
     * @param type    pioche, hache, pelle, houe, epee, cisailles
     * @param vitesse vitesse de minage du jeu (bois 2, pierre 4, fer 6, diamant 8, netherite 9, or 12)
     * @param niveau  0 bois/or, 1 pierre/cuivre, 2 fer, 3 diamant, 4 netherite
     */
    public record Outil(String type, float vitesse, int niveau) {}

    private Outils() {}

    /** L'outil correspondant à un nom d'objet du jeu (« diamond_pickaxe »), ou null. */
    public static Outil lire(String materiau) {
        if (materiau == null) return null;
        String m = materiau.toLowerCase(java.util.Locale.ROOT).replace("minecraft:", "");
        if (m.equals("shears")) return new Outil("cisailles", 2f, 0);
        String type;
        if (m.endsWith("_pickaxe")) type = "pioche";
        else if (m.endsWith("_axe")) type = "hache";
        else if (m.endsWith("_shovel")) type = "pelle";
        else if (m.endsWith("_hoe")) type = "houe";
        else if (m.endsWith("_sword")) type = "epee";
        else return null;
        String matiere = m.substring(0, m.lastIndexOf('_'));
        float vitesse;
        int niveau;
        switch (matiere) {
            case "wooden" -> { vitesse = 2f; niveau = 0; }
            case "stone" -> { vitesse = 4f; niveau = 1; }
            case "copper" -> { vitesse = 5f; niveau = 1; }
            case "iron" -> { vitesse = 6f; niveau = 2; }
            case "diamond" -> { vitesse = 8f; niveau = 3; }
            case "netherite" -> { vitesse = 9f; niveau = 4; }
            case "golden" -> { vitesse = 12f; niveau = 0; }
            default -> { vitesse = 1f; niveau = 0; }
        }
        if (type.equals("epee")) vitesse = 1.5f;
        return new Outil(type, vitesse, niveau);
    }
}
