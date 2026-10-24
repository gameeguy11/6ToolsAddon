package gamerguy11.sixtoolsaddon.modules;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import gamerguy11.sixtoolsaddon.modules.autowasp.AutoWaspLogic.AutoWaspChestSwap;
import gamerguy11.sixtoolsaddon.modules.autowasp.AutoWaspFlightController;
import gamerguy11.sixtoolsaddon.modules.autowasp.AutoWaspPathing.AutoWaspPathfinder;
import gamerguy11.sixtoolsaddon.modules.autowasp.AutoWaspLogic.AutoWaspSpeed;
import gamerguy11.sixtoolsaddon.modules.autowasp.AutoWaspLogic.AutoWaspTargetTracker;
import java.lang.reflect.Field;
import java.util.ArrayList;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
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
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.component.EnchantmentEffectComponentTypes;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

public class SixToolsAutoWasp extends Module {
    private static final String DISPLAY_TITLE = "6Tools Auto Wasp";
    private static final Direction[] CARDINAL_DIRECTIONS = {
        Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };
    private static final int TARGET_SEARCH_TICKS = 10;
    private static final int CHEST_SWAP_COOLDOWN_TICKS = 10;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgChestSwap = settings.createGroup("Chest Swap");
    private final SettingGroup sgRamp = settings.createGroup("Speed Ramp");
    private final SettingGroup sgServerGlide = settings.createGroup("Server Glide");

    private final Setting<Boolean> followEfly = sgGeneral.add(new BoolSetting.Builder()
        .name("follow-efly")
        .description("Uses Efly's maximum horizontal and vertical speed, height limit and unloaded chunk check, and keeps Efly from controlling movement while this module is active.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> horizontalMaxBps = sgGeneral.add(new DoubleSetting.Builder()
        .name("horizontal-max-bps")
        .description("Fastest horizontal speed in blocks per second.")
        .defaultValue(AutoWaspSpeed.BUNCHED_SAFE * AutoWaspSpeed.TICKS_PER_SECOND)
        .range(2.8, AutoWaspSpeed.HORIZONTAL_TOP * AutoWaspSpeed.TICKS_PER_SECOND)
        .sliderRange(20, AutoWaspSpeed.HORIZONTAL_TOP * AutoWaspSpeed.TICKS_PER_SECOND)
        .decimalPlaces(1)
        .visible(() -> !followEfly.get())
        .build()
    );

    private final Setting<Double> verticalMaxBps = sgGeneral.add(new DoubleSetting.Builder()
        .name("vertical-max-bps")
        .description("Fastest climb or descent in blocks per second.")
        .defaultValue(AutoWaspSpeed.VERTICAL_TOP * AutoWaspSpeed.TICKS_PER_SECOND)
        .range(0, AutoWaspSpeed.VERTICAL_TOP * AutoWaspSpeed.TICKS_PER_SECOND)
        .sliderRange(0, AutoWaspSpeed.VERTICAL_TOP * AutoWaspSpeed.TICKS_PER_SECOND)
        .decimalPlaces(1)
        .visible(() -> !followEfly.get())
        .build()
    );

    private final Setting<Double> max3dBps = sgGeneral.add(new DoubleSetting.Builder()
        .name("3d-max-bps")
        .description("Fastest total 3D speed in blocks per second while climbing or descending, and for half a second after.")
        .defaultValue(180)
        .range(20, AutoWaspSpeed.HORIZONTAL_TOP * AutoWaspSpeed.TICKS_PER_SECOND)
        .sliderRange(20, AutoWaspSpeed.HORIZONTAL_TOP * AutoWaspSpeed.TICKS_PER_SECOND)
        .decimalPlaces(1)
        .build()
    );

    private final Setting<Boolean> limitMaxHeight = sgGeneral.add(new BoolSetting.Builder()
        .name("limit-max-height")
        .description("Stops you from flying above a set height.")
        .defaultValue(true)
        .visible(() -> !followEfly.get())
        .build()
    );

    private final Setting<Double> maxHeight = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-height")
        .description("The highest Y level you can reach.")
        .defaultValue(500)
        .min(-128)
        .sliderMax(500)
        .visible(() -> !followEfly.get() && limitMaxHeight.get())
        .build()
    );

    private final Setting<Boolean> noUnloadedChunks = sgGeneral.add(new BoolSetting.Builder()
        .name("no-unloaded-chunks")
        .description("Stops horizontal movement before entering unloaded chunks.")
        .defaultValue(true)
        .visible(() -> !followEfly.get())
        .build()
    );

    private final Setting<Boolean> autoTakeOff = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-take-off")
        .description("Jumps and opens the elytra automatically.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> avoidLanding = sgGeneral.add(new BoolSetting.Builder()
        .name("avoid-landing")
        .description("Will try to avoid landing if your target is on the ground.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> predictMovement = sgGeneral.add(new BoolSetting.Builder()
        .name("predict-movement")
        .description("Leads the target by its recent movement over your ping, and matches its speed when flying straight at it.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> onlyFriends = sgGeneral.add(new BoolSetting.Builder()
        .name("only-friends")
        .description("Will only follow friends.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> friendFilter = sgGeneral.add(new BoolSetting.Builder()
        .name("friend-filter")
        .description("When enabled: only-friends=true follows friends, only-friends=false follows non-friends.")
        .defaultValue(false)
        .build()
    );

    private final Setting<String> targetName = sgGeneral.add(new StringSetting.Builder()
        .name("target-name")
        .description("Only follows the player with this exact name. Leave empty to follow the closest player.")
        .defaultValue("")
        .build()
    );

    private final Setting<Action> action = sgGeneral.add(new EnumSetting.Builder<Action>()
        .name("action-on-target-loss")
        .description("What to do if you lose the target.")
        .defaultValue(Action.TOGGLE)
        .build()
    );

    private final Setting<Boolean> keepSearching = sgGeneral.add(new BoolSetting.Builder()
        .name("keep-searching")
        .description("Stays enabled and keeps looking for a target instead of disabling when none is loaded.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Vector3d> offset = sgGeneral.add(new Vector3dSetting.Builder()
        .name("offset")
        .description("How many blocks offset to wasp at from the target.")
        .defaultValue(0, 0, 0)
        .build()
    );

    private final Setting<Boolean> autoChestSwap = sgChestSwap.add(new BoolSetting.Builder()
        .name("auto-chest-swap")
        .description("Equips a chestplate near the target on stable ground, and elytra for pursuit, replacing an elytra with 1 durability left.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> chestSwapHorizontalRange = sgChestSwap.add(new DoubleSetting.Builder()
        .name("chest-swap-horizontal-range")
        .description("Maximum horizontal distance to the target before swapping off elytra.")
        .defaultValue(2.5)
        .range(0.5, 6.0)
        .sliderRange(0.5, 6.0)
        .visible(autoChestSwap::get)
        .build()
    );

    private final Setting<Double> chestSwapVerticalRange = sgChestSwap.add(new DoubleSetting.Builder()
        .name("chest-swap-vertical-range")
        .description("Maximum vertical difference to the target before swapping off elytra.")
        .defaultValue(1.0)
        .range(0.0, 4.0)
        .sliderRange(0.0, 4.0)
        .visible(autoChestSwap::get)
        .build()
    );

    private final Setting<Double> chestSwapGroundDistance = sgChestSwap.add(new DoubleSetting.Builder()
        .name("chest-swap-ground-distance")
        .description("Only swaps off elytra when there is solid ground close enough below you.")
        .defaultValue(1.25)
        .range(0.0, 4.0)
        .sliderRange(0.0, 4.0)
        .visible(autoChestSwap::get)
        .build()
    );

    private final Setting<Double> chestSwapHysteresis = sgChestSwap.add(new DoubleSetting.Builder()
        .name("chest-swap-hysteresis")
        .description("Once the chestplate is on, every chest-swap range is this many blocks wider before the elytra goes back on.")
        .defaultValue(1.5)
        .range(0.0, 4.0)
        .sliderRange(0.0, 4.0)
        .visible(autoChestSwap::get)
        .build()
    );

    private final Setting<Double> rampStep = sgRamp.add(new DoubleSetting.Builder()
        .name("ramp-step")
        .description("Horizontal boost acceleration in blocks per tick squared at rest, tapering to zero at the top speed.")
        .defaultValue(0.3)
        .range(0.01, 5.0)
        .sliderRange(0.05, 2.0)
        .build()
    );

    private final Setting<Integer> setbackRewindTicks = sgRamp.add(new IntSetting.Builder()
        .name("setback-rewind-ticks")
        .description("A server setback rewinds both boost ramps by this many ticks of acceleration instead of restarting them.")
        .defaultValue(15)
        .range(0, 200)
        .sliderRange(0, 60)
        .build()
    );

    private final Setting<Integer> setbackHoldTicks = sgRamp.add(new IntSetting.Builder()
        .name("setback-hold-ticks")
        .description("After a setback, hold the rewound speed for this many ticks before accelerating again.")
        .defaultValue(1)
        .range(0, 20)
        .sliderRange(0, 10)
        .build()
    );

    private final Setting<Integer> pinnedGlideTicks = sgRamp.add(new IntSetting.Builder()
        .name("pinned-glide-ticks")
        .description("When two setbacks in a row put you on the same spot, glide with vanilla physics for this many ticks. Zero disables.")
        .defaultValue(10)
        .range(0, 60)
        .sliderRange(0, 40)
        .build()
    );

    private final Setting<Boolean> serverGlideGuard = sgServerGlide.add(new BoolSetting.Builder()
        .name("server-glide-guard")
        .description("Keep walls out of reach of the server's own elytra glide.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> steerServerGlide = sgServerGlide.add(new BoolSetting.Builder()
        .name("steer-server-glide")
        .description("Near a wall the server's glide could reach, report a level heading away from it.")
        .defaultValue(true)
        .build()
    );

    private final AutoWaspPathfinder pathfinder = new AutoWaspPathfinder(this);
    private final AutoWaspFlightController flightController = new AutoWaspFlightController(this, pathfinder);
    private final AutoWaspTargetTracker targetTracker = new AutoWaspTargetTracker();

    public PlayerEntity target;
    private int targetSearchTimer;
    private boolean waitingForElytra;
    private int chestSwapCooldown;

    public SixToolsAutoWasp() {
        super(SixToolsAddon.CATEGORY, "6tools-auto-wasp", "Follows a player on an elytra with obstacle-aware routing, using Efly's limits.");
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
        if (mc.player == null || mc.world == null) {
            toggle();
            return;
        }

        Modules modules = Modules.get();
        for (Module conflict : new Module[] {
            modules.get(meteordevelopment.meteorclient.systems.modules.movement.AutoWasp.class),
            modules.get(meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFly.class)
        }) {
            if (conflict != null && conflict.isActive()) {
                conflict.toggle();
                info("Disabled %s for Auto Wasp pursuit.", conflict.title);
            }
        }

        resetNavigationState();
        chestSwapCooldown = 0;

        Efly efly = Modules.get().get(Efly.class);
        if (efly != null) efly.setExternalControl(true);

        if (target == null || target.isRemoved()) {
            if (tryAcquireTarget(false)) {
                info("Target set to: " + target.getName().getString());
            } else if (!keepSearching.get()) {
                error("No valid targets.");
                toggle();
            }
        }
    }

    @Override
    public void onDeactivate() {
        target = null;
        targetSearchTimer = 0;
        waitingForElytra = false;
        chestSwapCooldown = 0;
        targetTracker.reset();
        flightController.onDeactivate();
        pathfinder.onDeactivate();

        Efly efly = Modules.get().get(Efly.class);
        if (efly != null) efly.setExternalControl(false);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        onTickSafe();
    }

    @EventHandler
    private void onPostTick(TickEvent.Post event) {
        if (mc.player != null && mc.world != null) flightController.postTick();
    }

    @EventHandler
    private void onMove(PlayerMoveEvent event) {
        onMoveSafe(event);
    }

    @EventHandler
    private void onReceive(PacketEvent.Receive event) {
        flightController.onReceive(event.packet);
    }

    @EventHandler
    private void onSend(PacketEvent.Send event) {
        flightController.onSend(event.packet);
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (isActive()) toggle();
    }

    private void onTickSafe() {
        if (mc.player == null || mc.world == null || !mc.player.isAlive() || mc.player.isSpectator()) return;
        flightController.preTick(hasUsableElytra());
        if (Modules.get().isActive(Freecam.class)) return;

        pathfinder.tickCaches();
        if (chestSwapCooldown > 0) chestSwapCooldown--;
        if (targetSearchTimer > 0) targetSearchTimer--;

        if (target == null || target.isRemoved() || target.isDead() || target.getHealth() <= 0) {
            if (!handleMissingTarget()) {
                tickAutoChestSwap();
                return;
            }
        }

        if (target == null) {
            tickAutoChestSwap();
            return;
        }

        if (!acceptsTarget(target)) {
            if (!handleMissingTarget()) {
                tickAutoChestSwap();
                return;
            }
        }

        if (target == null) {
            tickAutoChestSwap();
            return;
        }

        targetTracker.sample(target.getId(), target.getEntityPos());
        boolean shouldUseElytra = tickAutoChestSwap();
        if (!hasUsableElytra()) {
            if (!shouldUseElytra) {
                waitingForElytra = false;
                flightController.resetFlightTracking();
                return;
            }
            if (!waitingForElytra) {
                warning("No usable elytra equipped, waiting for swap...");
                waitingForElytra = true;
            }
            flightController.resetFlightTracking();
            return;
        }

        waitingForElytra = false;
        if (!mc.player.isGliding()) {
            if (autoTakeOff.get()) flightController.handleNotGliding();
            else flightController.resetFlightTracking();
            return;
        }

        flightController.onGlidingTick();
        pathfinder.tickPathing(mc.player.getEntityPos(), getTargetPos(), flightController.getStuckTicks());
    }

    private void onMoveSafe(PlayerMoveEvent event) {
        if (mc.player == null || mc.world == null || !mc.player.isAlive() || mc.player.isSpectator()
            || event.type != MovementType.SELF) return;
        if (Modules.get().isActive(Freecam.class)) return;
        if (target == null || target.isRemoved() || target.isDead()) return;
        if (!hasUsableElytra() || !mc.player.isGliding()) return;

        Vec3d targetPos = getTargetPos();
        Vec3d steer = pathfinder.steerTarget(targetPos);
        Vec3d lead = predictMovement.get() && steer == targetPos ? targetTracker.velocity() : Vec3d.ZERO;
        flightController.onMove(event, steer, lead);
        applyEflyLimits(event);
    }

    private void applyEflyLimits(PlayerMoveEvent event) {
        double x = event.movement.x;
        double y = event.movement.y;
        double z = event.movement.z;

        if (heightLimitEnabled() && y > 0) {
            double limit = heightLimit();
            double py = mc.player.getY();
            if (py >= limit) y = 0;
            else if (py + y > limit) y = limit - py;
        }

        if (skipUnloadedChunks()) {
            int chunkX = MathHelper.floor(mc.player.getX() + x) >> 4;
            int chunkZ = MathHelper.floor(mc.player.getZ() + z) >> 4;
            if (!mc.world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
                x = 0;
                z = 0;
            }
        }

        ((IVec3d) event.movement).meteor$set(x, y, z);
    }

    private boolean acceptsTarget(PlayerEntity player) {
        String wanted = targetName.get().trim();
        if (!wanted.isEmpty() && !player.getName().getString().equalsIgnoreCase(wanted)) return false;

        boolean isFriend = Friends.get().isFriend(player);
        return friendFilter.get()
            ? (onlyFriends.get() ? isFriend : !isFriend)
            : !onlyFriends.get() || isFriend;
    }

    private void findTarget() {
        var found = TargetUtils.get(entity -> {
            if (!(entity instanceof PlayerEntity player) || entity == mc.player) return false;
            if (player.isDead() || player.getHealth() <= 0) return false;
            return acceptsTarget(player);
        }, SortPriority.LowestDistance);

        target = found instanceof PlayerEntity player ? player : null;
    }

    private Vec3d getTargetPos() {
        if (target == null || mc.player == null) return mc.player != null ? mc.player.getEntityPos() : Vec3d.ZERO;

        Vec3d targetPos = target.getEntityPos();
        if (predictMovement.get()) targetPos = targetTracker.predict(targetPos, pingMs());
        targetPos = targetPos.add(offset.get().x, offset.get().y, offset.get().z);
        if (avoidLanding.get()) targetPos = liftTargetIfOnGround(targetPos);
        return pathfinder.adjustToSafeCorridor(targetPos, mc.player.getEntityPos());
    }

    private int pingMs() {
        var handler = mc.getNetworkHandler();
        var info = handler == null || mc.player == null ? null : handler.getPlayerListEntry(mc.player.getUuid());
        return info == null ? 0 : Math.max(0, info.getLatency());
    }

    private Vec3d liftTargetIfOnGround(Vec3d targetPos) {
        if (target == null) return targetPos;

        double distance = Math.max(0.35, target.getBoundingBox().getLengthX() / 2.0);
        for (Direction direction : CARDINAL_DIRECTIONS) {
            BlockPos pos = BlockPos.ofFloored(targetPos.offset(direction, distance).offset(direction.rotateYClockwise(), distance)).down();
            if (pathfinder.isSolid(pos) && Math.abs(targetPos.y - (pos.getY() + 1)) <= 0.35) {
                return new Vec3d(targetPos.x, pos.getY() + 1.35, targetPos.z);
            }
        }
        return targetPos;
    }

    private boolean tickAutoChestSwap() {
        boolean shouldUseElytra = !shouldSwapToChestplate();
        if (autoChestSwap.get() && target != null) requestChestGear(shouldUseElytra);
        return shouldUseElytra;
    }

    private boolean shouldSwapToChestplate() {
        if (!autoChestSwap.get() || mc.player == null || target == null) return false;

        ItemStack worn = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        boolean wearingChestplate = !worn.isEmpty() && !worn.contains(DataComponentTypes.GLIDER);
        double extra = wearingChestplate ? chestSwapHysteresis.get() : 0;
        double horizontalDistance = Math.hypot(mc.player.getX() - target.getX(), mc.player.getZ() - target.getZ());
        double verticalDistance = Math.abs(mc.player.getY() - target.getY());
        int scanDepth = Math.max(3, MathHelper.ceil(chestSwapGroundDistance.get() + extra) + 2);
        double groundDistance = pathfinder.distanceToSolidBelow(mc.player.getEntityPos(), scanDepth);
        return AutoWaspChestSwap.wearChestplate(wearingChestplate, horizontalDistance, verticalDistance, groundDistance,
            chestSwapHorizontalRange.get(), chestSwapVerticalRange.get(), chestSwapGroundDistance.get(),
            chestSwapHysteresis.get());
    }

    private void requestChestGear(boolean glider) {
        if (mc.player == null || mc.interactionManager == null || chestSwapCooldown > 0
            || mc.player.currentScreenHandler != mc.player.playerScreenHandler
            || !mc.player.playerScreenHandler.getCursorStack().isEmpty()) return;

        ItemStack worn = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (glider ? isUsableGlider(worn) : !worn.isEmpty() && !worn.contains(DataComponentTypes.GLIDER)) return;

        Slot chestSlot = mc.player.playerScreenHandler.getSlot(6);
        if (!chestSlot.canTakeItems(mc.player)) return;

        var candidates = new ArrayList<AutoWaspChestSwap.Candidate>();
        PlayerInventory inventory = mc.player.getInventory();
        for (int i = 0; i <= PlayerInventory.MAIN_SIZE; i++) {
            int slot = i == PlayerInventory.MAIN_SIZE ? PlayerInventory.OFF_HAND_SLOT : i;
            ItemStack stack = inventory.getStack(slot);
            if (stack.isEmpty() || stack.contains(DataComponentTypes.GLIDER) != glider || !chestSlot.canInsert(stack)
                || EnchantmentHelper.hasAnyEnchantmentsWith(stack, EnchantmentEffectComponentTypes.PREVENT_ARMOR_CHANGE)) continue;

            double[] armor = new double[2];
            if (!glider) {
                AttributeModifiersComponent modifiers = stack.getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT);
                for (AttributeModifiersComponent.Entry entry : modifiers.modifiers()) {
                    if (!entry.slot().matches(EquipmentSlot.CHEST)) continue;
                    if (entry.attribute().equals(EntityAttributes.ARMOR)) armor[0] += entry.modifier().value();
                    else if (entry.attribute().equals(EntityAttributes.ARMOR_TOUGHNESS)) armor[1] += entry.modifier().value();
                }
            }
            candidates.add(new AutoWaspChestSwap.Candidate(slot, glider, remainingDurability(stack), armor[0], armor[1]));
        }

        int slot = AutoWaspChestSwap.best(candidates, glider);
        if (slot < 0) return;

        if (SlotUtils.isHotbar(slot) || slot == PlayerInventory.OFF_HAND_SLOT) {
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, 6, slot, SlotActionType.SWAP, mc.player);
        } else {
            InvUtils.move().from(slot).toArmor(2);
        }

        if (glider) flightController.elytraSwapped();
        chestSwapCooldown = CHEST_SWAP_COOLDOWN_TICKS;
    }

    private boolean hasUsableElytra() {
        return mc.player != null && isUsableGlider(mc.player.getEquippedStack(EquipmentSlot.CHEST));
    }

    private static boolean isUsableGlider(ItemStack stack) {
        return !stack.isEmpty() && stack.contains(DataComponentTypes.GLIDER) && remainingDurability(stack) > 1;
    }

    private static int remainingDurability(ItemStack stack) {
        return stack.isDamageable() ? stack.getMaxDamage() - stack.getDamage() : Integer.MAX_VALUE;
    }

    private boolean handleMissingTarget() {
        boolean lostTarget = target != null;
        clearTargetState();
        if (lostTarget && !keepSearching.get() && action.get() != Action.CHOOSE_NEW_TARGET) {
            handleTargetLoss();
            return false;
        }
        if (tryAcquireTarget(true)) {
            info("New target: " + target.getName().getString());
            return true;
        }

        if (keepSearching.get()) return false;

        handleTargetLoss();
        return false;
    }

    private boolean tryAcquireTarget(boolean useCooldown) {
        if (useCooldown && targetSearchTimer > 0) return false;

        findTarget();
        targetSearchTimer = TARGET_SEARCH_TICKS;
        return target != null;
    }

    private void clearTargetState() {
        target = null;
        targetTracker.reset();
        pathfinder.clearTargetState();
        flightController.clearStuckState();
    }

    private void resetNavigationState() {
        targetSearchTimer = 0;
        targetTracker.reset();
        pathfinder.resetNavigationState();
        flightController.resetNavigationState();
    }

    private void handleTargetLoss() {
        warning("Lost target!");

        switch (action.get()) {
            case CHOOSE_NEW_TARGET -> {
                findTarget();
                if (target == null) {
                    error("No valid targets found.");
                    toggle();
                } else {
                    info("New target: " + target.getName().getString());
                }
            }
            case TOGGLE -> toggle();
            case DISCONNECT -> {
                if (isActive()) toggle();
                if (mc.player != null && mc.player.networkHandler != null) {
                    mc.player.networkHandler.onDisconnect(new DisconnectS2CPacket(Text.literal("%s[%s6Tools Auto Wasp%s] Lost target.".formatted(Formatting.GRAY, Formatting.BLUE, Formatting.GRAY))));
                }
            }
        }
    }

    private Efly followedEfly() {
        return followEfly.get() ? Modules.get().get(Efly.class) : null;
    }

    public MinecraftClient client() {
        return mc;
    }

    public double horizontalMaxSpeed() {
        Efly efly = followedEfly();
        double perTick = efly != null ? efly.horizontalSpeed.get() : horizontalMaxBps.get() / AutoWaspSpeed.TICKS_PER_SECOND;
        return MathHelper.clamp(perTick, 0.14, AutoWaspSpeed.HORIZONTAL_TOP);
    }

    public double verticalMaxSpeed() {
        Efly efly = followedEfly();
        double perTick = efly != null ? efly.verticalSpeed.get() : verticalMaxBps.get() / AutoWaspSpeed.TICKS_PER_SECOND;
        return MathHelper.clamp(perTick, 0, AutoWaspSpeed.VERTICAL_TOP);
    }

    public double max3dSpeed() {
        return max3dBps.get() / AutoWaspSpeed.TICKS_PER_SECOND;
    }

    private boolean heightLimitEnabled() {
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

    public double rampStepValue() {
        return rampStep.get();
    }

    public int setbackRewindTicksValue() {
        return setbackRewindTicks.get();
    }

    public int setbackHoldTicksValue() {
        return setbackHoldTicks.get();
    }

    public int pinnedGlideTicksValue() {
        return pinnedGlideTicks.get();
    }

    public boolean serverGlideGuardValue() {
        return serverGlideGuard.get();
    }

    public boolean steerServerGlideValue() {
        return steerServerGlide.get();
    }

    @Override
    public String getInfoString() {
        if (target == null) return "Searching";
        String state = flightController.state();
        String name = target.getName().getString();
        return state.equals("Pursuing") || state.equals("Waiting") ? name : name + " | " + state;
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        var list = theme.verticalList();
        list.add(theme.label("Auto Wasp adapted from DEVILS (ThianYong-hub) via Comet"));
        list.add(theme.label("GNU GPL v3 (GPL-3.0-only), no warranty"));
        return list;
    }

    public enum Action {
        TOGGLE("Toggle module"),
        CHOOSE_NEW_TARGET("Choose new target"),
        DISCONNECT("Disconnect");

        private final String title;

        Action(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }
}
