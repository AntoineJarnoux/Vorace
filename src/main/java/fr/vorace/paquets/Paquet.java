package fr.vorace.paquets;

import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Un paquet en route. On peut le lire, le modifier ({@link #ecrire}), le remplacer ou l'annuler.
 * <p>
 * Les noms de paquets et de champs sont ceux du jeu (noms officiels de Mojang, utilisés par Paper) :
 * {@code ClientboundSetTimePacket} et son champ {@code dayTime}, par exemple. {@code /vorace paquets journal}
 * les affiche en direct.
 */
public final class Paquet {

    private final Player joueur;
    private final String nomConnexion;
    private final Sens sens;
    private Object brut;
    private boolean annule;

    Paquet(Player joueur, String nomConnexion, Sens sens, Object brut) {
        this.joueur = joueur;
        this.nomConnexion = nomConnexion;
        this.sens = sens;
        this.brut = brut;
    }

    /** Le joueur, ou null pendant la connexion (avant son arrivée dans le monde). */
    public Player joueur() { return joueur; }
    /** Pseudo annoncé à la connexion (connu avant {@link #joueur()}), ou null. */
    public String nomConnexion() { return joueur != null ? joueur.getName() : nomConnexion; }
    public Sens sens() { return sens; }
    /** Nom court du paquet, par exemple « ClientboundSetEntityDataPacket ». */
    public String nom() { return brut.getClass().getSimpleName(); }
    /** L'objet du jeu (classe net.minecraft...). */
    public Object brut() { return brut; }

    public boolean annule() { return annule; }
    /** Le paquet n'est ni envoyé ni reçu. */
    public void annuler() { annule = true; }
    public void setAnnule(boolean a) { annule = a; }

    /** Remplace le paquet par un autre objet paquet du jeu. */
    public void remplacer(Object autre) {
        if (autre == null) throw new IllegalArgumentException("paquet null : utiliser annuler()");
        brut = autre;
    }

    /** Valeur d'un champ (« dayTime »). */
    public Object lire(String champ) {
        return Reflexion.lire(brut, champ);
    }

    /** Valeur d'un champ, convertie. */
    public <T> T lire(String champ, Class<T> type) {
        return type.cast(Reflexion.lire(brut, champ));
    }

    /** Texte simple d'un champ texte du jeu (message, titre...). */
    public String texte(String champ) {
        return Reflexion.texte(Reflexion.lire(brut, champ));
    }

    /**
     * Change un champ. Les paquets « record » (non modifiables) sont recréés automatiquement avec la nouvelle valeur.
     * @return ce paquet, pour enchaîner
     */
    public Paquet ecrire(String champ, Object valeur) {
        brut = Reflexion.avec(brut, champ, valeur);
        return this;
    }

    /** Tous les champs et leurs valeurs, dans l'ordre. */
    public Map<String, Object> champs() {
        return Reflexion.champs(brut);
    }

    @Override
    public String toString() {
        return sens + " " + nom() + " " + champs();
    }
}
