package fr.vorace.glyphes;

import fr.vorace.Messages;
import fr.vorace.Vorace;
import fr.vorace.pack.Journal;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Glyphes : images dans le texte, emojis du chat, fonds de menus. */
public final class Glyphes {

    /**
     * Un glyphe chargé.
     * @param hauteur hauteur affichée (celle de l'image pour un fond de menu)
     * @param avance  largeur à l'écran en pixels (pour revenir en arrière après un fond de menu)
     */
    public record Glyphe(DefinitionGlyphe def, int code, int hauteur, int avance) {
        public String texte() { return new String(Character.toChars(code)); }
    }

    private static final java.util.regex.Pattern ID_VALIDE = java.util.regex.Pattern.compile("[a-z0-9_.-]+");

    private final Vorace plugin;
    private volatile Map<String, Glyphe> glyphes = Map.of();
    private volatile Map<String, Glyphe> parRaccourci = Map.of();
    private volatile Pattern motif;
    private final Map<String, Integer> codes = new LinkedHashMap<>();
    private final Map<UUID, List<String>> suggestions = new ConcurrentHashMap<>();

    public Glyphes(Vorace plugin) {
        this.plugin = plugin;
    }

    public Path dossier() { return plugin.getDataFolder().toPath().resolve("glyphes"); }
    private Path ressources() { return plugin.getDataFolder().toPath().resolve("ressources"); }
    public Collection<Glyphe> tous() { return glyphes.values(); }
    public Glyphe glyphe(String id) { return id == null ? null : glyphes.get(id.toLowerCase(Locale.ROOT)); }

    // ------------------------------------------------------------------ chargement

    public void charger() {
        installerExemple();
        List<String> problemes = new ArrayList<>();
        Map<String, DefinitionGlyphe> lues = new LinkedHashMap<>();
        List<Path> fichiers;
        try (Stream<Path> f = Files.walk(dossier())) {
            fichiers = f.filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".yml")).sorted().toList();
        } catch (IOException e) {
            fichiers = List.of();
        }
        for (Path p : fichiers) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(p.toFile());
            String nom = dossier().relativize(p).toString().replace('\\', '/');
            for (String id : yml.getKeys(false)) {
                if (!yml.isConfigurationSection(id)) continue;
                String propre = id.toLowerCase(Locale.ROOT);
                if (!ID_VALIDE.matcher(propre).matches()) {
                    problemes.add(nom + " : identifiant invalide « " + id + " » (lettres minuscules, chiffres, _ - .)");
                    continue;
                }
                if (lues.containsKey(propre)) problemes.add(nom + " : glyphe « " + propre + " » déjà défini dans " + lues.get(propre).fichier() + ", remplacé");
                lues.put(propre, DefinitionGlyphe.lire(propre, nom, yml.getConfigurationSection(id), problemes));
            }
        }

        attribuerCodes(lues.keySet(), problemes);
        Map<String, Glyphe> resultat = new LinkedHashMap<>();
        Map<String, Glyphe> raccourcis = new HashMap<>();
        Path textures = ressources().resolve("textures");
        for (DefinitionGlyphe d : lues.values()) {
            Integer code = codes.get(d.id());
            if (code == null) continue;
            Path png = textures.resolve(d.texture() + ".png");
            int hauteur = d.hauteur();
            if (hauteur <= 0) {
                int[] t = GenerateurGlyphes.taille(png);
                hauteur = t == null ? 9 : t[1];
            }
            Glyphe g = new Glyphe(d, code, hauteur, GenerateurGlyphes.avance(png, hauteur));
            if (!Files.isRegularFile(png)) problemes.add(d.fichier() + " : image du glyphe « " + d.id() + " » introuvable (ressources/textures/" + d.texture() + ".png)");
            if (d.montee() > hauteur) problemes.add(d.fichier() + " : glyphe « " + d.id() + " » : montee (" + d.montee() + ") plus grande que la hauteur (" + hauteur + ")");
            resultat.put(d.id(), g);
            for (String r : d.raccourcis()) {
                Glyphe ancien = raccourcis.put(r, g);
                if (ancien != null && ancien != g) problemes.add("raccourci « " + r + " » utilisé par " + ancien.def().id() + " et " + d.id() + " : " + d.id() + " gagne");
            }
        }
        glyphes = resultat;
        parRaccourci = raccourcis;
        List<String> tri = new ArrayList<>(raccourcis.keySet());
        tri.sort(Comparator.comparingInt(String::length).reversed());
        motif = tri.isEmpty() ? null : Pattern.compile(String.join("|", tri.stream().map(Pattern::quote).toList()));
        Messages.resolveur(resolveur());
        problemes.forEach(m -> plugin.getLogger().warning("Glyphes : " + m));
        plugin.getLogger().info(resultat.size() + " glyphe(s) chargé(s)");
        if (Bukkit.isPrimaryThread()) for (Player p : Bukkit.getOnlinePlayers()) mettreAJourSuggestions(p);
    }

    /** Les caractères sont gardés d'un redémarrage à l'autre (donnees/glyphes.txt) : un texte déjà écrit garde son image. */
    private synchronized void attribuerCodes(Collection<String> ids, List<String> problemes) {
        Path f = plugin.getDataFolder().toPath().resolve("donnees").resolve("glyphes.txt");
        codes.clear();
        try {
            if (Files.isRegularFile(f)) {
                for (String l : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                    String[] p = l.trim().split("\\s+");
                    if (p.length == 2) {
                        try { codes.put(p[1], Integer.parseInt(p[0], 16)); } catch (NumberFormatException ignore) { }
                    }
                }
            }
        } catch (IOException e) {
            problemes.add("impossible de lire donnees/glyphes.txt : " + e.getMessage());
        }
        boolean change = false;
        java.util.Set<Integer> pris = new java.util.HashSet<>(codes.values());
        int suivant = GenerateurGlyphes.PREMIER;
        for (String id : ids) {
            if (codes.containsKey(id)) continue;
            while (pris.contains(suivant)) suivant++;
            if (suivant > GenerateurGlyphes.DERNIER) {
                problemes.add("trop de glyphes : « " + id + " » ignoré");
                continue;
            }
            codes.put(id, suivant);
            pris.add(suivant);
            change = true;
        }
        if (change) {
            try {
                Files.createDirectories(f.getParent());
                List<String> lignes = new ArrayList<>();
                codes.forEach((id, c) -> lignes.add(Integer.toHexString(c).toUpperCase(Locale.ROOT) + " " + id));
                Files.write(f, lignes, StandardCharsets.UTF_8);
            } catch (IOException e) {
                problemes.add("impossible d'écrire donnees/glyphes.txt : " + e.getMessage());
            }
        }
    }

    /** Fichiers pour le pack (appelé hors du fil principal pendant la construction). */
    public Map<String, byte[]> fichiersPack(Journal journal) {
        List<GenerateurGlyphes.Glyphe> l = new ArrayList<>();
        for (Glyphe g : glyphes.values()) l.add(new GenerateurGlyphes.Glyphe(g.def().id(), g.code(), g.def().texture(), g.hauteur(), g.def().montee()));
        return GenerateurGlyphes.generer(l, ressources(), journal);
    }

    // ------------------------------------------------------------------ texte

    /** Le glyphe comme composant de texte (en blanc, sinon la couleur du texte teinterait l'image). */
    public Component composant(String id) {
        Glyphe g = glyphe(id);
        return g == null ? null : composant(g);
    }

    public static Component composant(Glyphe g) {
        return Component.text(g.texte()).color(NamedTextColor.WHITE)
            .decoration(TextDecoration.BOLD, false).decoration(TextDecoration.ITALIC, false)
            .decoration(TextDecoration.OBFUSCATED, false).decoration(TextDecoration.UNDERLINED, false)
            .decoration(TextDecoration.STRIKETHROUGH, false);
    }

    public static boolean autorise(Player p, Glyphe g) {
        return p == null || g.def().permission() == null || p.hasPermission(g.def().permission());
    }

    /** Remplace les raccourcis (« :coeur: ») que ce joueur a le droit d'utiliser. */
    public Component remplacer(Player p, Component texte) {
        Pattern m = motif;
        if (m == null || texte == null) return texte;
        Map<String, Glyphe> r = parRaccourci;
        return texte.replaceText(TextReplacementConfig.builder().match(m).replacement((resultat, b) -> {
            Glyphe g = r.get(resultat.group());
            return g != null && autorise(p, g) ? composant(g) : Component.text(resultat.group());
        }).build());
    }

    /** Texte simple : raccourcis remplacés par les caractères des glyphes. */
    public String remplacer(Player p, String texte) {
        Pattern m = motif;
        if (m == null || texte == null) return texte;
        Map<String, Glyphe> r = parRaccourci;
        return m.matcher(texte).replaceAll(res -> {
            Glyphe g = r.get(res.group());
            return java.util.regex.Matcher.quoteReplacement(g != null && autorise(p, g) ? g.texte() : res.group());
        });
    }

    /**
     * Titre de menu avec un fond : le glyphe est placé en haut à gauche de la fenêtre,
     * puis le titre s'écrit à sa place normale par-dessus.
     */
    public Component titreMenu(String id, Component titre) {
        Glyphe g = glyphe(id);
        Component t = titre == null ? Component.empty() : titre;
        if (g == null) return t;
        return Component.text()
            .append(Component.text(GenerateurGlyphes.decalage(-8)))
            .append(composant(g))
            .append(Component.text(GenerateurGlyphes.decalage(8 - g.avance())))
            .append(t)
            .build();
    }

    /** Balises MiniMessage : <glyphe:coeur> (ou <g:coeur>) et <decalage:-8>. */
    public TagResolver resolveur() {
        return TagResolver.resolver(
            TagResolver.resolver(Set.of("glyphe", "g"), (args, ctx) -> {
                String id = args.hasNext() ? args.pop().value() : "";
                Component c = composant(id);
                return Tag.selfClosingInserting(c != null ? c : Component.text("[" + id + "?]", NamedTextColor.RED));
            }),
            TagResolver.resolver(Set.of("decalage", "shift"), (args, ctx) -> {
                int n = 0;
                try { if (args.hasNext()) n = Integer.parseInt(args.pop().value().trim()); } catch (NumberFormatException ignore) { }
                return Tag.selfClosingInserting(Component.text(GenerateurGlyphes.decalage(n)));
            }));
    }

    // ------------------------------------------------------------------ suggestions du chat

    /** Les raccourcis apparaissent dans les suggestions du chat (touche Tab). */
    public void mettreAJourSuggestions(Player p) {
        try {
            List<String> anciens = suggestions.remove(p.getUniqueId());
            if (anciens != null && !anciens.isEmpty()) p.removeCustomChatCompletions(anciens);
            List<String> nouveaux = new ArrayList<>();
            for (Map.Entry<String, Glyphe> e : parRaccourci.entrySet()) {
                if (e.getKey().startsWith(":") && autorise(p, e.getValue())) nouveaux.add(e.getKey());
            }
            if (!nouveaux.isEmpty()) {
                p.addCustomChatCompletions(nouveaux);
                suggestions.put(p.getUniqueId(), nouveaux);
            }
        } catch (Throwable ignore) {
            // version sans suggestions personnalisées
        }
    }

    public void oublier(Player p) {
        suggestions.remove(p.getUniqueId());
    }

    // ------------------------------------------------------------------ exemples

    private void installerExemple() {
        try {
            Files.createDirectories(dossier());
            Path liste = plugin.getDataFolder().toPath().resolve("donnees").resolve("exemples.txt");
            List<String> faits = new ArrayList<>(Files.isRegularFile(liste) ? Files.readAllLines(liste) : List.of());
            if (faits.contains("glyphes")) return;
            copier("exemples/glyphes.yml", dossier().resolve("exemple-glyphes.yml"));
            for (String t : List.of("coeur", "etoile", "sourire", "feu", "fond_coffre")) {
                copier("exemples/glyphes/" + t + ".png", ressources().resolve("textures/exemple/glyphes/" + t + ".png"));
            }
            faits.add("glyphes");
            Files.createDirectories(liste.getParent());
            Files.write(liste, faits);
        } catch (IOException e) {
            plugin.getLogger().warning("Impossible de créer les exemples de glyphes : " + e.getMessage());
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
