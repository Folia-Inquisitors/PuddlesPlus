package me.abhiram.puddlesplus;

import me.abhiram.puddlesplus.file.PluginConfig;
import me.abhiram.puddlesplus.listener.GenericListener;
import me.abhiram.puddlesplus.manager.PuddleManager;
import me.abhiram.puddlesplus.render.PuddleRenderer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class PuddlesPlus extends JavaPlugin {

    private PuddleManager puddleManager;

    private PluginConfig pluginConfig;


    @Override
    public void onLoad() {

        this.pluginConfig = new PluginConfig(this);

        this.puddleManager = new PuddleManager(PuddleRenderer.create(this), this);
    }



    @Override
    public void onEnable() {
        // Plugin startup logic

        int taskRate = this.pluginConfig.getConfig().getInt("puddle-task-rate");

        getServer().getGlobalRegionScheduler().runAtFixedRate(
                this,
                task -> {
                    for (Player player : Bukkit.getOnlinePlayers()) {

                        Location loc = player.getLocation();

                        getServer().getRegionScheduler().run(
                                this,
                                loc,
                                regionTask -> puddleManager.run(player)
                        );
                    }
                },
                taskRate,
                taskRate
        );

        getLogger().info("Puddle Scheduler started!");

        Bukkit.getPluginManager().registerEvents(new GenericListener(this), this);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public PuddleManager getPuddleManager() {
        return this.puddleManager;
    }

    public PluginConfig getPluginConfig() {
        return this.pluginConfig;
    }
}
