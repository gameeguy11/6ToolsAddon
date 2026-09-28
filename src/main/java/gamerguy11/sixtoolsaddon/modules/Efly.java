package gamerguy11.sixtoolsaddon.modules;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.ChestSwap;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.WitherSkullEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public class Efly extends Module {
    private final SettingGroup sgAutopilot = settings.createGroup("Autopilot");
    private final SettingGroup sgMapping = settings.createGroup("Mapping");
    private final SettingGroup sgBuildingMode = settings.createGroup("Building Mode");
    private final SettingGroup sgPlayerAvoidance = settings.createGroup("Player Avoidance System");
    private final SettingGroup sgLanding = settings.createGroup("Landing");
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgInventory = settings.createGroup("Inventory");

    public final Setting<Boolean> mappingMode = sgMapping.add(new BoolSetting.Builder()
        .name("mapping-mode")
        .description("Pauses the player's horizontal movement until nearby chunks are loaded")
        .defaultValue(false)
        .build()
    );

    public final Setting<Integer> mappingRenderRadius = sgMapping.add(new IntSetting.Builder()
        .name("render-radius")
        .description("How many chunks around you need to load before you start moving again.")
        .defaultValue(9)
        .min(0)
        .sliderMax(32)
        .visible(mappingMode::get)
        .build()
    );

    public final Setting<Boolean> mappingHideWarning = sgMapping.add(new BoolSetting.Builder()
        .name("hide-waiting-warning")
        .description("Toggle the warning message about waiting for chunks to load")
        .defaultValue(false)
        .visible(mappingMode::get)
        .build()
    );

    public final Setting<Boolean> buildingMode = sgBuildingMode.add(new BoolSetting.Builder()
        .name("building-mode")
        .description("Slows you down when blocks are nearby")
        .defaultValue(false)
        .build()
    );

    public final Setting<Double> buildingModeDistance = sgBuildingMode.add(new DoubleSetting.Builder()
        .name("building-mode-distance")
        .description("Distance from a block that triggers the slowdown")
        .defaultValue(15.0)
        .min(0.1)
        .sliderMax(30)
        .visible(buildingMode::get)
        .build()
    );

    public final Setting<Double> buildingModeMinSpeed = sgBuildingMode.add(new DoubleSetting.Builder()
        .name("building-mode-min-speed")
        .description("Speed you ease down to when near blocks")
        .defaultValue(0.7)
        .min(0)
        .sliderMax(1)
        .visible(buildingMode::get)
        .build()
    );

    public final Setting<Boolean> playerAvoidance = sgPlayerAvoidance.add(new BoolSetting.Builder()
        .name("player-avoidance")
        .description("Distances you from other players when they get close")
        .defaultValue(false)
        .build()
    );

    public final Setting<Double> avoidanceRadius = sgPlayerAvoidance.add(new DoubleSetting.Builder()
        .name("radius")
        .description("Distance at which a player triggers avoidance")
        .defaultValue(30.0)
        .min(0)
        .sliderMax(50)
        .visible(playerAvoidance::get)
        .build()
    );

    public final Setting<Boolean> avoidanceIgnoreFriends = sgPlayerAvoidance.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Ignores players on your friends list")
        .defaultValue(true)
        .visible(playerAvoidance::get)
        .build()
    );

    public final Setting<Boolean> avoidWitherSkulls = sgPlayerAvoidance.add(new BoolSetting.Builder()
        .name("avoid-wither-skulls")
        .description("Also avoids wither skulls")
        .defaultValue(true)
        .visible(playerAvoidance::get)
        .build()
    );

    public final Setting<Double> witherSkullRadius = sgPlayerAvoidance.add(new DoubleSetting.Builder()
        .name("wither-skull-radius")
        .description("Distance at which a wither skull triggers avoidance")
        .defaultValue(10.0)
        .min(0)
        .sliderMax(50)
        .visible(() -> playerAvoidance.get() && avoidWitherSkulls.get())
        .build()
    );

    public final Setting<Boolean> avoidArrows = sgPlayerAvoidance.add(new BoolSetting.Builder()
        .name("avoid-arrows")
        .description("Also avoids arrows")
        .defaultValue(true)
        .visible(playerAvoidance::get)
        .build()
    );

    public final Setting<Double> arrowRadius = sgPlayerAvoidance.add(new DoubleSetting.Builder()
        .name("arrow-radius")
        .description("Distance at which an arrow triggers avoidance")
        .defaultValue(50.0)
        .min(0)
        .sliderMax(50)
        .visible(() -> playerAvoidance.get() && avoidArrows.get())
        .build()
    );

    public final Setting<Boolean> avoidBlocks = sgPlayerAvoidance.add(new BoolSetting.Builder()
        .name("avoid-blocks")
        .description("Also avoids nearby blocks while moving away from players and wither skulls")
        .defaultValue(true)
        .visible(playerAvoidance::get)
        .build()
    );

    public final Setting<Double> blockAvoidanceRadius = sgPlayerAvoidance.add(new DoubleSetting.Builder()
        .name("block-radius")
        .description("Distance at which a block triggers avoidance")
        .defaultValue(3.0)
        .min(0)
        .sliderMax(10)
        .visible(() -> playerAvoidance.get() && avoidBlocks.get())
        .build()
    );

    public final Setting<Boolean> avoidanceVerticalStep = sgPlayerAvoidance.add(new BoolSetting.Builder()
        .name("vertical-step")
        .description("Moves up or down if avoidance gets you stuck, then avoids normally again")
        .defaultValue(true)
        .visible(playerAvoidance::get)
        .build()
    );

    public final Setting<Integer> avoidanceStuckTicks = sgPlayerAvoidance.add(new IntSetting.Builder()
        .name("stuck-ticks")
        .description("How long you have to be stuck for vertical movement to kick in")
        .defaultValue(3)
        .min(1)
        .sliderMax(20)
        .visible(() -> playerAvoidance.get() && avoidanceVerticalStep.get())
        .build()
    );

    public final Setting<Boolean> avoidanceLateral = sgPlayerAvoidance.add(new BoolSetting.Builder()
        .name("sidestep")
        .description("Tries to move sideways to an incoming player rather than simply away")
        .defaultValue(true)
        .visible(playerAvoidance::get)
        .build()
    );

    public final Setting<Boolean> landGently = sgLanding.add(new BoolSetting.Builder()
        .name("anti-slam")
        .description("Slows you down when landing to prevent fall damage")
        .defaultValue(false)
        .build()
    );

    public final Setting<Double> landGentlyDistance = sgLanding.add(new DoubleSetting.Builder()
        .name("anti-slam-distance")
        .description("Distance from the ground where slowing begins")
        .defaultValue(20.0)
        .min(0.1)
        .sliderMax(30)
        .visible(landGently::get)
        .build()
    );

    public final Setting<Double> landGentlyMinDistance = sgLanding.add(new DoubleSetting.Builder()
        .name("anti-slam-min-distance")
        .description("Distance from the ground where speed reaches the minimum value")
        .defaultValue(0.5)
        .min(0)
        .sliderMax(5)
        .visible(landGently::get)
        .build()
    );

    public final Setting<Double> landGentlyMinSpeed = sgLanding.add(new DoubleSetting.Builder()
        .name("anti-slam-min-speed")
        .description("The speed to slow down to before landing")
        .defaultValue(0.2)
        .min(0)
        .sliderMax(1)
        .visible(landGently::get)
        .build()
    );

    public final Setting<Double> horizontalSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("maximum-horizontal-speed")
        .description("The fastest horizontal speed will go (blocks per tick)")
        .defaultValue(14.999)
        .min(0)
        .build()
    );

    public final Setting<Double> verticalSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("maximum-vertical-speed")
        .description("The fastest vertical speed will go (blocks per tick)")
        .defaultValue(5.999)
        .min(0)
        .build()
    );

    public final Setting<Double> startSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("minimum-horizontal-speed")
        .description("The speed you start at when moving horizontally, before acceleration kicks in")
        .min(0)
        .defaultValue(2.999)
        .build()
    );

    public final Setting<Double> verticalStartSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("minimum-vertical-speed")
        .description("The speed you start at when moving vertically, before acceleration kicks in")
        .min(0)
        .defaultValue(5.999)
        .build()
    );

    public final Setting<Double> accelerationPlateau = sgGeneral.add(new DoubleSetting.Builder()
        .name("horizontal-acceleration-plateau")
        .description("The horizontal speed where acceleration will tend to 0")
        .min(0.01)
        .defaultValue(14.999)
        .build()
    );

    public final Setting<Double> verticalAccelerationPlateau = sgGeneral.add(new DoubleSetting.Builder()
        .name("vertical-acceleration-plateau")
        .description("The vertical speed where acceleration will tend to 0")
        .min(0.01)
        .defaultValue(29.999)
        .build()
    );

    public final Setting<Boolean> accelerateUpward = sgGeneral.add(new BoolSetting.Builder()
        .name("accelerate-upward")
        .description("Also accelerates upwards. Not recommended if vertical speed goes above 7.999")
        .defaultValue(false)
        .build()
    );

    public final Setting<Integer> accelerationDelay = sgGeneral.add(new IntSetting.Builder()
        .name("acceleration-delay")
        .description("Adds a slight delay before accelerating. 1 Tick is necessary to avoid getting stuck.")
        .min(0)
        .sliderMax(100)
        .defaultValue(1)
        .build()
    );

    public final Setting<Double> accelerationStep = sgGeneral.add(new DoubleSetting.Builder()
        .name("horizontal-acceleration-step")
        .description("How fast horizontal speed ramps up")
        .min(0.01)
        .max(5)
        .defaultValue(0.3)
        .build()
    );

    public final Setting<Double> verticalAccelerationStep = sgGeneral.add(new DoubleSetting.Builder()
        .name("vertical-acceleration-step")
        .description("How fast vertical speed ramps up")
        .min(0.01)
        .max(5)
        .defaultValue(1.0)
        .build()
    );

    public final Setting<Boolean> limitMaxHeight = sgGeneral.add(new BoolSetting.Builder()
        .name("limit-max-height")
        .description("Stops you from flying above a set height")
        .defaultValue(true)
        .build()
    );

    public final Setting<Double> maxHeight = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-height")
        .description("The max height that you will be able to reach")
        .defaultValue(500.0)
        .min(-128)
        .sliderMax(500)
        .visible(limitMaxHeight::get)
        .build()
    );

    public final Setting<Boolean> autoTakeOff = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-take-off")
        .description("Takes off automatically without needing to double jump")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> stopInWater = sgGeneral.add(new BoolSetting.Builder()
        .name("stop-in-water")
        .description("Stops flying when you touch water")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> dontGoIntoUnloadedChunks = sgGeneral.add(new BoolSetting.Builder()
        .name("no-unloaded-chunks")
        .description("Stops you from flying into unloaded chunks")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> noCrash = sgGeneral.add(new BoolSetting.Builder()
        .name("no-crash")
        .description("Stops you from flying into walls")
        .defaultValue(false)
        .build()
    );

    public final Setting<Integer> crashLookAhead = sgGeneral.add(new IntSetting.Builder()
        .name("crash-look-ahead")
        .description("Distance to look ahead for walls")
        .defaultValue(3)
        .range(1, 15)
        .sliderMin(1)
        .visible(noCrash::get)
        .build()
    );

    private final Setting<Boolean> instaDrop = sgGeneral.add(new BoolSetting.Builder()
        .name("insta-drop")
        .description("Instantly drops you out of flight")
        .defaultValue(false)
        .build()
    );

    public final Setting<Double> fallMultiplier = sgGeneral.add(new DoubleSetting.Builder()
        .name("fall-multiplier")
        .description("Multiplier for how fast you fall naturally")
        .defaultValue(0)
        .min(0)
        .build()
    );

    public final Setting<Boolean> replace = sgInventory.add(new BoolSetting.Builder()
        .name("elytra-replace")
        .description("Replaces a broken elytra with a new one")
        .defaultValue(false)
        .build()
    );

    public final Setting<Integer> replaceDurability = sgInventory.add(new IntSetting.Builder()
        .name("replace-durability")
        .description("Durability left on the elytra before it's replaced")
        .defaultValue(2)
        .sliderRange(1, 500)
        .visible(replace::get)
        .build()
    );

    public final Setting<ChestSwapMode> chestSwap = sgInventory.add(new EnumSetting.Builder<ChestSwapMode>()
        .name("chest-swap")
        .description("Swaps to an elytra when toggling this module")
        .defaultValue(ChestSwapMode.Never)
        .build()
    );

    public final Setting<Boolean> autoReplenish = sgInventory.add(new BoolSetting.Builder()
        .name("replenish-fireworks")
        .description("Moves fireworks into a chosen hotbar slot")
        .defaultValue(false)
        .build()
    );

    public final Setting<Integer> replenishSlot = sgInventory.add(new IntSetting.Builder()
        .name("replenish-slot")
        .description("Hotbar slot to move fireworks into")
        .defaultValue(9)
        .range(1, 9)
        .sliderRange(1, 9)
        .visible(autoReplenish::get)
        .build()
    );

    public final Setting<Boolean> autoPilot = sgAutopilot.add(new BoolSetting.Builder()
        .name("auto-pilot")
        .description("Moves forward automatically while elytra flying")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> useFireworks = sgAutopilot.add(new BoolSetting.Builder()
        .name("use-fireworks")
        .description("Uses fireworks automatically at an interval")
        .defaultValue(false)
        .visible(autoPilot::get)
        .build()
    );

    public final Setting<Double> autoPilotFireworkDelay = sgAutopilot.add(new DoubleSetting.Builder()
        .name("firework-delay")
        .description("Seconds between automatic firework uses")
        .min(1)
        .defaultValue(8)
        .sliderMax(20)
        .visible(useFireworks::get)
        .build()
    );

    public final Setting<Double> autoPilotMinimumHeight = sgAutopilot.add(new DoubleSetting.Builder()
        .name("minimum-height")
        .description("Minimum height autopilot needs before it flies forward")
        .defaultValue(120)
        .min(-128)
        .sliderMax(260)
        .visible(autoPilot::get)
        .build()
    );

    private boolean lastJumpPressed;
    private boolean incrementJumpTimer;
    private boolean lastForwardPressed;
    private int jumpTimer;
    private double velX, velY, velZ;
    private double ticksLeft;
    private Vec3d forward, right;
    private double acceleration;
    private boolean atMaxSpeed;
    private int accelerationDelayTicks;
    private double verticalAcceleration;
    private boolean atMaxVerticalSpeed;
    private int verticalAccelerationDelayTicks;
    private boolean mappingWaitingForChunks;

    private boolean buildingModeEngaged;
    private double buildingModeEntryHorizontalSpeed;
    private double buildingModeEntryVerticalSpeed;
    private int buildingModeTicksElapsed;
    private static final int BUILDING_MODE_SLOWDOWN_TICKS = 5;

    private boolean avoidanceSteering;
    private Vec3d avoidanceLateralDir;

    private int avoidanceStuckTicksCount;
    private static final int VERTICAL_STEP_TIMEOUT_TICKS = 40;
    private boolean verticalStepActive;
    private boolean verticalStepUp;
    private double verticalStepStartY;
    private int verticalStepTicks;

    public Efly() {
        super(SixToolsAddon.CATEGORY, "efly", "Specifically designed to maximise elytrafly capabilities and speed on 6b6t");
    }

    @Override
    public void onActivate() {
        atMaxSpeed = false;
        lastJumpPressed = false;
        jumpTimer = 0;
        ticksLeft = 0;
        accelerationDelayTicks = 0;
        acceleration = startSpeed.get();

        atMaxVerticalSpeed = false;
        verticalAccelerationDelayTicks = 0;
        verticalAcceleration = verticalStartSpeed.get();

        buildingModeEngaged = false;

        PlayerEntity player = mc.player;
        if (player == null) return;

        if ((chestSwap.get() == ChestSwapMode.Always || chestSwap.get() == ChestSwapMode.WaitForGround)
            && player.getEquippedStack(EquipmentSlot.CHEST).getItem() != Items.ELYTRA && isActive()) {
            swapToChestSwap();
        }
    }

    @Override
    public void onDeactivate() {
        mappingWaitingForChunks = false;

        if (autoPilot.get() && !isKeyPhysicallyPressed(mc.options.forwardKey)) mc.options.forwardKey.setPressed(false);
        releaseAvoidance();
        releaseVerticalStep();

        PlayerEntity player = mc.player;
        if (player == null) return;

        if (chestSwap.get() == ChestSwapMode.Always && player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {
            swapToChestSwap();
        } else if (chestSwap.get() == ChestSwapMode.WaitForGround) {
            enableGroundListener();
        }

        if (player.isGliding() && instaDrop.get()) {
            enableInstaDropListener();
        }
    }

    private void swapToChestSwap() {
        ChestSwap chestSwapModule = Modules.get().get(ChestSwap.class);
        if (chestSwapModule != null) chestSwapModule.swap();
    }

    @EventHandler
    @SuppressWarnings("unused")
    private void onPlayerMove(PlayerMoveEvent event) {
        PlayerEntity player = mc.player;
        ClientWorld world = mc.world;
        if (player == null || world == null) return;

        if (!(player.getEquippedStack(EquipmentSlot.CHEST).contains(DataComponentTypes.GLIDER))) return;

        autoTakeoff();
        updatePlayerAvoidance();

        if (player.isGliding()) {
            velX = 0;
            velY = event.movement.y;
            velZ = 0;
            forward = Vec3d.fromPolar(0, player.getYaw()).multiply(0.1);
            right = Vec3d.fromPolar(0, player.getYaw() + 90).multiply(0.1);

            if (player.isTouchingWater() && stopInWater.get()) {
                ClientPlayNetworkHandler networkHandler = mc.getNetworkHandler();
                if (networkHandler != null) {
                    networkHandler.sendPacket(new ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                }
                return;
            }

            handleFallMultiplier();
            handleAutopilot();

            handleAcceleration();
            handleVerticalAcceleration();
            handleHorizontalSpeed();
            handleVerticalSpeed();
            handleLandGently();
            handleBuildingMode();
            handleMaxHeight();

            int chunkX = (int) ((player.getX() + velX) / 16);
            int chunkZ = (int) ((player.getZ() + velZ) / 16);
            if (dontGoIntoUnloadedChunks.get()) {
                if (world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
                    ((IVec3d) event.movement).meteor$set(velX, velY, velZ);
                } else {

                    ((IVec3d) event.movement).meteor$set(0, velY, 0);
                }
            } else {
                ((IVec3d) event.movement).meteor$set(velX, velY, velZ);
            }
        } else {
            mappingWaitingForChunks = false;

            if (lastForwardPressed) {
                if (!isKeyPhysicallyPressed(mc.options.forwardKey)) mc.options.forwardKey.setPressed(false);
                lastForwardPressed = false;
            }
        }

        if (noCrash.get() && player.isGliding()) {
            Vec3d lookAheadPos = player.getEntityPos().add(player.getVelocity().normalize().multiply(crashLookAhead.get()));
            RaycastContext raycastContext = new RaycastContext(player.getEntityPos(), new Vec3d(lookAheadPos.getX(), player.getY(), lookAheadPos.getZ()), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player);
            BlockHitResult hitResult = world.raycast(raycastContext);
            if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
                ((IVec3d) event.movement).meteor$set(0, velY, 0);
            }
        }

    }

    @EventHandler
    @SuppressWarnings("unused")
    private void onTick(@SuppressWarnings("unused") TickEvent.Post event) {
        if (autoReplenish.get()) {
            FindItemResult fireworks = InvUtils.find(Items.FIREWORK_ROCKET);

            if (fireworks.found() && !fireworks.isHotbar()) {
                InvUtils.move().from(fireworks.slot()).toHotbar(replenishSlot.get() - 1);
            }
        }

        PlayerEntity player = mc.player;
        if (replace.get() && player != null) {
            var chestStack = player.getEquippedStack(EquipmentSlot.CHEST);

            if (chestStack.getItem() == Items.ELYTRA) {
                if (chestStack.getMaxDamage() - chestStack.getDamage() <= replaceDurability.get()) {
                    FindItemResult elytra = InvUtils.find(stack -> stack.getMaxDamage() - stack.getDamage() > replaceDurability.get() && stack.getItem() == Items.ELYTRA);

                    InvUtils.move().from(elytra.slot()).toArmor(2);
                }
            }
        }
    }

    @EventHandler
    @SuppressWarnings("unused")
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof PlayerPositionLookS2CPacket) zeroAcceleration();
    }

    @EventHandler
    @SuppressWarnings("unused")
    private void onRender2D(Render2DEvent event) {
        if (!mappingMode.get() || !mappingWaitingForChunks || mappingHideWarning.get()) return;

        String text = "Efly: Waiting for chunks to load (render radius: " + mappingRenderRadius.get() + ")";
        int orange = 0xFFFFA500;

        int x = (mc.getWindow().getScaledWidth() - mc.textRenderer.getWidth(text)) / 2;
        int y = mc.getWindow().getScaledHeight() / 2 + 20;

        event.drawContext.drawText(mc.textRenderer, text, x, y, orange, true);
    }

    private void autoTakeoff() {
        PlayerEntity player = mc.player;
        if (player == null) return;

        if (incrementJumpTimer) jumpTimer++;

        boolean jumpPressed = mc.options.jumpKey.isPressed();

        if (autoTakeOff.get() && jumpPressed) {
            if (!lastJumpPressed && !player.isGliding()) {
                jumpTimer = 0;
                incrementJumpTimer = true;
            }

            if (jumpTimer >= 8) {
                jumpTimer = 0;
                incrementJumpTimer = false;
                player.setJumping(false);
                player.setSprinting(true);
                player.jump();

                ClientPlayNetworkHandler networkHandler = mc.getNetworkHandler();
                if (networkHandler != null) {
                    networkHandler.sendPacket(new ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                }
            }
        }

        lastJumpPressed = jumpPressed;
    }

    private void handleAutopilot() {
        PlayerEntity player = mc.player;
        if (player == null || !player.isGliding()) return;

        if (autoPilot.get() && !avoidanceSteering && player.getY() > autoPilotMinimumHeight.get()) {
            mc.options.forwardKey.setPressed(true);
            lastForwardPressed = true;
        }

        if (useFireworks.get()) {
            if (ticksLeft <= 0) {
                ticksLeft = autoPilotFireworkDelay.get() * 20;

                FindItemResult itemResult = InvUtils.findInHotbar(Items.FIREWORK_ROCKET);
                if (!itemResult.found()) return;

                ClientPlayerInteractionManager interactionManager = mc.interactionManager;
                if (interactionManager == null) return;

                if (itemResult.isOffhand()) {
                    interactionManager.interactItem(player, Hand.OFF_HAND);
                    player.swingHand(Hand.OFF_HAND);
                } else {
                    InvUtils.swap(itemResult.slot(), true);

                    interactionManager.interactItem(player, Hand.MAIN_HAND);
                    player.swingHand(Hand.MAIN_HAND);

                    InvUtils.swapBack();
                }
            }
            ticksLeft--;
        }
    }

    private void handleHorizontalSpeed() {
        boolean a = false;
        boolean b = false;

        if (mc.options.forwardKey.isPressed()) {
            velX += forward.x * getSpeed() * 10;
            velZ += forward.z * getSpeed() * 10;
            a = true;
        } else if (mc.options.backKey.isPressed()) {
            velX -= forward.x * getSpeed() * 10;
            velZ -= forward.z * getSpeed() * 10;
            a = true;
        }

        if (mc.options.rightKey.isPressed()) {
            velX += right.x * getSpeed() * 10;
            velZ += right.z * getSpeed() * 10;
            b = true;
        } else if (mc.options.leftKey.isPressed()) {
            velX -= right.x * getSpeed() * 10;
            velZ -= right.z * getSpeed() * 10;
            b = true;
        }

        if (a && b) {
            double diagonal = 1 / Math.sqrt(2);
            velX *= diagonal;
            velZ *= diagonal;
        }

        if (mappingMode.get()) {
            mappingWaitingForChunks = !areChunksLoadedInRadius(mappingRenderRadius.get());

            if (mappingWaitingForChunks) {
                velX = 0;
                velZ = 0;

                resetHorizontalAcceleration();
            }
        } else {
            mappingWaitingForChunks = false;
        }
    }

    private boolean areChunksLoadedInRadius(int radius) {
        if (mc.player == null || mc.world == null) return false;

        int centerX = (int) Math.floor(mc.player.getX()) >> 4;
        int centerZ = (int) Math.floor(mc.player.getZ()) >> 4;
        int radiusSq = radius * radius;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radiusSq) continue;
                if (!mc.world.getChunkManager().isChunkLoaded(centerX + dx, centerZ + dz)) return false;
            }
        }

        return true;
    }

    private void handleVerticalSpeed() {
        if (mc.options.jumpKey.isPressed()) velY += 0.5 * getVerticalSpeed();
        else if (mc.options.sneakKey.isPressed()) velY -= 0.5 * getVerticalSpeed();
    }

    private void handleFallMultiplier() {
        if (velY < 0) velY *= fallMultiplier.get();
        else if (velY > 0) velY = 0;
    }

    private void handleLandGently() {
        if (!landGently.get() || velY >= 0) return;

        double currentSpeed = -velY;
        double minSpeed = landGentlyMinSpeed.get();
        if (currentSpeed <= minSpeed) return;

        double startDist = landGentlyDistance.get();
        double minDist = Math.min(landGentlyMinDistance.get(), startDist);

        double distanceToGround = distanceToGroundBelow(startDist);
        if (distanceToGround >= startDist) return;

        double allowedSpeed;
        if (distanceToGround <= minDist) {
            allowedSpeed = minSpeed;
        } else {
            double t = (distanceToGround - minDist) / Math.max(startDist - minDist, 0.0001);
            allowedSpeed = minSpeed + t * (currentSpeed - minSpeed);
        }

        if (allowedSpeed < currentSpeed) velY = -allowedSpeed;
    }

    private double distanceToGroundBelow(double maxDistance) {
        PlayerEntity player = mc.player;
        ClientWorld world = mc.world;
        if (player == null || world == null) return maxDistance;

        Box box = player.getBoundingBox();
        double y = player.getY();

        double[] xs = {box.minX, (box.minX + box.maxX) / 2.0, box.maxX};
        double[] zs = {box.minZ, (box.minZ + box.maxZ) / 2.0, box.maxZ};

        double nearest = maxDistance;

        for (double x : xs) {
            for (double z : zs) {
                Vec3d start = new Vec3d(x, y, z);
                Vec3d end = start.add(0, -maxDistance, 0);

                RaycastContext raycastContext = new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player);
                BlockHitResult hitResult = world.raycast(raycastContext);

                if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK) continue;

                double distance = y - hitResult.getPos().y;
                if (distance < nearest) nearest = distance;
            }
        }

        return nearest;
    }

    private void handleBuildingMode() {
        if (!buildingMode.get() || !isNearAnyBlock(buildingModeDistance.get())) {
            buildingModeEngaged = false;
            return;
        }

        zeroAcceleration();

        double minSpeed = buildingModeMinSpeed.get();
        double verticalTarget = minSpeed * 1.5;

        double horizontalSpeed = Math.hypot(velX, velZ);
        double verticalSpeed = Math.abs(velY);

        if (!buildingModeEngaged) {

            buildingModeEntryHorizontalSpeed = Math.max(horizontalSpeed, minSpeed);
            buildingModeEntryVerticalSpeed = Math.max(verticalSpeed, verticalTarget);
            buildingModeTicksElapsed = 0;
            buildingModeEngaged = true;
        } else {
            buildingModeTicksElapsed++;
        }

        double t = Math.min(1.0, buildingModeTicksElapsed / (double) BUILDING_MODE_SLOWDOWN_TICKS);
        double buildingModeHorizontalCap = buildingModeEntryHorizontalSpeed + (minSpeed - buildingModeEntryHorizontalSpeed) * t;
        double buildingModeVerticalCap = buildingModeEntryVerticalSpeed + (verticalTarget - buildingModeEntryVerticalSpeed) * t;

        if (horizontalSpeed > buildingModeHorizontalCap) {
            double scale = buildingModeHorizontalCap / horizontalSpeed;
            velX *= scale;
            velZ *= scale;
        }

        if (verticalSpeed > buildingModeVerticalCap) {
            velY = Math.signum(velY) * buildingModeVerticalCap;
        }
    }

    private boolean isNearAnyBlock(double distance) {
        PlayerEntity player = mc.player;
        ClientWorld world = mc.world;
        if (player == null || world == null) return false;

        Box box = player.getBoundingBox().expand(distance);
        return world.getBlockCollisions(player, box).iterator().hasNext();
    }

    private void handleMaxHeight() {
        if (!limitMaxHeight.get() || velY <= 0) return;

        PlayerEntity player = mc.player;
        if (player == null) return;

        double limit = maxHeight.get();
        double currentY = player.getY();

        if (currentY >= limit) {
            velY = 0;
        } else if (currentY + velY > limit) {
            velY = limit - currentY;
        }
    }

    private void handleAcceleration() {
        boolean movementKeyPressed = mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed()
            || mc.options.leftKey.isPressed() || mc.options.rightKey.isPressed();

        if (!movementKeyPressed) {

            resetHorizontalAcceleration();
            return;
        }

        if (atMaxSpeed) return;

        if (accelerationDelayTicks < accelerationDelay.get()) {

            accelerationDelayTicks++;
            return;
        }

        double plateau = accelerationPlateau.get();
        double remainingToPlateau = Math.max(0, plateau - acceleration);
        double gain = accelerationStep.get() * (remainingToPlateau / plateau);

        acceleration = Math.min(acceleration + gain, horizontalSpeed.get());

        if (acceleration >= horizontalSpeed.get()) {
            atMaxSpeed = true;
        }
    }

    private void zeroAcceleration() {
        resetHorizontalAcceleration();

        atMaxVerticalSpeed = false;
        verticalAccelerationDelayTicks = 0;
        verticalAcceleration = verticalStartSpeed.get();
    }

    private void resetHorizontalAcceleration() {
        atMaxSpeed = false;
        accelerationDelayTicks = 0;
        acceleration = startSpeed.get();
    }

    private double getSpeed() {
        return acceleration;
    }

    private void handleVerticalAcceleration() {
        boolean movingUp = mc.options.jumpKey.isPressed();
        boolean movingDown = mc.options.sneakKey.isPressed();

        if (!movingUp && !movingDown) {

            atMaxVerticalSpeed = false;
            verticalAccelerationDelayTicks = 0;
            verticalAcceleration = verticalStartSpeed.get();
            return;
        }

        if (movingUp && !accelerateUpward.get()) {

            atMaxVerticalSpeed = false;
            verticalAccelerationDelayTicks = 0;
            verticalAcceleration = verticalStartSpeed.get();
            return;
        }

        if (atMaxVerticalSpeed) return;

        if (verticalAccelerationDelayTicks < accelerationDelay.get()) {

            verticalAccelerationDelayTicks++;
            return;
        }

        double plateau = verticalAccelerationPlateau.get();
        double remainingToPlateau = Math.max(0, plateau - verticalAcceleration);
        double gain = verticalAccelerationStep.get() * (remainingToPlateau / plateau);

        verticalAcceleration = Math.min(verticalAcceleration + gain, verticalSpeed.get());

        if (verticalAcceleration >= verticalSpeed.get()) {
            atMaxVerticalSpeed = true;
        }
    }

    private double getVerticalSpeed() {
        return verticalAcceleration;
    }

    private void updatePlayerAvoidance() {
        if (!playerAvoidance.get()) {
            releaseAvoidance();
            releaseVerticalStep();
            return;
        }

        if (isMovementKeyPhysicallyPressed() || isKeyPhysicallyPressed(mc.options.sneakKey)) {

            releaseAvoidanceExceptPhysical();
            releaseVerticalStep();
            return;
        }

        Vec3d away = findAvoidanceAwayVector();
        if (away == null) {
            releaseAvoidance();
            releaseVerticalStep();
            return;
        }

        PlayerEntity player = mc.player;
        if (player == null || !player.isGliding()) return;

        if (verticalStepActive) {

            avoidanceSteering = true;
            stepVerticalStep();
            return;
        }

        steerTowardsDirection(away);
        avoidanceSteering = true;

        if (avoidanceVerticalStep.get()) updateAvoidanceStuckDetection();
    }

    private void steerTowardsDirection(Vec3d dir) {
        PlayerEntity player = mc.player;
        if (player == null) return;

        double targetYaw = Math.toDegrees(Math.atan2(-dir.x, dir.z));
        double relative = wrapDegrees(targetYaw - player.getYaw());

        int octant = ((int) Math.round(relative / 45.0) % 8 + 8) % 8;

        mc.options.forwardKey.setPressed(octant == 0 || octant == 1 || octant == 7);
        mc.options.backKey.setPressed(octant == 3 || octant == 4 || octant == 5);
        mc.options.rightKey.setPressed(octant == 1 || octant == 2 || octant == 3);
        mc.options.leftKey.setPressed(octant == 5 || octant == 6 || octant == 7);
    }

    private Vec3d findAvoidanceAwayVector() {
        PlayerEntity self = mc.player;
        ClientWorld world = mc.world;
        if (self == null || world == null) return null;

        double[] acc = {0, 0};
        boolean foundThreat = false;

        for (PlayerEntity player : world.getPlayers()) {
            if (player == self) continue;
            if (avoidanceIgnoreFriends.get() && Friends.get().isFriend(player)) continue;

            if (accumulateThreat(player.getEntityPos(), avoidanceRadius.get(), acc)) foundThreat = true;
        }

        if (avoidWitherSkulls.get() || avoidArrows.get()) {
            for (Entity entity : world.getEntities()) {
                if (avoidWitherSkulls.get() && entity instanceof WitherSkullEntity) {
                    if (accumulateThreat(entity.getEntityPos(), witherSkullRadius.get(), acc)) foundThreat = true;
                } else if (avoidArrows.get() && entity instanceof ArrowEntity) {
                    if (accumulateThreat(entity.getEntityPos(), arrowRadius.get(), acc)) foundThreat = true;
                }
            }
        }

        if (avoidBlocks.get() && isBlockAvoidanceTriggered()) {
            if (accumulateNearbyBlockThreats(blockAvoidanceRadius.get(), acc)) foundThreat = true;
        }

        if (!foundThreat) return null;

        Vec3d direction = new Vec3d(acc[0], 0, acc[1]);
        if (direction.lengthSquared() == 0) return null;

        direction = direction.normalize();

        if (avoidanceLateral.get()) {
            direction = lateralize(direction);
        } else {
            avoidanceLateralDir = null;
        }

        return direction;
    }

    private boolean isPlayerWithinAvoidanceRadius() {
        PlayerEntity self = mc.player;
        ClientWorld world = mc.world;
        if (self == null || world == null) return false;

        double radius = avoidanceRadius.get();
        if (radius <= 0) return false;

        double radiusSq = radius * radius;

        for (PlayerEntity player : world.getPlayers()) {
            if (player == self) continue;
            if (avoidanceIgnoreFriends.get() && Friends.get().isFriend(player)) continue;

            if (self.getEntityPos().squaredDistanceTo(player.getEntityPos()) < radiusSq) return true;
        }

        return false;
    }

    private boolean isWitherSkullWithinAvoidanceRadius() {
        if (!avoidWitherSkulls.get()) return false;

        PlayerEntity self = mc.player;
        ClientWorld world = mc.world;
        if (self == null || world == null) return false;

        double radius = witherSkullRadius.get();
        if (radius <= 0) return false;

        double radiusSq = radius * radius;

        for (Entity entity : world.getEntities()) {
            if (!(entity instanceof WitherSkullEntity)) continue;
            if (self.getEntityPos().squaredDistanceTo(entity.getEntityPos()) < radiusSq) return true;
        }

        return false;
    }

    private boolean isBlockAvoidanceTriggered() {
        return isPlayerWithinAvoidanceRadius() || isWitherSkullWithinAvoidanceRadius();
    }

    private Vec3d lateralize(Vec3d away) {
        Vec3d perpA = new Vec3d(-away.z, 0, away.x);
        Vec3d perpB = new Vec3d(away.z, 0, -away.x);

        boolean sideA;
        if (avoidanceLateralDir != null) {
            sideA = avoidanceLateralDir.dotProduct(perpA) >= avoidanceLateralDir.dotProduct(perpB);
        } else {
            PlayerEntity player = mc.player;
            Vec3d vel = player != null ? player.getVelocity() : Vec3d.ZERO;
            Vec3d horizontalVel = new Vec3d(vel.x, 0, vel.z);
            sideA = !(horizontalVel.lengthSquared() > 1.0E-4 && horizontalVel.dotProduct(perpB) > horizontalVel.dotProduct(perpA));
        }

        double baseAngle = sideA ? 90 : -90;
        Vec3d optionA = rotateHorizontal(away, baseAngle - 10);
        Vec3d optionB = rotateHorizontal(away, baseAngle + 10);
        Vec3d chosen = optionA.dotProduct(away) >= optionB.dotProduct(away) ? optionA : optionB;

        avoidanceLateralDir = chosen;
        return chosen;
    }

    private Vec3d rotateHorizontal(Vec3d v, double degrees) {
        double rad = Math.toRadians(degrees);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);

        return new Vec3d(v.x * cos - v.z * sin, 0, v.x * sin + v.z * cos);
    }

    private boolean accumulateThreat(Vec3d threatPos, double radius, double[] acc) {
        if (radius <= 0) return false;

        PlayerEntity player = mc.player;
        if (player == null) return false;

        double distanceSq = player.getEntityPos().squaredDistanceTo(threatPos);
        if (distanceSq >= radius * radius || distanceSq == 0) return false;

        Vec3d awayFromThreat = player.getEntityPos().subtract(threatPos);
        awayFromThreat = new Vec3d(awayFromThreat.x, 0, awayFromThreat.z);
        if (awayFromThreat.lengthSquared() == 0) return false;

        double weight = 1 - (Math.sqrt(distanceSq) / radius);
        awayFromThreat = awayFromThreat.normalize().multiply(weight);

        acc[0] += awayFromThreat.x;
        acc[1] += awayFromThreat.z;
        return true;
    }

    private boolean accumulateNearbyBlockThreats(double radius, double[] acc) {
        if (radius <= 0) return false;

        PlayerEntity player = mc.player;
        ClientWorld world = mc.world;
        if (player == null || world == null) return false;

        boolean foundThreat = false;
        BlockPos center = player.getBlockPos();
        int r = (int) Math.ceil(radius);

        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = 0; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    BlockState state = world.getBlockState(pos);

                    boolean isCobweb = state.isOf(Blocks.COBWEB);
                    if (!isCobweb && state.getCollisionShape(world, pos).isEmpty()) continue;

                    Vec3d blockCenter = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    if (accumulateThreat(blockCenter, radius, acc)) foundThreat = true;
                }
            }
        }

        return foundThreat;
    }

    private void updateAvoidanceStuckDetection() {
        PlayerEntity player = mc.player;
        if (player == null) return;

        avoidanceStuckTicksCount = player.horizontalCollision ? avoidanceStuckTicksCount + 1 : 0;
        if (avoidanceStuckTicksCount < avoidanceStuckTicks.get()) return;

        beginVerticalStep();
    }

    private void beginVerticalStep() {
        PlayerEntity player = mc.player;
        if (player == null) return;

        boolean upClear = hasVerticalClearance(1.0);
        boolean downClear = hasVerticalClearance(-1.0);

        if (upClear) {
            verticalStepUp = true;
        } else if (downClear) {
            verticalStepUp = false;
        } else {
            avoidanceStuckTicksCount = 0;
            return;
        }

        verticalStepActive = true;
        verticalStepStartY = player.getY();
        verticalStepTicks = 0;
        avoidanceStuckTicksCount = 0;
        releaseHorizontalKeys();
    }

    private boolean hasVerticalClearance(double dy) {
        PlayerEntity player = mc.player;
        ClientWorld world = mc.world;
        if (player == null || world == null) return false;

        Box box = player.getBoundingBox().offset(0, dy, 0);
        return !world.getBlockCollisions(player, box).iterator().hasNext();
    }

    private void stepVerticalStep() {
        PlayerEntity player = mc.player;
        if (player == null) {
            releaseVerticalStep();
            return;
        }

        verticalStepTicks++;

        double traveled = Math.abs(player.getY() - verticalStepStartY);
        if (traveled >= 1.0 || verticalStepTicks > VERTICAL_STEP_TIMEOUT_TICKS) {
            releaseVerticalStep();
            return;
        }

        if (!isKeyPhysicallyPressed(mc.options.jumpKey)) mc.options.jumpKey.setPressed(verticalStepUp);
        if (!isKeyPhysicallyPressed(mc.options.sneakKey)) mc.options.sneakKey.setPressed(!verticalStepUp);
    }

    private void releaseVerticalStep() {
        avoidanceStuckTicksCount = 0;

        if (verticalStepActive) {
            if (!isKeyPhysicallyPressed(mc.options.jumpKey)) mc.options.jumpKey.setPressed(false);
            if (!isKeyPhysicallyPressed(mc.options.sneakKey)) mc.options.sneakKey.setPressed(false);
        }

        verticalStepActive = false;
        verticalStepTicks = 0;
    }

    private void releaseHorizontalKeys() {
        if (!isKeyPhysicallyPressed(mc.options.forwardKey)) mc.options.forwardKey.setPressed(false);
        if (!isKeyPhysicallyPressed(mc.options.backKey)) mc.options.backKey.setPressed(false);
        if (!isKeyPhysicallyPressed(mc.options.leftKey)) mc.options.leftKey.setPressed(false);
        if (!isKeyPhysicallyPressed(mc.options.rightKey)) mc.options.rightKey.setPressed(false);
    }

    private void releaseAvoidance() {
        avoidanceLateralDir = null;

        if (!avoidanceSteering) return;
        if (!isKeyPhysicallyPressed(mc.options.forwardKey)) mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        avoidanceSteering = false;
    }

    private void releaseAvoidanceExceptPhysical() {
        avoidanceLateralDir = null;

        if (!avoidanceSteering) return;

        if (!isKeyPhysicallyPressed(mc.options.forwardKey)) mc.options.forwardKey.setPressed(false);
        if (!isKeyPhysicallyPressed(mc.options.backKey)) mc.options.backKey.setPressed(false);
        if (!isKeyPhysicallyPressed(mc.options.leftKey)) mc.options.leftKey.setPressed(false);
        if (!isKeyPhysicallyPressed(mc.options.rightKey)) mc.options.rightKey.setPressed(false);

        avoidanceSteering = false;
    }

    private boolean isMovementKeyPhysicallyPressed() {
        return isKeyPhysicallyPressed(mc.options.forwardKey)
            || isKeyPhysicallyPressed(mc.options.backKey)
            || isKeyPhysicallyPressed(mc.options.leftKey)
            || isKeyPhysicallyPressed(mc.options.rightKey);
    }

    private boolean isKeyPhysicallyPressed(KeyBinding binding) {
        if (ForeverForward.isHolding(binding)) return true;

        if (mc.getWindow() == null) return binding.isPressed();

        InputUtil.Key key = InputUtil.fromTranslationKey(binding.getBoundKeyTranslationKey());
        if (key.getCategory() != InputUtil.Type.KEYSYM) return binding.isPressed();

        return InputUtil.isKeyPressed(mc.getWindow(), key.getCode());
    }

    private static double wrapDegrees(double degrees) {
        double wrapped = degrees % 360.0;
        if (wrapped >= 180.0) wrapped -= 360.0;
        if (wrapped < -180.0) wrapped += 360.0;
        return wrapped;
    }

    private class StaticGroundListener {
        @EventHandler
        @SuppressWarnings("unused")
        private void chestSwapGroundListener(@SuppressWarnings("unused") PlayerMoveEvent event) {
            PlayerEntity player = mc.player;
            if (player == null || !player.isOnGround()) return;

            if (player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {
                swapToChestSwap();
                disableGroundListener();
            }
        }
    }

    private final StaticGroundListener staticGroundListener = new StaticGroundListener();

    protected void enableGroundListener() {
        MeteorClient.EVENT_BUS.subscribe(staticGroundListener);
    }

    protected void disableGroundListener() {
        MeteorClient.EVENT_BUS.unsubscribe(staticGroundListener);
    }

    private class StaticInstaDropListener {
        @EventHandler
        @SuppressWarnings("unused")
        private void onInstadropTick(@SuppressWarnings("unused") TickEvent.Post event) {
            ClientPlayerEntity player = mc.player;
            if (player != null && player.isGliding()) {
                player.setVelocity(0, 0, 0);
                player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.OnGroundOnly(true, player.horizontalCollision));
            } else {
                disableInstaDropListener();
            }
        }
    }

    private final StaticInstaDropListener staticInstadropListener = new StaticInstaDropListener();

    protected void enableInstaDropListener() {
        MeteorClient.EVENT_BUS.subscribe(staticInstadropListener);
    }

    protected void disableInstaDropListener() {
        MeteorClient.EVENT_BUS.unsubscribe(staticInstadropListener);
    }

    public enum ChestSwapMode {
        Always,
        Never,
        WaitForGround
    }

}
