package gamerguy11.sixtoolsaddon.modules;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.block.BlockState;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MapPlacer extends Module {
    public enum Mode {
        Single,
        Wall
    }

    public enum WallBlock {
        SeaLantern(Items.SEA_LANTERN),
        Glowstone(Items.GLOWSTONE),
        CryingObsidian(Items.CRYING_OBSIDIAN);

        public final Item item;
        WallBlock(Item item) { this.item = item; }
    }

    public enum FillOrder {
        Rows,
        Columns
    }

    private enum Phase { IDLE, WALL, FRAMES, FILL }

    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgWall = this.settings.createGroup("Wall");

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Single = fill 1x1 item frames (original). Wall = right-click to build a backing wall, place frames and fill numbered maparts.")
        .defaultValue(Mode.Single)
        .build()
    );
    private final Setting<Boolean> nearbyFrames = sgGeneral.add(new BoolSetting.Builder()
        .name("nearby-frames")
        .description("Single mode: also fill empty item frames that were already there (not only frames you just placed).")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.Single)
        .build()
    );
    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Max distance to an item frame for the interaction (vanilla entity interaction range is 3).")
        .defaultValue(3.0)
        .min(1)
        .max(6)
        .sliderMax(6)
        .build()
    );
    private final Setting<Boolean> swingHand = sgGeneral.add(new BoolSetting.Builder()
        .name("swing")
        .description("Swing hand.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> wallWidth = sgWall.add(new IntSetting.Builder()
        .name("width")
        .description("Wall width in maps (as seen by you).")
        .defaultValue(3)
        .min(1).max(15).sliderMax(15)
        .visible(() -> mode.get() == Mode.Wall)
        .build()
    );
    private final Setting<Integer> wallHeight = sgWall.add(new IntSetting.Builder()
        .name("height")
        .description("Wall height in maps.")
        .defaultValue(4)
        .min(1).max(15).sliderMax(15)
        .visible(() -> mode.get() == Mode.Wall)
        .build()
    );
    private final Setting<WallBlock> wallBlock = sgWall.add(new EnumSetting.Builder<WallBlock>()
        .name("block")
        .description("Block used for the backing wall.")
        .defaultValue(WallBlock.SeaLantern)
        .visible(() -> mode.get() == Mode.Wall)
        .build()
    );
    private final Setting<FillOrder> fillOrder = sgWall.add(new EnumSetting.Builder<FillOrder>()
        .name("order")
        .description("How map numbers are laid out. Rows: 1 2 3 / 4 5 6. Columns: 1 4 / 2 5 / 3 6.")
        .defaultValue(FillOrder.Rows)
        .visible(() -> mode.get() == Mode.Wall)
        .build()
    );
    private final Setting<Integer> startIndex = sgWall.add(new IntSetting.Builder()
        .name("first-number")
        .description("Number in the mapart name that goes in the top-left frame.")
        .defaultValue(1)
        .min(0).max(999).sliderMax(20)
        .visible(() -> mode.get() == Mode.Wall)
        .build()
    );
    private final Setting<Boolean> airPlace = sgWall.add(new BoolSetting.Builder()
        .name("air-place")
        .description("Place wall blocks that have no neighbouring block to click on directly in the air (needs server support). Otherwise the wall grows from existing blocks.")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.Wall)
        .build()
    );
    private final Setting<Double> airDistance = sgWall.add(new DoubleSetting.Builder()
        .name("air-distance")
        .description("When you right-click on air, the wall is centred this many blocks in front of you.")
        .defaultValue(3.0)
        .min(1).max(6).sliderMax(6)
        .visible(() -> mode.get() == Mode.Wall)
        .build()
    );
    private final Setting<Double> placeRange = sgWall.add(new DoubleSetting.Builder()
        .name("place-range")
        .description("Max distance for placing wall blocks and frames.")
        .defaultValue(4.5)
        .min(1).max(6).sliderMax(6)
        .visible(() -> mode.get() == Mode.Wall)
        .build()
    );
    private final Setting<Integer> delay = sgWall.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks between actions.")
        .defaultValue(2)
        .min(0).max(20).sliderMax(10)
        .visible(() -> mode.get() == Mode.Wall)
        .build()
    );
    private final Setting<Integer> hotbarSlot = sgWall.add(new IntSetting.Builder()
        .name("hotbar-slot")
        .description("Hotbar slot (1-9) used to bring items/maps from your main inventory (both modes).")
        .defaultValue(9)
        .min(1).max(9).sliderMin(1).sliderMax(9)
        .build()
    );

    private static final int PLACE_WINDOW_TICKS = 20;
    private static final int RETRY_TICKS = 20;
    private static final int MAX_ATTEMPTS = 2;
    private static final double SCAN_RADIUS = 6.0;

    private int tickCounter = 0;
    private int lastFramePlaceTick = Integer.MIN_VALUE / 2;

    private final Set<Integer> seen = new HashSet<>();
    private final Set<Integer> mine = new HashSet<>();
    private final Set<Integer> filled = new HashSet<>();
    private final Set<Integer> abandoned = new HashSet<>();
    private final Map<Integer, Integer> attempts = new HashMap<>();
    private final Map<Integer, Integer> lastAttempt = new HashMap<>();

    private static final int NEVER = Integer.MIN_VALUE / 2;
    private static final int WALL_MAX_TRIES = 3;
    private static final int TIMEOUT_TICKS = 400;
    private static final int READY = 0, WAIT = 1, MISSING = 2;
    private static final Pattern NUMBER = Pattern.compile("\\d+");

    private static class Cell {
        final BlockPos wall;
        final BlockPos frame;
        final int mapNumber;
        boolean wallDone, wallFailed, frameDone, done;
        int wallTries, frameTries, fillTries;
        int wallLast = NEVER, frameLast = NEVER, fillLast = NEVER;

        Cell(BlockPos wall, BlockPos frame, int mapNumber) {
            this.wall = wall;
            this.frame = frame;
            this.mapNumber = mapNumber;
        }
    }

    private Phase phase = Phase.IDLE;
    private final List<Cell> cells = new ArrayList<>();
    private final List<Cell> wallOrder = new ArrayList<>();
    private Direction facing = Direction.NORTH;
    private int cooldown = 0;
    private int idleTicks = 0;
    private int originalSlot = 0;

    public MapPlacer() {
        super(SixToolsAddon.CATEGORY, "map-placer", "Fills item frames with maps. Single mode: 1x1. Wall mode: builds a wall and fills a numbered WxH mapart.");
    }

    @Override
    public void onActivate() {
        clearTracking();
        if (mode.get() == Mode.Wall) {
            info("Wall mode: hold an item frame and right-click to build a %dx%d map wall.", wallWidth.get(), wallHeight.get());
        } else {
            info("Place an item frame with a map in your hotbar and it will be filled automatically.");
        }
    }

    @Override
    public void onDeactivate() {
        clearTracking();
    }

    private void clearTracking() {
        seen.clear();
        mine.clear();
        filled.clear();
        abandoned.clear();
        attempts.clear();
        lastAttempt.clear();
        lastFramePlaceTick = Integer.MIN_VALUE / 2;

        phase = Phase.IDLE;
        cells.clear();
        wallOrder.clear();
        cooldown = 0;
        idleTicks = 0;
    }

    private void handleUseClick() {
        if (mc.currentScreen != null) return;

        boolean mainFrame = isFrameItem(mc.player.getMainHandStack().getItem());
        boolean holdingFrame = mainFrame || isFrameItem(mc.player.getOffHandStack().getItem());

        if (mode.get() == Mode.Wall) {
            if (!mainFrame) return;
            if (!mc.options.useKey.wasPressed()) return;
            while (mc.options.useKey.wasPressed()) {  }
            mc.options.useKey.setPressed(false);
            if (phase != Phase.IDLE) {
                warning("Already building a wall.");
                return;
            }
            startWall();
            return;
        }

        if (holdingFrame && mc.options.useKey.isPressed()) {
            lastFramePlaceTick = tickCounter;
        }
    }

    private static boolean isFrameItem(Item item) {
        return item == Items.ITEM_FRAME || item == Items.GLOW_ITEM_FRAME;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        tickCounter++;
        handleUseClick();

        if (mode.get() == Mode.Wall) {
            tickWall();
            return;
        }
        if (phase != Phase.IDLE) { phase = Phase.IDLE; cells.clear(); wallOrder.clear(); }

        tickSingle();
    }

    private void tickSingle() {
        Box box = mc.player.getBoundingBox().expand(SCAN_RADIUS);
        List<ItemFrameEntity> frames = mc.world.getEntitiesByClass(ItemFrameEntity.class, box, e -> true);

        pruneTracking(frames);
        if (frames.isEmpty()) return;

        for (ItemFrameEntity frame : frames) {
            int id = frame.getId();
            boolean isNew = seen.add(id);

            if (filled.contains(id) || abandoned.contains(id)) continue;

            if (!frame.getHeldItemStack().isEmpty()) {
                if (attempts.containsKey(id)) filled.add(id);
                continue;
            }

            if (isNew && tickCounter - lastFramePlaceTick <= PLACE_WINDOW_TICKS) mine.add(id);
            if (!mine.contains(id) && !nearbyFrames.get()) continue;

            if (!inRange(frame)) continue;

            Integer last = lastAttempt.get(id);
            if (last != null && tickCounter - last < RETRY_TICKS) continue;

            if (attempts.getOrDefault(id, 0) >= MAX_ATTEMPTS) {
                abandoned.add(id);
                continue;
            }

            if (!placeMap(frame)) return;

            attempts.merge(id, 1, Integer::sum);
            lastAttempt.put(id, tickCounter);
        }
    }

    private void pruneTracking(List<ItemFrameEntity> frames) {
        Set<Integer> present = new HashSet<>();
        for (ItemFrameEntity f : frames) present.add(f.getId());

        seen.retainAll(present);
        mine.retainAll(present);
        filled.retainAll(present);
        abandoned.retainAll(present);
        attempts.keySet().retainAll(present);
        lastAttempt.keySet().retainAll(present);
    }

    private boolean inRange(ItemFrameEntity frame) {
        double r = range.get();
        return frame.getBoundingBox().squaredMagnitude(mc.player.getEyePos()) <= r * r;
    }

    private int findMapSlot() {
        PlayerInventory inv = mc.player.getInventory();
        int selected = inv.getSelectedSlot();
        if (inv.getStack(selected).getItem() == Items.FILLED_MAP) return selected;
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).getItem() == Items.FILLED_MAP) return i;
        }
        for (int i = 9; i < 36; i++) {
            if (inv.getStack(i).getItem() == Items.FILLED_MAP) {
                int hb = hotbarSlot.get() - 1;
                swapWithHotbar(i, hb);
                return inv.getStack(hb).getItem() == Items.FILLED_MAP ? hb : -1;
            }
        }
        return -1;
    }

    private boolean placeMap(ItemFrameEntity frame) {
        int slot = findMapSlot();
        if (slot == -1) return false;

        PlayerInventory inv = mc.player.getInventory();
        int previous = inv.getSelectedSlot();
        boolean switched = slot != previous;

        if (switched) setSlot(slot);

        sendFrameInteract(frame);

        if (switched) {
            setSlot(previous);
        }
        return true;
    }

    private void sendFrameInteract(ItemFrameEntity frame) {
        EntityHitResult hit = new EntityHitResult(frame, frame.getBoundingBox().getCenter());
        mc.interactionManager.interactEntityAtLocation(mc.player, frame, hit, Hand.MAIN_HAND);
        mc.interactionManager.interactEntity(mc.player, frame, Hand.MAIN_HAND);
        if (swingHand.get()) mc.player.swingHand(Hand.MAIN_HAND);
    }

    private void startWall() {
        int w = wallWidth.get(), h = wallHeight.get();

        Direction view = mc.player.getHorizontalFacing();
        facing = view.getOpposite();
        Direction right = view.rotateYClockwise();

        BlockPos anchor;
        HitResult hr = mc.crosshairTarget;
        if (hr instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
            anchor = bhr.getBlockPos().offset(bhr.getSide());
        } else {
            Vec3d p = mc.player.getEyePos().add(mc.player.getRotationVec(1.0F).multiply(airDistance.get()));
            anchor = BlockPos.ofFloored(p);
        }

        cells.clear();
        wallOrder.clear();
        boolean rows = fillOrder.get() == FillOrder.Rows;
        for (int i = 0; i < w * h; i++) {
            int col = rows ? i % w : i / h;
            int row = rows ? i / w : i % h;
            BlockPos wall = anchor.offset(right, col - (w - 1) / 2).up(h - 1 - row);
            cells.add(new Cell(wall, wall.offset(facing), startIndex.get() + i));
        }
        wallOrder.addAll(cells);
        wallOrder.sort(Comparator.comparingInt(c -> c.wall.getY()));

        Item blockItem = wallBlock.get().item;
        int blocksNeeded = 0;
        for (Cell c : cells) if (mc.world.getBlockState(c.wall).isReplaceable()) blocksNeeded++;

        if (countItems(s -> s.getItem() == blockItem) < blocksNeeded) {
            error("Need %d %s, you have fewer.", blocksNeeded, wallBlock.get());
            cells.clear(); wallOrder.clear();
            return;
        }
        if (countItems(s -> isFrameItem(s.getItem())) < w * h) {
            error("Need %d item frames, you have fewer.", w * h);
            cells.clear(); wallOrder.clear();
            return;
        }

        Set<Integer> available = new HashSet<>();
        PlayerInventory inv = mc.player.getInventory();
        for (int i = 0; i < 36; i++) {
            int n = mapNumber(inv.getStack(i));
            if (n >= 0) available.add(n);
        }
        List<Integer> missing = new ArrayList<>();
        for (Cell c : cells) if (!available.contains(c.mapNumber)) missing.add(c.mapNumber);
        if (!missing.isEmpty()) {
            warning("Missing maparts (by name number): %s. Those frames will stay empty.", missing);
        }

        originalSlot = inv.getSelectedSlot();
        phase = Phase.WALL;
        idleTicks = 0;
        cooldown = 0;
        info("Building %dx%d wall (%s), facing %s.", w, h, wallBlock.get(), facing);
    }

    private void tickWall() {
        if (phase == Phase.IDLE) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        if (++idleTicks > TIMEOUT_TICKS) {
            error("Timed out (out of reach, no support to place against, or the server rejected placements).");
            finish(false);
            return;
        }
        if (idleTicks % 60 == 59) info("Waiting... move closer to the wall so everything is in reach.");

        switch (phase) {
            case WALL -> stepWall();
            case FRAMES -> stepFrames();
            case FILL -> stepFill();
            default -> {}
        }
    }

    private void progress() {
        idleTicks = 0;
        cooldown = delay.get();
    }

    private void stepWall() {
        boolean remaining = false;
        Cell target = null;
        BlockHitResult targetHit = null;

        for (Cell c : wallOrder) {
            if (c.wallDone || c.wallFailed) continue;

            if (!mc.world.getBlockState(c.wall).isReplaceable()) { c.wallDone = true; continue; }

            if (tickCounter - c.wallLast < RETRY_TICKS) { remaining = true; continue; }

            if (c.wallTries >= WALL_MAX_TRIES) {
                c.wallFailed = true;
                warning("Could not place wall block at %s.", c.wall.toShortString());
                continue;
            }

            remaining = true;
            if (target != null) continue;

            BlockHitResult hit = makeBlockHit(c.wall);
            if (hit != null) { target = c; targetHit = hit; }
        }

        if (!remaining) {
            phase = Phase.FRAMES;
            progress();
            return;
        }
        if (target == null) return;

        Item blockItem = wallBlock.get().item;
        int sel = selectItem(s -> s.getItem() == blockItem);
        if (sel == MISSING) {
            error("Ran out of %s.", wallBlock.get());
            finish(false);
            return;
        }
        if (sel == WAIT) { cooldown = Math.max(1, delay.get()); return; }

        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, targetHit);
        if (swingHand.get()) mc.player.swingHand(Hand.MAIN_HAND);
        target.wallTries++;
        target.wallLast = tickCounter;
        progress();
    }

    private BlockHitResult makeBlockHit(BlockPos target) {
        Vec3d eye = mc.player.getEyePos();
        double r = placeRange.get();
        double r2 = r * r;

        for (Direction d : Direction.values()) {
            BlockPos n = target.offset(d);
            BlockState ns = mc.world.getBlockState(n);
            if (ns.isAir() || ns.isReplaceable()) continue;
            if (!ns.isFullCube(mc.world, n)) continue;

            Direction face = d.getOpposite();
            Vec3d loc = Vec3d.ofCenter(n).add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
            if (eye.squaredDistanceTo(loc) > r2) continue;
            return new BlockHitResult(loc, face, n, false);
        }

        if (airPlace.get()) {
            Vec3d loc = Vec3d.ofCenter(target);
            if (eye.squaredDistanceTo(loc) <= r2) return new BlockHitResult(loc, Direction.DOWN, target, false);
        }
        return null;
    }

    private void stepFrames() {
        boolean remaining = false;
        Cell target = null;
        BlockHitResult targetHit = null;
        double r2 = placeRange.get() * placeRange.get();
        Vec3d eye = mc.player.getEyePos();

        for (Cell c : cells) {
            if (c.frameDone) continue;

            if (mc.world.getBlockState(c.wall).isReplaceable()) { c.frameDone = true; c.done = true; continue; }

            if (findFrame(c) != null) { c.frameDone = true; continue; }

            if (!mc.world.getBlockState(c.frame).isReplaceable()) {
                c.frameDone = true;
                c.done = true;
                warning("Space for frame at %s is blocked, skipping map %d.", c.frame.toShortString(), c.mapNumber);
                continue;
            }

            if (tickCounter - c.frameLast < RETRY_TICKS) { remaining = true; continue; }

            if (c.frameTries >= WALL_MAX_TRIES) {
                c.frameDone = true;
                c.done = true;
                warning("Could not place item frame at %s.", c.frame.toShortString());
                continue;
            }

            remaining = true;
            if (target != null) continue;

            Vec3d loc = Vec3d.ofCenter(c.wall).add(facing.getOffsetX() * 0.5, facing.getOffsetY() * 0.5, facing.getOffsetZ() * 0.5);
            if (eye.squaredDistanceTo(loc) > r2) continue;
            target = c;
            targetHit = new BlockHitResult(loc, facing, c.wall, false);
        }

        if (!remaining) {
            phase = Phase.FILL;
            progress();
            return;
        }
        if (target == null) return;

        int sel = selectItem(s -> isFrameItem(s.getItem()));
        if (sel == MISSING) {
            error("Ran out of item frames.");
            finish(false);
            return;
        }
        if (sel == WAIT) { cooldown = Math.max(1, delay.get()); return; }

        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, targetHit);
        if (swingHand.get()) mc.player.swingHand(Hand.MAIN_HAND);
        target.frameTries++;
        target.frameLast = tickCounter;
        progress();
    }

    private ItemFrameEntity findFrame(Cell c) {
        for (ItemFrameEntity f : mc.world.getEntitiesByClass(ItemFrameEntity.class, new Box(c.frame), e -> true)) {
            if (f.getHorizontalFacing() == facing && f.getBlockPos().equals(c.frame)) return f;
        }
        return null;
    }

    private void stepFill() {
        boolean pending = false;

        for (Cell c : cells) {
            if (c.done) continue;

            ItemFrameEntity frame = findFrame(c);
            if (frame == null) { c.done = true; continue; }
            if (!frame.getHeldItemStack().isEmpty()) { c.done = true; continue; }

            pending = true;

            if (tickCounter - c.fillLast < RETRY_TICKS) continue;

            if (c.fillTries >= MAX_ATTEMPTS) {
                c.done = true;
                warning("Map %d was not accepted by its frame.", c.mapNumber);
                continue;
            }

            if (!inRange(frame)) continue;

            final int number = c.mapNumber;
            int sel = selectItem(s -> mapNumber(s) == number);
            if (sel == MISSING) {
                c.done = true;
                warning("No mapart named %d in your inventory.", number);
                continue;
            }
            if (sel == WAIT) { cooldown = Math.max(1, delay.get()); return; }

            sendFrameInteract(frame);
            c.fillTries++;
            c.fillLast = tickCounter;
            progress();
            return;
        }

        if (!pending) {
            info("Map wall finished.");
            finish(true);
        }
    }

    private void finish(boolean success) {
        if (mc.player != null && mc.player.getInventory().getSelectedSlot() != originalSlot) {
            setSlot(originalSlot);
        }
        phase = Phase.IDLE;
        cells.clear();
        wallOrder.clear();
        idleTicks = 0;
        cooldown = 0;
    }

    private void setSlot(int slot) {
        InvUtils.swap(slot, false);
    }

    private void swapWithHotbar(int inventoryIndex, int hotbarSlot) {
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, inventoryIndex, hotbarSlot, SlotActionType.SWAP, mc.player);
    }

    private int selectItem(Predicate<ItemStack> pred) {
        PlayerInventory inv = mc.player.getInventory();

        if (pred.test(inv.getStack(inv.getSelectedSlot()))) return READY;

        for (int i = 0; i < 9; i++) {
            if (pred.test(inv.getStack(i))) {
                setSlot(i);
                return READY;
            }
        }
        for (int i = 9; i < 36; i++) {
            if (pred.test(inv.getStack(i))) {
                int hb = hotbarSlot.get() - 1;
                swapWithHotbar(i, hb);
                if (!pred.test(inv.getStack(hb))) return MISSING;
                setSlot(hb);
                return WAIT;
            }
        }
        return MISSING;
    }

    private int countItems(Predicate<ItemStack> pred) {
        PlayerInventory inv = mc.player.getInventory();
        int total = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isEmpty() && pred.test(s)) total += s.getCount();
        }
        return total;
    }

    private static int mapNumber(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() != Items.FILLED_MAP) return -1;
        String name = stack.getName().getString();
        Matcher m = NUMBER.matcher(name);
        int last = -1;
        while (m.find()) {
            String g = m.group();
            if (g.length() <= 6) last = Integer.parseInt(g);
        }
        return last;
    }
}
