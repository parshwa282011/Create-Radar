package com.happysg.radar.networking.packets;

import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity;
import com.happysg.radar.item.binos.Binoculars;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

public class FirePacket {

    private static final String TAG_FILTERER_POS = "filtererPos";


    private final boolean enable;

    public FirePacket(boolean enable) {
        this.enable = enable;
    }
    public static void encode(FirePacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.enable);
    }
    public static FirePacket decode(FriendlyByteBuf buf) {
        return new FirePacket(buf.readBoolean());
    }

    public static void handle(FirePacket msg, Object ignored) {
    }

    private static ItemStack findBinosStack(Player player) {
        // i prioritize “using item” (scoped) because that’s the cleanest intent
        ItemStack using = player.getUseItem();
        if (!using.isEmpty() && using.getItem() instanceof Binoculars) return using;

        ItemStack main = player.getMainHandItem();
        if (!main.isEmpty() && main.getItem() instanceof Binoculars) return main;

        ItemStack off = player.getOffhandItem();
        if (!off.isEmpty() && off.getItem() instanceof Binoculars) return off;

        return ItemStack.EMPTY;
    }

    @Nullable
    private static BlockPos getFiltererPos(ItemStack stack) {
        CompoundTag tag = com.happysg.radar.utils.NbtCompat.getTag(stack);
        if (tag == null) return null;
        if (!tag.contains(TAG_FILTERER_POS)) return null;

        return com.happysg.radar.utils.NbtCompat.readBlockPos(tag.getCompound(TAG_FILTERER_POS));
    }
}
