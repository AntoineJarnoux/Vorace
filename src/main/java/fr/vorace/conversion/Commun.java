package fr.vorace.conversion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Conversions communes à tous les plugins (enchantements, attributs, couleurs, outils...). */
final class Commun {

    private Commun() {}

    /** Drapeaux Bukkit (HIDE_ENCHANTS...) vers les mots de « cacher: ». */
    static List<String> cacher(List<String> drapeaux) {
        List<String> r = new ArrayList<>();
        for (String d : drapeaux) {
            String c = switch (d.toUpperCase(Locale.ROOT).replace("HIDE_", "")) {
                case "ENCHANTS" -> "enchantements";
                case "ATTRIBUTES" -> "attributs";
                case "UNBREAKABLE" -> "incassable";
                case "DYE" -> "teinture";
                case "ADDITIONAL_TOOLTIP", "POTION_EFFECTS", "ITEM_SPECIFICS" -> "infos";
                case "ARMOR_TRIM" -> "garniture";
                default -> null;
            };
            if (c != null && !r.contains(c)) r.add(c);
        }
        return r;
    }

    /** « 3, 252, 136 », « #03fc88 », « 0x03fc88 » ou un nombre -> « #03fc88 ». */
    static String couleur(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        try {
            if (s.startsWith("#")) return s.length() == 7 ? s.toLowerCase(Locale.ROOT) : null;
            if (s.toLowerCase(Locale.ROOT).startsWith("0x")) return String.format("#%06x", Integer.parseInt(s.substring(2), 16) & 0xFFFFFF);
            String[] p = s.split("\\s*,\\s*");
            if (p.length >= 3) return String.format("#%02x%02x%02x", Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()));
            return String.format("#%06x", Integer.parseInt(s) & 0xFFFFFF);
        } catch (RuntimeException e) {
            return null;
        }
    }

    static String emplacement(String slot) {
        if (slot == null) return "main";
        return switch (slot.toUpperCase(Locale.ROOT).replace("_", "")) {
            case "MAINHAND", "HAND" -> "main";
            case "OFFHAND" -> "main-gauche";
            case "HEAD", "HELMET" -> "tete";
            case "CHEST", "CHESTPLATE" -> "torse";
            case "LEGS", "LEGGINGS" -> "jambes";
            case "FEET", "BOOTS" -> "pieds";
            case "ARMOR" -> "armure";
            default -> "partout";
        };
    }

    static String operation(String op) {
        if (op == null) return "ajout";
        return switch (op.toUpperCase(Locale.ROOT)) {
            case "ADD_SCALAR", "ADD_MULTIPLIED_BASE", "MULTIPLY_BASE" -> "pourcentage";
            case "MULTIPLY_SCALAR_1", "ADD_MULTIPLIED_TOTAL", "MULTIPLY_TOTAL" -> "multiplier";
            default -> "ajout";
        };
    }

    /** Nom d'attribut (GENERIC_ATTACK_DAMAGE, attackDamage, minecraft:generic.attack_damage...) -> nom du jeu. */
    static String attribut(String nom) {
        String n = nom.trim().replace("minecraft:", "");
        // attackDamage -> attack_damage
        n = n.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
        n = n.replace("generic.", "").replace("player.", "").replace("generic_", "").replace("player_", "");
        return n;
    }

    /** Ajoute un attribut à la section « attributs » (une clé par attribut, la deuxième prend un suffixe). */
    static void ajouterAttribut(Map<String, Object> attributs, String nom, double valeur, String operation, String emplacement) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("valeur", valeur);
        a.put("operation", operation);
        a.put("emplacement", emplacement);
        String cle = nom;
        int i = 2;
        while (attributs.containsKey(cle)) cle = nom + "_" + (i++);
        attributs.put(cle, a);
    }

    /** Niveau d'outil (WOODEN, STONE, IRON, GOLDEN, DIAMOND, NETHERITE) -> mot de Vorace. */
    static String niveau(String n) {
        if (n == null) return null;
        String s = n.toUpperCase(Locale.ROOT);
        if (s.contains("NETHERITE")) return "netherite";
        if (s.contains("DIAMOND")) return "diamant";
        if (s.contains("IRON")) return "fer";
        if (s.contains("STONE")) return "pierre";
        if (s.contains("WOOD") || s.contains("GOLD")) return "bois";
        return null;
    }

    /** Outil (PICKAXE, AXE...) -> mot de Vorace. */
    static String outil(String n) {
        if (n == null) return null;
        String s = n.toUpperCase(Locale.ROOT);
        if (s.contains("PICKAXE")) return "pioche";
        if (s.contains("AXE")) return "hache";
        if (s.contains("SHOVEL") || s.contains("SPADE")) return "pelle";
        if (s.contains("HOE")) return "houe";
        if (s.contains("SWORD")) return "epee";
        if (s.contains("SHEARS")) return "cisailles";
        return null;
    }

    /** Enchantement (« DAMAGE_ALL », « sharpness », « minecraft:sharpness ») -> nom du jeu, ou null pour un autre plugin. */
    static String enchantement(String n) {
        String s = n.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("minecraft:")) s = s.substring(10);
        if (s.contains(":")) return null;
        return switch (s) {
            case "arrow_fire" -> "flame";
            case "arrow_damage" -> "power";
            case "arrow_knockback" -> "punch";
            case "arrow_infinite" -> "infinity";
            case "damage_all" -> "sharpness";
            case "damage_undead" -> "smite";
            case "damage_arthropods" -> "bane_of_arthropods";
            case "dig_speed" -> "efficiency";
            case "durability" -> "unbreaking";
            case "loot_bonus_blocks" -> "fortune";
            case "loot_bonus_mobs" -> "looting";
            case "luck" -> "luck_of_the_sea";
            case "oxygen" -> "respiration";
            case "water_worker" -> "aqua_affinity";
            case "protection_environmental" -> "protection";
            case "protection_fire" -> "fire_protection";
            case "protection_fall" -> "feather_falling";
            case "protection_explosions" -> "blast_protection";
            case "protection_projectile" -> "projectile_protection";
            case "sweeping_edge", "sweeping" -> "sweeping_edge";
            default -> s;
        };
    }

    /** Commandes d'un autre plugin -> « console: ... » / « joueur: ... » avec %joueur%. */
    static String commande(String prefixe, String cmd) {
        String c = cmd.trim();
        if (c.startsWith("/")) c = c.substring(1);
        c = c.replace("%p%", "%joueur%").replace("%player%", "%joueur%").replace("{player}", "%joueur%").replace("%player_name%", "%joueur%");
        return prefixe + ": " + c;
    }
}
