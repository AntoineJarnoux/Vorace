package fr.vorace.conversion;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/** Petits outils pour lire les fichiers .yml des autres plugins (sans dépendre de leur code). */
final class Yml {

    private Yml() {}

    static Map<String, Object> lire(Path f) throws IOException {
        Object o = new Yaml(new SafeConstructor(new LoaderOptions())).load(Files.readString(f, StandardCharsets.UTF_8));
        return carte(o);
    }

    /** Tous les .yml d'un dossier (sous-dossiers compris), triés. */
    static List<Path> fichiers(Path dossier) {
        if (!Files.isDirectory(dossier)) return List.of();
        try (Stream<Path> s = Files.walk(dossier)) {
            return s.filter(p -> {
                String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                return Files.isRegularFile(p) && (n.endsWith(".yml") || n.endsWith(".yaml"));
            }).sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> carte(Object o) {
        if (!(o instanceof Map<?, ?> m)) return new LinkedHashMap<>();
        Map<String, Object> r = new LinkedHashMap<>();
        m.forEach((k, v) -> r.put(String.valueOf(k), v));
        return r;
    }

    static Map<String, Object> carte(Map<String, Object> m, String cle) {
        Object o = valeur(m, cle);
        return o instanceof Map<?, ?> ? carte(o) : null;
    }

    /** Valeur d'une clé, sans tenir compte des majuscules ni des « - » / « _ ». */
    static Object valeur(Map<String, Object> m, String cle) {
        if (m == null) return null;
        if (m.containsKey(cle)) return m.get(cle);
        String c = norm(cle);
        for (Map.Entry<String, Object> e : m.entrySet()) if (norm(e.getKey()).equals(c)) return e.getValue();
        return null;
    }

    private static String norm(String s) {
        return s.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
    }

    static String texte(Map<String, Object> m, String cle) {
        Object o = valeur(m, cle);
        return o == null || o instanceof Map || o instanceof List ? null : String.valueOf(o);
    }

    static List<String> liste(Map<String, Object> m, String cle) {
        Object o = valeur(m, cle);
        List<String> r = new ArrayList<>();
        if (o instanceof List<?> l) {
            for (Object x : l) if (x != null && !(x instanceof Map)) r.add(String.valueOf(x));
        } else if (o != null && !(o instanceof Map)) {
            r.add(String.valueOf(o));
        }
        return r;
    }

    static List<Map<String, Object>> listeCartes(Map<String, Object> m, String cle) {
        Object o = valeur(m, cle);
        List<Map<String, Object>> r = new ArrayList<>();
        if (o instanceof List<?> l) for (Object x : l) if (x instanceof Map) r.add(carte(x));
        return r;
    }

    static Boolean bool(Map<String, Object> m, String cle) {
        Object o = valeur(m, cle);
        if (o instanceof Boolean b) return b;
        if (o != null) return Boolean.parseBoolean(String.valueOf(o).trim());
        return null;
    }

    static Double nombre(Map<String, Object> m, String cle) {
        Object o = valeur(m, cle);
        if (o instanceof Number n) return n.doubleValue();
        if (o != null) {
            try { return Double.parseDouble(String.valueOf(o).trim()); } catch (NumberFormatException ignore) { }
        }
        return null;
    }

    /** Texte coloré : « &amp;c » ou « §c » (ancien format) vers MiniMessage ; un texte MiniMessage reste tel quel. */
    static String miniMessage(String s) {
        if (s == null) return null;
        if (s.indexOf('&') < 0 && s.indexOf('§') < 0) return s;
        boolean section = s.indexOf('§') >= 0;
        try {
            LegacyComponentSerializer l = section ? LegacyComponentSerializer.legacySection() : LegacyComponentSerializer.builder().character('&').hexColors().build();
            return MiniMessage.miniMessage().serialize(l.deserialize(s));
        } catch (RuntimeException e) {
            return s;
        }
    }

    /** « 1, 2, 3 » ou « 1,2,3 » ou [1, 2, 3] -> trois nombres, ou null. */
    static double[] triplet(Object o) {
        if (o == null) return null;
        String[] p;
        if (o instanceof List<?> l) p = l.stream().map(String::valueOf).toArray(String[]::new);
        else p = String.valueOf(o).trim().split("\\s*,\\s*|\\s+");
        if (p.length < 3) return null;
        try {
            return new double[]{Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2])};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String nombreCourt(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(Math.round(d * 1000) / 1000.0);
    }
}
