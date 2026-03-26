package me.abhiram.puddlesplus.render;

import me.abhiram.puddlesplus.model.Puddle;
import org.bukkit.entity.Player;

import java.util.Set;

public interface PuddleRenderer {
    void render(Player player, Set<Puddle> puddles);

    void clear(Player player);
}
