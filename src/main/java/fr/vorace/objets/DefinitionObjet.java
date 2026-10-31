package fr.vorace.objets;

import fr.vorace.blocs.DefinitionBloc;
import fr.vorace.meubles.DefinitionMeuble;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Un objet décrit dans plugins/Vorace/objets/*.yml. */
public record DefinitionObjet(
    String id, String fichier, String materiau, String nom, List<String> description,
    String texture, String modele, boolean enMain,
    Map<String, Integer> enchantements, Boolean brillance, boolean incassable, List<String> cacher,
    Integer couleur, List<Attribut> attributs, Integer taillePile, Integer durabilite, Integer cmdFixe,
    List<String> commandesDroit, List<String> commandesGauche, boolean consommer, int delaiTicks, String messageDelai,
    Recette recette, DefinitionMeuble meuble, DefinitionBloc bloc
) {

    public static final Pattern ID_VALIDE = Pattern.compile("[a-z0-9_.\\-/]+");

    public record Attribut(String nom, double valeur, String operation, String emplacement) {}

    public record Recette(String type, List<String> forme, Map<Character, String> ingredients, List<String> sansForme,
                          String entree, float experience, int temps, int quantite) {}

    /** Lit une section YAML. Les erreurs (non bloquantes) vont dans la liste « problemes ». */
    public static DefinitionObjet lire(String id, String fichier, ConfigurationSection s, List<String> problemes) {
        String materiau = s.getString("materiau", "PAPER").toLowerCase(Locale.ROOT).replace("minecraft:", "");
        Map<String, Integer> ench = new LinkedHashMap<>();
        ConfigurationSection se = s.getConfigurationSection("enchantements");
        if (se != null) for (String k : se.getKeys(false)) ench.put(k.toLowerCase(Locale.ROOT), se.getInt(k, 1));

        Integer couleur = null;
        String c = s.getString("couleur");
        if (c != null && !c.isBlank()) {
            try {
                couleur = Integer.parseInt(c.trim().replace("#", ""), 16);
            } catch (NumberFormatException e) {
                problemes.add(id + " : couleur invalide « " + c + " » (format #RRGGBB)");
            }
        }

        List<Attribut> attributs = new ArrayList<>();
        ConfigurationSection sa = s.getConfigurationSection("attributs");
        if (sa != null) {
            for (String k : sa.getKeys(false)) {
                if (sa.isConfigurationSection(k)) {
                    ConfigurationSection x = sa.getConfigurationSection(k);
                    attributs.add(new Attribut(k, x.getDouble("valeur", 0), x.getString("operation", "ajout"), x.getString("emplacement", "main")));
                } else {
                    attributs.add(new Attribut(k, sa.getDouble(k, 0), "ajout", "main"));
                }
            }
        }

        Recette recette = null;
        ConfigurationSection sr = s.getConfigurationSection("recette");
        if (sr != null) {
            Map<Character, String> ingr = new LinkedHashMap<>();
            ConfigurationSection si = sr.getConfigurationSection("ingredients");
            if (si != null) for (String k : si.getKeys(false)) if (!k.isEmpty()) ingr.put(k.charAt(0), si.getString(k));
            recette = new Recette(sr.getString("type", sr.contains("forme") ? "forme" : sr.contains("entree") ? "four" : "sans-forme").toLowerCase(Locale.ROOT),
                sr.getStringList("forme"), ingr, sr.getStringList("liste"), sr.getString("entree"),
                (float) sr.getDouble("experience", 0.1), sr.getInt("temps", 200), Math.max(1, sr.getInt("quantite", 1)));
        }

        ConfigurationSection sc = s.getConfigurationSection("commandes");
        List<String> droit = sc == null ? List.of() : sc.getStringList("clic-droit");
        List<String> gauche = sc == null ? List.of() : sc.getStringList("clic-gauche");

        String texture = nettoyer(s.getString("texture"));
        String modele = nettoyer(s.getString("modele"));
        if (texture != null && modele != null) problemes.add(id + " : « texture » et « modele » sont tous les deux remplis, le modèle est utilisé");

        return new DefinitionObjet(id, fichier, materiau,
            s.getString("nom"), s.getStringList("description"),
            modele == null ? texture : null, modele, s.getBoolean("en-main", materiau.endsWith("_sword") || materiau.endsWith("_axe")
                || materiau.endsWith("_pickaxe") || materiau.endsWith("_shovel") || materiau.endsWith("_hoe")),
            ench, s.contains("brillance") ? s.getBoolean("brillance", false) : null, s.getBoolean("incassable", false),
            s.getStringList("cacher"), couleur, attributs,
            s.contains("taille-pile") ? s.getInt("taille-pile", 64) : null,
            s.contains("durabilite") ? s.getInt("durabilite", 0) : null,
            s.contains("custom-model-data") ? s.getInt("custom-model-data", 0) : null,
            droit, gauche, sc != null && sc.getBoolean("consommer", false), sc == null ? 0 : sc.getInt("delai-ticks", 0),
            sc == null ? null : sc.getString("message-delai"),
            recette, s.isConfigurationSection("meuble") ? DefinitionMeuble.lire(id, s.getConfigurationSection("meuble"), problemes) : null,
            s.isConfigurationSection("bloc") ? DefinitionBloc.lire(id, s.getConfigurationSection("bloc"), problemes) : null);
    }

    private static String nettoyer(String chemin) {
        if (chemin == null || chemin.isBlank()) return null;
        String c = chemin.trim().replace('\\', '/');
        if (c.endsWith(".png")) c = c.substring(0, c.length() - 4);
        if (c.endsWith(".json")) c = c.substring(0, c.length() - 5);
        while (c.startsWith("/")) c = c.substring(1);
        return c;
    }

    /** L'objet a une apparence à lui dans le pack (image, modèle ou bloc). */
    public boolean aUnVisuel() {
        return texture != null || modele != null || bloc != null;
    }

    public boolean aDesCommandes() {
        return !commandesDroit.isEmpty() || !commandesGauche.isEmpty();
    }
}
