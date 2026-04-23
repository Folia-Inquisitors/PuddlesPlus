package me.abhiram.puddlesplus.render;

import me.abhiram.puddlesplus.PuddlesPlus;
import me.abhiram.puddlesplus.model.Puddle;
import me.abhiram.puddlesplus.render.impl.BukkitPuddleRenderer;
import org.bukkit.entity.Player;

import java.util.Set;

public interface PuddleRenderer {
    static PuddleRenderer create(PuddlesPlus plugin) {
        return new BukkitPuddleRenderer(plugin);
    }

    void render(Player player, Set<Puddle> puddles);

    void clear(Player player);
}
