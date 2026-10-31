package fr.vorace.blocs;

import fr.vorace.Vorace;
import fr.vorace.objets.DefinitionObjet;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.NotePlayEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Pose, minage et protection des blocs personnalisés. */
public final class EcouteurBlocs implements Listener {

    /** Blocs qu'un clic droit utilise (on ne pose pas de bloc dessus sans s'accroupir). */
    private static final Set<String> UTILISABLES = Set.of("crafting_table", "anvil", "chipped_anvil", "damaged_anvil",
        "enchanting_table", "grindstone", "stonecutter", "loom", "cartography_table", "smithing_table", "lectern",
        "bell", "lever", "respawn_anchor", "beacon", "note_block", "jukebox", "comparator", "repeater", "daylight_detector",
        "cake", "crafter", "decorated_pot");

    private final Vorace plugin;
    private final Map<UUID, Long> derniereAction = new ConcurrentHashMap<>();

    public EcouteurBlocs(Vorace plugin) {
        this.plugin = plugin;
    }

    private Blocs blocs() { return plugin.blocs(); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void clic(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block b = e.getClickedBlock();
        if (b == null) return;
        Player p = e.getPlayer();
        boolean surPerso = blocs().definition(b) != null;
        // un bloc personnalisé ne doit jamais changer de note
        if (surPerso) e.setUseInteractedBlock(Event.Result.DENY);

        ItemStack objet = e.getItem();
        DefinitionObjet d = objet == null ? null : plugin.objets().definition(plugin.objets().id(objet));
        if (d == null || d.bloc() == null || d.meuble() != null) return;
        if (!surPerso && !p.isSneaking() && utilisable(b)) return;
        e.setCancelled(true);
        long t = System.currentTimeMillis();
        Long avant = derniereAction.put(p.getUniqueId(), t);
        if (avant != null && t - avant < 120) return; // même clic pour les deux mains
        EquipmentSlot main = e.getHand() == null ? EquipmentSlot.HAND : e.getHand();
        blocs().poser(p, objet, d, b, e.getBlockFace(), main);
    }

    private static boolean utilisable(Block b) {
        try {
            if (b.getState() instanceof InventoryHolder) return true;
            BlockData bd = b.getBlockData();
            if (bd instanceof Openable) return true;
        } catch (Throwable ignore) {
            // bloc sans état
        }
        String n = b.getType().name().toLowerCase();
        return UTILISABLES.contains(n) || n.endsWith("_button") || n.endsWith("_bed") || n.endsWith("_sign")
            || n.endsWith("_hanging_sign") || n.endsWith("shulker_box");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void debutMinage(BlockDamageEvent e) {
        DefinitionObjet d = blocs().definition(e.getBlock());
        if (d == null || e.getPlayer().getGameMode() == GameMode.CREATIVE) return;
        e.setInstaBreak(false);
        blocs().commencerMinage(e.getPlayer(), e.getBlock(), d);
    }

    @EventHandler
    public void finMinage(BlockDamageAbortEvent e) {
        if (blocs().enTrainDeMiner(e.getPlayer(), e.getBlock())) blocs().arreterMinage(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void cassage(BlockBreakEvent e) {
        if (blocs().simulation) return;
        DefinitionObjet d = blocs().definition(e.getBlock());
        if (d == null) return;
        // le cassage d'un bloc personnalisé passe toujours par Vorace (vitesse, butin)
        e.setCancelled(true);
        if (e.getPlayer().getGameMode() == GameMode.CREATIVE) blocs().casserPar(e.getPlayer(), e.getBlock(), d);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void apresPose(BlockPlaceEvent e) {
        if (!blocs().simulation) blocs().verifierVoisins(e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void apresCassage(BlockBreakEvent e) {
        if (!blocs().simulation) blocs().verifierVoisins(e.getBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void physique(BlockPhysicsEvent e) {
        Block b = e.getBlock();
        if (b.getType() == Material.NOTE_BLOCK && blocs().definition(b) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void note(NotePlayEvent e) {
        if (blocs().definition(e.getBlock()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void pistonPousse(BlockPistonExtendEvent e) {
        for (Block b : e.getBlocks()) if (blocs().definition(b) != null) { e.setCancelled(true); return; }
    }

    @EventHandler(ignoreCancelled = true)
    public void pistonTire(BlockPistonRetractEvent e) {
        for (Block b : e.getBlocks()) if (blocs().definition(b) != null) { e.setCancelled(true); return; }
    }

    @EventHandler(ignoreCancelled = true)
    public void feu(BlockBurnEvent e) {
        if (blocs().definition(e.getBlock()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void explosionEntite(EntityExplodeEvent e) {
        exploser(e.blockList());
    }

    @EventHandler(ignoreCancelled = true)
    public void explosionBloc(BlockExplodeEvent e) {
        exploser(e.blockList());
    }

    private void exploser(List<Block> liste) {
        for (Block b : new ArrayList<>(liste)) {
            if (blocs().definition(b) == null) continue;
            liste.remove(b);
            blocs().casserSansJoueur(b);
        }
    }

    @EventHandler
    public void chargementChunk(ChunkLoadEvent e) {
        if (!e.isNewChunk()) blocs().verifierChunk(e.getChunk());
    }

    @EventHandler
    public void generation(ChunkPopulateEvent e) {
        blocs().generer(e.getChunk());
    }

    @EventHandler
    public void arrivee(PlayerJoinEvent e) {
        blocs().nettoyerJoueur(e.getPlayer());
    }

    @EventHandler
    public void depart(PlayerQuitEvent e) {
        blocs().arreterMinage(e.getPlayer());
        derniereAction.remove(e.getPlayer().getUniqueId());
    }
}
