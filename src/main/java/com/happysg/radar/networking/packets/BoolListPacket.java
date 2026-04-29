package com.happysg.radar.networking.packets;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

public class BoolListPacket {
    private static final int EXPECTED_FLAG_COUNT = 7;

    public final boolean mainHand;
    public final boolean[] flags;
    public final String key;

    public BoolListPacket(boolean mainHand, boolean[] flags, String key) {
        this.mainHand = mainHand;
        this.flags = flags;
        this.key = key;
    }

    public static void encode(BoolListPacket pkt, FriendlyByteBuf buf) {
        buf.writeBoolean(pkt.mainHand);
        buf.writeUtf(pkt.key);
        buf.writeInt(pkt.flags.length);
        for (boolean b : pkt.flags) buf.writeBoolean(b);
    }

    public static BoolListPacket decode(FriendlyByteBuf buf) {
        boolean main = buf.readBoolean();
        String key = buf.readUtf();
        int len = buf.readInt();
        boolean[] f = new boolean[len];
        for (int i = 0; i < len; i++) f[i] = buf.readBoolean();
        return new BoolListPacket(main, f, key);
    }

    public static void handle(BoolListPacket pkt, Object ignored) {
    }
}
