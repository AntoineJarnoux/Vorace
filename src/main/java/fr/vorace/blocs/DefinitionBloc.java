package fr.vorace.blocs;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Section « bloc: » d'un objet Vorace : l'objet devient un bloc plein posable.
 *
 * @param faces         textures par face (« tout » pour une seule texture)
 * @param modele        modèle Blockbench d'un bloc plein (à la place des textures)
 * @param durete        dureté comme dans le jeu (terre 0.5, pierre 1.5, minerai de fer 3, obsidienne 50)
 * @param outil         pioche, hache, pelle, houe, epee, cisailles ou aucun
 * @param niveau        niveau d'outil minimum pour récupérer le butin : 0 aucun/bois/or, 1 pierre, 2 fer, 3 diamant, 4 netherite
 * @param butin         objets lâchés (vide = le bloc lui-même)
 * @param fortune       Fortune multiplie le butin
 * @param toucherDeSoie Toucher de soie rend le bloc lui-même
 * @param xpMin         expérience lâchée (minimum)
 * @param xpMax         expérience lâchée (maximum)
 * @param sonPose       son à la pose
 * @param sonCasse      son au cassage
 * @param generation    génération dans les nouveaux chunks, ou null
 */
public record DefinitionBloc(
    Map<String, String> faces, String modele, double durete, String outil, int niveau, List<Butin> butin,
    boolean fortune, boolean toucherDeSoie, int xpMin, int xpMax, String sonPose, String sonCasse, Generation generation
) {

    /** « vorace:rubis 1-2 50% » ou « DIAMOND 1 ». */
    public record Butin(String objet, int min, int max, double chance) {
        public static Butin lire(String texte) {
            String[] p = texte.trim().split("\\s+");
            if (p.length == 0 || p[0].isBlank()) throw new IllegalArgumentException("butin vide");
            int min = 1, max = 1;
            double chance = 1;
            for (int i = 1; i < p.length; i++) {
                String x = p[i];
                if (x.endsWith("%")) {
                    chance = Double.parseDouble(x.substring(0, x.length() - 1).replace(',', '.')) / 100.0;
                } else {
                    int[] q = intervalle(x);
                    min = q[0];
                    max = q[1];
                }
            }
            return new Butin(p[0], min, max, Math.max(0, Math.min(1, chance)));
        }
    }

    /** Filons générés dans les nouveaux chunks. */
    public record Generation(List<String> mondes, int yMin, int yMax, int filons, int taille, List<String> remplace) {}

    public static int[] intervalle(String texte) {
        String t = texte.trim();
        int tiret = t.indexOf('-', 1); // « -5 » est un nombre négatif, « 1-3 » un intervalle
        if (tiret > 0) {
            int a = Integer.parseInt(t.substring(0, tiret).trim()), b = Integer.parseInt(t.substring(tiret + 1).trim());
            return new int[]{Math.min(a, b), Math.max(a, b)};
        }
        int v = Integer.parseInt(t);
        return new int[]{v, v};
    }

    public static int niveau(String texte) {
        if (texte == null) return 0;
        return switch (texte.trim().toLowerCase(Locale.ROOT)) {
            case "pierre", "stone", "cuivre", "copper", "1" -> 1;
            case "fer", "iron", "2" -> 2;
            case "diamant", "diamond", "3" -> 3;
            case "netherite", "4" -> 4;
            default -> 0;
        };
    }

    public static DefinitionBloc lire(String id, ConfigurationSection s, List<String> problemes) {
        Map<String, String> faces = new LinkedHashMap<>();
        if (s.isConfigurationSection("textures")) {
            ConfigurationSection t = s.getConfigurationSection("textures");
            for (String k : t.getKeys(false)) {
                String face = switch (k.toLowerCase(Locale.ROOT)) {
                    case "haut", "dessus", "top" -> "haut";
                    case "bas", "dessous", "bottom" -> "bas";
                    case "cotes", "côtés", "cote", "sides" -> "cotes";
                    case "nord", "north", "avant" -> "nord";
                    case "sud", "south", "arriere" -> "sud";
                    case "est", "east" -> "est";
                    case "ouest", "west" -> "ouest";
                    case "tout", "all" -> "tout";
                    default -> null;
                };
                if (face == null) problemes.add(id + " : face de bloc inconnue « " + k + " » (haut, bas, cotes, nord, sud, est, ouest)");
                else faces.put(face, nettoyer(t.getString(k)));
            }
        } else if (s.getString("textures") != null) {
            faces.put("tout", nettoyer(s.getString("textures")));
        } else if (s.getString("texture") != null) {
            faces.put("tout", nettoyer(s.getString("texture")));
        }
        faces.values().removeIf(v -> v == null);
        String modele = nettoyer(s.getString("modele"));

        String outil = s.getString("outil", "aucun").trim().toLowerCase(Locale.ROOT);
        outil = switch (outil) {
            case "pioche", "pickaxe" -> "pioche";
            case "hache", "axe" -> "hache";
            case "pelle", "shovel" -> "pelle";
            case "houe", "hoe" -> "houe";
            case "epee", "épée", "sword" -> "epee";
            case "cisailles", "shears" -> "cisailles";
            case "aucun", "main", "none" -> "aucun";
            default -> {
                problemes.add(id + " : outil inconnu « " + outil + " » (pioche, hache, pelle, houe, epee, cisailles, aucun)");
                yield "aucun";
            }
        };

        List<Butin> butin = new ArrayList<>();
        for (String b : s.getStringList("butin")) {
            try {
                butin.add(Butin.lire(b));
            } catch (RuntimeException e) {
                problemes.add(id + " : butin invalide « " + b + " » (exemple : « vorace:rubis 1-2 » ou « DIAMOND 1 50% »)");
            }
        }

        int xpMin = 0, xpMax = 0;
        String xp = s.getString("experience");
        if (xp != null) {
            try {
                int[] q = intervalle(xp);
                xpMin = Math.max(0, q[0]);
                xpMax = Math.max(0, q[1]);
            } catch (RuntimeException e) {
                problemes.add(id + " : experience invalide « " + xp + " » (exemple : « 3-7 »)");
            }
        }

        Generation gen = null;
        ConfigurationSection g = s.getConfigurationSection("generation");
        if (g != null) {
            int yMin = -64, yMax = 64;
            String h = g.getString("hauteur");
            if (h != null) {
                try {
                    String[] p = h.split("\\s*(,|\\.\\.|à)\\s*");
                    yMin = Integer.parseInt(p[0].trim());
                    yMax = Integer.parseInt(p[p.length - 1].trim());
                } catch (RuntimeException e) {
                    problemes.add(id + " : hauteur de génération invalide « " + h + " » (exemple : « -64, 32 »)");
                }
            }
            List<String> remplace = g.getStringList("remplace");
            if (remplace.isEmpty()) remplace = List.of("STONE", "DEEPSLATE", "TUFF", "GRANITE", "DIORITE", "ANDESITE");
            gen = new Generation(g.getStringList("mondes"), Math.min(yMin, yMax), Math.max(yMin, yMax),
                Math.max(0, Math.min(64, g.getInt("filons", 4))), Math.max(1, Math.min(32, g.getInt("taille", 5))), remplace);
        }

        double durete = Math.max(0, s.getDouble("durete", 1.5));
        return new DefinitionBloc(faces, modele, durete, outil, niveau(s.getString("niveau")), butin,
            s.getBoolean("fortune", !butin.isEmpty()), s.getBoolean("toucher-de-soie", !butin.isEmpty()), xpMin, xpMax,
            s.getString("son-pose", "block.stone.place"), s.getString("son-casse", "block.stone.break"), gen);
    }

    private static String nettoyer(String chemin) {
        if (chemin == null || chemin.isBlank()) return null;
        String c = chemin.trim().replace('\\', '/');
        if (c.endsWith(".png")) c = c.substring(0, c.length() - 4);
        if (c.endsWith(".json")) c = c.substring(0, c.length() - 5);
        while (c.startsWith("/")) c = c.substring(1);
        return c;
    }
}
