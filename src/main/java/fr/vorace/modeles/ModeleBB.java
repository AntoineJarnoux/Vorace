package fr.vorace.modeles;

import java.util.List;
import java.util.Map;

/**
 * Un modèle animé lu dans un fichier .bbmodel de Blockbench (sans Bukkit).
 * Toutes les longueurs sont en pixels de Blockbench (16 = un bloc), l'origine est au sol, au centre du modèle,
 * l'avant du modèle regarde le nord (-z) comme dans ModelEngine.
 *
 * @param parties    os du modèle dans l'ordre (un parent est toujours avant ses enfants)
 * @param textures   images du modèle
 * @param animations animations par nom (en minuscules)
 * @param largeur    largeur du modèle au sol (pixels, pour la hitbox)
 * @param hauteur    hauteur du modèle (pixels)
 */
public record ModeleBB(String id, List<Partie> parties, List<Texture> textures, Map<String, Animation> animations,
                       double largeur, double hauteur) {

    /**
     * Un os (groupe de Blockbench), ou un cube tourné de façon libre détaché dans sa propre partie.
     * @param parent    index de la partie parente, ou -1
     * @param pivot     point de rotation (absolu)
     * @param rotation  rotation de base en degrés (x, y, z ; ordre de Blockbench : x puis y puis z)
     * @param cubes     cubes affichés par cette partie (peut être vide)
     * @param echelle   réduction du modèle de cette partie pour tenir dans les limites d'un modèle d'objet (1 = aucune)
     * @param uuid      identifiant Blockbench de l'os (lien avec les animations), ou null pour un cube détaché
     */
    public record Partie(String nom, String uuid, int parent, double[] pivot, double[] rotation, List<Cube> cubes, double echelle) {
        public boolean visible() { return !cubes.isEmpty(); }

        /** Os de tête (convention de ModelEngine : nom commençant par « h_ », ou « head », « tete ») : suit le regard du mob. */
        public boolean tete() {
            String n = nom == null ? "" : nom.toLowerCase(java.util.Locale.ROOT);
            return n.startsWith("h_") || n.equals("head") || n.equals("tete") || n.equals("tête");
        }

        /** Os de nom (« tag_... ») : le nom du mob s'affiche à cet endroit. */
        public boolean etiquette() {
            return nom != null && nom.toLowerCase(java.util.Locale.ROOT).startsWith("tag_");
        }
    }

    /**
     * @param de, a      coins du cube (absolus, gonflement compris)
     * @param faces      faces par direction (north, south, east, west, up, down)
     * @param axe        axe de rotation propre au cube (x, y, z) ou null ; angle -45 à 45 par pas de 22.5
     * @param origine    centre de la rotation propre
     */
    public record Cube(double[] de, double[] a, Map<String, Face> faces, String axe, double angle, double[] origine) {}

    /** @param uv en pixels de la texture ; @param texture index dans textures() */
    public record Face(double[] uv, int texture, int rotation) {}

    /**
     * @param imageTics   durée d'une image en tics pour une texture animée (frame_time de Blockbench)
     * @param interpolee  images fondues entre elles (frame_interpolate de Blockbench)
     */
    public record Texture(String nom, byte[] png, int largeurUV, int hauteurUV, int imageTics, boolean interpolee) {
        public Texture(String nom, byte[] png, int largeurUV, int hauteurUV) { this(nom, png, largeurUV, hauteurUV, 1, false); }
    }

    /** @param boucle « loop », « once » ou « hold » */
    public record Animation(String nom, double duree, String boucle, Map<String, Canaux> os, List<Evenement> evenements) {
        public Animation(String nom, double duree, String boucle, Map<String, Canaux> os) { this(nom, duree, boucle, os, List.of()); }
    }

    /**
     * Clé d'effet d'une animation (piste « Effets » de Blockbench).
     * @param type   « sound », « particle » ou « timeline »
     * @param valeur nom du son ou de la particule, ou script de la clé « timeline »
     */
    public record Evenement(double temps, String type, String valeur) {}

    /** Clés d'un os : position, rotation, échelle (listes triées par temps). */
    public record Canaux(List<Cle> position, List<Cle> rotation, List<Cle> echelle) {}

    /** @param interpolation linear, catmullrom ou step */
    /** @param expressions expressions Molang de x, y, z (null quand ce sont des nombres) */
    public record Cle(double temps, double x, double y, double z, String interpolation, Molang[] expressions) {
        public Cle(double temps, double x, double y, double z, String interpolation) { this(temps, x, y, z, interpolation, null); }
    }
}
