package com.happysg.radar.networking.packets;

import com.happysg.radar.block.controller.id.IDManager;
import com.happysg.radar.networking.ModMessages;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class IDRecordRequestPacket {
    private final long shipId;

    public IDRecordRequestPacket(long shipId) {
        this.shipId = shipId;
    }

    public IDRecordRequestPacket(FriendlyByteBuf buffer) {
        this.shipId = buffer.readLong();
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeLong(shipId);
    }

    public boolean handle(Object ignored) {
        return true;
    }
}
