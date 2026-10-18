package gamerguy11.sixtoolsaddon.modules;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import java.lang.reflect.Field;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.settings.Vector3dSetting;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

public class SixToolsAutoWasp extends Module {
    private static final String DISPLAY_TITLE = "6Tools Auto Wasp";

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTarget = settings.createGroup("Target");
    private final SettingGroup sgSpeed = settings.createGroup("Speed");
    private final SettingGroup sgFlight = settings.createGroup("Flight");

    private final Setting<Boolean> followEfly = sgGeneral.add(new BoolSetting.Builder()
        .name("follow-efly")
        .description("Uses the speeds, acceleration, height limit and chunk check from Efly so both modules always fly the same way.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Action> action = sgGeneral.add(new EnumSetting.Builder<Action>()
        .name("action-on-target-loss")
        .description("What to do if you lose the target.")
        .defaultValue(Action.TOGGLE)
        .build()
    );

    private final Setting<Vector3d> offset = sgGeneral.add(new Vector3dSetting.Builder()
        .name("offset")
        .description("How many blocks offset to wasp at from the target.")
        .defaultValue(0, 0, 0)
        .build()
    );

    private final Setting<Boolean> avoidLanding = sgGeneral.add(new BoolSetting.Builder()
        .name("avoid-landing")
        .description("Stays just above the ground when your target is standing on it so you do not land.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> predictMovement = sgGeneral.add(new BoolSetting.Builder()
        .name("predict-movement")
        .description("Aims ahead of the target using how fast it is moving.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> predictionTicks = sgGeneral.add(new IntSetting.Builder()
        .name("prediction-ticks")
        .description("Maximum number of ticks to aim ahead of the target.")
        .defaultValue(3)
        .range(1, 10)
        .sliderRange(1, 10)
        .visible(predictMovement::get)
        .build()
    );

    private final Setting<Boolean> onlyFriends = sgTarget.add(new BoolSetting.Builder()
        .name("only-friends")
        .description("Will only follow friends.")
        .defaultValue(false)
        .build()
    );

    private final Setting<String> targetName = sgTarget.add(new StringSetting.Builder()
        .name("target-name")
        .description("Only follows the player with this exact name. Leave empty to follow the closest player.")
        .defaultValue("")
        .build()
    );

    private final Setting<Double> horizontalSpeed = sgSpeed.add(new DoubleSetting.Builder()
        .name("horizontal-speed")
        .description("Fastest horizontal speed in blocks per tick.")
        .defaultValue(14.999)
        .min(0.1)
        .sliderMax(15.0)
        .visible(() -> !followEfly.get())
        .build()
    );

    private final Setting<Double> verticalSpeed = sgSpeed.add(new DoubleSetting.Builder()
        .name("vertical-speed")
        .description("Fastest vertical speed in blocks per tick.")
        .defaultValue(5.999)
        .min(0.1)
        .sliderMax(8.0)
        .visible(() -> !followEfly.get())
        .build()
    );

    private final Setting<Boolean> useAcceleration = sgSpeed.add(new BoolSetting.Builder()
        .name("acceleration")
        .description("Ramps up to full speed instead of starting at it, which avoids rubberbanding.")
        .defaultValue(true)
        .visible(() -> !followEfly.get())
        .build()
    );

    private final Setting<Double> startSpeed = sgSpeed.add(new DoubleSetting.Builder()
        .name("start-speed")
        .description("Horizontal speed the ramp starts from.")
        .defaultValue(2.999)
        .min(0.0)
        .sliderMax(15.0)
        .visible(() -> !followEfly.get() && useAcceleration.get())
        .build()
    );

    private final Setting<Double> accelerationStep = sgSpeed.add(new DoubleSetting.Builder()
        .name("acceleration-step")
        .description("How fast horizontal speed ramps up.")
        .defaultValue(0.3)
        .min(0.01)
        .max(5.0)
        .visible(() -> !followEfly.get() && useAcceleration.get())
        .build()
    );

    private final Setting<Double> accelerationPlateau = sgSpeed.add(new DoubleSetting.Builder()
        .name("acceleration-plateau")
        .description("The horizontal speed where the ramp tends to zero.")
        .defaultValue(14.999)
        .min(0.01)
        .sliderMax(15.0)
        .visible(() -> !followEfly.get() && useAcceleration.get())
        .build()
    );

    private final Setting<Integer> accelerationDelay = sgSpeed.add(new IntSetting.Builder()
        .name("acceleration-delay")
        .description("Ticks to wait before the ramp starts.")
        .defaultValue(1)
        .min(0)
        .sliderMax(40)
        .visible(() -> !followEfly.get() && useAcceleration.get())
        .build()
    );

    private final Setting<Double> speedCap = sgSpeed.add(new DoubleSetting.Builder()
        .name("speed-cap")
        .description("Hard limit on total blocks per tick (horizontal and vertical combined). Vanilla's elytra speed check sits at about 17.3.")
        .defaultValue(17.0)
        .min(1.0)
        .sliderMax(17.3)
        .build()
    );

    private final Setting<Boolean> autoTakeOff = sgFlight.add(new BoolSetting.Builder()
        .name("auto-take-off")
        .description("Jumps and starts gliding by itself.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> manageElytra = sgFlight.add(new BoolSetting.Builder()
        .name("manage-elytra")
        .description("Equips an elytra from your inventory when you have none on and swaps it out before it breaks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> replaceDurability = sgFlight.add(new IntSetting.Builder()
        .name("replace-durability")
        .description("Durability left on the elytra before it is swapped.")
        .defaultValue(2)
        .range(1, 500)
        .sliderRange(1, 100)
        .visible(manageElytra::get)
        .build()
    );

    private final Setting<Boolean> obstacleClimb = sgFlight.add(new BoolSetting.Builder()
        .name("obstacle-climb")
        .description("Climbs over blocks in the way instead of flying straight into them.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> climbLookAhead = sgFlight.add(new IntSetting.Builder()
        .name("climb-look-ahead")
        .description("How many blocks ahead to look for obstacles.")
        .defaultValue(6)
        .range(1, 15)
        .sliderRange(1, 15)
        .visible(obstacleClimb::get)
        .build()
    );

    private final Setting<Double> climbSpeed = sgFlight.add(new DoubleSetting.Builder()
        .name("climb-speed")
        .description("Vertical speed used while climbing over an obstacle.")
        .defaultValue(3.0)
        .min(0.1)
        .sliderMax(8.0)
        .visible(obstacleClimb::get)
        .build()
    );

    private final Setting<Boolean> limitMaxHeight = sgFlight.add(new BoolSetting.Builder()
        .name("limit-max-height")
        .description("Stops you from flying above a set height.")
        .defaultValue(true)
        .visible(() -> !followEfly.get())
        .build()
    );

    private final Setting<Double> maxHeight = sgFlight.add(new DoubleSetting.Builder()
        .name("max-height")
        .description("The highest Y level you will fly to.")
        .defaultValue(500.0)
        .min(-128.0)
        .sliderMax(500.0)
        .visible(() -> !followEfly.get() && limitMaxHeight.get())
        .build()
    );

    private final Setting<Boolean> noUnloadedChunks = sgFlight.add(new BoolSetting.Builder()
        .name("no-unloaded-chunks")
        .description("Stops moving horizontally when the next chunk is not loaded.")
        .defaultValue(true)
        .visible(() -> !followEfly.get())
        .build()
    );

    public PlayerEntity target;

    private int jumpTimer;
    private boolean incrementJumpTimer;
    private boolean warnedNoElytra;
    private int elytraCooldown;
    private double speed;
    private int delayTicks;
    private Vec3d lastTargetPos;
    private Vec3d targetVelocity = Vec3d.ZERO;

    public SixToolsAutoWasp() {
        super(SixToolsAddon.CATEGORY, "6tools-auto-wasp", "Follows a player on an elytra using the same speed model as Efly, climbs over obstacles and keeps your elytra equipped.");
        applyTitle();
    }

    private void applyTitle() {
        try {
            Field field = Module.class.getDeclaredField("title");
            field.setAccessible(true);
            field.set(this, DISPLAY_TITLE);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onActivate() {
        jumpTimer = 0;
        incrementJumpTimer = false;
        warnedNoElytra = false;
        elytraCooldown = 0;
        lastTargetPos = null;
        targetVelocity = Vec3d.ZERO;
        resetAcceleration();

        if (!selectTarget()) {
            error("No valid targets.");
            toggle();
            return;
        }

        Efly efly = Modules.get().get(Efly.class);
        if (efly != null) efly.setExternalControl(true);
    }

    @Override
    public void onDeactivate() {
        target = null;
        lastTargetPos = null;
        targetVelocity = Vec3d.ZERO;

        Efly efly = Modules.get().get(Efly.class);
        if (efly != null) efly.setExternalControl(false);
    }

    @Override
    public String getInfoString() {
        return target == null ? null : target.getName().getString();
    }

    private boolean selectTarget() {
        String wanted = targetName.get().trim();

        Entity found = TargetUtils.get(entity -> {
            if (!(entity instanceof PlayerEntity player) || entity == mc.player) return false;
            if (player.isDead() || player.getHealth() <= 0) return false;
            if (!wanted.isEmpty() && !player.getName().getString().equalsIgnoreCase(wanted)) return false;
            return !onlyFriends.get() || Friends.get().isFriend(player);
        }, SortPriority.LowestDistance);

        if (!(found instanceof PlayerEntity player)) {
            target = null;
            return false;
        }

        target = player;
        lastTargetPos = null;
        targetVelocity = Vec3d.ZERO;
        info("%s set as target.", player.getName().getString());
        return true;
    }

    private boolean handleTargetLoss() {
        warning("Lost target!");

        switch (action.get()) {
            case CHOOSE_NEW_TARGET -> {
                if (!selectTarget()) {
                    error("No valid targets.");
                    toggle();
                }
            }
            case TOGGLE -> toggle();
            case DISCONNECT -> {
                if (mc.player != null && mc.player.networkHandler != null) {
                    mc.player.networkHandler.onDisconnect(new DisconnectS2CPacket(Text.literal("%s[%s6Tools Auto Wasp%s] Lost target.".formatted(Formatting.GRAY, Formatting.BLUE, Formatting.GRAY))));
                }
                if (isActive()) toggle();
            }
        }

        return isActive() && target != null && !target.isRemoved();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (target == null || target.isRemoved() || !target.isAlive()) {
            if (!handleTargetLoss()) return;
        }

        trackTarget();

        if (mc.currentScreen == null) manageElytraSlot();

        if (!hasGlider()) {
            if (!warnedNoElytra) {
                warning("No elytra equipped.");
                warnedNoElytra = true;
            }
            return;
        }
        warnedNoElytra = false;

        takeOff();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (isActive()) toggle();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof PlayerPositionLookS2CPacket) resetAcceleration();
    }

    @EventHandler
    private void onMove(PlayerMoveEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (target == null || target.isRemoved()) return;
        if (!hasGlider() || !mc.player.isGliding()) return;

        Vec3d targetPos = aimPosition();

        stepAcceleration();
        double maxHorizontal = speed;
        double maxVertical = maxVerticalSpeed();

        double xDist = targetPos.x - mc.player.getX();
        double yDist = targetPos.y - mc.player.getY();
        double zDist = targetPos.z - mc.player.getZ();

        double horizontalDist = Math.sqrt(xDist * xDist + zDist * zDist);
        double xVel = 0;
        double zVel = 0;
        if (horizontalDist > 1.0E-5) {
            double move = Math.min(horizontalDist, maxHorizontal);
            xVel = xDist / horizontalDist * move;
            zVel = zDist / horizontalDist * move;
        }

        double yVel = 0;
        if (Math.abs(yDist) > 1.0E-5) {
            yVel = Math.abs(yDist) < maxVertical ? yDist : maxVertical * Math.signum(yDist);
        }

        if (obstacleClimb.get() && blockedAhead(xVel, zVel)) {
            yVel = Math.max(yVel, Math.min(maxVertical, climbSpeed.get()));
            xVel *= 0.35;
            zVel *= 0.35;
        }

        double total = Math.sqrt(xVel * xVel + yVel * yVel + zVel * zVel);
        double cap = speedCap.get();
        if (total > cap) {
            double scale = cap / total;
            xVel *= scale;
            yVel *= scale;
            zVel *= scale;
        }

        yVel = limitHeight(yVel);

        if (skipUnloadedChunks()) {
            int chunkX = (int) Math.floor(mc.player.getX() + xVel) >> 4;
            int chunkZ = (int) Math.floor(mc.player.getZ() + zVel) >> 4;
            if (!mc.world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
                xVel = 0;
                zVel = 0;
            }
        }

        ((IVec3d) event.movement).meteor$set(xVel, yVel, zVel);
    }

    private Vec3d aimPosition() {
        Vec3d aim = target.getEntityPos().add(offset.get().x, offset.get().y, offset.get().z);

        if (predictMovement.get()) {
            double distance = mc.player.getEntityPos().distanceTo(aim);
            double lead = Math.min(predictionTicks.get(), distance / Math.max(speed, 1.0));
            aim = aim.add(targetVelocity.multiply(lead));
        }

        if (avoidLanding.get()) {
            double half = target.getBoundingBox().getLengthX() / 2;

            for (Direction dir : Direction.Type.HORIZONTAL) {
                BlockPos pos = BlockPos.ofFloored(aim.offset(dir, half).offset(dir.rotateYClockwise(), half)).down();
                BlockState state = mc.world.getBlockState(pos);
                if (!state.getCollisionShape(mc.world, pos).isEmpty() && Math.abs(aim.y - (pos.getY() + 1)) <= 0.25) {
                    aim = new Vec3d(aim.x, pos.getY() + 1.25, aim.z);
                    break;
                }
            }
        }

        return aim;
    }

    private void trackTarget() {
        Vec3d pos = target.getEntityPos();

        if (lastTargetPos != null) {
            Vec3d delta = pos.subtract(lastTargetPos);
            if (delta.lengthSquared() < 10000) targetVelocity = targetVelocity.multiply(0.5).add(delta.multiply(0.5));
            else targetVelocity = Vec3d.ZERO;
        }

        lastTargetPos = pos;
    }

    private boolean blockedAhead(double xVel, double zVel) {
        double length = Math.hypot(xVel, zVel);
        if (length < 0.05) return false;

        double look = climbLookAhead.get();
        Box probe = mc.player.getBoundingBox()
            .expand(0.0, -0.1, 0.0)
            .stretch(xVel / length * look, 0.0, zVel / length * look);

        return mc.world.getBlockCollisions(mc.player, probe).iterator().hasNext();
    }

    private double limitHeight(double yVel) {
        if (yVel <= 0 || !limitHeightEnabled()) return yVel;

        double limit = heightLimit();
        double y = mc.player.getY();
        if (y >= limit) return 0;
        if (y + yVel > limit) return limit - y;
        return yVel;
    }

    private boolean hasGlider() {
        return mc.player.getEquippedStack(EquipmentSlot.CHEST).contains(DataComponentTypes.GLIDER);
    }

    private void manageElytraSlot() {
        if (!manageElytra.get()) return;
        if (elytraCooldown > 0) {
            elytraCooldown--;
            return;
        }

        int minimum = replaceDurability.get();
        ItemStack chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        boolean usable = chest.contains(DataComponentTypes.GLIDER)
            && (!chest.isDamageable() || chest.getMaxDamage() - chest.getDamage() > minimum);
        if (usable) return;

        FindItemResult elytra = InvUtils.find(stack -> stack.getItem() == Items.ELYTRA && stack.getMaxDamage() - stack.getDamage() > minimum);
        if (!elytra.found()) return;

        elytraCooldown = 10;
        InvUtils.move().from(elytra.slot()).toArmor(2);
    }

    private void takeOff() {
        if (mc.player.isGliding()) {
            incrementJumpTimer = false;
            jumpTimer = 0;
            return;
        }

        if (!autoTakeOff.get()) return;

        if (incrementJumpTimer) jumpTimer++;
        else incrementJumpTimer = true;

        if (mc.player.isOnGround()) {
            mc.player.jump();
            return;
        }

        if (jumpTimer >= 4) {
            jumpTimer = 0;
            mc.player.setJumping(false);
            mc.player.setSprinting(true);
            if (mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            }
        }
    }

    private Efly followedEfly() {
        if (!followEfly.get()) return null;
        return Modules.get().get(Efly.class);
    }

    private double maxHorizontalSpeed() {
        Efly efly = followedEfly();
        return efly != null ? efly.horizontalSpeed.get() : horizontalSpeed.get();
    }

    private double maxVerticalSpeed() {
        Efly efly = followedEfly();
        return efly != null ? efly.verticalSpeed.get() : verticalSpeed.get();
    }

    private double rampStart() {
        Efly efly = followedEfly();
        return efly != null ? efly.startSpeed.get() : startSpeed.get();
    }

    private boolean rampEnabled() {
        return followedEfly() != null || useAcceleration.get();
    }

    private double rampPlateau() {
        Efly efly = followedEfly();
        return efly != null ? efly.accelerationPlateau.get() : accelerationPlateau.get();
    }

    private double rampStep() {
        Efly efly = followedEfly();
        return efly != null ? efly.accelerationStep.get() : accelerationStep.get();
    }

    private int rampDelay() {
        Efly efly = followedEfly();
        return efly != null ? efly.accelerationDelay.get() : accelerationDelay.get();
    }

    private boolean limitHeightEnabled() {
        Efly efly = followedEfly();
        return efly != null ? efly.limitMaxHeight.get() : limitMaxHeight.get();
    }

    private double heightLimit() {
        Efly efly = followedEfly();
        return efly != null ? efly.maxHeight.get() : maxHeight.get();
    }

    private boolean skipUnloadedChunks() {
        Efly efly = followedEfly();
        return efly != null ? efly.dontGoIntoUnloadedChunks.get() : noUnloadedChunks.get();
    }

    private void resetAcceleration() {
        speed = Math.min(rampStart(), maxHorizontalSpeed());
        delayTicks = 0;
    }

    private void stepAcceleration() {
        double max = maxHorizontalSpeed();

        if (!rampEnabled() || speed >= max) {
            speed = max;
            return;
        }

        if (delayTicks < rampDelay()) {
            delayTicks++;
            return;
        }

        double plateau = Math.max(0.01, rampPlateau());
        double gain = rampStep() * (Math.max(0.0, plateau - speed) / plateau);
        speed = Math.min(speed + gain, max);
        if (max - speed < 0.05) speed = max;
    }

    public enum Action {
        TOGGLE,
        CHOOSE_NEW_TARGET,
        DISCONNECT;

        @Override
        public String toString() {
            return switch (this) {
                case TOGGLE -> "Toggle module";
                case CHOOSE_NEW_TARGET -> "Choose new target";
                case DISCONNECT -> "Disconnect";
            };
        }
    }
}
