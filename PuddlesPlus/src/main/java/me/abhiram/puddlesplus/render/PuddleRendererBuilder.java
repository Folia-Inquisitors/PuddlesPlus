package me.abhiram.puddlesplus.render;

import me.abhiram.puddlesplus.PuddlesPlus;
import me.abhiram.puddlesplus.render.impl.PacketWaterPuddleRenderer;

public class PuddleRendererBuilder {
    private RendererType type = RendererType.PACKET_WATER;
    private final PuddlesPlus plugin;

    public PuddleRendererBuilder(PuddlesPlus plugin){
        this.plugin = plugin;
    }

    public PuddleRendererBuilder type(final RendererType type) {
        this.type = type;
        return this;
    }

    public PuddleRenderer build() {
        return switch (type) {
            case PACKET_WATER -> new PacketWaterPuddleRenderer(this.plugin);
        };
    }

    public enum RendererType {
        PACKET_WATER
    }
}
