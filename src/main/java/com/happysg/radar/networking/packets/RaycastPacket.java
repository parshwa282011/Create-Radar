package com.happysg.radar.networking.packets;


import com.happysg.radar.CreateRadar;
import com.happysg.radar.config.RadarConfig;
import com.happysg.radar.item.binos.Binoculars;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class RaycastPacket implements CustomPacketPayload {
    public static final Type<RaycastPacket> TYPE = new Type<>(CreateRadar.asResource("bino_raycast"));
    public static final StreamCodec<FriendlyByteBuf, RaycastPacket> STREAM_CODEC =
            StreamCodec.ofMember(RaycastPacket::encode, RaycastPacket::decode);

    // tune these however you want
    private static final double STEP = 0.25;

    public RaycastPacket() {}

    public static void encode(RaycastPacket msg, FriendlyByteBuf buf) {

    }

    public static RaycastPacket decode(FriendlyByteBuf buf) {
        return new RaycastPacket();
    }

    public static void handle(RaycastPacket msg, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        net.minecraft.world.item.ItemStack stack = findBinosStack(player);
        if (stack.isEmpty()) return;

        BlockPos hit = raycastFirstNonTransparentBlock(level, player, RadarConfig.server().binoRaycastRange.get(), STEP);
        if (hit == null) {
            clearLastHit(stack);
            player.displayClientMessage(Component.translatable(CreateRadar.MODID + ".binoculars.out_of_range"), true);
            return;
        }

        storeLastHit(stack, level.dimension(), hit);
    }

    private static net.minecraft.world.item.ItemStack findBinosStack(Player player) {
        net.minecraft.world.item.ItemStack using = player.getUseItem();
        if (!using.isEmpty() && using.getItem() instanceof Binoculars) return using;

        net.minecraft.world.item.ItemStack main = player.getMainHandItem();
        if (!main.isEmpty() && main.getItem() instanceof Binoculars) return main;

        net.minecraft.world.item.ItemStack off = player.getOffhandItem();
        if (!off.isEmpty() && off.getItem() instanceof Binoculars) return off;

        return net.minecraft.world.item.ItemStack.EMPTY;
    }


    @Nullable
    private static BlockPos raycastFirstNonTransparentBlock(ServerLevel level, Player player, double maxDistance, double step) {
        Vec3 start = player.getEyePosition();
        Vec3 dir = player.getLookAngle().normalize();

        BlockPos lastPos = BlockPos.containing(start);

        for (double t = 0.0; t <= maxDistance; t += step) {
            Vec3 p = start.add(dir.scale(t));
            BlockPos pos = BlockPos.containing(p);
            if (pos.equals(lastPos)) continue;
            lastPos = pos;

            if (!level.isLoaded(pos)) continue;

            BlockState state = level.getBlockState(pos);

            if (state.isAir()) continue;

            if (isTransparentPassThrough(level, pos, state)) continue;

            return pos;
        }

        return null;
    }

    private static boolean isTransparentPassThrough(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getCollisionShape(level, pos).isEmpty()) return true;

        if (!state.canOcclude() || !state.isSolidRender(level, pos)) return true;
        if (!state.getFluidState().isEmpty()) return true;

        return false;
    }

    private static void storeLastHit(net.minecraft.world.item.ItemStack stack, ResourceKey<Level> dim, BlockPos pos) {
        CompoundTag tag = com.happysg.radar.utils.NbtCompat.getOrCreateTag(stack);

        CompoundTag hit = new CompoundTag();
        hit.putInt("x", pos.getX());
        hit.putInt("y", pos.getY());
        hit.putInt("z", pos.getZ());
        hit.putString("dim", dim.location().toString());

        tag.put("LastHitPos", hit);
    }

    private static void clearLastHit(net.minecraft.world.item.ItemStack stack) {
        CompoundTag tag = com.happysg.radar.utils.NbtCompat.getTag(stack);
        if (tag == null) return;
        tag.remove("LastHitPos");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
