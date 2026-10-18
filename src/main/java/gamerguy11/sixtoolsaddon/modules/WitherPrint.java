package gamerguy11.sixtoolsaddon.modules;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;

public final class WitherPrint extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPlacement = settings.createGroup("Placement");
    private final SettingGroup sgInventory = settings.createGroup("Inventory");
    private final SettingGroup sgRestock = settings.createGroup("Auto Restock");
    private final SettingGroup sgElytra = settings.createGroup("Efly");
    private final SettingGroup sgNaming = settings.createGroup("Wither Naming");
    private final SettingGroup sgReliability = settings.createGroup("Reliability");

    private final Setting<Boolean> autoWalk = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-walk")
        .description("Automatically holds forward while the Wither Printer is travelling and building. Restocking and the final Name Tag interaction temporarily take control when needed.")
        .defaultValue(true)
        .build()
    );

    private final Setting<DirectionMode> directionMode = sgGeneral.add(new EnumSetting.Builder<DirectionMode>()
        .name("direction-mode")
        .description("Motion follows your actual horizontal travel direction and falls back to look direction when nearly stationary. Look always uses the direction you are facing.")
        .defaultValue(DirectionMode.Motion)
        .build()
    );

    private final Setting<Double> behindDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("behind-distance")
        .description("Horizontal distance behind your exact player position used to choose the bottom soul block when Wither Naming is disabled. Supports decimals such as 1.5 and 2.5.")
        .defaultValue(2.5)
        .range(0.5, 6.0)
        .sliderRange(0.5, 6.0)
        .decimalPlaces(1)
        .build()
    );

    private final Setting<Integer> verticalOffset = sgGeneral.add(new IntSetting.Builder()
        .name("vertical-offset")
        .description("Y offset of the bottom soul block relative to your feet when Wither Naming is disabled.")
        .defaultValue(-1)
        .range(-5, 2)
        .sliderRange(-5, 2)
        .build()
    );

    private final Setting<Integer> spacing = sgGeneral.add(new IntSetting.Builder()
        .name("wither-spacing")
        .description("Minimum horizontal distance between completed wither build anchors.")
        .defaultValue(10)
        .range(3, 50)
        .sliderRange(3, 50)
        .build()
    );

    private final Setting<Boolean> stopWhenEmpty = sgGeneral.add(new BoolSetting.Builder()
        .name("stop-when-empty")
        .description("Disables the module when fewer than 4 soul blocks or 3 skulls remain.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> airPlace = sgPlacement.add(new BoolSetting.Builder()
        .name("air-place")
        .description("Allows the bottom soul block to be packet-placed in air when it has no neighboring support block.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotate = sgPlacement.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Sends a placement rotation through Meteor before each placement. Usually unnecessary in singleplayer; useful for stricter servers later.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> swing = sgPlacement.add(new BoolSetting.Builder()
        .name("swing")
        .description("Swings the hand when placing.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> maxPlaceRange = sgPlacement.add(new DoubleSetting.Builder()
        .name("max-place-range")
        .description("Maximum eye-to-target distance at which this module sends a placement attempt.")
        .defaultValue(4.5)
        .range(2.0, 6.0)
        .sliderRange(2.0, 6.0)
        .build()
    );

    private final Setting<Boolean> burstPlacement = sgPlacement.add(new BoolSetting.Builder()
        .name("burst-placement")
        .description("Places a Wither in three placement ticks: 2 lower soul blocks, then 2 soul arms, then all 3 skulls. Reliability checks can insert retry/wait ticks if a placement is not confirmed.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> ticksBetweenPlacements = sgPlacement.add(new IntSetting.Builder()
        .name("placement-delay")
        .description("Extra ticks between placement bursts. 0 means the next confirmed burst can be sent on the next game tick.")
        .defaultValue(0)
        .range(0, 10)
        .sliderRange(0, 10)
        .build()
    );

    private final Setting<Boolean> autoRefill = sgInventory.add(new BoolSetting.Builder()
        .name("auto-refill-hotbar")
        .description("Moves soul material and skull stacks from the main inventory into staging hotbar slots when no usable stack is already in the hotbar/offhand.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> soulStagingSlot = sgInventory.add(new IntSetting.Builder()
        .name("soul-staging-slot")
        .description("Hotbar slot used when soul material has to be pulled from the main inventory (1-9).")
        .defaultValue(8)
        .range(1, 9)
        .sliderRange(1, 9)
        .visible(autoRefill::get)
        .build()
    );

    private final Setting<Integer> skullStagingSlot = sgInventory.add(new IntSetting.Builder()
        .name("skull-staging-slot")
        .description("Hotbar slot used when skulls have to be pulled from the main inventory (1-9).")
        .defaultValue(9)
        .range(1, 9)
        .sliderRange(1, 9)
        .visible(autoRefill::get)
        .build()
    );

    private final Setting<Integer> refillDelay = sgInventory.add(new IntSetting.Builder()
        .name("refill-delay")
        .description("Ticks to wait after an inventory-to-hotbar move before trying to place.")
        .defaultValue(1)
        .range(0, 5)
        .sliderRange(0, 5)
        .visible(autoRefill::get)
        .build()
    );

    private final Setting<Boolean> autoRestock = sgRestock.add(new BoolSetting.Builder()
        .name("auto-restock")
        .description("When materials run low, flies forward, opens an Ender Chest, and smart-restocks only soul material and Wither Skeleton skulls from one supply shulker. Partial loads are balanced around the 4-soul / 3-skull Wither ratio.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> restockTravelDistance = sgRestock.add(new DoubleSetting.Builder()
        .name("restock-travel-distance")
        .description("Horizontal distance to fly at not-placing-speed before stopping for the Ender Chest refill.")
        .defaultValue(10.0)
        .range(5.0, 200.0)
        .sliderRange(10.0, 100.0)
        .decimalPlaces(1)
        .visible(autoRestock::get)
        .build()
    );

    private final Setting<Integer> restockStagingSlot = sgInventory.add(new IntSetting.Builder()
        .name("restock-staging-slot")
        .description("Hotbar slot used to stage the Ender Chest and supply shulker during automatic restocking (1-9).")
        .defaultValue(7)
        .range(1, 9)
        .sliderRange(1, 9)
        .visible(autoRestock::get)
        .build()
    );

    private final Setting<Integer> restockActionDelay = sgRestock.add(new IntSetting.Builder()
        .name("restock-action-delay")
        .description("Ticks between world interactions during restocking. 0 is the fastest path; increase it only if a server rejects interactions.")
        .defaultValue(0)
        .range(0, 10)
        .sliderRange(0, 5)
        .visible(autoRestock::get)
        .build()
    );

    private final Setting<Integer> restockSettleDelay = sgRestock.add(new IntSetting.Builder()
        .name("restock-settle-delay")
        .description("Ticks to allow server inventory updates to settle after the one-tick quick-transfer burst.")
        .defaultValue(1)
        .range(1, 20)
        .sliderRange(1, 10)
        .visible(autoRestock::get)
        .build()
    );

    private final Setting<Integer> restockTimeout = sgRestock.add(new IntSetting.Builder()
        .name("restock-timeout")
        .description("Maximum ticks to wait for a restock placement, container opening, or inventory synchronization stage.")
        .defaultValue(80)
        .range(20, 300)
        .sliderRange(20, 160)
        .visible(autoRestock::get)
        .build()
    );

    private final Setting<Boolean> nameWithers = sgNaming.add(new BoolSetting.Builder()
        .name("name-withers")
        .description("Uses already-renamed Name Tags from your inventory to name each newly spawned Wither.")
        .defaultValue(false)
        .build()
    );

    private final Setting<NamingMode> namingMode = sgNaming.add(new EnumSetting.Builder<NamingMode>()
        .name("name-mode")
        .description("Inventory Cycle loops through every distinct renamed Name Tag currently available. Specific uses only one exact configured name.")
        .defaultValue(NamingMode.InventoryCycle)
        .visible(nameWithers::get)
        .build()
    );

    private final Setting<String> specificWitherName = sgNaming.add(new StringSetting.Builder()
        .name("specific-name")
        .description("Exact existing Name Tag custom name to use in Specific mode. The module does not rename plain Name Tags.")
        .defaultValue("")
        .visible(() -> nameWithers.get() && namingMode.get() == NamingMode.Specific)
        .build()
    );

    private final Setting<Integer> nameTagStagingSlot = sgNaming.add(new IntSetting.Builder()
        .name("name-tag-staging-slot")
        .description("Hotbar slot used automatically when the required renamed Name Tag is only in the main inventory (1-9).")
        .defaultValue(6)
        .range(1, 9)
        .sliderRange(1, 9)
        .visible(nameWithers::get)
        .build()
    );

    private final Setting<Double> namingBehindDistance = sgNaming.add(new DoubleSetting.Builder()
        .name("naming-behind-distance")
        .description("Behind distance used for Wither build anchors while automatic Wither naming is enabled. The normal General behind-distance is used when naming is disabled.")
        .defaultValue(1.0)
        .range(0.5, 6.0)
        .sliderRange(0.5, 6.0)
        .decimalPlaces(1)
        .visible(nameWithers::get)
        .build()
    );

    private final Setting<Integer> namingVerticalOffset = sgNaming.add(new IntSetting.Builder()
        .name("naming-vertical-offset")
        .description("Vertical offset used for Wither build anchors while automatic Wither naming is enabled. The normal General vertical-offset is used when naming is disabled.")
        .defaultValue(-1)
        .range(-5, 2)
        .sliderRange(-5, 2)
        .visible(nameWithers::get)
        .build()
    );

    private final Setting<Double> namingPlacingSpeed = sgNaming.add(new DoubleSetting.Builder()
        .name("naming-placing-speed")
        .description("Efly horizontal speed used while building Withers when automatic naming is enabled. The normal placing-speed is used when naming is disabled. The final Name Tag interaction still temporarily holds at 0 speed for reliability.")
        .defaultValue(0.0)
        .range(0.0, 10.0)
        .sliderRange(0.0, 2.0)
        .decimalPlaces(2)
        .visible(nameWithers::get)
        .build()
    );

    private final Setting<Boolean> manageElytraSpeed = sgElytra.add(new BoolSetting.Builder()
        .name("manage-efly-speed")
        .description("Takes over Efly's horizontal speed while this module is running, depending on whether it is actively building a wither. Efly's own speed and acceleration settings are not changed.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> notPlacingSpeed = sgElytra.add(new DoubleSetting.Builder()
        .name("not-placing-speed")
        .description("Efly horizontal speed while waiting/travelling between wither build positions.")
        .defaultValue(2.0)
        .range(0.0, 10.0)
        .sliderRange(0.0, 5.0)
        .decimalPlaces(2)
        .visible(manageElytraSpeed::get)
        .build()
    );

    private final Setting<Double> placingSpeed = sgElytra.add(new DoubleSetting.Builder()
        .name("placing-speed")
        .description("Efly horizontal speed while building when Wither Naming is disabled. When naming is enabled, naming-placing-speed is used for the build and the actual Name Tag interaction temporarily holds at 0 speed.")
        .defaultValue(0.2)
        .range(0.0, 10.0)
        .sliderRange(0.0, 2.0)
        .decimalPlaces(2)
        .visible(manageElytraSpeed::get)
        .build()
    );

    private final Setting<Integer> confirmationTicks = sgReliability.add(new IntSetting.Builder()
        .name("confirmation-wait")
        .description("Ticks to wait for a placed block to appear before retrying it.")
        .defaultValue(1)
        .range(0, 10)
        .sliderRange(0, 5)
        .build()
    );

    private final Setting<Integer> maxRetries = sgReliability.add(new IntSetting.Builder()
        .name("max-retries")
        .description("Maximum placement attempts for one position before abandoning the current wither.")
        .defaultValue(4)
        .range(1, 12)
        .sliderRange(1, 8)
        .build()
    );

    private final Setting<Integer> spawnConfirmationTicks = sgReliability.add(new IntSetting.Builder()
        .name("spawn-confirmation-wait")
        .description("How long to look for a spawned Wither / consumed structure after placing the final skull.")
        .defaultValue(4)
        .range(2, 40)
        .sliderRange(2, 20)
        .build()
    );

    private WitherPlan plan;
    private int step;

    private int burstPhase;
    private int attempts;
    private int confirmCooldown;
    private int placementCooldown;
    private int inventoryCooldown;
    private int finalSpawnWait;
    private boolean finalSkullSent;
    private boolean finalSkullObservedPlaced;
    private boolean finalWitherBaselineCaptured;
    private final Set<UUID> withersBeforeFinalSkull = new HashSet<>();
    private BlockPos lastCompletedBase;
    private int completed;
    private boolean outOfMaterialsReported;

    private NamingState namingState = NamingState.Idle;
    private int pendingNameTagHotbarSlot = -1;
    private double lastNamingRangeDistance = -1.0;
    private UUID namingTargetUuid;
    private String pendingWitherName;
    private String lastCycledWitherName;
    private final List<String> cycleNameOrder = new ArrayList<>();
    private int namingTicks;
    private int namingConfirmCooldown;
    private int namingAttempts;
    private int namingOriginalSelectedSlot = -1;
    private int matchingNameTagsBeforeInteraction;

    private RestockState restockState = RestockState.Idle;
    private int restockStateTicks;
    private Vec3d restockTravelStart;
    private Direction restockTravelDirection = Direction.NORTH;
    private BlockPos restockEnderChestPos;
    private BlockPos restockShulkerPos;
    private ItemStack restockShulkerStack = ItemStack.EMPTY;
    private int restockEnderChestSourceSlot = -1;
    private int restockPlacementHotbarSlot = -1;
    private int restockShulkerChestSlot = -1;
    private int restockShulkerInventorySlot = -1;
    private int restockShulkerReceiveSlot = -1;
    private int restockContainerId = -1;
    private int restockOriginalSelectedSlot = -1;
    private boolean restockForwardWasDown;
    private boolean restockBackWasDown;
    private boolean restockLeftWasDown;
    private boolean restockRightWasDown;
    private boolean restockJumpWasDown;
    private boolean restockSneakWasDown;
    private boolean restockMovementCaptured;
    private boolean restockAutoWalkWasActive;
    private int restockDroppedStacks;
    private final List<Integer> restockTransferSlots = new ArrayList<>();
    private final List<Integer> restockPendingDropSlots = new ArrayList<>();
    private boolean restockPartialTransfer;
    private int restockPlannedSoulStacks;
    private int restockPlannedSkullStacks;
    private int restockIgnoredShulkerStacks;
    private int restockLeftoverStacks;
    private boolean resumeAtPlacingSpeed;

    private boolean autoWalkStateCaptured;
    private boolean autoWalkOriginalForwardDown;

    private Efly managedEfly;

    public WitherPrint() {
        super(SixToolsAddon.CATEGORY, "wither-print", "Continuously builds complete withers behind you while you fly with Efly and smart-restocks Soul/Skull materials from Ender Chest supply shulkers.");
    }

    @Override
    public void onActivate() {
        resetCurrentPlan();
        lastCompletedBase = null;
        completed = 0;
        outOfMaterialsReported = false;
        lastCycledWitherName = null;
        cycleNameOrder.clear();
        clearRestockState(false);
        resumeAtPlacingSpeed = false;
        releaseAutoWalkState();
        releaseManagedElytraState();
        syncAutoWalk();
        syncManagedElytraSpeed();

        if (autoRefill.get() && soulStagingSlot.get().equals(skullStagingSlot.get())) {
            error("Soul staging slot and skull staging slot must be different.");
            toggle();
        }
    }

    @Override
    public void onDeactivate() {
        resetCurrentPlan();
        clearRestockState(true);
        releaseAutoWalkState();
        releaseManagedElytraState();
    }

    @Override
    public String getInfoString() {
        if (restockState != RestockState.Idle) return completed + " | restock:" + restockState.label;
        if (namingState != NamingState.Idle) return completed + " | naming";
        if (plan == null) return Integer.toString(completed);
        return completed + " | " + stepName();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        syncManagedElytraSpeed();
        syncAutoWalk();

        if (mc.world.getDifficulty() == Difficulty.PEACEFUL) {
            error("Withers cannot spawn in Peaceful difficulty. Disabling Wither Printer.");
            toggle();
            return;
        }

        if (restockState != RestockState.Idle) {
            tickRestock();
            return;
        }

        if (namingState != NamingState.Idle) {
            tickWitherNaming();
            return;
        }

        if (mc.currentScreen != null) return;

        if (inventoryCooldown > 0) {
            inventoryCooldown--;
            return;
        }

        if (placementCooldown > 0) {
            placementCooldown--;
            return;
        }

        if (plan == null) {
            if (!hasMaterialsForCompleteWither()) {
                onOutOfMaterials();
                return;
            }

            outOfMaterialsReported = false;
            if (burstPlacement.get() && autoRefill.get() && !prepareBurstInventoryForPlan()) return;
            if (!tryCreatePlan()) return;
        }

        for (int guard = 0; guard < 8 && plan != null; guard++) {
            boolean advancedWithoutSending = burstPlacement.get() ? attemptBurstStep() : attemptStep();
            if (!advancedWithoutSending) return;
        }
    }

    private boolean attemptBurstStep() {
        if (burstPhase <= 0) {
            return handleBurstNormalPhase(
                new BlockPos[] { plan.soulPositions()[0], plan.soulPositions()[1] },
                MaterialKind.Soul,
                1,
                "lower soul pair"
            );
        }

        if (burstPhase == 1) {

            if (!isSoulBlock(plan.soulPositions()[0]) || !isSoulBlock(plan.soulPositions()[1])) {
                burstPhase = 0;
                resetStepAttemptState();
                return true;
            }

            return handleBurstNormalPhase(
                new BlockPos[] { plan.soulPositions()[2], plan.soulPositions()[3] },
                MaterialKind.Soul,
                2,
                "soul arm pair"
            );
        }

        if (burstPhase == 2) return handleBurstSkullPhase();

        finishCurrentWither();
        return false;
    }

    private boolean handleBurstNormalPhase(BlockPos[] positions, MaterialKind kind, int nextPhase, String label) {
        boolean allPresent = true;
        for (BlockPos pos : positions) {
            if (!isExpectedBlock(pos, kind)) {
                allPresent = false;
                break;
            }
        }

        if (allPresent) {
            burstPhase = nextPhase;
            resetStepAttemptState();
            return true;
        }

        if (confirmCooldown > 0) {
            confirmCooldown--;
            return false;
        }

        List<BlockPos> missing = new ArrayList<>();
        for (BlockPos pos : positions) {
            if (isExpectedBlock(pos, kind)) continue;
            if (!isReplaceable(pos)) {
                abortCurrent(label + " target became blocked at " + shortPos(pos));
                return false;
            }
            if (!withinRange(pos)) {
                attempts++;
                if (attempts >= maxRetries.get()) abortCurrent(label + " moved out of placement range");
                return false;
            }
            missing.add(pos);
        }

        if (attempts >= maxRetries.get()) {
            abortCurrent(label + " was not confirmed after " + maxRetries.get() + " attempts");
            return false;
        }

        if (!prepareBurstMaterial(kind, missing.size())) return false;

        int sent = 0;
        boolean blocked = false;
        for (BlockPos pos : missing) {
            PlacementAttempt result = place(pos, kind);
            if (result == PlacementAttempt.Sent) sent++;
            else if (result == PlacementAttempt.NoMaterial) {
                onOutOfMaterials();
                return false;
            } else if (result == PlacementAttempt.Refilled) {

                break;
            } else if (result == PlacementAttempt.Blocked) {
                blocked = true;
            }
        }

        if (sent > 0 || blocked) {
            attempts++;
            confirmCooldown = confirmationTicks.get();
            placementCooldown = ticksBetweenPlacements.get();
        }

        if (blocked && attempts >= maxRetries.get()) {
            abortCurrent(label + " placement remained blocked");
        }

        return false;
    }

    private boolean handleBurstSkullPhase() {

        if (!allSoulBlocksPresent() && !finalSkullSent) {
            int missing = firstMissingSoulIndex();
            burstPhase = missing >= 0 && missing < 2 ? 0 : 1;
            resetStepAttemptState();
            return true;
        }

        if (finalSkullSent) {

            if (burstSoulStructureConsumptionStarted() || newWitherSpawnObserved()) {
                finishCurrentWither();
                return false;
            }

            if (allSkullsPresent()) finalSkullObservedPlaced = true;

            if (finalSpawnWait > 0) {
                finalSpawnWait--;
                return false;
            }

            if (finalSkullObservedPlaced) {
                finishCurrentWither();
                return false;
            }

            finalSkullSent = false;
            finalSkullObservedPlaced = false;
            clearFinalWitherBaseline();
            confirmCooldown = 0;
            if (attempts >= maxRetries.get()) {
                abortCurrent("three-skull burst was not accepted after " + maxRetries.get() + " attempts");
                return false;
            }
        }

        if (confirmCooldown > 0) {
            confirmCooldown--;
            return false;
        }

        List<BlockPos> missing = new ArrayList<>();
        for (BlockPos pos : plan.skullPositions()) {
            if (isWitherSkull(pos)) continue;
            if (!isReplaceable(pos)) {
                abortCurrent("skull burst target became blocked at " + shortPos(pos));
                return false;
            }
            if (!withinRange(pos)) {
                attempts++;
                if (attempts >= maxRetries.get()) abortCurrent("skull burst moved out of placement range");
                return false;
            }
            missing.add(pos);
        }

        if (missing.isEmpty()) {

            finalSkullSent = true;
            finalSkullObservedPlaced = true;
            if (!finalWitherBaselineCaptured) captureFinalWitherBaseline();
            finalSpawnWait = spawnConfirmationTicks.get();
            return false;
        }

        if (attempts >= maxRetries.get()) {
            abortCurrent("skull burst was not confirmed after " + maxRetries.get() + " attempts");
            return false;
        }

        if (!prepareBurstMaterial(MaterialKind.Skull, missing.size())) return false;
        if (!finalWitherBaselineCaptured) captureFinalWitherBaseline();

        int sent = 0;
        boolean blocked = false;
        int requiredThisBurst = missing.size();

        for (BlockPos pos : missing) {
            PlacementAttempt result = place(pos, MaterialKind.Skull);
            if (result == PlacementAttempt.Sent) {
                sent++;
            } else if (result == PlacementAttempt.NoMaterial) {
                onOutOfMaterials();
                return false;
            } else if (result == PlacementAttempt.Refilled) {
                break;
            } else if (result == PlacementAttempt.Blocked) {
                blocked = true;
            }
        }

        if (sent > 0 || blocked) {
            attempts++;
            confirmCooldown = confirmationTicks.get();
            placementCooldown = ticksBetweenPlacements.get();
        }

        if (requiredThisBurst > 0 && sent == requiredThisBurst && !blocked) {
            finalSkullSent = true;
            finalSkullObservedPlaced = false;
            finalSpawnWait = spawnConfirmationTicks.get();
        } else if (blocked && attempts >= maxRetries.get()) {
            abortCurrent("skull burst placement remained blocked");
        }

        return false;
    }

    private boolean prepareBurstInventoryForPlan() {
        boolean moved = false;

        if (countUsableHotbarItems(MaterialKind.Soul) < 4) {
            int source = findLargestMainInventoryStack(MaterialKind.Soul);
            if (source >= 0) {
                InvUtils.move().from(source).toHotbar(soulStagingSlot.get() - 1);
                moved = true;
            }
        }

        if (countUsableHotbarItems(MaterialKind.Skull) < 3) {
            int source = findLargestMainInventoryStack(MaterialKind.Skull);
            if (source >= 0) {
                InvUtils.move().from(source).toHotbar(skullStagingSlot.get() - 1);
                moved = true;
            }
        }

        if (moved) {
            inventoryCooldown = refillDelay.get();
            return false;
        }

        return true;
    }

    private boolean prepareBurstMaterial(MaterialKind kind, int requiredCount) {
        if (requiredCount <= 0) return true;
        if (countUsableHotbarItems(kind) >= requiredCount) return true;
        if (!autoRefill.get()) return countUsableHotbarItems(kind) > 0;

        int sourceSlot = findLargestMainInventoryStack(kind);
        if (sourceSlot < 0) return countUsableHotbarItems(kind) > 0;

        int staging = kind == MaterialKind.Soul ? soulStagingSlot.get() - 1 : skullStagingSlot.get() - 1;
        InvUtils.move().from(sourceSlot).toHotbar(staging);
        inventoryCooldown = refillDelay.get();
        return false;
    }

    private int countUsableHotbarItems(MaterialKind kind) {
        int count = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (matchesMaterial(stack, kind)) count += stack.getCount();
        }

        ItemStack offhand = mc.player.getOffHandStack();
        if (matchesMaterial(offhand, kind)) count += offhand.getCount();
        return count;
    }

    private int findLargestMainInventoryStack(MaterialKind kind) {
        int bestSlot = -1;
        int bestCount = -1;
        for (int i = 9; i <= 35; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!matchesMaterial(stack, kind)) continue;
            if (stack.getCount() > bestCount) {
                bestCount = stack.getCount();
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    private boolean matchesMaterial(ItemStack stack, MaterialKind kind) {
        return kind == MaterialKind.Soul
            ? isAllowedSoulStack(stack)
            : stack.isOf(Items.WITHER_SKELETON_SKULL);
    }

    private boolean attemptStep() {
        if (step < 4) return handleNormalStep(plan.soulPositions()[step], MaterialKind.Soul);

        if (!(step == 6 && finalSkullSent) && !allSoulBlocksPresent()) {
            int missing = firstMissingSoulIndex();
            if (missing >= 0) {
                step = missing;
                resetStepAttemptState();
                return true;
            }
        }

        if (step == 4) return handleNormalStep(plan.skullPositions()[0], MaterialKind.Skull);
        if (step == 5) return handleNormalStep(plan.skullPositions()[1], MaterialKind.Skull);
        if (step == 6) return handleFinalSkull();

        finishCurrentWither();
        return false;
    }

    private boolean handleNormalStep(BlockPos pos, MaterialKind kind) {
        if (isExpectedBlock(pos, kind)) {
            step++;
            resetStepAttemptState();
            return true;
        }

        if (confirmCooldown > 0) {
            confirmCooldown--;
            return false;
        }

        if (!isReplaceable(pos)) {
            abortCurrent("target became blocked at " + shortPos(pos));
            return false;
        }

        if (!withinRange(pos)) {

            attempts++;
            if (attempts >= maxRetries.get()) abortCurrent("target moved out of placement range");
            return false;
        }

        if (attempts >= maxRetries.get()) {
            abortCurrent("placement was not confirmed after " + maxRetries.get() + " attempts");
            return false;
        }

        PlacementAttempt result = place(pos, kind);
        if (result == PlacementAttempt.Sent) {
            attempts++;
            confirmCooldown = confirmationTicks.get();
            placementCooldown = ticksBetweenPlacements.get();
        } else if (result == PlacementAttempt.Blocked) {
            attempts++;
            confirmCooldown = confirmationTicks.get();
            if (attempts >= maxRetries.get()) abortCurrent("placement remained blocked at " + shortPos(pos));
        } else if (result == PlacementAttempt.NoMaterial) {
            onOutOfMaterials();
        }

        return false;
    }

    private boolean handleFinalSkull() {
        BlockPos finalPos = plan.skullPositions()[2];

        if (finalSkullSent) {
            if (structureConsumed() || structureConsumptionStarted() || newWitherSpawnObserved()) {
                finishCurrentWither();
                return false;
            }

            if (isWitherSkull(finalPos)) finalSkullObservedPlaced = true;

            if (finalSpawnWait > 0) {
                finalSpawnWait--;
                return false;
            }

            if (finalSkullObservedPlaced) {
                finishCurrentWither();
                return false;
            }

            finalSkullSent = false;
            clearFinalWitherBaseline();
            if (attempts >= maxRetries.get()) {
                abortCurrent("final skull was not accepted after " + maxRetries.get() + " attempts");
                return false;
            }
        }

        if (!allSoulBlocksPresent() || !isWitherSkull(plan.skullPositions()[0]) || !isWitherSkull(plan.skullPositions()[1])) {
            int missingSoul = firstMissingSoulIndex();
            if (missingSoul >= 0) step = missingSoul;
            else if (!isWitherSkull(plan.skullPositions()[0])) step = 4;
            else step = 5;
            finalSkullSent = false;
            finalSkullObservedPlaced = false;
            clearFinalWitherBaseline();
            resetStepAttemptState();
            return true;
        }

        if (!isReplaceable(finalPos)) {
            abortCurrent("final skull target became blocked at " + shortPos(finalPos));
            return false;
        }

        if (!withinRange(finalPos)) {
            attempts++;
            if (attempts >= maxRetries.get()) abortCurrent("final skull moved out of placement range");
            return false;
        }

        if (!finalWitherBaselineCaptured) captureFinalWitherBaseline();

        PlacementAttempt result = place(finalPos, MaterialKind.Skull);
        if (result == PlacementAttempt.Sent) {
            attempts++;
            finalSkullSent = true;
            finalSkullObservedPlaced = false;
            finalSpawnWait = spawnConfirmationTicks.get();
            placementCooldown = ticksBetweenPlacements.get();
        } else if (result == PlacementAttempt.Blocked) {
            attempts++;
            if (attempts >= maxRetries.get()) abortCurrent("final skull placement remained blocked at " + shortPos(finalPos));
        } else if (result == PlacementAttempt.NoMaterial) {
            onOutOfMaterials();
        }

        return false;
    }

    private PlacementAttempt place(BlockPos pos, MaterialKind kind) {
        FindItemResult item = findUsableHotbarItem(kind);

        if (!item.found()) {
            if (!autoRefill.get()) return PlacementAttempt.NoMaterial;

            FindItemResult source = findMainInventoryItem(kind);
            if (!source.found()) return PlacementAttempt.NoMaterial;

            int staging = kind == MaterialKind.Soul ? soulStagingSlot.get() - 1 : skullStagingSlot.get() - 1;
            InvUtils.move().from(source.slot()).toHotbar(staging);
            inventoryCooldown = refillDelay.get();
            return PlacementAttempt.Refilled;
        }

        if (!airPlace.get() && BlockUtils.getPlaceSide(pos) == null) return PlacementAttempt.Blocked;

        boolean sent = BlockUtils.place(
            pos,
            item,
            rotate.get(),
            50,
            swing.get(),
            true,
            true
        );

        return sent ? PlacementAttempt.Sent : PlacementAttempt.Blocked;
    }

    private FindItemResult findUsableHotbarItem(MaterialKind kind) {
        return switch (kind) {
            case Soul -> InvUtils.findInHotbar(this::isAllowedSoulStack);
            case Skull -> InvUtils.findInHotbar(stack -> stack.isOf(Items.WITHER_SKELETON_SKULL));
        };
    }

    private FindItemResult findMainInventoryItem(MaterialKind kind) {
        Predicate<ItemStack> predicate = switch (kind) {
            case Soul -> this::isAllowedSoulStack;
            case Skull -> stack -> stack.isOf(Items.WITHER_SKELETON_SKULL);
        };

        return InvUtils.find(predicate, 9, 35);
    }

    private double getActiveBehindDistance() {
        return nameWithers.get() ? namingBehindDistance.get() : behindDistance.get();
    }

    private int getActiveVerticalOffset() {
        return nameWithers.get() ? namingVerticalOffset.get() : verticalOffset.get();
    }

    private double getActivePlacingSpeed() {
        return nameWithers.get() ? namingPlacingSpeed.get() : placingSpeed.get();
    }

    private boolean tryCreatePlan() {
        Direction travel = getTravelDirection();
        Direction behind = travel.getOpposite();
        Direction left = leftOf(travel);
        Direction right = left.getOpposite();

        Vec3d playerPos = mc.player.getEntityPos();
        double activeBehindDistance = getActiveBehindDistance();
        int activeVerticalOffset = getActiveVerticalOffset();
        double targetX = playerPos.x + behind.getOffsetX() * activeBehindDistance;
        double targetZ = playerPos.z + behind.getOffsetZ() * activeBehindDistance;
        int targetY = mc.player.getBlockPos().getY() + activeVerticalOffset;
        BlockPos movingBase = BlockPos.ofFloored(targetX, targetY, targetZ);
        BlockPos base = movingBase;

        if (lastCompletedBase != null) {

            int dx = movingBase.getX() - lastCompletedBase.getX();
            int dz = movingBase.getZ() - lastCompletedBase.getZ();
            int forwardProgress = dx * travel.getOffsetX() + dz * travel.getOffsetZ();
            if (forwardProgress < spacing.get()) return false;

            base = new BlockPos(
                lastCompletedBase.getX() + travel.getOffsetX() * spacing.get(),
                targetY,
                lastCompletedBase.getZ() + travel.getOffsetZ() * spacing.get()
            );

            WitherPlan exactSpacingCandidate = WitherPlan.create(base, left, right);
            if (!candidateClear(exactSpacingCandidate)) {

                base = movingBase;
            }
        }

        WitherPlan candidate = WitherPlan.create(base, left, right);

        if (!candidateClear(candidate)) return false;

        plan = candidate;
        resumeAtPlacingSpeed = false;
        step = 0;
        burstPhase = 0;
        resetStepAttemptState();
        finalSkullSent = false;
        finalSkullObservedPlaced = false;
        clearFinalWitherBaseline();
        finalSpawnWait = 0;
        syncManagedElytraSpeed();
        return true;
    }

    private boolean candidateClear(WitherPlan candidate) {
        for (BlockPos pos : candidate.soulPositions()) {
            if (!mc.world.getBlockState(pos).isReplaceable()) return false;
            if (!BlockUtils.canPlaceBlock(pos, true, Blocks.SOUL_SAND)) return false;
        }

        for (BlockPos pos : candidate.skullPositions()) {
            if (!mc.world.getBlockState(pos).isReplaceable()) return false;
            if (!BlockUtils.canPlaceBlock(pos, true, Blocks.WITHER_SKELETON_SKULL)) return false;
        }

        if (!mc.world.getBlockState(candidate.bottomLeftClear()).isAir()) return false;
        if (!mc.world.getBlockState(candidate.bottomRightClear()).isAir()) return false;

        for (BlockPos pos : candidate.allStructurePositions()) {
            if (!withinRange(pos)) return false;
        }

        return true;
    }

    private Direction getTravelDirection() {
        if (directionMode.get() == DirectionMode.Look) {
            return horizontal(mc.player.getHorizontalFacing());
        }

        Vec3d velocity = mc.player.getVelocity();
        double ax = Math.abs(velocity.x);
        double az = Math.abs(velocity.z);

        if (Math.max(ax, az) < 0.01) return horizontal(mc.player.getHorizontalFacing());
        if (ax > az) return velocity.x >= 0 ? Direction.EAST : Direction.WEST;
        return velocity.z >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private static Direction horizontal(Direction direction) {
        return switch (direction) {
            case NORTH, SOUTH, EAST, WEST -> direction;
            default -> Direction.NORTH;
        };
    }

    private static Direction leftOf(Direction forward) {
        return switch (forward) {
            case NORTH -> Direction.WEST;
            case SOUTH -> Direction.EAST;
            case EAST -> Direction.NORTH;
            case WEST -> Direction.SOUTH;
            default -> Direction.WEST;
        };
    }

    private boolean allSoulBlocksPresent() {
        if (plan == null) return false;
        for (BlockPos pos : plan.soulPositions()) if (!isSoulBlock(pos)) return false;
        return true;
    }

    private boolean allSkullsPresent() {
        if (plan == null) return false;
        for (BlockPos pos : plan.skullPositions()) if (!isWitherSkull(pos)) return false;
        return true;
    }

    private boolean burstSoulStructureConsumptionStarted() {
        if (plan == null || !finalSkullSent) return false;
        for (BlockPos pos : plan.soulPositions()) if (!isSoulBlock(pos)) return true;
        return false;
    }

    private int firstMissingSoulIndex() {
        if (plan == null) return -1;
        BlockPos[] positions = plan.soulPositions();
        for (int i = 0; i < positions.length; i++) if (!isSoulBlock(positions[i])) return i;
        return -1;
    }

    private boolean isExpectedBlock(BlockPos pos, MaterialKind kind) {
        return kind == MaterialKind.Soul ? isSoulBlock(pos) : isWitherSkull(pos);
    }

    private boolean isSoulBlock(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        return state.isOf(Blocks.SOUL_SAND) || state.isOf(Blocks.SOUL_SOIL);
    }

    private boolean isWitherSkull(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        return state.isOf(Blocks.WITHER_SKELETON_SKULL) || state.isOf(Blocks.WITHER_SKELETON_WALL_SKULL);
    }

    private boolean isReplaceable(BlockPos pos) {
        return mc.world.getBlockState(pos).isReplaceable();
    }

    private boolean isAllowedSoulStack(ItemStack stack) {
        return stack.isOf(Items.SOUL_SAND) || stack.isOf(Items.SOUL_SOIL);
    }

    private boolean hasMaterialsForCompleteWither() {
        int souls = 0;
        int skulls = 0;

        int end = autoRefill.get() ? 35 : 8;
        for (int i = 0; i <= end; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            if (isAllowedSoulStack(stack)) souls += stack.getCount();
            else if (stack.isOf(Items.WITHER_SKELETON_SKULL)) skulls += stack.getCount();
        }

        ItemStack offhand = mc.player.getOffHandStack();
        if (isAllowedSoulStack(offhand)) souls += offhand.getCount();
        else if (offhand.isOf(Items.WITHER_SKELETON_SKULL)) skulls += offhand.getCount();

        return souls >= 4 && skulls >= 3;
    }

    private boolean withinRange(BlockPos pos) {
        Vec3d eye = mc.player.getEyePos();
        return eye.distanceTo(Vec3d.ofCenter(pos)) <= maxPlaceRange.get();
    }

    private void captureFinalWitherBaseline() {
        withersBeforeFinalSkull.clear();
        if (plan == null) return;

        Vec3d center = Vec3d.ofCenter(plan.base()).add(0, 1.5, 0);
        Box box = Box.of(center, 8.0, 8.0, 8.0);
        for (WitherEntity wither : mc.world.getEntitiesByClass(WitherEntity.class, box, entity -> true)) {
            withersBeforeFinalSkull.add(wither.getUuid());
        }

        finalWitherBaselineCaptured = true;
    }

    private WitherEntity findNewWitherSpawn() {
        if (plan == null || !finalSkullSent || !finalWitherBaselineCaptured) return null;

        Vec3d center = Vec3d.ofCenter(plan.base()).add(0, 1.5, 0);
        Box box = Box.of(center, 6.0, 6.0, 6.0);
        WitherEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (WitherEntity wither : mc.world.getEntitiesByClass(WitherEntity.class, box, entity -> true)) {
            if (withersBeforeFinalSkull.contains(wither.getUuid())) continue;
            double distance = wither.getEntityPos().squaredDistanceTo(center);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = wither;
            }
        }

        return nearest;
    }

    private boolean newWitherSpawnObserved() {
        return findNewWitherSpawn() != null;
    }

    private void clearFinalWitherBaseline() {
        finalWitherBaselineCaptured = false;
        withersBeforeFinalSkull.clear();
    }

    private boolean structureConsumed() {
        if (plan == null || !finalSkullSent) return false;

        int remainingSoul = 0;
        int remainingSkulls = 0;
        for (BlockPos pos : plan.soulPositions()) if (isSoulBlock(pos)) remainingSoul++;
        for (BlockPos pos : plan.skullPositions()) if (isWitherSkull(pos)) remainingSkulls++;
        return remainingSoul == 0 && remainingSkulls == 0;
    }

    private boolean structureConsumptionStarted() {
        if (plan == null || !finalSkullSent) return false;

        for (BlockPos pos : plan.soulPositions()) if (!isSoulBlock(pos)) return true;
        if (!isWitherSkull(plan.skullPositions()[0])) return true;
        return !isWitherSkull(plan.skullPositions()[1]);
    }

    private void finishCurrentWither() {
        if (nameWithers.get() && plan != null) {
            if (namingState == NamingState.Idle) beginWitherNaming();
            return;
        }

        completeCurrentWither();
    }

    private void completeCurrentWither() {
        if (plan != null) lastCompletedBase = plan.base();
        completed++;
        if (mc.getSession() != null) gamerguy11.sixtoolsaddon.utils.PrintStats.record(mc.getSession().getUsername());
        resetCurrentPlan();
        syncManagedElytraSpeed();
        syncAutoWalk();
    }

    private void beginWitherNaming() {
        namingState = NamingState.WaitTarget;
        namingTargetUuid = null;
        pendingWitherName = null;
        namingTicks = 0;
        namingConfirmCooldown = 0;
        namingAttempts = 0;
        matchingNameTagsBeforeInteraction = -1;
        pendingNameTagHotbarSlot = -1;
        lastNamingRangeDistance = -1.0;
        namingOriginalSelectedSlot = mc.player == null ? -1 : mc.player.getInventory().getSelectedSlot();
        syncManagedElytraSpeed();
    }

    private void tickWitherNaming() {
        if (plan == null) {
            clearNamingState(true);
            return;
        }

        if (!nameWithers.get()) {
            completeCurrentWither();
            return;
        }

        namingTicks++;
        int timeout = Math.max(30, spawnConfirmationTicks.get() * 4);
        if (namingTicks > timeout) {
            if (lastNamingRangeDistance >= 0.0 && targetOutsideNamingRange()) {
                warning(String.format("Timed out naming '%s': the Wither stayed outside entity interaction range (closest hitbox distance %.2f, player range %.2f). Continuing without a name.",
                    pendingWitherName == null ? "?" : pendingWitherName,
                    lastNamingRangeDistance,
                    mc.player.getEntityInteractionRange()));
            } else {
                warning("Timed out while trying to name the newly spawned Wither; continuing without a name.");
            }
            completeCurrentWither();
            return;
        }

        WitherEntity target = findNamingTarget();
        if (target == null) {
            WitherEntity spawned = findNewWitherSpawn();
            if (spawned == null) return;
            namingTargetUuid = spawned.getUuid();
            target = spawned;
        }

        if (pendingWitherName == null) {
            pendingWitherName = chooseWitherName();
            if (pendingWitherName == null) {
                if (namingMode.get() == NamingMode.Specific && specificWitherName.get().isEmpty()) {
                    warning("Wither naming is set to Specific, but specific-name is empty. Continuing without naming this Wither.");
                } else if (namingMode.get() == NamingMode.Specific) {
                    warning("No renamed Name Tag named '" + specificWitherName.get() + "' is currently in the player inventory. Continuing without naming this Wither.");
                } else {
                    warning("Inventory Cycle found no renamed Name Tags in the player inventory. Continuing without naming this Wither.");
                }
                completeCurrentWither();
                return;
            }
            namingState = NamingState.PrepareTag;
        }

        if (namingState == NamingState.WaitStage) {
            if (namingConfirmCooldown > 0) {
                namingConfirmCooldown--;
                return;
            }
            namingState = NamingState.PrepareTag;
        }

        if (namingState == NamingState.WaitSelected) {
            if (namingConfirmCooldown > 0) {
                namingConfirmCooldown--;
                return;
            }

            String heldName = renamedNameTagName(mc.player.getMainHandStack());
            if (!pendingWitherName.equals(heldName)) {
                namingState = NamingState.PrepareTag;
                return;
            }

            sendNameTagInteraction(target, pendingNameTagHotbarSlot, true);
            return;
        }

        if (namingState == NamingState.WaitConfirm) {
            if (targetHasExpectedName(target) || countMatchingNameTags(pendingWitherName) < matchingNameTagsBeforeInteraction) {
                onWitherNamedSuccessfully();
                return;
            }

            if (namingConfirmCooldown > 0) {
                namingConfirmCooldown--;
                return;
            }

            if (namingAttempts >= maxRetries.get()) {
                warning("Name Tag interaction for '" + pendingWitherName + "' was sent while the expected tag was held and the Wither was in entity range, but the server never confirmed it. Continuing without retrying further.");
                completeCurrentWither();
                return;
            }

            namingState = NamingState.PrepareTag;
        }

        if (namingState != NamingState.PrepareTag) return;

        int hotbarSlot = findMatchingNamedTagHotbarSlot(pendingWitherName);
        if (hotbarSlot < 0) {
            int source = findMatchingNamedTagInventorySlot(pendingWitherName);
            if (source < 0) {
                warning("The renamed Name Tag '" + pendingWitherName + "' disappeared before it could be used; continuing without naming this Wither.");
                completeCurrentWither();
                return;
            }

            if (source >= 9) {
                InvUtils.move().from(source).toHotbar(nameTagStagingSlot.get() - 1);
                namingConfirmCooldown = refillDelay.get();
                namingState = NamingState.WaitStage;
                return;
            }
            hotbarSlot = source;
        }

        pendingNameTagHotbarSlot = hotbarSlot;
        if (mc.player.getInventory().getSelectedSlot() != hotbarSlot ||
            !pendingWitherName.equals(renamedNameTagName(mc.player.getMainHandStack()))) {
            InvUtils.swap(hotbarSlot, false);
            namingConfirmCooldown = 1;
            namingState = NamingState.WaitSelected;
            return;
        }

        sendNameTagInteraction(target, hotbarSlot, false);
    }

    private WitherEntity findNamingTarget() {
        if (namingTargetUuid == null || plan == null) return null;
        Vec3d center = Vec3d.ofCenter(plan.base()).add(0, 1.5, 0);
        Box box = Box.of(center, 10.0, 10.0, 10.0);
        for (WitherEntity wither : mc.world.getEntitiesByClass(WitherEntity.class, box, entity -> true)) {
            if (wither.getUuid().equals(namingTargetUuid)) return wither;
        }
        return null;
    }

    private void sendNameTagInteraction(WitherEntity target, int hotbarSlot, boolean slotAlreadySynced) {
        if (target == null || hotbarSlot < 0 || hotbarSlot > 8) return;

        matchingNameTagsBeforeInteraction = countMatchingNameTags(pendingWitherName);
        if (matchingNameTagsBeforeInteraction <= 0) {
            warning("No matching renamed Name Tag remains for '" + pendingWitherName + "'. Continuing without naming this Wither.");
            completeCurrentWither();
            return;
        }

        if (!mc.player.canInteractWithEntity(target, 0.0)) {
            lastNamingRangeDistance = distanceToAabb(mc.player.getEyePos(), target.getBoundingBox());

            namingState = NamingState.WaitSelected;
            pendingNameTagHotbarSlot = hotbarSlot;
            namingConfirmCooldown = 1;
            return;
        }

        if (!slotAlreadySynced || mc.player.getInventory().getSelectedSlot() != hotbarSlot) {
            InvUtils.swap(hotbarSlot, false);
        }
        lastNamingRangeDistance = -1.0;

        Box box = target.getBoundingBox();
        Vec3d hitPos = new Vec3d(
            (box.minX + box.maxX) * 0.5,
            (box.minY + box.maxY) * 0.5,
            (box.minZ + box.maxZ) * 0.5
        );
        Runnable interact = () -> performVanillaEntityUse(target);
        Rotations.rotate(Rotations.getYaw(hitPos), Rotations.getPitch(hitPos), 100, interact);

        namingAttempts++;
        namingConfirmCooldown = Math.max(8, confirmationTicks.get() + 5);
        namingState = NamingState.WaitConfirm;
    }

    private void performVanillaEntityUse(WitherEntity target) {
        if (target == null || target.isRemoved() || mc.player == null || mc.interactionManager == null) return;
        if (pendingWitherName == null || !pendingWitherName.equals(renamedNameTagName(mc.player.getMainHandStack()))) return;
        if (!mc.player.canInteractWithEntity(target, 0.0)) {
            lastNamingRangeDistance = distanceToAabb(mc.player.getEyePos(), target.getBoundingBox());
            return;
        }

        Vec3d hitPos = closestInteractionPoint(mc.player.getEyePos(), target.getBoundingBox());
        EntityHitResult entityHit = new EntityHitResult(target, hitPos);
        ActionResult result = mc.interactionManager.interactEntityAtLocation(mc.player, target, entityHit, Hand.MAIN_HAND);
        if (!result.isAccepted()) {
            result = mc.interactionManager.interactEntity(mc.player, target, Hand.MAIN_HAND);
        }

        if (swing.get() && result instanceof ActionResult.Success success &&
            success.swingSource() == ActionResult.SwingSource.CLIENT) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }

    private static Vec3d closestInteractionPoint(Vec3d eye, Box box) {

        Vec3d center = new Vec3d(
            (box.minX + box.maxX) * 0.5,
            (box.minY + box.maxY) * 0.5,
            (box.minZ + box.maxZ) * 0.5
        );
        if (box.contains(eye)) return center;
        return box.raycast(eye, center).orElse(center);
    }

    private static double distanceToAabb(Vec3d point, Box box) {
        double x = Math.max(box.minX - point.x, Math.max(0.0, point.x - box.maxX));
        double y = Math.max(box.minY - point.y, Math.max(0.0, point.y - box.maxY));
        double z = Math.max(box.minZ - point.z, Math.max(0.0, point.z - box.maxZ));
        return Math.sqrt(x * x + y * y + z * z);
    }

    private void onWitherNamedSuccessfully() {
        if (namingMode.get() == NamingMode.InventoryCycle) lastCycledWitherName = pendingWitherName;
        completeCurrentWither();
    }

    private boolean targetOutsideNamingRange() {
        WitherEntity target = findNamingTarget();
        return target != null && !mc.player.canInteractWithEntity(target, 0.0);
    }

    private boolean targetHasExpectedName(WitherEntity target) {
        if (target == null || pendingWitherName == null || target.getCustomName() == null) return false;
        return pendingWitherName.equals(target.getCustomName().getString());
    }

    private String chooseWitherName() {
        if (namingMode.get() == NamingMode.Specific) {
            String wanted = specificWitherName.get();
            if (wanted == null || wanted.isEmpty()) return null;
            return findMatchingNamedTagInventorySlot(wanted) >= 0 ? wanted : null;
        }

        List<String> names = availableRenamedNameTagNames();
        if (names.isEmpty()) return null;
        if (names.size() == 1) return names.get(0);

        for (String name : names) if (!cycleNameOrder.contains(name)) cycleNameOrder.add(name);

        int start = lastCycledWitherName == null ? 0 : cycleNameOrder.indexOf(lastCycledWitherName) + 1;
        if (start <= 0 || start >= cycleNameOrder.size()) start = 0;
        for (int offset = 0; offset < cycleNameOrder.size(); offset++) {
            String candidate = cycleNameOrder.get((start + offset) % cycleNameOrder.size());
            if (names.contains(candidate)) return candidate;
        }

        return names.get(0);
    }

    private List<String> availableRenamedNameTagNames() {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (int i = 0; i <= 35; i++) {
            String name = renamedNameTagName(mc.player.getInventory().getStack(i));
            if (name != null) unique.add(name);
        }
        return new ArrayList<>(unique);
    }

    private String renamedNameTagName(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isOf(Items.NAME_TAG)) return null;
        Text customName = stack.get(DataComponentTypes.CUSTOM_NAME);
        if (customName == null) return null;
        String name = customName.getString();
        return name.isEmpty() ? null : name;
    }

    private int findMatchingNamedTagInventorySlot(String name) {
        if (name == null) return -1;
        for (int i = 0; i <= 35; i++) {
            String stackName = renamedNameTagName(mc.player.getInventory().getStack(i));
            if (name.equals(stackName)) return i;
        }
        return -1;
    }

    private int findMatchingNamedTagHotbarSlot(String name) {
        if (name == null) return -1;
        for (int i = 0; i < 9; i++) {
            String stackName = renamedNameTagName(mc.player.getInventory().getStack(i));
            if (name.equals(stackName)) return i;
        }
        return -1;
    }

    private int countMatchingNameTags(String name) {
        if (name == null) return 0;
        int count = 0;
        for (int i = 0; i <= 35; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            String stackName = renamedNameTagName(stack);
            if (name.equals(stackName)) count += stack.getCount();
        }
        return count;
    }

    private void clearNamingState(boolean restoreSelection) {
        if (restoreSelection && mc.player != null && namingOriginalSelectedSlot >= 0 && namingOriginalSelectedSlot < 9) {
            InvUtils.swap(namingOriginalSelectedSlot, false);
        }
        namingState = NamingState.Idle;
        namingTargetUuid = null;
        pendingWitherName = null;
        namingTicks = 0;
        namingConfirmCooldown = 0;
        namingAttempts = 0;
        namingOriginalSelectedSlot = -1;
        matchingNameTagsBeforeInteraction = -1;
        pendingNameTagHotbarSlot = -1;
        lastNamingRangeDistance = -1.0;
    }

    private void abortCurrent(String reason) {
        warning("Skipping current wither: " + reason + ".");
        resetCurrentPlan();
    }

    private void onOutOfMaterials() {
        if (autoRestock.get() && restockState == RestockState.Idle) {
            if (startRestock()) return;
        }

        if (!outOfMaterialsReported) {
            String source = autoRefill.get() ? "inventory/hotbar/offhand" : "hotbar/offhand";
            warning("Need at least 4 soul sand/soul soil and 3 wither skeleton skulls available in " + source + " for the next complete wither.");
            if (autoRestock.get()) warning("Automatic restock could not start; check Efly, Ender Chest availability, and restock settings.");
            outOfMaterialsReported = true;
        }

        if (stopWhenEmpty.get()) toggle();
    }

    private boolean startRestock() {
        if (!autoRestock.get() || restockState != RestockState.Idle) return false;
        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null) return false;
        if (!airPlace.get()) {
            warning("Automatic restock requires air-place because the Ender Chest and shulker are placed while flying.");
            return false;
        }

        if (!manageElytraSpeed.get()) {
            warning("Automatic restock requires manage-efly-speed so it can switch to travel speed, stop in the air, and resume at placing speed.");
            return false;
        }

        if (!autoRefill.get()) {
            warning("Automatic restock requires auto-refill-hotbar so materials transferred into the main inventory can be consumed by the printer.");
            return false;
        }

        Efly efly = Modules.get().get(Efly.class);
        if (efly == null || !efly.isActive() || efly.isExternallyControlled() || !mc.player.isGliding()) {
            warning("Automatic restock requires Efly to be active (and not controlled by Auto Wasp) while you are elytra flying.");
            return false;
        }

        int enderChestSlot = findPlayerInventorySlot(stack -> stack.isOf(Items.ENDER_CHEST));
        if (enderChestSlot < 0) {
            warning("Automatic restock needs an Ender Chest in the player inventory/hotbar.");
            return false;
        }

        resetCurrentPlan();
        restockTravelStart = mc.player.getEntityPos();
        restockTravelDirection = getTravelDirection();
        restockOriginalSelectedSlot = mc.player.getInventory().getSelectedSlot();
        restockForwardWasDown = mc.options.forwardKey.isPressed();
        restockBackWasDown = mc.options.backKey.isPressed();
        restockLeftWasDown = mc.options.leftKey.isPressed();
        restockRightWasDown = mc.options.rightKey.isPressed();
        restockJumpWasDown = mc.options.jumpKey.isPressed();
        restockSneakWasDown = mc.options.sneakKey.isPressed();
        restockMovementCaptured = true;
        restockAutoWalkWasActive = autoWalk.get() && autoWalkStateCaptured;
        restockEnderChestSourceSlot = enderChestSlot;
        restockDroppedStacks = 0;
        resumeAtPlacingSpeed = false;
        transitionRestock(RestockState.Travel);
        info("Materials depleted. Flying %.1f blocks before automatic restock.", restockTravelDistance.get());
        syncManagedElytraSpeed();
        return true;
    }

    private void tickRestock() {
        restockStateTicks++;

        Efly efly = Modules.get().get(Efly.class);
        if (efly == null || !efly.isActive() || !mc.player.isGliding()) {
            abortRestock("Efly stopped during automatic restock.");
            return;
        }

        if (restockState == RestockState.Travel) {

            mc.options.forwardKey.setPressed(true);
            mc.options.backKey.setPressed(false);
            mc.options.leftKey.setPressed(false);
            mc.options.rightKey.setPressed(false);
        } else {

            mc.options.forwardKey.setPressed(false);
            mc.options.backKey.setPressed(false);
            mc.options.leftKey.setPressed(false);
            mc.options.rightKey.setPressed(false);
            mc.options.jumpKey.setPressed(false);
            mc.options.sneakKey.setPressed(false);
            mc.player.setVelocity(Vec3d.ZERO);
        }

        switch (restockState) {
            case Travel -> restockTravel();
            case PrepareEnderChest -> prepareRestockEnderChest();
            case WaitEnderChestStage -> waitRestockEnderChestStage();
            case PlaceEnderChest -> placeRestockEnderChest();
            case WaitEnderChestBlock -> waitRestockEnderChestBlock();
            case OpenEnderChest -> openRestockEnderChest();
            case WaitEnderChestScreen -> waitRestockEnderChestScreen();
            case WaitEnderChestSpace -> waitRestockEnderChestSpace();
            case WaitShulkerPickup -> waitRestockShulkerPickup();
            case WaitEnderChestClose -> waitRestockEnderChestClose();
            case PrepareShulker -> prepareRestockShulker();
            case WaitShulkerStage -> waitRestockShulkerStage();
            case PlaceShulker -> placeRestockShulker();
            case WaitShulkerBlock -> waitRestockShulkerBlock();
            case OpenShulker -> openRestockShulker();
            case WaitShulkerScreen -> waitRestockShulkerScreen();
            case PrepareCapacity -> prepareRestockCapacity();
            case WaitCapacitySync -> waitRestockCapacitySync();
            case Transfer -> transferRestockShulker();
            case Settle -> settleRestockShulker();
            case WaitShulkerClose -> waitRestockShulkerClose();
            case Idle -> {
            }
        }
    }

    private void restockTravel() {
        if (restockTravelStart == null) {
            abortRestock("Restock travel start position was lost.");
            return;
        }

        double dx = mc.player.getX() - restockTravelStart.x;
        double dz = mc.player.getZ() - restockTravelStart.z;
        if (Math.hypot(dx, dz) < restockTravelDistance.get()) return;

        mc.options.forwardKey.setPressed(false);
        mc.player.setVelocity(Vec3d.ZERO);
        transitionRestock(RestockState.PrepareEnderChest);
        syncManagedElytraSpeed();
    }

    private void prepareRestockEnderChest() {
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler || mc.currentScreen != null) {
            if (restockStateTicks >= restockTimeout.get()) abortRestock("Player inventory did not become available before Ender Chest placement.");
            return;
        }

        restockEnderChestSourceSlot = findPlayerInventorySlot(stack -> stack.isOf(Items.ENDER_CHEST));
        if (restockEnderChestSourceSlot < 0) {
            abortRestock("No Ender Chest remained in the player inventory.");
            return;
        }

        restockPlacementHotbarSlot = stageInventorySlotForPlacement(restockEnderChestSourceSlot);
        if (restockPlacementHotbarSlot < 0) {
            abortRestock("Could not stage the Ender Chest in the hotbar.");
            return;
        }

        if (restockEnderChestSourceSlot >= 9) {
            transitionRestock(RestockState.WaitEnderChestStage);
            return;
        }

        InvUtils.swap(restockPlacementHotbarSlot, false);
        restockEnderChestPos = aboveHeadTarget();
        transitionRestock(RestockState.PlaceEnderChest);
    }

    private void waitRestockEnderChestStage() {
        ItemStack staged = mc.player.getInventory().getStack(restockPlacementHotbarSlot);
        if (staged.isOf(Items.ENDER_CHEST)) {
            InvUtils.swap(restockPlacementHotbarSlot, false);
            restockEnderChestPos = aboveHeadTarget();
            transitionRestock(RestockState.PlaceEnderChest);
            return;
        }

        if (restockStateTicks >= restockTimeout.get()) {
            abortRestock("Timed out moving the Ender Chest into the restock hotbar slot.");
        }
    }

    private void placeRestockEnderChest() {
        if (restockStateTicks < restockActionDelay.get()) return;
        if (restockEnderChestPos == null) restockEnderChestPos = aboveHeadTarget();

        if (!BlockUtils.canPlaceBlock(restockEnderChestPos, true, Blocks.ENDER_CHEST)) {
            abortRestock("The above-head Ender Chest position is blocked.");
            return;
        }

        if (!withinRange(restockEnderChestPos)) {
            abortRestock("The above-head Ender Chest position is outside placement range.");
            return;
        }

        ItemStack staged = mc.player.getInventory().getStack(restockPlacementHotbarSlot);
        if (!staged.isOf(Items.ENDER_CHEST)) {
            abortRestock("The staged Ender Chest disappeared before placement.");
            return;
        }

        sendAirPlacement(restockEnderChestPos, restockPlacementHotbarSlot);
        transitionRestock(RestockState.WaitEnderChestBlock);
    }

    private void waitRestockEnderChestBlock() {
        if (mc.world.getBlockState(restockEnderChestPos).isOf(Blocks.ENDER_CHEST)) {
            transitionRestock(RestockState.OpenEnderChest);
            return;
        }

        if (restockStateTicks >= restockTimeout.get()) {
            abortRestock("The server did not accept the air-placed Ender Chest.");
        }
    }

    private void openRestockEnderChest() {
        if (restockStateTicks < restockActionDelay.get()) return;
        if (!mc.world.getBlockState(restockEnderChestPos).isOf(Blocks.ENDER_CHEST)) {
            abortRestock("The Ender Chest disappeared before it could be opened.");
            return;
        }

        interactRestockBlock(restockEnderChestPos);
        transitionRestock(RestockState.WaitEnderChestScreen);
    }

    private void waitRestockEnderChestScreen() {
        if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler && handler.getRows() == 3) {
            restockContainerId = handler.syncId;
            restockShulkerChestSlot = firstShulkerSlot(handler, 27);

            if (restockShulkerChestSlot < 0) {
                if (restockStateTicks >= restockTimeout.get()) {
                    abortRestock("Timed out waiting for a shulker box to synchronize into the Ender Chest.");
                }
                return;
            }

            restockShulkerStack = handler.getSlot(restockShulkerChestSlot).getStack().copy();

            if (findEmptyPlayerInventorySlot() < 0) {
                int dropSlot = findOnePrinterDropSlot();
                if (dropSlot < 0) {
                    abortRestock("The inventory is full and no expendable Wither material or firework-rocket stack can be dropped to receive the supply shulker. Protected items were not touched.");
                    return;
                }

                dropPlayerInventoryStack(dropSlot);
                restockDroppedStacks++;
                transitionRestock(RestockState.WaitEnderChestSpace);
                return;
            }

            takeRestockShulker(handler);
            return;
        }

        if (restockStateTicks >= restockTimeout.get()) {
            abortRestock("The Ender Chest screen did not open.");
        }
    }

    private void waitRestockEnderChestSpace() {
        if (!(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler) || handler.syncId != restockContainerId) {
            abortRestock("The Ender Chest closed while making room for the supply shulker.");
            return;
        }

        if (findEmptyPlayerInventorySlot() >= 0) {

            restockShulkerChestSlot = firstMatchingShulkerSlot(handler, restockShulkerStack, 27);
            if (restockShulkerChestSlot < 0) {
                if (restockStateTicks >= restockTimeout.get()) {
                    abortRestock("Timed out waiting for the selected supply shulker to re-synchronize in the Ender Chest.");
                }
                return;
            }
            takeRestockShulker(handler);
            return;
        }

        if (restockStateTicks >= restockTimeout.get()) {
            abortRestock("Timed out making an inventory slot for the supply shulker.");
        }
    }

    private void takeRestockShulker(GenericContainerScreenHandler handler) {

        restockShulkerReceiveSlot = findEmptyPlayerInventorySlot();
        if (restockShulkerReceiveSlot < 0) {
            abortRestock("No empty inventory slot remained for the supply shulker.");
            return;
        }

        int destinationHandlerSlot = playerInventorySlotToCurrentHandlerId(restockShulkerReceiveSlot);
        if (destinationHandlerSlot < 0) {
            abortRestock("Could not map the reserved supply-shulker inventory slot.");
            return;
        }

        mc.interactionManager.clickSlot(
            handler.syncId,
            restockShulkerChestSlot,
            0,
            SlotActionType.PICKUP,
            mc.player
        );
        mc.interactionManager.clickSlot(
            handler.syncId,
            destinationHandlerSlot,
            0,
            SlotActionType.PICKUP,
            mc.player
        );
        transitionRestock(RestockState.WaitShulkerPickup);
    }

    private void waitRestockShulkerPickup() {
        if (restockShulkerReceiveSlot >= 0 && restockShulkerReceiveSlot <= 35
            && ItemStack.areEqual(mc.player.getInventory().getStack(restockShulkerReceiveSlot), restockShulkerStack)) {
            restockShulkerInventorySlot = restockShulkerReceiveSlot;
            mc.player.closeHandledScreen();
            transitionRestock(RestockState.WaitEnderChestClose);
            return;
        }

        if (restockStateTicks >= restockTimeout.get()) {
            abortRestock("The supply shulker was not transferred from the Ender Chest into its reserved inventory slot.");
        }
    }

    private void waitRestockEnderChestClose() {
        if (mc.player.currentScreenHandler == mc.player.playerScreenHandler) {
            transitionRestock(RestockState.PrepareShulker);
            return;
        }

        if (restockStateTicks >= restockTimeout.get()) {
            abortRestock("The Ender Chest screen did not close cleanly.");
        }
    }

    private void prepareRestockShulker() {
        restockShulkerInventorySlot = findMatchingPlayerInventorySlot(restockShulkerStack);
        if (restockShulkerInventorySlot < 0) {
            abortRestock("The supply shulker disappeared before placement.");
            return;
        }

        restockPlacementHotbarSlot = stageInventorySlotForPlacement(restockShulkerInventorySlot);
        if (restockPlacementHotbarSlot < 0) {
            abortRestock("Could not stage the supply shulker in the hotbar.");
            return;
        }

        if (restockShulkerInventorySlot >= 9) {
            transitionRestock(RestockState.WaitShulkerStage);
            return;
        }

        InvUtils.swap(restockPlacementHotbarSlot, false);
        restockShulkerPos = findRestockShulkerTarget();
        if (restockShulkerPos == null) {
            abortRestock("No open block next to the Ender Chest can hold the supply shulker.");
            return;
        }
        transitionRestock(RestockState.PlaceShulker);
    }

    private void waitRestockShulkerStage() {
        ItemStack staged = mc.player.getInventory().getStack(restockPlacementHotbarSlot);
        if (ItemStack.areEqual(staged, restockShulkerStack)) {
            InvUtils.swap(restockPlacementHotbarSlot, false);
            restockShulkerPos = findRestockShulkerTarget();
            if (restockShulkerPos == null) {
                abortRestock("No open block next to the Ender Chest can hold the supply shulker.");
                return;
            }
            transitionRestock(RestockState.PlaceShulker);
            return;
        }

        if (restockStateTicks >= restockTimeout.get()) {
            abortRestock("Timed out moving the supply shulker into the restock hotbar slot.");
        }
    }

    private void placeRestockShulker() {
        if (restockStateTicks < restockActionDelay.get()) return;
        if (restockShulkerPos == null) {
            abortRestock("Supply shulker target was lost.");
            return;
        }

        ItemStack staged = mc.player.getInventory().getStack(restockPlacementHotbarSlot);
        if (!(staged.getItem() instanceof BlockItem blockItem) || !(blockItem.getBlock() instanceof ShulkerBoxBlock)) {
            abortRestock("The staged supply shulker disappeared before placement.");
            return;
        }

        if (!BlockUtils.canPlaceBlock(restockShulkerPos, true, blockItem.getBlock())) {
            abortRestock("The shulker position next to the Ender Chest became blocked.");
            return;
        }

        if (!withinRange(restockShulkerPos)) {
            abortRestock("The supply shulker position moved outside placement range.");
            return;
        }

        sendAirPlacement(restockShulkerPos, restockPlacementHotbarSlot);
        transitionRestock(RestockState.WaitShulkerBlock);
    }

    private void waitRestockShulkerBlock() {
        if (mc.world.getBlockState(restockShulkerPos).getBlock() instanceof ShulkerBoxBlock) {
            transitionRestock(RestockState.OpenShulker);
            return;
        }

        if (restockStateTicks >= restockTimeout.get()) {
            abortRestock("The server did not accept the air-placed supply shulker.");
        }
    }

    private void openRestockShulker() {
        if (restockStateTicks < restockActionDelay.get()) return;
        if (!(mc.world.getBlockState(restockShulkerPos).getBlock() instanceof ShulkerBoxBlock)) {
            abortRestock("The supply shulker disappeared before it could be opened.");
            return;
        }

        interactRestockBlock(restockShulkerPos);
        transitionRestock(RestockState.WaitShulkerScreen);
    }

    private void waitRestockShulkerScreen() {
        if (mc.player.currentScreenHandler instanceof ShulkerBoxScreenHandler handler) {
            restockContainerId = handler.syncId;
            transitionRestock(RestockState.PrepareCapacity);
            return;
        }

        if (restockStateTicks >= restockTimeout.get()) {
            abortRestock("The supply shulker screen did not open.");
        }
    }

    private void prepareRestockCapacity() {
        if (!(mc.player.currentScreenHandler instanceof ShulkerBoxScreenHandler handler) || handler.syncId != restockContainerId) {
            abortRestock("The supply shulker screen closed while preparing inventory space.");
            return;
        }

        RestockLoadPlan loadPlan = computeRestockLoadPlan(handler);
        restockTransferSlots.clear();
        restockTransferSlots.addAll(loadPlan.transferSlots());
        restockPartialTransfer = loadPlan.partial();
        restockPlannedSoulStacks = loadPlan.soulStacks();
        restockPlannedSkullStacks = loadPlan.skullStacks();
        restockIgnoredShulkerStacks = loadPlan.ignoredStacks();

        if (loadPlan.dropSlots().isEmpty()) {
            if (restockTransferSlots.isEmpty()) {

                restockLeftoverStacks = countNonEmptyContainerSlots(handler, 27);
                warning("Smart restock found no transferable Soul Sand/Soul Soil or Wither Skeleton Skull stack. Other shulker contents were left untouched.");
                mc.player.closeHandledScreen();
                transitionRestock(RestockState.WaitShulkerClose);
                return;
            }

            transitionRestock(RestockState.Transfer);
            return;
        }

        restockPendingDropSlots.clear();
        restockPendingDropSlots.addAll(loadPlan.dropSlots());
        for (int inventorySlot : loadPlan.dropSlots()) {
            dropPlayerInventoryStack(inventorySlot);
            restockDroppedStacks++;
        }
        transitionRestock(RestockState.WaitCapacitySync);
    }

    private void waitRestockCapacitySync() {
        if (!(mc.player.currentScreenHandler instanceof ShulkerBoxScreenHandler handler) || handler.syncId != restockContainerId) {
            abortRestock("The supply shulker screen closed while waiting for inventory-space synchronization.");
            return;
        }

        boolean allDropped = true;
        for (int inventorySlot : restockPendingDropSlots) {
            if (!mc.player.getInventory().getStack(inventorySlot).isEmpty()) {
                allDropped = false;
                break;
            }
        }

        if (allDropped) {
            restockPendingDropSlots.clear();

            transitionRestock(RestockState.PrepareCapacity);
            return;
        }

        if (restockStateTicks >= restockTimeout.get()) {
            abortRestock("The server did not synchronize the dropped inventory stacks in time.");
        }
    }

    private void transferRestockShulker() {
        if (!(mc.player.currentScreenHandler instanceof ShulkerBoxScreenHandler handler) || handler.syncId != restockContainerId) {
            abortRestock("The supply shulker screen closed before quick transfer.");
            return;
        }
        if (restockStateTicks < restockActionDelay.get()) return;

        for (int slot : restockTransferSlots) {
            if (slot < 0 || slot >= Math.min(27, handler.slots.size())) continue;
            if (!handler.getSlot(slot).hasStack()) continue;
            mc.interactionManager.clickSlot(
                handler.syncId,
                slot,
                0,
                SlotActionType.QUICK_MOVE,
                mc.player
            );
        }

        transitionRestock(RestockState.Settle);
    }

    private void settleRestockShulker() {
        if (!(mc.player.currentScreenHandler instanceof ShulkerBoxScreenHandler handler) || handler.syncId != restockContainerId) {
            abortRestock("The supply shulker screen closed before transfer synchronization finished.");
            return;
        }

        if (restockStateTicks >= restockSettleDelay.get() && selectedTransferSlotsEmpty(handler)) {
            restockLeftoverStacks = countNonEmptyContainerSlots(handler, 27);
            mc.player.closeHandledScreen();
            transitionRestock(RestockState.WaitShulkerClose);
            return;
        }

        int retryInterval = Math.max(1, restockSettleDelay.get());
        if (restockStateTicks >= restockSettleDelay.get()
            && restockStateTicks < restockTimeout.get()
            && restockStateTicks % retryInterval == 0) {
            sendRemainingSelectedShulkerQuickMoves(handler);
        }

        if (restockStateTicks >= restockTimeout.get()) {

            restockLeftoverStacks = countNonEmptyContainerSlots(handler, 27);
            warning("Restock transfer timed out with %d shulker stack%s left behind; resuming with everything that was accepted.", restockLeftoverStacks, restockLeftoverStacks == 1 ? "" : "s");
            mc.player.closeHandledScreen();
            transitionRestock(RestockState.WaitShulkerClose);
        }
    }

    private boolean selectedTransferSlotsEmpty(ShulkerBoxScreenHandler handler) {
        for (int slot : restockTransferSlots) {
            if (slot >= 0 && slot < Math.min(27, handler.slots.size()) && handler.getSlot(slot).hasStack()) return false;
        }
        return true;
    }

    private void sendRemainingSelectedShulkerQuickMoves(ShulkerBoxScreenHandler handler) {
        for (int slot : restockTransferSlots) {
            if (slot < 0 || slot >= Math.min(27, handler.slots.size())) continue;
            if (!handler.getSlot(slot).hasStack()) continue;
            mc.interactionManager.clickSlot(
                handler.syncId,
                slot,
                0,
                SlotActionType.QUICK_MOVE,
                mc.player
            );
        }
    }

    private void waitRestockShulkerClose() {
        if (mc.player.currentScreenHandler == mc.player.playerScreenHandler) {
            finishRestock();
            return;
        }

        if (restockStateTicks >= restockTimeout.get()) {
            abortRestock("The supply shulker screen did not close cleanly.");
        }
    }

    private void finishRestock() {
        int dropped = restockDroppedStacks;
        int leftovers = restockLeftoverStacks;
        int ignored = restockIgnoredShulkerStacks;
        int materialLeftovers = Math.max(0, leftovers - ignored);
        boolean partial = restockPartialTransfer || materialLeftovers > 0;
        int soulStacks = restockPlannedSoulStacks;
        int skullStacks = restockPlannedSkullStacks;
        restoreRestockInputAndSelection();
        clearRestockState(false);
        outOfMaterialsReported = false;
        resumeAtPlacingSpeed = true;
        syncManagedElytraSpeed();
        syncAutoWalk();

        if (partial) {
            info("Smart restock complete. Planned %d soul stack%s + %d skull stack%s; %d Wither-material stack%s and %d non-material stack%s remain in the placed shulker%s.",
                soulStacks, soulStacks == 1 ? "" : "s",
                skullStacks, skullStacks == 1 ? "" : "s",
                materialLeftovers, materialLeftovers == 1 ? "" : "s",
                ignored, ignored == 1 ? "" : "s",
                dropped > 0 ? " after dropping expendable rockets" : "");
        } else {
            info("Smart restock complete. Took every Soul/Skull stack (%d soul + %d skull); %d non-material stack%s intentionally remain%s.",
                soulStacks,
                skullStacks,
                ignored, ignored == 1 ? "" : "s",
                dropped > 0 ? " after dropping expendable rockets" : "");
        }
    }

    private void abortRestock(String reason) {
        error("Automatic restock stopped: " + reason);
        if (mc.player != null && mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
            mc.player.closeHandledScreen();
        }
        restoreRestockInputAndSelection();
        clearRestockState(false);
        resumeAtPlacingSpeed = false;

        if (isActive()) toggle();
    }

    private void transitionRestock(RestockState next) {
        restockState = next;
        restockStateTicks = 0;
        syncManagedElytraSpeed();
    }

    private void clearRestockState(boolean restoreInput) {
        if (restoreInput) restoreRestockInputAndSelection();

        restockState = RestockState.Idle;
        restockStateTicks = 0;
        restockTravelStart = null;
        restockTravelDirection = Direction.NORTH;
        restockEnderChestPos = null;
        restockShulkerPos = null;
        restockShulkerStack = ItemStack.EMPTY;
        restockEnderChestSourceSlot = -1;
        restockPlacementHotbarSlot = -1;
        restockShulkerChestSlot = -1;
        restockShulkerInventorySlot = -1;
        restockShulkerReceiveSlot = -1;
        restockContainerId = -1;
        restockOriginalSelectedSlot = -1;
        restockMovementCaptured = false;
        restockAutoWalkWasActive = false;
        restockDroppedStacks = 0;
        restockTransferSlots.clear();
        restockPendingDropSlots.clear();
        restockPartialTransfer = false;
        restockPlannedSoulStacks = 0;
        restockPlannedSkullStacks = 0;
        restockIgnoredShulkerStacks = 0;
        restockLeftoverStacks = 0;
    }

    private void restoreRestockInputAndSelection() {
        if (mc.player == null) return;

        if (restockMovementCaptured) {
            boolean restoreForward = restockForwardWasDown;
            if (restockAutoWalkWasActive && !autoWalk.get()) restoreForward = autoWalkOriginalForwardDown;
            mc.options.forwardKey.setPressed(restoreForward);
            mc.options.backKey.setPressed(restockBackWasDown);
            mc.options.leftKey.setPressed(restockLeftWasDown);
            mc.options.rightKey.setPressed(restockRightWasDown);
            mc.options.jumpKey.setPressed(restockJumpWasDown);
            mc.options.sneakKey.setPressed(restockSneakWasDown);
        }
        if (restockOriginalSelectedSlot >= 0 && restockOriginalSelectedSlot < 9) {
            InvUtils.swap(restockOriginalSelectedSlot, false);
        }
    }

    private int stageInventorySlotForPlacement(int sourceInventorySlot) {
        if (sourceInventorySlot < 0 || sourceInventorySlot > 35) return -1;
        if (sourceInventorySlot < 9) return sourceInventorySlot;
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) return -1;

        int hotbar = restockStagingSlot.get() - 1;
        mc.interactionManager.clickSlot(
            mc.player.playerScreenHandler.syncId,
            playerInventorySlotToHandlerId(sourceInventorySlot),
            hotbar,
            SlotActionType.SWAP,
            mc.player
        );
        return hotbar;
    }

    private void sendAirPlacement(BlockPos target, int hotbarSlot) {
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(target), Direction.UP, target, false);
        Runnable action = () -> {
            InvUtils.swap(hotbarSlot, false);
            BlockUtils.interact(hit, Hand.MAIN_HAND, swing.get());
        };

        if (rotate.get()) {
            Rotations.rotate(Rotations.getYaw(hit.getPos()), Rotations.getPitch(hit.getPos()), 100, action);
        } else {
            action.run();
        }
    }

    private void interactRestockBlock(BlockPos target) {
        Direction face = BlockUtils.getDirection(target);
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(target), face, target, false);
        Runnable action = () -> BlockUtils.interact(hit, Hand.MAIN_HAND, swing.get());

        if (rotate.get()) {
            Rotations.rotate(Rotations.getYaw(hit.getPos()), Rotations.getPitch(hit.getPos()), 100, action);
        } else {
            action.run();
        }
    }

    private BlockPos aboveHeadTarget() {
        return new BlockPos(
            (int) Math.floor(mc.player.getX()),
            (int) Math.ceil(mc.player.getBoundingBox().maxY),
            (int) Math.floor(mc.player.getZ())
        );
    }

    private BlockPos findRestockShulkerTarget() {
        if (restockEnderChestPos == null || restockShulkerStack.isEmpty()) return null;
        if (!(restockShulkerStack.getItem() instanceof BlockItem blockItem) || !(blockItem.getBlock() instanceof ShulkerBoxBlock)) return null;

        Direction side = leftOf(restockTravelDirection);
        Direction[] candidates = {
            side,
            side.getOpposite(),
            restockTravelDirection,
            restockTravelDirection.getOpposite()
        };

        for (Direction direction : candidates) {
            BlockPos candidate = restockEnderChestPos.offset(direction);
            if (!withinRange(candidate)) continue;
            if (BlockUtils.canPlaceBlock(candidate, true, blockItem.getBlock())) return candidate;
        }

        return null;
    }

    private int firstShulkerSlot(GenericContainerScreenHandler handler, int containerSlots) {
        for (int slot = 0; slot < Math.min(containerSlots, handler.slots.size()); slot++) {
            if (isShulkerStack(handler.getSlot(slot).getStack())) return slot;
        }
        return -1;
    }

    private int firstMatchingShulkerSlot(GenericContainerScreenHandler handler, ItemStack expected, int containerSlots) {
        for (int slot = 0; slot < Math.min(containerSlots, handler.slots.size()); slot++) {
            ItemStack stack = handler.getSlot(slot).getStack();
            if (ItemStack.areEqual(stack, expected)) return slot;
        }
        return -1;
    }

    private static boolean isShulkerStack(ItemStack stack) {
        return !stack.isEmpty()
            && stack.getItem() instanceof BlockItem blockItem
            && blockItem.getBlock() instanceof ShulkerBoxBlock;
    }

    private int findPlayerInventorySlot(Predicate<ItemStack> predicate) {
        for (int i = 0; i <= 35; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && predicate.test(stack)) return i;
        }
        return -1;
    }

    private int findMatchingPlayerInventorySlot(ItemStack expected) {
        if (expected == null || expected.isEmpty()) return -1;
        for (int i = 0; i <= 35; i++) {
            if (ItemStack.areEqual(mc.player.getInventory().getStack(i), expected)) return i;
        }
        return -1;
    }

    private int findEmptyPlayerInventorySlot() {
        for (int i = 0; i <= 35; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) return i;
        }
        return -1;
    }

    private int findOnePrinterDropSlot() {

        int bestRocket = -1;
        for (int i = 0; i <= 35; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isOf(Items.FIREWORK_ROCKET)) continue;
            if (bestRocket < 0 || stack.getCount() < mc.player.getInventory().getStack(bestRocket).getCount()) {
                bestRocket = i;
            }
        }
        if (bestRocket >= 0) return bestRocket;

        long currentSoul = 0;
        long currentSkulls = 0;
        for (int i = 0; i <= 35; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (isAllowedSoulStack(stack)) currentSoul += stack.getCount();
            else if (stack.isOf(Items.WITHER_SKELETON_SKULL)) currentSkulls += stack.getCount();
        }
        ItemStack offhand = mc.player.getOffHandStack();
        if (isAllowedSoulStack(offhand)) currentSoul += offhand.getCount();
        else if (offhand.isOf(Items.WITHER_SKELETON_SKULL)) currentSkulls += offhand.getCount();

        int best = -1;
        long bestCompleteWithers = Long.MIN_VALUE;
        long bestRatioGap = Long.MAX_VALUE;
        int bestStackSize = Integer.MAX_VALUE;

        for (int i = 0; i <= 35; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!isPrinterMaterial(stack)) continue;

            long soulAfter = currentSoul - (isAllowedSoulStack(stack) ? stack.getCount() : 0);
            long skullAfter = currentSkulls - (stack.isOf(Items.WITHER_SKELETON_SKULL) ? stack.getCount() : 0);
            long completeWithers = Math.min(soulAfter / 4L, skullAfter / 3L);
            long ratioGap = Math.abs(3L * soulAfter - 4L * skullAfter);

            if (best < 0
                || completeWithers > bestCompleteWithers
                || (completeWithers == bestCompleteWithers && ratioGap < bestRatioGap)
                || (completeWithers == bestCompleteWithers && ratioGap == bestRatioGap && stack.getCount() < bestStackSize)) {
                best = i;
                bestCompleteWithers = completeWithers;
                bestRatioGap = ratioGap;
                bestStackSize = stack.getCount();
            }
        }

        return best;
    }

    private boolean isPrinterMaterial(ItemStack stack) {
        return !stack.isEmpty() && (isAllowedSoulStack(stack) || stack.isOf(Items.WITHER_SKELETON_SKULL));
    }

    private void dropPlayerInventoryStack(int inventorySlot) {
        int handlerSlot = playerInventorySlotToCurrentHandlerId(inventorySlot);
        if (handlerSlot < 0) return;

        mc.interactionManager.clickSlot(
            mc.player.currentScreenHandler.syncId,
            handlerSlot,
            1,
            SlotActionType.THROW,
            mc.player
        );
    }

    private int playerInventorySlotToCurrentHandlerId(int inventorySlot) {
        if (inventorySlot < 0 || inventorySlot > 35) return -1;

        if (mc.player.currentScreenHandler == mc.player.playerScreenHandler) {
            return playerInventorySlotToHandlerId(inventorySlot);
        }

        int containerSlots;
        if (mc.player.currentScreenHandler instanceof ShulkerBoxScreenHandler) {
            containerSlots = 27;
        } else if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler chestMenu) {
            containerSlots = chestMenu.getRows() * 9;
        } else {
            return -1;
        }

        if (inventorySlot >= 9) return containerSlots + (inventorySlot - 9);
        return containerSlots + 27 + inventorySlot;
    }

    private static int playerInventorySlotToHandlerId(int inventorySlot) {
        return inventorySlot < 9 ? 36 + inventorySlot : inventorySlot;
    }

    private List<ItemStack> copyPlayerInventory() {
        List<ItemStack> result = new ArrayList<>(36);
        for (int i = 0; i <= 35; i++) result.add(mc.player.getInventory().getStack(i).copy());
        return result;
    }

    private static List<ItemStack> copyContainerStacks(ShulkerBoxScreenHandler handler, int count) {
        List<ItemStack> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) result.add(handler.getSlot(i).getStack().copy());
        return result;
    }

    private static int extraSlotsNeeded(List<ItemStack> playerInventory, List<ItemStack> incoming) {
        List<ItemStack> virtual = new ArrayList<>(playerInventory.size() + incoming.size());
        for (ItemStack stack : playerInventory) virtual.add(stack.copy());
        int physicalSlots = playerInventory.size();

        for (ItemStack source : incoming) {
            if (source.isEmpty()) continue;
            int remaining = source.getCount();

            for (ItemStack existing : virtual) {
                if (remaining <= 0) break;
                if (existing.isEmpty()) continue;
                if (!ItemStack.areItemsAndComponentsEqual(source, existing)) continue;

                int room = Math.max(0, existing.getMaxCount() - existing.getCount());
                if (room <= 0) continue;

                int moved = Math.min(room, remaining);
                existing.increment(moved);
                remaining -= moved;
            }

            while (remaining > 0) {
                int empty = -1;
                for (int i = 0; i < virtual.size(); i++) {
                    if (virtual.get(i).isEmpty()) {
                        empty = i;
                        break;
                    }
                }

                int moved = Math.min(source.getMaxCount(), remaining);
                ItemStack inserted = source.copy();
                inserted.setCount(moved);

                if (empty >= 0) virtual.set(empty, inserted);
                else virtual.add(inserted);
                remaining -= moved;
            }
        }

        return Math.max(0, virtual.size() - physicalSlots);
    }

    private RestockLoadPlan computeRestockLoadPlan(ShulkerBoxScreenHandler handler) {
        List<ItemStack> incoming = copyContainerStacks(handler, 27);
        List<Integer> materialSourceSlots = witherMaterialSourceSlots(incoming);
        List<ItemStack> baseInventory = copyPlayerInventory();
        int ignoredStacks = countNonEmptyContainerSlots(handler, 27) - materialSourceSlots.size();

        if (materialSourceSlots.isEmpty()) {
            return new RestockLoadPlan(List.of(), List.of(), false, 0, 0, ignoredStacks);
        }

        List<ItemStack> allMaterialStacks = stacksForSourceSlots(incoming, materialSourceSlots);
        if (extraSlotsNeeded(baseInventory, allMaterialStacks) == 0) {
            return makeRestockLoadPlan(List.of(), materialSourceSlots, incoming, false, ignoredStacks);
        }

        List<ItemStack> virtualInventory = copyStacks(baseInventory);
        List<Integer> drops = new ArrayList<>();
        SmartTransferPlan best = computeSmartPartialTransfer(virtualInventory, incoming);

        for (int guard = 0; guard < 36; guard++) {
            int bestDropSlot = -1;
            SmartTransferPlan bestAfterDrop = best;
            List<ItemStack> bestVirtual = null;

            for (int slot = 0; slot < 36; slot++) {
                if (drops.contains(slot)) continue;
                ItemStack stack = virtualInventory.get(slot);
                if (!stack.isOf(Items.FIREWORK_ROCKET)) continue;

                List<ItemStack> testInventory = copyStacks(virtualInventory);
                testInventory.set(slot, ItemStack.EMPTY);
                SmartTransferPlan candidate = computeSmartPartialTransfer(testInventory, incoming);

                if (isBetterSmartPlan(candidate, bestAfterDrop)) {
                    bestDropSlot = slot;
                    bestAfterDrop = candidate;
                    bestVirtual = testInventory;
                }
            }

            if (bestDropSlot < 0) break;
            virtualInventory = bestVirtual;
            drops.add(bestDropSlot);
            best = bestAfterDrop;

            if (best.transferSlots().size() >= materialSourceSlots.size()) break;
        }

        boolean partial = best.transferSlots().size() < materialSourceSlots.size();
        return new RestockLoadPlan(
            List.copyOf(drops),
            List.copyOf(best.transferSlots()),
            partial,
            best.soulStacks(),
            best.skullStacks(),
            ignoredStacks
        );
    }

    private SmartTransferPlan computeSmartPartialTransfer(List<ItemStack> playerInventory, List<ItemStack> incoming) {

        SmartTransferPlan exactFullStackPlan = computeExactFullStackSmartTransfer(playerInventory, incoming);
        if (exactFullStackPlan != null) return exactFullStackPlan;

        List<ItemStack> virtual = copyStacks(playerInventory);
        List<Integer> remaining = witherMaterialSourceSlots(incoming);
        List<Integer> selected = new ArrayList<>();

        long soulItems = countSoulItems(virtual);
        long skullItems = countSkullItems(virtual);

        if (mc.player != null) {
            ItemStack offhand = mc.player.getOffHandStack();
            if (isAllowedSoulStack(offhand)) soulItems += offhand.getCount();
            else if (offhand.isOf(Items.WITHER_SKELETON_SKULL)) skullItems += offhand.getCount();
        }

        int soulStacks = 0;
        int skullStacks = 0;
        int addedItems = 0;

        while (!remaining.isEmpty()) {
            SmartTransferCandidate best = null;

            for (int sourceSlot : remaining) {
                ItemStack source = incoming.get(sourceSlot);
                if (!isPrinterMaterial(source)) continue;

                List<ItemStack> test = copyStacks(virtual);
                if (!insertEntireStack(test, source)) continue;

                long candidateSoulItems = soulItems + (isAllowedSoulStack(source) ? source.getCount() : 0);
                long candidateSkullItems = skullItems + (source.isOf(Items.WITHER_SKELETON_SKULL) ? source.getCount() : 0);
                long completeWithers = Math.min(candidateSoulItems / 4L, candidateSkullItems / 3L);
                long ratioGap = Math.abs(3L * candidateSoulItems - 4L * candidateSkullItems);
                int emptySlotsRemaining = countEmptySlots(test);
                int candidateAddedItems = addedItems + source.getCount();

                SmartTransferCandidate candidate = new SmartTransferCandidate(
                    sourceSlot,
                    test,
                    candidateSoulItems,
                    candidateSkullItems,
                    completeWithers,
                    ratioGap,
                    emptySlotsRemaining,
                    candidateAddedItems,
                    source.getCount()
                );

                if (isBetterSmartCandidate(candidate, best)) best = candidate;
            }

            if (best == null) break;

            selected.add(best.sourceSlot());
            remaining.remove(Integer.valueOf(best.sourceSlot()));
            virtual = best.virtualInventory();
            soulItems = best.soulItems();
            skullItems = best.skullItems();
            addedItems = best.addedItems();

            ItemStack selectedStack = incoming.get(best.sourceSlot());
            if (isAllowedSoulStack(selectedStack)) soulStacks++;
            else skullStacks++;
        }

        long completeWithers = Math.min(soulItems / 4L, skullItems / 3L);
        long ratioGap = Math.abs(3L * soulItems - 4L * skullItems);

        return new SmartTransferPlan(
            List.copyOf(selected),
            soulStacks,
            skullStacks,
            addedItems,
            completeWithers,
            ratioGap
        );
    }

    private SmartTransferPlan computeExactFullStackSmartTransfer(List<ItemStack> playerInventory, List<ItemStack> incoming) {
        List<Integer> sandSlots = new ArrayList<>();
        List<Integer> soilSlots = new ArrayList<>();
        List<Integer> skullSlots = new ArrayList<>();

        ItemStack sandPrototype = ItemStack.EMPTY;
        ItemStack soilPrototype = ItemStack.EMPTY;
        ItemStack skullPrototype = ItemStack.EMPTY;

        for (int slot : witherMaterialSourceSlots(incoming)) {
            ItemStack stack = incoming.get(slot);

            if (stack.getCount() != stack.getMaxCount()) return null;

            if (stack.isOf(Items.SOUL_SAND)) {
                if (sandPrototype.isEmpty()) sandPrototype = stack;
                else if (!ItemStack.areItemsAndComponentsEqual(sandPrototype, stack)) return null;
                sandSlots.add(slot);
            } else if (stack.isOf(Items.SOUL_SOIL)) {
                if (soilPrototype.isEmpty()) soilPrototype = stack;
                else if (!ItemStack.areItemsAndComponentsEqual(soilPrototype, stack)) return null;
                soilSlots.add(slot);
            } else if (stack.isOf(Items.WITHER_SKELETON_SKULL)) {
                if (skullPrototype.isEmpty()) skullPrototype = stack;
                else if (!ItemStack.areItemsAndComponentsEqual(skullPrototype, stack)) return null;
                skullSlots.add(slot);
            }
        }

        if (sandSlots.isEmpty() && soilSlots.isEmpty() && skullSlots.isEmpty()) {
            return new SmartTransferPlan(List.of(), 0, 0, 0, 0, 0);
        }

        long carriedSoul = countSoulItems(playerInventory);
        long carriedSkulls = countSkullItems(playerInventory);
        if (mc.player != null) {
            ItemStack offhand = mc.player.getOffHandStack();
            if (isAllowedSoulStack(offhand)) carriedSoul += offhand.getCount();
            else if (offhand.isOf(Items.WITHER_SKELETON_SKULL)) carriedSkulls += offhand.getCount();
        }

        SmartTransferPlan best = null;
        int bestEmptySlots = -1;

        for (int sandCount = 0; sandCount <= sandSlots.size(); sandCount++) {
            for (int soilCount = 0; soilCount <= soilSlots.size(); soilCount++) {
                for (int skullCount = 0; skullCount <= skullSlots.size(); skullCount++) {
                    int selectedCount = sandCount + soilCount + skullCount;
                    if (selectedCount == 0) continue;

                    List<ItemStack> virtual = copyStacks(playerInventory);
                    boolean fits = true;

                    for (int i = 0; i < sandCount && fits; i++) {
                        fits = insertEntireStack(virtual, incoming.get(sandSlots.get(i)));
                    }
                    for (int i = 0; i < soilCount && fits; i++) {
                        fits = insertEntireStack(virtual, incoming.get(soilSlots.get(i)));
                    }
                    for (int i = 0; i < skullCount && fits; i++) {
                        fits = insertEntireStack(virtual, incoming.get(skullSlots.get(i)));
                    }
                    if (!fits) continue;

                    List<Integer> selected = new ArrayList<>(selectedCount);
                    selected.addAll(sandSlots.subList(0, sandCount));
                    selected.addAll(soilSlots.subList(0, soilCount));
                    selected.addAll(skullSlots.subList(0, skullCount));

                    long addedSoul = 0;
                    for (int i = 0; i < sandCount; i++) addedSoul += incoming.get(sandSlots.get(i)).getCount();
                    for (int i = 0; i < soilCount; i++) addedSoul += incoming.get(soilSlots.get(i)).getCount();

                    long addedSkulls = 0;
                    for (int i = 0; i < skullCount; i++) addedSkulls += incoming.get(skullSlots.get(i)).getCount();

                    long finalSoul = carriedSoul + addedSoul;
                    long finalSkulls = carriedSkulls + addedSkulls;
                    long completeWithers = Math.min(finalSoul / 4L, finalSkulls / 3L);
                    long ratioGap = Math.abs(3L * finalSoul - 4L * finalSkulls);
                    int totalItems = (int) Math.min(Integer.MAX_VALUE, addedSoul + addedSkulls);
                    int emptySlots = countEmptySlots(virtual);

                    SmartTransferPlan candidate = new SmartTransferPlan(
                        List.copyOf(selected),
                        sandCount + soilCount,
                        skullCount,
                        totalItems,
                        completeWithers,
                        ratioGap
                    );

                    if (isBetterSmartPlan(candidate, best)
                        || (sameSmartPlanScore(candidate, best) && emptySlots > bestEmptySlots)) {
                        best = candidate;
                        bestEmptySlots = emptySlots;
                    }
                }
            }
        }

        return best == null
            ? new SmartTransferPlan(List.of(), 0, 0, 0, Math.min(carriedSoul / 4L, carriedSkulls / 3L), Math.abs(3L * carriedSoul - 4L * carriedSkulls))
            : best;
    }

    private static boolean isBetterSmartCandidate(SmartTransferCandidate candidate, SmartTransferCandidate current) {
        if (current == null) return true;
        if (candidate.completeWithers() != current.completeWithers()) {
            return candidate.completeWithers() > current.completeWithers();
        }

        if (candidate.ratioGap() != current.ratioGap()) {
            return candidate.ratioGap() < current.ratioGap();
        }

        if (candidate.emptySlotsRemaining() != current.emptySlotsRemaining()) {
            return candidate.emptySlotsRemaining() > current.emptySlotsRemaining();
        }

        if (candidate.addedItems() != current.addedItems()) {
            return candidate.addedItems() > current.addedItems();
        }
        if (candidate.sourceStackItems() != current.sourceStackItems()) {
            return candidate.sourceStackItems() > current.sourceStackItems();
        }
        return candidate.sourceSlot() < current.sourceSlot();
    }

    private static boolean isBetterSmartPlan(SmartTransferPlan candidate, SmartTransferPlan current) {
        if (current == null) return true;
        if (candidate.completeWithers() != current.completeWithers()) {
            return candidate.completeWithers() > current.completeWithers();
        }

        if (candidate.totalItems() != current.totalItems()) {
            return candidate.totalItems() > current.totalItems();
        }
        if (candidate.ratioGap() != current.ratioGap()) {
            return candidate.ratioGap() < current.ratioGap();
        }
        return candidate.transferSlots().size() > current.transferSlots().size();
    }

    private static boolean sameSmartPlanScore(SmartTransferPlan candidate, SmartTransferPlan current) {
        if (candidate == null || current == null) return false;
        return candidate.completeWithers() == current.completeWithers()
            && candidate.totalItems() == current.totalItems()
            && candidate.ratioGap() == current.ratioGap()
            && candidate.transferSlots().size() == current.transferSlots().size();
    }

    private static List<Integer> witherMaterialSourceSlots(List<ItemStack> incoming) {
        List<Integer> result = new ArrayList<>();
        for (int slot = 0; slot < incoming.size(); slot++) {
            ItemStack stack = incoming.get(slot);
            if (stack.isEmpty()) continue;
            if (stack.isOf(Items.SOUL_SAND)
                || stack.isOf(Items.SOUL_SOIL)
                || stack.isOf(Items.WITHER_SKELETON_SKULL)) {
                result.add(slot);
            }
        }
        return result;
    }

    private static List<ItemStack> stacksForSourceSlots(List<ItemStack> incoming, List<Integer> sourceSlots) {
        List<ItemStack> result = new ArrayList<>(sourceSlots.size());
        for (int slot : sourceSlots) {
            if (slot >= 0 && slot < incoming.size()) result.add(incoming.get(slot));
        }
        return result;
    }

    private long countSoulItems(List<ItemStack> inventory) {
        long result = 0;
        for (ItemStack stack : inventory) {
            if (isAllowedSoulStack(stack)) result += stack.getCount();
        }
        return result;
    }

    private static long countSkullItems(List<ItemStack> inventory) {
        long result = 0;
        for (ItemStack stack : inventory) {
            if (stack.isOf(Items.WITHER_SKELETON_SKULL)) result += stack.getCount();
        }
        return result;
    }

    private static int countEmptySlots(List<ItemStack> inventory) {
        int result = 0;
        for (ItemStack stack : inventory) {
            if (stack.isEmpty()) result++;
        }
        return result;
    }

    private RestockLoadPlan makeRestockLoadPlan(
        List<Integer> drops,
        List<Integer> transferSlots,
        List<ItemStack> incoming,
        boolean partial,
        int ignoredStacks
    ) {
        int soulStacks = 0;
        int skullStacks = 0;
        for (int slot : transferSlots) {
            if (slot < 0 || slot >= incoming.size()) continue;
            ItemStack stack = incoming.get(slot);
            if (isAllowedSoulStack(stack)) soulStacks++;
            else if (stack.isOf(Items.WITHER_SKELETON_SKULL)) skullStacks++;
        }
        return new RestockLoadPlan(
            List.copyOf(drops),
            List.copyOf(transferSlots),
            partial,
            soulStacks,
            skullStacks,
            ignoredStacks
        );
    }

    private static boolean insertEntireStack(List<ItemStack> inventory, ItemStack source) {
        if (source.isEmpty()) return true;
        List<ItemStack> test = copyStacks(inventory);
        int remaining = source.getCount();

        for (ItemStack existing : test) {
            if (remaining <= 0) break;
            if (existing.isEmpty()) continue;
            if (!ItemStack.areItemsAndComponentsEqual(source, existing)) continue;

            int room = Math.max(0, existing.getMaxCount() - existing.getCount());
            if (room <= 0) continue;
            int moved = Math.min(room, remaining);
            existing.increment(moved);
            remaining -= moved;
        }

        for (int i = 0; remaining > 0 && i < test.size(); i++) {
            if (!test.get(i).isEmpty()) continue;
            int moved = Math.min(source.getMaxCount(), remaining);
            ItemStack inserted = source.copy();
            inserted.setCount(moved);
            test.set(i, inserted);
            remaining -= moved;
        }

        if (remaining > 0) return false;
        inventory.clear();
        inventory.addAll(test);
        return true;
    }

    private static int countNonEmptyContainerSlots(ShulkerBoxScreenHandler handler, int count) {
        int result = 0;
        for (int i = 0; i < Math.min(count, handler.slots.size()); i++) {
            if (handler.getSlot(i).hasStack()) result++;
        }
        return result;
    }

    private record RestockLoadPlan(
        List<Integer> dropSlots,
        List<Integer> transferSlots,
        boolean partial,
        int soulStacks,
        int skullStacks,
        int ignoredStacks
    ) {}

    private record SmartTransferPlan(
        List<Integer> transferSlots,
        int soulStacks,
        int skullStacks,
        int totalItems,
        long completeWithers,
        long ratioGap
    ) {}

    private record SmartTransferCandidate(
        int sourceSlot,
        List<ItemStack> virtualInventory,
        long soulItems,
        long skullItems,
        long completeWithers,
        long ratioGap,
        int emptySlotsRemaining,
        int addedItems,
        int sourceStackItems
    ) {}

    private static List<ItemStack> copyStacks(List<ItemStack> source) {
        List<ItemStack> result = new ArrayList<>(source.size());
        for (ItemStack stack : source) result.add(stack.copy());
        return result;
    }

    private void resetCurrentPlan() {
        clearNamingState(true);
        plan = null;
        step = 0;
        burstPhase = 0;
        attempts = 0;
        confirmCooldown = 0;
        placementCooldown = 0;
        inventoryCooldown = 0;
        finalSpawnWait = 0;
        finalSkullSent = false;
        finalSkullObservedPlaced = false;
        clearFinalWitherBaseline();
    }

    private void syncAutoWalk() {
        if (mc.player == null || mc.options == null) return;

        if (!autoWalk.get()) {
            releaseAutoWalkState();
            return;
        }

        if (restockState != RestockState.Idle) return;

        if (namingState != NamingState.Idle) {
            if (autoWalkStateCaptured) mc.options.forwardKey.setPressed(false);
            return;
        }

        if (!autoWalkStateCaptured) {
            autoWalkOriginalForwardDown = mc.options.forwardKey.isPressed();
            autoWalkStateCaptured = true;
        }

        mc.options.forwardKey.setPressed(true);
    }

    private void releaseAutoWalkState() {
        if (autoWalkStateCaptured && mc.options != null) {
            mc.options.forwardKey.setPressed(autoWalkOriginalForwardDown);
        }
        autoWalkStateCaptured = false;
    }

    private void syncManagedElytraSpeed() {
        if (!manageElytraSpeed.get()) {
            releaseManagedElytraState();
            return;
        }

        Efly efly = Modules.get().get(Efly.class);
        if (efly == null || !efly.isActive() || efly.isExternallyControlled()) {
            releaseManagedElytraState();
            return;
        }

        if (managedEfly != efly) {
            releaseManagedElytraState();
            managedEfly = efly;
        }

        double desiredSpeed;
        if (restockState == RestockState.Travel) desiredSpeed = notPlacingSpeed.get();
        else if (restockState != RestockState.Idle) desiredSpeed = 0.0;
        else if (namingState != NamingState.Idle) desiredSpeed = 0.0;
        else if (plan != null || resumeAtPlacingSpeed) desiredSpeed = getActivePlacingSpeed();
        else desiredSpeed = notPlacingSpeed.get();

        efly.setSpeedOverride(desiredSpeed);
    }

    private void releaseManagedElytraState() {
        if (managedEfly != null) managedEfly.clearSpeedOverride();
        managedEfly = null;
    }

    private void resetStepAttemptState() {
        attempts = 0;
        confirmCooldown = 0;
    }

    private String stepName() {
        if (burstPlacement.get()) {
            return switch (burstPhase) {
                case 0 -> "2x-soul-lower";
                case 1 -> "2x-soul-arms";
                case 2 -> finalSkullSent ? "spawn-confirm" : "3x-skulls";
                default -> "finish";
            };
        }

        return switch (step) {
            case 0 -> "base";
            case 1 -> "stem";
            case 2 -> "left-soul";
            case 3 -> "right-soul";
            case 4 -> "left-skull";
            case 5 -> "middle-skull";
            case 6 -> "final-skull";
            default -> "finish";
        };
    }

    private static String shortPos(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private enum RestockState {
        Idle("idle"),
        Travel("travel"),
        PrepareEnderChest("prepare-echest"),
        WaitEnderChestStage("stage-echest"),
        PlaceEnderChest("place-echest"),
        WaitEnderChestBlock("wait-echest"),
        OpenEnderChest("open-echest"),
        WaitEnderChestScreen("wait-echest-gui"),
        WaitEnderChestSpace("free-box-slot"),
        WaitShulkerPickup("take-shulker"),
        WaitEnderChestClose("close-echest"),
        PrepareShulker("prepare-shulker"),
        WaitShulkerStage("stage-shulker"),
        PlaceShulker("place-shulker"),
        WaitShulkerBlock("wait-shulker"),
        OpenShulker("open-shulker"),
        WaitShulkerScreen("wait-shulker-gui"),
        PrepareCapacity("make-space"),
        WaitCapacitySync("space-sync"),
        Transfer("quick-transfer"),
        Settle("settle"),
        WaitShulkerClose("resume");

        private final String label;

        RestockState(String label) {
            this.label = label;
        }
    }

    private enum NamingState {
        Idle,
        WaitTarget,
        PrepareTag,
        WaitStage,
        WaitSelected,
        WaitConfirm
    }

    public enum DirectionMode {
        Motion,
        Look
    }

    public enum NamingMode {
        InventoryCycle,
        Specific
    }

    private enum MaterialKind {
        Soul,
        Skull
    }

    private enum PlacementAttempt {
        Sent,
        Refilled,
        NoMaterial,
        Blocked
    }

    private record WitherPlan(
        BlockPos base,
        BlockPos[] soulPositions,
        BlockPos[] skullPositions,
        BlockPos bottomLeftClear,
        BlockPos bottomRightClear,
        BlockPos[] allStructurePositions
    ) {
        static WitherPlan create(BlockPos base, Direction left, Direction right) {
            BlockPos stem = base.up();
            BlockPos leftSoul = stem.offset(left);
            BlockPos rightSoul = stem.offset(right);

            BlockPos leftSkull = leftSoul.up();
            BlockPos middleSkull = stem.up();
            BlockPos rightSkull = rightSoul.up();

            BlockPos[] souls = { base, stem, leftSoul, rightSoul };
            BlockPos[] skulls = { leftSkull, middleSkull, rightSkull };
            BlockPos[] all = { base, stem, leftSoul, rightSoul, leftSkull, middleSkull, rightSkull };

            return new WitherPlan(
                base,
                souls,
                skulls,
                base.offset(left),
                base.offset(right),
                all
            );
        }
    }
}
