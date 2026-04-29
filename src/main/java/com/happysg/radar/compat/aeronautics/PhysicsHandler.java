package com.happysg.radar.compat.aeronautics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Loader-safe physics bridge for Create Aeronautics/Simulated.
 *
 * Aeronautics exposes moving constructions as Simulated sub-levels; until a stable
 * public transform lookup is used here, radar logic remains in ordinary world
 * space. Keeping this bridge prevents old VS-specific code from leaking through
 * the rest of the mod.
 */
public final class PhysicsHandler {
    private PhysicsHandler() {
    }

    public static BlockPos getWorldPos(Level level, BlockPos pos) {
        return pos;
    }

    public static BlockPos getWorldPos(BlockEntity blockEntity) {
        return getWorldPos(blockEntity.getLevel(), blockEntity.getBlockPos());
    }

    public static Vec3 getWorldVec(Level level, BlockPos pos) {
        return pos.getCenter();
    }

    public static Vec3 getWorldVec(Level level, Vec3 vec3) {
        return vec3;
    }

    public static Vec3 getWorldVec(BlockEntity blockEntity) {
        return blockEntity.getBlockPos().getCenter();
    }

    public static Vec3 getLocalVec(Vec3 vec3, BlockEntity be) {
        return vec3;
    }

    public static Vec3 getWorldVecDirectionTransform(Vec3 vec3, BlockEntity be) {
        return vec3;
    }

    public static boolean isBlockInContraption(Level level, BlockPos blockPos) {
        return false;
    }
}
