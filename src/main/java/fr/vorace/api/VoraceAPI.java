package fr.vorace.api;

import fr.vorace.Vorace;
import fr.vorace.objets.DefinitionObjet;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Accès simple aux objets de Vorace depuis un autre plugin.
 * <pre>
 * ItemStack epee = VoraceAPI.objet("epee_rubis", 1);
 * String id = VoraceAPI.id(joueur.getInventory().getItemInMainHand());
 * ItemDisplay chaise = VoraceAPI.poserMeuble("chaise", bloc, 90f);
 * </pre>
 */
public final class VoraceAPI {

    private static Vorace plugin;

    private VoraceAPI() {}

    public static void initialiser(Vorace p) { plugin = p; }

    /** Fabrique un objet Vorace, ou null s'il n'existe pas. */
    public static ItemStack objet(String id, int quantite) {
        return plugin == null ? null : plugin.objets().fabriquer(id, quantite);
    }

    /** Identifiant Vorace de cet objet, ou null si ce n'est pas un objet Vorace. */
    public static String id(ItemStack objet) {
        return plugin == null ? null : plugin.objets().id(objet);
    }

    /** Tous les identifiants d'objets. */
    public static List<String> ids() {
        return plugin == null ? List.of() : plugin.objets().toutes().stream().map(d -> d.id()).toList();
    }

    /** Identifiant du meuble Vorace lié à cette entité (le meuble ou sa hitbox), ou null. */
    public static String meuble(Entity e) {
        if (plugin == null || plugin.meubles() == null) return null;
        ItemDisplay d = plugin.meubles().depuisEntite(e);
        return d == null ? null : plugin.meubles().id(d);
    }

    /** Pose un meuble sans vérification (place, protections). Renvoie son entité, ou null si ce n'est pas un meuble. */
    public static ItemDisplay poserMeuble(String id, Block origine, float yaw) {
        if (plugin == null || plugin.meubles() == null) return null;
        DefinitionObjet d = plugin.objets().definition(id);
        return d == null || d.meuble() == null ? null : plugin.meubles().creer(d, origine, yaw);
    }

    /** Casse un meuble (sans le rendre). */
    public static boolean retirerMeuble(Entity e) {
        if (plugin == null || plugin.meubles() == null) return false;
        ItemDisplay d = plugin.meubles().depuisEntite(e);
        return d != null && plugin.meubles().casser(null, d, false);
    }

    /** Identifiant du bloc Vorace à cet endroit, ou null. */
    public static String bloc(Block b) {
        return plugin == null || plugin.blocs() == null ? null : plugin.blocs().id(b);
    }

    /** Pose un bloc Vorace sans vérification. Faux si ce n'est pas un bloc. */
    public static boolean poserBloc(String id, Block b) {
        if (plugin == null || plugin.blocs() == null) return false;
        DefinitionObjet d = plugin.objets().definition(id);
        return d != null && d.bloc() != null && plugin.blocs().placer(b, id);
    }

    // ------------------------------------------------------------------ glyphes (0.5)

    /** Caractère d'un glyphe (à mettre dans n'importe quel texte), ou null. Pensez au blanc (§f) devant si le texte est coloré. */
    public static String glyphe(String id) {
        if (plugin == null || plugin.glyphes() == null) return null;
        fr.vorace.glyphes.Glyphes.Glyphe g = plugin.glyphes().glyphe(id);
        return g == null ? null : g.texte();
    }

    /** Glyphe en composant Adventure (déjà en blanc), ou null. */
    public static net.kyori.adventure.text.Component glypheComposant(String id) {
        return plugin == null || plugin.glyphes() == null ? null : plugin.glyphes().composant(id);
    }

    /** Texte qui décale la suite de n pixels (négatif = vers la gauche). */
    public static String decalage(int pixels) {
        return fr.vorace.glyphes.GenerateurGlyphes.decalage(pixels);
    }

    /** Titre de menu avec un fond personnalisé (glyphe « fond-menu: true »). */
    public static net.kyori.adventure.text.Component titreMenu(String fond, net.kyori.adventure.text.Component titre) {
        return plugin == null || plugin.glyphes() == null ? titre : plugin.glyphes().titreMenu(fond, titre);
    }

    /** Remplace les raccourcis d'emojis (« :coeur: ») que ce joueur a le droit d'utiliser. */
    public static net.kyori.adventure.text.Component emojis(Player joueur, net.kyori.adventure.text.Component texte) {
        return plugin == null || plugin.glyphes() == null ? texte : plugin.glyphes().remplacer(joueur, texte);
    }

    // ------------------------------------------------------------------ modèles animés (0.6)

    /** Pose un modèle animé (fichier modeles-animes/<id>.bbmodel) ; renvoie son entité de base, ou null. */
    public static Entity poserModele(String id, org.bukkit.Location ou, float yaw, String animation) {
        if (plugin == null || plugin.modeles() == null) return null;
        fr.vorace.modeles.Modeles.Instance i = plugin.modeles().poser(id, ou, yaw, animation);
        return i == null ? null : i.base();
    }

    /** Un mob porte le modèle (il devient invisible, « repos », « marche », « attaque », « degat », « mort » sont joués tout seuls). */
    public static boolean attacherModele(org.bukkit.entity.LivingEntity mob, String id) {
        return plugin != null && plugin.modeles() != null && plugin.modeles().attacher(mob, id) != null;
    }

    /** Joue une animation sur le modèle de cette entité (base posée ou mob). Faux si pas de modèle ou animation inconnue. */
    public static boolean jouerAnimation(Entity base, String animation, boolean boucle) {
        if (plugin == null || plugin.modeles() == null) return false;
        fr.vorace.modeles.Modeles.Instance i = plugin.modeles().instance(base);
        return i != null && i.jouer(animation, boucle);
    }

    /**
     * Joue une animation par-dessus celle en cours : seuls les os qu'elle anime changent (attaquer en marchant).
     * Faux si pas de modèle ou animation inconnue.
     */
    public static boolean jouerAnimationParDessus(Entity base, String animation, boolean boucle) {
        if (plugin == null || plugin.modeles() == null) return false;
        fr.vorace.modeles.Modeles.Instance i = plugin.modeles().instance(base);
        return i != null && i.jouerParDessus(animation, boucle);
    }

    /** Agrandit le modèle de cette entité (1 = taille du .bbmodel, gardée avec le monde). Faux si pas de modèle. */
    public static boolean tailleModele(Entity base, float taille) {
        if (plugin == null || plugin.modeles() == null) return false;
        fr.vorace.modeles.Modeles.Instance i = plugin.modeles().instance(base);
        if (i == null) return false;
        i.taille(taille);
        return true;
    }

    /**
     * Position actuelle d'un os du modèle (nom de Blockbench : « main_droite », « tag_nom », « mount »...),
     * pour y accrocher un objet, un passager ou des particules. Null si pas de modèle ou os inconnu.
     */
    public static org.bukkit.Location positionOs(Entity base, String os) {
        if (plugin == null || plugin.modeles() == null) return null;
        fr.vorace.modeles.Modeles.Instance i = plugin.modeles().instance(base);
        return i == null ? null : i.positionOs(os);
    }

    /**
     * Oriente les os de tête (« h_... », « head », « tete ») : degrés par rapport au corps, pitch positif = vers le bas.
     * Float.NaN rend la tête au regard du mob. Faux si pas de modèle.
     */
    public static boolean regardModele(Entity base, float yawRelatif, float pitch) {
        if (plugin == null || plugin.modeles() == null) return false;
        fr.vorace.modeles.Modeles.Instance i = plugin.modeles().instance(base);
        if (i == null) return false;
        i.regard(yawRelatif, pitch);
        return true;
    }

    /** Identifiant du modèle animé de cette entité, ou null. */
    public static String modeleAnime(Entity base) {
        if (plugin == null || plugin.modeles() == null) return null;
        fr.vorace.modeles.Modeles.Instance i = plugin.modeles().instance(base);
        return i == null ? null : i.modele().id();
    }

    /** Retire le modèle (une base posée disparaît, un mob redevient visible). */
    public static boolean retirerModele(Entity base) {
        if (plugin == null || plugin.modeles() == null) return false;
        fr.vorace.modeles.Modeles.Instance i = plugin.modeles().instance(base);
        if (i == null) return false;
        plugin.modeles().retirer(i);
        return true;
    }

    /** Seuls ces joueurs voient le modèle de cette entité (aucun joueur : tout le monde le revoit). Faux si pas de modèle. */
    public static boolean modeleVisiblePour(Entity base, Player... joueurs) {
        if (plugin == null || plugin.modeles() == null) return false;
        fr.vorace.modeles.Modeles.Instance i = plugin.modeles().instance(base);
        if (i == null) return false;
        i.visiblePour(java.util.List.of(joueurs));
        return true;
    }

    // ------------------------------------------------------------------ paquets (0.7)

    /**
     * Écoute des paquets du jeu (rôle de ProtocolLib). Les noms sont les noms courts des classes du jeu, voir
     * {@code /vorace paquets journal}. Sans nom : tous les paquets de ce sens. L'écoute s'arrête toute seule quand
     * votre plugin s'arrête.
     * <pre>
     * VoraceAPI.ecouterPaquets(this, Sens.SORTANT, p -&gt; p.ecrire("dayTime", 6000L), "ClientboundSetTimePacket");
     * </pre>
     * @return l'abonnement (pour l'arrêter avec annuler()), ou null si la couche paquets est désactivée
     */
    public static fr.vorace.paquets.Abonnement ecouterPaquets(org.bukkit.plugin.Plugin votrePlugin, fr.vorace.paquets.Sens sens,
                                                              fr.vorace.paquets.EcouteurPaquet ecouteur, String... paquets) {
        if (plugin == null || plugin.paquets() == null) return null;
        return plugin.paquets().ecouter(votrePlugin, sens, ecouteur, paquets);
    }

    /** Envoie un paquet du jeu (objet net.minecraft...) au joueur ; les écoutes le voient passer. */
    public static void envoyerPaquet(Player joueur, Object paquet) {
        envoyerPaquet(joueur, paquet, true);
    }

    /** Envoie un paquet du jeu au joueur ; avec ecouteurs = faux, les écoutes ne le voient pas. */
    public static void envoyerPaquet(Player joueur, Object paquet, boolean ecouteurs) {
        if (plugin == null || plugin.paquets() == null) throw new IllegalStateException("couche paquets désactivée");
        plugin.paquets().envoyer(joueur, paquet, ecouteurs);
    }

    /** Fait comme si le joueur avait envoyé ce paquet au serveur. */
    public static void recevoirPaquet(Player joueur, Object paquet) {
        if (plugin == null || plugin.paquets() == null) throw new IllegalStateException("couche paquets désactivée");
        plugin.paquets().recevoir(joueur, paquet, true);
    }

    /** Vrai si la couche paquets fonctionne (config « paquets.actif »). */
    public static boolean paquetsActifs() {
        return plugin != null && plugin.paquets() != null;
    }
}
