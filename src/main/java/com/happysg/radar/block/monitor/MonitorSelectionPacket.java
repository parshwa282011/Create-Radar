package com.happysg.radar.block.monitor;

import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.networking.NetworkHandler;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Client -> Server: select a track on a given monitor controller.
 */
public class MonitorSelectionPacket {


    private final BlockPos controllerPos;
    private final String selectedId;

    public MonitorSelectionPacket(BlockPos controllerPos, String selectedId) {
        this.controllerPos = controllerPos;
        this.selectedId = selectedId;
    }

    public static void encode(MonitorSelectionPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.controllerPos);
        buf.writeBoolean(msg.selectedId != null);
        if (msg.selectedId != null) {
            buf.writeUtf(msg.selectedId);
        }
    }

    public static MonitorSelectionPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String id = buf.readBoolean() ? buf.readUtf() : null;
        return new MonitorSelectionPacket(pos, id);
    }

    public static void handle(MonitorSelectionPacket msg, Object ignored) {
    }

    public static void send(BlockPos controllerPos, String selectedId) {
        NetworkHandler.CHANNEL.sendToServer(new MonitorSelectionPacket(controllerPos, selectedId));
    }
}
