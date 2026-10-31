package fr.vorace.interfaces;

import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Menu en pages : une liste d'éléments (5 lignes de 9 par page), la ligne du bas pour la navigation et jusqu'à
 * quatre boutons en plus (cases 1, 2, 6, 7 de la ligne du bas).
 */
public final class MenuListe {

    private MenuListe() {}

    public static final int PAR_PAGE = 45;

    public interface ActionElement<T> {
        void clic(org.bukkit.entity.Player p, T element, boolean droit, boolean maj);
    }

    /**
     * @param fabrique recrée le menu à une autre page (pour les boutons de page)
     */
    public static <T> Menu creer(String titre, List<T> elements, int page, Function<T, ItemStack> icone, ActionElement<T> action,
                                 Function<Integer, Menu> fabrique, Supplier<Menu> parent, String vide) {
        int pages = Math.max(1, (elements.size() + PAR_PAGE - 1) / PAR_PAGE);
        int pg = Math.max(0, Math.min(page, pages - 1));
        Menu m = new Menu(titre + (pages > 1 ? " <gray>(" + (pg + 1) + "/" + pages + ")" : ""), 6);
        for (int i = 0; i < PAR_PAGE; i++) {
            int index = pg * PAR_PAGE + i;
            if (index >= elements.size()) break;
            T el = elements.get(index);
            m.bouton(i, icone.apply(el), action == null ? null : (p, d, maj) -> action.clic(p, el, d, maj));
        }
        if (elements.isEmpty() && vide != null) m.bouton(22, Menu.icone("BARRIER", "<gray>Rien ici", vide), null);
        if (pg > 0) m.bouton(m.bas() + 3, Menu.icone("ARROW", "<yellow>« Page précédente"), (p, d, maj) -> fabrique.apply(pg - 1).ouvrir(p));
        if (pg < pages - 1) m.bouton(m.bas() + 5, Menu.icone("ARROW", "<yellow>Page suivante »"), (p, d, maj) -> fabrique.apply(pg + 1).ouvrir(p));
        m.bouton(m.bas() + 4, Menu.icone("BOOK", "<white>" + elements.size() + " élément(s)"), null);
        return m;
    }
}
