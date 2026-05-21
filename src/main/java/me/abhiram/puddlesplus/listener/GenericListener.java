package me.abhiram.puddlesplus.listener;

import me.abhiram.puddlesplus.PuddlesPlus;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class GenericListener implements Listener {
    private final PuddlesPlus plugin;

    public GenericListener(PuddlesPlus plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event) {
        this.plugin.getPuddleManager().clearPlayer(event.getPlayer(), false);
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        this.plugin.getPuddleManager().clearPlayer(event.getPlayer(), false);
    }
}
