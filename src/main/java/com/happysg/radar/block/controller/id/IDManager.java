package com.happysg.radar.block.controller.id;

import net.minecraft.nbt.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

public class IDManager extends SavedData {

    public static final IDManager INSTANCE = new IDManager();

    // Records are keyed by a stable transponder id.
    public static final Map<Long, IDRecord> ID_RECORDS = new HashMap<>();

    public record IDRecord(String name, String secretID) {}

    public static void addIDRecord(long id, String secretID, String name) {
        ID_RECORDS.put(id, new IDRecord(name, secretID));
        INSTANCE.setDirty();
    }

    public static IDRecord getIDRecordById(long id) {
        return ID_RECORDS.get(id);
    }

    public static IDManager load(CompoundTag tag) {
        if (!tag.contains("idRecords")) return INSTANCE;

        ListTag list = tag.getList("idRecords", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);

            // i prefer the new long key if present
            if (c.contains("shipId", Tag.TAG_LONG)) {
                long shipId = c.getLong("shipId");
                String name = c.getString("name");       // now slug
                String secretID = c.getString("secretID");
                ID_RECORDS.put(shipId, new IDRecord(name, secretID));
                continue;
            }
            String legacySlug = c.getString("shipSlug");
            String name = c.getString("name");
            String secretID = c.getString("secretID");

            long legacyKey = legacySlug.hashCode();
            ID_RECORDS.put(legacyKey, new IDRecord(name.isEmpty() ? legacySlug : name, secretID));
        }

        return INSTANCE;
    }

    public static IDManager load(CompoundTag tag, HolderLookup.Provider provider) {
        return load(tag);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();

        for (Map.Entry<Long, IDRecord> e : ID_RECORDS.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putLong("shipId", e.getKey());
            c.putString("name", e.getValue().name());         // slug stored here
            c.putString("secretID", e.getValue().secretID());
            list.add(c);
        }

        tag.put("idRecords", list);
        return tag;
    }

    public static void load(MinecraftServer server) {
        server.overworld()
                .getDataStorage()
                .computeIfAbsent(new Factory<>(() -> INSTANCE, IDManager::load), "create_radar_ids");
    }
}
