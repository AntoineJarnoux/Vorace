package fr.vorace.modeles;

import fr.vorace.Vorace;
import fr.vorace.modeles.Animateur.Pose;
import fr.vorace.modeles.ModeleBB.Animation;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Modèles animés (rôle de ModelEngine) : chaque os d'un .bbmodel est une entité d'affichage dont la transformation
 * suit l'animation. Les entités d'affichage n'existent que si un joueur est à portée (modeles.distance-affichage) ;
 * les animations se fondent entre elles, les dégâts et attaques se jouent par-dessus la marche, la tête suit le regard. Un modèle est soit posé (une entité Interaction sert de base et de hitbox, sauvegardée avec
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
    private final NamespacedKey clePose, cleMob, clePartie, cleTaille;
    /** Réglages (config.yml, section modeles). */
    private int intervalle = 2, distance = 48, fonduTics = 4;
    private boolean teinteDegats = true, suiviTete = true, effetsActifs = true;
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
        cleTaille = new NamespacedKey(plugin, "modele_taille");
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
        lireReglages();
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
        lireReglages();
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
        Float t = mob.getPersistentDataContainer().get(cleTaille, PersistentDataType.FLOAT);
        if (t != null) i.taille = Math.max(0.05f, Math.min(16f, t));
        instances.put(mob.getUniqueId(), i);
        i.reconstruire();
        return i;
    }

    public void retirer(Instance i) {
        i.supprimerParties();
        instances.remove(i.base.getUniqueId());
        if (i.mob) {
            i.base.getPersistentDataContainer().remove(cleMob);
            i.base.getPersistentDataContainer().remove(cleTaille);
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
            if (e instanceof Display && pdc.has(clePartie, PersistentDataType.BYTE)) { e.remove(); continue; } // reste d'un arrêt brutal
            if (instances.containsKey(e.getUniqueId())) continue;
            String pose = pdc.get(clePose, PersistentDataType.STRING);
            if (pose != null && e instanceof Interaction) {
                String[] p = pose.split("\\|", -1);
                ModeleBB m = modele(p[0]);
                if (m == null) continue;
                float yaw = 0;
                try { yaw = p.length > 2 ? Float.parseFloat(p[2]) : e.getLocation().getYaw(); } catch (NumberFormatException ignore) { }
                Instance i = new Instance(m, e, false, yaw);
                try { if (p.length > 3) i.taille = Math.max(0.05f, Math.min(16f, Float.parseFloat(p[3]))); } catch (NumberFormatException ignore) { }
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
                Float t = pdc.get(cleTaille, PersistentDataType.FLOAT);
                if (t != null) i.taille = Math.max(0.05f, Math.min(16f, t));
                instances.put(e.getUniqueId(), i);
                i.reconstruire();
            }
        }
    }

    // ------------------------------------------------------------------ animation

    /** Relit les réglages de la section « modeles » de config.yml. */
    void lireReglages() {
        try {
            intervalle = Math.max(1, Math.min(5, plugin.getConfig().getInt("modeles.intervalle-tics", 2)));
            distance = Math.max(0, plugin.getConfig().getInt("modeles.distance-affichage", 48));
            fonduTics = (int) Math.round(Math.max(0, Math.min(2, plugin.getConfig().getDouble("modeles.fondu-secondes", 0.2))) * 20);
            teinteDegats = plugin.getConfig().getBoolean("modeles.teinte-degats", true);
            suiviTete = plugin.getConfig().getBoolean("modeles.suivi-tete", true);
            effetsActifs = plugin.getConfig().getBoolean("modeles.effets", true);
        } catch (Throwable t) {
            intervalle = 2;
        }
    }

    private void tic() {
        tic++;
        boolean verifier = tic % 10 == 0;
        Map<World, List<Player>> joueurs = new HashMap<>();
        for (Instance i : new ArrayList<>(instances.values())) {
            try {
                if (verifier || i.distanceJoueur < 0) i.verifierDistance(joueurs);
                if (i.endormi) {
                    // personne ne regarde : aucune entité d'affichage, aucun calcul ; seulement la fin du modèle
                    if (i.fixe != null || !i.base.isValid() || i.base.isDead()) {
                        i.supprimerParties();
                        instances.remove(i.base.getUniqueId());
                    }
                    continue;
                }
                int iv = i.lointain ? Math.min(10, intervalle * 2) : intervalle;
                if (!i.mettreAJour(iv)) {
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

    /** Rôles automatiques joués par-dessus l'animation en cours (seuls les os qu'ils animent changent). */
    static final java.util.Set<String> PAR_DESSUS = java.util.Set.of("degat", "attaque", "clic");

    /** Un modèle en jeu. */
    public final class Instance {
        final ModeleBB modele;
        final Entity base;
        final boolean mob;
        float yaw;
        ItemDisplay[] parties = new ItemDisplay[0];
        private TextDisplay[] etiquettes = new TextDisplay[0];
        private ItemStack[] objets = new ItemStack[0], rouges;
        private final Transformation[] envoyees;
        private final boolean[] estEtiquette;
        private final boolean aEtiquettes;
        /** Animation demandée (commande, API, clic, dégâts) ; null = animation automatique. */
        private String forcee;
        private boolean forceeBoucle, forceePartielle;
        private long debutForcee;
        private String auto;
        private long debutAuto;
        /** Animation complète affichée, et la précédente pendant le fondu. */
        private String pleine, avant;
        private long debutPleine, debutAvant, debutFondu = Long.MIN_VALUE / 2;
        private boolean pleineBoucle, avantBoucle;
        /** Derniers temps vus pour les clés d'effet (animation complète, animation par-dessus). */
        private double tEffetPleine = -1e-9, tEffetDessus = -1e-9;
        private String effetDessusCle;
        private Location derniere;
        /** Mort : la base n'existe plus, le modèle reste ici le temps de l'animation. */
        private Location fixe;
        /** Joueurs qui voient le modèle ; null = tout le monde. */
        private java.util.Set<UUID> spectateurs;
        private float taille = 1f;
        /** Personne à portée : les entités d'affichage sont retirées. */
        boolean endormi = true;
        /** Joueur le plus proche au-delà de la moitié de la distance d'affichage : images deux fois moins souvent. */
        boolean lointain;
        double distanceJoueur = -1;
        private long finTeinte = -1;
        private Pose[] dernieresPoses;
        /** Regard imposé par l'API (degrés, par rapport au corps) ; NaN = celui du mob. */
        private float regardYaw = Float.NaN, regardPitch = Float.NaN;

        Instance(ModeleBB modele, Entity base, boolean mob, float yaw) {
            this.modele = modele;
            this.base = base;
            this.mob = mob;
            this.yaw = yaw;
            int n = modele.parties().size();
            this.envoyees = new Transformation[n];
            this.estEtiquette = new boolean[n];
            boolean e = false;
            for (int k = 0; k < n; k++) { estEtiquette[k] = modele.parties().get(k).etiquette(); e |= estEtiquette[k]; }
            this.aEtiquettes = e;
        }

        public ModeleBB modele() { return modele; }
        public Entity base() { return base; }
        public boolean surMob() { return mob; }
        public String animation() { return forcee != null ? forcee : auto; }
        public float taille() { return taille; }
        /** Vrai si aucun joueur n'est à portée (le modèle n'a pas d'entité d'affichage en ce moment). */
        public boolean endormi() { return endormi; }

        /** Seuls ces joueurs voient le modèle (null ou vide : tout le monde). */
        public void visiblePour(Collection<? extends Player> joueurs) {
            spectateurs = joueurs == null || joueurs.isEmpty() ? null : new java.util.HashSet<>(joueurs.stream().map(Player::getUniqueId).toList());
            appliquerVisibilite();
            distanceJoueur = -1; // revérifie la portée avec les nouveaux spectateurs
        }

        public boolean prive() { return spectateurs != null; }

        public boolean voit(UUID joueur) { return spectateurs == null || spectateurs.contains(joueur); }

        private List<Display> affichages() {
            List<Display> l = new ArrayList<>(parties.length + etiquettes.length);
            for (ItemDisplay d : parties) if (d != null && d.isValid()) l.add(d);
            for (TextDisplay d : etiquettes) if (d != null && d.isValid()) l.add(d);
            return l;
        }

        void appliquerVisibilite() {
            for (Display d : affichages()) cacher(d);
        }

        private void cacher(Display d) {
            d.setVisibleByDefault(spectateurs == null);
            if (spectateurs != null) for (UUID u : spectateurs) {
                Player j = Bukkit.getPlayer(u);
                if (j != null) j.showEntity(plugin, d);
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
            String vue = endormi ? "endormi (aucun joueur à " + distance + " blocs)" : lointain ? "lointain" : "proche";
            return "animation=" + animation() + (forcee != null ? " (demandée" + (forceeBoucle ? ", en boucle" : "") + (forceePartielle ? ", par-dessus" : "") + ")" : " (auto)")
                + " t=" + String.format(Locale.ROOT, "%.2f", tAnim / 20.0) + "s parties=" + n + "/" + modele.parties().stream().filter(ModeleBB.Partie::visible).count()
                + " taille=" + String.format(Locale.ROOT, "%.2f", taille) + " vue=" + vue
                + " empreinte=" + String.format(Locale.ROOT, "%.4f", somme);
        }

        // -------------------------------------------------------------- portée

        /** Joueur le plus proche (parmi ceux qui voient le modèle) : endort ou réveille le modèle. */
        void verifierDistance(Map<World, List<Player>> cache) {
            Location l = position();
            World w = l.getWorld();
            double meilleur = Double.MAX_VALUE;
            if (w != null) {
                for (Player j : cache.computeIfAbsent(w, World::getPlayers)) {
                    if (!voit(j.getUniqueId())) continue;
                    meilleur = Math.min(meilleur, j.getLocation().distanceSquared(l));
                }
            }
            distanceJoueur = meilleur == Double.MAX_VALUE ? Double.MAX_VALUE : Math.sqrt(meilleur);
            if (distance <= 0) {
                lointain = false;
                if (endormi) reveiller();
                return;
            }
            // un peu de marge pour ne pas faire apparaître et disparaître le modèle à chaque pas à la limite
            double rayon = distance + modele.largeur() / 16.0 * taille;
            if (endormi ? distanceJoueur <= rayon : distanceJoueur <= rayon + 4) {
                lointain = distanceJoueur > rayon / 2;
                if (endormi) reveiller();
            } else if (!endormi) {
                supprimerParties();
                endormi = true;
            }
        }

        private void reveiller() {
            endormi = false;
            reconstruire();
        }

        private float porteeVue() {
            // view_range 1 = 64 blocs (réglage « distance des entités » du joueur à 100 %)
            return distance <= 0 ? 2f : (float) Math.max(0.5, (distance + 8) / 64.0);
        }

        // -------------------------------------------------------------- entités

        void reconstruire() {
            supprimerParties();
            if (endormi) return;
            Location l = position();
            l.setYaw(0);
            l.setPitch(0);
            int n = modele.parties().size();
            parties = new ItemDisplay[n];
            etiquettes = new TextDisplay[aEtiquettes ? n : 0];
            objets = new ItemStack[n];
            rouges = null;
            finTeinte = -1;
            java.util.Arrays.fill(envoyees, null);
            float vue = porteeVue();
            for (int k = 0; k < n; k++) {
                if (!modele.parties().get(k).visible()) continue;
                String id = GenerateurModeles.idPartie(modele.id(), k);
                ItemStack objet = plugin.objets().objetVisuel(id);
                objets[k] = objet;
                parties[k] = l.getWorld().spawn(l, ItemDisplay.class, d -> {
                    d.setPersistent(false);
                    d.setItemStack(objet);
                    d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                    d.getPersistentDataContainer().set(clePartie, PersistentDataType.BYTE, (byte) 1);
                    d.setTeleportDuration(2);
                    d.setViewRange(vue);
                    if (spectateurs != null) d.setVisibleByDefault(false);
                });
            }
            derniere = l;
            if (spectateurs != null) appliquerVisibilite();
            mettreAJour(1);
        }

        void supprimerParties() {
            for (Display d : affichages()) d.remove();
            parties = new ItemDisplay[0];
            etiquettes = new TextDisplay[0];
        }

        Location position() {
            return fixe != null ? fixe.clone() : base.getLocation();
        }

        // -------------------------------------------------------------- taille, regard, repères

        /** Agrandit le modèle entier (1 = taille du .bbmodel). Gardé avec le monde. */
        public void taille(float t) {
            taille = Math.max(0.05f, Math.min(16f, t));
            if (base instanceof Interaction it) {
                it.setInteractionWidth((float) Math.max(0.3, Math.min(8, modele.largeur() / 16.0 * taille)));
                it.setInteractionHeight((float) Math.max(0.3, Math.min(16, modele.hauteur() / 16.0 * taille)));
            }
            if (mob) {
                if (Math.abs(taille - 1f) < 1e-4) base.getPersistentDataContainer().remove(cleTaille);
                else base.getPersistentDataContainer().set(cleTaille, PersistentDataType.FLOAT, taille);
            } else {
                memoriser();
            }
            java.util.Arrays.fill(envoyees, null);
            distanceJoueur = -1;
        }

        /**
         * Impose le regard des os de tête (degrés par rapport au corps, pitch positif = vers le bas).
         * NaN : la tête suit de nouveau le regard du mob (ou reste droite pour un modèle posé).
         */
        public void regard(float yawRelatif, float pitch) {
            regardYaw = yawRelatif;
            regardPitch = pitch;
        }

        /** Position actuelle d'un os (nom de Blockbench, sans tenir compte des majuscules), ou null s'il n'existe pas. */
        public Location positionOs(String nom) {
            int k = -1;
            for (int i = 0; i < modele.parties().size(); i++) if (modele.parties().get(i).nom().equalsIgnoreCase(nom)) { k = i; break; }
            if (k < 0) return null;
            Pose[] p = dernieresPoses;
            if (p == null || endormi) p = Animateur.posesCompletes(modele, List.of(), yaw, taille, 0f, 0f);
            if (p[k] == null) return null;
            Vector3f t = p[k].translation();
            return position().add(t.x, t.y, t.z);
        }

        // -------------------------------------------------------------- animations

        /** Joue une animation ; boucle = la répéter jusqu'à un autre ordre. Faux si elle n'existe pas. */
        public boolean jouer(String nom, boolean boucle) {
            return demarrer(nom, boucle, false);
        }

        /**
         * Joue une animation par-dessus l'animation en cours : seuls les os qu'elle anime changent (attaquer en marchant).
         * Faux si elle n'existe pas.
         */
        public boolean jouerParDessus(String nom, boolean boucle) {
            return demarrer(nom, boucle, true);
        }

        private boolean demarrer(String nom, boolean boucle, boolean dessus) {
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
            forceePartielle = dessus;
            debutForcee = tic;
            if (!mob) memoriser();
            return true;
        }

        /** Revient aux animations automatiques. */
        public void arreterAnimation() {
            forcee = null;
            forceePartielle = false;
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
                    // une animation demandée en boucle (commande, API) n'est pas coupée par un coup reçu
                    if (PAR_DESSUS.contains(role) && forcee != null && !forceePartielle && forceeBoucle) return false;
                    forcee = n;
                    forceeBoucle = false;
                    forceePartielle = PAR_DESSUS.contains(role);
                    debutForcee = tic;
                    return true;
                }
            }
            return false;
        }

        /** Le mob est touché : animation « degat » par-dessus et teinte rouge un court instant. */
        public void blesser() {
            jouerAuto("degat");
            if (!teinteDegats || endormi) return;
            if (rouges == null) {
                rouges = new ItemStack[objets.length];
                for (int k = 0; k < objets.length; k++) if (objets[k] != null) rouges[k] = Teinte.teinter(objets[k], COULEUR_DEGATS);
            }
            boolean fait = false;
            for (int k = 0; k < parties.length && k < rouges.length; k++) {
                if (parties[k] != null && parties[k].isValid() && rouges[k] != null) { parties[k].setItemStack(rouges[k]); fait = true; }
            }
            if (fait) finTeinte = tic + 8;
        }

        private void finirTeinte() {
            finTeinte = -1;
            for (int k = 0; k < parties.length && k < objets.length; k++) {
                if (parties[k] != null && parties[k].isValid() && objets[k] != null) parties[k].setItemStack(objets[k]);
            }
        }

        /** Le mob est mort : le modèle joue « mort » sur place puis disparaît. */
        public void mourir() {
            fixe = base.getLocation();
            if (finTeinte >= 0) finirTeinte();
            if (!jouerAuto("mort")) fixe = null;
        }

        private void memoriser() {
            base.getPersistentDataContainer().set(clePose, PersistentDataType.STRING,
                modele.id() + "|" + (forcee != null && forceeBoucle && !forceePartielle ? forcee : "") + "|" + yaw
                    + (Math.abs(taille - 1f) < 1e-4 ? "" : "|" + taille));
        }

        Animation trouver(String nom) {
            if (nom == null) return null;
            String n = nom.toLowerCase(Locale.ROOT);
            Animation a = modele.animations().get(n);
            if (a != null) return a;
            for (String alias : AUTO.getOrDefault(n, List.of())) if (modele.animations().containsKey(alias)) return modele.animations().get(alias);
            return null;
        }

        private double tempsPiste(Animation a, long debut, boolean boucle) {
            double t = (tic - debut) / 20.0;
            if (a != null && boucle && a.duree() > 0) t = t % a.duree();
            return t;
        }

        /** Faux quand le modèle doit disparaître. */
        boolean mettreAJour(int intervalle) {
            boolean mort = fixe != null;
            if (!mort && (!base.isValid() || base.isDead())) return false;
            Location l = position();

            float teteYaw = 0, tetePitch = 0;
            if (mob && !mort) {
                LivingEntity le = (LivingEntity) base;
                yaw = corps(le);
                double dx = derniere == null ? 0 : l.getX() - derniere.getX(), dz = derniere == null ? 0 : l.getZ() - derniere.getZ();
                double vitesse = Math.sqrt(dx * dx + dz * dz);
                String voulue = vitesse > 0.15 && trouver("course") != null ? "course" : vitesse > 0.01 ? "marche" : "repos";
                Animation a = trouver(voulue);
                String nom = a == null ? null : a.nom();
                if (nom != null && !nom.equals(auto)) { auto = nom; debutAuto = tic; }
                if (nom == null) auto = null;
                if (suiviTete && Float.isNaN(regardYaw)) {
                    teteYaw = limiter(enDegres(tete(le) - yaw), 75);
                    tetePitch = limiter(l.getPitch(), 45);
                }
            } else if (auto == null && forcee == null) {
                Animation a = trouver("repos");
                if (a != null) { auto = a.nom(); debutAuto = tic; }
            }
            if (!Float.isNaN(regardYaw)) {
                teteYaw = limiter(regardYaw, 90);
                tetePitch = limiter(Float.isNaN(regardPitch) ? 0 : regardPitch, 60);
            }

            // déplacement des parties avec la base
            if (derniere == null || !derniere.getWorld().equals(l.getWorld()) || derniere.distanceSquared(l) > 1e-6) {
                Location c = l.clone();
                c.setYaw(0);
                c.setPitch(0);
                for (Display d : affichages()) d.teleport(c);
                derniere = l;
            }

            if (finTeinte >= 0 && tic >= finTeinte) finirTeinte();
            if (tic % intervalle != 0 && intervalle > 1) return true;

            // fin d'une animation demandée
            if (forcee != null) {
                Animation fa = modele.animations().get(forcee);
                double tf = (tic - debutForcee) / 20.0;
                if (fa == null) {
                    forcee = null;
                } else if (!forceeBoucle && !fa.boucle().equals("loop") && tf >= fa.duree()) {
                    if (mort) return false;
                    if (!fa.boucle().equals("hold") || forceePartielle) forcee = null;
                }
            }

            // animation complète (demandée ou automatique), avec fondu depuis la précédente
            String pNom = forcee != null && !forceePartielle ? forcee : auto;
            long pDebut = forcee != null && !forceePartielle ? debutForcee : debutAuto;
            boolean pBoucle = forcee != null && !forceePartielle && forceeBoucle;
            if (!java.util.Objects.equals(pNom, pleine) || pDebut != debutPleine) {
                if (!java.util.Objects.equals(pNom, pleine) && fonduTics > 0) {
                    avant = pleine;
                    debutAvant = debutPleine;
                    avantBoucle = pleineBoucle;
                    debutFondu = tic;
                }
                pleine = pNom;
                debutPleine = pDebut;
                pleineBoucle = pBoucle;
                tEffetPleine = -1e-9;
            }
            List<Animateur.Couche> couches = new ArrayList<>(3);
            Animation pa = pleine == null ? null : modele.animations().get(pleine);
            double tp = tempsPiste(pa, debutPleine, pleineBoucle);
            float poids = 1f;
            if (tic - debutFondu < fonduTics) {
                Animation aa = avant == null ? null : modele.animations().get(avant);
                couches.add(new Animateur.Couche(aa, tempsPiste(aa, debutAvant, avantBoucle), 1f, false));
                poids = (tic - debutFondu) / (float) fonduTics;
            }
            couches.add(new Animateur.Couche(pa, tp, poids, false));

            // animation par-dessus (dégâts, attaque...) : entre et sort en fondu
            Animation da = null;
            double td = 0;
            if (forcee != null && forceePartielle) {
                da = modele.animations().get(forcee);
                td = tempsPiste(da, debutForcee, forceeBoucle);
                float w = 1f;
                if (fonduTics > 0 && da != null) {
                    w = Math.min(1f, (float) (td * 20 / fonduTics));
                    if (!forceeBoucle && !da.boucle().equals("loop") && !da.boucle().equals("hold")) w = Math.min(w, (float) ((da.duree() - td) * 20 / fonduTics));
                }
                couches.add(new Animateur.Couche(da, td, Math.max(0f, w), true));
            }

            // clés d'effet (sons, particules, scripts)
            if (pa != null) tEffetPleine = effets(pa, tEffetPleine, (tic - debutPleine) / 20.0, pleineBoucle);
            String cleDessus = da == null ? null : da.nom() + "@" + debutForcee;
            if (!java.util.Objects.equals(cleDessus, effetDessusCle)) { effetDessusCle = cleDessus; tEffetDessus = -1e-9; }
            if (da != null) tEffetDessus = effets(da, tEffetDessus, (tic - debutForcee) / 20.0, forceeBoucle);

            Pose[] poses = Animateur.posesCompletes(modele, couches, yaw, taille, teteYaw, tetePitch);
            dernieresPoses = poses;
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
            if (aEtiquettes) etiquettes(poses, l, intervalle);
            return true;
        }

        /** Joue les clés d'effet atteintes depuis le dernier passage ; renvoie le nouveau temps vu. */
        private double effets(Animation a, double avant, double maintenant, boolean boucle) {
            if (!effetsActifs || a.evenements().isEmpty()) return maintenant;
            for (ModeleBB.Evenement e : Animateur.evenements(a, avant, maintenant, boucle)) effet(a, e);
            return maintenant;
        }

        /** Sons et particules joués depuis l'apparition (pour /vorace mob info). */
        private int sonsJoues, particulesJouees;
        public int sonsJoues() { return sonsJoues; }
        public int particulesJouees() { return particulesJouees; }

        private void effet(Animation a, ModeleBB.Evenement e) {
            fr.vorace.api.VoraceModeleEffetEvent ev = new fr.vorace.api.VoraceModeleEffetEvent(base, modele.id(), a.nom(), e.type(), e.valeur());
            Bukkit.getPluginManager().callEvent(ev);
            if (ev.isCancelled()) return;
            Location l = position().add(0, modele.hauteur() / 32.0 * taille, 0);
            World w = l.getWorld();
            if (w == null) return;
            switch (e.type()) {
                case "sound" -> {
                    String son = e.valeur().toLowerCase(Locale.ROOT);
                    sonsJoues++;
                    if (spectateurs == null) w.playSound(l, son, org.bukkit.SoundCategory.NEUTRAL, 1f, 1f);
                    else for (UUID u : spectateurs) { Player j = Bukkit.getPlayer(u); if (j != null) j.playSound(l, son, org.bukkit.SoundCategory.NEUTRAL, 1f, 1f); }
                }
                case "particle" -> {
                    String nom = e.valeur().toLowerCase(Locale.ROOT);
                    if (nom.startsWith("minecraft:")) nom = nom.substring(10);
                    org.bukkit.Particle p = null;
                    try { p = org.bukkit.Registry.PARTICLE_TYPE.get(NamespacedKey.minecraft(nom)); } catch (IllegalArgumentException ignore) { }
                    // particule de Bedrock, inconnue ou qui demande des données : seul l'événement est envoyé
                    if (p == null || p.getDataType() != Void.class) return;
                    particulesJouees++;
                    if (spectateurs == null) w.spawnParticle(p, l, 6, 0.2, 0.2, 0.2, 0.02);
                    else for (UUID u : spectateurs) { Player j = Bukkit.getPlayer(u); if (j != null) j.spawnParticle(p, l, 6, 0.2, 0.2, 0.2, 0.02); }
                }
                default -> { } // « timeline » : seulement l'événement, pour les plugins
            }
        }

        /** Nom du mob (ou de la base) affiché à l'os « tag_ ». */
        private void etiquettes(Pose[] poses, Location l, int intervalle) {
            net.kyori.adventure.text.Component nom = base.customName();
            for (int k = 0; k < etiquettes.length; k++) {
                if (!estEtiquette[k]) continue;
                TextDisplay t = etiquettes[k];
                if (nom == null || poses[k] == null) {
                    if (t != null && t.isValid()) t.remove();
                    etiquettes[k] = null;
                    continue;
                }
                if (t == null || !t.isValid()) {
                    Location c = l.clone();
                    c.setYaw(0);
                    c.setPitch(0);
                    float vue = porteeVue();
                    t = l.getWorld().spawn(c, TextDisplay.class, d -> {
                        d.setPersistent(false);
                        d.setBillboard(Display.Billboard.CENTER);
                        d.getPersistentDataContainer().set(clePartie, PersistentDataType.BYTE, (byte) 1);
                        d.setTeleportDuration(2);
                        d.setViewRange(vue);
                    });
                    cacher(t);
                    etiquettes[k] = t;
                }
                if (!nom.equals(t.text())) t.text(nom);
                Vector3f v = new Vector3f(poses[k].translation()).add(0, 0.2f, 0);
                Transformation tr = new Transformation(v, new Quaternionf(), new Vector3f(1, 1, 1), new Quaternionf());
                if (!tr.equals(t.getTransformation())) {
                    t.setInterpolationDelay(0);
                    t.setInterpolationDuration(intervalle);
                    t.setTransformation(tr);
                }
            }
        }
    }

    private static final int COULEUR_DEGATS = 0xFF6464;

    private static float limiter(float v, float max) {
        return Math.max(-max, Math.min(max, v));
    }

    /** Angle ramené entre -180 et 180. */
    static float enDegres(float a) {
        float r = a % 360f;
        if (r >= 180f) r -= 360f;
        if (r < -180f) r += 360f;
        return r;
    }

    private static float corps(LivingEntity e) {
        try {
            return e.getBodyYaw();
        } catch (Throwable t) {
            return e.getLocation().getYaw();
        }
    }

    /** Méthodes du jeu pour l'orientation de la tête (Paper : noms de Mojang), par classe. */
    private static final Map<Class<?>, java.lang.reflect.Method> POIGNEES = new HashMap<>();
    private static java.lang.reflect.Method rotationTete;
    private static boolean teteIndisponible;

    /** Orientation de la tête d'un mob (où il regarde), ou celle de son entité si le serveur ne la donne pas. */
    private static float tete(LivingEntity e) {
        if (!teteIndisponible) {
            try {
                java.lang.reflect.Method g = POIGNEES.get(e.getClass());
                if (g == null) {
                    g = e.getClass().getMethod("getHandle");
                    POIGNEES.put(e.getClass(), g);
                }
                Object h = g.invoke(e);
                if (rotationTete == null || !rotationTete.getDeclaringClass().isInstance(h)) rotationTete = h.getClass().getMethod("getYHeadRot");
                return ((Number) rotationTete.invoke(h)).floatValue();
            } catch (Throwable t) {
                teteIndisponible = true;
            }
        }
        return e.getLocation().getYaw();
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
