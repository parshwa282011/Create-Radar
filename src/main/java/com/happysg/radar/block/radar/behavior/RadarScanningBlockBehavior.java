package com.happysg.radar.block.radar.behavior;

import com.happysg.radar.block.arad.aradnetworks.RadarContactRegistry;
import com.happysg.radar.block.radar.bearing.RadarBearingBlockEntity;

import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.block.radar.track.TrackCategory;
import com.happysg.radar.compat.aeronautics.PhysicsHandler;
import com.happysg.radar.config.RadarConfig;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.happysg.radar.block.behavior.networks.config.DetectionConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

public class RadarScanningBlockBehavior extends BlockEntityBehaviour {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final BehaviourType<RadarScanningBlockBehavior> TYPE = new BehaviourType<>();

    private int trackExpiration = 100;
    private int fov = RadarConfig.server().radarFOV.get();
    private int yRange = 20;
    private double range = RadarConfig.server().radarBaseRange.get();
    private double angle;
    private boolean running = false;
    private SmartBlockEntity bearingEntity;
    private RadarBearingBlockEntity radarBearing;
    Vec3 scanPos = Vec3.ZERO;
    private long lastAeroLogTick = -1;
    private long lastAeroErrorLogTick = -1;

    private final Set<Entity> scannedEntities = new HashSet<>();
    private final Set<Projectile> scannedProjectiles = new HashSet<>();
    private final HashMap<String, RadarTrack> radarTracks = new HashMap<>();

    public RadarScanningBlockBehavior(SmartBlockEntity be) {
        super(be);
        this.bearingEntity = be;
    }

    public void applyDetectionConfig(DetectionConfig cfg) {
        if (cfg == null) cfg = DetectionConfig.DEFAULT;
        setScanFlags(
                cfg.player(),
                cfg.vs2(),
                cfg.contraption(),
                cfg.mob(),
                cfg.animal(),
                cfg.projectile(),
                cfg.item()
        );
    }


    private boolean scanPlayers = true;
    private boolean scanVS2 = true;
    private boolean scanContraptions = true;
    private boolean scanMobs = true;
    private boolean scanAnimals = true;
    private boolean scanProjectiles = true;
    private boolean scanItems = true;
    private double lastAeroHorizontalDistance = -1;
    private double lastAeroVerticalDistance = -1;
    private Vec3 lastAeroPos = null;

    private boolean allowCategory(TrackCategory c) {
        return switch (c) {
            case PLAYER -> scanPlayers;
            case AERONAUTICS -> scanVS2;
            case CONTRAPTION -> scanContraptions;
            case PROJECTILE -> scanProjectiles;
            case ITEM -> scanItems;

            case ANIMAL -> scanAnimals;
            case HOSTILE, MOB -> scanMobs;

            default -> true;
        };
    }

    private void pruneDisabledTracksNow() {
        radarTracks.entrySet().removeIf(e -> !allowCategory(e.getValue().trackCategory()));
    }

    public void setScanFlags(boolean players, boolean vs2, boolean contraptions, boolean mobs, boolean animals, boolean projectiles, boolean items) {
        boolean changed = players != scanPlayers || vs2 != scanVS2 || contraptions != scanContraptions || mobs != scanMobs || animals != scanAnimals || projectiles != scanProjectiles || items != scanItems;

        this.scanPlayers = players;
        this.scanVS2 = vs2;
        this.scanContraptions = contraptions;
        this.scanMobs = mobs;
        this.scanAnimals = animals;
        this.scanProjectiles = projectiles;
        this.scanItems = items;

        if (changed) {
            pruneDisabledTracksNow();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (blockEntity.getLevel() == null || blockEntity.getLevel().isClientSide)
            return;
        if(blockEntity.getLevel().getGameTime() %5!=1)return;
        removeDeadTracks();
        if (running)
            updateRadarTracks();
        if (running) {
            scannedEntities.clear();
            scannedProjectiles.clear();

            scanForEntityTracks();
        }
    }


    private void updateRadarTracks() {
        scanPos = PhysicsHandler.getWorldPos(bearingEntity).getCenter();
        Level level = blockEntity.getLevel();
        if (level == null )return;


        for (Entity entity : scannedEntities) {
            if (entity.isAlive() && isInFovAndRange(entity.position())) {
                radarTracks.compute(entity.getUUID().toString(), (id, track) -> {
                    if (track == null) return new RadarTrack(entity);
                    track.updateRadarTrack(entity);
                    return track;
                });

                if (entity instanceof Projectile)
                    scannedProjectiles.add((Projectile) entity);
            }
        }

        if (level instanceof ServerLevel sl && scanVS2) {
            scanForAeronauticsTracks(sl);
        }
    }

    private void scanForAeronauticsTracks(ServerLevel level) {
        try {
            Class<?> containerClass = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
            Object container = containerClass.getMethod("getContainer", ServerLevel.class).invoke(null, level);
            if (container == null) return;

            Method getAllSubLevels = container.getClass().getMethod("getAllSubLevels");
            Iterable<?> subLevels = (Iterable<?>) getAllSubLevels.invoke(container);

            int considered = 0;
            int accepted = 0;
            long now = level.getGameTime();

            for (Object subLevel : subLevels) {
                if (subLevel == null) continue;
                if ((boolean) subLevel.getClass().getMethod("isRemoved").invoke(subLevel)) continue;
                considered++;

                Object bounds = subLevel.getClass().getMethod("boundingBox").invoke(subLevel);
                double minX = getDouble(bounds, "minX");
                double minY = getDouble(bounds, "minY");
                double minZ = getDouble(bounds, "minZ");
                double maxX = getDouble(bounds, "maxX");
                double maxY = getDouble(bounds, "maxY");
                double maxZ = getDouble(bounds, "maxZ");
                Vec3 pos = new Vec3(
                        (minX + maxX) * 0.5,
                        (minY + maxY) * 0.5,
                        (minZ + maxZ) * 0.5
                );

                if (!isAeroSubLevelInRange(minX, minY, minZ, maxX, maxY, maxZ, pos)) continue;

                Field velocityField = subLevel.getClass().getField("latestLinearVelocity");
                Object latestLinearVelocity = velocityField.get(subLevel);
                Vec3 velocity = new Vec3(
                        getDouble(latestLinearVelocity, "x"),
                        getDouble(latestLinearVelocity, "y"),
                        getDouble(latestLinearVelocity, "z")
                );
                String id = subLevel.getClass().getMethod("getUniqueId").invoke(subLevel).toString();
                float height = (float) Math.max(1.0, maxY - minY);

                radarTracks.put(id, new RadarTrack(id, pos, velocity, now, TrackCategory.AERONAUTICS, "CreateAero:ship", height));
                RadarContactRegistry.markInRange(level, id.hashCode(), trackExpiration);
                accepted++;
            }

            if (now - lastAeroLogTick > 40) {
                LOGGER.warn("[RADAR-AERO] scan radar={} considered={} accepted={} scanPos={} lastPos={} horiz={} vertical={} range={} aeroYRange={} angle={} fov={}",
                        blockEntity.getBlockPos(), considered, accepted, scanPos, lastAeroPos,
                        lastAeroHorizontalDistance, lastAeroVerticalDistance, range,
                        aeroVerticalRange(), angle, fov);
                lastAeroLogTick = now;
            }
        } catch (Throwable t) {
            long now = level.getGameTime();
            if (now - lastAeroErrorLogTick > 100) {
                LOGGER.warn("[RADAR-AERO] failed to scan Create Aeronautics sublevels radar={}: {}",
                        blockEntity.getBlockPos(), t.toString());
                lastAeroErrorLogTick = now;
            }
        }
    }

    private static double getDouble(Object object, String methodName) throws ReflectiveOperationException {
        return ((Number) object.getClass().getMethod(methodName).invoke(object)).doubleValue();
    }

    private boolean isAeroSubLevelInRange(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, Vec3 center) {
        double closestX = clamp(scanPos.x(), minX, maxX);
        double closestY = clamp(scanPos.y(), minY, maxY);
        double closestZ = clamp(scanPos.z(), minZ, maxZ);

        double dx = closestX - scanPos.x();
        double dz = closestZ - scanPos.z();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        double verticalDistance = Math.abs(closestY - scanPos.y());

        lastAeroHorizontalDistance = horizontalDistance;
        lastAeroVerticalDistance = verticalDistance;
        lastAeroPos = center;

        return horizontalDistance <= range && verticalDistance <= aeroVerticalRange();
    }

    private double aeroVerticalRange() {
        return Math.max(RadarConfig.server().radarYScanRange.get(), 64.0);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean isInFovAndRange(Vec3 target) {
        double horizontalDistance = Math.sqrt(Math.pow(target.x() - scanPos.x(), 2) + Math.pow(target.z() - scanPos.z(), 2));
        double verticalDistance = Math.abs(target.y() - scanPos.y());
        double yScanRange = RadarConfig.server().radarYScanRange.get();

        if (horizontalDistance > range || verticalDistance > yScanRange)
            return false;

        if (horizontalDistance < 2)
            return true;

        double angleToEntity = Math.toDegrees(Math.atan2(target.x() - scanPos.x(), target.z() - scanPos.z()));
        angleToEntity = (angleToEntity + 360) % 360;
        double angleDiff = Math.abs(angleToEntity - angle);
        if (angleDiff > 180) angleDiff = 360 - angleDiff;

        return angleDiff <= fov / 2.0;
    }

    private void removeDeadTracks() {
        // entities
        for (Entity entity : scannedEntities) {
            if (!entity.isAlive())
                radarTracks.remove(entity.getUUID().toString());
        }

        // ttl expiration (works for everything: entities, ships, projectiles)
        List<String> toRemove = new ArrayList<>();
        assert blockEntity.getLevel() != null;
        long currentTime = blockEntity.getLevel().getGameTime();
        for (RadarTrack track : radarTracks.values()) {
            if (currentTime - track.scannedTime() > trackExpiration)
                toRemove.add(track.id());
        }
        toRemove.forEach(radarTracks::remove);

        // projectiles
        scannedProjectiles.removeIf(p -> {
            boolean dead = !p.isAlive();
            if (dead) radarTracks.remove(p.getUUID().toString());
            return dead;
        });
    }
    private void scanForEntityTracks() {
        Level level = blockEntity.getLevel();
        if (level == null) return;

        boolean scanAll =
                scanPlayers && scanVS2 && scanContraptions && scanMobs && scanAnimals && scanProjectiles && scanItems;

        for (AABB aabb : splitAABB(getRadarAABB(), 256)) {
            if (scanAll) {
                scannedEntities.addAll(level.getEntities(null, aabb));
                continue;
            }

            if (scanPlayers)
                scannedEntities.addAll(level.getEntitiesOfClass(Player.class, aabb));

            if (scanProjectiles)
                scannedEntities.addAll(level.getEntitiesOfClass(Projectile.class, aabb));

            if (scanItems)
                scannedEntities.addAll(level.getEntitiesOfClass(ItemEntity.class, aabb));

            if (scanContraptions)
                scannedEntities.addAll(level.getEntitiesOfClass(AbstractContraptionEntity.class, aabb));

            if (scanAnimals)
                scannedEntities.addAll(level.getEntitiesOfClass(Animal.class, aabb));

            if (scanMobs) {
                scannedEntities.addAll(level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, aabb,
                        e -> !(e instanceof Animal)));
            }
        }
    }

    private AABB getRadarAABB() {
        BlockPos radarPos = PhysicsHandler.getWorldPos(blockEntity);
        double x = radarPos.getX() + 0.5;
        double y = radarPos.getY() + 0.5;
        double z = radarPos.getZ() + 0.5;

        double yScan = RadarConfig.server().radarYScanRange.get();
        Level level = blockEntity.getLevel();
        double minY = level != null ? Math.max(y - yScan, level.getMinBuildHeight()) : y - yScan;
        double maxY = level != null ? Math.min(y + yScan, level.getMaxBuildHeight()) : y + yScan;

        return new AABB(
                x - range, minY, z - range,
                x + range, maxY, z + range
        );
    }

    public static List<AABB> splitAABB(AABB aabb, double maxSize) {
        List<AABB> result = new ArrayList<>();
        for (double x = aabb.minX; x < aabb.maxX; x += maxSize) {
            for (double y = aabb.minY; y < aabb.maxY; y += maxSize) {
                for (double z = aabb.minZ; z < aabb.maxZ; z += maxSize) {
                    result.add(new AABB(
                            x, y, z,
                            Math.min(x + maxSize, aabb.maxX),
                            Math.min(y + maxSize, aabb.maxY),
                            Math.min(z + maxSize, aabb.maxZ)
                    ));
                }
            }
        }
        return result;
    }

    @Override
    public void read(CompoundTag nbt, net.minecraft.core.HolderLookup.Provider registries, boolean clientPacket) {
        super.read(nbt, registries, clientPacket);
        if (nbt.contains("fov")) fov = nbt.getInt("fov");
        if (nbt.contains("yRange")) yRange = nbt.getInt("yRange");
        if (nbt.contains("range")) range = nbt.getDouble("range");
        if (nbt.contains("angle")) angle = nbt.getDouble("angle");
        if (nbt.contains("scanPosX")) scanPos = new Vec3(nbt.getDouble("scanPosX"), nbt.getDouble("scanPosY"), nbt.getDouble("scanPosZ"));
        if (nbt.contains("running")) running = nbt.getBoolean("running");
        if (nbt.contains("trackExpiration")) trackExpiration = nbt.getInt("trackExpiration");
    }

    @Override
    public void write(CompoundTag nbt, net.minecraft.core.HolderLookup.Provider registries, boolean clientPacket) {
        super.write(nbt, registries, clientPacket);
        nbt.putInt("fov", fov);
        nbt.putInt("yRange", yRange);
        nbt.putDouble("range", range);
        nbt.putDouble("angle", angle);
        nbt.putDouble("scanPosX", scanPos.x);
        nbt.putDouble("scanPosY", scanPos.y);
        nbt.putDouble("scanPosZ", scanPos.z);
        nbt.putBoolean("running", running);
        nbt.putInt("trackExpiration", trackExpiration);
    }

    public void setFov(int fov) { this.fov = fov; }
    public void setYRange(int yRange) { this.yRange = yRange; }
    public void setRange(double range) { this.range = range; }
    public void setAngle(double angle) { this.angle = angle; }
    public void setScanPos(Vec3 scanPos) { this.scanPos = scanPos; }
    public void setRunning(boolean running) { this.running = running; }
    public void setTrackExpiration(int trackExpiration) { this.trackExpiration = trackExpiration; }

    public Collection<RadarTrack> getRadarTracks() {
        return radarTracks.values();
    }

    @Override
    public BehaviourType<?> getType() {
        return TYPE;
    }

    public float getAngle() {
        return (float) angle;
    }
}
