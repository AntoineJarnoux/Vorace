package fr.vorace.objets;

import fr.vorace.pack.FormatPack;
import fr.vorace.pack.Journal;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Teste la génération du pack des objets pour les trois familles de versions. */
public final class TestObjets {
    static int echecs = 0;
    static void verifier(boolean ok, String quoi) { System.out.println((ok ? "OK    " : "ECHEC ") + quoi); if (!ok) echecs++; }
    static String s(Map<String, byte[]> m, String k) { byte[] b = m.get(k); return b == null ? null : new String(b, StandardCharsets.UTF_8); }

    public static void main(String[] a) throws Exception {
        Path r = Files.createTempDirectory("vorace-res");
        Files.createDirectories(r.resolve("textures/armes"));
        Files.createDirectories(r.resolve("modeles"));
        Files.write(r.resolve("textures/armes/rubis.png"), new byte[]{1, 2, 3});
        Files.write(r.resolve("textures/couronne_or.png"), new byte[]{4});
        Files.writeString(r.resolve("textures/couronne_or.png.mcmeta"), "{\"animation\":{}}");
        // export Blockbench typique : textures sans espace de noms, et une texture du jeu de base
        Files.writeString(r.resolve("modeles/couronne.json"), "{\"credit\":\"Made with Blockbench\",\"textures\":{\"0\":\"couronne_or\",\"1\":\"item/couronne_or\",\"particle\":\"minecraft:block/gold_block\",\"2\":\"#0\"},\"elements\":[]}");

        List<Visuel> v = List.of(
            new Visuel("rubis", "paper", "armes/rubis", null, false, 1001, null),
            new Visuel("epee", "diamond_sword", "armes/rubis", null, true, 1002, null),
            new Visuel("couronne", "paper", null, "couronne", false, 1003, null),
            new Visuel("bottes", "leather_boots", "armes/rubis", null, false, 1004, 0xFF3366FF),
            new Visuel("absent", "paper", "nexiste/pas", null, false, 1005, null));

        Journal j = new Journal();
        Map<String, byte[]> m = GenerateurPack.generer(v, r, new FormatPack(46, 0, "t"), j);
        verifier(s(m, "assets/vorace/items/rubis.json").equals("{\"model\":{\"type\":\"minecraft:model\",\"model\":\"vorace:item/rubis\"}}"), "1.21.4+ : définition items/rubis.json");
        verifier(s(m, "assets/vorace/models/item/rubis.json").contains("\"minecraft:item/generated\"") && s(m, "assets/vorace/models/item/rubis.json").contains("vorace:item/armes/rubis"), "modèle plat généré");
        verifier(s(m, "assets/vorace/models/item/epee.json").contains("item/handheld"), "épée : modèle tenu en main");
        verifier(m.containsKey("assets/vorace/textures/item/armes/rubis.png"), "texture copiée");
        String cour = s(m, "assets/vorace/models/item/couronne.json");
        verifier(cour.contains("\"0\":\"vorace:item/couronne_or\"") && cour.contains("\"1\":\"vorace:item/couronne_or\"") && cour.contains("minecraft:block/gold_block") && cour.contains("\"#0\""), "Blockbench : textures reliées, texture du jeu gardée " + cour);
        verifier(m.containsKey("assets/vorace/textures/item/couronne_or.png.mcmeta"), "animation de texture copiée");
        verifier(s(m, "assets/vorace/items/bottes.json").contains("\"minecraft:dye\""), "cuir : teinte dans la définition");
        verifier(!m.containsKey("assets/vorace/items/absent.json") && j.compter(Journal.Niveau.ERREUR) == 1, "texture absente : erreur claire, objet ignoré");
        verifier(m.keySet().stream().noneMatch(k -> k.startsWith("assets/minecraft/")), "1.21.4+ : aucun fichier du jeu de base modifié");

        Map<String, byte[]> m42 = GenerateurPack.generer(v, r, new FormatPack(42, 0, "t"), new Journal());
        verifier(m42.containsKey("assets/vorace/models/item/rubis.json") && !m42.containsKey("assets/vorace/items/rubis.json"), "1.21.2/3 : modèle direct, pas de définition items/");

        Map<String, byte[]> m34 = GenerateurPack.generer(v, r, new FormatPack(34, 0, "t"), new Journal());
        String paper = s(m34, "assets/minecraft/models/item/paper.json");
        verifier(paper != null && paper.indexOf("1001") < paper.indexOf("1003") && paper.contains("minecraft:item/paper") && paper.contains("item/generated"), "1.21/1.21.1 : overrides custom_model_data triés " + paper);
        verifier(s(m34, "assets/minecraft/models/item/diamond_sword.json").contains("item/handheld"), "1.21/1.21.1 : épée de base tenue en main");
        System.out.println(echecs == 0 ? "TOUS LES TESTS PASSENT" : echecs + " ECHEC(S)");
        System.exit(echecs == 0 ? 0 : 1);
    }
}
