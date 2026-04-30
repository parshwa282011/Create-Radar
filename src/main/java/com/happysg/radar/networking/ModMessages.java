package com.happysg.radar.networking;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

public class ModMessages {
    public static void register() {
    }

    public static <MSG> void sendToServer(MSG message) {
        if (message instanceof CustomPacketPayload payload) {
            Minecraft.getInstance().getConnection().send(payload);
        }
    }

    public static <MSG> void sendToPlayer(MSG message, ServerPlayer player) {
        if (message instanceof CustomPacketPayload payload) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    public static <MSG> void sendToClients(MSG message) {
        if (message instanceof CustomPacketPayload payload) {
            PacketDistributor.sendToAllPlayers(payload);
        }
    }
}
