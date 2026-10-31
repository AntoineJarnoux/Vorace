package fr.vorace.pack;

import java.util.ArrayList;
import java.util.List;

/** Ce qui s'est passé pendant une fusion : fusions réussies, conflits, erreurs. */
public final class Journal {

    public enum Niveau { INFO, FUSION, CONFLIT, ERREUR }

    public record Ligne(Niveau niveau, String message) {
        @Override public String toString() { return "[" + niveau + "] " + message; }
    }

    private final List<Ligne> lignes = new ArrayList<>();

    public synchronized void info(String m) { lignes.add(new Ligne(Niveau.INFO, m)); }
    public synchronized void fusion(String m) { lignes.add(new Ligne(Niveau.FUSION, m)); }
    public synchronized void conflit(String m) { lignes.add(new Ligne(Niveau.CONFLIT, m)); }
    public synchronized void erreur(String m) { lignes.add(new Ligne(Niveau.ERREUR, m)); }

    public synchronized List<Ligne> lignes() { return List.copyOf(lignes); }

    public synchronized long compter(Niveau n) {
        return lignes.stream().filter(l -> l.niveau() == n).count();
    }

    public synchronized String texte() {
        StringBuilder sb = new StringBuilder();
        for (Ligne l : lignes) sb.append(l).append('\n');
        return sb.toString();
    }
}
