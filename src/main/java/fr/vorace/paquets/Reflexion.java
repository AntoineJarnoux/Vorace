package fr.vorace.paquets;

import io.netty.channel.Channel;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/** Lecture et écriture des champs des objets du jeu, sans dépendre d'une version précise. */
final class Reflexion {

    private static final Map<Class<?>, Map<String, Field>> CHAMPS = new ConcurrentHashMap<>();

    private Reflexion() {}

    private static final Map<Class<?>, String> NOMS = new ConcurrentHashMap<>();

    /** Nom d'un paquet : « ClientboundSetTimePacket », ou « ServerboundMovePlayerPacket.Pos » pour une variante. */
    static String nom(Class<?> c) {
        return NOMS.computeIfAbsent(c, k -> k.getEnclosingClass() == null ? k.getSimpleName() : nom(k.getEnclosingClass()) + "." + k.getSimpleName());
    }

    /** Champs d'instance de la classe et de ses parents, par nom (le plus proche gagne). */
    static Map<String, Field> champsDe(Class<?> c) {
        return CHAMPS.computeIfAbsent(c, k -> {
            Map<String, Field> m = new LinkedHashMap<>();
            List<Class<?>> chaine = new ArrayList<>();
            for (Class<?> x = k; x != null && x != Object.class; x = x.getSuperclass()) chaine.add(0, x);
            for (Class<?> x : chaine) {
                for (Field f : x.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    try { f.setAccessible(true); } catch (RuntimeException e) { continue; }
                    m.put(f.getName(), f);
                }
            }
            return m;
        });
    }

    static Field champ(Object o, String nom) {
        Field f = champsDe(o.getClass()).get(nom);
        if (f == null) throw new IllegalArgumentException(nom(o.getClass()) + " n'a pas de champ « " + nom + " » (champs : " + champsDe(o.getClass()).keySet() + ")");
        return f;
    }

    static Object lire(Object o, String nom) {
        try {
            return champ(o, nom).get(o);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    static Map<String, Object> champs(Object o) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (Map.Entry<String, Field> e : champsDe(o.getClass()).entrySet()) {
            try {
                m.put(e.getKey(), e.getValue().get(o));
            } catch (IllegalAccessException ex) {
                m.put(e.getKey(), "?");
            }
        }
        return m;
    }

    /** Objet avec ce champ changé : modifié sur place, ou recréé pour un record. */
    static Object avec(Object o, String nom, Object valeur) {
        Class<?> c = o.getClass();
        if (c.isRecord()) {
            RecordComponent[] comp = c.getRecordComponents();
            Class<?>[] types = new Class<?>[comp.length];
            Object[] valeurs = new Object[comp.length];
            boolean trouve = false;
            for (int i = 0; i < comp.length; i++) {
                types[i] = comp[i].getType();
                if (comp[i].getName().equals(nom)) {
                    valeurs[i] = valeur;
                    trouve = true;
                } else {
                    valeurs[i] = lire(o, comp[i].getName());
                }
            }
            if (!trouve) throw new IllegalArgumentException(nom(c) + " n'a pas de champ « " + nom + " »");
            try {
                Constructor<?> k = c.getDeclaredConstructor(types);
                k.setAccessible(true);
                return k.newInstance(valeurs);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Impossible de recréer " + nom(c) + " : " + e, e);
            }
        }
        try {
            champ(o, nom).set(o, valeur);
            return o;
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Texte simple d'un texte du jeu (Component de Minecraft ou d'Adventure), ou toString. */
    static String texte(Object t) {
        if (t == null) return null;
        if (t instanceof net.kyori.adventure.text.Component a) {
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(a);
        }
        try {
            Method m = t.getClass().getMethod("getString");
            return String.valueOf(m.invoke(t));
        } catch (ReflectiveOperationException e) {
            return t.toString();
        }
    }

    /** Premier champ dont le type convient. */
    static Object champParType(Object o, Predicate<Class<?>> type) throws IllegalAccessException {
        for (Field f : champsDe(o.getClass()).values()) if (type.test(f.getType())) return f.get(o);
        throw new IllegalArgumentException("aucun champ adapté dans " + o.getClass().getName());
    }

    /** Connexion réseau d'un joueur (CraftPlayer → ServerPlayer.connection → Connection.channel). */
    static Channel canal(Player p) throws ReflectiveOperationException {
        Object handle = p.getClass().getMethod("getHandle").invoke(p);
        Object ecouteur = lire(handle, "connection");
        Object connexion = champParType(ecouteur, t -> t.getSimpleName().equals("Connection"));
        return (Channel) champParType(connexion, Channel.class::isAssignableFrom);
    }
}
