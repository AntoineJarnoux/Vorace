package fr.vorace.blocs;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.vorace.pack.Journal;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Teste les états des blocs à note, la lecture du butin, les outils et les fichiers du pack des blocs. */
public final class TestBlocs {
    static int echecs = 0;
    static void verifier(boolean ok, String quoi) { System.out.println((ok ? "OK    " : "ECHEC ") + quoi); if (!ok) echecs++; }

    public static void main(String[] a) throws Exception {
        // états : chaque numéro a son état, aller-retour par la clé, jamais l'état par défaut du jeu
        Set<String> vus = new HashSet<>();
        boolean allerRetour = true;
        for (int n = 0; n < EtatsNoteBlock.CAPACITE; n++) {
            String e = EtatsNoteBlock.etat(n);
            vus.add(e);
            if (!e.equals(EtatsNoteBlock.cle(EtatsNoteBlock.donnees(n)))) allerRetour = false;
        }
        verifier(EtatsNoteBlock.CAPACITE == 550 && vus.size() == 550, "550 blocs possibles, tous différents");
        verifier(allerRetour, "état relu depuis les données de bloc");
        verifier(!vus.contains("instrument=harp,note=0"), "l'état par défaut du bloc à note n'est jamais pris");
        verifier(EtatsNoteBlock.etat(0).equals("instrument=custom_head,note=0"), "les instruments de têtes de mobs d'abord");
        verifier("instrument=banjo,note=7".equals(EtatsNoteBlock.cle("minecraft:note_block[instrument=banjo,note=7,powered=true]")),
            "clé lue même quand le bloc est alimenté");
        verifier(EtatsNoteBlock.cle("minecraft:stone") == null && EtatsNoteBlock.cle(null) == null, "pas de clé pour un autre bloc");
        boolean hors = false;
        try { EtatsNoteBlock.etat(550); } catch (IllegalArgumentException e) { hors = true; }
        verifier(hors, "numéro hors limites refusé");

        // butin
        DefinitionBloc.Butin b = DefinitionBloc.Butin.lire("vorace:rubis 1-2");
        verifier(b.objet().equals("vorace:rubis") && b.min() == 1 && b.max() == 2 && b.chance() == 1, "butin « vorace:rubis 1-2 »");
        b = DefinitionBloc.Butin.lire("DIAMOND 3 25%");
        verifier(b.objet().equals("DIAMOND") && b.min() == 3 && b.max() == 3 && Math.abs(b.chance() - 0.25) < 1e-9, "butin « DIAMOND 3 25% »");
        verifier(DefinitionBloc.Butin.lire("COAL").min() == 1, "quantité 1 par défaut");
        int[] q = DefinitionBloc.intervalle("7-3");
        verifier(q[0] == 3 && q[1] == 7, "intervalle remis dans l'ordre");
        q = DefinitionBloc.intervalle("-5");
        verifier(q[0] == -5 && q[1] == -5, "nombre négatif");
        verifier(DefinitionBloc.niveau("fer") == 2 && DefinitionBloc.niveau("diamond") == 3 && DefinitionBloc.niveau(null) == 0, "niveaux d'outil");

        // outils
        Outils.Outil o = Outils.lire("minecraft:iron_pickaxe");
        verifier(o != null && o.type().equals("pioche") && o.niveau() == 2 && o.vitesse() == 6f, "pioche en fer");
        o = Outils.lire("NETHERITE_AXE");
        verifier(o != null && o.type().equals("hache") && o.niveau() == 4, "hache en netherite");
        verifier(Outils.lire("DIAMOND") == null && Outils.lire("STICK") == null, "pas un outil");
        verifier(Outils.lire("GOLDEN_PICKAXE").niveau() == 0 && Outils.lire("GOLDEN_PICKAXE").vitesse() == 12f, "or : rapide mais niveau bois");

        // pack : les deux blocs d'exemple
        Path r = Files.createTempDirectory("vorace-blocs");
        for (String t : List.of("minerai_rubis", "bloc_rubis")) copier("exemples/blocs/" + t + ".png", r.resolve("textures/exemple/" + t + ".png"));
        Journal j = new Journal();
        Map<String, byte[]> pack = GenerateurBlocs.generer(List.of(
            new GenerateurBlocs.Apparence("minerai_rubis", 0, Map.of("tout", "exemple/minerai_rubis"), null),
            new GenerateurBlocs.Apparence("bloc_rubis", 26, Map.of("haut", "exemple/bloc_rubis", "cotes", "exemple/minerai_rubis"), null),
            new GenerateurBlocs.Apparence("absent", 2, Map.of("tout", "exemple/absent"), null)), r, j);
        JsonObject etats = JsonParser.parseString(new String(pack.get("assets/minecraft/blockstates/note_block.json"), StandardCharsets.UTF_8))
            .getAsJsonObject().getAsJsonObject("variants");
        verifier(etats.size() == EtatsNoteBlock.INSTRUMENTS.size() * 25 * 2, "les 1150 états du bloc à note sont décrits");
        verifier(modele(etats, "instrument=custom_head,note=0,powered=false").equals("vorace:block/minerai_rubis")
            && modele(etats, "instrument=custom_head,note=0,powered=true").equals("vorace:block/minerai_rubis"), "minerai : même modèle alimenté ou non");
        verifier(modele(etats, "instrument=piglin,note=1,powered=false").equals("vorace:block/bloc_rubis"), "numéro 26 = piglin note 1");
        verifier(modele(etats, "instrument=harp,note=0,powered=false").equals("minecraft:block/note_block")
            && modele(etats, "instrument=custom_head,note=2,powered=false").equals("minecraft:block/note_block"), "les autres états gardent le bloc à note du jeu");
        String minerai = new String(pack.get("assets/vorace/models/block/minerai_rubis.json"), StandardCharsets.UTF_8);
        verifier(minerai.contains("minecraft:block/cube_all") && minerai.contains("vorace:item/exemple/minerai_rubis")
            && pack.containsKey("assets/vorace/textures/item/exemple/minerai_rubis.png"), "minerai : cube_all et texture copiée");
        JsonObject tex = JsonParser.parseString(new String(pack.get("assets/vorace/models/block/bloc_rubis.json"), StandardCharsets.UTF_8))
            .getAsJsonObject().getAsJsonObject("textures");
        verifier(tex.get("up").getAsString().endsWith("bloc_rubis") && tex.get("down").getAsString().endsWith("bloc_rubis")
            && tex.get("north").getAsString().endsWith("minerai_rubis"), "faces : haut, bas comme le haut, côtés");
        verifier(!pack.containsKey("assets/vorace/models/block/absent.json") && j.compter(Journal.Niveau.ERREUR) == 1, "texture absente signalée");
        verifier(GenerateurBlocs.generer(List.of(), r, new Journal()).isEmpty(), "aucun bloc : le bloc à note du jeu n'est pas touché");

        System.out.println(echecs == 0 ? "TOUS LES TESTS PASSENT" : echecs + " ECHEC(S)");
        System.exit(echecs == 0 ? 0 : 1);
    }

    static String modele(JsonObject variantes, String cle) {
        return variantes.getAsJsonObject(cle).get("model").getAsString();
    }

    static void copier(String ressource, Path cible) throws Exception {
        Files.createDirectories(cible.getParent());
        try (InputStream in = TestBlocs.class.getClassLoader().getResourceAsStream(ressource)) {
            if (in == null) throw new IllegalStateException("ressource absente : " + ressource);
            Files.copy(in, cible);
        }
    }
}
