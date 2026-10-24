package gamerguy11.sixtoolsaddon.modules.autowasp;

import gamerguy11.sixtoolsaddon.modules.autowasp.AutoWaspLogic.*;
import gamerguy11.sixtoolsaddon.modules.autowasp.AutoWaspGlide.*;
import gamerguy11.sixtoolsaddon.modules.autowasp.AutoWaspPathing.*;
import gamerguy11.sixtoolsaddon.modules.SixToolsAutoWasp;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.utils.player.Rotations;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.entity.EntityPosition;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class AutoWaspFlightController {
    public static final int STUCK_REPATH_TICKS = 12;

    private static final double FLOOR_CLEARANCE = 2.2;
    private static final double CEILING_CLEARANCE = 1.5;
    private static final int SAFETY_SCAN = 14;
    private static final double OBSTACLE_LOOK_AHEAD = 7.5;
    private static final double AVOIDANCE_STRENGTH = 0.85;
    private static final double COLLISION_STEP = 0.55;
    private static final double NETHER_MAX_FLIGHT_Y = 123.0;
    private static final double AVOIDANCE_REFERENCE_SPEED = 2.0;
    private static final double VERTICAL_EPSILON = 1.0E-3;
    private static final int GLIDE_STEER_PRIORITY = Integer.MIN_VALUE;

    private final SixToolsAutoWasp module;
    private final AutoWaspPathfinder pathfinder;
    private final AutoWaspGlideTracker glide;
    private final AutoWaspRamp horizontalRamp = new AutoWaspRamp(new AutoWaspRamp.Settings(AutoWaspSpeed.HORIZONTAL_START,
        AutoWaspSpeed.HORIZONTAL_TOP, .3, 15, 1, AutoWaspSpeed.BUNCHED_SAFE));
    private final AutoWaspRamp verticalRamp = new AutoWaspRamp(new AutoWaspRamp.Settings(AutoWaspSpeed.VERTICAL_START,
        AutoWaspSpeed.VERTICAL_TOP, AutoWaspSpeed.VERTICAL_STEP, 15, 1, AutoWaspSpeed.VERTICAL_TOP));
    private final AutoWaspSwapDelay swapDelay = new AutoWaspSwapDelay();
    private final AutoWaspTakeoff takeoff = new AutoWaspTakeoff();
    private final AutoWaspSetbacks setbacks = new AutoWaspSetbacks();
    private final AutoWaspSpeed.Limit3d limit3d = new AutoWaspSpeed.Limit3d();

    private Vec3d lastPlayerPos;
    private int stuckTicks;
    private double adaptiveHorizontalCap = -1;
    private boolean controlling;
    private Vec3d lastControlledPosition;
    private Vec3d moveStart;
    private Vec3d lastOutput = Vec3d.ZERO;
    private int verticalDirection;
    private long rampTick = Long.MIN_VALUE;
    private long clientTicks;
    private String state = "Waiting";

    public AutoWaspFlightController(SixToolsAutoWasp module, AutoWaspPathfinder pathfinder) {
        this.module = module;
        this.pathfinder = pathfinder;
        this.glide = new AutoWaspGlideTracker(module);
    }

    public void onDeactivate() {
        lastPlayerPos = null;
        stuckTicks = 0;
        adaptiveHorizontalCap = -1;
        resetControl();
    }

    public void resetNavigationState() {
        lastPlayerPos = module.client().player != null ? module.client().player.getEntityPos() : null;
        stuckTicks = 0;
        adaptiveHorizontalCap = -1;
        resetControl();
    }

    public void resetFlightTracking() {
        pathfinder.resetPathState();
        stuckTicks = 0;
        adaptiveHorizontalCap = -1;
        lastPlayerPos = module.client().player != null ? module.client().player.getEntityPos() : null;
        releaseControl();
        state = "Waiting";
    }

    public void clearStuckState() {
        stuckTicks = 0;
        lastPlayerPos = module.client().player != null ? module.client().player.getEntityPos() : null;
    }

    public void onReceive(Packet<?> packet) {
        glide.onReceive(packet);
    }

    public void onSend(Packet<?> packet) {
        glide.onSend(packet);
    }

    public void elytraSwapped() {
        swapDelay.swapped();
    }

    public void preTick(boolean usableElytra) {
        ClientPlayerEntity player = module.client().player;
        clientTicks++;
        setbacks.tick();
        consumeCorrections();
        glide.preTick();
        if (player != null) swapDelay.observe(clientTicks, usableElytra, player.isGliding());
        if (!controlling) releaseControl();
        controlling = false;
    }

    public void postTick() {
        ClientPlayerEntity player = module.client().player;
        if (controlling && player != null) {
            lastControlledPosition = player.getEntityPos();
            limitRampsToCollision(player);
        }
        moveStart = null;
        glide.postTick();
    }

    public void handleNotGliding() {
        ClientPlayerEntity player = module.client().player;
        if (player == null || module.client().world == null) return;

        boolean inLiquid = player.isTouchingWater() || player.isInLava();
        switch (takeoff.next(clientTicks, player.isOnGround(), player.isGliding(), player.getVelocity().y,
            inLiquid)) {
            case JUMP -> player.jump();
            case OPEN -> {
                player.setJumping(false);
                player.setSprinting(true);
                if (!player.isOnGround() && !player.isGliding() && module.client().getNetworkHandler() != null) {
                    module.client().getNetworkHandler().sendPacket(new ClientCommandC2SPacket(player,
                        ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                }
            }
            case WAIT -> {}
        }

        stuckTicks = 0;
        lastPlayerPos = player.getEntityPos();
        adaptiveHorizontalCap = -1;
        releaseControl();
        state = "Taking off";
    }

    public void onGlidingTick() {
        updateStuckState();
    }

    public int getStuckTicks() {
        return stuckTicks;
    }

    public String state() {
        return state;
    }

    public void onMove(PlayerMoveEvent event, Vec3d steerTarget, Vec3d targetVelocity) {
        ClientPlayerEntity player = module.client().player;
        if (player == null || module.client().world == null || steerTarget == null) return;
        controlling = true;

        if (setbacks.pinned()) {
            deferToVanilla(player, "Unpinning");
            return;
        }
        if (!swapDelay.ready()) {
            deferToVanilla(player, "Settling glide");
            return;
        }

        Vec3d position = player.getEntityPos();
        Vec3d safeTarget = pathfinder.adjustToSafeCorridor(steerTarget, position);
        Vec3d toTarget = safeTarget.subtract(position);
        Vec3d lead = targetVelocity == null ? Vec3d.ZERO : targetVelocity;
        updateRamps(toTarget.y + lead.y);

        double maxHorizontal = Math.min(resolveHorizontalCap(), horizontalRamp.speed());
        double maxVertical = Math.min(module.verticalMaxSpeed(),
            verticalRamp.engaged() ? verticalRamp.speed() : AutoWaspSpeed.VERTICAL_START);
        double horizontal = toTarget.horizontalLength();
        double speed = Math.min(maxHorizontal, horizontal);
        double xVel = 0;
        double zVel = 0;
        if (horizontal > 1.0E-5) {
            xVel = (toTarget.x / horizontal) * speed;
            zVel = (toTarget.z / horizontal) * speed;
        }
        double yVel = MathHelper.clamp(toTarget.y, -maxVertical, maxVertical);
        Vec3d desired = AutoWaspSpeed.capHorizontal(new Vec3d(xVel, yVel, zVel).add(lead), maxHorizontal);

        Vec3d base = new Vec3d(desired.x,
            applyVerticalSafety(MathHelper.clamp(desired.y, -maxVertical, maxVertical), position), desired.z);
        Vec3d avoid = computeLocalAvoidance(safeTarget, base, Math.min(maxHorizontal, AVOIDANCE_REFERENCE_SPEED));
        Vec3d velocity = applyDimensionFlightLimit(applyEmergencyBraking(base.add(avoid)), position);
        velocity = AutoWaspSpeed.capHorizontal(velocity, maxHorizontal);
        velocity = new Vec3d(velocity.x, MathHelper.clamp(velocity.y, -maxVertical, maxVertical), velocity.z);
        if (limit3d.active()) velocity = AutoWaspSpeed.capLength(velocity, module.max3dSpeed());
        velocity = guardServerGlide(player, velocity);
        if (limit3d.active() && state.equals("Pursuing")) state = "3D limited";

        ((IVec3d) event.movement).meteor$set(velocity.x, velocity.y, velocity.z);
        moveStart = position;
        lastOutput = velocity;
    }

    private void updateRamps(double verticalWanted) {
        if (rampTick == clientTicks) return;
        rampTick = clientTicks;
        configureRamps();
        if (horizontalRamp.engaged()) horizontalRamp.advance();
        else horizontalRamp.engage(lastOutput.horizontalLength());
        int direction = verticalWanted > VERTICAL_EPSILON ? 1 : verticalWanted < -VERTICAL_EPSILON ? -1 : 0;
        if (direction == 0) verticalRamp.disengage();
        else if (direction == verticalDirection && verticalRamp.engaged()) verticalRamp.advance();
        else verticalRamp.engage(lastOutput.y * direction > 0 ? Math.abs(lastOutput.y) : 0);
        verticalDirection = direction;
        if (limit3d.update(verticalWanted)) AutoWaspSpeed.holdRampsTo(horizontalRamp, verticalRamp, module.max3dSpeed());
    }

    private void limitRampsToCollision(ClientPlayerEntity player) {
        if (moveStart == null) return;
        Vec3d moved = player.getEntityPos().subtract(moveStart);
        if (player.horizontalCollision) horizontalRamp.limitTo(moved.horizontalLength());
        if (player.verticalCollision) verticalRamp.limitTo(Math.abs(moved.y));
    }

    private void consumeCorrections() {
        int count = glide.takeCorrections();
        ClientPlayerEntity player = module.client().player;
        if (count == 0 || !controlling || player == null || !player.isGliding()) return;
        PlayerPositionLookS2CPacket packet = glide.latestSetback();
        Vec3d target = packet == null || lastControlledPosition == null ? player.getEntityPos()
            : EntityPosition.apply(new EntityPosition(lastControlledPosition,
                player.getVelocity(), player.getYaw(), player.getPitch()),
                packet.change(), packet.relatives()).position();
        switch (setbacks.classify(clientTicks, target, lastControlledPosition)) {
            case TELEPORT -> {
                disengageRamps();
                lastOutput = Vec3d.ZERO;
                pathfinder.resetPathState();
                clearStuckState();
            }
            case PINNED -> {
                horizontalRamp.corrected();
                verticalRamp.corrected();
                if (module.pinnedGlideTicksValue() > 0) {
                    setbacks.pin(module.pinnedGlideTicksValue());
                    disengageRamps();
                }
            }
            case SETBACK -> {
                horizontalRamp.corrected();
                verticalRamp.corrected();
            }
        }
        lastPlayerPos = player.getEntityPos();
    }

    private Vec3d guardServerGlide(ClientPlayerEntity player, Vec3d velocity) {
        AutoWaspServerGlide.Context context = glide.context(player.getPitch(), player.getYaw());
        if (context == null) return velocity;
        Box body = player.getBoundingBox();
        if (module.steerServerGlideValue()) {
            AutoWaspServerGlide.Rotation steer = AutoWaspServerGlide.steer(context, body, glide::chunkLoaded);
            if (steer != null) {
                Rotations.rotate(steer.yaw(), steer.pitch(), GLIDE_STEER_PRIORITY);
                context = glide.context(steer.pitch(), steer.yaw());
            }
        }
        if (!module.serverGlideGuardValue() || context == null) {
            state = "Pursuing";
            return velocity;
        }
        AutoWaspServerGlide.Clearance clearance = AutoWaspServerGlide.clear(context, body, velocity, glide::chunkLoaded);
        state = clearance.limited() ? "Glide clearance" : "Pursuing";
        return clearance.motion();
    }

    private void deferToVanilla(ClientPlayerEntity player, String why) {
        disengageRamps();
        moveStart = null;
        lastOutput = player.getVelocity();
        state = why;
    }

    private void releaseControl() {
        disengageRamps();
        moveStart = null;
        ClientPlayerEntity player = module.client().player;
        lastOutput = player == null ? Vec3d.ZERO : player.getVelocity();
    }

    private void disengageRamps() {
        horizontalRamp.disengage();
        verticalRamp.disengage();
        verticalDirection = 0;
        limit3d.reset();
    }

    private void resetControl() {
        horizontalRamp.reset();
        verticalRamp.reset();
        verticalDirection = 0;
        limit3d.reset();
        rampTick = Long.MIN_VALUE;
        swapDelay.reset();
        takeoff.reset();
        setbacks.reset();
        glide.reset();
        controlling = false;
        lastControlledPosition = null;
        moveStart = null;
        lastOutput = Vec3d.ZERO;
        clientTicks = 0;
        state = "Waiting";
    }

    private void configureRamps() {
        int rewind = module.setbackRewindTicksValue();
        int hold = module.setbackHoldTicksValue();
        horizontalRamp.configure(new AutoWaspRamp.Settings(AutoWaspSpeed.HORIZONTAL_START, AutoWaspSpeed.HORIZONTAL_TOP,
            module.rampStepValue(), rewind, hold, module.horizontalMaxSpeed()));
        verticalRamp.configure(new AutoWaspRamp.Settings(AutoWaspSpeed.VERTICAL_START, AutoWaspSpeed.VERTICAL_TOP,
            AutoWaspSpeed.VERTICAL_STEP, rewind, hold, module.verticalMaxSpeed()));
    }

    private void updateStuckState() {
        if (module.client().player == null) return;

        Vec3d now = module.client().player.getEntityPos();
        if (lastPlayerPos == null) {
            lastPlayerPos = now;
            stuckTicks = 0;
            return;
        }

        if (now.squaredDistanceTo(lastPlayerPos) < 0.03) stuckTicks++;
        else stuckTicks = 0;
        lastPlayerPos = now;
    }

    private double resolveHorizontalCap() {
        double baseCap = module.horizontalMaxSpeed();
        boolean drag = isInDragState();
        if (!drag) {
            adaptiveHorizontalCap = baseCap;
            return baseCap;
        }

        double actual = module.client().player.getVelocity().horizontalLength();
        if (adaptiveHorizontalCap < 0 || adaptiveHorizontalCap > baseCap) adaptiveHorizontalCap = baseCap;

        double targetCap = Math.min(baseCap, Math.max(0.18, actual * 1.18 + 0.05));
        adaptiveHorizontalCap += (targetCap - adaptiveHorizontalCap) * 0.35;
        adaptiveHorizontalCap = MathHelper.clamp(adaptiveHorizontalCap, 0.14, baseCap);
        return adaptiveHorizontalCap;
    }

    private boolean isInDragState() {
        return module.client().player != null && isInsideSoftDragBlock(module.client().player.getBoundingBox());
    }

    private boolean isInsideSoftDragBlock(Box box) {
        int minX = MathHelper.floor(box.minX + 1.0E-4);
        int maxX = MathHelper.floor(box.maxX - 1.0E-4);
        int minY = MathHelper.floor(box.minY + 1.0E-4);
        int maxY = MathHelper.floor(box.maxY - 1.0E-4);
        int minZ = MathHelper.floor(box.minZ + 1.0E-4);
        int maxZ = MathHelper.floor(box.maxZ - 1.0E-4);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (isSoftDragBlock(new BlockPos(x, y, z))) return true;
                }
            }
        }

        return false;
    }

    private boolean isSoftDragBlock(BlockPos pos) {
        var state = module.client().world.getBlockState(pos);
        return state.isOf(Blocks.COBWEB)
            || state.isOf(Blocks.VINE)
            || state.isOf(Blocks.WEEPING_VINES)
            || state.isOf(Blocks.WEEPING_VINES_PLANT)
            || state.isOf(Blocks.TWISTING_VINES)
            || state.isOf(Blocks.TWISTING_VINES_PLANT)
            || state.isOf(Blocks.CAVE_VINES)
            || state.isOf(Blocks.CAVE_VINES_PLANT);
    }

    private double applyVerticalSafety(double yVel, Vec3d currentPos) {
        double floorDist = pathfinder.distanceToSolidBelow(currentPos, SAFETY_SCAN);
        double ceilingDist = pathfinder.distanceToSolidAbove(currentPos, SAFETY_SCAN);

        double downBudget = Math.max(0.0, floorDist - FLOOR_CLEARANCE - 0.35);
        double safeMaxDown = Math.min(module.verticalMaxSpeed(), downBudget * 0.8);
        yVel = Math.max(yVel, -safeMaxDown);

        if (floorDist < FLOOR_CLEARANCE + 0.35) yVel = Math.max(yVel, 0.12);
        if (ceilingDist < CEILING_CLEARANCE + 0.25) yVel = Math.min(yVel, -0.08);
        return yVel;
    }

    private Vec3d applyDimensionFlightLimit(Vec3d velocity, Vec3d currentPos) {
        if (!pathfinder.hasNetherFlightCap()) return velocity;

        double y = currentPos.y;
        double vy = velocity.y;
        if (y >= NETHER_MAX_FLIGHT_Y) vy = Math.min(vy, -0.10);
        else if (y >= NETHER_MAX_FLIGHT_Y - 0.75) vy = Math.min(vy, 0.0);
        else if (y >= NETHER_MAX_FLIGHT_Y - 1.5) vy = Math.min(vy, 0.08);

        return new Vec3d(velocity.x, vy, velocity.z);
    }

    private Vec3d applyEmergencyBraking(Vec3d velocity) {
        if (module.client().player == null) return velocity;

        double horizontal = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        if (horizontal < 1.0E-4) return velocity;

        Vec3d pos = module.client().player.getEntityPos();
        Vec3d dir = new Vec3d(velocity.x / horizontal, 0, velocity.z / horizontal);
        Vec3d left = new Vec3d(-dir.z, 0, dir.x);
        Vec3d right = left.multiply(-1);

        double checkDistance = Math.max(1.5, Math.min(OBSTACLE_LOOK_AHEAD + 1.0, horizontal * 4.2));
        double clearAhead = probeClearDistance(pos, dir, checkDistance);
        if (clearAhead > checkDistance * 0.88) return velocity;

        double clearLeft = probeClearDistance(pos, left, checkDistance * 0.9);
        double clearRight = probeClearDistance(pos, right, checkDistance * 0.9);
        double clearUpLeft = probeClearDistance(pos, left.add(0, 0.42, 0).normalize(), checkDistance * 0.85);
        double clearUpRight = probeClearDistance(pos, right.add(0, 0.42, 0).normalize(), checkDistance * 0.85);

        Vec3d bestEscape = dir;
        double bestEscapeClear = clearAhead;
        if (clearLeft > bestEscapeClear) {
            bestEscape = left;
            bestEscapeClear = clearLeft;
        }
        if (clearRight > bestEscapeClear) {
            bestEscape = right;
            bestEscapeClear = clearRight;
        }
        if (clearUpLeft > bestEscapeClear) {
            bestEscape = left.add(0, 0.42, 0).normalize();
            bestEscapeClear = clearUpLeft;
        }
        if (clearUpRight > bestEscapeClear) {
            bestEscape = right.add(0, 0.42, 0).normalize();
            bestEscapeClear = clearUpRight;
        }

        double speedFactor = MathHelper.clamp(clearAhead / checkDistance, 0.35, 1.0);
        double desiredHorizontal = horizontal * speedFactor;
        if (bestEscape != dir && bestEscapeClear > clearAhead + 0.35) {
            double steer = MathHelper.clamp((checkDistance - clearAhead) / checkDistance, 0.2, 0.72);
            Vec3d blended = dir.multiply(1.0 - steer).add(bestEscape.multiply(steer)).normalize();
            desiredHorizontal = horizontal * Math.max(0.45, speedFactor);
            velocity = new Vec3d(blended.x * desiredHorizontal, velocity.y, blended.z * desiredHorizontal);
        } else {
            double scale = Math.max(0.35, speedFactor);
            velocity = new Vec3d(velocity.x * scale, velocity.y, velocity.z * scale);
        }

        double vx = velocity.x;
        double vz = velocity.z;
        double vy = velocity.y;
        double floorDist = pathfinder.distanceToSolidBelow(pos, SAFETY_SCAN);
        double ceilingDist = pathfinder.distanceToSolidAbove(pos, SAFETY_SCAN);

        if (clearAhead < 0.8) {
            vx *= 0.55;
            vz *= 0.55;
            vy = Math.max(vy, 0.24);
            stuckTicks = Math.max(stuckTicks, STUCK_REPATH_TICKS);
        } else if (clearAhead < 1.35) {
            vy = Math.max(vy, 0.14);
        }

        if (floorDist < FLOOR_CLEARANCE + 0.45) vy = Math.max(vy, 0.10);
        if (ceilingDist < CEILING_CLEARANCE + 0.2) vy = Math.min(vy, 0.05);
        return new Vec3d(vx, vy, vz);
    }

    private Vec3d computeLocalAvoidance(Vec3d steerTarget, Vec3d baseVelocity, double horizontalCap) {
        if (module.client().player == null) return Vec3d.ZERO;

        Vec3d pos = module.client().player.getEntityPos();
        Vec3d avoid = Vec3d.ZERO;
        double floorDist = pathfinder.distanceToSolidBelow(pos, SAFETY_SCAN);
        double ceilingDist = pathfinder.distanceToSolidAbove(pos, SAFETY_SCAN);

        if (floorDist < FLOOR_CLEARANCE) {
            double push = (FLOOR_CLEARANCE - floorDist) * 0.75;
            avoid = avoid.add(0, Math.min(module.verticalMaxSpeed(), push), 0);
        }
        if (ceilingDist < CEILING_CLEARANCE) {
            double push = (CEILING_CLEARANCE - ceilingDist) * 0.75;
            avoid = avoid.add(0, -Math.min(module.verticalMaxSpeed(), push), 0);
        }

        Vec3d heading = steerTarget.subtract(pos);
        double headingLen = Math.sqrt(heading.x * heading.x + heading.z * heading.z);
        if (headingLen < 1.0E-4) {
            heading = new Vec3d(baseVelocity.x, 0, baseVelocity.z);
            headingLen = Math.sqrt(heading.x * heading.x + heading.z * heading.z);
        }
        if (headingLen <= 1.0E-4) return avoid;

        Vec3d forward = new Vec3d(heading.x / headingLen, 0, heading.z / headingLen);
        double lookAhead = OBSTACLE_LOOK_AHEAD;
        double frontClear = probeClearDistance(pos, forward, lookAhead);
        if (frontClear >= lookAhead - 0.15) return avoid;

        Vec3d left = new Vec3d(-forward.z, 0, forward.x);
        Vec3d right = left.multiply(-1);
        Vec3d targetDir = steerTarget.subtract(pos).normalize();
        Vec3d[] candidates = {
            left,
            right,
            left.add(forward.multiply(0.45)).normalize(),
            right.add(forward.multiply(0.45)).normalize(),
            left.add(0, 0.7, 0).normalize(),
            right.add(0, 0.7, 0).normalize(),
            new Vec3d(0, 1, 0),
            left.add(forward.multiply(-0.35)).normalize(),
            right.add(forward.multiply(-0.35)).normalize()
        };

        Vec3d bestDir = left;
        double bestScore = Double.NEGATIVE_INFINITY;
        double bestSpace = 0;
        for (Vec3d candidate : candidates) {
            double space = probeClearDistance(pos, candidate, lookAhead);
            double align = candidate.dotProduct(targetDir);
            Vec3d sample = pos.add(candidate.multiply(Math.max(0.9, space * 0.65)));
            double sampleFloor = pathfinder.distanceToSolidBelow(sample, Math.max(6, SAFETY_SCAN / 2));
            double sampleCeiling = pathfinder.distanceToSolidAbove(sample, Math.max(6, SAFETY_SCAN / 2));
            int sideOpenings = pathfinder.countLateralOpenings(sample, 0.9);
            double score = space * 1.55 + align * 1.05 + sideOpenings * 0.3;
            if (sampleFloor < FLOOR_CLEARANCE) score -= (FLOOR_CLEARANCE - sampleFloor) * 1.4;
            if (sampleCeiling < CEILING_CLEARANCE) score -= (CEILING_CLEARANCE - sampleCeiling) * 1.4;
            if (candidate.y > 0 && floorDist < FLOOR_CLEARANCE + 0.7) score += 1.0;
            if (candidate.y > 0 && ceilingDist < CEILING_CLEARANCE + 0.7) score -= 0.8;
            if (score > bestScore) {
                bestScore = score;
                bestDir = candidate;
                bestSpace = space;
            }
        }

        double urgency = Math.max(0.0, 1.0 - bestSpace / lookAhead);
        double sideSpeed = horizontalCap * (0.45 + urgency) * AVOIDANCE_STRENGTH;
        return avoid.add(bestDir.multiply(sideSpeed));
    }

    private double probeClearDistance(Vec3d start, Vec3d dir, double maxDistance) {
        double step = Math.max(0.25, COLLISION_STEP);
        for (double distance = step; distance <= maxDistance; distance += step) {
            if (!pathfinder.hasPlayerClearance(start.add(dir.multiply(distance)))) return distance - step;
        }
        return maxDistance;
    }
}
