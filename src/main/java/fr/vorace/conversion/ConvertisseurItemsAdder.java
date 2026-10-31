package fr.vorace.conversion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Convertit le contenu d'ItemsAdder (plugins/ItemsAdder/contents/&lt;espace&gt;/configs/*.yml) : objets, meubles, blocs,
 * images de police, butin des blocs et recettes. D'après la documentation publique d'ItemsAdder (son wiki), sans rien
 * reprendre de son code.
 */
final class ConvertisseurItemsAdder {

    private static final Set<String> CLES_OBJET = Set.of("enabled", "name", "display_name", "lore", "permission", "permission_suffix",
        "resource", "graphics", "material", "enchants", "glint", "attribute_modifiers", "durability", "max_stack_size", "item_flags",
        "behaviours", "specific_properties", "events", "template", "variant_of", "blocked_enchants", "item_model", "drop");

    private final Ecrivain e;
    /** « espace:id » vers la définition (modèles « variant_of » appliqués). */
    private final Map<String, Map<String, Object>> definitions = new LinkedHashMap<>();
    private final Map<String, String> ids = new LinkedHashMap<>();
    private final List<Object[]> butins = new ArrayList<>();    // {espace, carte}
    private final List<Object[]> recettes = new ArrayList<>();  // {espace, genre, id, carte}

    ConvertisseurItemsAdder(Ecrivain e) {
        this.e = e;
    }

    /** Dossiers de textures et de modèles d'ItemsAdder (nouvelle et ancienne organisation). */
    static Ressources ressources(Path racine) {
        Ressources r = new Ressources();
        Path contents = racine.resolve("contents");
        for (Path ns : sousDossiers(contents)) {
            String espace = ns.getFileName().toString().toLowerCase(Locale.ROOT);
            r.ajouterEspace(espace, ns);                                   // contents/<espace>/textures, models
            r.ajouterAssets(ns.resolve("resourcepack"));                   // contents/<espace>/resourcepack/<espace>/textures
            r.ajouterAssets(ns.resolve("resourcepack/assets"));            // contents/<espace>/resourcepack/assets/<espace>/...
            r.ajouterEspace(espace, ns.resolve("resourcepack/" + espace));
        }
        r.chercherAssets(contents, 5);
        r.ajouterAssets(racine.resolve("data/resource_pack/assets"));     // ItemsAdder 2.x
        return r;
    }

    private static List<Path> sousDossiers(Path d) {
        if (!Files.isDirectory(d)) return List.of();
        try (Stream<Path> s = Files.list(d)) {
            return s.filter(Files::isDirectory).sorted().toList();
        } catch (IOException ex) {
            return List.of();
        }
    }

    void convertir(Path racine) {
        List<Path> fichiers = new ArrayList<>();
        Path contents = racine.resolve("contents");
        for (Path ns : sousDossiers(contents)) {
            for (Path f : Yml.fichiers(ns)) {
                String rel = ns.relativize(f).toString().replace('\\', '/');
                if (!rel.startsWith("resourcepack/")) fichiers.add(f);
            }
        }
        fichiers.addAll(Yml.fichiers(racine.resolve("data/items_packs"))); // ItemsAdder 2.x
        if (fichiers.isEmpty()) e.remarque("aucun fichier de configuration trouvé dans " + contents);

        // 1. lecture
        Map<String, Map<String, Object>> brut = new LinkedHashMap<>();
        Map<String, Map<String, Object>> images = new LinkedHashMap<>();
        for (Path f : fichiers) {
            Map<String, Object> y;
            try {
                y = Yml.lire(f);
            } catch (IOException | RuntimeException ex) {
                e.remarque("fichier illisible : " + racine.relativize(f) + " (" + ex.getMessage() + ")");
                continue;
            }
            Map<String, Object> info = Yml.carte(y, "info");
            String espace = info == null ? null : Yml.texte(info, "namespace");
            if (espace == null) espace = espaceDuChemin(contents, f);
            final String ns = espace.toLowerCase(Locale.ROOT);
            Map<String, Object> items = Yml.carte(y, "items");
            if (items != null) items.forEach((id, v) -> { if (v instanceof Map) brut.put(ns + ":" + id, Yml.carte(v)); });
            Map<String, Object> fi = Yml.carte(y, "font_images");
            if (fi != null) fi.forEach((id, v) -> { if (v instanceof Map) images.put(ns + ":" + id, Yml.carte(v)); });
            Map<String, Object> loots = Yml.carte(y, "loots");
            Map<String, Object> blocs = loots == null ? null : Yml.carte(loots, "blocks");
            if (blocs != null) blocs.forEach((id, v) -> { if (v instanceof Map) butins.add(new Object[]{ns, Yml.carte(v)}); });
            Map<String, Object> rec = Yml.carte(y, "recipes");
            if (rec != null) {
                rec.forEach((genre, liste) -> {
                    if (liste instanceof Map<?, ?> m) Yml.carte(m).forEach((id, r) -> {
                        if (r instanceof Map) recettes.add(new Object[]{ns, genre.toLowerCase(Locale.ROOT), id, Yml.carte(r)});
                    });
                });
            }
            for (String autre : y.keySet()) {
                if (!Set.of("info", "items", "font_images", "loots", "recipes").contains(autre.toLowerCase(Locale.ROOT)))
                    e.remarque(racine.relativize(f) + " : section « " + autre + " » non convertie");
            }
        }
        for (Map.Entry<String, Map<String, Object>> x : brut.entrySet()) {
            Map<String, Object> s = x.getValue();
            if (Boolean.TRUE.equals(Yml.bool(s, "template"))) continue;
            if (Boolean.FALSE.equals(Yml.bool(s, "enabled"))) { e.remarque("ItemsAdder « " + x.getKey() + " » désactivé : ignoré"); continue; }
            definitions.put(x.getKey(), avecModele(x.getKey(), s, brut, 0));
        }
        // 2. identifiants
        for (String cle : definitions.keySet()) {
            String espace = cle.substring(0, cle.indexOf(':'));
            String v = e.id(cle.substring(cle.indexOf(':') + 1), espace);
            ids.put(cle, v);
            e.correspondances.put("itemsadder:" + cle, v);
        }
        // 3. conversion
        definitions.forEach(this::objet);
        butins();
        recettes();
        images.forEach(this::glyphe);
    }

    private static String espaceDuChemin(Path contents, Path f) {
        try {
            Path rel = contents.relativize(f);
            if (rel.getNameCount() > 1 && !rel.startsWith("..")) return rel.getName(0).toString();
        } catch (IllegalArgumentException ignore) { }
        return "itemsadder";
    }

    private Map<String, Object> avecModele(String cle, Map<String, Object> s, Map<String, Map<String, Object>> tous, int profondeur) {
        String base = Yml.texte(s, "variant_of");
        if (base == null || profondeur > 5) return s;
        String espace = cle.substring(0, cle.indexOf(':'));
        String cb = base.contains(":") ? base : espace + ":" + base;
        Map<String, Object> t = tous.get(cb);
        if (t == null) { e.remarque("ItemsAdder « " + cle + " » : modèle « " + base + " » introuvable"); return s; }
        Map<String, Object> r = new LinkedHashMap<>(avecModele(cb, t, tous, profondeur + 1));
        r.remove("template");
        fusionner(r, s);
        r.remove("variant_of");
        return r;
    }

    private static void fusionner(Map<String, Object> dans, Map<String, Object> depuis) {
        for (Map.Entry<String, Object> x : depuis.entrySet()) {
            Object avant = dans.get(x.getKey());
            if (avant instanceof Map && x.getValue() instanceof Map) {
                Map<String, Object> copie = new LinkedHashMap<>(Yml.carte(avant));
                fusionner(copie, Yml.carte(x.getValue()));
                dans.put(x.getKey(), copie);
            } else {
                dans.put(x.getKey(), x.getValue());
            }
        }
    }

    /** Référence d'ItemsAdder (« item/rubis.png ») -> référence complète (« espace:item/rubis »). */
    private static String ref(String espace, String r) {
        if (r == null) return null;
        String s = r.trim().replace('\\', '/').replaceAll("\\.(png|json)$", "");
        return s.contains(":") ? s : espace + ":" + s;
    }

    /** Texte d'ItemsAdder qui est une clé de traduction (« display-name-table ») plutôt qu'un vrai texte. */
    private static boolean cleTraduction(String s) {
        return s != null && s.matches("[a-z0-9_.]+(-[a-z0-9_.]+)+");
    }

    // ------------------------------------------------------------------ objets

    private void objet(String cle, Map<String, Object> s) {
        String espace = cle.substring(0, cle.indexOf(':'));
        String id = ids.get(cle);
        String quoi = "ItemsAdder « " + cle + " »";
        Map<String, Object> v = new LinkedHashMap<>();
        Map<String, Object> res = Yml.carte(s, "resource");
        Map<String, Object> gfx = Yml.carte(s, "graphics");
        String materiau = Yml.texte(s, "material");
        if (materiau == null && res != null) materiau = Yml.texte(res, "material");
        v.put("materiau", materiau == null ? "PAPER" : materiau.toUpperCase(Locale.ROOT).replace("MINECRAFT:", ""));

        String nom = Yml.texte(s, "name");
        if (nom == null) nom = Yml.texte(s, "display_name");
        if (nom != null && cleTraduction(nom)) {
            e.remarque(quoi + " : le nom « " + nom + " » est une clé de traduction d'ItemsAdder : mets le vrai nom dans « nom: »");
            nom = null;
        }
        if (nom != null) v.put("nom", Yml.miniMessage(nom));
        List<String> lore = new ArrayList<>();
        for (String l : Yml.liste(s, "lore")) {
            if (cleTraduction(l)) e.remarque(quoi + " : ligne de description « " + l + " » (clé de traduction) ignorée");
            else lore.add(Yml.miniMessage(l));
        }
        if (!lore.isEmpty()) v.put("description", lore);

        Map<String, Object> comp = Yml.carte(s, "behaviours");
        Map<String, Object> anciennes = Yml.carte(s, "specific_properties");
        Map<String, Object> bloc = comp == null ? null : Yml.carte(comp, "block");
        if (bloc == null && anciennes != null) bloc = Yml.carte(anciennes, "block");
        Map<String, Object> meuble = comp == null ? null : Yml.carte(comp, "furniture");
        if (meuble == null && anciennes != null) meuble = Yml.carte(anciennes, "furniture");

        Map<String, Object> faces = apparence(v, id, espace, res, gfx, bloc != null, quoi);

        // enchantements
        Map<String, Object> ench = new LinkedHashMap<>();
        for (String x : Yml.liste(s, "enchants")) {
            int i = x.lastIndexOf(':');
            String n = i < 0 ? x : x.substring(0, i);
            int niv = 1;
            try { if (i >= 0) niv = Integer.parseInt(x.substring(i + 1).trim()); } catch (NumberFormatException ex) { n = x; }
            String nomEnch = Commun.enchantement(n);
            if (nomEnch == null) e.remarque(quoi + " : enchantement d'un autre plugin « " + x + " » ignoré");
            else ench.put(nomEnch, niv);
        }
        if (!ench.isEmpty()) v.put("enchantements", ench);
        if (Boolean.TRUE.equals(Yml.bool(s, "glint"))) v.put("brillance", true);
        Map<String, Object> attrs = Yml.carte(s, "attribute_modifiers");
        if (attrs != null) {
            Map<String, Object> attributs = new LinkedHashMap<>();
            attrs.forEach((slot, liste) -> {
                if (!(liste instanceof Map<?, ?> m)) return;
                Yml.carte(m).forEach((nomA, val) -> {
                    try {
                        double d = Double.parseDouble(String.valueOf(val).trim());
                        Commun.ajouterAttribut(attributs, Commun.attribut(nomA), d, "ajout", Commun.emplacement(slot));
                    } catch (NumberFormatException ex) {
                        e.remarque(quoi + " : attribut « " + nomA + " » illisible");
                    }
                });
            });
            if (!attributs.isEmpty()) v.put("attributs", attributs);
        }
        Map<String, Object> dur = Yml.carte(s, "durability");
        if (dur != null) {
            Double max = Yml.nombre(dur, "max_durability");
            if (max != null) v.put("durabilite", max.intValue());
            if (Boolean.TRUE.equals(Yml.bool(dur, "unbreakable"))) v.put("incassable", true);
            if (Yml.valeur(dur, "usages") != null) e.remarque(quoi + " : nombre d'utilisations (usages) pas encore pris en charge");
        }
        Double pile = Yml.nombre(s, "max_stack_size");
        if (pile != null) v.put("taille-pile", pile.intValue());
        List<String> cacher = Commun.cacher(Yml.liste(s, "item_flags"));
        if (!cacher.isEmpty()) v.put("cacher", cacher);

        evenements(v, Yml.carte(s, "events"), quoi);

        if (meuble != null) {
            v.put("meuble", meuble(meuble, comp, quoi));
            e.meubles++;
        }
        if (bloc != null) {
            Map<String, Object> b = bloc(bloc, quoi);
            if (faces != null) b.putAll(faces);
            v.put("bloc", b);
            e.blocs++;
        }
        if (comp != null) {
            Set<String> autres = new LinkedHashSet<>(comp.keySet());
            autres.removeAll(Set.of("furniture", "furniture_sit", "block", "storage"));
            if (!autres.isEmpty()) e.remarque(quoi + " : comportements non convertis " + autres);
        }
        Set<String> inconnues = new LinkedHashSet<>();
        for (String k : s.keySet()) if (!CLES_OBJET.contains(k.toLowerCase(Locale.ROOT))) inconnues.add(k);
        if (!inconnues.isEmpty()) e.remarque(quoi + " : réglages non convertis " + inconnues);
        e.objets.put(id, v);
    }

    /**
     * Apparence (« graphics: » d'ItemsAdder 4 ou « resource: » des versions d'avant).
     * @return pour un bloc, la partie « textures: » ou « modele: » de la section bloc (sinon null)
     */
    private Map<String, Object> apparence(Map<String, Object> v, String idVorace, String espace, Map<String, Object> res, Map<String, Object> gfx,
                                          boolean estBloc, String quoi) {
        if (res != null) {
            Double cmd = Yml.nombre(res, "model_id");
            if (cmd != null) v.put("custom-model-data", cmd.intValue());
        }
        String modele = null, texture = null, parent = null;
        Map<String, String> textures = new LinkedHashMap<>();
        if (gfx != null) {
            modele = Yml.texte(gfx, "model");
            texture = Yml.texte(gfx, "texture");
            parent = Yml.texte(gfx, "parent");
            Object t = Yml.valeur(gfx, "textures");
            if (t instanceof Map<?, ?> m) m.forEach((k, x) -> textures.put(String.valueOf(k), String.valueOf(x)));
            if (Yml.valeur(gfx, "models") instanceof Map<?, ?> mm) {
                Object normal = mm.get("normal");
                if (normal != null && modele == null) modele = String.valueOf(normal);
                e.remarque(quoi + " : seuls le modèle « normal » est converti (pas les états tir/lancer...)");
            }
        } else if (res != null) {
            boolean genere = Boolean.TRUE.equals(Yml.bool(res, "generate"));
            String mp = Yml.texte(res, "model_path");
            List<String> l = Yml.liste(res, "textures");
            if (l.isEmpty()) l = Yml.liste(res, "texture");
            if (mp != null && !genere) modele = mp;
            else if (l.size() == 1) texture = l.get(0);
            else if (!l.isEmpty()) {
                if (estBloc && l.size() == 6) {
                    // ordre des faces d'un bloc d'ItemsAdder : bas, est, nord, sud, haut, ouest
                    String[] f = {"down", "east", "north", "south", "up", "west"};
                    for (int i = 0; i < 6; i++) textures.put(f[i], l.get(i));
                    e.remarque(quoi + " : bloc à 6 textures, vérifie que chaque face est à sa place");
                } else {
                    for (int i = 0; i < l.size(); i++) textures.put("layer" + i, l.get(i));
                }
            } else if (mp != null) modele = mp;
        }

        if (estBloc) {
            Map<String, Object> b = new LinkedHashMap<>();
            if (modele != null) {
                String m = e.modele(ref(espace, modele), quoi);
                if (m != null) b.put("modele", m);
            } else if (texture != null && (parent == null || parent.replace("minecraft:", "").equals("block/cube_all"))) {
                String t = e.texture(ref(espace, texture), quoi);
                if (t != null) b.put("textures", t);
            } else if (!textures.isEmpty() && parent == null) {
                Map<String, Object> fs = new LinkedHashMap<>();
                textures.forEach((k, x) -> {
                    String face = switch (k.toLowerCase(Locale.ROOT)) {
                        case "up", "top" -> "haut";
                        case "down", "bottom" -> "bas";
                        case "north" -> "nord";
                        case "south" -> "sud";
                        case "east" -> "est";
                        case "west" -> "ouest";
                        case "side" -> "cotes";
                        default -> "tout";
                    };
                    String t = e.texture(ref(espace, x), quoi);
                    if (t != null) fs.put(face, t);
                });
                if (fs.size() == 1 && fs.containsKey("tout")) b.put("textures", fs.get("tout"));
                else if (!fs.isEmpty()) b.put("textures", fs);
            } else if (parent != null) {
                Map<String, String> tm = new LinkedHashMap<>();
                if (texture != null) tm.put(cleUnique(parent), ref(espace, texture));
                textures.forEach((k, x) -> tm.put(k, ref(espace, x)));
                String m = e.modeleGenere(idVorace, parent(espace, parent), tm, quoi);
                if (m != null) b.put("modele", m);
            }
            if (b.isEmpty()) e.remarque(quoi + " : apparence du bloc introuvable");
            return b;
        }

        if (modele != null) {
            String m = e.modele(ref(espace, modele), quoi);
            if (m != null) v.put("modele", m);
            return null;
        }
        boolean plat = parent == null || parent.replace("minecraft:", "").matches("item/(generated|handheld)");
        if (texture != null && plat && textures.isEmpty()) {
            String t = e.texture(ref(espace, texture), quoi);
            if (t != null) v.put("texture", t);
            if (parent != null && parent.endsWith("handheld")) v.put("en-main", true);
            return null;
        }
        if (texture == null && textures.isEmpty()) return null;
        if (textures.containsKey("normal") && plat) {
            String t = e.texture(ref(espace, textures.get("normal")), quoi);
            if (t != null) v.put("texture", t);
            e.remarque(quoi + " : seule la texture « normal » est convertie (pas les états de l'arc, de la canne...)");
            return null;
        }
        Map<String, String> tm = new LinkedHashMap<>();
        if (texture != null) tm.put(plat ? "layer0" : cleUnique(parent), ref(espace, texture));
        textures.forEach((k, x) -> tm.put(k, ref(espace, x)));
        String m = e.modeleGenere(idVorace, parent == null ? "minecraft:item/generated" : parent(espace, parent), tm, quoi);
        if (m != null) v.put("modele", m);
        return null;
    }

    /** Modèle parent : « item/... », « block/... » = jeu de base (ou fichier du pack du même nom), sinon modèle de l'espace. */
    private static String parent(String espace, String p) {
        String s = p.trim().replaceAll("\\.json$", "");
        if (s.contains(":")) return s;
        if (s.startsWith("item/") || s.startsWith("block/") || s.startsWith("builtin/")) return "minecraft:" + s;
        return espace + ":" + s;
    }

    private static String cleUnique(String parent) {
        String p = parent.replace("minecraft:", "");
        if (p.equals("block/cube_all") || p.equals("block/leaves")) return "all";
        if (p.equals("block/cross") || p.equals("block/tinted_cross")) return "cross";
        return "layer0";
    }

    /** events.interact.right.execute_commands -> commandes au clic droit. */
    private void evenements(Map<String, Object> v, Map<String, Object> ev, String quoi) {
        if (ev == null) return;
        Map<String, Object> inter = Yml.carte(ev, "interact");
        List<String> liste = new ArrayList<>();
        boolean consommer = false;
        if (inter != null) {
            for (String cote : List.of("right", "right_shift")) {
                Map<String, Object> r = Yml.carte(inter, cote);
                if (r == null) continue;
                Object ec = Yml.valeur(r, "execute_commands");
                List<Map<String, Object>> cmds = new ArrayList<>();
                if (ec instanceof List<?> l) for (Object x : l) { if (x instanceof Map) cmds.add(Yml.carte(x)); }
                else if (ec instanceof Map<?, ?> m) Yml.carte(m).values().forEach(x -> { if (x instanceof Map) cmds.add(Yml.carte(x)); });
                for (Map<String, Object> c : cmds) {
                    String cmd = Yml.texte(c, "command");
                    if (cmd == null) continue;
                    String l = Commun.commande(Boolean.TRUE.equals(Yml.bool(c, "as_console")) ? "console" : "joueur", cmd);
                    if (!liste.contains(l)) liste.add(l);
                }
                if (Boolean.TRUE.equals(Yml.bool(r, "decrement_amount"))) consommer = true;
                if (Yml.valeur(r, "place_furniture") != null) e.remarque(quoi + " : objet qui pose un autre meuble (icône 2D) : donne directement le meuble « " + Yml.texte(Yml.carte(r, "place_furniture"), "furniture") + " »");
            }
        }
        if (!liste.isEmpty()) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("clic-droit", liste);
            if (consommer) c.put("consommer", true);
            v.put("commandes", c);
        }
        Set<String> autres = new LinkedHashSet<>(ev.keySet());
        autres.remove("interact");
        if (!autres.isEmpty()) e.remarque(quoi + " : événements non convertis " + autres);
    }

    // ------------------------------------------------------------------ meubles

    private Map<String, Object> meuble(Map<String, Object> f, Map<String, Object> comportements, String quoi) {
        Map<String, Object> m = new LinkedHashMap<>();
        boolean solide = !Boolean.FALSE.equals(Yml.bool(f, "solid"));
        Map<String, Object> hb = Yml.carte(f, "hitbox");
        double l = 1, w = 1, h = 1, ol = 0, ow = 0, oh = 0;
        if (hb != null) {
            l = val(hb, "length", 1); w = val(hb, "width", 1); h = val(hb, "height", 1);
            ol = val(hb, "length_offset", 0); ow = val(hb, "width_offset", 0); oh = val(hb, "height_offset", 0);
        }
        if (solide) {
            List<String> blocs = new ArrayList<>();
            int nw = (int) Math.max(1, Math.ceil(w)), nl = (int) Math.max(1, Math.ceil(l)), nh = (int) Math.max(1, Math.ceil(h));
            for (int y = 0; y < nh; y++)
                for (int x = 0; x < nw; x++)
                    for (int z = 0; z < nl; z++)
                        blocs.add((x + (int) Math.round(ow)) + "," + (y + (int) Math.round(oh)) + "," + (z + (int) Math.round(ol)));
            m.put("hitbox", blocs);
            if (w != Math.rint(w) || l != Math.rint(l) || h != Math.rint(h)) e.remarque(quoi + " : hitbox " + w + "x" + h + "x" + l + " arrondie au bloc près");
        } else {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("largeur", Math.max(w, l));
            x.put("hauteur", h);
            m.put("hitbox", x);
        }
        Double lum = Yml.nombre(f, "light_level");
        if (lum != null && lum > 0) m.put("lumiere", (int) Math.min(15, lum));
        Map<String, Object> po = Yml.carte(f, "placeable_on");
        if (po != null) {
            List<String> pose = new ArrayList<>();
            if (!Boolean.FALSE.equals(Yml.bool(po, "floor"))) pose.add("sol");
            if (Boolean.TRUE.equals(Yml.bool(po, "walls"))) pose.add("mur");
            if (Boolean.TRUE.equals(Yml.bool(po, "ceiling"))) pose.add("plafond");
            if (!pose.isEmpty()) m.put("pose", pose);
        }
        Map<String, Object> dt = Yml.carte(f, "display_transformation");
        if (dt != null) {
            Map<String, Object> sc = Yml.carte(dt, "scale");
            Double sx = sc == null ? null : Yml.nombre(sc, "x");
            if (sx != null && sx != 1) m.put("taille", sx);
            e.remarque(quoi + " : position/rotation d'affichage d'ItemsAdder non reprises, vérifie le meuble en jeu (rotation-modele, decalage)");
        }
        String entite = Yml.texte(f, "entity");
        if (entite == null || entite.equalsIgnoreCase("armor_stand"))
            e.remarque(quoi + " : meuble prévu pour un porte-armure, vérifie sa hauteur en jeu (« decalage » si besoin)");
        if (comportements != null) {
            Map<String, Object> sit = Yml.carte(comportements, "furniture_sit");
            if (sit != null) {
                Double sh = Yml.nombre(sit, "sit_height");
                m.put("sieges", List.of("0, " + Yml.nombreCourt(sh == null ? 0.5 : sh) + ", 0"));
            }
            Map<String, Object> st = Yml.carte(comportements, "storage");
            if (st != null) {
                Double rows = Yml.nombre(st, "rows");
                m.put("stockage", rows == null ? 3 : (int) Math.max(1, Math.min(6, rows)));
                String t = Yml.texte(st, "title");
                if (t != null && !cleTraduction(t)) m.put("titre-stockage", Yml.miniMessage(t));
            }
        }
        Map<String, Object> son = Yml.carte(f, "sound");
        sons(m, son);
        return m;
    }

    private static double val(Map<String, Object> m, String cle, double defaut) {
        Double d = Yml.nombre(m, cle);
        return d == null ? defaut : d;
    }

    private static void sons(Map<String, Object> m, Map<String, Object> son) {
        if (son == null) return;
        Map<String, Object> p = Yml.carte(son, "place");
        Map<String, Object> c = Yml.carte(son, "break");
        String sp = p == null ? Yml.texte(son, "place") : Yml.texte(p, "name");
        String sc = c == null ? Yml.texte(son, "break") : Yml.texte(c, "name");
        if (sp != null) m.put("son-pose", sp.replace("minecraft:", ""));
        if (sc != null) m.put("son-casse", sc.replace("minecraft:", ""));
    }

    // ------------------------------------------------------------------ blocs

    private Map<String, Object> bloc(Map<String, Object> b, String quoi) {
        Map<String, Object> r = new LinkedHashMap<>();
        Map<String, Object> pm = Yml.carte(b, "placed_model");
        String type = pm == null ? null : Yml.texte(pm, "type");
        if (type != null && !type.equalsIgnoreCase("REAL_NOTE")) e.remarque(quoi + " : bloc de type " + type + " converti en bloc plein (bloc à note)");
        Double h = Yml.nombre(b, "hardness");
        if (h != null) r.put("durete", h < 0 ? 0 : Math.min(50, h));
        List<String> outils = Yml.liste(b, "break_tools_whitelist");
        if (!outils.isEmpty()) {
            String o = Commun.outil(outils.get(0));
            if (o != null) r.put("outil", o);
            String n = Commun.niveau(outils.get(0));
            if (n != null) r.put("niveau", n);
        }
        if (Boolean.FALSE.equals(Yml.bool(b, "drop_when_mined")) || Boolean.TRUE.equals(Yml.bool(b, "cancel_drop")))
            e.remarque(quoi + " : bloc qui ne lâche rien : pas encore possible dans Vorace (il lâche le bloc)");
        sons(r, Yml.carte(b, "sound"));
        if (Yml.valeur(b, "light_level") != null) e.remarque(quoi + " : lumière des blocs pas encore prise en charge");
        return r;
    }

    /** loots.blocks : butin des blocs personnalisés. */
    @SuppressWarnings("unchecked")
    private void butins() {
        for (Object[] x : butins) {
            String espace = (String) x[0];
            Map<String, Object> l = (Map<String, Object>) x[1];
            String type = Yml.texte(l, "type");
            if (type == null) continue;
            String cle = type.contains(":") ? type : espace + ":" + type;
            String id = ids.get(cle);
            if (id == null || !(e.objets.get(id).get("bloc") instanceof Map)) {
                e.remarque("butin d'ItemsAdder pour « " + type + " » (pas un bloc converti) ignoré");
                continue;
            }
            Map<String, Object> bloc = (Map<String, Object>) e.objets.get(id).get("bloc");
            List<String> liste = new ArrayList<>();
            Object items = Yml.valeur(l, "items");
            List<Map<String, Object>> entrees = new ArrayList<>();
            if (items instanceof Map<?, ?> m) {
                Map<String, Object> mm = Yml.carte(m);
                if (mm.containsKey("item")) entrees.add(mm);
                else mm.values().forEach(o -> { if (o instanceof Map) entrees.add(Yml.carte(o)); });
            } else if (items instanceof List<?> li) {
                for (Object o : li) if (o instanceof Map) entrees.add(Yml.carte(o));
            }
            for (Map<String, Object> it : entrees) {
                String obj = objet(espace, Yml.texte(it, "item"));
                if (obj == null) continue;
                double min = val(it, "min_amount", 1), max = val(it, "max_amount", min), chance = val(it, "chance", 100);
                StringBuilder sb = new StringBuilder(obj).append(' ').append((int) min);
                if (max > min) sb.append('-').append((int) max);
                if (chance < 100) sb.append(' ').append(Yml.nombreCourt(chance)).append('%');
                liste.add(sb.toString());
            }
            if (!liste.isEmpty()) bloc.put("butin", liste);
            if (Yml.valeur(l, "exp") != null) e.remarque("butin d'ItemsAdder pour « " + type + " » : expérience pas encore prise en charge");
        }
    }

    /** Objet cité par ItemsAdder (« espace:id » d'ItemsAdder ou objet du jeu) -> référence Vorace, ou null. */
    private String objet(String espace, String ref) {
        if (ref == null) return null;
        String s = ref.trim();
        String cle = s.contains(":") ? s : espace + ":" + s;
        if (ids.containsKey(cle)) return "vorace:" + ids.get(cle);
        String mc = s.toLowerCase(Locale.ROOT).replace("minecraft:", "");
        if (mc.matches("[a-z0-9_]+")) return mc.toUpperCase(Locale.ROOT);
        e.remarque("objet « " + ref + " » d'un autre plugin ignoré");
        return null;
    }

    // ------------------------------------------------------------------ recettes

    @SuppressWarnings("unchecked")
    private void recettes() {
        for (Object[] x : recettes) {
            String espace = (String) x[0], genre = (String) x[1], idr = (String) x[2];
            Map<String, Object> r = (Map<String, Object>) x[3];
            String quoi = "recette ItemsAdder « " + idr + " »";
            if (Boolean.FALSE.equals(Yml.bool(r, "enabled"))) continue;
            Map<String, Object> res = Yml.carte(r, "result");
            String resultat = res == null ? null : objet(espace, Yml.texte(res, "item"));
            if (resultat == null || !resultat.startsWith("vorace:")) { e.remarque(quoi + " : résultat qui n'est pas un objet converti, ignorée"); continue; }
            Map<String, Object> v = new LinkedHashMap<>();
            Double q = res == null ? null : Yml.nombre(res, "amount");
            if (genre.contains("cooking") || genre.contains("furnace")) {
                Map<String, Object> ing = Yml.carte(r, "ingredient");
                String entree = ing == null ? null : objet(espace, Yml.texte(ing, "item"));
                if (entree == null) { e.remarque(quoi + " : ingrédient illisible"); continue; }
                v.put("type", "four");
                v.put("entree", entree);
                Double xp = Yml.nombre(r, "exp");
                if (xp != null) v.put("experience", xp);
                Double t = Yml.nombre(r, "cook_time");
                if (t != null) v.put("temps", t.intValue());
            } else if (genre.contains("crafting")) {
                Map<String, Object> ing = Yml.carte(r, "ingredients");
                List<String> forme = Yml.liste(r, "pattern");
                if (ing == null) { e.remarque(quoi + " : pas d'ingrédients"); continue; }
                Map<String, Object> ingredients = new LinkedHashMap<>();
                boolean ok = true;
                for (Map.Entry<String, Object> i : ing.entrySet()) {
                    Object val = i.getValue();
                    String o = objet(espace, val instanceof Map<?, ?> m ? Yml.texte(Yml.carte(m), "item") : String.valueOf(val));
                    if (o == null) { ok = false; break; }
                    ingredients.put(i.getKey(), o);
                }
                if (!ok) { e.remarque(quoi + " : ingrédient d'un autre plugin, ignorée"); continue; }
                if (!forme.isEmpty() && !Boolean.TRUE.equals(Yml.bool(r, "shapeless"))) {
                    v.put("type", "forme");
                    v.put("forme", forme);
                    v.put("ingredients", ingredients);
                } else {
                    v.put("type", "sans-forme");
                    v.put("liste", new ArrayList<>(ingredients.values()));
                }
            } else {
                e.remarque(quoi + " : recette de type « " + genre + " » pas encore prise en charge");
                continue;
            }
            if (q != null && q > 1) v.put("quantite", q.intValue());
            e.recette(resultat.substring("vorace:".length()), v, quoi);
        }
    }

    // ------------------------------------------------------------------ images de police

    private void glyphe(String cle, Map<String, Object> g) {
        String espace = cle.substring(0, cle.indexOf(':'));
        String id = cle.substring(cle.indexOf(':') + 1);
        String quoi = "ItemsAdder image « " + cle + " »";
        String chemin = Yml.texte(g, "path");
        if (chemin == null) { e.remarque(quoi + " : pas d'image"); return; }
        String t = e.texture(ref(espace, chemin), quoi);
        if (t == null) return;
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("texture", t);
        Double h = Yml.nombre(g, "scale_ratio");
        if (h != null) v.put("hauteur", h.intValue());
        Double y = Yml.nombre(g, "y_position");
        if (y != null) v.put("montee", y.intValue());
        String gid = e.idGlyphe(id, espace);
        List<String> r = e.raccourcis(gid, List.of(":" + id + ":"), quoi);
        if (!r.isEmpty()) v.put("raccourcis", r);
        String perm = Yml.texte(g, "permission");
        if (perm != null && !perm.isBlank()) v.put("permission", perm);
        if (Boolean.TRUE.equals(Yml.bool(g, "show_in_gui"))) v.put("liste", true);
        e.glyphes.put(gid, v);
    }
}
