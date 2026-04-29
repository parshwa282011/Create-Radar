package com.happysg.radar.networking.packets;

import com.happysg.radar.networking.networkhandlers.ListNBTHandler;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SaveListsPacket {
    private final List<String> entries;
     final String idString;
    private final boolean isIdString;

    /** Constructor for list mode **/
    public SaveListsPacket(List<String> entries) {
        if (entries == null ) {
            throw new IllegalArgumentException("entries and friendOrFoe cannot be null");
        }
        this.entries      = new ArrayList<>(entries);
        this.idString     = null;
        this.isIdString   = false;
    }

    /** Constructor for single‐string mode **/
    public SaveListsPacket(String idString) {
        if (idString == null) {
            throw new IllegalArgumentException("idString cannot be null");
        }
        this.entries      = Collections.emptyList();
        this.idString     = idString;
        this.isIdString   = true;
    }

    public static void encode(SaveListsPacket pkt, FriendlyByteBuf buf) {
        buf.writeByte(2);
        buf.writeBoolean(pkt.isIdString);

        if (pkt.isIdString) {
            buf.writeUtf(pkt.idString, 32767);
            return;
        }

        buf.writeVarInt(pkt.entries.size());
        for (String s : pkt.entries) {
            buf.writeUtf(s == null ? "" : s, 32767);
        }
    }

    public static SaveListsPacket decode(FriendlyByteBuf buf) {
        int version = buf.readByte();

        if (version == 1) {
            // i can add your old decode here if needed
            boolean isId = buf.readBoolean();
            if (isId) return new SaveListsPacket(buf.readUtf(32767));
            int es = buf.readVarInt();
            List<String> entries = new ArrayList<>(es);
            for (int i = 0; i < es; i++) entries.add(buf.readUtf(32767));
            return new SaveListsPacket(entries);
        }

        // version 2 (current)
        boolean isId = buf.readBoolean();
        if (isId) {
            return new SaveListsPacket(buf.readUtf(32767));
        }

        int es = buf.readVarInt();
        List<String> entries = new ArrayList<>(es);
        for (int i = 0; i < es; i++) {
            entries.add(buf.readUtf(32767));
        }
        return new SaveListsPacket(entries);
    }


    /** Handle on the server: call the appropriate ListNBTHandler method **/
    public static void handle(SaveListsPacket pkt, Object ignored) {
    }
}
