package fr.vorace.objets;

/**
 * Ce qu'il faut pour dessiner un objet dans le pack (sans rien de Bukkit, pour pouvoir le tester).
 *
 * @param id       identifiant de l'objet (a-z, 0-9, _ - . /)
 * @param materiau objet Minecraft de base, en minuscules (paper, diamond_sword...)
 * @param texture  chemin dans ressources/textures/ sans ".png", ou null
 * @param modele   chemin dans ressources/modeles/ sans ".json", ou null
 * @param enMain   modèle plat tenu comme une épée (item/handheld) au lieu de item/generated
 * @param cmd      numéro custom_model_data (utilisé seulement avant 1.21.2)
 * @param teinte   couleur par défaut si l'objet est teintable (cuir), ou null
 * @param parent   modèle déjà fourni ailleurs (ex. « vorace:block/minerai » pour un bloc), ou null
 */
public record Visuel(String id, String materiau, String texture, String modele, boolean enMain, int cmd, Integer teinte, String parent) {
    public Visuel(String id, String materiau, String texture, String modele, boolean enMain, int cmd, Integer teinte) {
        this(id, materiau, texture, modele, enMain, cmd, teinte, null);
    }
}
