package com.happysg.radar.networking.packets;

import com.happysg.radar.block.controller.id.IDManager;
import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;

public class IDRecordPacket {
    long shipId;
    String shipSlug;
    String secretID;
    String newSlug;

    public IDRecordPacket(long shipId, String shipSlug, String secretID, String newName) {
        this.shipId = shipId;
        this.shipSlug = shipSlug == null ? "" : shipSlug;
        this.secretID = secretID == null ? "" : secretID;
        this.newSlug = newName == null ? "" : newName;
    }

    public IDRecordPacket(FriendlyByteBuf buffer) {
        this.shipId = buffer.readLong();
        this.shipSlug = buffer.readUtf(32767);
        this.secretID = buffer.readUtf(32767);
        this.newSlug = buffer.readUtf(32767);
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeLong(shipId);
        buffer.writeUtf(shipSlug, 32767);
        buffer.writeUtf(secretID, 32767);
        buffer.writeUtf(newSlug, 32767);
    }

    public boolean handle(Object ignored) {
        return true;
    }
}
