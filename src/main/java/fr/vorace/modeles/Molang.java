package fr.vorace.modeles;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Petit évaluateur Molang pour les clés d'animation de Blockbench (sans Bukkit) : nombres, + - * /, parenthèses,
 * comparaisons, &amp;&amp; || !, condition ? a : b, fonctions math.* (angles en degrés comme dans Molang) et le temps
 * de l'animation (query.anim_time, q.anim_time, query.life_time). Une variable inconnue vaut 0.
 */
public final class Molang {

    private interface Noeud { double val(double t); }

    private final String source;
    private final Noeud racine;
    private final boolean constant;

    private Molang(String source, Noeud racine, boolean constant) {
        this.source = source;
        this.racine = racine;
        this.constant = constant;
    }

    public String source() { return source; }

    /** Vrai si l'expression ne dépend pas du temps (elle peut être remplacée par sa valeur). */
    public boolean constant() { return constant; }

    /** @param tempsAnimation secondes depuis le début de l'animation */
    public double evaluer(double tempsAnimation) {
        double v = racine.val(tempsAnimation);
        return Double.isFinite(v) ? v : 0;
    }

    /** Analyse une expression ; IllegalArgumentException si elle n'est pas comprise. */
    public static Molang compiler(String expression) {
        Analyseur a = new Analyseur(expression);
        Noeud n = a.expression();
        a.espaces();
        if (a.i < a.s.length() && a.s.charAt(a.i) == ';') a.i++;
        a.espaces();
        if (a.i != a.s.length()) throw new IllegalArgumentException("caractère inattendu « " + a.s.charAt(a.i) + " »");
        return new Molang(expression, n, !a.temps);
    }

    private static final class Analyseur {
        final String s;
        int i;
        boolean temps;

        Analyseur(String s) { this.s = s.toLowerCase(Locale.ROOT).trim(); }

        void espaces() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }

        boolean lit(String t) {
            espaces();
            if (s.startsWith(t, i)) { i += t.length(); return true; }
            return false;
        }

        Noeud expression() {
            Noeud c = ou();
            if (lit("??")) { expression(); return c; } // a ?? b : a est toujours défini ici
            if (lit("?")) {
                Noeud a = expression();
                if (!lit(":")) throw new IllegalArgumentException("« : » attendu");
                Noeud b = expression();
                return t -> c.val(t) != 0 ? a.val(t) : b.val(t);
            }
            return c;
        }

        Noeud ou() {
            Noeud g = et();
            while (lit("||")) { Noeud a = g, b = et(); g = t -> a.val(t) != 0 || b.val(t) != 0 ? 1 : 0; }
            return g;
        }

        Noeud et() {
            Noeud g = comparaison();
            while (lit("&&")) { Noeud a = g, b = comparaison(); g = t -> a.val(t) != 0 && b.val(t) != 0 ? 1 : 0; }
            return g;
        }

        Noeud comparaison() {
            Noeud g = somme();
            while (true) {
                Noeud a = g;
                if (lit("<=")) { Noeud b = somme(); g = t -> a.val(t) <= b.val(t) ? 1 : 0; }
                else if (lit(">=")) { Noeud b = somme(); g = t -> a.val(t) >= b.val(t) ? 1 : 0; }
                else if (lit("==")) { Noeud b = somme(); g = t -> a.val(t) == b.val(t) ? 1 : 0; }
                else if (lit("!=")) { Noeud b = somme(); g = t -> a.val(t) != b.val(t) ? 1 : 0; }
                else if (lit("<")) { Noeud b = somme(); g = t -> a.val(t) < b.val(t) ? 1 : 0; }
                else if (lit(">")) { Noeud b = somme(); g = t -> a.val(t) > b.val(t) ? 1 : 0; }
                else return g;
            }
        }

        Noeud somme() {
            Noeud g = produit();
            while (true) {
                Noeud a = g;
                if (lit("+")) { Noeud b = produit(); g = t -> a.val(t) + b.val(t); }
                else if (lit("-")) { Noeud b = produit(); g = t -> a.val(t) - b.val(t); }
                else return g;
            }
        }

        Noeud produit() {
            Noeud g = unaire();
            while (true) {
                Noeud a = g;
                if (lit("*")) { Noeud b = unaire(); g = t -> a.val(t) * b.val(t); }
                else if (lit("/")) { Noeud b = unaire(); g = t -> { double d = b.val(t); return d == 0 ? 0 : a.val(t) / d; }; }
                else return g;
            }
        }

        Noeud unaire() {
            if (lit("-")) { Noeud a = unaire(); return t -> -a.val(t); }
            if (lit("+")) return unaire();
            if (lit("!")) { Noeud a = unaire(); return t -> a.val(t) == 0 ? 1 : 0; }
            return terme();
        }

        Noeud terme() {
            espaces();
            if (i >= s.length()) throw new IllegalArgumentException("expression incomplète");
            char c = s.charAt(i);
            if (lit("(")) {
                Noeud n = expression();
                if (!lit(")")) throw new IllegalArgumentException("« ) » attendue");
                return n;
            }
            if (Character.isDigit(c) || c == '.') {
                int d = i;
                while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) i++;
                double v = Double.parseDouble(s.substring(d, i));
                if (i < s.length() && s.charAt(i) == 'f') i++; // 1.5f
                return t -> v;
            }
            if (Character.isLetter(c) || c == '_') {
                int d = i;
                while (i < s.length() && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_' || s.charAt(i) == '.')) i++;
                String nom = s.substring(d, i);
                List<Noeud> args = new ArrayList<>();
                if (lit("(")) {
                    if (!lit(")")) {
                        do { args.add(expression()); } while (lit(","));
                        if (!lit(")")) throw new IllegalArgumentException("« ) » attendue après " + nom);
                    }
                }
                return fonction(nom, args);
            }
            throw new IllegalArgumentException("caractère inattendu « " + c + " »");
        }

        Noeud arg(List<Noeud> a, int k, String nom) {
            if (k >= a.size()) throw new IllegalArgumentException(nom + " : argument manquant");
            return a.get(k);
        }

        Noeud fonction(String nom, List<Noeud> a) {
            switch (nom) {
                case "query.anim_time", "q.anim_time", "query.life_time", "q.life_time" -> { temps = true; return t -> t; }
                case "math.pi" -> { return t -> Math.PI; }
                case "true" -> { return t -> 1; }
                case "false" -> { return t -> 0; }
                default -> { }
            }
            if (!nom.startsWith("math.")) {
                // variable.*, query.* inconnues : 0 (comme Blockbench sans valeur de prévisualisation)
                if (nom.startsWith("variable.") || nom.startsWith("v.") || nom.startsWith("query.") || nom.startsWith("q.")
                    || nom.startsWith("temp.") || nom.startsWith("t.") || nom.startsWith("context.") || nom.startsWith("c.")) return t -> 0;
                throw new IllegalArgumentException("nom inconnu « " + nom + " »");
            }
            String f = nom.substring(5);
            switch (f) {
                case "sin" -> { Noeud x = arg(a, 0, nom); return t -> Math.sin(Math.toRadians(x.val(t))); }
                case "cos" -> { Noeud x = arg(a, 0, nom); return t -> Math.cos(Math.toRadians(x.val(t))); }
                case "asin" -> { Noeud x = arg(a, 0, nom); return t -> Math.toDegrees(Math.asin(x.val(t))); }
                case "acos" -> { Noeud x = arg(a, 0, nom); return t -> Math.toDegrees(Math.acos(x.val(t))); }
                case "atan" -> { Noeud x = arg(a, 0, nom); return t -> Math.toDegrees(Math.atan(x.val(t))); }
                case "atan2" -> { Noeud y = arg(a, 0, nom), x = arg(a, 1, nom); return t -> Math.toDegrees(Math.atan2(y.val(t), x.val(t))); }
                case "abs" -> { Noeud x = arg(a, 0, nom); return t -> Math.abs(x.val(t)); }
                case "floor" -> { Noeud x = arg(a, 0, nom); return t -> Math.floor(x.val(t)); }
                case "ceil" -> { Noeud x = arg(a, 0, nom); return t -> Math.ceil(x.val(t)); }
                case "round" -> { Noeud x = arg(a, 0, nom); return t -> Math.round(x.val(t)); }
                case "trunc" -> { Noeud x = arg(a, 0, nom); return t -> (double) (long) x.val(t); }
                case "sqrt" -> { Noeud x = arg(a, 0, nom); return t -> Math.sqrt(Math.max(0, x.val(t))); }
                case "exp" -> { Noeud x = arg(a, 0, nom); return t -> Math.exp(x.val(t)); }
                case "ln" -> { Noeud x = arg(a, 0, nom); return t -> Math.log(x.val(t)); }
                case "pow" -> { Noeud x = arg(a, 0, nom), y = arg(a, 1, nom); return t -> Math.pow(x.val(t), y.val(t)); }
                case "mod" -> { Noeud x = arg(a, 0, nom), y = arg(a, 1, nom); return t -> { double d = y.val(t); return d == 0 ? 0 : x.val(t) % d; }; }
                case "min" -> { Noeud x = arg(a, 0, nom), y = arg(a, 1, nom); return t -> Math.min(x.val(t), y.val(t)); }
                case "max" -> { Noeud x = arg(a, 0, nom), y = arg(a, 1, nom); return t -> Math.max(x.val(t), y.val(t)); }
                case "clamp" -> { Noeud x = arg(a, 0, nom), lo = arg(a, 1, nom), hi = arg(a, 2, nom); return t -> Math.max(lo.val(t), Math.min(hi.val(t), x.val(t))); }
                case "lerp" -> { Noeud x = arg(a, 0, nom), y = arg(a, 1, nom), k = arg(a, 2, nom); return t -> x.val(t) + (y.val(t) - x.val(t)) * k.val(t); }
                case "lerprotate" -> { Noeud x = arg(a, 0, nom), y = arg(a, 1, nom), k = arg(a, 2, nom);
                    return t -> { double d = ((y.val(t) - x.val(t)) % 360 + 540) % 360 - 180; return x.val(t) + d * k.val(t); }; }
                case "sign" -> { Noeud x = arg(a, 0, nom); return t -> Math.signum(x.val(t)); }
                case "random" -> { Noeud x = arg(a, 0, nom), y = arg(a, 1, nom); temps = true; return t -> x.val(t) + Math.random() * (y.val(t) - x.val(t)); }
                default -> throw new IllegalArgumentException("fonction inconnue « " + nom + " »");
            }
        }
    }
}
