package me.abhiram.puddlesplus.render.impl;

import me.abhiram.puddlesplus.PuddlesPlus;
import me.abhiram.puddlesplus.model.Puddle;
import me.abhiram.puddlesplus.render.PuddleRenderer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BukkitPuddleRenderer implements PuddleRenderer {
    private final PuddlesPlus plugin;

    // Active puddles (positions only)
    private final Map<UUID, Set<Location>> active = new ConcurrentHashMap<>();

    // Original block states (for restore)
    private final Map<UUID, Map<Location, BlockData>> original = new ConcurrentHashMap<>();

    public BukkitPuddleRenderer(PuddlesPlus plugin) {
        this.plugin = plugin;
    }

    @Override
    public void render(Player player, Set<Puddle> puddles) {
        final World playerWorld = player.getWorld();
        final UUID uuid = player.getUniqueId();

        final Set<Location> newLocations = new HashSet<>();
        final Map<Location, BlockData> originalSnapshot = original.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());

        for (Puddle puddle : puddles) {
            Location location = new Location(playerWorld, puddle.x(), puddle.y(), puddle.z());
            newLocations.add(location);
            originalSnapshot.computeIfAbsent(location, p -> player.getWorld().getBlockAt(location).getBlockData());
        }

        Set<Location> previous = active.getOrDefault(uuid, Collections.emptySet());

        // Compute diff (only send changes)
        Set<Location> toAdd = new HashSet<>(newLocations);
        toAdd.removeAll(previous);

        Set<Location> toRemove = new HashSet<>(previous);
        toRemove.removeAll(newLocations);

        active.put(uuid, newLocations);

        player.getScheduler().run(plugin, (task) -> {
            sendWater(player, toAdd);
            restore(player, uuid, toRemove);
        }, null);
    }

    private void sendWater(Player player, Set<Location> locations) {
        if (locations.isEmpty()) return;

        Levelled water = (Levelled) Bukkit.createBlockData(Material.WATER);
        water.setLevel(7);

        for (Location location : locations) {
            player.sendBlockChange(location, water);
        }
    }

    private void restore(Player player, UUID uuid, Set<Location> locations) {
        if (locations.isEmpty()) return;

        Map<Location, BlockData> map = original.get(uuid);
        if (map == null) return;

        for (Location location : locations) {
            BlockData data = map.remove(location);
            if (data == null) continue;
            player.sendBlockChange(location, data);
        }
    }

    @Override
    public void clear(Player player) {
        UUID uuid = player.getUniqueId();

        Set<Location> positions = active.remove(uuid);
        Map<Location, BlockData> map = original.remove(uuid);

        if (positions == null || map == null) return;

        player.getScheduler().run(plugin, task -> {
            for (Location location : positions) {
                BlockData data = map.get(location);
                if (data == null) continue;
                player.sendBlockChange(location, data);
            }
        }, null);
    }
}
