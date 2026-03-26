package me.abhiram.puddlesplus.listener;

import me.abhiram.puddlesplus.PuddlesPlus;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class GenericListener implements Listener {
    private final PuddlesPlus plugin;

    public GenericListener(PuddlesPlus plugin) {
        this.plugin = plugin;
    }



    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event){
        // Clear all the cached puddles for the player
        this.plugin.getPuddleManager().clearPlayer(event.getPlayer());
    }
}
