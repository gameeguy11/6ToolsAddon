package gamerguy11.sixtoolsaddon.modules;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalGetToBlock;
import gamerguy11.sixtoolsaddon.SixToolsAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.*;
import net.minecraft.block.enums.ChestType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.*;

public class AutoStashSorter extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgFilter = settings.createGroup("Filtering");
    private final SettingGroup sgTiming = settings.createGroup("Timing");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> scanRadius = sgGeneral.add(new IntSetting.Builder()
        .name("scan-radius")
        .description("How far (in blocks) to look for chests/barrels around you when you start the module.")
        .defaultValue(16).min(3).sliderRange(3, 48)
        .build()
    );

    private final Setting<Boolean> includeBarrels = sgGeneral.add(new BoolSetting.Builder()
        .name("include-barrels")
        .description("Also scan barrels, not just chests.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> includeTrappedChests = sgGeneral.add(new BoolSetting.Builder()
        .name("include-trapped-chests")
        .description("Also scan trapped chests.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> maxContainers = sgGeneral.add(new IntSetting.Builder()
        .name("max-containers")
        .description("Safety cap on how many containers to scan in one run.")
        .defaultValue(40).min(1).sliderRange(1, 100)
        .build()
    );

    private final Setting<Boolean> keepHotbar = sgFilter.add(new BoolSetting.Builder()
        .name("keep-hotbar")
        .description("Don't touch your hotbar (slots 1-9), only sort your main inventory.")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<String>> keepItems = sgFilter.add(new StringListSetting.Builder()
        .name("keep-items")
        .description("Item IDs (e.g. minecraft:ender_pearl) to never put in a chest.")
        .build()
    );

    private final Setting<Boolean> fallbackToEmptiest = sgFilter.add(new BoolSetting.Builder()
        .name("fallback-to-emptiest-chest")
        .description("If no scanned chest already has this item, dump it in whichever scanned chest has the most free space instead of skipping it.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> interactDelay = sgTiming.add(new IntSetting.Builder()
        .name("interact-delay")
        .description("Ticks to wait after opening a container before reading/depositing.")
        .defaultValue(4).min(1).sliderRange(1, 20)
        .build()
    );

    private final Setting<Integer> closeDelay = sgTiming.add(new IntSetting.Builder()
        .name("close-delay")
        .description("Ticks to wait after closing a container before moving on.")
        .defaultValue(3).min(1).sliderRange(1, 20)
        .build()
    );

    private final Setting<Integer> gotoTimeout = sgTiming.add(new IntSetting.Builder()
        .name("goto-timeout")
        .description("Max ticks to spend walking to one container before giving up on it.")
        .defaultValue(200).min(20).sliderRange(20, 600)
        .build()
    );

    private final Setting<Boolean> chatFeedback = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-feedback")
        .description("Print progress messages.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> renderContainers = sgRender.add(new BoolSetting.Builder()
        .name("render-containers")
        .description("Highlight scanned containers.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> scannedColor = sgRender.add(new ColorSetting.Builder()
        .name("scanned-color")
        .defaultValue(new SettingColor(0, 200, 255, 80))
        .visible(renderContainers::get)
        .build()
    );

    private enum State { IDLE, GOTO_SCAN, OPEN_SCAN, READ_SCAN, CLOSE_SCAN, GOTO_DEPOSIT, OPEN_DEPOSIT, DEPOSIT, CLOSE_DEPOSIT, DONE }

    private static class StashContainer {
        final BlockPos pos;
        final int totalSlots;
        final Map<Item, Integer> counts = new HashMap<>();
        int freeSlots;
        boolean scanned = false;

        StashContainer(BlockPos pos, int totalSlots) {
            this.pos = pos;
            this.totalSlots = totalSlots;
        }
    }

    private State state = State.IDLE;
    private int stateTimer = 0;
    private int gotoTimer = 0;

    private final List<StashContainer> containers = new ArrayList<>();
    private int scanIndex = -1;

    private int currentPlayerInvSlot = -1;
    private StashContainer currentTarget;

    @Override
    public void onActivate() {
        containers.clear();
        scanIndex = -1;
        currentPlayerInvSlot = -1;
        currentTarget = null;
        stateTimer = 0;
        gotoTimer = 0;

        scanForContainers();

        if (containers.isEmpty()) {
            warning("No chests/barrels found within %d blocks.", scanRadius.get());
            toggle();
            return;
        }

        if (chatFeedback.get()) info("Found (highlight)%d(default) containers, scanning them...", containers.size());
        scanIndex = 0;
        state = State.GOTO_SCAN;
    }

    @Override
    public void onDeactivate() {
        if (mc.player != null && mc.currentScreen != null) mc.player.closeHandledScreen();
        cancelPathing();
        state = State.IDLE;
    }

    private void scanForContainers() {
        if (mc.player == null || mc.world == null) return;

        BlockPos center = mc.player.getBlockPos();
        int r = scanRadius.get();
        Set<BlockPos> processed = new HashSet<>();

        for (int x = -r; x <= r && containers.size() < maxContainers.get(); x++) {
            for (int y = -Math.min(r, 24); y <= Math.min(r, 24) && containers.size() < maxContainers.get(); y++) {
                for (int z = -r; z <= r && containers.size() < maxContainers.get(); z++) {
                    BlockPos pos = center.add(x, y, z);
                    if (processed.contains(pos)) continue;

                    BlockState bs = mc.world.getBlockState(pos);
                    Block block = bs.getBlock();

                    if (block instanceof ChestBlock && !(block instanceof TrappedChestBlock)) {
                        addChestLike(pos, bs, processed, false);
                    } else if (includeTrappedChests.get() && block instanceof TrappedChestBlock) {
                        addChestLike(pos, bs, processed, true);
                    } else if (includeBarrels.get() && block instanceof BarrelBlock) {
                        containers.add(new StashContainer(pos, 27));
                        processed.add(pos);
                    }
                }
            }
        }
    }

    private void addChestLike(BlockPos pos, BlockState bs, Set<BlockPos> processed, boolean trapped) {
        int slots = 27;
        if (bs.contains(Properties.CHEST_TYPE)) {
            ChestType type = bs.get(Properties.CHEST_TYPE);
            if (type != ChestType.SINGLE) {
                Direction facing = bs.get(Properties.HORIZONTAL_FACING);
                BlockPos other = type == ChestType.LEFT
                    ? pos.offset(facing.rotateYClockwise())
                    : pos.offset(facing.rotateYCounterclockwise());
                processed.add(other);
                slots = 54;
            }
        }
        processed.add(pos);
        containers.add(new StashContainer(pos, slots));
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (stateTimer > 0) {
            stateTimer--;
            return;
        }

        switch (state) {
            case GOTO_SCAN -> handleGoto(true);
            case OPEN_SCAN -> handleOpen(containers.get(scanIndex).pos, true);
            case READ_SCAN -> handleReadScan();
            case CLOSE_SCAN -> handleCloseScan();
            case GOTO_DEPOSIT -> handleGoto(false);
            case OPEN_DEPOSIT -> handleOpen(currentTarget.pos, false);
            case DEPOSIT -> handleDeposit();
            case CLOSE_DEPOSIT -> handleCloseDeposit();
            case DONE -> {
                if (chatFeedback.get()) info("Sorting done.");
                toggle();
            }
            default -> {}
        }
    }

    private void handleGoto(boolean scanning) {
        BlockPos target = scanning ? containers.get(scanIndex).pos : currentTarget.pos;
        double dist = Math.sqrt(mc.player.getBlockPos().getSquaredDistance(target));

        if (!BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing() && gotoTimer == 0) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess()
                .setGoalAndPath(new GoalGetToBlock(target));
        }

        gotoTimer++;
        if (dist <= 4.0) {
            cancelPathing();
            gotoTimer = 0;
            state = scanning ? State.OPEN_SCAN : State.OPEN_DEPOSIT;
            return;
        }

        if (gotoTimer > gotoTimeout.get()) {
            cancelPathing();
            gotoTimer = 0;
            if (chatFeedback.get()) warning("Timed out walking to a container, skipping it.");
            if (scanning) advanceScan();
            else advanceDeposit(true);
        }
    }

    private void handleOpen(BlockPos pos, boolean scanning) {
        Vec3d eyePos = mc.player.getEyePos();
        Direction face = bestFace(pos, eyePos);
        Vec3d hitVec = faceHitVector(pos, face);

        double yaw = Rotations.getYaw(hitVec);
        double pitch = Rotations.getPitch(hitVec);
        mc.player.setYaw((float) yaw);
        mc.player.setPitch((float) pitch);
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
            (float) yaw, (float) pitch, mc.player.isOnGround(), mc.player.horizontalCollision));

        BlockHitResult hit = new BlockHitResult(hitVec, face, pos, false);
        ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        if (result != ActionResult.SUCCESS && result != ActionResult.CONSUME) {
            mc.interactionManager.interactBlock(mc.player, Hand.OFF_HAND, hit);
        }

        stateTimer = interactDelay.get();
        state = scanning ? State.READ_SCAN : State.DEPOSIT;
    }

    private void handleReadScan() {
        StashContainer c = containers.get(scanIndex);

        if (!(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler)) {

            advanceScan();
            return;
        }

        int free = 0;
        for (int i = 0; i < c.totalSlots; i++) {
            Slot slot = handler.getSlot(i);
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) {
                free++;
            } else {
                c.counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }
        c.freeSlots = free;
        c.scanned = true;

        state = State.CLOSE_SCAN;
    }

    private void handleCloseScan() {
        if (mc.currentScreen != null) mc.player.closeHandledScreen();
        stateTimer = closeDelay.get();
        advanceScan();
    }

    private void advanceScan() {
        scanIndex++;
        if (scanIndex >= containers.size()) {
            startDepositPhase();
        } else {
            state = State.GOTO_SCAN;
        }
    }

    private void startDepositPhase() {
        if (chatFeedback.get()) info("Scan complete, sorting inventory...");
        currentPlayerInvSlot = -1;
        if (!advanceToNextItem()) {
            state = State.DONE;
        }
    }

    private boolean advanceToNextItem() {
        int start = keepHotbar.get() ? 9 : 0;

        for (int slot = start; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.isEmpty()) continue;
            if (isKept(stack.getItem())) continue;

            StashContainer target = findBestContainer(stack.getItem());
            if (target == null) continue;

            currentPlayerInvSlot = slot;
            currentTarget = target;
            state = State.GOTO_DEPOSIT;
            return true;
        }
        return false;
    }

    private boolean isKept(Item item) {
        String id = Registries.ITEM.getId(item).toString();
        for (String kept : keepItems.get()) {
            if (kept.equalsIgnoreCase(id)) return true;
        }
        return false;
    }

    private StashContainer findBestContainer(Item item) {
        StashContainer best = null;
        int bestCount = -1;
        double bestDist = Double.MAX_VALUE;

        for (StashContainer c : containers) {
            if (!c.scanned || c.freeSlots <= 0) continue;
            int count = c.counts.getOrDefault(item, 0);
            if (count <= 0) continue;

            double dist = mc.player.getBlockPos().getSquaredDistance(c.pos);
            if (count > bestCount || (count == bestCount && dist < bestDist)) {
                best = c;
                bestCount = count;
                bestDist = dist;
            }
        }

        if (best != null) return best;
        if (!fallbackToEmptiest.get()) return null;

        StashContainer emptiest = null;
        int mostFree = 0;
        for (StashContainer c : containers) {
            if (!c.scanned) continue;
            if (c.freeSlots > mostFree) {
                mostFree = c.freeSlots;
                emptiest = c;
            }
        }
        return emptiest;
    }

    private void handleDeposit() {
        if (!(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler)) {
            advanceDeposit(true);
            return;
        }

        ItemStack stack = mc.player.getInventory().getStack(currentPlayerInvSlot);
        if (stack.isEmpty() || currentTarget.freeSlots <= 0) {
            state = State.CLOSE_DEPOSIT;
            return;
        }

        int screenSlot = currentTarget.totalSlots + playerInvOffset(currentPlayerInvSlot);
        Slot slot = handler.getSlot(screenSlot);

        mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.QUICK_MOVE, mc.player);

        currentTarget.counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
        currentTarget.freeSlots = Math.max(0, currentTarget.freeSlots - 1);

        state = State.CLOSE_DEPOSIT;
        stateTimer = interactDelay.get();
    }

    private int playerInvOffset(int playerInvIndex) {
        if (playerInvIndex < 9) return 27 + playerInvIndex;
        return playerInvIndex - 9;
    }

    private void handleCloseDeposit() {
        if (mc.currentScreen != null) mc.player.closeHandledScreen();
        stateTimer = closeDelay.get();
        advanceDeposit(false);
    }

    private void advanceDeposit(boolean skippedOpen) {
        if (!advanceToNextItem()) {
            state = State.DONE;
        }
    }

    private void cancelPathing() {
        if (BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing()) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
        }
    }

    private Direction bestFace(BlockPos pos, Vec3d eyePos) {
        Vec3d center = Vec3d.ofCenter(pos);
        Vec3d toBlock = center.subtract(eyePos).normalize();
        Direction best = Direction.UP;
        double bestDot = Double.NEGATIVE_INFINITY;
        for (Direction face : Direction.values()) {
            Vec3d normal = Vec3d.of(face.getVector());
            double dot = toBlock.dotProduct(normal);
            if (dot > bestDot) {
                bestDot = dot;
                best = face;
            }
        }
        return best == Direction.DOWN ? Direction.UP : best;
    }

    private Vec3d faceHitVector(BlockPos pos, Direction face) {
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        switch (face) {
            case UP -> y = pos.getY() + 1.0;
            case DOWN -> y = pos.getY();
            case NORTH -> z = pos.getZ();
            case SOUTH -> z = pos.getZ() + 1.0;
            case WEST -> x = pos.getX();
            case EAST -> x = pos.getX() + 1.0;
        }
        return new Vec3d(x, y, z);
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!renderContainers.get()) return;
        for (StashContainer c : containers) {
            if (!c.scanned) continue;
            Box box = new Box(c.pos).expand(0.02);
            event.renderer.box(box, scannedColor.get(), scannedColor.get(), ShapeMode.Both, 0);
        }
    }

    public AutoStashSorter() {
        super(
            SixToolsAddon.CATEGORY,
            "auto-stash-sorter",
            "Scans nearby chests/barrels, then sorts your inventory into whichever one already has the most of each item."
        );
    }
}
