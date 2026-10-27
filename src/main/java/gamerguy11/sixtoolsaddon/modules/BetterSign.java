package gamerguy11.sixtoolsaddon.modules;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixin.AbstractSignEditScreenAccessor;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.MeteorStarscript;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.client.gui.screen.ingame.HangingSignEditScreen;
import net.minecraft.client.gui.screen.ingame.SignEditScreen;
import net.minecraft.item.HangingSignItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SignItem;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSignC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class BetterSign extends Module {
    private static final String DISPLAY_TITLE = "Better sign";
    private static final String[] DEFAULT_LINES = {"JOIN ChickenCult", "dc.gg/HX6rSFg3k", "Event hosting", "community"};
    private static final int OPEN_TIMEOUT_TICKS = 30;
    private static final int MAX_ATTEMPTS = 3;

    private final SettingGroup sgSign = settings.createGroup("Normal Sign Text");
    private final SettingGroup sgHang = settings.createGroup("Hanging Sign Text");
    private final SettingGroup sgAura = settings.createGroup("Sign Aura");
    private final SettingGroup sgExtra = settings.createGroup("Visible");
    private final SettingGroup sgPlace = settings.createGroup("Auto Place");

    private final Setting<Boolean> autoPlace = sgPlace.add(new BoolSetting.Builder()
        .name("auto-place")
        .description("Places a sign on the last block you placed or interacted with.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> placeDelay = sgPlace.add(new IntSetting.Builder()
        .name("place-delay")
        .description("Delay between sign placements in ticks.")
        .defaultValue(0)
        .min(0)
        .sliderMax(10)
        .visible(autoPlace::get)
        .build()
    );

    private final Setting<List<Item>> signTypes = sgPlace.add(new ItemListSetting.Builder()
        .name("do-not-place-these-signs")
        .description("Which signs to NOT use when auto-placing.")
        .filter(item -> item instanceof SignItem)
        .visible(autoPlace::get)
        .build()
    );

    private final Setting<Boolean> bothSides = sgExtra.add(new BoolSetting.Builder()
        .name("both-sides")
        .description("Writes on the back of the sign as well.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> differentText = sgExtra.add(new BoolSetting.Builder()
        .name("different-text-on-rear")
        .description("Writes different text on the back of the sign.")
        .defaultValue(false)
        .visible(bothSides::get)
        .build()
    );

    private final Setting<Boolean> autoDye = sgExtra.add(new BoolSetting.Builder()
        .name("auto-dye")
        .description("Dyes the signs you write on.")
        .defaultValue(false)
        .build()
    );

    private final Setting<List<Item>> dyeColors = sgExtra.add(new ItemListSetting.Builder()
        .name("dye-colors")
        .description("Which dyes to dye the sign with.")
        .visible(autoDye::get)
        .filter(this::isDye)
        .build()
    );

    private final Setting<Boolean> autoGlow = sgExtra.add(new BoolSetting.Builder()
        .name("auto-glow")
        .description("Makes the signs you write on glow.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> signAura = sgAura.add(new BoolSetting.Builder()
        .name("sign-aura")
        .description("Walks up to every sign in range and writes your text on it, front and back. Waxed signs and signs that already have the text are skipped.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> signAuraRotate = sgAura.add(new BoolSetting.Builder()
        .name("sign-aura-rotate")
        .description("Looks at the sign before editing it.")
        .defaultValue(true)
        .visible(signAura::get)
        .build()
    );

    private final Setting<Double> signAuraRange = sgAura.add(new DoubleSetting.Builder()
        .name("sign-aura-range")
        .description("How far away signs get edited.")
        .defaultValue(4.0)
        .min(0)
        .max(6)
        .sliderRange(0, 6)
        .visible(signAura::get)
        .build()
    );

    private final Setting<Integer> signAuraDelay = sgAura.add(new IntSetting.Builder()
        .name("sign-aura-delay")
        .description("Ticks between editing signs.")
        .defaultValue(5)
        .min(0)
        .sliderMax(20)
        .visible(signAura::get)
        .build()
    );

    private final Setting<String> lineOne = text(sgSign, "line-one", DEFAULT_LINES[0], null);
    private final Setting<String> lineTwo = text(sgSign, "line-two", DEFAULT_LINES[1], null);
    private final Setting<String> lineThree = text(sgSign, "line-three", DEFAULT_LINES[2], null);
    private final Setting<String> lineFour = text(sgSign, "line-four", DEFAULT_LINES[3], null);
    private final Setting<String> rearLineOne = text(sgSign, "rear-line-one", DEFAULT_LINES[0], differentText);
    private final Setting<String> rearLineTwo = text(sgSign, "rear-line-two", DEFAULT_LINES[1], differentText);
    private final Setting<String> rearLineThree = text(sgSign, "rear-line-three", DEFAULT_LINES[2], differentText);
    private final Setting<String> rearLineFour = text(sgSign, "rear-line-four", DEFAULT_LINES[3], differentText);

    private final Setting<String> hangLineOne = text(sgHang, "line-one", DEFAULT_LINES[0], null);
    private final Setting<String> hangLineTwo = text(sgHang, "line-two", DEFAULT_LINES[1], null);
    private final Setting<String> hangLineThree = text(sgHang, "line-three", DEFAULT_LINES[2], null);
    private final Setting<String> hangLineFour = text(sgHang, "line-four", DEFAULT_LINES[3], null);
    private final Setting<String> hangRearLineOne = text(sgHang, "rear-line-one", DEFAULT_LINES[0], differentText);
    private final Setting<String> hangRearLineTwo = text(sgHang, "rear-line-two", DEFAULT_LINES[1], differentText);
    private final Setting<String> hangRearLineThree = text(sgHang, "rear-line-three", DEFAULT_LINES[2], differentText);
    private final Setting<String> hangRearLineFour = text(sgHang, "rear-line-four", DEFAULT_LINES[3], differentText);

    private final List<Setting<String>> normalFront = List.of(lineOne, lineTwo, lineThree, lineFour);
    private final List<Setting<String>> normalRear = List.of(rearLineOne, rearLineTwo, rearLineThree, rearLineFour);
    private final List<Setting<String>> hangingFront = List.of(hangLineOne, hangLineTwo, hangLineThree, hangLineFour);
    private final List<Setting<String>> hangingRear = List.of(hangRearLineOne, hangRearLineTwo, hangRearLineThree, hangRearLineFour);

    private final Set<BlockPos> finished = new HashSet<>();
    private final Set<BlockPos> rearPending = new HashSet<>();
    private final Map<BlockPos, Integer> attempts = new HashMap<>();
    private final String[][] expected = new String[4][];
    private BlockPos activePos;
    private int activeTicks;
    private int timer;
    private BlockPos lastPlacedBlock;
    private Direction lastPlacedSide = Direction.UP;
    private int placeTimer;
    private boolean interacting;
    private BlockPos rearInteractPos;
    private int rearInteractTicks;

    public BetterSign() {
        super(SixToolsAddon.CATEGORY, "better-sign", "Writes your text on signs, on both sides, and can dye and glow them. Sign aura edits every sign around you.");
        applyTitle();
    }

    private Setting<String> text(SettingGroup group, String name, String value, Setting<Boolean> visibleWhen) {
        StringSetting.Builder builder = new StringSetting.Builder().name(name).defaultValue(value);
        if (visibleWhen != null) builder.visible(() -> bothSides.get() && visibleWhen.get());
        return group.add(builder.build());
    }

    private void applyTitle() {
        try {
            Field field = Module.class.getDeclaredField("title");
            field.setAccessible(true);
            field.set(this, DISPLAY_TITLE);
        } catch (Throwable ignored) {
           gamerguy11.sixtoolsaddon.utils.Swallowed.log("BetterSign:209", ignored);
        }
    }

    @Override
    public void onActivate() {
        resetState();
    }

    @Override
    public void onDeactivate() {
        resetState();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        resetState();
    }

    private void resetState() {
        finished.clear();
        rearPending.clear();
        attempts.clear();
        activePos = null;
        activeTicks = 0;
        timer = 0;
        lastPlacedBlock = null;
        lastPlacedSide = Direction.UP;
        placeTimer = 0;
        interacting = false;
        rearInteractPos = null;
        rearInteractTicks = 0;
    }

    private boolean isDye(Item item) {
        return item == Items.WHITE_DYE || item == Items.ORANGE_DYE || item == Items.MAGENTA_DYE || item == Items.LIGHT_BLUE_DYE
            || item == Items.YELLOW_DYE || item == Items.LIME_DYE || item == Items.PINK_DYE || item == Items.GRAY_DYE
            || item == Items.LIGHT_GRAY_DYE || item == Items.CYAN_DYE || item == Items.PURPLE_DYE || item == Items.BLUE_DYE
            || item == Items.BROWN_DYE || item == Items.GREEN_DYE || item == Items.RED_DYE || item == Items.BLACK_DYE;
    }

    private void refreshExpected() {
        expected[0] = render(normalFront);
        expected[1] = render(differentText.get() ? normalRear : normalFront);
        expected[2] = render(hangingFront);
        expected[3] = render(differentText.get() ? hangingRear : hangingFront);
    }

    private String[] render(List<Setting<String>> lines) {
        String[] out = new String[4];
        for (int i = 0; i < 4; i++) {
            String value;
            try {
                value = MeteorStarscript.run(MeteorStarscript.compile(lines.get(i).get()));
            } catch (Throwable error) {
                value = lines.get(i).get();
            }
            out[i] = value == null ? "" : value;
        }
        return out;
    }

    private String[] linesFor(boolean hanging, boolean front) {
        return expected[(hanging ? 2 : 0) + (front ? 0 : 1)];
    }

    private boolean isHanging(BlockPos pos) {
        return mc.world.getBlockState(pos).getBlock().asItem() instanceof HangingSignItem;
    }

    private boolean matches(SignBlockEntity sign, boolean front, boolean hanging) {
        SignText text = front ? sign.getFrontText() : sign.getBackText();
        String[] lines = linesFor(hanging, front);
        for (int i = 0; i < 4; i++) {
            if (!text.getMessage(i, false).getString().equals(lines[i])) return false;
        }
        return true;
    }

    private BlockHitResult hitFor(BlockPos pos) {
        Vec3d center = Vec3d.ofCenter(pos);
        Vec3d eye = mc.player.getEyePos();
        Direction side = Direction.getFacing(eye.x - center.x, eye.y - center.y, eye.z - center.z);
        return new BlockHitResult(center, side, pos, false);
    }

    private void interact(BlockPos pos) {
        interacting = true;
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitFor(pos));
        interacting = false;
    }

    private void send(BlockPos pos, boolean front, String[] lines) {
        mc.player.networkHandler.sendPacket(new UpdateSignC2SPacket(pos, front, lines[0], lines[1], lines[2], lines[3]));
    }

    @EventHandler
    private void onPlacePacket(PacketEvent.Send event) {
        if (mc.world == null || !autoPlace.get() || interacting) return;
        if (event.packet instanceof PlayerInteractBlockC2SPacket packet) {
            BlockHitResult hit = packet.getBlockHitResult();
            if (hit.getType() == BlockHitResult.Type.BLOCK) {
                lastPlacedBlock = hit.getBlockPos();
                lastPlacedSide = hit.getSide();
                placeTimer = placeDelay.get();
            }
        }
    }

    private void placeSign(BlockPos target, Direction hitSide) {
        FindItemResult slot = InvUtils.findInHotbar(stack ->
            stack.getItem() instanceof SignItem && !(stack.getItem() instanceof HangingSignItem) && !signTypes.get().contains(stack.getItem()));
        if (!slot.found()) {
            slot = InvUtils.findInHotbar(stack -> stack.getItem() instanceof HangingSignItem && !signTypes.get().contains(stack.getItem()));
        }
        if (!slot.found()) {
            info("No sign in hotbar!");
            return;
        }

        boolean hangingItem = mc.player.getInventory().getStack(slot.slot()).getItem() instanceof HangingSignItem;
        BlockPos on = hangingItem ? target : target.up();
        Direction side = hangingItem ? (hitSide == Direction.UP ? Direction.DOWN : hitSide) : Direction.UP;
        Vec3d hitVec = hangingItem
            ? Vec3d.ofCenter(on).add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5)
            : Vec3d.ofCenter(on).add(0, 1, 0);

        InvUtils.swap(slot.slot(), true);
        interacting = true;
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(hitVec, side, on, false));
        interacting = false;
        InvUtils.swapBack();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        if (rearInteractPos != null && --rearInteractTicks <= 0) {
            BlockPos rear = rearInteractPos;
            rearInteractPos = null;
            activeTicks = 0;
            interact(rear);
        }

        if (autoPlace.get()) {
            placeTimer--;
            if (placeTimer <= 0 && lastPlacedBlock != null) {
                placeSign(lastPlacedBlock, lastPlacedSide);
                lastPlacedBlock = null;
                placeTimer = placeDelay.get();
            }
        }

        if (!signAura.get()) return;

        if (activePos != null) {
            if (++activeTicks < OPEN_TIMEOUT_TICKS) return;
            fail(activePos);
            activePos = null;
        }
        if (--timer > 0) return;

        finished.removeIf(known -> !(mc.world.getBlockEntity(known) instanceof SignBlockEntity));
        refreshExpected();

        BlockPos best = null;
        double bestDistance = signAuraRange.get();
        Vec3d eye = mc.player.getEyePos();
        for (BlockEntity entity : Utils.blockEntities()) {
            if (!(entity instanceof SignBlockEntity sign)) continue;
            BlockPos pos = sign.getPos();
            if (finished.contains(pos) || sign.isWaxed()) continue;
            double distance = eye.distanceTo(Vec3d.ofCenter(pos));
            if (distance >= bestDistance) continue;
            if (!needsEdit(sign)) {
                finished.add(pos);
                continue;
            }
            best = pos;
            bestDistance = distance;
        }
        if (best == null) return;

        BlockPos pos = best;
        activePos = pos;
        activeTicks = 0;
        timer = signAuraDelay.get();
        Runnable click = () -> interact(pos);
        if (signAuraRotate.get()) Rotations.rotate(Rotations.getYaw(pos), Rotations.getPitch(pos), click);
        else click.run();
    }

    private boolean needsEdit(SignBlockEntity sign) {
        BlockPos pos = sign.getPos();
        if (rearPending.contains(pos)) return true;
        boolean hanging = isHanging(pos);
        if (!matches(sign, true, hanging)) return true;
        return bothSides.get() && !matches(sign, false, hanging);
    }

    private void fail(BlockPos pos) {
        int count = attempts.merge(pos, 1, Integer::sum);
        if (count >= MAX_ATTEMPTS) {
            finished.add(pos);
            rearPending.remove(pos);
            attempts.remove(pos);
        }
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (!(event.screen instanceof SignEditScreen) && !(event.screen instanceof HangingSignEditScreen)) return;
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        SignBlockEntity sign = ((AbstractSignEditScreenAccessor) event.screen).meteor$getSign();
        BlockPos pos = sign.getPos();
        boolean hanging = isHanging(pos);
        refreshExpected();

        boolean continuing = rearPending.remove(pos);
        boolean writeFront = !continuing && !matches(sign, true, hanging);
        boolean writeBack = continuing || (bothSides.get() && !matches(sign, false, hanging));

        event.cancel();
        activeTicks = 0;

        if (writeFront) {
            send(pos, true, linesFor(hanging, true));
            if (writeBack) {
                rearPending.add(pos);
                // Re-open the sign a couple of ticks later so the server has processed the front edit first.
                rearInteractPos = pos;
                rearInteractTicks = 3;
                return;
            }
        } else if (writeBack) {
            send(pos, false, linesFor(hanging, false));
        }

        finish(pos, writeFront || writeBack);
    }

    private void finish(BlockPos pos, boolean wrote) {
        finished.add(pos);
        attempts.remove(pos);
        if (pos.equals(activePos)) activePos = null;
        if (!wrote) return;
        if (autoDye.get()) useOnSign(stack -> dyeColors.get().contains(stack.getItem()), pos);
        if (autoGlow.get()) useOnSign(stack -> stack.isOf(Items.GLOW_INK_SAC), pos);
    }

    private void useOnSign(Predicate<ItemStack> wanted, BlockPos pos) {
        FindItemResult item = InvUtils.find(wanted);
        if (!item.found()) return;
        BlockHitResult hit = hitFor(pos);
        interacting = true;
        if (item.isOffhand()) {
            mc.interactionManager.interactBlock(mc.player, Hand.OFF_HAND, hit);
        } else if (item.isHotbar()) {
            InvUtils.swap(item.slot(), true);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            InvUtils.swapBack();
        } else {
            int selected = mc.player.getInventory().getSelectedSlot();
            InvUtils.move().from(item.slot()).to(selected);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            InvUtils.move().from(selected).to(item.slot());
        }
        interacting = false;
    }
}
