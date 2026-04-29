package com.happysg.radar.networking.packets;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public class RadarLinkConfigurationPacket {
    public final BlockPos pos;
    public final CompoundTag configData;

    public RadarLinkConfigurationPacket(BlockPos pos, CompoundTag configData) {
        this.pos = pos;
        this.configData = configData;
    }
}
