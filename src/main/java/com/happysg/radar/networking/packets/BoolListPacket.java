package com.happysg.radar.networking.packets;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.networking.networkhandlers.BoolNBThelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class BoolListPacket implements CustomPacketPayload {
    public static final Type<BoolListPacket> TYPE = new Type<>(CreateRadar.asResource("bool_list"));
    public static final StreamCodec<FriendlyByteBuf, BoolListPacket> STREAM_CODEC =
            StreamCodec.ofMember(BoolListPacket::encode, BoolListPacket::decode);

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

    public static void handle(BoolListPacket pkt, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;

        ItemStack stack = pkt.mainHand ? player.getMainHandItem() : player.getItemInHand(InteractionHand.OFF_HAND);
        if (stack.isEmpty()) return;

        boolean[] flags = normalizedFlags(pkt.flags);
        BoolNBThelper.saveBooleansAsBytes(stack, flags, pkt.key);
        saveStructuredConfig(stack, pkt.key, flags);

        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static boolean[] normalizedFlags(boolean[] input) {
        boolean[] out = new boolean[EXPECTED_FLAG_COUNT];
        if (input != null) {
            System.arraycopy(input, 0, out, 0, Math.min(input.length, out.length));
        }
        return out;
    }

    private static void saveStructuredConfig(ItemStack stack, String key, boolean[] flags) {
        CompoundTag root = com.happysg.radar.utils.NbtCompat.getOrCreateTag(stack);
        CompoundTag filters = root.contains("Filters", Tag.TAG_COMPOUND)
                ? root.getCompound("Filters")
                : new CompoundTag();

        if ("detectBools".equals(key)) {
            CompoundTag detection = new CompoundTag();
            detection.putBoolean("player", flags[0]);
            detection.putBoolean("vs2", flags[1]);
            detection.putBoolean("contraption", flags[2]);
            detection.putBoolean("mob", flags[3]);
            detection.putBoolean("animal", flags[4]);
            detection.putBoolean("projectile", flags[5]);
            detection.putBoolean("item", flags[6]);
            filters.put("detection", detection);
        } else if ("TargetBools".equals(key)) {
            CompoundTag targeting = new CompoundTag();
            targeting.putBoolean("player", flags[0]);
            targeting.putBoolean("contraption", flags[1]);
            targeting.putBoolean("mob", flags[2]);
            targeting.putBoolean("animal", flags[3]);
            targeting.putBoolean("projectile", flags[4]);
            targeting.putBoolean("lineSight", flags[5]);
            targeting.putBoolean("autoTarget", flags[6]);
            filters.put("targeting", targeting);
        }

        if (!filters.isEmpty()) {
            root.put("Filters", filters);
        }
        com.happysg.radar.utils.NbtCompat.setTag(stack, root);
    }
}
