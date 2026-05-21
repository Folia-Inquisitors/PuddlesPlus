package me.abhiram.puddlesplus.model;

import java.util.Objects;
import java.util.UUID;

public class Puddle {
    private final UUID worldId;
    private final int x;
    private final int y;
    private final int z;
    private int depth;

    public Puddle(UUID worldId, int x, int y, int z, int depth) {
        this.worldId = worldId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.depth = Math.max(1, depth);
    }

    public UUID worldId() { return worldId; }
    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }
    public int depth() { return depth; }

    public void soak(int maxDepth) {
        if (depth < maxDepth) {
            depth++;
        }
    }

    public void dry() { this.depth--; }

    public boolean isDry() { return depth <= 0; }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof Puddle puddle)) return false;

        return x == puddle.x
                && y == puddle.y
                && z == puddle.z
                && worldId.equals(puddle.worldId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(worldId, x, y, z);
    }
}
