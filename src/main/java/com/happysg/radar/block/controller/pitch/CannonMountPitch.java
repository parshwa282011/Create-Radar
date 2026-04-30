package com.happysg.radar.block.controller.pitch;

import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.compat.cbc.CannonTargeting;
import com.happysg.radar.compat.cbc.CannonUtil;
import com.happysg.radar.compat.aeronautics.PhysicsHandler;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.List;

public class CannonMountPitch {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final AutoPitchControllerBlockEntity controller;

    public CannonMountPitch(AutoPitchControllerBlockEntity controller) {
        this.controller = controller;
    }

    public void tick(CannonMountBlockEntity mount) {
        rotateCBC(mount);
    }

    public void setTarget(CannonMountBlockEntity mount, Vec3 targetPos) {
        setTargetCBC(mount, targetPos);
    }

    public boolean atTargetPitch(CannonMountBlockEntity mount, boolean lag) {
        PitchOrientedContraptionEntity contraption = mount.getContraption();
        if (contraption == null) {
            return false;
        }

        if (!(contraption.getContraption() instanceof AbstractMountedCannonContraption cannonContraption)) {
            return false;
        }

        double tol = AutoPitchControllerBlockEntity.getCbcTolerance();
        if (!lag) {
            tol += 0.15;
        }

        double currentPitch = contraption.pitch;
        int invert = -cannonContraption.initialOrientation().getStepX() + cannonContraption.initialOrientation().getStepZ();
        currentPitch = currentPitch * -invert;

        double targetPitch = clampToCbcPitchLimits(mount, controller.getTargetAngle());
        return Math.abs(currentPitch - targetPitch) < tol;
    }

    public double getMaxEngagementRangeBlocks(CannonMountBlockEntity mount, ServerLevel sl) {
        PitchOrientedContraptionEntity ce = mount.getContraption();
        if (ce == null) {
            return 0;
        }
        if (!(ce.getContraption() instanceof AbstractMountedCannonContraption cannon)) {
            return 0;
        }

        double r = CannonUtil.getMaxProjectileRangeBlocks(cannon, sl);
        LOGGER.debug("RANGE DBG endpoint={} cannon={} range={} blocks", controller.getBlockPos(), cannon.getClass().getSimpleName(), r);
        return r;
    }

    public boolean canEngageTrack(CannonMountBlockEntity mount, @Nullable RadarTrack track, boolean requireLos, ServerLevel sl) {
        if (track == null) {
            return false;
        }
        if (controller.firingControl == null) {
            return false;
        }
        if (mount.getContraption() == null) {
            return false;
        }
        if (!(mount.getContraption().getContraption() instanceof AbstractMountedCannonContraption)) {
            return false;
        }

        Vec3 p = track.position();
        if (p == null) {
            return false;
        }

        double max = controller.getMaxEngagementRangeBlocks();
        if (max > 0.0) {
            Vec3 start = controller.firingControl.getCannonRayStart();
            if (start.distanceToSqr(p) > (max * max)) {
                return false;
            }
        }

        Vec3 origin = controller.getRayStart();
        List<Double> pitches = CannonTargeting.calculatePitch(mount, origin, p, sl);
        if (pitches == null || pitches.isEmpty()) {
            return false;
        }

        return controller.firingControl.hasLineOfSightTo(track, requireLos);
    }

    private void rotateCBC(CannonMountBlockEntity mount) {
        if (!controller.isRunningController()) {
            LOGGER.debug("PITCH.rotateCBC aborted: isRunning=false");
            return;
        }

        PitchOrientedContraptionEntity contraption = mount.getContraption();
        if (contraption == null) {
            return;
        }

        if (!(contraption.getContraption() instanceof AbstractMountedCannonContraption cannonContraption)) {
            return;
        }

        double currentPitch = contraption.pitch;
        int invert = -cannonContraption.initialOrientation().getStepX() + cannonContraption.initialOrientation().getStepZ();
        currentPitch = currentPitch * -invert;

        double targetPitch = clampToCbcPitchLimits(mount, controller.getTargetAngle());
        double diff = targetPitch - currentPitch;

        double nearDeadbandDeg = AutoPitchControllerBlockEntity.getCbcTolerance();
        if (controller.firingControl != null) {
            Vec3 muzzle = controller.firingControl.getCannonRayStart();
            Vec3 target = controller.getLastTargetPos();
            if (target != null) {
                double dist = muzzle.distanceTo(target);
                if (dist <= 10.0) {
                    nearDeadbandDeg = 6.0;
                }
            }
        }

        LOGGER.debug(
                "PITCH.rotateCBC current={} target={} diff={} speed={} deadband={}",
                currentPitch,
                targetPitch,
                diff,
                controller.getSpeed(),
                nearDeadbandDeg
        );

        if (Math.abs(diff) <= nearDeadbandDeg) {
            mount.setPitch((float) targetPitch);
            mount.notifyUpdate();
            return;
        }

        double rpm = Math.abs(controller.getSpeed());
        if (rpm <= 0.0) {
            return;
        }



        double stepDeg = rpm / 24.0;
        double move = Math.signum(diff) * Math.min(Math.abs(diff), stepDeg);
        double nextCtl = currentPitch + move;

        mount.setPitch((float) nextCtl);
        mount.notifyUpdate();
    }

    private void setTargetCBC(CannonMountBlockEntity mount, Vec3 targetPos) {
        if (!(controller.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        Vec3 origin = controller.getRayStart();
        List<Double> angles = CannonTargeting.calculatePitch(mount, origin, targetPos, serverLevel);

        LOGGER.debug("PITCH.solve origin={} target={} mountPos={}", origin, targetPos, mount.getBlockPos());
        controller.setLastTargetPos(targetPos);

        if (angles == null || angles.isEmpty()) {
            LOGGER.debug("PITCH.solve FAILED: no pitch roots");
            controller.setRunning(false);
            return;
        }

        Double targetAngle = chooseReachablePitch(mount, angles);
        if (targetAngle == null) {
            LOGGER.debug("PITCH.solve FAILED: roots outside mount limits angles={} limits={}", angles, describeCbcPitchLimits(mount));
            controller.setRunning(false);
            return;
        }

        controller.setInternalTargetAngle(targetAngle);
        LOGGER.debug("PITCH.solve targetAngle={} rawAngles={} limits={}", controller.getTargetAngle(), angles, describeCbcPitchLimits(mount));

        controller.setRunning(true);
        controller.notifyUpdate();
        controller.setChanged();
    }

    @Nullable
    private Double chooseReachablePitch(CannonMountBlockEntity mount, List<Double> angles) {
        double min = getCbcMinPitch(mount);
        double max = getCbcMaxPitch(mount);
        Double firstReachable = null;

        for (Double angle : angles) {
            if (angle == null || !Double.isFinite(angle)) {
                continue;
            }
            if (angle >= min && angle <= max) {
                if (!controller.isArtillery()) {
                    return angle;
                }
                firstReachable = angle;
            }
        }

        if (controller.isArtillery() && firstReachable != null) {
            return firstReachable;
        }

        return null;
    }

    private double clampToCbcPitchLimits(CannonMountBlockEntity mount, double pitch) {
        return Math.max(getCbcMinPitch(mount), Math.min(getCbcMaxPitch(mount), pitch));
    }

    private double getCbcMinPitch(CannonMountBlockEntity mount) {
        return Math.max(controller.getMinAngleDeg(), -invokeCbcPitchLimit(mount, "getMaxDepress", 45.0));
    }

    private double getCbcMaxPitch(CannonMountBlockEntity mount) {
        return Math.min(controller.getMaxAngleDeg(), invokeCbcPitchLimit(mount, "getMaxElevate", 60.0));
    }

    private double invokeCbcPitchLimit(CannonMountBlockEntity mount, String methodName, double fallback) {
        try {
            Method method = CannonMountBlockEntity.class.getDeclaredMethod(methodName);
            method.setAccessible(true);
            Object value = method.invoke(mount);
            if (value instanceof Number number) {
                return Math.max(0.0, number.doubleValue());
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("PITCH.limit failed method={} mount={} err={}", methodName, mount.getBlockPos(), e.toString());
        }
        return fallback;
    }

    private String describeCbcPitchLimits(CannonMountBlockEntity mount) {
        return "[" + getCbcMinPitch(mount) + ", " + getCbcMaxPitch(mount) + "]";
    }
}
