package me.abhiram.puddlesplus.manager;

import me.abhiram.puddlesplus.PuddlesPlus;
import me.abhiram.puddlesplus.model.Puddle;
import me.abhiram.puddlesplus.render.PuddleRenderer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

public final class PuddleManager {
    private static final int[][] CARDINAL_DIRECTIONS = {
            {1, 0},
            {-1, 0},
            {0, 1},
            {0, -1}
    };
    private static final int[][] GROWTH_DIRECTIONS = {
            {1, 0},
            {-1, 0},
            {0, 1},
            {0, -1},
            {1, 1},
            {1, -1},
            {-1, 1},
            {-1, -1}
    };

    private final PuddleRenderer renderer;
    private final Map<UUID, Set<Puddle>> puddlesByWorld = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerWorlds = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> dryDelayCyclesByWorld = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastDryDelayUpdateCycle = new ConcurrentHashMap<>();
    private final AtomicLong cycle = new AtomicLong();

    private final PuddlesPlus plugin;

    private final int radius;
    private final int maxPuddlesPerWorld;
    private final int seedAttemptsPerCycle;
    private final int maxExpansionsPerCycle;
    private final int maxPuddleSize;
    private final int nearbyPuddlesToMerge;
    private final int mergeSearchRadius;
    private final int maxDepth;
    private final int dryDelayCycles;
    private final double seedChance;
    private final double expandChance;
    private final double singlePuddleExpandChance;
    private final double thunderstormMultiplier;
    private final double shrinkChance;

    public PuddleManager(final PuddleRenderer renderer, PuddlesPlus puddlesPlus) {
        this.renderer = renderer;
        this.plugin = puddlesPlus;

        validatePuddleMode();

        int taskRate = readPositiveConfigInt("puddle-task-rate", 20);
        double dryDelaySeconds = readNonNegativeConfigDouble("drying.start-after-rain-seconds", 5.0);

        this.radius = readPositiveConfigInt("radius", 20);
        this.maxPuddlesPerWorld = readNonNegativeConfigInt("max-puddles-per-world", "max-puddles", 300);
        this.seedAttemptsPerCycle = readPositiveConfigInt("growth.seed-attempts-per-cycle", 6);
        this.maxExpansionsPerCycle = readPositiveConfigInt("growth.max-expansions-per-cycle", "cluster-size", 2);
        this.maxPuddleSize = readPositiveConfigInt("growth.max-puddle-size", 5);
        this.nearbyPuddlesToMerge = readPositiveConfigInt("growth.nearby-puddles-to-merge", 3);
        this.mergeSearchRadius = readPositiveConfigInt("growth.merge-search-radius", 4);
        this.maxDepth = readPositiveConfigInt("growth.max-depth", 3);
        this.dryDelayCycles = (int) Math.ceil(dryDelaySeconds * 20.0 / taskRate);
        this.seedChance = readChance("growth.seed-chance", 0.16);
        this.expandChance = readChance("growth.expand-chance", 0.08);
        this.singlePuddleExpandChance = readChance("growth.single-puddle-expand-chance", 0.025);
        this.thunderstormMultiplier = readMinimumConfigDouble("growth.thunderstorm-multiplier", 2.0, 1.0);
        this.shrinkChance = readChance("drying.shrink-chance", 0.18);
    }

    public void nextCycle() {
        cycle.incrementAndGet();
    }

    public void run(Player player) {
        UUID playerId = player.getUniqueId();
        World world = player.getWorld();
        UUID worldId = world.getUID();
        UUID previousWorldId = playerWorlds.get(playerId);

        if (previousWorldId != null && !previousWorldId.equals(worldId)) {
            renderer.clear(player, false);
        }

        playerWorlds.put(playerId, worldId);

        Set<Puddle> puddles = puddlesByWorld.computeIfAbsent(worldId, id -> ConcurrentHashMap.newKeySet());

        if (world.hasStorm()) {
            dryDelayCyclesByWorld.remove(worldId);
            lastDryDelayUpdateCycle.remove(worldId);
            growNearPlayer(player, puddles, rainMultiplier(world));
        } else {
            int dryCycles = incrementDryDelayOncePerCycle(worldId);

            if (dryCycles >= dryDelayCycles) {
                dryNearPlayer(player, puddles);
            }
        }

        if (puddles.isEmpty()) {
            puddlesByWorld.remove(worldId, puddles);
            dryDelayCyclesByWorld.remove(worldId);
            lastDryDelayUpdateCycle.remove(worldId);
        }

        renderer.render(player, visiblePuddles(player, puddles));
    }

    private void growNearPlayer(final Player player, final Set<Puddle> puddles, final double rainMultiplier) {
        int available = maxPuddlesPerWorld - puddles.size();
        if (available <= 0) return;

        ThreadLocalRandom random = ThreadLocalRandom.current();
        int growthBudget = Math.min(available, Math.max(1, (int) Math.round(maxExpansionsPerCycle * rainMultiplier)));

        for (int i = 0; i < seedAttemptsPerCycle && available > 0; i++) {
            if (roll(seedChance * rainMultiplier) && addStarterPuddle(player, puddles, random)) {
                available--;
            }
        }

        growthBudget = Math.min(growthBudget, available);

        if (growthBudget > 0 && !puddles.isEmpty()) {
            expandExistingPuddles(player, puddles, growthBudget, rainMultiplier, random);
        }
    }

    private boolean addStarterPuddle(final Player player, final Set<Puddle> puddles, final ThreadLocalRandom random) {
        Location center = player.getLocation();
        World world = center.getWorld();

        for (int attempt = 0; attempt < maxExpansionsPerCycle * 3; attempt++) {
            int x = center.getBlockX() + random.nextInt(-radius, radius + 1);
            int z = center.getBlockZ() + random.nextInt(-radius, radius + 1);
            Puddle puddle = createPuddleIfValid(world, world.getUID(), x, z, null, random);

            if (puddle == null || containsColumn(puddles, puddle)) continue;

            return puddles.add(puddle);
        }

        return false;
    }

    private void expandExistingPuddles(
            final Player player,
            final Set<Puddle> puddles,
            final int growthBudget,
            final double rainMultiplier,
            final ThreadLocalRandom random) {

        World world = player.getWorld();
        List<Puddle> edges = new ArrayList<>();

        for (Puddle puddle : puddles) {
            if (!world.getUID().equals(puddle.worldId())) continue;
            if (!isNearPlayer(player, puddle, radius * 2)) continue;
            if (!isEdge(puddles, puddle)) continue;

            int patchSize = connectedPuddleSize(puddles, puddle, maxPuddleSize);
            if (patchSize >= maxPuddleSize) continue;
            if (patchSize == 1 && nearbyPuddleCount(puddles, puddle, mergeSearchRadius) < nearbyPuddlesToMerge) continue;

            edges.add(puddle);
        }

        Collections.shuffle(edges);

        int added = 0;
        for (Puddle edge : edges) {
            if (added >= growthBudget) return;

            edge.soak(maxDepth);

            List<int[]> directions = shuffledDirections();
            for (int[] direction : directions) {
                if (added >= growthBudget) return;

                int x = edge.x() + direction[0];
                int z = edge.z() + direction[1];
                Material ground = groundMaterial(world, x, z);
                int patchSize = connectedPuddleSize(puddles, edge, maxPuddleSize);
                double baseChance = patchSize == 1 ? singlePuddleExpandChance : expandChance;
                double chance = baseChance * rainMultiplier * growthMultiplier(ground);

                if (!roll(chance)) continue;

                Puddle puddle = createPuddleIfValid(world, edge.worldId(), x, z, edge, random);

                if (puddle == null || containsColumn(puddles, puddle)) continue;
                if (patchSize >= maxPuddleSize) continue;

                puddles.add(puddle);
                added++;
                break;
            }
        }
    }

    private void dryNearPlayer(final Player player, final Set<Puddle> puddles) {
        List<Puddle> candidates = new ArrayList<>();

        for (Puddle puddle : puddles) {
            if (!player.getWorld().getUID().equals(puddle.worldId())) continue;
            if (!isNearPlayer(player, puddle, radius * 2)) continue;

            candidates.add(puddle);
        }

        Collections.shuffle(candidates);

        for (Puddle puddle : candidates) {
            Material ground = player.getWorld().getBlockAt(puddle.x(), puddle.y() - 1, puddle.z()).getType();
            double chance = shrinkChance * dryingMultiplier(ground);

            if (isEdge(puddles, puddle)) {
                chance *= 1.5;
            } else {
                chance *= 0.45;
            }

            if (!roll(chance)) continue;

            puddle.dry();

            if (puddle.isDry()) {
                puddles.remove(puddle);
            }
        }
    }

    private Puddle createPuddleIfValid(
            final World world,
            final UUID worldId,
            final int x,
            final int z,
            final Puddle parent,
            final ThreadLocalRandom random) {

        int groundY = world.getHighestBlockYAt(x, z);
        int waterY = groundY + 1;

        if (parent != null && Math.abs(groundY - (parent.y() - 1)) > 1) return null;
        if (!world.getBlockAt(x, waterY, z).isEmpty()) return null;
        if (world.getBlockAt(x, waterY, z).getLightFromSky() <= 0) return null;
        if (!isValidGround(world, x, groundY, z)) return null;
        if (!isFlatEnough(world, x, groundY, z)) return null;

        int depth = random.nextDouble() < 0.15 ? 2 : 1;

        return new Puddle(worldId, x, waterY, z, depth);
    }

    private Set<Puddle> visiblePuddles(final Player player, final Set<Puddle> puddles) {
        Set<Puddle> visible = new HashSet<>();

        for (Puddle puddle : puddles) {
            if (!player.getWorld().getUID().equals(puddle.worldId())) continue;
            if (!isNearPlayer(player, puddle, radius * 2)) continue;

            visible.add(puddle);
        }

        return visible;
    }

    private boolean isNearPlayer(final Player player, final Puddle puddle, final int distance) {
        int dx = puddle.x() - player.getLocation().getBlockX();
        int dz = puddle.z() - player.getLocation().getBlockZ();
        long distanceSquared = (long) dx * dx + (long) dz * dz;
        long maxDistanceSquared = (long) distance * distance;

        return distanceSquared <= maxDistanceSquared;
    }

    private boolean isEdge(final Set<Puddle> puddles, final Puddle puddle) {
        for (int[] direction : CARDINAL_DIRECTIONS) {
            if (!containsColumn(puddles, puddle.worldId(), puddle.x() + direction[0], puddle.z() + direction[1])) {
                return true;
            }
        }

        return false;
    }

    private boolean containsColumn(final Set<Puddle> puddles, final Puddle candidate) {
        return containsColumn(puddles, candidate.worldId(), candidate.x(), candidate.z());
    }

    private boolean containsColumn(final Set<Puddle> puddles, final UUID worldId, final int x, final int z) {
        for (Puddle puddle : puddles) {
            if (puddle.worldId().equals(worldId) && puddle.x() == x && puddle.z() == z) {
                return true;
            }
        }

        return false;
    }

    private int connectedPuddleSize(final Set<Puddle> puddles, final Puddle start, final int stopAt) {
        Set<String> visited = new HashSet<>();
        ArrayDeque<Puddle> queue = new ArrayDeque<>();
        queue.add(start);

        while (!queue.isEmpty() && visited.size() < stopAt) {
            Puddle current = queue.removeFirst();
            String key = columnKey(current.x(), current.z());

            if (!visited.add(key)) continue;

            for (int[] direction : CARDINAL_DIRECTIONS) {
                Puddle neighbor = findColumn(
                        puddles,
                        current.worldId(),
                        current.x() + direction[0],
                        current.z() + direction[1]
                );

                if (neighbor != null && !visited.contains(columnKey(neighbor.x(), neighbor.z()))) {
                    queue.add(neighbor);
                }
            }
        }

        return visited.size();
    }

    private Puddle findColumn(final Set<Puddle> puddles, final UUID worldId, final int x, final int z) {
        for (Puddle puddle : puddles) {
            if (puddle.worldId().equals(worldId) && puddle.x() == x && puddle.z() == z) {
                return puddle;
            }
        }

        return null;
    }

    private String columnKey(final int x, final int z) {
        return x + ":" + z;
    }

    private List<int[]> shuffledDirections() {
        List<int[]> directions = new ArrayList<>(GROWTH_DIRECTIONS.length);
        Collections.addAll(directions, GROWTH_DIRECTIONS);
        Collections.shuffle(directions);

        return directions;
    }

    private int nearbyPuddleCount(final Set<Puddle> puddles, final Puddle center, final int searchRadius) {
        int count = 0;
        long maxDistanceSquared = (long) searchRadius * searchRadius;

        for (Puddle puddle : puddles) {
            if (puddle.equals(center)) continue;
            if (!puddle.worldId().equals(center.worldId())) continue;

            int dx = puddle.x() - center.x();
            int dz = puddle.z() - center.z();
            long distanceSquared = (long) dx * dx + (long) dz * dz;

            if (distanceSquared <= maxDistanceSquared) {
                count++;
            }
        }

        return count;
    }

    private double rainMultiplier(final World world) {
        return world.isThundering() ? thunderstormMultiplier : 1.0;
    }

    private int incrementDryDelayOncePerCycle(final UUID worldId) {
        long currentCycle = cycle.get();
        Long lastCycle = lastDryDelayUpdateCycle.put(worldId, currentCycle);

        if (lastCycle != null && lastCycle == currentCycle) {
            return dryDelayCyclesByWorld.getOrDefault(worldId, 0);
        }

        return dryDelayCyclesByWorld.merge(worldId, 1, Integer::sum);
    }

    private boolean isValidGround(
            final World world,
            final int x,
            final int y,
            final int z) {

        return growthMultiplier(world.getBlockAt(x, y, z).getType()) > 0;
    }

    private Material groundMaterial(final World world, final int x, final int z) {
        int groundY = world.getHighestBlockYAt(x, z);

        return world.getBlockAt(x, groundY, z).getType();
    }

    private double growthMultiplier(final Material type) {
        return switch (type) {
            case CLAY, MUD, PACKED_MUD -> 1.25;
            case STONE, COBBLESTONE, ANDESITE, DIORITE, GRANITE,
                    DEEPSLATE, TUFF, CALCITE, MOSS_BLOCK -> 1.0;
            case GRASS_BLOCK, DIRT, COARSE_DIRT, PODZOL, ROOTED_DIRT -> 0.75;
            case SAND, RED_SAND, GRAVEL -> 0.45;
            default -> 0.0;
        };
    }

    private double dryingMultiplier(final Material type) {
        return switch (type) {
            case SAND, RED_SAND, GRAVEL -> 1.55;
            case GRASS_BLOCK, DIRT, COARSE_DIRT, PODZOL, ROOTED_DIRT -> 1.25;
            case CLAY, MUD, PACKED_MUD -> 0.65;
            default -> 1.0;
        };
    }

    private boolean isFlatEnough(
            final World world,
            final int x,
            final int y,
            final int z) {

        for (int[] direction : CARDINAL_DIRECTIONS) {
            int ny = world.getHighestBlockYAt(x + direction[0], z + direction[1]);
            if (Math.abs(ny - y) > 1) {
                return false;
            }
        }

        return true;
    }

    public void clearPlayer(final Player player) {
        clearPlayer(player, true);
    }

    public void clearPlayer(final Player player, final boolean restoreBlocks) {
        UUID uuid = player.getUniqueId();
        playerWorlds.remove(uuid);
        renderer.clear(player, restoreBlocks);
    }

    public void clearAll(final Collection<? extends Player> players, final boolean restoreBlocks) {
        for (Player player : players) {
            clearPlayer(player, restoreBlocks);
        }

        puddlesByWorld.clear();
        dryDelayCyclesByWorld.clear();
        lastDryDelayUpdateCycle.clear();
    }

    private void validatePuddleMode() {
        String mode = this.plugin.getPluginConfig().getConfig().getString("puddle-mode", "PACKET");

        if (mode == null) return;

        String normalizedMode = mode.trim().toUpperCase(Locale.ROOT);

        if (!"PACKET".equals(normalizedMode)) {
            this.plugin.getLogger().warning("Only PACKET puddle mode is active in this build; using PACKET rendering.");
        }
    }

    private boolean roll(final double chance) {
        return ThreadLocalRandom.current().nextDouble() < clamp(chance, 0.0, 1.0);
    }

    private int readPositiveConfigInt(final String path, final int fallback) {
        return readPositiveConfigInt(path, null, fallback);
    }

    private int readPositiveConfigInt(final String path, final String legacyPath, final int fallback) {
        int value = readConfigInt(path, legacyPath, fallback);

        if (value < 1) {
            this.plugin.getLogger().warning(path + " must be at least 1; using " + fallback + ".");
            return fallback;
        }

        return value;
    }

    private int readNonNegativeConfigInt(final String path, final String legacyPath, final int fallback) {
        int value = readConfigInt(path, legacyPath, fallback);

        if (value < 0) {
            this.plugin.getLogger().warning(path + " cannot be negative; using " + fallback + ".");
            return fallback;
        }

        return value;
    }

    private int readConfigInt(final String path, final String legacyPath, final int fallback) {
        if (legacyPath != null
                && !this.plugin.getPluginConfig().getConfig().contains(path)
                && this.plugin.getPluginConfig().getConfig().contains(legacyPath)) {
            return this.plugin.getPluginConfig().getConfig().getInt(legacyPath, fallback);
        }

        return this.plugin.getPluginConfig().getConfig().getInt(path, fallback);
    }

    private double readChance(final String path, final double fallback) {
        double value = this.plugin.getPluginConfig().getConfig().getDouble(path, fallback);

        if (!Double.isFinite(value)) {
            this.plugin.getLogger().warning(path + " must be a finite number; using " + fallback + ".");
            return fallback;
        }

        if (value < 0.0 || value > 1.0) {
            this.plugin.getLogger().warning(path + " must be between 0.0 and 1.0; clamping it.");
            return clamp(value, 0.0, 1.0);
        }

        return value;
    }

    private double readNonNegativeConfigDouble(final String path, final double fallback) {
        double value = this.plugin.getPluginConfig().getConfig().getDouble(path, fallback);

        if (!Double.isFinite(value) || value < 0.0) {
            this.plugin.getLogger().warning(path + " cannot be negative; using " + fallback + ".");
            return fallback;
        }

        return value;
    }

    private double readMinimumConfigDouble(final String path, final double fallback, final double minimum) {
        double value = this.plugin.getPluginConfig().getConfig().getDouble(path, fallback);

        if (!Double.isFinite(value) || value < minimum) {
            this.plugin.getLogger().warning(path + " must be at least " + minimum + "; using " + fallback + ".");
            return fallback;
        }

        return value;
    }

    private double clamp(final double value, final double minimum, final double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
