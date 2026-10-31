package fr.vorace.meubles;

import fr.vorace.Messages;
import fr.vorace.Vorace;
import fr.vorace.api.VoraceMeubleCasseEvent;
import fr.vorace.api.VoraceMeublePoseEvent;
import fr.vorace.meubles.Geometrie.Point;
import fr.vorace.objets.DefinitionObjet;
import fr.vorace.objets.EcouteurObjets;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Meubles : un ItemDisplay montre le modèle, des barrières (ou une entité Interaction) servent de hitbox.
 * Tout ce qu'il faut pour retrouver un meuble est rangé dans les données de son ItemDisplay,
 * donc les meubles survivent aux redémarrages sans fichier à part.
 */
public final class Meubles {

    private final Vorace plugin;
    final NamespacedKey cleMeuble, cleBlocs, cleLumiere, cleHitbox, cleStockage, cleVersion, cleLien, cleSiege, cleOrigine, cleYaw;
    private final Map<UUID, Inventory> stockagesOuverts = new HashMap<>();
    private final Set<ItemDisplay> sieges = new HashSet<>();
    /** Vrai pendant qu'on lance nos propres BlockPlaceEvent / BlockBreakEvent de vérification (protections). */
    boolean simulation;

    public Meubles(Vorace plugin) {
        this.plugin = plugin;
        cleMeuble = new NamespacedKey(plugin, "meuble");
        cleBlocs = new NamespacedKey(plugin, "meuble-blocs");
        cleLumiere = new NamespacedKey(plugin, "meuble-lumiere");
        cleHitbox = new NamespacedKey(plugin, "meuble-hitbox");
        cleStockage = new NamespacedKey(plugin, "meuble-stockage");
        cleVersion = new NamespacedKey(plugin, "meuble-version");
        cleLien = new NamespacedKey(plugin, "meuble-lien");
        cleSiege = new NamespacedKey(plugin, "siege");
        cleOrigine = new NamespacedKey(plugin, "meuble-origine");
        cleYaw = new NamespacedKey(plugin, "meuble-yaw");
    }

    // ------------------------------------------------------------------ recherche

    public static DefinitionMeuble meuble(DefinitionObjet d) {
        return d == null ? null : d.meuble();
    }

    /** Identifiant Vorace du meuble, ou null si l'entité n'est pas un meuble. */
    public String id(Entity e) {
        if (!(e instanceof ItemDisplay)) return null;
        return e.getPersistentDataContainer().get(cleMeuble, PersistentDataType.STRING);
    }

    /** Le meuble (son ItemDisplay) lié à une entité : lui-même, ou celui d'une hitbox Interaction. */
    public ItemDisplay depuisEntite(Entity e) {
        if (e == null) return null;
        if (id(e) != null) return (ItemDisplay) e;
        if (e instanceof Interaction) {
            String lien = e.getPersistentDataContainer().get(cleLien, PersistentDataType.STRING);
            if (lien == null) return null;
            Entity m = Bukkit.getEntity(UUID.fromString(lien));
            if (m instanceof ItemDisplay d && id(d) != null) return d;
            // meuble disparu : la hitbox ne sert plus à rien
            e.remove();
        }
        return null;
    }

    /** Le meuble dont la hitbox en barrières contient ce bloc. */
    public ItemDisplay depuisBloc(Block b) {
        if (b == null || b.getType() != Material.BARRIER) return null;
        Location centre = new Location(b.getWorld(), b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5);
        for (Entity e : b.getWorld().getNearbyEntities(centre, 12, 12, 12, x -> id(x) != null)) {
            for (int[] c : Geometrie.lireBlocs(e.getPersistentDataContainer().get(cleBlocs, PersistentDataType.STRING))) {
                if (c[0] == b.getX() && c[1] == b.getY() && c[2] == b.getZ()) return (ItemDisplay) e;
            }
        }
        return null;
    }

    /** Un meuble dont le bloc d'origine est celui-ci. */
    public ItemDisplay depuisOrigine(Block b) {
        Location centre = new Location(b.getWorld(), b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5);
        String o = b.getX() + "," + b.getY() + "," + b.getZ();
        for (Entity e : b.getWorld().getNearbyEntities(centre, 3, 3, 3, x -> id(x) != null)) {
            if (o.equals(e.getPersistentDataContainer().get(cleOrigine, PersistentDataType.STRING))) return (ItemDisplay) e;
        }
        return null;
    }

    /** Le meuble le plus proche d'un endroit (commande /vorace meuble). */
    public ItemDisplay plusProche(Location l, double rayon) {
        ItemDisplay meilleur = null;
        double dist = Double.MAX_VALUE;
        for (Entity e : l.getWorld().getNearbyEntities(l, rayon, rayon, rayon, x -> id(x) != null)) {
            double d = e.getLocation().distanceSquared(l);
            if (d < dist) { dist = d; meilleur = (ItemDisplay) e; }
        }
        return meilleur;
    }

    public DefinitionObjet definition(ItemDisplay d) {
        String id = id(d);
        return id == null ? null : plugin.objets().definition(id);
    }

    // ------------------------------------------------------------------ pose

    /**
     * Pose un meuble à partir d'un clic droit sur un bloc.
     * @return vrai si le meuble a été posé
     */
    public boolean poser(Player p, ItemStack objet, DefinitionObjet d, Block clique, BlockFace face, EquipmentSlot main) {
        DefinitionMeuble m = d.meuble();
        Block origine;
        float yaw;
        if (face == BlockFace.UP || (clique.isReplaceable() && clique.getType() != Material.LIGHT)) {
            if (!m.sol()) { refus(p, "Ce meuble ne se pose pas au sol."); return false; }
            origine = clique.isReplaceable() ? clique : clique.getRelative(face);
            yaw = Geometrie.orientation(p.getLocation().getYaw(), m.directions());
        } else if (face == BlockFace.DOWN) {
            if (!m.plafond()) { refus(p, "Ce meuble ne se pose pas au plafond."); return false; }
            origine = clique.getRelative(face);
            yaw = Geometrie.orientation(p.getLocation().getYaw(), m.directions());
        } else {
            origine = clique.getRelative(face);
            if (m.mur()) {
                yaw = Geometrie.orientationMur(face.getModX(), face.getModZ());
            } else if (m.sol()) {
                yaw = Geometrie.orientation(p.getLocation().getYaw(), m.directions());
            } else {
                refus(p, "Ce meuble ne se pose pas contre un mur.");
                return false;
            }
        }

        // le modèle peut avoir été dessiné tourné : tout (hitbox, sièges) suit le modèle
        yaw = Geometrie.normaliser(yaw + m.rotationModele());

        // place libre ?
        int q = Geometrie.quarts(yaw);
        List<Block> blocs = new ArrayList<>();
        for (int[] b : m.blocs()) {
            int[] r = Geometrie.tournerBloc(b, q);
            blocs.add(origine.getRelative(r[0], r[1], r[2]));
        }
        if (!origine.isReplaceable()) { refus(p, "Pas assez de place ici."); return false; }
        for (Block b : blocs) {
            if (!b.isReplaceable() || b.getY() < b.getWorld().getMinHeight() || b.getY() >= b.getWorld().getMaxHeight()) {
                refus(p, "Pas assez de place ici.");
                return false;
            }
        }
        if (depuisOrigine(origine) != null) { refus(p, "Il y a déjà un meuble ici."); return false; }

        // protections (WorldGuard, GriefPrevention, claims...) : on demande comme pour un bloc posé
        for (Block b : blocs.isEmpty() ? List.of(origine) : blocs) {
            BlockPlaceEvent ev = new BlockPlaceEvent(b, b.getState(), clique, objet, p, true, main);
            simulation = true;
            try {
                Bukkit.getPluginManager().callEvent(ev);
            } finally {
                simulation = false;
            }
            if (ev.isCancelled() || !ev.canBuild()) return false;
        }
        Location pos = position(origine, m, yaw);
        VoraceMeublePoseEvent api = new VoraceMeublePoseEvent(p, d.id(), pos);
        Bukkit.getPluginManager().callEvent(api);
        if (api.isCancelled()) return false;

        creer(d, origine, yaw);
        if (p.getGameMode() != GameMode.CREATIVE) objet.setAmount(objet.getAmount() - 1);
        if (main == EquipmentSlot.OFF_HAND) p.swingOffHand(); else p.swingMainHand();
        son(pos, m.sonPose());
        return true;
    }

    private Location position(Block origine, DefinitionMeuble m, float yaw) {
        Point dec = Geometrie.tourner(m.decalage(), yaw);
        return new Location(origine.getWorld(), origine.getX() + 0.5 + dec.x(), origine.getY() + 0.5 + dec.y(),
            origine.getZ() + 0.5 + dec.z(), yaw, 0f);
    }

    /** Crée le meuble sans aucune vérification (sert aussi à l'API). Le yaw est celui du modèle, rotation-modele comprise. */
    public ItemDisplay creer(DefinitionObjet d, Block origine, float yaw) {
        DefinitionMeuble m = d.meuble();
        World w = origine.getWorld();
        Location pos = position(origine, m, yaw);
        int q = Geometrie.quarts(yaw);
        List<int[]> coords = new ArrayList<>();
        for (int[] b : m.blocs()) {
            int[] r = Geometrie.tournerBloc(b, q);
            coords.add(new int[]{origine.getX() + r[0], origine.getY() + r[1], origine.getZ() + r[2]});
        }
        ItemDisplay disp = w.spawn(pos, ItemDisplay.class, e -> {
            appliquerVisuel(e, d);
            e.setPersistent(true);
            PersistentDataContainer pdc = e.getPersistentDataContainer();
            pdc.set(cleMeuble, PersistentDataType.STRING, d.id());
            pdc.set(cleBlocs, PersistentDataType.STRING, Geometrie.ecrireBlocs(coords));
            pdc.set(cleOrigine, PersistentDataType.STRING, origine.getX() + "," + origine.getY() + "," + origine.getZ());
            pdc.set(cleYaw, PersistentDataType.STRING, Float.toString(yaw));
        });
        for (int[] c : coords) w.getBlockAt(c[0], c[1], c[2]).setType(Material.BARRIER);

        if (m.lumiere() > 0) {
            Block l = origine;
            if (l.getType() == Material.BARRIER) l = origine.getRelative(BlockFace.UP);
            if (l.getType().isAir() || l.getType() == Material.LIGHT) {
                BlockData bd = Bukkit.createBlockData(Material.LIGHT);
                if (bd instanceof Levelled lv) lv.setLevel(Math.min(m.lumiere(), lv.getMaximumLevel()));
                l.setBlockData(bd);
                disp.getPersistentDataContainer().set(cleLumiere, PersistentDataType.STRING, l.getX() + "," + l.getY() + "," + l.getZ());
            }
        }
        if (m.aHitboxEntite()) {
            Location bas = new Location(w, origine.getX() + 0.5, origine.getY(), origine.getZ() + 0.5);
            String lien = disp.getUniqueId().toString();
            Interaction hb = w.spawn(bas, Interaction.class, e -> {
                e.setInteractionWidth(m.largeur());
                e.setInteractionHeight(m.hauteur());
                e.setResponsive(true);
                e.setPersistent(true);
                e.getPersistentDataContainer().set(cleLien, PersistentDataType.STRING, lien);
            });
            disp.getPersistentDataContainer().set(cleHitbox, PersistentDataType.STRING, hb.getUniqueId().toString());
        }
        return disp;
    }

    private void appliquerVisuel(ItemDisplay e, DefinitionObjet d) {
        DefinitionMeuble m = d.meuble();
        e.setItemStack(plugin.objets().fabriquer(d, 1));
        e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
        float t = m.taille();
        e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(t, t, t), new AxisAngle4f()));
        e.setBrightness(m.luminosite() >= 0 ? new Display.Brightness(m.luminosite(), m.luminosite()) : null);
        e.getPersistentDataContainer().set(cleVersion, PersistentDataType.STRING, Integer.toHexString(d.hashCode()));
    }

    /** Met à jour l'apparence d'un meuble posé si son objet a changé. */
    public boolean rafraichir(ItemDisplay e) {
        DefinitionObjet d = definition(e);
        if (d == null || d.meuble() == null) return false;
        String v = e.getPersistentDataContainer().get(cleVersion, PersistentDataType.STRING);
        if (Integer.toHexString(d.hashCode()).equals(v)) return false;
        appliquerVisuel(e, d);
        return true;
    }

    /** Rafraîchit tous les meubles des chunks chargés (après /vorace reload). */
    public int rafraichirTout() {
        int n = 0;
        for (World w : Bukkit.getWorlds()) {
            for (ItemDisplay e : w.getEntitiesByClass(ItemDisplay.class)) {
                if (id(e) != null && rafraichir(e)) n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ cassage

    /**
     * Casse un meuble.
     * @param p      le joueur qui casse (null : commande ou plugin)
     * @param rendre rendre l'objet (sauf en créatif ou si « butin: false »)
     */
    public boolean casser(Player p, ItemDisplay disp, boolean rendre) {
        DefinitionObjet d = definition(disp);
        Block origine = origine(disp);
        if (p != null && origine != null) {
            BlockBreakEvent ev = new BlockBreakEvent(origine, p);
            simulation = true;
            try {
                Bukkit.getPluginManager().callEvent(ev);
            } finally {
                simulation = false;
            }
            if (ev.isCancelled()) return false;
        }
        VoraceMeubleCasseEvent api = new VoraceMeubleCasseEvent(p, id(disp), disp);
        Bukkit.getPluginManager().callEvent(api);
        if (api.isCancelled()) return false;

        Location centre = origine == null ? disp.getLocation() : new Location(disp.getWorld(), origine.getX() + 0.5, origine.getY() + 0.5, origine.getZ() + 0.5);
        ItemStack butin = d != null ? plugin.objets().fabriquer(d, 1) : disp.getItemStack();
        boolean donner = rendre && (p == null || p.getGameMode() != GameMode.CREATIVE) && (d == null || d.meuble() == null || d.meuble().butin());
        String son = d != null && d.meuble() != null ? d.meuble().sonCasse() : "block.wood.break";
        supprimer(disp, centre);
        if (donner && butin != null && !butin.getType().isAir()) disp.getWorld().dropItemNaturally(centre, butin);
        son(centre, son);
        return true;
    }

    /** Retire le meuble et tout ce qui va avec (barrières, lumière, hitbox, sièges, contenu du coffre lâché par terre). */
    private void supprimer(ItemDisplay disp, Location centre) {
        PersistentDataContainer pdc = disp.getPersistentDataContainer();
        World w = disp.getWorld();
        for (int[] c : Geometrie.lireBlocs(pdc.get(cleBlocs, PersistentDataType.STRING))) {
            Block b = w.getBlockAt(c[0], c[1], c[2]);
            if (b.getType() == Material.BARRIER) b.setType(Material.AIR);
        }
        for (int[] c : Geometrie.lireBlocs(pdc.get(cleLumiere, PersistentDataType.STRING))) {
            Block b = w.getBlockAt(c[0], c[1], c[2]);
            if (b.getType() == Material.LIGHT) b.setType(Material.AIR);
        }
        String hb = pdc.get(cleHitbox, PersistentDataType.STRING);
        if (hb != null) {
            Entity e = Bukkit.getEntity(UUID.fromString(hb));
            if (e != null) e.remove();
        }
        String id = disp.getUniqueId().toString();
        for (ItemDisplay s : new ArrayList<>(sieges)) {
            if (id.equals(s.getPersistentDataContainer().get(cleSiege, PersistentDataType.STRING))) {
                s.eject();
                s.remove();
                sieges.remove(s);
            }
        }
        // coffre : on ferme pour tout le monde puis on lâche le contenu
        Inventory ouvert = stockagesOuverts.remove(disp.getUniqueId());
        ItemStack[] contenu;
        if (ouvert != null) {
            contenu = ouvert.getContents();
            ouvert.clear();
            for (HumanEntity h : new ArrayList<>(ouvert.getViewers())) h.closeInventory();
        } else {
            contenu = lireStockage(disp);
        }
        for (ItemStack it : contenu) if (it != null && !it.getType().isAir()) w.dropItemNaturally(centre, it);
        disp.remove();
    }

    public Block origine(ItemDisplay disp) {
        List<int[]> o = Geometrie.lireBlocs(disp.getPersistentDataContainer().get(cleOrigine, PersistentDataType.STRING));
        return o.isEmpty() ? null : disp.getWorld().getBlockAt(o.get(0)[0], o.get(0)[1], o.get(0)[2]);
    }

    private float yaw(ItemDisplay disp) {
        try {
            return Float.parseFloat(disp.getPersistentDataContainer().get(cleYaw, PersistentDataType.STRING));
        } catch (RuntimeException e) {
            return disp.getLocation().getYaw();
        }
    }

    // ------------------------------------------------------------------ interaction

    /** Clic droit sur un meuble. Faux si le meuble ne fait rien (le clic suit alors son cours normal). */
    public boolean interagir(Player p, ItemDisplay disp, Location point) {
        DefinitionObjet d = definition(disp);
        if (d == null || d.meuble() == null || !d.meuble().interactif()) return false;
        DefinitionMeuble m = d.meuble();
        for (String c : m.commandes()) EcouteurObjets.executer(p, c);
        if (m.stockage() > 0) {
            ouvrirStockage(p, disp, m);
        } else if (!m.sieges().isEmpty()) {
            asseoir(p, disp, m, point == null ? p.getLocation() : point);
        }
        return true;
    }

    private void asseoir(Player p, ItemDisplay disp, DefinitionMeuble m, Location point) {
        if (p.getVehicle() != null) return;
        Block o = origine(disp);
        if (o == null) return;
        float yaw = yaw(disp);
        String lien = disp.getUniqueId().toString();
        Location choisi = null;
        double dist = Double.MAX_VALUE;
        for (Point s : m.sieges()) {
            Point r = Geometrie.tourner(s, yaw);
            Location l = new Location(disp.getWorld(), o.getX() + 0.5 + r.x(), o.getY() + r.y(), o.getZ() + 0.5 + r.z(), yaw, 0f);
            boolean occupe = false;
            for (ItemDisplay x : sieges) {
                if (lien.equals(x.getPersistentDataContainer().get(cleSiege, PersistentDataType.STRING))
                    && x.getLocation().distanceSquared(l) < 0.01 && !x.getPassengers().isEmpty()) { occupe = true; break; }
            }
            if (occupe) continue;
            double dd = l.distanceSquared(point);
            if (dd < dist) { dist = dd; choisi = l; }
        }
        if (choisi == null) { refus(p, "Toutes les places sont prises."); return; }
        ItemDisplay siege = disp.getWorld().spawn(choisi, ItemDisplay.class, e -> {
            e.setPersistent(false);
            e.getPersistentDataContainer().set(cleSiege, PersistentDataType.STRING, lien);
        });
        sieges.add(siege);
        siege.addPassenger(p);
    }

    /** Un joueur descend d'un siège : on retire le siège et on le pose au-dessus du meuble. */
    void descendre(Entity joueur, Entity vehicule) {
        if (!(vehicule instanceof ItemDisplay s) || !sieges.contains(s)) return;
        sieges.remove(s);
        Location l = s.getLocation();
        Bukkit.getScheduler().runTask(plugin, () -> {
            s.remove();
            if (!joueur.isValid()) return;
            Block b = l.getBlock();
            for (int i = 0; i < 4; i++) {
                if (!b.isSolid() && !b.getRelative(BlockFace.UP).isSolid()) break;
                b = b.getRelative(BlockFace.UP);
            }
            Location jl = joueur.getLocation();
            joueur.teleport(new Location(l.getWorld(), l.getX(), Math.max(l.getY(), b.getY()), l.getZ(), jl.getYaw(), jl.getPitch()));
        });
    }

    /** Sécurité : retire les sièges vides (toutes les 5 secondes). */
    public void nettoyerSieges() {
        sieges.removeIf(s -> {
            if (s.isValid() && !s.getPassengers().isEmpty()) return false;
            s.remove();
            return true;
        });
    }

    // ------------------------------------------------------------------ stockage

    private void ouvrirStockage(Player p, ItemDisplay disp, DefinitionMeuble m) {
        Inventory inv = stockagesOuverts.get(disp.getUniqueId());
        if (inv == null) {
            StockageMeuble holder = new StockageMeuble(disp.getUniqueId());
            DefinitionObjet d = definition(disp);
            String titre = m.titreStockage() != null ? m.titreStockage() : d != null && d.nom() != null ? d.nom() : "Rangement";
            inv = Bukkit.createInventory(holder, m.stockage() * 9, Messages.objet(titre));
            holder.inventaire = inv;
            ItemStack[] contenu = lireStockage(disp);
            ItemStack[] tableau = new ItemStack[inv.getSize()];
            for (int i = 0; i < Math.min(contenu.length, tableau.length); i++) tableau[i] = contenu[i];
            inv.setContents(tableau);
            // objets en trop si le coffre a rapetissé : lâchés par terre
            for (int i = tableau.length; i < contenu.length; i++) {
                if (contenu[i] != null) disp.getWorld().dropItemNaturally(disp.getLocation(), contenu[i]);
            }
            stockagesOuverts.put(disp.getUniqueId(), inv);
        }
        p.openInventory(inv);
    }

    /** Appelé quand un joueur ferme un coffre de meuble. */
    void fermerStockage(StockageMeuble holder, Inventory inv, int spectateursRestants) {
        Entity e = Bukkit.getEntity(holder.meuble);
        if (e instanceof ItemDisplay d && id(d) != null) ecrireStockage(d, inv.getContents());
        if (spectateursRestants <= 0) stockagesOuverts.remove(holder.meuble);
    }

    /** Enregistre les coffres encore ouverts (arrêt du serveur). */
    public void arreter() {
        for (Map.Entry<UUID, Inventory> en : new ArrayList<>(stockagesOuverts.entrySet())) {
            Entity e = Bukkit.getEntity(en.getKey());
            if (e instanceof ItemDisplay d) ecrireStockage(d, en.getValue().getContents());
            for (HumanEntity h : new ArrayList<>(en.getValue().getViewers())) h.closeInventory();
        }
        stockagesOuverts.clear();
        for (ItemDisplay s : sieges) { s.eject(); s.remove(); }
        sieges.clear();
    }

    ItemStack[] lireStockage(ItemDisplay disp) {
        byte[] data = disp.getPersistentDataContainer().get(cleStockage, PersistentDataType.BYTE_ARRAY);
        if (data == null || data.length == 0) return new ItemStack[0];
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            int taille = in.readInt();
            int n = in.readInt();
            ItemStack[] r = new ItemStack[Math.max(0, Math.min(taille, 54))];
            for (int i = 0; i < n; i++) {
                int slot = in.readInt();
                byte[] b = new byte[in.readInt()];
                in.readFully(b);
                if (slot >= 0 && slot < r.length) r[slot] = ItemStack.deserializeBytes(b);
            }
            return r;
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().warning("Contenu du meuble " + id(disp) + " illisible : " + e);
            return new ItemStack[0];
        }
    }

    void ecrireStockage(ItemDisplay disp, ItemStack[] contenu) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bos);
            List<Integer> pleins = new ArrayList<>();
            for (int i = 0; i < contenu.length; i++) if (contenu[i] != null && !contenu[i].getType().isAir()) pleins.add(i);
            out.writeInt(contenu.length);
            out.writeInt(pleins.size());
            for (int i : pleins) {
                byte[] b = contenu[i].serializeAsBytes();
                out.writeInt(i);
                out.writeInt(b.length);
                out.write(b);
            }
            out.flush();
            disp.getPersistentDataContainer().set(cleStockage, PersistentDataType.BYTE_ARRAY, bos.toByteArray());
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().warning("Impossible d'enregistrer le contenu du meuble " + id(disp) + " : " + e);
        }
    }

    // ------------------------------------------------------------------ divers

    private static void refus(Player p, String message) {
        p.sendActionBar(Messages.objet("<red>" + message));
    }

    private static void son(Location l, String son) {
        if (son == null || son.isBlank() || l.getWorld() == null) return;
        try {
            l.getWorld().playSound(l, son.contains(":") ? son : "minecraft:" + son, 1f, 1f);
        } catch (RuntimeException ignore) {
            // son inconnu : pas grave
        }
    }

    public int nombreSieges() { return sieges.size(); }

    public Collection<ItemDisplay> siegesActifs() { return sieges; }
}
