package com.happysg.radar.compat.cbc;

import com.happysg.radar.compat.Mods;
import com.happysg.radar.mixin.AutocannonProjectileAccessor;
import com.mojang.logging.LogUtils;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.core.BlockPos;
import java.util.function.Predicate;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;
import com.happysg.radar.compat.cbcwpf.CBCWPFCompat;

import org.slf4j.Logger;

import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.MountedAutocannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.MountedBigCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;
import rbasamoyai.createbigcannons.cannons.autocannon.IAutocannonBlockEntity;
import rbasamoyai.createbigcannons.cannons.autocannon.material.AutocannonMaterial;

import rbasamoyai.createbigcannons.cannons.big_cannons.BigCannonBehavior;
import rbasamoyai.createbigcannons.cannons.big_cannons.IBigCannonBlockEntity;
import rbasamoyai.createbigcannons.munitions.big_cannon.AbstractBigCannonProjectile;
import rbasamoyai.createbigcannons.munitions.big_cannon.ProjectileBlock;
import rbasamoyai.createbigcannons.munitions.big_cannon.propellant.BigCannonPropellantBlock;
import rbasamoyai.createbigcannons.munitions.config.components.BallisticPropertiesComponent;
import rbasamoyai.createbigcannons.cannons.autocannon.breech.AutocannonBreechBlockEntity;
import rbasamoyai.createbigcannons.munitions.autocannon.AutocannonAmmoItem;
import rbasamoyai.createbigcannons.munitions.autocannon.AbstractAutocannonProjectile;
import net.arsenalists.createenergycannons.content.cannons.magnetic.railgun.MountedEnergyCannonContraption;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CannonUtil {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final BallisticPropertiesComponent AC_FALLBACK = new BallisticPropertiesComponent(-0.025, 0.01, false, 0, 0, 0, 0);

    public static boolean isAutocannonFamily(AbstractMountedCannonContraption cannon) {
        return isAutoCannon(cannon)
                || isRotaryCannon(cannon)
                || isMediumCannon(cannon)
                || isTwinAutocannon(cannon)
                || isHeavyAutocannon(cannon)
                || CBCWPFCompat.isShupapiumAutocannon(cannon);
    }

    public static int getBarrelLength(AbstractMountedCannonContraption cannon) {
        if (cannon == null)
            return 0;
        if(cannon.initialOrientation() == Direction.WEST || cannon.initialOrientation() == Direction.NORTH){
            return getCannonExtensionLength(cannon, "backExtensionLength");
        }
        else{
            return getCannonExtensionLength(cannon, "frontExtensionLength");
        }
    }
    public static Vec3 getCannonMountOffset(Level level, BlockPos pos) {
        return getCannonMountOffset(level.getBlockEntity(pos));
    }

    public static Vec3 getCannonMountOffset(BlockEntity mount) {
        if (mount == null) return Vec3.ZERO;
        return isUp(mount) ? new Vec3(0, 2, 0) : new Vec3(0, -2, 0);
    }

    public static BallisticPropertiesComponent getAutocannonBallistics(AbstractMountedCannonContraption cannon, Level level) {
        if (cannon == null || level == null) return AC_FALLBACK;

        if (CBCWPFCompat.isShupapiumAutocannon(cannon)) {
            BallisticPropertiesComponent bp = CBCWPFCompat.resolveAutocannonBallistics(cannon, level);
            return bp != null ? bp : AC_FALLBACK;
        }

        java.util.function.Function<ItemStack, BallisticPropertiesComponent> fromCBCAmmo = (stack) -> {
            if (stack == null || stack.isEmpty()) return AC_FALLBACK;
            if (!(stack.getItem() instanceof AutocannonAmmoItem ammo)) return BallisticPropertiesComponent.DEFAULT;

            AbstractAutocannonProjectile proj = ammo.getAutocannonProjectile(stack, level);
            if (proj == null) return BallisticPropertiesComponent.DEFAULT;

            return ((AutocannonProjectileAccessor) proj).getBallisticProperties();
        };

        return AC_FALLBACK;
    }

    public static BallisticPropertiesComponent getBallistics(AbstractMountedCannonContraption cannon, ServerLevel level) {
        if (cannon == null || level == null) return BallisticPropertiesComponent.DEFAULT;

        if (isAutocannonFamily(cannon)) {
            return getAutocannonBallistics(cannon, level);
        }

        Map<BlockPos, BlockEntity> presentBlockEntities = cannon.presentBlockEntities;
        for (BlockEntity blockEntity : presentBlockEntities.values()) {
            if (!(blockEntity instanceof IBigCannonBlockEntity cannonBlockEntity)) continue;

            BigCannonBehavior behavior = cannonBlockEntity.cannonBehavior();
            StructureTemplate.StructureBlockInfo containedBlockInfo = behavior.block();
            Block block = containedBlockInfo.state().getBlock();

            if (block instanceof ProjectileBlock<?> projectileBlock) {
                AbstractBigCannonProjectile projectile = projectileBlock.getProjectile(level, Collections.singletonList(containedBlockInfo));
                try {
                    Method method = projectile.getClass().getDeclaredMethod("getBallisticProperties");
                    method.setAccessible(true);
                    BallisticPropertiesComponent bp = (BallisticPropertiesComponent) method.invoke(projectile);
                    return bp != null ? bp : BallisticPropertiesComponent.DEFAULT;
                } catch (Throwable ignored) {
                    return BallisticPropertiesComponent.DEFAULT;
                }
            }
        }

        return BallisticPropertiesComponent.DEFAULT;
    }

    public static float getRotarySpeed( AbstractMountedCannonContraption contraptionEntity) {
        return 0f;
    }

    public static float getMediumCannonSpeed(AbstractMountedCannonContraption contraptionEntity) {
        return 0f;
    }

    public static int getBigCannonSpeed(ServerLevel level, AbstractMountedCannonContraption cannon ,PitchOrientedContraptionEntity contraptionEntity) {
        if(contraptionEntity == null) return 0;

        Map<BlockPos, BlockEntity> presentBlockEntities = cannon.presentBlockEntities;
        int speeed = 0;
        for (BlockEntity blockEntity : presentBlockEntities.values()) {
            if (!(blockEntity instanceof IBigCannonBlockEntity cannonBlockEntity)) continue;
            BigCannonBehavior behavior = cannonBlockEntity.cannonBehavior();
            StructureTemplate.StructureBlockInfo containedBlockInfo = behavior.block();

            Block block = containedBlockInfo.state().getBlock();
            if (block instanceof BigCannonPropellantBlock propellantBlock) {
                speeed += (int) propellantBlock.getChargePower(containedBlockInfo);
            } else if (block instanceof ProjectileBlock<?> projectileBlock) {
                AbstractBigCannonProjectile projectile = projectileBlock.getProjectile(level, Collections.singletonList(containedBlockInfo));
                speeed += (int) projectile.addedChargePower();
            }
        }
        return speeed;
    }

    public static float getInitialVelocity(AbstractMountedCannonContraption cannon, ServerLevel level) {
        LOGGER.debug("→ getInitialVelocity for contraption={} mods: BigCannon={}, AutoCannon={}, Rotary={}, Medium={}, Energy={}",
                cannon != null ? cannon.getClass().getSimpleName() : "null",
                isBigCannon(cannon), isAutoCannon(cannon),
                isRotaryCannon(cannon), isMediumCannon(cannon), isEnergyCannon(cannon)
        );
        if (cannon == null) return 0f;

        if (CBCWPFCompat.isShupapiumAutocannon(cannon)) {
            LOGGER.debug("   • Shupapium WPF muzzle speed = {}", CBCWPFCompat.resolveShupapiumMuzzleSpeed(cannon));
            return CBCWPFCompat.resolveShupapiumMuzzleSpeed(cannon);
        }

        if (isEnergyCannon(cannon)) {
            float velocity = ((MountedEnergyCannonContraption) cannon).getMuzzleVelocity(level);
            LOGGER.debug("   • EnergyCannon speed = {}", velocity);
            return velocity;
        }

        if (isBigCannon(cannon)) {
            LOGGER.debug("   • BigCannon speed = {}", getBigCannonSpeed(level,cannon, (PitchOrientedContraptionEntity)cannon.entity));
            return getBigCannonSpeed(level, cannon ,(PitchOrientedContraptionEntity)cannon.entity);
        } else if (isAutoCannon(cannon)) {
            LOGGER.debug("   • AutoCannon speed = {}", getAutoCannonSpeed(cannon));
            return getAutoCannonSpeed(cannon);
        }
        else if(isRotaryCannon(cannon)){
            LOGGER.debug("   • RotaryCannon speed = {}", getRotarySpeed(cannon));
            return getRotarySpeed(cannon);
        }
        else if(isMediumCannon(cannon)){
            LOGGER.debug("   • MediumCannon speed = {}", getMediumCannonSpeed(cannon));
            return getMediumCannonSpeed(cannon);
        } else if(isTwinAutocannon(cannon)){
            LOGGER.debug("   • TwinACannon speed = {}", getAutoCannonSpeed(cannon));
            return getAutoCannonSpeed(cannon);
        } else if(isHeavyAutocannon(cannon)){
            LOGGER.debug("   • HeavyACannon speed = {}", getAutoCannonSpeed(cannon));
            return getAutoCannonSpeed(cannon);
        }
        LOGGER.debug("   • No known cannon type → returning 0");
        return 0;
    }

    public static int getAutocannonLifetimeTicks(AbstractMountedCannonContraption cannon) {
        if (cannon == null) return 100;

        if (CBCWPFCompat.isShupapiumAutocannon(cannon)) {
            int t = CBCWPFCompat.resolveLifetimeTicks(cannon);
            return t > 0 ? t : 100;
        }

        // Only CBC autocannon contraptions have this accessor reliably
        if (!isAutoCannon(cannon)) {
            return 100;
        }

        try {
            AutocannonMaterial mat = getAutocannonMaterial(cannon);
            if (mat != null) {
                int t = mat.properties().projectileLifetime();
                if (t > 0) return t;
            }
        } catch (Throwable ignored) {
            LOGGER.debug("Mixin maybe didnt apply?");
        }

        return 100;
    }

    public static double getMaxProjectileRangeBlocks(AbstractMountedCannonContraption cannon, ServerLevel level) {
        if (cannon == null || level == null) return 0;

        double speed = getInitialVelocity(cannon, level);
        if (speed <= 0) return 0;

        // lifetime
        int lifeTicks = getAutocannonLifetimeTicks(cannon);
        if (lifeTicks <= 0) return 0;

        if (isAutocannonFamily(cannon)) {
            BallisticPropertiesComponent bp = getAutocannonBallistics(cannon, level);

            if (bp.isQuadraticDrag()) {
                return speed * lifeTicks; // generous upper bound
            }

            double drag = Math.max(0.0, Math.min(0.25, bp.drag()));
            double retained = Math.pow(1.0 - drag, lifeTicks);
            double avg = (1.0 + retained) * 0.5;
            return speed * lifeTicks * avg;
        }

        // Big cannon path (your existing approximation)
        double drag = getProjectileDrag(cannon, level);
        drag = Math.max(0.0, Math.min(0.25, drag));

        double retained = Math.pow(1.0 - drag, lifeTicks);
        double avg = (1.0 + retained) * 0.5;

        return speed * lifeTicks * avg;
    }

    public static double getProjectileGravity(AbstractMountedCannonContraption cannon, ServerLevel level) {
        if (isAutocannonFamily(cannon)) {
            return getAutocannonBallistics(cannon, level).gravity();
        }
        Map<BlockPos, BlockEntity> presentBlockEntities = cannon.presentBlockEntities;
        for (BlockEntity blockEntity : presentBlockEntities.values()) {
            if (!(blockEntity instanceof IBigCannonBlockEntity cannonBlockEntity)) continue;
            BigCannonBehavior behavior = cannonBlockEntity.cannonBehavior();
            StructureTemplate.StructureBlockInfo containedBlockInfo = behavior.block();

            Block block = containedBlockInfo.state().getBlock();
            if (block instanceof ProjectileBlock<?> projectileBlock) {
                AbstractBigCannonProjectile projectile = projectileBlock.getProjectile(level, Collections.singletonList(containedBlockInfo));
                BallisticPropertiesComponent ballisticProperties;
                try {

                    Method method = projectile.getClass().getDeclaredMethod("getBallisticProperties");
                    method.setAccessible(true);
                    ballisticProperties = (BallisticPropertiesComponent) method.invoke(projectile);
                } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException |
                         ClassCastException e) {
                    return 0.05;
                }
                return ballisticProperties.gravity();
            }
        }
        return 0.05;
    }

    public static double getProjectileDrag(AbstractMountedCannonContraption cannon, ServerLevel level) {
        Map<BlockPos, BlockEntity> presentBlockEntities = cannon.presentBlockEntities;
        double drag = 0.01;

        if (isAutocannonFamily(cannon)) {
            return getAutocannonBallistics(cannon, level).drag();
        }

        for (BlockEntity blockEntity : presentBlockEntities.values()) {
            if (!(blockEntity instanceof IBigCannonBlockEntity cannonBlockEntity)) continue;

            BigCannonBehavior behavior = cannonBlockEntity.cannonBehavior();
            StructureTemplate.StructureBlockInfo containedBlockInfo = behavior.block();

            Block block = containedBlockInfo.state().getBlock();
            if (block instanceof ProjectileBlock<?> projectileBlock) {
                AbstractBigCannonProjectile projectile = projectileBlock.getProjectile(level, Collections.singletonList(containedBlockInfo));
                try {
                    Method method = projectile.getClass().getDeclaredMethod("getBallisticProperties");
                    method.setAccessible(true);
                    BallisticPropertiesComponent bp = (BallisticPropertiesComponent) method.invoke(projectile);
                    if (bp != null) drag = bp.drag();
                } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | ClassCastException e) {
                    return drag;
                }
            }
        }
        return drag;
    }

    public static boolean isHeavyAutocannon(AbstractMountedCannonContraption cannon) {
        return false;
    }

    public static boolean isTwinAutocannon(AbstractMountedCannonContraption cannon) {
        return false;
    }

    public static boolean isBigCannon(AbstractMountedCannonContraption cannon) {
        return cannon instanceof MountedBigCannonContraption;
    }

    public static boolean isAutoCannon(AbstractMountedCannonContraption cannon) {
        return cannon instanceof MountedAutocannonContraption;
    }
    public static boolean isRotaryCannon(AbstractMountedCannonContraption cannonContraption){
        return false;
    }
    public static boolean isMediumCannon(AbstractMountedCannonContraption cannonContraption){
        return false;
    }

    public static boolean isEnergyCannon(AbstractMountedCannonContraption cannonContraption){
        if(!Mods.CREATEENERGYCANNONS.isLoaded()) return false;
        return cannonContraption instanceof MountedEnergyCannonContraption;
    }

    public static boolean isLaserCannon(AbstractMountedCannonContraption cannonContraption){
        if(!Mods.CREATEENERGYCANNONS.isLoaded()) return false;
        return cannonContraption != null && cannonContraption.getClass().getSimpleName().equals("MountedLaserCannonContraption");
    }


    public static boolean isCannonReadyToFire(CannonMountBlockEntity mount) {
        if (mount == null) return false;

        if (Mods.CREATEENERGYCANNONS.isLoaded() && mount instanceof net.arsenalists.createenergycannons.content.energymount.EnergyCannonMountBlockEntity energyMount) {
            return energyMount.isReadyToFire();
        }

        // Regular cannons are always ready
        return true;
    }

    private static float getAutoCannonSpeed(AbstractMountedCannonContraption cannon) {
        AutocannonMaterial cann = getAutocannonMaterial(cannon);
        if (cann == null) return 0f;
        var props = cann.properties();

        Predicate<BlockEntity> isBarrel =
                e -> e instanceof IAutocannonBlockEntity;

        float speed = props.baseSpeed();
        BlockPos pos = cannon.getStartPos().relative(cannon.initialOrientation());
        int count = 0;

        while (true) {
            BlockEntity be = cannon.presentBlockEntities.get(pos);
            if (be == null || !isBarrel.test(be)) break;

            count++;
            if (count <= props.maxSpeedIncreases())  speed += props.speedIncreasePerBarrel();
            if (count >  props.maxBarrelLength())    break;

            pos = pos.relative(cannon.initialOrientation());
        }

        return speed;
    }

    private static AutocannonMaterial getAutocannonMaterial(AbstractMountedCannonContraption cannon) {
        if (cannon == null) return null;
        try {
            Field field = MountedAutocannonContraption.class.getDeclaredField("cannonMaterial");
            field.setAccessible(true);
            Object value = field.get(cannon);
            return value instanceof AutocannonMaterial material ? material : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("[RADAR-CBC] Could not read autocannon material from {}: {}",
                    cannon.getClass().getName(), e.toString());
            return null;
        }
    }

    private static int getCannonExtensionLength(AbstractMountedCannonContraption cannon, String fieldName) {
        try {
            Field field = AbstractMountedCannonContraption.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(cannon);
            if (value instanceof Number number) {
                return Math.max(0, number.intValue());
            }
            LOGGER.warn("[RADAR-CBC] Cannon extension field {} on {} was not numeric: {}",
                    fieldName, cannon.getClass().getName(), value);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("[RADAR-CBC] Could not read cannon extension {} from {}: {}",
                    fieldName, cannon.getClass().getName(), e.toString());
        }
        return 0;
    }


    public static boolean isUp(Level level , Vec3 mountPos){
        BlockEntity blockEntity =  level.getBlockEntity(new BlockPos( (int) mountPos.x, (int) mountPos.y, (int) mountPos.z));
        return isUp(blockEntity);
    }

    public static boolean isUp(BlockEntity blockEntity) {
        if(!(blockEntity instanceof CannonMountBlockEntity cannonMountBlockEntity)) return true;
        if(cannonMountBlockEntity.getContraption() == null) return true;
        return !(cannonMountBlockEntity.getContraption().position().y < blockEntity.getBlockPos().getY());
    }

}
