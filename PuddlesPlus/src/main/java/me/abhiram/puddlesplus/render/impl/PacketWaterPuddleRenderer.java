package me.abhiram.puddlesplus.render.impl;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import me.abhiram.puddlesplus.PuddlesPlus;
import me.abhiram.puddlesplus.model.Puddle;
import me.abhiram.puddlesplus.render.PuddleRenderer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.*;

public final class PacketWaterPuddleRenderer implements PuddleRenderer {

    private final PuddlesPlus plugin;

    public PacketWaterPuddleRenderer(PuddlesPlus plugin) {
        this.plugin = plugin;
    }

    private static final ExecutorService PACKET_POOL =
            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    // Active puddles (positions only)
    private final Map<UUID, Set<BlockPosition>> active = new ConcurrentHashMap<>();

    // Original block states (for restore)
    private final Map<UUID, Map<BlockPosition, WrappedBlockData>> original = new ConcurrentHashMap<>();

    @Override
    public void render(final Player player, final Set<Puddle> puddles) {

        final UUID uuid = player.getUniqueId();

        // Snapshot positions (MAIN THREAD ONLY)
        final Set<BlockPosition> newPositions = new HashSet<>();
        final Map<BlockPosition, WrappedBlockData> originalSnapshot =
                original.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());

        for (Puddle puddle : puddles) {

            BlockPosition pos = new BlockPosition(
                    puddle.x(),
                    puddle.y(),
                    puddle.z());

            newPositions.add(pos);

            // Cache original block ONLY once
            originalSnapshot.computeIfAbsent(pos, p ->
                    WrappedBlockData.createData(
                            player.getWorld()
                                    .getBlockAt(p.getX(), p.getY(), p.getZ())
                                    .getBlockData()
                    ));
        }

        Set<BlockPosition> previous = active.getOrDefault(uuid, Collections.emptySet());

        // Compute diff (only send changes)
        Set<BlockPosition> toAdd = new HashSet<>(newPositions);
        toAdd.removeAll(previous);

        Set<BlockPosition> toRemove = new HashSet<>(previous);
        toRemove.removeAll(newPositions);

        active.put(uuid, newPositions);

        PACKET_POOL.submit(() -> {
            try {
                sendWater(player, toAdd);
                restore(player, uuid, toRemove);
            } catch (Exception ignored) {
            }
        });
    }

    private void sendWater(Player player, Set<BlockPosition> positions) throws Exception {

        if (positions.isEmpty()) return;

        Levelled water = (Levelled) Bukkit.createBlockData(Material.WATER);
        water.setLevel(7);
        WrappedBlockData waterData = WrappedBlockData.createData(water);

        for (BlockPosition pos : positions) {

            PacketContainer packet =
                    plugin.getProtocolManager()
                            .createPacket(PacketType.Play.Server.BLOCK_CHANGE);

            packet.getBlockPositionModifier().write(0, pos);
            packet.getBlockData().write(0, waterData);

            plugin.getProtocolManager().sendServerPacket(player, packet);
        }
    }

    private void restore(Player player, UUID uuid, Set<BlockPosition> positions) throws Exception {

        if (positions.isEmpty()) return;

        Map<BlockPosition, WrappedBlockData> map = original.get(uuid);
        if (map == null) return;

        for (BlockPosition pos : positions) {

            WrappedBlockData data = map.remove(pos);
            if (data == null) continue;

            PacketContainer packet =
                    plugin.getProtocolManager()
                            .createPacket(PacketType.Play.Server.BLOCK_CHANGE);

            packet.getBlockPositionModifier().write(0, pos);
            packet.getBlockData().write(0, data);

            plugin.getProtocolManager().sendServerPacket(player, packet);
        }
    }

    @Override
    public void clear(final Player player) {

        UUID uuid = player.getUniqueId();

        Set<BlockPosition> positions = active.remove(uuid);
        Map<BlockPosition, WrappedBlockData> map = original.remove(uuid);

        if (positions == null || map == null) return;

        PACKET_POOL.submit(() -> {
            try {
                for (BlockPosition pos : positions) {

                    WrappedBlockData data = map.get(pos);
                    if (data == null) continue;

                    PacketContainer packet =
                            plugin.getProtocolManager()
                                    .createPacket(PacketType.Play.Server.BLOCK_CHANGE);

                    packet.getBlockPositionModifier().write(0, pos);
                    packet.getBlockData().write(0, data);

                    plugin.getProtocolManager().sendServerPacket(player, packet);
                }
            } catch (Exception ignored) {
            }
        });
    }
}