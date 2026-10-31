package fr.vorace.glyphes;

import fr.vorace.Vorace;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/**
 * PlaceholderAPI : %vorace_glyphe_coeur% (ou %vorace_coeur%), %vorace_decalage_-8%.
 * Chargée seulement si PlaceholderAPI est présent.
 */
public final class ExtensionPlaceholder extends PlaceholderExpansion {

    private final Vorace plugin;

    public ExtensionPlaceholder(Vorace plugin) {
        this.plugin = plugin;
    }

    @Override public String getIdentifier() { return "vorace"; }
    @Override public String getAuthor() { return "ORA-KUN"; }
    @Override public String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer joueur, String params) {
        if (params == null) return null;
        String p = params.trim();
        if (p.startsWith("decalage_") || p.startsWith("shift_")) {
            try {
                return GenerateurGlyphes.decalage(Integer.parseInt(p.substring(p.indexOf('_') + 1)));
            } catch (NumberFormatException e) {
                return "";
            }
        }
        if (p.startsWith("glyphe_")) p = p.substring(7);
        Glyphes.Glyphe g = plugin.glyphes().glyphe(p);
        return g == null ? null : g.texte();
    }
}
