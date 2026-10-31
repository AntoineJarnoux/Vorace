package fr.vorace.objets;

import fr.vorace.Messages;
import fr.vorace.Vorace;
import fr.vorace.blocs.EtatsNoteBlock;
import fr.vorace.blocs.GenerateurBlocs;
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
    private final Map<String, Integer> numerosBlocs = new LinkedHashMap<>();
    private volatile Map<String, String> blocParEtat = Map.of();
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
        chargerAnciens();
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
        attribuerNumerosBlocs(lues.values(), problemes);
        problemes.forEach(m -> plugin.getLogger().warning("Objets : " + m));
        plugin.getLogger().info(lues.size() + " objet(s) chargé(s) depuis " + fichiers.size() + " fichier(s)");
        if (avecRecettes) enregistrerRecettes();
    }

    /** Fichiers pour le pack (appelé hors du fil principal pendant la construction). */
    public Map<String, byte[]> fichiersPack(FormatPack format, Journal journal) {
        List<Visuel> visuels = new ArrayList<>();
        List<GenerateurBlocs.Apparence> blocs = new ArrayList<>();
        for (DefinitionObjet d : definitions.values()) {
            Integer teinte = d.couleur() != null && d.materiau().startsWith("leather_") ? (0xFF000000 | d.couleur()) : null;
            Integer numero = d.bloc() == null ? null : numeroBloc(d.id());
            String parent = numero != null && d.texture() == null && d.modele() == null ? GenerateurPack.NS + ":block/" + d.id() : null;
            visuels.add(new Visuel(d.id(), materiauValide(d).name().toLowerCase(Locale.ROOT), d.texture(), d.modele(), d.enMain(), cmd(d), teinte, parent));
            if (numero != null) blocs.add(new GenerateurBlocs.Apparence(d.id(), numero, d.bloc().faces(), d.bloc().modele()));
        }
        if (plugin.modeles() != null) {
            for (String id : plugin.modeles().idsParties()) visuels.add(new Visuel(id, "paper", null, null, false, cmdPour(id), null, GenerateurPack.NS + ":" + id));
            sauverCmd();
        }
        Map<String, byte[]> fichiers = new java.util.TreeMap<>(GenerateurPack.generer(visuels, dossierRessources(), format, journal));
        fichiers.putAll(GenerateurBlocs.generer(blocs, dossierRessources(), journal));
        return fichiers;
    }

    // ----------------------------------------------------------------- blocs

    /** Numéro d'état du bloc personnalisé (voir EtatsNoteBlock), ou null. */
    public synchronized Integer numeroBloc(String id) {
        return numerosBlocs.get(id);
    }

    /** Identifiant du bloc personnalisé qui utilise cet état (« instrument=x,note=n »), ou null. */
    public String idDepuisEtat(String cle) {
        return cle == null ? null : blocParEtat.get(cle);
    }

    /** Les numéros sont gardés d'un redémarrage à l'autre (donnees/blocs.txt) : un bloc posé garde son apparence. */
    private synchronized void attribuerNumerosBlocs(Collection<DefinitionObjet> defs, List<String> problemes) {
        Path f = plugin.getDataFolder().toPath().resolve("donnees").resolve("blocs.txt");
        numerosBlocs.clear();
        try {
            if (Files.isRegularFile(f)) {
                for (String l : Files.readAllLines(f)) {
                    String[] p = l.trim().split("\\s+", 2);
                    if (p.length == 2 && !p[0].startsWith("#")) {
                        try { numerosBlocs.put(p[1], Integer.parseInt(p[0])); } catch (NumberFormatException ignore) { }
                    }
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Impossible de lire donnees/blocs.txt : " + e.getMessage());
        }
        boolean change = false;
        for (DefinitionObjet d : defs) {
            if (d.bloc() == null || numerosBlocs.containsKey(d.id())) continue;
            java.util.Set<Integer> pris = new java.util.HashSet<>(numerosBlocs.values());
            int n = 0;
            while (pris.contains(n)) n++;
            if (n >= EtatsNoteBlock.CAPACITE) {
                problemes.add(d.id() + " : trop de blocs personnalisés (" + EtatsNoteBlock.CAPACITE + " au maximum)");
                continue;
            }
            numerosBlocs.put(d.id(), n);
            change = true;
        }
        Map<String, String> parEtat = new java.util.HashMap<>();
        for (DefinitionObjet d : defs) {
            Integer n = d.bloc() == null ? null : numerosBlocs.get(d.id());
            if (n != null && n >= 0 && n < EtatsNoteBlock.CAPACITE) parEtat.put(EtatsNoteBlock.etat(n), d.id());
        }
        blocParEtat = parEtat;
        if (change) {
            try {
                Files.createDirectories(f.getParent());
                List<String> lignes = new ArrayList<>(List.of("# numéro d'état -> bloc Vorace (ne pas modifier : les blocs posés changeraient d'apparence)"));
                numerosBlocs.forEach((id, n) -> lignes.add(n + " " + id));
                Files.write(f, lignes);
            } catch (IOException e) {
                plugin.getLogger().warning("Impossible d'enregistrer donnees/blocs.txt : " + e.getMessage());
            }
        }
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

        if (d.aUnVisuel()) {
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

    // ---------------------------------------------------------------- objets convertis d'autres plugins

    /** Clés où Nexo et Oraxen rangent l'identifiant de leurs objets. */
    private static final List<String[]> CLES_ANCIENNES = List.of(new String[]{"nexo", "id"}, new String[]{"oraxen", "id"});
    private volatile Map<String, String> anciens = Map.of();
    private List<NamespacedKey> clesAnciennes;

    /** Lit donnees/conversion-ids.yml (écrit par /vorace convertir) : ancien objet -> objet Vorace. */
    private void chargerAnciens() {
        File f = plugin.getDataFolder().toPath().resolve("donnees/conversion-ids.yml").toFile();
        if (!f.isFile()) { anciens = Map.of(); return; }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        Map<String, String> m = new java.util.HashMap<>();
        for (String k : y.getKeys(false)) {
            String v = y.getString(k);
            if (v != null) m.put(k.toLowerCase(Locale.ROOT), v);
        }
        anciens = m;
    }

    /** Pour un objet de Nexo ou d'Oraxen déjà converti : l'identifiant Vorace qui le remplace, sinon null. */
    public String idAncien(ItemStack objet) {
        if (anciens.isEmpty() || objet == null || !objet.hasItemMeta()) return null;
        if (clesAnciennes == null) {
            List<NamespacedKey> l = new ArrayList<>();
            for (String[] c : CLES_ANCIENNES) {
                NamespacedKey k = NamespacedKey.fromString(c[0] + ":" + c[1]);
                if (k != null) l.add(k);
            }
            clesAnciennes = l;
        }
        ItemMeta meta = objet.getItemMeta();
        if (meta == null) return null;
        for (NamespacedKey k : clesAnciennes) {
            String v;
            try { v = meta.getPersistentDataContainer().get(k, PersistentDataType.STRING); } catch (RuntimeException e) { continue; }
            if (v == null) continue;
            String id = anciens.get((k.getNamespace() + ":" + v).toLowerCase(Locale.ROOT));
            if (id != null && definitions.containsKey(id)) return id;
        }
        return null;
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
        } catch (Throwable ex) {
            plugin.getLogger().warning("Enchantement « " + nom + " » illisible : " + ex);
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
            try {
                Attribute a = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(essai));
                if (a != null) return a;
            } catch (Throwable ignore) {
                // clé invalide pour cette version : on essaie la suivante
            }
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
        return cmdPour(d.id());
    }

    /** Numéro custom_model_data stable pour un identifiant d'apparence (objet ou partie de modèle animé). */
    public synchronized int cmdPour(String id) {
        Integer n = cmdAttribues.get(id);
        if (n != null) return n;
        int max = 1000;
        for (int v : cmdAttribues.values()) max = Math.max(max, v);
        cmdAttribues.put(id, max + 1);
        return max + 1;
    }

    /** Objet invisible dans les inventaires, qui sert seulement à afficher une apparence du pack (parties de modèles animés). */
    public ItemStack objetVisuel(String id) {
        ItemStack objet = new ItemStack(Material.PAPER);
        ItemMeta meta = objet.getItemMeta();
        if (meta == null) return objet;
        FormatPack f = plugin.packs().format();
        if (f == null || f.majeur() < 42 || !appliquerItemModel(meta, id)) meta.setCustomModelData(cmdPour(id));
        objet.setItemMeta(meta);
        return objet;
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
            } catch (Throwable e) {
                plugin.getLogger().warning("Objet " + d.id() + " : recette ignorée (" + e + ")");
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

    /**
     * Installe chaque série d'exemples une seule fois (notée dans donnees/exemples.txt) :
     * un serveur qui se met à jour reçoit les nouveaux exemples, et un exemple supprimé ne revient pas.
     */
    private void installerExemple() {
        try {
            Files.createDirectories(dossierObjets());
            Files.createDirectories(dossierRessources().resolve("textures"));
            Files.createDirectories(dossierRessources().resolve("modeles"));
            Path liste = plugin.getDataFolder().toPath().resolve("donnees").resolve("exemples.txt");
            List<String> faits = new ArrayList<>(Files.isRegularFile(liste) ? Files.readAllLines(liste) : List.of());
            if (!Files.isRegularFile(liste)) {
                // avant 0.3 : les exemples d'objets étaient installés seulement si le dossier était vide
                boolean vide;
                try (Stream<Path> s = Files.list(dossierObjets())) { vide = s.findAny().isEmpty(); }
                if (!vide) faits.add("objets");
            }
            boolean change = false;
            if (!faits.contains("objets")) {
                copier("exemples/exemple.yml", dossierObjets().resolve("exemple.yml"));
                copier("exemples/rubis.png", dossierRessources().resolve("textures/exemple/rubis.png"));
                copier("exemples/epee_rubis.png", dossierRessources().resolve("textures/exemple/epee_rubis.png"));
                faits.add("objets");
                change = true;
            }
            if (!faits.contains("meubles")) {
                copier("exemples/meubles.yml", dossierObjets().resolve("exemple-meubles.yml"));
                for (String m : List.of("chaise", "lampe", "caisse", "table")) {
                    copier("exemples/meubles/" + m + ".json", dossierRessources().resolve("modeles/exemple/" + m + ".json"));
                }
                for (String t : List.of("bois", "lampe")) {
                    copier("exemples/meubles/" + t + ".png", dossierRessources().resolve("textures/exemple/" + t + ".png"));
                }
                faits.add("meubles");
                change = true;
            }
            if (!faits.contains("blocs")) {
                copier("exemples/blocs.yml", dossierObjets().resolve("exemple-blocs.yml"));
                for (String t : List.of("minerai_rubis", "bloc_rubis")) {
                    copier("exemples/blocs/" + t + ".png", dossierRessources().resolve("textures/exemple/" + t + ".png"));
                }
                faits.add("blocs");
                change = true;
            }
            // modèles d'exemple de la 0.3.0 (textures abîmées sur la chaise et la table) : remplacés s'ils n'ont pas été modifiés
            for (String m : List.of("chaise", "lampe", "caisse", "table")) {
                Path f = dossierRessources().resolve("modeles/exemple/" + m + ".json");
                if (Files.isRegularFile(f) && Files.readString(f).contains("\"credit\": \"Vorace (exemple)\"")) {
                    Files.delete(f);
                    copier("exemples/meubles/" + m + ".json", f);
                }
            }
            if (change) {
                Files.createDirectories(liste.getParent());
                Files.write(liste, faits);
            }
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
