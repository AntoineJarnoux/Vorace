package fr.vorace.objets;

import fr.vorace.Messages;
import fr.vorace.Vorace;
import fr.vorace.pack.FormatPack;
import fr.vorace.pack.Journal;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/** Charge les objets, fabrique les ItemStack, enregistre les recettes. */
public final class Objets {

    private final Vorace plugin;
    private final NamespacedKey cleId;
    private final NamespacedKey cleVersion;
    private volatile Map<String, DefinitionObjet> definitions = Map.of();
    private final Map<String, Integer> cmdAttribues = new LinkedHashMap<>();
    private final List<NamespacedKey> recettes = new ArrayList<>();
    private Method setItemModel;
    private boolean itemModelCherche;

    public Objets(Vorace plugin) {
        this.plugin = plugin;
        this.cleId = new NamespacedKey(plugin, "id");
        this.cleVersion = new NamespacedKey(plugin, "version");
    }

    public Path dossierObjets() { return plugin.getDataFolder().toPath().resolve("objets"); }
    public Path dossierRessources() { return plugin.getDataFolder().toPath().resolve("ressources"); }
    public Collection<DefinitionObjet> toutes() { return definitions.values(); }
    public DefinitionObjet definition(String id) { return definitions.get(id); }

    /** Charge tous les .yml de plugins/Vorace/objets (sous-dossiers compris). */
    public void charger(boolean avecRecettes) {
        installerExemple();
        chargerCmd();
        Map<String, DefinitionObjet> lues = new LinkedHashMap<>();
        List<String> problemes = new ArrayList<>();
        List<Path> fichiers;
        try (Stream<Path> f = Files.walk(dossierObjets())) {
            fichiers = f.filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".yml")).sorted().toList();
        } catch (IOException e) {
            fichiers = List.of();
        }
        for (Path p : fichiers) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(p.toFile());
            String nomFichier = dossierObjets().relativize(p).toString().replace('\\', '/');
            for (String id : yml.getKeys(false)) {
                if (!yml.isConfigurationSection(id)) continue;
                String idPropre = id.toLowerCase(Locale.ROOT);
                if (!DefinitionObjet.ID_VALIDE.matcher(idPropre).matches()) {
                    problemes.add(nomFichier + " : identifiant invalide « " + id + " » (lettres minuscules, chiffres, _ - . /)");
                    continue;
                }
                if (lues.containsKey(idPropre)) problemes.add(nomFichier + " : « " + idPropre + " » existe déjà dans " + lues.get(idPropre).fichier() + ", remplacé");
                DefinitionObjet d = DefinitionObjet.lire(idPropre, nomFichier, yml.getConfigurationSection(id), problemes);
                if (Material.matchMaterial(d.materiau()) == null) {
                    problemes.add(idPropre + " : matériau inconnu « " + d.materiau() + " », PAPER utilisé");
                }
                lues.put(idPropre, d);
            }
        }
        definitions = lues;
        for (DefinitionObjet d : lues.values()) cmd(d);
        sauverCmd();
        problemes.forEach(m -> plugin.getLogger().warning("Objets : " + m));
        plugin.getLogger().info(lues.size() + " objet(s) chargé(s) depuis " + fichiers.size() + " fichier(s)");
        if (avecRecettes) enregistrerRecettes();
    }

    /** Fichiers pour le pack (appelé hors du fil principal pendant la construction). */
    public Map<String, byte[]> fichiersPack(FormatPack format, Journal journal) {
        List<Visuel> visuels = new ArrayList<>();
        for (DefinitionObjet d : definitions.values()) {
            Integer teinte = d.couleur() != null && d.materiau().startsWith("leather_") ? (0xFF000000 | d.couleur()) : null;
            visuels.add(new Visuel(d.id(), materiauValide(d).name().toLowerCase(Locale.ROOT), d.texture(), d.modele(), d.enMain(), cmd(d), teinte));
        }
        return GenerateurPack.generer(visuels, dossierRessources(), format, journal);
    }

    // ----------------------------------------------------------------- objets

    public ItemStack fabriquer(String id, int quantite) {
        DefinitionObjet d = definitions.get(id);
        return d == null ? null : fabriquer(d, quantite);
    }

    public ItemStack fabriquer(DefinitionObjet d, int quantite) {
        ItemStack objet = new ItemStack(materiauValide(d), Math.max(1, quantite));
        ItemMeta meta = objet.getItemMeta();
        if (meta == null) return objet;
        if (d.nom() != null) meta.displayName(Messages.objet(d.nom()));
        if (!d.description().isEmpty()) {
            List<Component> lore = new ArrayList<>();
            for (String l : d.description()) lore.add(Messages.objet(l));
            meta.lore(lore);
        }
        meta.getPersistentDataContainer().set(cleId, PersistentDataType.STRING, d.id());
        meta.getPersistentDataContainer().set(cleVersion, PersistentDataType.STRING, Integer.toHexString(d.hashCode()));

        if (d.texture() != null || d.modele() != null) {
            FormatPack f = plugin.packs().format();
            if (f != null && f.majeur() >= 42 && appliquerItemModel(meta, d.id())) {
                // composant item_model : rien d'autre à faire
            } else {
                meta.setCustomModelData(cmd(d));
            }
        } else if (d.cmdFixe() != null) {
            meta.setCustomModelData(d.cmdFixe());
        }

        for (Map.Entry<String, Integer> e : d.enchantements().entrySet()) {
            Enchantment ench = enchantement(e.getKey());
            if (ench != null) meta.addEnchant(ench, e.getValue(), true);
            else plugin.getLogger().warning("Objet " + d.id() + " : enchantement inconnu « " + e.getKey() + " »");
        }
        if (d.brillance() != null) meta.setEnchantmentGlintOverride(d.brillance());
        if (d.incassable()) meta.setUnbreakable(true);
        for (String f : d.cacher()) {
            ItemFlag flag = drapeau(f);
            if (flag != null) meta.addItemFlags(flag);
        }
        if (d.couleur() != null) {
            if (meta instanceof LeatherArmorMeta cuir) cuir.setColor(Color.fromRGB(d.couleur() & 0xFFFFFF));
            else if (meta instanceof PotionMeta potion) potion.setColor(Color.fromRGB(d.couleur() & 0xFFFFFF));
        }
        int n = 0;
        for (DefinitionObjet.Attribut a : d.attributs()) {
            Attribute attr = attribut(a.nom());
            if (attr == null) {
                plugin.getLogger().warning("Objet " + d.id() + " : attribut inconnu « " + a.nom() + " »");
                continue;
            }
            EquipmentSlotGroup groupe = emplacement(a.emplacement());
            AttributeModifier.Operation op = switch (a.operation().toLowerCase(Locale.ROOT)) {
                case "pourcentage", "multiplier-base", "add_scalar" -> AttributeModifier.Operation.ADD_SCALAR;
                case "multiplier", "multiply_scalar_1" -> AttributeModifier.Operation.MULTIPLY_SCALAR_1;
                default -> AttributeModifier.Operation.ADD_NUMBER;
            };
            meta.addAttributeModifier(attr, new AttributeModifier(new NamespacedKey(plugin, d.id().replace('/', '.') + "." + (n++)), a.valeur(), op, groupe));
        }
        if (d.taillePile() != null) meta.setMaxStackSize(Math.max(1, Math.min(99, d.taillePile())));
        if (d.durabilite() != null && meta instanceof Damageable dmg) dmg.setMaxDamage(Math.max(1, d.durabilite()));
        objet.setItemMeta(meta);
        return objet;
    }

    /** Identifiant Vorace d'un objet, ou null si ce n'est pas un objet Vorace. */
    public String id(ItemStack objet) {
        if (objet == null || !objet.hasItemMeta()) return null;
        ItemMeta meta = objet.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer().get(cleId, PersistentDataType.STRING);
    }

    /** Vrai si l'objet a été fabriqué avec une ancienne version de sa définition. */
    public boolean perime(ItemStack objet, DefinitionObjet d) {
        String v = objet.getItemMeta().getPersistentDataContainer().get(cleVersion, PersistentDataType.STRING);
        return !Integer.toHexString(d.hashCode()).equals(v);
    }

    /** Refait l'objet avec sa définition actuelle, en gardant quantité, usure et enchantements ajoutés. */
    public ItemStack mettreAJour(ItemStack ancien, DefinitionObjet d) {
        ItemStack neuf = fabriquer(d, ancien.getAmount());
        Map<Enchantment, Integer> enchants = ancien.getEnchantments();
        if (enchants != null && !enchants.isEmpty()) neuf.addUnsafeEnchantments(enchants);
        if (ancien.getItemMeta() instanceof Damageable vieux && vieux.hasDamage()) {
            ItemMeta m = neuf.getItemMeta();
            if (m instanceof Damageable dm) {
                dm.setDamage(vieux.getDamage());
                neuf.setItemMeta(m);
            }
        }
        return neuf;
    }

    private Material materiauValide(DefinitionObjet d) {
        Material m = Material.matchMaterial(d.materiau());
        return m == null || !m.isItem() || m.isAir() ? Material.PAPER : m;
    }

    private boolean appliquerItemModel(ItemMeta meta, String id) {
        if (!itemModelCherche) {
            itemModelCherche = true;
            try {
                setItemModel = ItemMeta.class.getMethod("setItemModel", NamespacedKey.class);
            } catch (NoSuchMethodException e) {
                setItemModel = null;
            }
        }
        if (setItemModel == null) return false;
        try {
            setItemModel.invoke(meta, new NamespacedKey(GenerateurPack.NS, id));
            return true;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private Enchantment enchantement(String nom) {
        try {
            return Registry.ENCHANTMENT.get(cle(nom));
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Enchantements pas encore disponibles : " + ex.getMessage());
            return null;
        }
    }

    private static NamespacedKey cle(String nom) {
        String n = nom.toLowerCase(Locale.ROOT).trim();
        NamespacedKey k = n.contains(":") ? NamespacedKey.fromString(n) : NamespacedKey.minecraft(n);
        return k == null ? NamespacedKey.minecraft("air") : k;
    }

    private static Attribute attribut(String nom) {
        String n = nom.toLowerCase(Locale.ROOT).trim().replace("minecraft:", "").replace("generic.", "").replace("player.", "");
        n = switch (n) {
            case "degats", "dégâts", "attaque" -> "attack_damage";
            case "vitesse-attaque", "vitesse_attaque" -> "attack_speed";
            case "vitesse" -> "movement_speed";
            case "vie", "sante", "santé" -> "max_health";
            case "armure" -> "armor";
            case "solidite", "solidité" -> "armor_toughness";
            case "recul" -> "knockback_resistance";
            case "chance" -> "luck";
            case "taille" -> "scale";
            default -> n.replace('-', '_');
        };
        // 1.21.2+ : "attack_damage" ; 1.21 / 1.21.1 : "generic.attack_damage" ou "player.xxx"
        for (String essai : new String[]{n, "generic." + n, "player." + n}) {
            Attribute a = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(essai));
            if (a != null) return a;
        }
        return null;
    }

    private static EquipmentSlotGroup emplacement(String nom) {
        String n = nom == null ? "main" : nom.toLowerCase(Locale.ROOT);
        String anglais = switch (n) {
            case "main", "main-droite" -> "mainhand";
            case "main-gauche", "seconde-main" -> "offhand";
            case "mains" -> "hand";
            case "tete", "tête", "casque" -> "head";
            case "torse", "plastron" -> "chest";
            case "jambes", "jambieres", "jambières" -> "legs";
            case "pieds", "bottes" -> "feet";
            case "armure" -> "armor";
            case "partout", "tous" -> "any";
            default -> n;
        };
        EquipmentSlotGroup g = EquipmentSlotGroup.getByName(anglais);
        return g == null ? EquipmentSlotGroup.MAINHAND : g;
    }

    private static ItemFlag drapeau(String nom) {
        String n = nom.toUpperCase(Locale.ROOT).trim().replace('-', '_');
        n = switch (n) {
            case "ENCHANTEMENTS", "ENCHANTS" -> "HIDE_ENCHANTS";
            case "ATTRIBUTS", "ATTRIBUTES" -> "HIDE_ATTRIBUTES";
            case "INCASSABLE", "UNBREAKABLE" -> "HIDE_UNBREAKABLE";
            case "TEINTURE", "COULEUR", "DYE" -> "HIDE_DYE";
            case "INFOS", "TOOLTIP", "ADDITIONAL_TOOLTIP" -> "HIDE_ADDITIONAL_TOOLTIP";
            case "GARNITURE", "ARMOR_TRIM" -> "HIDE_ARMOR_TRIM";
            default -> n.startsWith("HIDE_") ? n : "HIDE_" + n;
        };
        try {
            return ItemFlag.valueOf(n);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ------------------------------------------------------- custom_model_data

    private File fichierCmd() { return new File(plugin.getDataFolder(), "donnees/custom-model-data.yml"); }

    private synchronized void chargerCmd() {
        cmdAttribues.clear();
        File f = fichierCmd();
        if (!f.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        for (String k : y.getKeys(false)) cmdAttribues.put(k, y.getInt(k, 0));
    }

    private synchronized void sauverCmd() {
        try {
            File f = fichierCmd();
            Files.createDirectories(f.getParentFile().toPath());
            YamlConfiguration y = new YamlConfiguration();
            cmdAttribues.forEach(y::set);
            y.save(f);
        } catch (IOException e) {
            plugin.getLogger().warning("Impossible d'enregistrer donnees/custom-model-data.yml : " + e.getMessage());
        }
    }

    /** Numéro custom_model_data stable de l'objet (gardé d'un redémarrage à l'autre). */
    synchronized int cmd(DefinitionObjet d) {
        if (d.cmdFixe() != null) return d.cmdFixe();
        Integer n = cmdAttribues.get(d.id());
        if (n != null) return n;
        int max = 1000;
        for (int v : cmdAttribues.values()) max = Math.max(max, v);
        cmdAttribues.put(d.id(), max + 1);
        return max + 1;
    }

    // ---------------------------------------------------------------- recettes

    public void enregistrerRecettes() {
        for (NamespacedKey k : recettes) Bukkit.removeRecipe(k);
        recettes.clear();
        for (DefinitionObjet d : definitions.values()) {
            DefinitionObjet.Recette r = d.recette();
            if (r == null) continue;
            try {
                NamespacedKey cle = new NamespacedKey(plugin, "objet." + d.id().replace('/', '.'));
                ItemStack resultat = fabriquer(d, r.quantite());
                boolean ok;
                switch (r.type()) {
                    case "four", "fourneau", "cuisson" -> {
                        RecipeChoice entree = choix(r.entree());
                        if (entree == null) throw new IllegalArgumentException("entrée inconnue « " + r.entree() + " »");
                        ok = Bukkit.addRecipe(new FurnaceRecipe(cle, resultat, entree, r.experience(), r.temps()));
                    }
                    case "sans-forme", "sansforme", "melange" -> {
                        ShapelessRecipe sr = new ShapelessRecipe(cle, resultat);
                        for (String i : r.sansForme()) {
                            RecipeChoice c = choix(i);
                            if (c == null) throw new IllegalArgumentException("ingrédient inconnu « " + i + " »");
                            sr.addIngredient(c);
                        }
                        ok = Bukkit.addRecipe(sr);
                    }
                    default -> {
                        ShapedRecipe sr = new ShapedRecipe(cle, resultat);
                        sr.shape(r.forme().toArray(new String[0]));
                        for (Map.Entry<Character, String> e : r.ingredients().entrySet()) {
                            RecipeChoice c = choix(e.getValue());
                            if (c == null) throw new IllegalArgumentException("ingrédient inconnu « " + e.getValue() + " »");
                            sr.setIngredient(e.getKey(), c);
                        }
                        ok = Bukkit.addRecipe(sr);
                    }
                }
                if (ok) recettes.add(cle);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Objet " + d.id() + " : recette ignorée (" + e.getMessage() + ")");
            }
        }
        if (!recettes.isEmpty()) plugin.getLogger().info(recettes.size() + " recette(s) enregistrée(s)");
    }

    /** "DIAMOND", "minecraft:diamond" ou "vorace:rubis" (un objet Vorace). */
    private RecipeChoice choix(String texte) {
        if (texte == null || texte.isBlank()) return null;
        String t = texte.trim();
        if (t.toLowerCase(Locale.ROOT).startsWith("vorace:")) {
            ItemStack i = fabriquer(t.substring(7).toLowerCase(Locale.ROOT), 1);
            return i == null ? null : new RecipeChoice.ExactChoice(i);
        }
        Material m = Material.matchMaterial(t);
        return m == null ? null : new RecipeChoice.MaterialChoice(m);
    }

    // ----------------------------------------------------------------- exemple

    private void installerExemple() {
        try {
            Files.createDirectories(dossierObjets());
            Files.createDirectories(dossierRessources().resolve("textures"));
            Files.createDirectories(dossierRessources().resolve("modeles"));
            boolean vide;
            try (Stream<Path> s = Files.list(dossierObjets())) { vide = s.findAny().isEmpty(); }
            if (!vide) return;
            copier("exemples/exemple.yml", dossierObjets().resolve("exemple.yml"));
            copier("exemples/rubis.png", dossierRessources().resolve("textures/exemple/rubis.png"));
            copier("exemples/epee_rubis.png", dossierRessources().resolve("textures/exemple/epee_rubis.png"));
        } catch (IOException e) {
            plugin.getLogger().warning("Impossible de créer les exemples d'objets : " + e.getMessage());
        }
    }

    private void copier(String ressource, Path cible) throws IOException {
        if (Files.exists(cible)) return;
        try (InputStream in = plugin.getClass().getClassLoader().getResourceAsStream(ressource)) {
            if (in == null) return;
            Files.createDirectories(cible.getParent());
            Files.copy(in, cible);
        }
    }
}
