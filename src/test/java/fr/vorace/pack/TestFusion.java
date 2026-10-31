package fr.vorace.pack;

import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Tests sans serveur : java -cp ... fr.vorace.pack.TestFusion [pack.zip ...] */
public final class TestFusion {
    static int echecs = 0;
    static void verifier(boolean ok, String quoi) { System.out.println((ok ? "OK    " : "ECHEC ") + quoi); if (!ok) echecs++; }
    static byte[] b(String s) { return s.getBytes(StandardCharsets.UTF_8); }
    static String lire(Constructeur.Resultat r, String chemin) throws Exception {
        try (ZipInputStream z = new ZipInputStream(new java.io.ByteArrayInputStream(r.zip()))) {
            ZipEntry e; while ((e = z.getNextEntry()) != null) if (e.getName().equals(chemin)) return new String(z.readAllBytes(), StandardCharsets.UTF_8);
        }
        return null;
    }

    public static void main(String[] args) throws Exception {
        Map<String, byte[]> a = new LinkedHashMap<>(), c = new LinkedHashMap<>();
        a.put("pack.mcmeta", b("{\"pack\":{\"pack_format\":46,\"description\":\"A\"}}"));
        a.put("assets/minecraft/sounds.json", b("{\"a\":{\"sounds\":[\"x\"]},\"commun\":{\"sounds\":[\"a\"]}}"));
        a.put("assets/minecraft/font/default.json", b("{\"providers\":[{\"type\":\"bitmap\",\"file\":\"a:x.png\",\"chars\":[\"\\ue000\"]}]}"));
        a.put("assets/minecraft/models/item/paper.json", b("{\"parent\":\"item/generated\",\"textures\":{\"layer0\":\"item/paper\"},\"overrides\":[{\"predicate\":{\"custom_model_data\":20},\"model\":\"a:vingt\"},{\"predicate\":{\"custom_model_data\":5},\"model\":\"a:cinq\"}]}"));
        a.put("assets/minecraft/items/paper.json", b("{\"model\":{\"type\":\"minecraft:range_dispatch\",\"property\":\"minecraft:custom_model_data\",\"entries\":[{\"threshold\":20,\"model\":{\"type\":\"model\",\"model\":\"a:vingt\"}}],\"fallback\":{\"type\":\"model\",\"model\":\"item/paper\"}}}"));
        a.put("assets/minecraft/items/stick.json", b("{\"model\":{\"type\":\"minecraft:model\",\"model\":\"item/stick\"}}"));
        a.put("assets/minecraft/lang/fr_fr.json", b("{\"a\":\"1\",\"b\":\"2\"}"));
        a.put("assets/a/textures/x.png", b("PNG-A"));
        a.put("assets/minecraft/atlases/blocks.json", b("{\"sources\":[{\"type\":\"directory\",\"source\":\"a\",\"prefix\":\"a/\"}]}"));
        a.put("Thumbs.db", b("junk"));
        c.put("MonPack/pack.mcmeta", b("{\"pack\":{\"pack_format\":46,\"description\":\"C\"},\"overlays\":{\"entries\":[{\"formats\":[46,99],\"directory\":\"ov_c\"}]}}"));
        c.put("MonPack/assets/minecraft/sounds.json", b("{\"c\":{\"sounds\":[\"y\"]},\"commun\":{\"sounds\":[\"c\"]}}"));
        c.put("MonPack/assets/minecraft/font/default.json", b("// commentaire toléré\n{\"providers\":[{\"type\":\"bitmap\",\"file\":\"c:y.png\",\"chars\":[\"\\ue001\"]}]}"));
        c.put("MonPack/assets/minecraft/models/item/paper.json", b("{\"parent\":\"item/generated\",\"textures\":{\"layer0\":\"item/paper\"},\"overrides\":[{\"predicate\":{\"custom_model_data\":10},\"model\":\"c:dix\"},{\"predicate\":{\"custom_model_data\":20},\"model\":\"c:vingt\"}]}"));
        c.put("MonPack/assets/minecraft/items/paper.json", b("{\"model\":{\"type\":\"range_dispatch\",\"property\":\"custom_model_data\",\"entries\":[{\"threshold\":3,\"model\":{\"type\":\"model\",\"model\":\"c:trois\"}}]}}"));
        c.put("MonPack/assets/minecraft/items/stick.json", b("{\"model\":{\"type\":\"minecraft:select\",\"property\":\"minecraft:custom_model_data\",\"cases\":[{\"when\":\"baton\",\"model\":{\"type\":\"model\",\"model\":\"c:baton\"}}]}}"));
        c.put("MonPack/assets/minecraft/lang/fr_fr.json", b("{\"b\":\"deux\",\"c\":\"3\"}"));
        c.put("MonPack/assets/a/textures/x.png", b("PNG-C"));
        c.put("MonPack/assets/minecraft/atlases/blocks.json", b("{\"sources\":[{\"type\":\"directory\",\"source\":\"c\",\"prefix\":\"c/\"}]}"));
        c.put("MonPack/ov_c/assets/c/x.txt", b("ov"));

        Path tmp = Files.createTempDirectory("vorace");
        Path zipC = tmp.resolve("c.zip");
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(zipC))) {
            for (var e : c.entrySet()) { z.putNextEntry(new ZipEntry(e.getKey())); z.write(e.getValue()); z.closeEntry(); }
        }
        Path dirA = tmp.resolve("a");
        for (var e : a.entrySet()) { Path p = dirA.resolve(e.getKey()); Files.createDirectories(p.getParent()); Files.write(p, e.getValue()); }

        List<Source> sources = List.of(Source.charger("a", dirA), Source.charger("c.zip", zipC));
        verifier(!sources.get(0).fichiers().containsKey("Thumbs.db"), "fichiers parasites ignorés");
        verifier(sources.get(1).fichiers().containsKey("pack.mcmeta"), "dossier racine du zip retiré");
        Constructeur.Resultat r = Constructeur.construire(sources, new Constructeur.Options("Test", new FormatPack(46, 0, "test"), true, null));
        System.out.print(r.journal().texte());
        String sons = lire(r, "assets/minecraft/sounds.json");
        verifier(sons.contains("\"a\"") && sons.contains("\"c\"") && sons.contains("\"commun\":{\"sounds\":[\"c\"]}"), "sounds.json fusionné " + sons);
        String font = lire(r, "assets/minecraft/font/default.json");
        verifier(font.indexOf("c:y.png") < font.indexOf("a:x.png") && font.indexOf("a:x.png") > 0, "font : providers réunis, prioritaire devant " + font);
        String mod = lire(r, "assets/minecraft/models/item/paper.json");
        verifier(mod.indexOf("a:cinq") < mod.indexOf("c:dix") && mod.indexOf("c:dix") < mod.indexOf("c:vingt") && !mod.contains("a:vingt"), "overrides réunis et triés " + mod);
        String it = lire(r, "assets/minecraft/items/paper.json");
        verifier(it.indexOf("c:trois") < it.indexOf("a:vingt") && it.contains("item/paper"), "items range_dispatch réunis + repli gardé " + it);
        String st = lire(r, "assets/minecraft/items/stick.json");
        verifier(st.contains("c:baton") && st.contains("\"fallback\":{\"type\":\"minecraft:model\",\"model\":\"item/stick\"}"), "items : modèle simple devient le repli du select " + st);
        String lg = lire(r, "assets/minecraft/lang/fr_fr.json");
        verifier(lg.contains("\"a\":\"1\"") && lg.contains("\"b\":\"deux\"") && lg.contains("\"c\":\"3\""), "lang fusionné " + lg);
        verifier("PNG-C".equals(lire(r, "assets/a/textures/x.png")), "texture : la source prioritaire gagne");
        String at = lire(r, "assets/minecraft/atlases/blocks.json");
        verifier(at.contains("\"a/\"") && at.contains("\"c/\""), "atlas fusionné");
        String mc = lire(r, "pack.mcmeta");
        verifier(mc.contains("\"pack_format\": 46") && mc.contains("ov_c") && mc.contains("\"Test\""), "pack.mcmeta écrit avec overlays " + mc);
        verifier(lire(r, "ov_c/assets/c/x.txt") != null, "dossier d'overlay copié");
        Constructeur.Resultat r2 = Constructeur.construire(sources, new Constructeur.Options("Test", new FormatPack(46, 0, "test"), true, null));
        verifier(r.sha1().equals(r2.sha1()), "même contenu = même SHA-1");
        Constructeur.Resultat r3 = Constructeur.construire(sources, new Constructeur.Options("Test", new FormatPack(84, 0, "test"), true, null));
        String mc3 = lire(r3, "pack.mcmeta");
        verifier(mc3.contains("\"min_format\": 84") && mc3.contains("\"max_format\": 999") && !mc3.contains("pack_format"), "format 26.x : min_format/max_format " + mc3);

        // vrais packs passés en argument
        if (args.length > 0) {
            List<Source> vrais = new ArrayList<>();
            for (String s : args) vrais.add(Source.charger(Path.of(s).getFileName().toString(), Path.of(s)));
            Constructeur.Resultat rv = Constructeur.construire(vrais, new Constructeur.Options("Vrais packs", new FormatPack(46, 0, "test"), true, null));
            System.out.println("--- vrais packs : " + rv.journal().compter(Journal.Niveau.FUSION) + " fusions, " + rv.journal().compter(Journal.Niveau.CONFLIT) + " conflits, " + rv.journal().compter(Journal.Niveau.ERREUR) + " erreurs, " + rv.dureeMs() + " ms");
            rv.journal().lignes().stream().filter(l -> l.niveau() != Journal.Niveau.FUSION).limit(40).forEach(System.out::println);
            Files.write(Path.of(System.getProperty("java.io.tmpdir"), "vorace-vrais.zip"), rv.zip());
        }
        System.out.println(echecs == 0 ? "TOUS LES TESTS PASSENT" : echecs + " ECHEC(S)");
        System.exit(echecs == 0 ? 0 : 1);
    }
}
