package com.happysg.radar.networking;

import com.happysg.radar.block.monitor.MonitorSelectionPacket;
import com.happysg.radar.networking.packets.BoolListPacket;
import com.happysg.radar.networking.packets.FirePacket;
import com.happysg.radar.networking.packets.RaycastPacket;
import com.happysg.radar.networking.packets.SaveListsPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class NetworkHandler {
    public static final Channel CHANNEL = new Channel();

    public static void register() {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToServer(BoolListPacket.TYPE, BoolListPacket.STREAM_CODEC, BoolListPacket::handle);
        registrar.playToServer(SaveListsPacket.TYPE, SaveListsPacket.STREAM_CODEC, SaveListsPacket::handle);
        registrar.playToServer(MonitorSelectionPacket.TYPE, MonitorSelectionPacket.STREAM_CODEC, MonitorSelectionPacket::handle);
        registrar.playToServer(FirePacket.TYPE, FirePacket.STREAM_CODEC, FirePacket::handle);
        registrar.playToServer(RaycastPacket.TYPE, RaycastPacket.STREAM_CODEC, RaycastPacket::handle);
    }

    public static class Channel {
        public void sendToServer(Object packet) {
            if (packet instanceof CustomPacketPayload payload) {
                Minecraft.getInstance().getConnection().send(payload);
            }
        }
    }
}
