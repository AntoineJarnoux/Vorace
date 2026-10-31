package fr.vorace.conversion;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Convertit la configuration de Nexo (et d'Oraxen, même format) : objets, meubles, blocs, glyphes.
 * D'après la documentation publique de Nexo (docs.nexomc.com), sans rien reprendre de son code.
 */
final class ConvertisseurNexo {

    private static final Set<String> CLES_OBJET = Set.of("itemname", "displayname", "item_name", "customname", "material", "lore", "pack",
        "mechanics", "enchantments", "unbreakable", "color", "itemflags", "attributemodifiers", "components", "template", "templates",
        "excludefrominventory", "injectid", "persistentdata", "custom_model_data");
    private static final Set<String> CLES_MEUBLE = Set.of("hitbox", "barrier", "barriers", "seat", "seats", "lights", "light", "limited_placing",
        "rotatable", "restricted_rotation", "storage", "properties", "block_sounds", "drop", "type", "modelengine_id", "waterloggable",
        "preview_placement", "farmland_required", "farmblock_required");

    private final Ecrivain e;
    private final String nomPlugin;
    private final Map<String, Map<String, Object>> definitions = new LinkedHashMap<>();
    private final Map<String, String> ids = new LinkedHashMap<>();

    ConvertisseurNexo(Ecrivain e, String nomPlugin) {
        this.e = e;
        this.nomPlugin = nomPlugin;
    }

    /** @param racine plugins/Nexo (ou plugins/Oraxen) */
    void convertir(Path racine) {
        // 1. lecture de tous les objets (avec leurs modèles « template »)
        Map<String, Map<String, Object>> brut = new LinkedHashMap<>();
        for (Path f : Yml.fichiers(racine.resolve("items"))) {
            try {
                Yml.lire(f).forEach((id, v) -> {
                    if (v instanceof Map) brut.put(id, Yml.carte(v));
                });
            } catch (IOException | RuntimeException ex) {
                e.remarque("fichier illisible : " + racine.relativize(f) + " (" + ex.getMessage() + ")");
            }
        }
        for (Map.Entry<String, Map<String, Object>> x : brut.entrySet()) {
            if (Boolean.TRUE.equals(Yml.bool(x.getValue(), "template"))) continue; // modèle d'objet, pas un objet
            definitions.put(x.getKey(), avecModeles(x.getValue(), brut, 0));
        }
        // 2. identifiants Vorace (connus avant la conversion : le butin d'un bloc peut citer un autre objet)
        for (String id : definitions.keySet()) {
            String v = e.id(id, nomPlugin);
            ids.put(id, v);
            e.correspondances.put(nomPlugin + ":" + id, v);
        }
        // 3. conversion
        definitions.forEach(this::objet);
        // 4. glyphes
        for (Path f : Yml.fichiers(racine.resolve("glyphs"))) {
            try {
                Yml.lire(f).forEach((id, v) -> {
                    if (v instanceof Map) glyphe(id, Yml.carte(v));
                });
            } catch (IOException | RuntimeException ex) {
                e.remarque("fichier de glyphes illisible : " + racine.relativize(f) + " (" + ex.getMessage() + ")");
            }
        }
        // 5. recettes (recipes/shaped.yml, shapeless.yml, furnace.yml...)
        for (Path f : Yml.fichiers(racine.resolve("recipes"))) {
            String genre = f.getFileName().toString().toLowerCase(Locale.ROOT).replaceAll("\\.ya?ml$", "");
            try {
                Yml.lire(f).forEach((id, r) -> {
                    if (r instanceof Map) recette(genre, id, Yml.carte(r));
                });
            } catch (IOException | RuntimeException ex) {
                e.remarque("fichier de recettes illisible : " + racine.relativize(f) + " (" + ex.getMessage() + ")");
            }
        }
    }

    /** Applique « template: x » (les valeurs de l'objet gagnent). */
    private Map<String, Object> avecModeles(Map<String, Object> s, Map<String, Map<String, Object>> tous, int profondeur) {
        List<String> modeles = new ArrayList<>(Yml.liste(s, "template"));
        modeles.addAll(Yml.liste(s, "templates"));
        modeles.removeIf(m -> m.equalsIgnoreCase("true") || m.equalsIgnoreCase("false"));
        if (modeles.isEmpty() || profondeur > 5) return s;
        Map<String, Object> r = new LinkedHashMap<>();
        for (String m : modeles) {
            Map<String, Object> t = tous.get(m);
            if (t == null) { e.remarque("modèle d'objet « " + m + " » introuvable"); continue; }
            fusionner(r, avecModeles(t, tous, profondeur + 1));
        }
        fusionner(r, s);
        r.remove("template");
        r.remove("templates");
        return r;
    }

    @SuppressWarnings("unchecked")
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

    // ------------------------------------------------------------------ objets

    private void objet(String idNexo, Map<String, Object> s) {
        String id = ids.get(idNexo);
        String quoi = nomPlugin + " « " + idNexo + " »";
        Map<String, Object> v = new LinkedHashMap<>();
        String materiau = Yml.texte(s, "material");
        v.put("materiau", materiau == null ? "PAPER" : materiau.toUpperCase(Locale.ROOT));
        String nom = Yml.texte(s, "itemname");
        if (nom == null) nom = Yml.texte(s, "displayname");
        if (nom == null) nom = Yml.texte(s, "customname");
        if (nom != null) v.put("nom", Yml.miniMessage(nom));
        List<String> lore = Yml.liste(s, "lore");
        if (!lore.isEmpty()) v.put("description", lore.stream().map(Yml::miniMessage).toList());

        Map<String, Object> pack = Yml.carte(s, "Pack");
        Map<String, Object> meca = Yml.carte(s, "Mechanics");
        Map<String, Object> bloc = meca == null ? null : Yml.carte(meca, "custom_block");
        if (bloc == null && meca != null) bloc = Yml.carte(meca, "noteblock"); // ancien format d'Oraxen
        apparence(v, pack, quoi, bloc != null);

        Map<String, Object> ench = Yml.carte(s, "Enchantments");
        if (ench != null && !ench.isEmpty()) {
            Map<String, Object> m = new LinkedHashMap<>();
            ench.forEach((k, n) -> {
                String nomEnch = Commun.enchantement(k);
                if (nomEnch == null) e.remarque(quoi + " : enchantement d'un autre plugin « " + k + " » ignoré");
                else m.put(nomEnch, n instanceof Number x ? x.intValue() : 1);
            });
            v.put("enchantements", m);
        }
        if (Boolean.TRUE.equals(Yml.bool(s, "unbreakable"))) v.put("incassable", true);
        String couleur = Commun.couleur(Yml.valeur(s, "color"));
        if (couleur != null) v.put("couleur", couleur);
        List<String> cacher = Commun.cacher(Yml.liste(s, "ItemFlags"));
        if (!cacher.isEmpty()) v.put("cacher", cacher);
        Map<String, Object> attributs = new LinkedHashMap<>();
        for (Map<String, Object> a : Yml.listeCartes(s, "AttributeModifiers")) {
            String at = Yml.texte(a, "attribute");
            Double n = Yml.nombre(a, "amount");
            if (at == null || n == null) continue;
            Commun.ajouterAttribut(attributs, Commun.attribut(at), n, Commun.operation(Yml.texte(a, "operation")), Commun.emplacement(Yml.texte(a, "slot")));
        }
        if (!attributs.isEmpty()) v.put("attributs", attributs);
        Map<String, Object> comp = Yml.carte(s, "Components");
        if (comp != null) {
            Double dur = Yml.nombre(comp, "max_damage");
            if (dur == null) dur = Yml.nombre(comp, "durability");
            if (dur != null) v.put("durabilite", dur.intValue());
            Double pile = Yml.nombre(comp, "max_stack_size");
            if (pile != null) v.put("taille-pile", pile.intValue());
            if (Boolean.TRUE.equals(Yml.bool(comp, "enchantment_glint_override"))) v.put("brillance", true);
            Set<String> autres = new LinkedHashSet<>(comp.keySet());
            autres.removeAll(Set.of("max_damage", "durability", "max_stack_size", "enchantment_glint_override", "item_model"));
            if (!autres.isEmpty()) e.remarque(quoi + " : composants non convertis " + autres);
        }

        if (meca != null) {
            Map<String, Object> meuble = Yml.carte(meca, "furniture");
            if (meuble != null) { v.put("meuble", meuble(meuble, quoi)); e.meubles++; }
            if (bloc != null) { v.put("bloc", bloc(bloc, pack, quoi)); e.blocs++; }
            Map<String, Object> cmd = Yml.carte(meca, "commands");
            if (cmd != null) commandes(v, cmd);
            Set<String> autres = new LinkedHashSet<>(meca.keySet());
            autres.removeAll(Set.of("furniture", "custom_block", "noteblock", "commands"));
            if (!autres.isEmpty()) e.remarque(quoi + " : mécaniques non converties " + autres);
        }
        Set<String> inconnues = new LinkedHashSet<>();
        for (String k : s.keySet()) if (!CLES_OBJET.contains(k.toLowerCase(Locale.ROOT))) inconnues.add(k);
        if (!inconnues.isEmpty()) e.remarque(quoi + " : réglages non convertis " + inconnues);
        e.objets.put(id, v);
    }

    /** Pack: model / texture(s) / parent_model / custom_model_data. */
    private void apparence(Map<String, Object> v, Map<String, Object> pack, String quoi, boolean estBloc) {
        if (pack == null) return;
        Double cmd = Yml.nombre(pack, "custom_model_data");
        if (cmd != null) v.put("custom-model-data", cmd.intValue());
        if (estBloc) return; // l'apparence d'un bloc est dans « bloc: »
        String modele = Yml.texte(pack, "model");
        Object textures = Yml.valeur(pack, "textures");
        String texture = Yml.texte(pack, "texture");
        String parent = Yml.texte(pack, "parent_model");
        boolean genere = texture != null || textures != null;
        if (modele != null && !genere) {
            String m = e.modele(modele, quoi);
            if (m != null) v.put("modele", m);
            return;
        }
        if (!genere) return;
        String p = parent == null ? "item/generated" : parent;
        boolean plat = p.replace("minecraft:", "").equals("item/generated") || p.replace("minecraft:", "").equals("item/handheld");
        if (plat && texture != null) {
            String t = e.texture(texture, quoi);
            if (t != null) v.put("texture", t);
            if (p.endsWith("handheld")) v.put("en-main", true);
            return;
        }
        Map<String, String> tm = new LinkedHashMap<>();
        if (texture != null) tm.put(plat ? "layer0" : cleUnique(p), texture);
        if (textures instanceof List<?> l) {
            for (int i = 0; i < l.size(); i++) tm.put(plat ? "layer" + i : (i == 0 ? cleUnique(p) : "layer" + i), String.valueOf(l.get(i)));
        } else if (textures instanceof Map<?, ?> m) {
            m.forEach((k, x) -> tm.put(String.valueOf(k), String.valueOf(x)));
        }
        String m = e.modeleGenere(nomFichier(v, quoi), p, tm, quoi);
        if (m != null) v.put("modele", m);
    }

    private static String cleUnique(String parent) {
        String p = parent.replace("minecraft:", "");
        if (p.equals("block/cube_all") || p.equals("block/leaves")) return "all";
        if (p.equals("block/cross") || p.equals("block/tinted_cross")) return "cross";
        return "layer0";
    }

    private String nomFichier(Map<String, Object> v, String quoi) {
        return quoi.replaceAll(".*« (.*) »", "$1").replaceAll("[^a-z0-9_./-]", "_");
    }

    private void commandes(Map<String, Object> v, Map<String, Object> cmd) {
        List<String> liste = new ArrayList<>();
        for (String c : Yml.liste(cmd, "console")) liste.add(Commun.commande("console", c));
        for (String c : Yml.liste(cmd, "player")) liste.add(Commun.commande("joueur", c));
        for (String c : Yml.liste(cmd, "opped_player")) liste.add(Commun.commande("console", c));
        if (liste.isEmpty()) return;
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("clic-droit", liste);
        Double delai = Yml.nombre(cmd, "cooldown");
        if (delai != null && delai > 0) c.put("delai-ticks", (int) Math.round(delai / 50.0));
        if (Boolean.TRUE.equals(Yml.bool(cmd, "one_usage"))) c.put("consommer", true);
        v.put("commandes", c);
    }

    // ------------------------------------------------------------------ meubles

    private Map<String, Object> meuble(Map<String, Object> f, String quoi) {
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> hb = Yml.carte(f, "hitbox");
        List<String> barrieres = new ArrayList<>();
        if (hb != null) barrieres.addAll(Yml.liste(hb, "barriers"));
        barrieres.addAll(Yml.liste(f, "barriers"));
        Object ancienne = Yml.valeur(f, "barrier");
        if (barrieres.isEmpty() && Boolean.TRUE.equals(ancienne instanceof Boolean b ? b : null)) barrieres.add("0,0,0");
        if (!barrieres.isEmpty()) {
            m.put("hitbox", barrieres.stream().map(b -> b.replace(" ", "")).toList());
        } else if (hb != null) {
            List<String> inter = new ArrayList<>(Yml.liste(hb, "interactions"));
            String une = Yml.texte(hb, "interaction");
            if (une != null) inter.add(0, une);
            if (!inter.isEmpty()) {
                String[] p = inter.get(0).trim().split("\\s+");
                double[] taille = p.length > 1 ? Yml.triplet(p[1] + ",0") : null;
                Map<String, Object> h = new LinkedHashMap<>();
                h.put("largeur", taille == null ? 1 : taille[0]);
                h.put("hauteur", taille == null ? 1 : taille[1]);
                m.put("hitbox", h);
                if (inter.size() > 1) e.remarque(quoi + " : " + inter.size() + " hitbox d'interaction, seule la première est gardée");
            }
        }
        List<String> sieges = new ArrayList<>(Yml.liste(f, "seats"));
        Object seat = Yml.valeur(f, "seat");
        if (seat instanceof Map<?, ?> sm) {
            Double h = Yml.nombre(Yml.carte(sm), "height");
            if (h != null) sieges.add("0, " + Yml.nombreCourt(h) + ", 0");
        } else if (seat != null) {
            sieges.add(String.valueOf(seat));
        }
        if (!sieges.isEmpty()) m.put("sieges", sieges.stream().map(x -> x.replace(",", ", ").replaceAll("\\s+", " ")).toList());

        int lumiere = 0;
        Object lights = Yml.valeur(f, "lights");
        List<String> lum = new ArrayList<>();
        if (lights instanceof Map<?, ?> lm) {
            Map<String, Object> l = Yml.carte(lm);
            lum.addAll(Yml.liste(l, "lights"));
            String un = Yml.texte(l, "light");
            if (un != null) lum.add(un);
            if (Boolean.TRUE.equals(Yml.bool(l, "toggleable"))) e.remarque(quoi + " : lumière à allumer/éteindre pas encore prise en charge (toujours allumée)");
        } else {
            lum.addAll(Yml.liste(f, "lights"));
        }
        Object light = Yml.valeur(f, "light");
        if (light != null && !(light instanceof Map)) lum.add(String.valueOf(light));
        for (String l : lum) {
            String[] p = l.trim().split("\\s+");
            try { lumiere = Math.max(lumiere, Integer.parseInt(p[p.length - 1])); } catch (NumberFormatException ignore) { }
        }
        if (lumiere > 0) m.put("lumiere", Math.min(15, lumiere));

        Map<String, Object> lp = Yml.carte(f, "limited_placing");
        if (lp != null) {
            List<String> pose = new ArrayList<>();
            if (!Boolean.FALSE.equals(Yml.bool(lp, "floor"))) pose.add("sol");
            if (!Boolean.FALSE.equals(Yml.bool(lp, "wall"))) pose.add("mur");
            if (!Boolean.FALSE.equals(Yml.bool(lp, "roof"))) pose.add("plafond");
            if (!pose.isEmpty()) m.put("pose", pose);
        }
        String rr = Yml.texte(f, "restricted_rotation");
        if (rr != null) m.put("rotation", rr.equalsIgnoreCase("VERY_STRICT") ? 4 : 8);

        Map<String, Object> st = Yml.carte(f, "storage");
        if (st != null) {
            Double rows = Yml.nombre(st, "rows");
            m.put("stockage", rows == null ? 6 : (int) Math.max(1, Math.min(6, rows)));
            String t = Yml.texte(st, "title");
            if (t != null) m.put("titre-stockage", Yml.miniMessage(t));
        }
        Map<String, Object> pr = Yml.carte(f, "properties");
        if (pr != null) {
            double[] sc = Yml.triplet(Yml.valeur(pr, "scale"));
            if (sc != null && sc[0] != 1) m.put("taille", sc[0]);
            double[] tr = Yml.triplet(Yml.valeur(pr, "translation"));
            if (tr != null && (tr[0] != 0 || tr[1] != 0 || tr[2] != 0)) m.put("decalage", Yml.nombreCourt(tr[0]) + ", " + Yml.nombreCourt(tr[1]) + ", " + Yml.nombreCourt(tr[2]));
            Map<String, Object> br = Yml.carte(pr, "brightness");
            Double bl = br == null ? null : Yml.nombre(br, "block_light");
            if (bl != null) m.put("luminosite", (int) Math.max(0, Math.min(15, bl)));
        }
        if (!m.containsKey("hitbox")) {
            // sans hitbox, Nexo met une hitbox d'interaction d'un bloc (on ne bute pas dedans)
            Map<String, Object> h = new LinkedHashMap<>();
            h.put("largeur", 1);
            h.put("hauteur", 1);
            m.put("hitbox", h);
        }
        sons(m, Yml.carte(f, "block_sounds"));
        String me = Yml.texte(f, "modelengine_id");
        if (me != null) e.remarque(quoi + " : meuble animé ModelEngine « " + me + " » : converti en meuble fixe (son modèle animé, après /vorace convertir modelengine : /vorace modele poser modelengine/" + me.toLowerCase(Locale.ROOT) + ")");
        Set<String> autres = new LinkedHashSet<>();
        for (String k : f.keySet()) if (!CLES_MEUBLE.contains(k.toLowerCase(Locale.ROOT))) autres.add(k);
        if (!autres.isEmpty()) e.remarque(quoi + " : réglages de meuble non convertis " + autres);
        return m;
    }

    private static void sons(Map<String, Object> m, Map<String, Object> sons) {
        if (sons == null) return;
        String pose = son(sons, "place_sound", "place");
        String casse = son(sons, "break_sound", "break");
        if (pose != null) m.put("son-pose", pose);
        if (casse != null) m.put("son-casse", casse);
    }

    private static String son(Map<String, Object> sons, String simple, String section) {
        String s = Yml.texte(sons, simple);
        if (s == null) {
            Map<String, Object> x = Yml.carte(sons, section);
            if (x != null) s = Yml.texte(x, "sound");
        }
        return s == null ? null : s.replace("minecraft:", "");
    }

    // ------------------------------------------------------------------ blocs

    private Map<String, Object> bloc(Map<String, Object> b, Map<String, Object> pack, String quoi) {
        Map<String, Object> r = new LinkedHashMap<>();
        String type = Yml.texte(b, "type");
        if (type != null && !type.equalsIgnoreCase("NOTEBLOCK")) e.remarque(quoi + " : bloc de type " + type + " converti en bloc plein");
        // apparence : textures du Pack (cube) ou modèle
        if (pack != null) {
            String modele = Yml.texte(b, "model");
            Object textures = Yml.valeur(pack, "textures");
            String texture = Yml.texte(pack, "texture");
            if (texture != null) {
                String t = e.texture(texture, quoi);
                if (t != null) r.put("textures", t);
            } else if (textures instanceof Map<?, ?> tm) {
                Map<String, Object> faces = new LinkedHashMap<>();
                tm.forEach((k, x) -> {
                    String face = switch (String.valueOf(k).toLowerCase(Locale.ROOT)) {
                        case "top", "up", "end" -> "haut";
                        case "bottom", "down" -> "bas";
                        case "side" -> "cotes";
                        case "north" -> "nord";
                        case "south" -> "sud";
                        case "east" -> "est";
                        case "west" -> "ouest";
                        case "all", "texture", "layer0" -> "tout";
                        default -> null;
                    };
                    String t = face == null ? null : e.texture(String.valueOf(x), quoi);
                    if (t != null) faces.put(face, t);
                });
                if (faces.size() == 1 && faces.containsKey("tout")) r.put("textures", faces.get("tout"));
                else if (!faces.isEmpty()) r.put("textures", faces);
            } else if (textures instanceof List<?> l && !l.isEmpty()) {
                String t = e.texture(String.valueOf(l.get(0)), quoi);
                if (t != null) r.put("textures", t);
            } else {
                String m = Yml.texte(pack, "model");
                if (m == null) m = modele;
                if (m != null) {
                    String copie = e.modele(m, quoi);
                    if (copie != null) r.put("modele", copie);
                }
            }
        }
        Double h = Yml.nombre(b, "hardness");
        if (h != null) {
            r.put("durete", Math.max(0, Math.min(50, h)));
            if (h > 50) e.remarque(quoi + " : dureté " + h + " ramenée à 50, vérifie la vitesse de minage");
        }
        Map<String, Object> drop = Yml.carte(b, "drop");
        if (drop != null) {
            String outil = Commun.outil(Yml.texte(drop, "best_tool"));
            if (outil != null) r.put("outil", outil);
            String niveau = Commun.niveau(Yml.texte(drop, "minimal_type"));
            if (niveau != null) r.put("niveau", niveau);
            List<String> butin = new ArrayList<>();
            for (Map<String, Object> l : Yml.listeCartes(drop, "loots")) {
                String objet = null;
                String nx = Yml.texte(l, "nexo_item");
                if (nx == null) nx = Yml.texte(l, "oraxen_item");
                if (nx != null) objet = "vorace:" + ids.getOrDefault(nx, nx);
                String mc = Yml.texte(l, "minecraft_type");
                if (objet == null && mc != null) objet = mc.toUpperCase(Locale.ROOT);
                if (objet == null) { e.remarque(quoi + " : butin d'un autre plugin ignoré " + l); continue; }
                String q = Yml.texte(l, "amount");
                StringBuilder sb = new StringBuilder(objet);
                if (q != null) sb.append(' ').append(q.replace("..", "-").replace(" ", ""));
                Double pr = Yml.nombre(l, "probability");
                if (pr != null && pr < 1) sb.append(' ').append(Yml.nombreCourt(pr * 100)).append('%');
                butin.add(sb.toString());
            }
            if (!butin.isEmpty()) r.put("butin", butin);
            Boolean soie = Yml.bool(drop, "silktouch");
            if (soie != null) r.put("toucher-de-soie", soie);
            Boolean fortune = Yml.bool(drop, "fortune");
            if (fortune != null) r.put("fortune", fortune);
        }
        sons(r, Yml.carte(b, "block_sounds"));
        if (Yml.valeur(b, "light") != null) e.remarque(quoi + " : lumière des blocs pas encore prise en charge");
        if (Boolean.TRUE.equals(Yml.bool(b, "is_falling"))) e.remarque(quoi + " : bloc qui tombe pas encore pris en charge");
        if (Yml.valeur(b, "storage") != null) e.remarque(quoi + " : bloc-coffre pas encore pris en charge (utilise un meuble avec stockage)");
        return r;
    }

    // ------------------------------------------------------------------ recettes

    /** Objet d'une recette ({nexo_item: x} ou {minecraft_type: X}) -> référence Vorace, ou null. */
    private String ingredient(Object o) {
        if (!(o instanceof Map<?, ?> m)) return o == null ? null : String.valueOf(o).toUpperCase(Locale.ROOT).replace("MINECRAFT:", "");
        Map<String, Object> c = Yml.carte(m);
        String nx = Yml.texte(c, "nexo_item");
        if (nx == null) nx = Yml.texte(c, "oraxen_item");
        if (nx != null) return ids.containsKey(nx) ? "vorace:" + ids.get(nx) : null;
        String mc = Yml.texte(c, "minecraft_type");
        if (mc != null) return mc.toUpperCase(Locale.ROOT).replace("MINECRAFT:", "");
        return null;
    }

    private void recette(String genre, String idr, Map<String, Object> r) {
        String quoi = nomPlugin + " recette « " + idr + " »";
        Object resultat = Yml.valeur(r, "result");
        String res = ingredient(resultat);
        if (res == null || !res.startsWith("vorace:")) { e.remarque(quoi + " : le résultat n'est pas un objet converti, ignorée"); return; }
        Map<String, Object> v = new LinkedHashMap<>();
        if (genre.contains("shapeless")) {
            Map<String, Object> ing = Yml.carte(r, "ingredients");
            List<Object> liste = new ArrayList<>();
            if (ing != null) for (Object x : ing.values()) {
                String o = ingredient(x);
                if (o == null) { e.remarque(quoi + " : ingrédient d'un autre plugin, ignorée"); return; }
                Double n = x instanceof Map<?, ?> m ? Yml.nombre(Yml.carte(m), "amount") : null;
                for (int i = 0; i < (n == null ? 1 : Math.max(1, n.intValue())); i++) liste.add(o);
            }
            if (liste.isEmpty()) return;
            v.put("type", "sans-forme");
            v.put("liste", liste);
        } else if (genre.contains("shaped")) {
            Map<String, Object> ing = Yml.carte(r, "ingredients");
            List<String> forme = Yml.liste(r, "shape");
            if (ing == null || forme.isEmpty()) { e.remarque(quoi + " : forme ou ingrédients manquants"); return; }
            Map<String, Object> ingredients = new LinkedHashMap<>();
            for (Map.Entry<String, Object> x : ing.entrySet()) {
                String o = ingredient(x.getValue());
                if (o == null) { e.remarque(quoi + " : ingrédient d'un autre plugin, ignorée"); return; }
                ingredients.put(x.getKey(), o);
            }
            List<String> f2 = new ArrayList<>();
            for (String ligne : forme) {
                StringBuilder sb = new StringBuilder();
                for (char c : ligne.toCharArray()) sb.append(ingredients.containsKey(String.valueOf(c)) ? c : ' ');
                f2.add(sb.toString());
            }
            v.put("type", "forme");
            v.put("forme", f2);
            v.put("ingredients", ingredients);
        } else if (genre.contains("furnace") || genre.contains("blast") || genre.contains("smok") || genre.contains("campfire")) {
            String entree = ingredient(Yml.valeur(r, "input"));
            if (entree == null) { e.remarque(quoi + " : ingrédient illisible"); return; }
            v.put("type", "four");
            v.put("entree", entree);
            Double xp = Yml.nombre(r, "experience");
            if (xp != null) v.put("experience", xp);
            Double t = Yml.nombre(r, "cookingTime");
            if (t != null) v.put("temps", t.intValue());
            if (!genre.contains("furnace")) e.remarque(quoi + " : recette de " + genre + " convertie en recette de four");
        } else {
            e.remarque(quoi + " : recettes « " + genre + " » pas encore prises en charge");
            return;
        }
        Double q = resultat instanceof Map<?, ?> m ? Yml.nombre(Yml.carte(m), "amount") : null;
        if (q != null && q > 1) v.put("quantite", q.intValue());
        e.recette(res.substring("vorace:".length()), v, quoi);
    }

    // ------------------------------------------------------------------ glyphes

    private void glyphe(String id, Map<String, Object> g) {
        String quoi = nomPlugin + " glyphe « " + id + " »";
        String tex = Yml.texte(g, "texture");
        if (tex == null) { e.remarque(quoi + " : pas de texture (glyphe spécial non converti)"); return; }
        String t = e.texture(tex, quoi);
        if (t == null) return;
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("texture", t);
        Double h = Yml.nombre(g, "height");
        if (h != null) v.put("hauteur", h.intValue());
        Double a = Yml.nombre(g, "ascent");
        if (a != null) v.put("montee", a.intValue());
        List<String> r = new ArrayList<>(Yml.liste(g, "placeholders"));
        Object chat = Yml.valeur(g, "chat");
        if (chat instanceof Map<?, ?> cm) r.addAll(Yml.liste(Yml.carte(cm), "placeholders"));
        r.addAll(Yml.liste(g, "placeholder"));
        String gid = e.idGlyphe(id, nomPlugin);
        r = e.raccourcis(gid, r, quoi);
        if (!r.isEmpty()) v.put("raccourcis", r);
        String perm = Yml.texte(g, "permission");
        if (perm != null && !perm.isBlank()) v.put("permission", perm);
        if (Boolean.TRUE.equals(Yml.bool(g, "is_emoji"))) v.put("liste", true);
        e.glyphes.put(gid, v);
    }
}
