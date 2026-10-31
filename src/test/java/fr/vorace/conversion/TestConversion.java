package fr.vorace.conversion;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.vorace.modeles.LecteurBBModel;
import fr.vorace.modeles.ModeleBB;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Convertit les exemples Nexo, ItemsAdder et ModelEngine de outils/exemples-conversion et vérifie le résultat. */
public final class TestConversion {
    static int echecs = 0;
    static void verifier(boolean ok, String quoi) { System.out.println((ok ? "OK    " : "ECHEC ") + quoi); if (!ok) echecs++; }

    @SuppressWarnings("unchecked")
    static Map<String, Object> m(Object o) { return (Map<String, Object>) o; }

    public static void main(String[] a) throws Exception {
        Path exemples = Path.of("outils/exemples-conversion");
        if (!Files.isDirectory(exemples)) throw new IllegalStateException("lancer depuis la racine du projet");
        Path plugins = Files.createTempDirectory("vorace-conv");
        copier(exemples, plugins);
        // modèle ModelEngine : le golem d'exemple avec un os « hitbox »
        String golem;
        try (InputStream in = TestConversion.class.getClassLoader().getResourceAsStream("exemples/modeles/golem_rubis.bbmodel")) {
            golem = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Files.createDirectories(plugins.resolve("ModelEngine/blueprints/monstres"));
        Files.writeString(plugins.resolve("ModelEngine/blueprints/monstres/golem_me.bbmodel"), avecHitbox(golem));

        Path vorace = plugins.resolve("Vorace");
        Files.createDirectories(vorace.resolve("objets"));
        Files.createDirectories(vorace.resolve("glyphes"));
        // objets et glyphes déjà dans Vorace : les conversions ne doivent pas les écraser
        Files.writeString(vorace.resolve("objets/exemple.yml"), "rubis:\n  materiau: PAPER\ntable:\n  materiau: PAPER\n");
        Files.writeString(vorace.resolve("glyphes/exemple.yml"), "etoile:\n  texture: x\n");

        for (Conversion.Source s : Conversion.Source.values()) verifier(Conversion.present(plugins, s) == (s != Conversion.Source.ORAXEN), s.nom + " détecté : " + Conversion.present(plugins, s));

        // ---------------------------------------------------------------- Nexo
        Conversion.Resultat rn = Conversion.convertir(plugins, vorace, Conversion.Source.NEXO);
        System.out.println("  " + rn.resume());
        Files.readAllLines(rn.rapport()).forEach(l -> System.out.println("  | " + l));
        Map<String, Object> on = yml(vorace.resolve("objets/converti/nexo.yml"));
        verifier(on.keySet().containsAll(List.of("epee_rubis", "nexo_rubis", "saphir", "chaise", "lanterne", "minerai_rubis")) && !on.containsKey("gemme_modele"),
            "Nexo : objets convertis, « rubis » déjà pris devient nexo_rubis, le modèle d'objet n'est pas un objet " + on.keySet());
        Map<String, Object> epee = m(on.get("epee_rubis"));
        verifier("DIAMOND_SWORD".equals(epee.get("materiau")) && "<red>Épée de rubis".equals(epee.get("nom")), "Nexo : matériau et nom");
        verifier(String.valueOf(epee.get("description")).contains("<gray>Taillée dans un rubis"), "Nexo : description &7 -> MiniMessage " + epee.get("description"));
        verifier("converti/nexo/minecraft/armes/epee_rubis".equals(epee.get("texture")) && Boolean.TRUE.equals(epee.get("en-main")), "Nexo : texture plate tenue en main");
        verifier(Files.isRegularFile(vorace.resolve("ressources/textures/converti/nexo/minecraft/armes/epee_rubis.png")), "Nexo : texture recopiée dans ressources/textures");
        verifier(Integer.valueOf(2).equals(m(epee.get("enchantements")).get("sharpness")), "Nexo : enchantement");
        Map<String, Object> att = m(m(epee.get("attributs")).get("attack_damage"));
        verifier(att != null && Double.valueOf(9).equals(att.get("valeur")) && "main".equals(att.get("emplacement")) && "ajout".equals(att.get("operation")), "Nexo : attribut " + epee.get("attributs"));
        verifier(Integer.valueOf(1).equals(epee.get("taille-pile")), "Nexo : composant max_stack_size");
        Map<String, Object> rec = m(epee.get("recette"));
        verifier(rec != null && "forme".equals(rec.get("type")) && List.of(" R ", " R ", " S ").equals(rec.get("forme"))
            && "vorace:nexo_rubis".equals(m(rec.get("ingredients")).get("R")) && "STICK".equals(m(rec.get("ingredients")).get("S")), "Nexo : recette en forme " + rec);

        Map<String, Object> saphir = m(on.get("saphir"));
        verifier("EMERALD".equals(saphir.get("materiau")) && String.valueOf(saphir.get("description")).contains("Une gemme rare"), "Nexo : modèle d'objet (template) appliqué");
        verifier("converti/nexo/minecraft/objets/saphir".equals(saphir.get("modele")), "Nexo : modèle recopié " + saphir.get("modele"));
        JsonObject ms = JsonParser.parseString(Files.readString(vorace.resolve("ressources/modeles/converti/nexo/minecraft/objets/saphir.json"))).getAsJsonObject();
        verifier("minecraft:block/block".equals(ms.get("parent").getAsString()) && ms.has("elements") && ms.getAsJsonObject("textures").get("0").getAsString().equals("converti/nexo/minecraft/objets/saphir")
            && ms.getAsJsonObject("textures").get("particle").getAsString().equals("converti/nexo/minecraft/objets/rubis"), "Nexo : parent personnalisé fusionné (réglages d'affichage d'un bloc ajoutés), textures réécrites " + ms.get("textures"));
        Map<String, Object> cmd = m(saphir.get("commandes"));
        verifier(cmd != null && List.of("console: say %joueur% brandit un saphir").equals(cmd.get("clic-droit")) && Integer.valueOf(40).equals(cmd.get("delai-ticks")), "Nexo : commandes " + cmd);

        Map<String, Object> chaise = m(m(on.get("chaise")).get("meuble"));
        verifier(List.of("0,0,0").equals(chaise.get("hitbox")) && List.of("0, 0.5, 0").equals(chaise.get("sieges")) && Integer.valueOf(4).equals(chaise.get("rotation"))
            && List.of("sol").equals(chaise.get("pose")) && "block.wood.place".equals(chaise.get("son-pose")), "Nexo : meuble chaise " + chaise);
        Map<String, Object> lanterne = m(m(on.get("lanterne")).get("meuble"));
        verifier(Double.valueOf(0.5).equals(m(lanterne.get("hitbox")).get("largeur")) && Double.valueOf(0.8).equals(m(lanterne.get("hitbox")).get("hauteur"))
            && Integer.valueOf(12).equals(lanterne.get("lumiere")), "Nexo : meuble lanterne (hitbox d'interaction, lumière) " + lanterne);
        Map<String, Object> mineraiN = m(on.get("minerai_rubis"));
        Map<String, Object> bn = m(mineraiN.get("bloc"));
        verifier(!mineraiN.containsKey("texture") && m(bn.get("textures")).get("haut").equals("converti/nexo/minecraft/blocs/minerai_rubis_haut")
            && m(bn.get("textures")).get("cotes") != null, "Nexo : bloc avec textures par face " + bn.get("textures"));
        verifier(Double.valueOf(3).equals(bn.get("durete")) && "pioche".equals(bn.get("outil")) && "fer".equals(bn.get("niveau"))
            && List.of("vorace:nexo_rubis 1-2").equals(bn.get("butin")) && Boolean.TRUE.equals(bn.get("fortune")), "Nexo : bloc (dureté, outil, butin) " + bn);
        Map<String, Object> gn = yml(vorace.resolve("glyphes/converti/nexo.yml"));
        Map<String, Object> et = m(gn.get("nexo_etoile"));
        verifier(et != null && Integer.valueOf(8).equals(et.get("hauteur")) && Integer.valueOf(7).equals(et.get("montee")) && !et.containsKey("raccourcis")
            && Boolean.TRUE.equals(et.get("liste")), "Nexo : glyphe renommé, raccourci « :etoile: » déjà pris non repris " + gn);
        verifier(Files.isRegularFile(vorace.resolve("pack/sources/converti-nexo/assets/minecraft/sounds.json"))
            && Files.isRegularFile(vorace.resolve("pack/sources/converti-nexo/assets/minecraft/sounds/meubles/grince.ogg")), "Nexo : sons recopiés dans un pack source");
        Map<String, Object> table = yml(vorace.resolve("donnees/conversion-ids.yml"));
        verifier("nexo_rubis".equals(table.get("nexo:rubis")) && "chaise".equals(table.get("nexo:chaise")), "Nexo : table des anciens identifiants");

        // ---------------------------------------------------------------- ItemsAdder
        Conversion.Resultat ri = Conversion.convertir(plugins, vorace, Conversion.Source.ITEMSADDER);
        System.out.println("  " + ri.resume());
        Files.readAllLines(ri.rapport()).forEach(l -> System.out.println("  | " + l));
        Map<String, Object> oi = yml(vorace.resolve("objets/converti/itemsadder.yml"));
        verifier(oi.keySet().containsAll(List.of("baguette", "demo_table", "tabouret", "bloc_ambre")), "ItemsAdder : objets convertis, « table » déjà pris devient demo_table " + oi.keySet());
        Map<String, Object> bag = m(oi.get("baguette"));
        verifier("STICK".equals(bag.get("materiau")) && String.valueOf(bag.get("nom")).contains("Baguette magique") && "converti/itemsadder/demo/item/baguette".equals(bag.get("texture")),
            "ItemsAdder : objet généré depuis sa texture " + bag);
        verifier(Integer.valueOf(3).equals(m(bag.get("enchantements")).get("unbreaking")), "ItemsAdder : ancien nom d'enchantement DURABILITY -> unbreaking");
        verifier(m(bag.get("attributs")).containsKey("attack_damage"), "ItemsAdder : attribut attackDamage");
        verifier(List.of("console: say %joueur% lance un sort").equals(m(bag.get("commandes")).get("clic-droit")), "ItemsAdder : commande au clic droit");
        Map<String, Object> rb = m(bag.get("recette"));
        verifier(rb != null && "forme".equals(rb.get("type")) && "STICK".equals(m(rb.get("ingredients")).get("X")), "ItemsAdder : recette " + rb);
        Map<String, Object> ta = m(oi.get("demo_table"));
        verifier(!ta.containsKey("nom") && "converti/itemsadder/demo/meubles/table".equals(ta.get("modele")), "ItemsAdder : clé de traduction ignorée, modèle recopié");
        verifier(List.of("0,0,0", "1,0,0").equals(m(ta.get("meuble")).get("hitbox")), "ItemsAdder : meuble solide 2x1 -> 2 barrières " + m(ta.get("meuble")).get("hitbox"));
        JsonObject mt = JsonParser.parseString(Files.readString(vorace.resolve("ressources/modeles/converti/itemsadder/demo/meubles/table.json"))).getAsJsonObject();
        verifier(mt.getAsJsonObject("textures").get("0").getAsString().equals("converti/itemsadder/demo/item/bois_clair"), "ItemsAdder : texture du modèle (espace demo:) recopiée");
        Map<String, Object> tab = m(oi.get("tabouret"));
        Map<String, Object> tabm = m(tab.get("meuble"));
        verifier("converti/itemsadder/demo/meubles/tabouret".equals(tab.get("modele")) && List.of("0, 0.4, 0").equals(tabm.get("sieges"))
            && Double.valueOf(0.5).equals(m(tabm.get("hitbox")).get("hauteur")), "ItemsAdder : graphics.model (dossier resourcepack/assets), siège, hitbox non solide " + tab);
        Map<String, Object> ba = m(m(oi.get("bloc_ambre")).get("bloc"));
        verifier("converti/itemsadder/demo/block/ambre".equals(ba.get("textures")) && "pioche".equals(ba.get("outil")) && List.of("vorace:baguette 1 50%").equals(ba.get("butin")),
            "ItemsAdder : bloc (texture, outil, butin de loots.blocks) " + ba);
        Map<String, Object> gi = yml(vorace.resolve("glyphes/converti/itemsadder.yml"));
        verifier(gi.containsKey("soleil") && Integer.valueOf(9).equals(m(gi.get("soleil")).get("hauteur")) && Files.isRegularFile(vorace.resolve("ressources/textures/converti/itemsadder/demo/font/soleil.png")),
            "ItemsAdder : image de police -> glyphe " + gi);
        verifier("baguette".equals(yml(vorace.resolve("donnees/conversion-ids.yml")).get("itemsadder:demo:baguette"))
            && "nexo_rubis".equals(yml(vorace.resolve("donnees/conversion-ids.yml")).get("nexo:rubis")), "table des identifiants complétée, pas remplacée");

        // ---------------------------------------------------------------- ModelEngine
        Conversion.Resultat rm = Conversion.convertir(plugins, vorace, Conversion.Source.MODELENGINE);
        System.out.println("  " + rm.resume());
        Path bb = vorace.resolve("modeles-animes/modelengine/golem_me.bbmodel");
        verifier(Files.isRegularFile(bb), "ModelEngine : blueprint recopié dans modeles-animes/modelengine (sans le sous-dossier)");
        List<String> problemes = new ArrayList<>();
        ModeleBB mod = LecteurBBModel.lire("modelengine/golem_me", Files.readString(bb), problemes);
        verifier(problemes.isEmpty() && mod.parties().size() == 8 && mod.parties().stream().noneMatch(p -> p.nom().equals("hitbox")), "ModelEngine : os « hitbox » caché " + problemes);
        verifier(Math.abs(mod.largeur() - 16) < 1e-6 && Math.abs(mod.hauteur() - 40) < 1e-6, "ModelEngine : taille de la hitbox prise sur l'os hitbox (" + mod.largeur() + " x " + mod.hauteur() + ")");

        // une deuxième conversion remplace la première sans doublons
        Conversion.Resultat rn2 = Conversion.convertir(plugins, vorace, Conversion.Source.NEXO);
        verifier(yml(vorace.resolve("objets/converti/nexo.yml")).keySet().equals(on.keySet()) && rn2.resume().equals(rn.resume()), "nouvelle conversion : mêmes identifiants (pas de nexo_nexo_rubis)");

        supprimer(plugins);
        if (echecs > 0) { System.out.println(echecs + " ECHEC(S)"); System.exit(1); }
        System.out.println("TOUS LES TESTS PASSENT");
    }

    static Map<String, Object> yml(Path f) throws IOException {
        Object o = new Yaml().load(Files.readString(f, StandardCharsets.UTF_8));
        return o == null ? Map.of() : m(o);
    }

    static String avecHitbox(String golem) {
        JsonObject g = JsonParser.parseString(golem).getAsJsonObject();
        JsonObject c = JsonParser.parseString("{\"name\":\"hb\",\"type\":\"cube\",\"uuid\":\"hitbox-cube-0001\",\"from\":[-8,0,-8],\"to\":[8,40,8],\"origin\":[0,0,0],\"faces\":{}}").getAsJsonObject();
        g.getAsJsonArray("elements").add(c);
        JsonObject os = JsonParser.parseString("{\"name\":\"hitbox\",\"origin\":[0,0,0],\"uuid\":\"hitbox-groupe-0001\",\"children\":[\"hitbox-cube-0001\"]}").getAsJsonObject();
        JsonArray outliner = g.getAsJsonArray("outliner");
        outliner.add(os);
        return g.toString();
    }

    static void copier(Path de, Path vers) throws IOException {
        try (Stream<Path> w = Files.walk(de)) {
            for (Path p : w.toList()) {
                Path c = vers.resolve(de.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(c);
                else Files.copy(p, c);
            }
        }
    }

    static void supprimer(Path d) throws IOException {
        try (Stream<Path> w = Files.walk(d)) {
            for (Path p : w.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        }
    }
}
