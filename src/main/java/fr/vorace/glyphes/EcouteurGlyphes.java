package fr.vorace.glyphes;

import fr.vorace.Vorace;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Emojis dans le chat et sur les panneaux, suggestions du chat. */
public final class EcouteurGlyphes implements Listener {

    private final Vorace plugin;

    public EcouteurGlyphes(Vorace plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void chat(AsyncChatEvent e) {
        if (!plugin.getConfig().getBoolean("glyphes.chat", true)) return;
        e.message(plugin.glyphes().remplacer(e.getPlayer(), e.message()));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void panneau(SignChangeEvent e) {
        if (!plugin.getConfig().getBoolean("glyphes.panneaux", true)) return;
        for (int i = 0; i < 4; i++) {
            Component l = e.line(i);
            if (l != null) e.line(i, plugin.glyphes().remplacer(e.getPlayer(), l));
        }
    }

    @EventHandler
    public void arrivee(PlayerJoinEvent e) {
        plugin.glyphes().mettreAJourSuggestions(e.getPlayer());
    }

    @EventHandler
    public void depart(PlayerQuitEvent e) {
        plugin.glyphes().oublier(e.getPlayer());
    }
}
