package me.abhiram.puddlesplus.model;

public class Puddle {
    private final int x;
    private final int y;
    private final int z;
    private int life;

    public Puddle(int x, int y, int z, int life) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.life = life;
    }

    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }

    public void decay() { this.life--; }

    public boolean isDead() { return life <= 0; }
}
