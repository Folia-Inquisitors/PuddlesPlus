package me.abhiram.puddlesplus.render.impl;

import me.abhiram.puddlesplus.PuddlesPlus;
import me.abhiram.puddlesplus.model.Puddle;
import me.abhiram.puddlesplus.render.PuddleRenderer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BukkitPuddleRenderer implements PuddleRenderer {
    private final PuddlesPlus plugin;

    private final Map<UUID, Map<Location, Integer>> active = new ConcurrentHashMap<>();

    public BukkitPuddleRenderer(PuddlesPlus plugin) {
        this.plugin = plugin;
    }

    @Override
    public void render(Player player, Set<Puddle> puddles) {
        final World playerWorld = player.getWorld();
        final UUID uuid = player.getUniqueId();

        final Map<Location, Integer> newLocations = new HashMap<>();

        for (Puddle puddle : puddles) {
            if (!playerWorld.getUID().equals(puddle.worldId())) continue;

            Location location = new Location(playerWorld, puddle.x(), puddle.y(), puddle.z());
            newLocations.put(location, puddle.depth());
        }

        Map<Location, Integer> previous = active.getOrDefault(uuid, Collections.emptyMap());

        Map<Location, Integer> toAdd = new HashMap<>();
        for (Map.Entry<Location, Integer> entry : newLocations.entrySet()) {
            Integer previousDepth = previous.get(entry.getKey());

            if (!entry.getValue().equals(previousDepth)) {
                toAdd.put(entry.getKey(), entry.getValue());
            }
        }

        Set<Location> toRemove = new HashSet<>(previous.keySet());
        toRemove.removeAll(newLocations.keySet());

        if (newLocations.isEmpty()) {
            active.remove(uuid);
        } else {
            active.put(uuid, Collections.unmodifiableMap(new HashMap<>(newLocations)));
        }

        if (toAdd.isEmpty() && toRemove.isEmpty()) return;

        player.getScheduler().run(plugin, (task) -> {
            sendWater(player, toAdd);
            restore(player, toRemove);
        }, null);
    }

    private void sendWater(Player player, Map<Location, Integer> locations) {
        if (locations.isEmpty()) return;

        for (Map.Entry<Location, Integer> entry : locations.entrySet()) {
            Location location = entry.getKey();

            World world = location.getWorld();

            if (world == null || !world.equals(player.getWorld())) continue;

            Levelled water = (Levelled) Bukkit.createBlockData(Material.WATER);
            water.setLevel(toWaterLevel(entry.getValue()));

            player.sendBlockChange(location, water);
        }
    }

    private int toWaterLevel(int depth) {
        if (depth >= 3) return 5;
        if (depth == 2) return 6;

        return 7;
    }

    private void restore(Player player, Set<Location> locations) {
        if (locations.isEmpty()) return;

        for (Location location : locations) {
            World world = location.getWorld();

            if (world == null || !world.equals(player.getWorld())) continue;

            player.sendBlockChange(location, world.getBlockAt(location).getBlockData());
        }
    }

    @Override
    public void clear(Player player, boolean restoreBlocks) {
        UUID uuid = player.getUniqueId();

        Map<Location, Integer> positions = active.remove(uuid);

        if (positions == null || positions.isEmpty() || !restoreBlocks) return;

        player.getScheduler().run(plugin, task -> {
            restore(player, positions.keySet());
        }, null);
    }
}
