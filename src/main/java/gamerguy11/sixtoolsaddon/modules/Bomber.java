package gamerguy11.sixtoolsaddon.modules;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class Bomber extends Module {
    private static final int RECENT_TICKS = 100;
    private static final int WARN_INTERVAL_TICKS = 100;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPlacement = settings.createGroup("Placement");
    private final SettingGroup sgIgnition = settings.createGroup("Ignition");
    private final SettingGroup sgInventory = settings.createGroup("Inventory");

    private final Setting<Boolean> onlyWhileFlying = sgGeneral.add(new BoolSetting.Builder()
        .name("only-while-flying")
        .description("Only drops TNT while you glide on an elytra.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> requireEfly = sgGeneral.add(new BoolSetting.Builder()
        .name("require-efly")
        .description("Only drops TNT while the Efly module is on.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> minSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("min-speed")
        .description("Minimum speed (blocks per tick) before TNT is dropped. Stops you from blowing yourself up while hovering or landing.")
        .defaultValue(0.4)
        .min(0.0)
        .sliderMax(3.0)
        .build()
    );

    private final Setting<Boolean> notify = sgGeneral.add(new BoolSetting.Builder()
        .name("notify")
        .description("Tells you in chat when TNT or flint and steel runs out.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> placeDelay = sgPlacement.add(new IntSetting.Builder()
        .name("place-delay")
        .description("Ticks between drops. Your speed decides the spacing: 1 tick at 2 blocks per tick is a TNT every 2 blocks.")
        .defaultValue(2)
        .min(1)
        .sliderMax(20)
        .build()
    );

    private final Setting<Integer> perDrop = sgPlacement.add(new IntSetting.Builder()
        .name("tnt-per-drop")
        .description("How many TNT are placed in one drop (one behind the other along your flight line).")
        .defaultValue(1)
        .min(1)
        .sliderMax(4)
        .build()
    );

    private final Setting<Double> behind = sgPlacement.add(new DoubleSetting.Builder()
        .name("behind")
        .description("How far behind you, against your flight direction, the TNT is placed.")
        .defaultValue(2.0)
        .min(0.0)
        .sliderMax(6.0)
        .build()
    );

    private final Setting<Integer> depth = sgPlacement.add(new IntSetting.Builder()
        .name("depth")
        .description("How many blocks below your feet the TNT is placed.")
        .defaultValue(2)
        .min(0)
        .sliderMax(6)
        .build()
    );

    private final Setting<Double> maxRange = sgPlacement.add(new DoubleSetting.Builder()
        .name("max-place-range")
        .description("Maximum distance from your eyes to the TNT that is placed.")
        .defaultValue(4.5)
        .min(1.0)
        .sliderMax(6.0)
        .build()
    );

    private final Setting<Boolean> airPlace = sgPlacement.add(new BoolSetting.Builder()
        .name("air-place")
        .description("Places the TNT in mid-air when there is no block next to it to place against.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotate = sgPlacement.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Sends a placement rotation before each placement. Slower, only for strict servers.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> swing = sgPlacement.add(new BoolSetting.Builder()
        .name("swing")
        .description("Swings your hand when placing and lighting.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignite = sgIgnition.add(new BoolSetting.Builder()
        .name("ignite")
        .description("Lights every placed TNT with flint and steel. Needs flint and steel in your hotbar or offhand.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> igniteDelay = sgIgnition.add(new IntSetting.Builder()
        .name("ignite-delay")
        .description("Ticks after placing before lighting. 0 lights it in the same tick, right after the placement packet.")
        .defaultValue(0)
        .min(0)
        .sliderMax(5)
        .visible(ignite::get)
        .build()
    );

    private final Setting<Integer> igniteAttempts = sgIgnition.add(new IntSetting.Builder()
        .name("ignite-attempts")
        .description("How many times a TNT that is still not lit is tried again.")
        .defaultValue(3)
        .min(1)
        .sliderMax(6)
        .visible(ignite::get)
        .build()
    );

    private final Setting<Double> igniteReach = sgIgnition.add(new DoubleSetting.Builder()
        .name("ignite-reach")
        .description("Maximum distance from your eyes to the TNT that is lit.")
        .defaultValue(4.5)
        .min(1.0)
        .sliderMax(6.0)
        .visible(ignite::get)
        .build()
    );

    private final Setting<Boolean> autoRefill = sgInventory.add(new BoolSetting.Builder()
        .name("auto-refill")
        .description("Moves TNT from your inventory to the hotbar when the hotbar has none.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> refillSlot = sgInventory.add(new IntSetting.Builder()
        .name("refill-slot")
        .description("Hotbar slot (1-9) the TNT goes to when no hotbar slot is empty.")
        .defaultValue(9)
        .range(1, 9)
        .sliderRange(1, 9)
        .visible(autoRefill::get)
        .build()
    );

    private final Setting<Integer> refillDelay = sgInventory.add(new IntSetting.Builder()
        .name("refill-delay")
        .description("Ticks to wait after a refill.")
        .defaultValue(4)
        .min(0)
        .sliderMax(20)
        .visible(autoRefill::get)
        .build()
    );

    private static final class Pending {
        final BlockPos pos;
        final int readyTick;
        final int expireTick;
        int attempts;

        Pending(BlockPos pos, int readyTick, int expireTick) {
            this.pos = pos;
            this.readyTick = readyTick;
            this.expireTick = expireTick;
        }
    }

    private final Map<BlockPos, Integer> recent = new HashMap<>();
    private final List<Pending> pending = new ArrayList<>();
    private int tick;
    private int nextPlaceTick;
    private int refillCooldown;
    private int lastWarnTick = -WARN_INTERVAL_TICKS;
    private Vec3d lastPos;
    private Vec3d flightDir = Vec3d.ZERO;
    private double speed;

    public Bomber() {
        super(SixToolsAddon.CATEGORY, "bomber", "Drops TNT behind and below you while you fly and lights it with flint and steel right after.");
    }

    @Override
    public void onActivate() {
        reset();
    }

    @Override
    public void onDeactivate() {
        reset();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        reset();
    }

    private void reset() {
        recent.clear();
        pending.clear();
        tick = 0;
        nextPlaceTick = 0;
        refillCooldown = 0;
        lastWarnTick = -WARN_INTERVAL_TICKS;
        lastPos = null;
        flightDir = Vec3d.ZERO;
        speed = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        tick++;
        if (refillCooldown > 0) refillCooldown--;
        recent.values().removeIf(placedAt -> tick - placedAt > RECENT_TICKS);
        trackFlight();

        if (shouldBomb() && tick >= nextPlaceTick) drop();
        lightPending();
    }

    private void trackFlight() {
        Vec3d now = mc.player.getEntityPos();
        if (lastPos != null) {
            Vec3d moved = now.subtract(lastPos);
            speed = moved.length();
            double horizontal = Math.hypot(moved.x, moved.z);
            if (horizontal > 0.05) flightDir = new Vec3d(moved.x / horizontal, 0, moved.z / horizontal);
        }
        lastPos = now;
        if (flightDir.lengthSquared() == 0) {
            double yaw = Math.toRadians(mc.player.getYaw());
            flightDir = new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw));
        }
    }

    private boolean shouldBomb() {
        if (onlyWhileFlying.get() && !mc.player.isGliding()) return false;
        if (requireEfly.get() && !Modules.get().isActive(Efly.class)) return false;
        return speed >= minSpeed.get();
    }

    private void drop() {
        FindItemResult tnt = InvUtils.findInHotbar(stack -> stack.isOf(Items.TNT));
        if (!tnt.found()) {
            refill();
            warn("No TNT in your hotbar.");
            return;
        }
        if (ignite.get() && !InvUtils.findInHotbar(stack -> stack.isOf(Items.FLINT_AND_STEEL)).found()) {
            warn("No flint and steel in your hotbar or offhand - not dropping TNT you cannot light.");
            return;
        }

        Vec3d eye = mc.player.getEyePos();
        for (int i = 0; i < perDrop.get(); i++) {
            double back = behind.get() + i;
            Vec3d at = mc.player.getEntityPos().subtract(flightDir.x * back, 0, flightDir.z * back);
            BlockPos pos = BlockPos.ofFloored(at.x, at.y, at.z).down(depth.get());

            if (recent.containsKey(pos) || !mc.world.getBlockState(pos).isReplaceable()) continue;
            if (eye.distanceTo(Vec3d.ofCenter(pos)) > maxRange.get()) continue;

            if (BlockUtils.place(pos, tnt, rotate.get(), 50, swing.get(), true, airPlace.get())) {
                recent.put(pos, tick);
                if (ignite.get()) pending.add(new Pending(pos, tick + igniteDelay.get(), tick + igniteDelay.get() + 10));
            }
        }
        nextPlaceTick = tick + placeDelay.get();
    }

    private void lightPending() {
        if (pending.isEmpty()) return;
        FindItemResult flint = InvUtils.findInHotbar(stack -> stack.isOf(Items.FLINT_AND_STEEL));
        Vec3d eye = mc.player.getEyePos();
        boolean swapped = false;

        Iterator<Pending> it = pending.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            if (tick > p.expireTick || p.attempts >= igniteAttempts.get() || !flint.found()) {
                it.remove();
                continue;
            }
            if (tick < p.readyTick) continue;

            Vec3d center = Vec3d.ofCenter(p.pos);
            if (eye.distanceTo(center) > igniteReach.get()) {
                it.remove();
                continue;
            }

            if (p.attempts > 0 && !mc.world.getBlockState(p.pos).isOf(Blocks.TNT)) {
                if (tick - p.readyTick >= 3) it.remove();
                continue;
            }

            Hand hand = flint.isOffhand() ? Hand.OFF_HAND : Hand.MAIN_HAND;
            if (!flint.isOffhand()) {
                InvUtils.swap(flint.slot(), true);
                swapped = true;
            }
            Direction face = Direction.getFacing(eye.x - center.x, eye.y - center.y, eye.z - center.z);
            Vec3d hit = center.add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
            mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(hand, new BlockHitResult(hit, face, p.pos, false), 0));
            if (swing.get()) mc.player.swingHand(hand);
            p.attempts++;
        }
        if (swapped) InvUtils.swapBack();
    }

    private void refill() {
        if (!autoRefill.get() || refillCooldown > 0) return;
        FindItemResult source = InvUtils.find(stack -> stack.isOf(Items.TNT), 9, 35);
        if (!source.found()) return;

        int slot = -1;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) {
                slot = i;
                break;
            }
        }
        if (slot < 0) slot = refillSlot.get() - 1;
        InvUtils.move().from(source.slot()).toHotbar(slot);
        refillCooldown = refillDelay.get();
    }

    private void warn(String message) {
        if (!notify.get() || tick - lastWarnTick < WARN_INTERVAL_TICKS) return;
        lastWarnTick = tick;
        warning(message);
    }
}
