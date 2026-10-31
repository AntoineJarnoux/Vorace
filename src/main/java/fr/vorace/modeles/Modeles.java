package fr.vorace.modeles;

import fr.vorace.Vorace;
import fr.vorace.modeles.Animateur.Pose;
import fr.vorace.modeles.ModeleBB.Animation;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Modèles animés (rôle de ModelEngine) : chaque os d'un .bbmodel est une entité d'affichage dont la transformation
 * suit l'animation. Un modèle est soit posé (une entité Interaction sert de base et de hitbox, sauvegardée avec
 * le monde), soit porté par un mob (rendu invisible) qui joue tout seul « repos », « marche », « attaque »...
 */
public final class Modeles {

    /** Noms d'animations reconnus automatiquement (anglais de ModelEngine et français). */
    public static final Map<String, List<String>> AUTO = Map.of(
        "repos", List.of("idle", "repos", "attente"),
        "marche", List.of("walk", "marche", "walking"),
        "course", List.of("run", "course", "running"),
        "degat", List.of("hurt", "degat", "damage"),
        "mort", List.of("death", "mort", "die"),
        "attaque", List.of("attack", "attaque"),
        "clic", List.of("interact", "clic", "interaction"));

    private final Vorace plugin;
    private final NamespacedKey clePose, cleMob, clePartie;
    private volatile Map<String, ModeleBB> modeles = Map.of();
    private final Map<UUID, Instance> instances = new LinkedHashMap<>();
    private BukkitTask tache;
    private long tic;
    /** Dernière erreur du calcul des animations (affichée par /vorace modele info). */
    private volatile String derniereErreur;
    private boolean erreurJournalisee;

    public Modeles(Vorace plugin) {
        this.plugin = plugin;
        clePose = new NamespacedKey(plugin, "modele");
        cleMob = new NamespacedKey(plugin, "modele_mob");
        clePartie = new NamespacedKey(plugin, "modele_partie");
    }

    public Path dossier() { return plugin.getDataFolder().toPath().resolve("modeles-animes"); }
    public Collection<ModeleBB> tous() { return modeles.values(); }
    public ModeleBB modele(String id) { return id == null ? null : modeles.get(id.toLowerCase(Locale.ROOT)); }
    public Collection<Instance> instances() { return instances.values(); }
    public long tics() { return tic; }
    public boolean actif() { return tache != null && !tache.isCancelled(); }
    public String derniereErreur() { return derniereErreur; }

    // ------------------------------------------------------------------ chargement

    public void charger() {
        installerExemple();
        Map<String, ModeleBB> lus = new LinkedHashMap<>();
        List<String> problemes = new ArrayList<>();
        List<Path> fichiers;
        try (Stream<Path> f = Files.walk(dossier())) {
            fichiers = f.filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".bbmodel")).sorted().toList();
        } catch (IOException e) {
            fichiers = List.of();
        }
        for (Path p : fichiers) {
            String nom = dossier().relativize(p).toString().replace('\\', '/');
            String id = nom.substring(0, nom.length() - ".bbmodel".length()).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_");
            try {
                ModeleBB m = LecteurBBModel.lire(id, Files.readString(p, StandardCharsets.UTF_8), problemes);
                lus.put(id, m);
            } catch (Exception e) {
                problemes.add(nom + " : fichier illisible (" + e.getMessage() + ")");
            }
        }
        modeles = lus;
        problemes.forEach(m -> plugin.getLogger().warning("Modèles animés : " + m));
        plugin.getLogger().info(lus.size() + " modèle(s) animé(s) chargé(s)");
        if (Bukkit.isPrimaryThread()) for (Instance i : new ArrayList<>(instances.values())) i.reconstruire();
    }

    /** Identifiants des objets d'apparence de toutes les parties visibles (pour le pack). */
    public List<String> idsParties() {
        List<String> ids = new ArrayList<>();
        for (ModeleBB m : modeles.values()) {
            for (int i = 0; i < m.parties().size(); i++) if (m.parties().get(i).visible()) ids.add(GenerateurModeles.idPartie(m.id(), i));
        }
        return ids;
    }

    public Map<String, byte[]> fichiersPack() {
        Map<String, byte[]> f = new java.util.TreeMap<>();
        for (ModeleBB m : modeles.values()) f.putAll(GenerateurModeles.generer(m));
        return f;
    }

    public void demarrer() {
        tache = Bukkit.getScheduler().runTaskTimer(plugin, this::tic, 1L, 1L);
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (World w : Bukkit.getWorlds()) charger(w.getEntities());
        });
    }

    public void arreter() {
        if (tache != null) tache.cancel();
        for (Instance i : new ArrayList<>(instances.values())) i.supprimerParties();
        instances.clear();
    }

    // ------------------------------------------------------------------ création

    /** Pose un modèle (sauvegardé avec le monde). */
    public Instance poser(String id, Location ou, float yaw, String animation) {
        ModeleBB m = modele(id);
        if (m == null) return null;
        Location l = ou.clone();
        l.setYaw(yaw);
        l.setPitch(0);
        float largeur = (float) Math.max(0.3, Math.min(8, m.largeur() / 16.0)), hauteur = (float) Math.max(0.3, Math.min(16, m.hauteur() / 16.0));
        Interaction base = l.getWorld().spawn(l, Interaction.class, e -> {
            e.setInteractionWidth(largeur);
            e.setInteractionHeight(hauteur);
            e.setResponsive(true);
            e.getPersistentDataContainer().set(clePose, PersistentDataType.STRING, m.id() + "|" + (animation == null ? "" : animation) + "|" + yaw);
        });
        Instance i = new Instance(m, base, false, yaw);
        instances.put(base.getUniqueId(), i);
        i.reconstruire();
        if (animation != null && !animation.isBlank()) i.jouer(animation, false);
        return i;
    }

    /** Un mob porte le modèle : il devient invisible et le modèle suit ses mouvements. */
    public Instance attacher(LivingEntity mob, String id) {
        ModeleBB m = modele(id);
        if (m == null) return null;
        Instance ancien = instances.get(mob.getUniqueId());
        if (ancien != null) ancien.supprimerParties();
        mob.getPersistentDataContainer().set(cleMob, PersistentDataType.STRING, m.id());
        mob.setInvisible(true);
        Instance i = new Instance(m, mob, true, mob.getLocation().getYaw());
        instances.put(mob.getUniqueId(), i);
        i.reconstruire();
        return i;
    }

    public void retirer(Instance i) {
        i.supprimerParties();
        instances.remove(i.base.getUniqueId());
        if (i.mob) {
            i.base.getPersistentDataContainer().remove(cleMob);
            if (i.base instanceof LivingEntity le) le.setInvisible(false);
        } else {
            i.base.remove();
        }
    }

    public Instance instance(Entity base) {
        return base == null ? null : instances.get(base.getUniqueId());
    }

    /** Instance la plus proche d'un endroit, ou null. */
    public Instance plusProche(Location l, double rayon) {
        Instance meilleur = null;
        double d = rayon * rayon;
        for (Instance i : instances.values()) {
            if (!i.base.getWorld().equals(l.getWorld())) continue;
            double x = i.base.getLocation().distanceSquared(l);
            if (x <= d) { d = x; meilleur = i; }
        }
        return meilleur;
    }

    /** Recrée les modèles des entités chargées (démarrage, chargement d'un chunk). */
    public void charger(Collection<? extends Entity> entites) {
        for (Entity e : entites) {
            PersistentDataContainer pdc = e.getPersistentDataContainer();
            if (e instanceof ItemDisplay && pdc.has(clePartie, PersistentDataType.BYTE)) { e.remove(); continue; } // reste d'un arrêt brutal
            if (instances.containsKey(e.getUniqueId())) continue;
            String pose = pdc.get(clePose, PersistentDataType.STRING);
            if (pose != null && e instanceof Interaction) {
                String[] p = pose.split("\\|", -1);
                ModeleBB m = modele(p[0]);
                if (m == null) continue;
                float yaw = 0;
                try { yaw = p.length > 2 ? Float.parseFloat(p[2]) : e.getLocation().getYaw(); } catch (NumberFormatException ignore) { }
                Instance i = new Instance(m, e, false, yaw);
                instances.put(e.getUniqueId(), i);
                i.reconstruire();
                if (p.length > 1 && !p[1].isBlank()) i.jouer(p[1], false);
                continue;
            }
            String mob = pdc.get(cleMob, PersistentDataType.STRING);
            if (mob != null && e instanceof LivingEntity le && !le.isDead()) {
                ModeleBB m = modele(mob);
                if (m == null) continue;
                Instance i = new Instance(m, e, true, e.getLocation().getYaw());
                instances.put(e.getUniqueId(), i);
                i.reconstruire();
            }
        }
    }

    // ------------------------------------------------------------------ animation

    private void tic() {
        tic++;
        int intervalle;
        try {
            intervalle = Math.max(1, Math.min(5, plugin.getConfig().getInt("modeles.intervalle-tics", 2)));
        } catch (Throwable t) {
            intervalle = 2;
        }
        for (Instance i : new ArrayList<>(instances.values())) {
            try {
                if (!i.mettreAJour(intervalle)) {
                    i.supprimerParties();
                    instances.remove(i.base.getUniqueId());
                }
            } catch (Throwable ex) {
                // une erreur (même de type Error) ne doit jamais figer tous les modèles : on la note une fois et on continue
                derniereErreur = i.modele.id() + " : " + ex;
                if (!erreurJournalisee) {
                    erreurJournalisee = true;
                    plugin.getLogger().log(java.util.logging.Level.WARNING, "Animation du modèle " + i.modele.id() + " impossible", ex);
                }
            }
        }
    }

    /** Un modèle en jeu. */
    public final class Instance {
        final ModeleBB modele;
        final Entity base;
        final boolean mob;
        float yaw;
        ItemDisplay[] parties = new ItemDisplay[0];
        private final Transformation[] envoyees;
        /** Animation demandée (commande, API, clic, dégâts) ; null = animation automatique. */
        private String forcee;
        private boolean forceeBoucle;
        private long debutForcee;
        private String auto;
        private long debutAuto;
        private Location derniere;
        /** Mort : la base n'existe plus, le modèle reste ici le temps de l'animation. */
        private Location fixe;
        /** Joueurs qui voient le modèle ; null = tout le monde. */
        private java.util.Set<UUID> spectateurs;

        Instance(ModeleBB modele, Entity base, boolean mob, float yaw) {
            this.modele = modele;
            this.base = base;
            this.mob = mob;
            this.yaw = yaw;
            this.envoyees = new Transformation[modele.parties().size()];
        }

        public ModeleBB modele() { return modele; }
        public Entity base() { return base; }
        public boolean surMob() { return mob; }
        public String animation() { return forcee != null ? forcee : auto; }

        /** Seuls ces joueurs voient le modèle (null ou vide : tout le monde). */
        public void visiblePour(Collection<? extends org.bukkit.entity.Player> joueurs) {
            spectateurs = joueurs == null || joueurs.isEmpty() ? null : new java.util.HashSet<>(joueurs.stream().map(org.bukkit.entity.Player::getUniqueId).toList());
            appliquerVisibilite();
        }

        public boolean prive() { return spectateurs != null; }

        public boolean voit(UUID joueur) { return spectateurs == null || spectateurs.contains(joueur); }

        void appliquerVisibilite() {
            for (ItemDisplay d : parties) {
                if (d == null || !d.isValid()) continue;
                d.setVisibleByDefault(spectateurs == null);
                if (spectateurs != null) for (UUID u : spectateurs) {
                    org.bukkit.entity.Player j = Bukkit.getPlayer(u);
                    if (j != null) j.showEntity(plugin, d);
                }
            }
        }
        public int partiesValides() {
            int n = 0;
            for (ItemDisplay d : parties) if (d != null && d.isValid()) n++;
            return n;
        }

        /** Résumé de l'état réel des entités d'affichage (pour /vorace modele info). */
        public String etat() {
            double somme = 0;
            int n = 0;
            for (ItemDisplay d : parties) {
                if (d == null || !d.isValid()) continue;
                Transformation tr = d.getTransformation();
                Vector3f v = tr.getTranslation();
                Quaternionf q = tr.getLeftRotation();
                somme += v.x * 1.3 + v.y * 1.7 + v.z * 1.9 + q.x * 2.3 + q.y * 2.9 + q.z * 3.1 + q.w * 3.7;
                n++;
                if (n == 1) {
                    somme += d.getInterpolationDuration() * 1000;
                }
            }
            long tAnim = forcee != null ? tic - debutForcee : tic - debutAuto;
            return "animation=" + animation() + (forcee != null ? " (demandée" + (forceeBoucle ? ", en boucle" : "") + ")" : " (auto)")
                + " t=" + String.format(Locale.ROOT, "%.2f", tAnim / 20.0) + "s parties=" + n + "/" + modele.parties().stream().filter(ModeleBB.Partie::visible).count()
                + " empreinte=" + String.format(Locale.ROOT, "%.4f", somme);
        }

        void reconstruire() {
            supprimerParties();
            Location l = position();
            l.setYaw(0);
            l.setPitch(0);
            int n = modele.parties().size();
            parties = new ItemDisplay[n];
            java.util.Arrays.fill(envoyees, null);
            for (int k = 0; k < n; k++) {
                if (!modele.parties().get(k).visible()) continue;
                String id = GenerateurModeles.idPartie(modele.id(), k);
                parties[k] = l.getWorld().spawn(l, ItemDisplay.class, d -> {
                    d.setPersistent(false);
                    d.setItemStack(plugin.objets().objetVisuel(id));
                    d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                    d.getPersistentDataContainer().set(clePartie, PersistentDataType.BYTE, (byte) 1);
                    d.setTeleportDuration(2);
                    d.setViewRange(2f);
                });
            }
            derniere = l;
            if (spectateurs != null) appliquerVisibilite();
            mettreAJour(1);
        }

        void supprimerParties() {
            for (ItemDisplay d : parties) if (d != null && d.isValid()) d.remove();
            parties = new ItemDisplay[0];
        }

        Location position() {
            return fixe != null ? fixe.clone() : base.getLocation();
        }

        /** Joue une animation ; boucle = la répéter jusqu'à un autre ordre. Faux si elle n'existe pas. */
        public boolean jouer(String nom, boolean boucle) {
            Animation a = trouver(nom);
            if (a == null) return false;
            if (Bukkit.isPrimaryThread()) {
                fr.vorace.api.VoraceModeleAnimationEvent ev = new fr.vorace.api.VoraceModeleAnimationEvent(base, modele.id(), a.nom(), boucle);
                Bukkit.getPluginManager().callEvent(ev);
                if (ev.isCancelled()) return false;
                a = trouver(ev.getAnimation());
                if (a == null) return false;
                boucle = ev.isBoucle();
            }
            forcee = a.nom();
            forceeBoucle = boucle;
            debutForcee = tic;
            if (!mob) memoriser();
            return true;
        }

        /** Revient aux animations automatiques. */
        public void arreterAnimation() {
            forcee = null;
            if (!mob) memoriser();
        }

        /** Joue une animation automatique (dégâts, attaque...) si le modèle l'a. */
        public boolean jouerAuto(String role) {
            for (String n : AUTO.getOrDefault(role, List.of())) {
                if (modele.animations().containsKey(n)) {
                    if (Bukkit.isPrimaryThread()) {
                        fr.vorace.api.VoraceModeleAnimationEvent ev = new fr.vorace.api.VoraceModeleAnimationEvent(base, modele.id(), n, false);
                        Bukkit.getPluginManager().callEvent(ev);
                        if (ev.isCancelled()) return false;
                        Animation choisie = trouver(ev.getAnimation());
                        if (choisie == null) return false;
                        n = choisie.nom();
                    }
                    forcee = n;
                    forceeBoucle = false;
                    debutForcee = tic;
                    return true;
                }
            }
            return false;
        }

        /** Le mob est mort : le modèle joue « mort » sur place puis disparaît. */
        public void mourir() {
            fixe = base.getLocation();
            if (!jouerAuto("mort")) fixe = null;
        }

        private void memoriser() {
            base.getPersistentDataContainer().set(clePose, PersistentDataType.STRING,
                modele.id() + "|" + (forcee != null && forceeBoucle ? forcee : "") + "|" + yaw);
        }

        Animation trouver(String nom) {
            if (nom == null) return null;
            String n = nom.toLowerCase(Locale.ROOT);
            Animation a = modele.animations().get(n);
            if (a != null) return a;
            for (String alias : AUTO.getOrDefault(n, List.of())) if (modele.animations().containsKey(alias)) return modele.animations().get(alias);
            return null;
        }

        /** Faux quand le modèle doit disparaître. */
        boolean mettreAJour(int intervalle) {
            boolean mort = fixe != null;
            if (!mort && (!base.isValid() || base.isDead())) return false;
            Location l = position();

            if (mob && !mort) {
                yaw = corps((LivingEntity) base);
                double dx = derniere == null ? 0 : l.getX() - derniere.getX(), dz = derniere == null ? 0 : l.getZ() - derniere.getZ();
                double vitesse = Math.sqrt(dx * dx + dz * dz);
                String voulue = vitesse > 0.15 && trouver("course") != null ? "course" : vitesse > 0.01 ? "marche" : "repos";
                Animation a = trouver(voulue);
                String nom = a == null ? null : a.nom();
                if (nom != null && !nom.equals(auto)) { auto = nom; debutAuto = tic; }
                if (nom == null) auto = null;
            } else if (auto == null && forcee == null) {
                Animation a = trouver("repos");
                if (a != null) { auto = a.nom(); debutAuto = tic; }
            }

            // déplacement des parties avec la base
            if (derniere == null || !derniere.getWorld().equals(l.getWorld()) || derniere.distanceSquared(l) > 1e-6) {
                Location c = l.clone();
                c.setYaw(0);
                c.setPitch(0);
                for (ItemDisplay d : parties) if (d != null && d.isValid()) d.teleport(c);
                derniere = l;
            }

            if (tic % intervalle != 0 && intervalle > 1) return true;

            Animation a;
            double t;
            if (forcee != null) {
                a = modele.animations().get(forcee);
                t = (tic - debutForcee) / 20.0;
                if (a != null && !forceeBoucle && !a.boucle().equals("loop") && t >= a.duree()) {
                    if (mort) return false;
                    if (!a.boucle().equals("hold")) {
                        forcee = null;
                        a = auto == null ? null : modele.animations().get(auto);
                        t = (tic - debutAuto) / 20.0;
                    }
                } else if (a != null && forceeBoucle && a.duree() > 0) {
                    t = t % a.duree();
                }
            } else {
                a = auto == null ? null : modele.animations().get(auto);
                t = (tic - debutAuto) / 20.0;
            }
            float taille = 1f;
            Pose[] poses = Animateur.poses(modele, a, t, yaw, taille);
            Quaternionf droite = Animateur.demiTour();
            for (int k = 0; k < parties.length; k++) {
                ItemDisplay d = parties[k];
                if (d == null || poses[k] == null || !d.isValid()) continue;
                Pose p = poses[k];
                Transformation tr = new Transformation(p.translation(), p.rotation(), p.echelle(), droite);
                if (tr.equals(envoyees[k])) continue;
                envoyees[k] = tr;
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(intervalle);
                d.setTransformation(tr);
            }
            return true;
        }
    }

    private static float corps(LivingEntity e) {
        try {
            return e.getBodyYaw();
        } catch (Throwable t) {
            return e.getLocation().getYaw();
        }
    }

    // ------------------------------------------------------------------ exemple

    private void installerExemple() {
        try {
            Files.createDirectories(dossier());
            Path liste = plugin.getDataFolder().toPath().resolve("donnees").resolve("exemples.txt");
            List<String> faits = new ArrayList<>(Files.isRegularFile(liste) ? Files.readAllLines(liste) : List.of());
            if (faits.contains("modeles")) return;
            Path cible = dossier().resolve("golem_rubis.bbmodel");
            if (!Files.exists(cible)) {
                try (InputStream in = plugin.getClass().getClassLoader().getResourceAsStream("exemples/modeles/golem_rubis.bbmodel")) {
                    if (in != null) Files.copy(in, cible);
                }
            }
            faits.add("modeles");
            Files.createDirectories(liste.getParent());
            Files.write(liste, faits);
        } catch (IOException e) {
            plugin.getLogger().warning("Impossible de créer l'exemple de modèle animé : " + e.getMessage());
        }
    }

}
