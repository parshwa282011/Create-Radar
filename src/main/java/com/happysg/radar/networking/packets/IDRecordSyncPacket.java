package com.happysg.radar.networking.packets;

import com.happysg.radar.block.controller.id.IDManager;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public class IDRecordSyncPacket {
    private final long shipId;
    private final boolean hasRecord;
    private final String name;
    private final String secretID;

    public IDRecordSyncPacket(long shipId, boolean hasRecord, String name, String secretID) {
        this.shipId = shipId;
        this.hasRecord = hasRecord;
        this.name = name == null ? "" : name;
        this.secretID = secretID == null ? "" : secretID;
    }

    public IDRecordSyncPacket(FriendlyByteBuf buffer) {
        this.shipId = buffer.readLong();
        this.hasRecord = buffer.readBoolean();
        this.name = buffer.readUtf(32767);
        this.secretID = buffer.readUtf(32767);
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeLong(shipId);
        buffer.writeBoolean(hasRecord);
        buffer.writeUtf(name, 32767);
        buffer.writeUtf(secretID, 32767);
    }

    public boolean handle(Object ignored) {
        return true;
    }

    @OnlyIn(Dist.CLIENT)
    private static class Client {
        private static void handle(long shipId, boolean hasRecord, String name, String secretID) {
            if (hasRecord) {
                IDManager.addIDRecord(shipId, secretID, name);
            } else {
                IDManager.ID_RECORDS.remove(shipId);
            }

        }
    }
}
