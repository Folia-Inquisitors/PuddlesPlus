package me.abhiram.puddlesplus;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import me.abhiram.puddlesplus.file.PluginConfig;
import me.abhiram.puddlesplus.listener.GenericListener;
import me.abhiram.puddlesplus.manager.PuddleManager;
import me.abhiram.puddlesplus.render.PuddleRenderer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class PuddlesPlus extends JavaPlugin {

    private PuddleManager puddleManager;

    private PluginConfig pluginConfig;

    private ScheduledTask puddleTask;


    @Override
    public void onLoad() {

        this.pluginConfig = new PluginConfig(this);

        this.puddleManager = new PuddleManager(PuddleRenderer.create(this), this);
    }



    @Override
    public void onEnable() {

        int taskRate = getPositiveConfigInt("puddle-task-rate", 20);

        this.puddleTask = getServer().getGlobalRegionScheduler().runAtFixedRate(
                this,
                task -> {
                    puddleManager.nextCycle();

                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.getScheduler().run(
                                this,
                                playerTask -> puddleManager.run(player),
                                () -> puddleManager.clearPlayer(player, false)
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
        if (this.puddleTask != null) {
            this.puddleTask.cancel();
            this.puddleTask = null;
        }

        if (this.puddleManager != null) {
            this.puddleManager.clearAll(Bukkit.getOnlinePlayers(), true);
        }
    }

    public PuddleManager getPuddleManager() {
        return this.puddleManager;
    }

    public PluginConfig getPluginConfig() {
        return this.pluginConfig;
    }

    private int getPositiveConfigInt(final String path, final int fallback) {
        int value = this.pluginConfig.getConfig().getInt(path, fallback);

        if (value < 1) {
            getLogger().warning(path + " must be at least 1; using " + fallback + ".");
            return fallback;
        }

        return value;
    }
}
