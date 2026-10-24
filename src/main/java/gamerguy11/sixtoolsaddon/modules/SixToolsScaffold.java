package gamerguy11.sixtoolsaddon.modules;

import gamerguy11.sixtoolsaddon.utils.PacketPlaceTracker;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import gamerguy11.sixtoolsaddon.SixToolsAddon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.util.math.Box;

public class SixToolsScaffold extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<ListMode> listMode = sgGeneral.add(new EnumSetting.Builder<ListMode>()
        .name("block-list-mode")
        .description("Block list selection mode.")
        .defaultValue(ListMode.Whitelist)
        .build()
    );

    private final Setting<List<Block>> whitelist = sgGeneral.add(new BlockListSetting.Builder()
        .name("whitelist")
        .description("Blocks allowed to scaffold.")
        .defaultValue(Blocks.TNT)
        .visible(() -> listMode.get() == ListMode.Whitelist)
        .build()
    );

    private final Setting<List<Block>> blacklist = sgGeneral.add(new BlockListSetting.Builder()
        .name("blacklist")
        .description("Blocks denied to scaffold.")
        .defaultValue(Blocks.OBSIDIAN)
        .visible(() -> listMode.get() == ListMode.Blacklist)
        .build()
    );

    private final Setting<Integer> ext = sgGeneral.add(new IntSetting.Builder()
        .name("extend")
        .description("How far to place in front of you.")
        .defaultValue(1)
        .range(0, 5)
        .build()
    );

    private final Setting<Boolean> packetPlaceOnce = sgGeneral.add(new BoolSetting.Builder()
        .name("packet-place-once")
        .description("Experimental: one packet per block, sent again only once the server says it's still air.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> keepY = sgGeneral.add(new BoolSetting.Builder()
        .name("keep-y")
        .description("Places blocks only at a specific Y value.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> height = sgGeneral.add(new IntSetting.Builder()
        .name("height")
        .description("Y value to scaffold at.")
        .defaultValue(119)
        .range(-64, 320)
        .sliderRange(-64, 320)
        .visible(keepY::get)
        .build()
    );

    private final SettingGroup sgCoating = settings.createGroup("TNT Coating");

    private final Setting<Boolean> coating = sgCoating.add(new BoolSetting.Builder()
        .name("coating")
        .description("Also covers the exposed terrain around you with the scaffold block (TNT by default).")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> coatRadius = sgCoating.add(new IntSetting.Builder()
        .name("coating-radius")
        .description("Cube half-size: radius 4 scans a 9 x 9 x 9 block cube.")
        .defaultValue(4)
        .range(1, 8)
        .sliderRange(1, 8)
        .visible(coating::get)
        .build()
    );

    private final Setting<Integer> coatPerTick = sgCoating.add(new IntSetting.Builder()
        .name("coating-blocks-per-tick")
        .description("Maximum coating placements per tick.")
        .defaultValue(2)
        .range(1, 10)
        .sliderRange(1, 10)
        .visible(coating::get)
        .build()
    );

    private final Setting<Double> coatReach = sgCoating.add(new DoubleSetting.Builder()
        .name("coating-reach")
        .description("Maximum eye-to-face distance for coating placements.")
        .defaultValue(4.5)
        .range(1.0, 6.0)
        .sliderRange(1.0, 6.0)
        .visible(coating::get)
        .build()
    );

    private boolean worked = false;

    public SixToolsScaffold() {
        super(SixToolsAddon.CATEGORY, "6tools-scaffold", "Scaffolds blocks under you.");
        applyTitle("6Tools Scaffold");
    }

    private void applyTitle(String title) {
        try {
            java.lang.reflect.Field field = Module.class.getDeclaredField("title");
            field.setAccessible(true);
            field.set(this, title);
        } catch (Throwable ignored) {
        }
    }

    private FindItemResult findBlock() {
        if (listMode.get() == ListMode.Whitelist) {
            return InvUtils.findInHotbar(itemStack -> itemStack.getItem() instanceof BlockItem && whitelist.get().contains(Block.getBlockFromItem(itemStack.getItem())));
        }
        return InvUtils.findInHotbar(itemStack -> itemStack.getItem() instanceof BlockItem && !blacklist.get().contains(Block.getBlockFromItem(itemStack.getItem())));
    }

    private void coat() {
        int r = coatRadius.get();
        double reach = coatReach.get();
        Vec3d eye = mc.player.getEyePos();
        BlockPos center = mc.player.getBlockPos();
        Box playerBox = mc.player.getBoundingBox();

        List<BlockPos> targets = new ArrayList<>();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    pos.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    if (!mc.world.getWorldBorder().contains(pos) || !mc.world.getBlockState(pos).isAir()) continue;
                    if (playerBox.intersects(new Box(pos))) continue;
                    targets.add(pos.toImmutable());
                }
            }
        }
        if (targets.isEmpty()) return;
        targets.sort(Comparator.comparingDouble(t -> t.getSquaredDistance(eye)));

        FindItemResult item = findBlock();
        if (!item.found()) return;

        int placed = 0;
        for (BlockPos target : targets) {
            if (placed >= coatPerTick.get()) break;
            if (!PacketPlaceTracker.canSend(target)) continue;

            for (Direction dir : Direction.values()) {
                BlockPos support = target.offset(dir);
                if (!mc.world.getBlockState(support).isSolidBlock(mc.world, support)) continue;
                Direction face = dir.getOpposite();
                Vec3d hit = Vec3d.ofCenter(support).add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
                if (hit.distanceTo(eye) > reach) continue;

                InvUtils.swap(item.slot(), true);
                mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, new BlockHitResult(hit, face, support, false), 0));
                InvUtils.swapBack();
                PacketPlaceTracker.markSent(target, PacketPlaceTracker.DEFAULT_RESEND_TICKS);
                placed++;
                break;
            }
        }
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (coating.get()) coat();

        float f = MathHelper.sin(mc.player.getYaw() * 0.017453292f);
        float g = MathHelper.cos(mc.player.getYaw() * 0.017453292f);

        for (int i = 0; i <= (mc.player.getVelocity().x == 0.0 && mc.player.getVelocity().z == 0.0 ? 0 : ext.get()); i++) {
            Vec3d pos = mc.player.getEntityPos().add(-f * i, -0.5, g * i);
            if (keepY.get()) ((IVec3d) pos).meteor$setY(height.get() - 1.0);

            BlockPos bPos = BlockPos.ofFloored(pos);

            if (!mc.world.getWorldBorder().contains(bPos) || !mc.world.getBlockState(bPos).isReplaceable()) {
                worked = false;
                continue;
            }
            worked = true;
            if (packetPlaceOnce.get() && !PacketPlaceTracker.canSend(bPos)) continue;
            FindItemResult item;
            if (listMode.get() == ListMode.Whitelist) {
                item = InvUtils.findInHotbar(itemStack -> itemStack.getItem() instanceof BlockItem && whitelist.get().contains(Block.getBlockFromItem(itemStack.getItem())));
            } else {
                item = InvUtils.findInHotbar(itemStack -> itemStack.getItem() instanceof BlockItem && !blacklist.get().contains(Block.getBlockFromItem(itemStack.getItem())));
            }
            if (!item.found()) {
                return;
            } else {
                InvUtils.swap(item.slot(), true);
            }

            mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, new BlockHitResult(pos, Direction.getFacing(pos).getOpposite(), bPos, true), 0));
            if (packetPlaceOnce.get()) PacketPlaceTracker.markSent(bPos, PacketPlaceTracker.DEFAULT_RESEND_TICKS);

            InvUtils.swapBack();
        }
    }
    public enum ListMode {
        Blacklist,
        Whitelist
    }

    public boolean hasWorked() {
        return worked;
    }
}
