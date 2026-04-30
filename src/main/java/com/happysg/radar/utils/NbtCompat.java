package com.happysg.radar.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class NbtCompat {
    private NbtCompat() {
    }

    public static CompoundTag getOrCreateTag(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return data.copyTag();
    }

    public static CompoundTag getTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : data.copyTag();
    }

    public static boolean hasTag(ItemStack stack) {
        return stack.has(DataComponents.CUSTOM_DATA);
    }

    public static void setTag(ItemStack stack, CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    public static CompoundTag writeBlockPos(BlockPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("X", pos.getX());
        tag.putInt("Y", pos.getY());
        tag.putInt("Z", pos.getZ());
        return tag;
    }

    public static BlockPos readBlockPos(CompoundTag tag) {
        if (tag.contains("X")) {
            return new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"));
        }
        if (tag.contains("x")) {
            return new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
        }
        if (tag.contains("Pos")) {
            return BlockPos.of(tag.getLong("Pos"));
        }
        if (tag.contains("pos")) {
            return BlockPos.of(tag.getLong("pos"));
        }
        return BlockPos.ZERO;
    }
}
