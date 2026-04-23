package me.abhiram.puddlesplus.manager;

import me.abhiram.puddlesplus.PuddlesPlus;
import me.abhiram.puddlesplus.model.Puddle;
import me.abhiram.puddlesplus.render.PuddleRenderer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class PuddleManager {


    private final PuddleRenderer renderer;
    private final Random random = new Random();

    private final Map<UUID, Set<Puddle>> puddleStore = new ConcurrentHashMap<>();

    private final PuddlesPlus plugin;

    private int RADIUS = 10;
    private int MAX_PUDDLES = 20;
    private int CLUSTER_SIZE = 6;

    public PuddleManager(final PuddleRenderer renderer, PuddlesPlus puddlesPlus) {
        this.renderer = renderer;
        this.plugin = puddlesPlus;

        this.RADIUS = this.plugin.getPluginConfig().getConfig().getInt("radius");
        this.MAX_PUDDLES = this.plugin.getPluginConfig().getConfig().getInt("max-puddles");
        this.CLUSTER_SIZE = this.plugin.getPluginConfig().getConfig().getInt("cluster-size");
    }

    public void run(Player player) {
        if (!shouldRender(player)) {
            clearPlayer(player);
            return;
        }

        UUID uuid = player.getUniqueId();
        Set<Puddle> puddles = puddleStore.computeIfAbsent(uuid, id -> new HashSet<>());

        puddles.removeIf(p -> {
            p.decay();
            return p.isDead();
        });

        if (puddles.size() < MAX_PUDDLES && random.nextInt(5) == 0) {
            puddles.addAll(generateCluster(player, puddles));
        }

        trimFarPuddles(player, puddles);

        renderer.render(player, puddles);
    }

    private boolean shouldRender(final Player player) {

        World world = player.getWorld();

        if (!world.hasStorm()) {
            return false;
        }

        Location loc = player.getLocation();

        if (world.getBlockAt(loc).getLightFromSky() <= 0) {
            return false;
        }

        return true;
    }

    private Set<Puddle> generateCluster(
            final Player player,
            final Set<Puddle> existing) {

        Set<Puddle> cluster = new HashSet<>();

        Location base = player.getLocation();
        World world = base.getWorld();

        int centerX = base.getBlockX() + random.nextInt(RADIUS * 2) - RADIUS;
        int centerZ = base.getBlockZ() + random.nextInt(RADIUS * 2) - RADIUS;

        for (int i = 0; i < CLUSTER_SIZE; i++) {

            int x = centerX + random.nextInt(3) - 1;
            int z = centerZ + random.nextInt(3) - 1;

            boolean exists = existing.stream()
                    .anyMatch(p -> p.x() == x && p.z() == z);

            if (exists) continue;

            int groundY = world.getHighestBlockYAt(x, z);

            if (!world.getBlockAt(x, groundY + 1, z).isEmpty()) continue;
            if (!isValidGround(world, x, groundY, z)) continue;
            if (!isFlatEnough(world, x, groundY, z)) continue;

            int life = 20 + random.nextInt(20);

            cluster.add(new Puddle(x, groundY + 1, z, life));
        }

        return cluster;
    }

    private void trimFarPuddles(Player player, Set<Puddle> puddles) {

        int px = player.getLocation().getBlockX();
        int pz = player.getLocation().getBlockZ();

        puddles.removeIf(p -> {
            int dx = p.x() - px;
            int dz = p.z() - pz;
            return (dx * dx + dz * dz) > (RADIUS * RADIUS * 4);
        });
    }

    private boolean isValidGround(
            final World world,
            final int x,
            final int y,
            final int z) {

        Material type = world.getBlockAt(x, y, z).getType();

        return switch (type) {
            case GRASS_BLOCK, DIRT, COARSE_DIRT, PODZOL,
                    STONE, ANDESITE, DIORITE, GRANITE,
                    SAND, RED_SAND, CLAY, MUD -> true;
            default -> false;
        };
    }

    private boolean isFlatEnough(
            final World world,
            final int x,
            final int y,
            final int z) {

        int[] dx = {1, -1, 0, 0};
        int[] dz = {0, 0, 1, -1};

        for (int i = 0; i < 4; i++) {
            int ny = world.getHighestBlockYAt(x + dx[i], z + dz[i]);
            if (Math.abs(ny - y) > 1) {
                return false;
            }
        }

        return true;
    }

    public void clearPlayer(final Player player) {
        UUID uuid = player.getUniqueId();
        puddleStore.remove(uuid);
        renderer.clear(player);
    }
}