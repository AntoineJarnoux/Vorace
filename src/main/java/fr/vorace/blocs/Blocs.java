package fr.vorace.blocs;

import fr.vorace.Messages;
import fr.vorace.Vorace;
import fr.vorace.api.VoraceBlocCasseEvent;
import fr.vorace.objets.DefinitionObjet;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Blocs personnalisés : des blocs à note dans un état réservé. Le serveur garde aussi la liste des blocs
 * personnalisés de chaque chunk (dans les données du chunk) pour remettre le bon état si le jeu le change.
 * Le minage est calculé par le serveur : vitesse selon l'outil, animation de fissure envoyée aux joueurs.
 */
public final class Blocs {

    private final Vorace plugin;
    private final NamespacedKey cleChunk;
    private final NamespacedKey cleMinage;
    /** Vrai pendant nos propres BlockBreakEvent / BlockPlaceEvent de vérification. */
    boolean simulation;
    private final Map<UUID, Minage> minages = new HashMap<>();
    private BukkitTask tache;

    public Blocs(Vorace plugin) {
        this.plugin = plugin;
        cleChunk = new NamespacedKey(plugin, "blocs");
        cleMinage = new NamespacedKey(plugin, "minage");
    }

    public void demarrer() {
        tache = Bukkit.getScheduler().runTaskTimer(plugin, this::tic, 1L, 1L);
    }

    public void arreter() {
        if (tache != null) tache.cancel();
        for (Minage m : new ArrayList<>(minages.values())) arreterMinage(m.joueur);
        minages.clear();
    }

    // ------------------------------------------------------------------ identification

    /** Identifiant du bloc personnalisé à cet endroit, ou null. */
    public String id(Block b) {
        if (b == null || b.getType() != Material.NOTE_BLOCK) return null;
        String enregistre = lireChunk(b.getChunk()).get(cleLocale(b));
        if (enregistre != null && plugin.objets().definition(enregistre) != null) return enregistre;
        return plugin.objets().idDepuisEtat(EtatsNoteBlock.cle(b.getBlockData().getAsString()));
    }

    public DefinitionObjet definition(Block b) {
        String id = id(b);
        DefinitionObjet d = id == null ? null : plugin.objets().definition(id);
        return d != null && d.bloc() != null ? d : null;
    }

    private BlockData donnees(String id) {
        Integer n = plugin.objets().numeroBloc(id);
        return n == null ? null : Bukkit.createBlockData(EtatsNoteBlock.donnees(n));
    }

    // ------------------------------------------------------------------ registre par chunk

    private static String cleLocale(Block b) {
        return (b.getX() & 15) + "," + b.getY() + "," + (b.getZ() & 15);
    }

    Map<String, String> lireChunk(Chunk c) {
        Map<String, String> m = new LinkedHashMap<>();
        String t = c.getPersistentDataContainer().get(cleChunk, PersistentDataType.STRING);
        if (t == null || t.isEmpty()) return m;
        for (String e : t.split(";")) {
            int eg = e.indexOf('=');
            if (eg > 0) m.put(e.substring(0, eg), e.substring(eg + 1));
        }
        return m;
    }

    private void ecrireChunk(Chunk c, Map<String, String> m) {
        PersistentDataContainer pdc = c.getPersistentDataContainer();
        if (m.isEmpty()) {
            pdc.remove(cleChunk);
            return;
        }
        StringBuilder sb = new StringBuilder();
        m.forEach((k, v) -> { if (!sb.isEmpty()) sb.append(';'); sb.append(k).append('=').append(v); });
        pdc.set(cleChunk, PersistentDataType.STRING, sb.toString());
    }

    private void enregistrer(Block b, String id) {
        Map<String, String> m = lireChunk(b.getChunk());
        if (id == null) m.remove(cleLocale(b)); else m.put(cleLocale(b), id);
        ecrireChunk(b.getChunk(), m);
    }

    /** Remet le bon état aux blocs personnalisés d'un chunk (après un chargement, ou si le jeu les a changés). */
    public int verifierChunk(Chunk c) {
        Map<String, String> m = lireChunk(c);
        if (m.isEmpty()) return 0;
        int corriges = 0;
        boolean change = false;
        for (Map.Entry<String, String> e : new ArrayList<>(m.entrySet())) {
            String[] p = e.getKey().split(",");
            Block b = c.getBlock(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
            if (b.getType() != Material.NOTE_BLOCK) { m.remove(e.getKey()); change = true; continue; }
            if (verifier(b, e.getValue())) corriges++;
        }
        if (change) ecrireChunk(c, m);
        return corriges;
    }

    /** Remet le bon état d'un bloc personnalisé ; vrai s'il fallait le corriger. */
    boolean verifier(Block b, String id) {
        BlockData bon = donnees(id);
        if (bon == null || b.getType() != Material.NOTE_BLOCK) return false;
        if (EtatsNoteBlock.cle(bon.getAsString()).equals(EtatsNoteBlock.cle(b.getBlockData().getAsString()))) return false;
        b.setBlockData(bon, false);
        return true;
    }

    /** Vérifie les blocs au-dessus et en dessous (ce sont eux qui changent l'instrument d'un bloc à note). */
    void verifierVoisins(Block b) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (BlockFace f : new BlockFace[]{BlockFace.UP, BlockFace.DOWN}) {
                Block v = b.getRelative(f);
                if (v.getType() != Material.NOTE_BLOCK) continue;
                String id = lireChunk(v.getChunk()).get(cleLocale(v));
                if (id != null) verifier(v, id);
            }
        });
    }

    // ------------------------------------------------------------------ pose

    public boolean poser(Player p, ItemStack objet, DefinitionObjet d, Block clique, BlockFace face, EquipmentSlot main) {
        Block cible = clique.isReplaceable() ? clique : clique.getRelative(face);
        if (!cible.isReplaceable()) return false;
        if (cible.getY() < cible.getWorld().getMinHeight() || cible.getY() >= cible.getWorld().getMaxHeight()) return false;
        BoundingBox boite = new BoundingBox(cible.getX(), cible.getY(), cible.getZ(), cible.getX() + 1, cible.getY() + 1, cible.getZ() + 1);
        if (!cible.getWorld().getNearbyEntities(boite, e -> e instanceof LivingEntity && !(e instanceof Player pl && pl.getGameMode() == GameMode.SPECTATOR)).isEmpty()) return false;
        BlockData donnees = donnees(d.id());
        if (donnees == null) return false;

        BlockState avant = cible.getState();
        cible.setBlockData(donnees, false);
        BlockPlaceEvent ev = new BlockPlaceEvent(cible, avant, clique, objet, p, true, main);
        simulation = true;
        try {
            Bukkit.getPluginManager().callEvent(ev);
        } finally {
            simulation = false;
        }
        if (ev.isCancelled() || !ev.canBuild()) {
            avant.update(true, false);
            return false;
        }
        enregistrer(cible, d.id());
        if (p.getGameMode() != GameMode.CREATIVE) objet.setAmount(objet.getAmount() - 1);
        if (main == EquipmentSlot.OFF_HAND) p.swingOffHand(); else p.swingMainHand();
        son(cible, d.bloc().sonPose());
        return true;
    }

    /** Pose sans vérification (API, génération). */
    public boolean placer(Block b, String id) {
        BlockData donnees = donnees(id);
        if (donnees == null) return false;
        b.setBlockData(donnees, false);
        enregistrer(b, id);
        return true;
    }

    // ------------------------------------------------------------------ minage

    private static final class Minage {
        final Player joueur;
        final Block bloc;
        final DefinitionObjet def;
        float progres;
        int dernierEnvoi = -1;
        int tics;

        Minage(Player joueur, Block bloc, DefinitionObjet def) {
            this.joueur = joueur;
            this.bloc = bloc;
            this.def = def;
        }
    }

    /** Le joueur commence à frapper un bloc personnalisé. */
    void commencerMinage(Player p, Block b, DefinitionObjet d) {
        Minage ancien = minages.get(p.getUniqueId());
        if (ancien != null && ancien.bloc.equals(b)) return;
        if (ancien != null) envoyerFissure(ancien, -1);
        minages.put(p.getUniqueId(), new Minage(p, b, d));
        bloquerMinageClient(p, true);
    }

    void arreterMinage(Player p) {
        Minage m = minages.remove(p.getUniqueId());
        if (m != null) envoyerFissure(m, -1);
        bloquerMinageClient(p, false);
    }

    boolean enTrainDeMiner(Player p, Block b) {
        Minage m = minages.get(p.getUniqueId());
        return m != null && m.bloc.equals(b);
    }

    private void tic() {
        if (minages.isEmpty()) return;
        for (Minage m : new ArrayList<>(minages.values())) {
            Player p = m.joueur;
            m.tics++;
            if (!p.isOnline() || !m.bloc.getWorld().equals(p.getWorld()) || id(m.bloc) == null
                || (m.tics % 5 == 0 && !m.bloc.equals(p.getTargetBlockExact(6)))) {
                arreterMinage(p);
                continue;
            }
            m.progres += vitesse(p, m.def.bloc());
            if (m.progres >= 1f) {
                arreterMinage(p);
                casserPar(p, m.bloc, m.def);
                continue;
            }
            int etape = (int) (m.progres * 10);
            if (etape != m.dernierEnvoi) {
                m.dernierEnvoi = etape;
                envoyerFissure(m, m.progres);
            }
        }
    }

    private void envoyerFissure(Minage m, float progres) {
        Location l = m.bloc.getLocation();
        for (Player x : m.bloc.getWorld().getPlayers()) {
            if (x.getLocation().distanceSquared(l) < 32 * 32) x.sendBlockDamage(l, Math.max(0f, progres), m.joueur.getEntityId());
        }
    }

    /** Avance du minage par tic (règle du jeu : vitesse / dureté / 30, ou / 100 sans le bon outil). */
    float vitesse(Player p, DefinitionBloc b) {
        if (b.durete() <= 0) return 1f;
        ItemStack main = p.getInventory().getItemInMainHand();
        String mat = main == null ? "air" : main.getType().name().toLowerCase(Locale.ROOT);
        Outils.Outil o = Outils.lire(mat);
        boolean bonOutil = o != null && o.type().equals(b.outil());
        float vitesse = bonOutil ? o.vitesse() : 1f;
        if (bonOutil && vitesse > 1f) {
            int eff = niveauEnchantement(main, "efficiency");
            if (eff > 0) vitesse += eff * eff + 1;
        }
        PotionEffect hate = effet(p, "haste");
        if (hate != null) vitesse *= 1f + 0.2f * (hate.getAmplifier() + 1);
        PotionEffect fatigue = effet(p, "mining_fatigue");
        if (fatigue != null) vitesse *= (float) Math.pow(0.3, Math.min(fatigue.getAmplifier() + 1, 4));
        boolean recolte = peutRecolter(o, b);
        return (float) (vitesse / b.durete() / (recolte ? 30.0 : 100.0));
    }

    static boolean peutRecolter(Outils.Outil o, DefinitionBloc b) {
        if (b.outil().equals("aucun")) return true;
        return o != null && o.type().equals(b.outil()) && o.niveau() >= b.niveau();
    }

    /** Le joueur a fini de miner : on casse comme le ferait le jeu, avec notre butin. */
    void casserPar(Player p, Block b, DefinitionObjet d) {
        casserPar(p, b, d, true);
    }

    /**
     * Casse comme le joueur avec l'outil qu'il tient (butin, Fortune, Toucher de soie, expérience).
     * @param protections faux pour /vorace bloc casser : ignore les zones protégées
     */
    public void casserPar(Player p, Block b, DefinitionObjet d, boolean protections) {
        if (protections) {
            BlockBreakEvent ev = new BlockBreakEvent(b, p);
            simulation = true;
            try {
                Bukkit.getPluginManager().callEvent(ev);
            } finally {
                simulation = false;
            }
            if (ev.isCancelled()) return;
        }
        ItemStack outil = p.getInventory().getItemInMainHand();
        boolean creatif = p.getGameMode() == GameMode.CREATIVE;
        Outils.Outil o = outil == null ? null : Outils.lire(outil.getType().name().toLowerCase(Locale.ROOT));
        boolean recolte = !creatif && peutRecolter(o, d.bloc());
        List<ItemStack> butin = recolte ? butin(d, outil) : List.of();
        VoraceBlocCasseEvent api = new VoraceBlocCasseEvent(p, d.id(), b, butin);
        Bukkit.getPluginManager().callEvent(api);
        if (api.isCancelled()) return;
        detruire(b, d, api.getButin(), recolte && niveauEnchantement(outil, "silk_touch") == 0 ? xp(d.bloc()) : 0);
        if (!creatif && o != null) {
            try {
                p.damageItemStack(EquipmentSlot.HAND, 1);
            } catch (Throwable ignore) {
                // ancienne API : l'outil ne s'use pas
            }
        }
    }

    /** Retire le bloc, lâche le butin et l'expérience, joue son et particules. */
    void detruire(Block b, DefinitionObjet d, List<ItemStack> butin, int xp) {
        BlockData avant = b.getBlockData();
        Location centre = b.getLocation().add(0.5, 0.5, 0.5);
        b.setType(Material.AIR);
        enregistrer(b, null);
        World w = b.getWorld();
        for (ItemStack it : butin) if (it != null && !it.getType().isAir()) w.dropItemNaturally(centre, it);
        if (xp > 0) w.spawn(centre, ExperienceOrb.class, orbe -> orbe.setExperience(xp));
        son(b, d.bloc().sonCasse());
        try {
            w.spawnParticle(Particle.BLOCK, centre, 40, 0.3, 0.3, 0.3, 0.0, avant);
        } catch (Throwable ignore) {
            // particules : pas grave
        }
        verifierVoisins(b);
    }

    List<ItemStack> butin(DefinitionObjet d, ItemStack outil) {
        DefinitionBloc b = d.bloc();
        List<ItemStack> r = new ArrayList<>();
        if (b.butin().isEmpty() || (b.toucherDeSoie() && niveauEnchantement(outil, "silk_touch") > 0)) {
            ItemStack soi = plugin.objets().fabriquer(d, 1);
            if (soi != null) r.add(soi);
            return r;
        }
        int fortune = b.fortune() ? niveauEnchantement(outil, "fortune") : 0;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (DefinitionBloc.Butin x : b.butin()) {
            if (rnd.nextDouble() > x.chance()) continue;
            int n = x.min() + (x.max() > x.min() ? rnd.nextInt(x.max() - x.min() + 1) : 0);
            if (fortune > 0) n *= Math.max(0, rnd.nextInt(fortune + 2) - 1) + 1; // règle des minerais du jeu
            if (n <= 0) continue;
            ItemStack it = objet(x.objet(), n);
            if (it == null) {
                plugin.getLogger().warning("Bloc " + d.id() + " : objet de butin inconnu « " + x.objet() + " »");
                continue;
            }
            r.add(it);
        }
        return r;
    }

    private ItemStack objet(String nom, int n) {
        String t = nom.trim();
        if (t.toLowerCase(Locale.ROOT).startsWith("vorace:")) return plugin.objets().fabriquer(t.substring(7).toLowerCase(Locale.ROOT), n);
        DefinitionObjet d = plugin.objets().definition(t.toLowerCase(Locale.ROOT));
        if (d != null && !t.contains(":")) return plugin.objets().fabriquer(d, n);
        Material m = Material.matchMaterial(t);
        return m == null || m.isAir() ? null : new ItemStack(m, n);
    }

    static int xp(DefinitionBloc b) {
        if (b.xpMax() <= 0) return 0;
        return b.xpMin() + (b.xpMax() > b.xpMin() ? ThreadLocalRandom.current().nextInt(b.xpMax() - b.xpMin() + 1) : 0);
    }

    /** Casse sans joueur (explosion, commande) : butin comme à la main. */
    public void casserSansJoueur(Block b) {
        DefinitionObjet d = definition(b);
        if (d == null) return;
        detruire(b, d, butin(d, null), 0);
    }

    // ------------------------------------------------------------------ minage côté client

    /**
     * Pendant qu'il mine un bloc personnalisé, le joueur a une vitesse de minage nulle côté jeu :
     * c'est le serveur qui avance le minage et montre la fissure. Sinon le client casserait le bloc
     * à la vitesse d'un bloc à note.
     */
    private void bloquerMinageClient(Player p, boolean bloquer) {
        try {
            Attribute a = attribut();
            if (a == null) return;
            AttributeInstance inst = p.getAttribute(a);
            if (inst == null) return;
            // removeModifier(clé) n'a pas la même signature selon les versions : on retire l'objet lui-même
            for (AttributeModifier m : new ArrayList<>(inst.getModifiers())) {
                if (cleMinage.equals(m.getKey())) inst.removeModifier(m);
            }
            if (bloquer) inst.addModifier(new AttributeModifier(cleMinage, -1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
        } catch (Throwable ignore) {
            // version sans cet attribut : le client peut montrer le bloc cassé un instant, le serveur le remet
        }
    }

    private Attribute attributMinage;
    private boolean attributCherche;

    private Attribute attribut() {
        if (attributCherche) return attributMinage;
        attributCherche = true;
        for (String k : new String[]{"block_break_speed", "player.block_break_speed"}) {
            try {
                Attribute a = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(k));
                if (a != null) { attributMinage = a; break; }
            } catch (Throwable ignore) {
                // essai suivant
            }
        }
        return attributMinage;
    }

    /** Retire la vitesse de minage nulle laissée par un ancien démarrage (arrivée du joueur). */
    void nettoyerJoueur(Player p) {
        if (!minages.containsKey(p.getUniqueId())) bloquerMinageClient(p, false);
    }

    // ------------------------------------------------------------------ génération

    void generer(Chunk c) {
        World w = c.getWorld();
        for (DefinitionObjet d : plugin.objets().toutes()) {
            if (d.bloc() == null || d.bloc().generation() == null) continue;
            DefinitionBloc.Generation g = d.bloc().generation();
            if (!g.mondes().isEmpty() && g.mondes().stream().noneMatch(m -> m.equalsIgnoreCase(w.getName()))) continue;
            if (g.mondes().isEmpty() && w.getEnvironment() != World.Environment.NORMAL) continue;
            BlockData donnees = donnees(d.id());
            if (donnees == null) continue;
            List<Material> remplace = new ArrayList<>();
            for (String r : g.remplace()) { Material m = Material.matchMaterial(r); if (m != null) remplace.add(m); }
            Random rnd = new Random(w.getSeed() ^ ((long) c.getX() * 341873128712L) ^ ((long) c.getZ() * 132897987541L) ^ d.id().hashCode());
            int yMin = Math.max(g.yMin(), w.getMinHeight()), yMax = Math.min(g.yMax(), w.getMaxHeight() - 1);
            if (yMax < yMin) continue;
            Map<String, String> registre = lireChunk(c);
            for (int f = 0; f < g.filons(); f++) {
                int x = rnd.nextInt(16), y = yMin + rnd.nextInt(yMax - yMin + 1), z = rnd.nextInt(16);
                for (int i = 0; i < g.taille(); i++) {
                    if (x >= 0 && x < 16 && z >= 0 && z < 16 && y >= yMin && y <= yMax) {
                        Block b = c.getBlock(x, y, z);
                        if (remplace.contains(b.getType())) {
                            b.setBlockData(donnees, false);
                            registre.put(cleLocale(b), d.id());
                        }
                    }
                    switch (rnd.nextInt(6)) {
                        case 0 -> x++;
                        case 1 -> x--;
                        case 2 -> y++;
                        case 3 -> y--;
                        case 4 -> z++;
                        default -> z--;
                    }
                }
            }
            ecrireChunk(c, registre);
        }
    }

    // ------------------------------------------------------------------ réglage Paper

    /**
     * Paper peut empêcher les blocs à note de changer d'instrument quand un voisin change
     * (block-updates.disable-noteblock-updates). C'est le plus sûr pour les blocs personnalisés :
     * Vorace l'active une fois dans config/paper-global.yml (prend effet au prochain redémarrage).
     */
    public void reglerPaper() {
        if (plugin.objets().toutes().stream().noneMatch(d -> d.bloc() != null)) return;
        if (!plugin.getConfig().getBoolean("blocs.reglage-paper-auto", true)) return;
        Path f = Path.of("config", "paper-global.yml");
        try {
            if (!Files.isRegularFile(f)) return;
            String t = Files.readString(f, StandardCharsets.UTF_8);
            if (t.contains("disable-noteblock-updates: true")) return;
            if (!t.contains("disable-noteblock-updates: false")) {
                plugin.getLogger().warning("Blocs : mets « block-updates.disable-noteblock-updates: true » dans config/paper-global.yml");
                return;
            }
            Files.writeString(f, t.replace("disable-noteblock-updates: false", "disable-noteblock-updates: true"), StandardCharsets.UTF_8);
            plugin.getLogger().warning("Blocs : réglage Paper « disable-noteblock-updates » activé dans config/paper-global.yml. "
                + "Redémarre le serveur une fois pour qu'il soit pris en compte (en attendant, Vorace corrige les blocs lui-même).");
        } catch (Exception e) {
            plugin.getLogger().warning("Blocs : impossible de lire config/paper-global.yml (" + e.getMessage() + ")");
        }
    }

    // ------------------------------------------------------------------ divers

    int niveauEnchantement(ItemStack it, String nom) {
        if (it == null || it.getType().isAir()) return 0;
        try {
            Enchantment e = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(nom));
            return e == null ? 0 : it.getEnchantmentLevel(e);
        } catch (Throwable t) {
            return 0;
        }
    }

    private static PotionEffect effet(Player p, String nom) {
        try {
            PotionEffectType t = Registry.EFFECT.get(NamespacedKey.minecraft(nom));
            return t == null ? null : p.getPotionEffect(t);
        } catch (Throwable e) {
            return null;
        }
    }

    private static void son(Block b, String son) {
        if (son == null || son.isBlank()) return;
        try {
            b.getWorld().playSound(b.getLocation().add(0.5, 0.5, 0.5), son.contains(":") ? son : "minecraft:" + son, 1f, 0.8f);
        } catch (RuntimeException ignore) {
            // son inconnu
        }
    }

    public static void refus(Player p, String message) {
        p.sendActionBar(Messages.objet("<red>" + message));
    }
}
