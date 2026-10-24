package gamerguy11.sixtoolsaddon.modules;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import gamerguy11.sixtoolsaddon.mixin.accessor.FishingBobberAccessor;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.PlaySoundFromEntityS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public class SixToolsAutoFish extends Module {
    private static final String DISPLAY_TITLE = "6Tools AutoFish";
    private static final Path FISH_DIR = FabricLoader.getInstance().getConfigDir().resolve("sixtoolsaddon").resolve("FishLogs");
    private static final Path FISH_FILE = FISH_DIR.resolve("MyFish.html");
    private static final Pattern CAUGHT_PATTERN = Pattern.compile("You caught an? (.+?): ([0-9]+(?:[.,][0-9]+)?) cm", Pattern.CASE_INSENSITIVE);
    private static final String NO_FISH_MESSAGE = "no more fish in this area";
    private static final String RECAST_MESSAGE = "move or look around before casting again";
    private static final int MOVE_TIMEOUT_TICKS = 100;
    private static final double WATER_LOOK_AHEAD = 1.6;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgMovement = settings.createGroup("Movement");
    private final SettingGroup sgDetection = settings.createGroup("Detection");
    private final SettingGroup sgFish = settings.createGroup("My Fish");

    private final Setting<Boolean> saveFish = sgFish.add(new BoolSetting.Builder()
        .name("save-fish")
        .description("Saves big catches to an HTML file. Use the My fish button to open it.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> minFishSize = sgFish.add(new DoubleSetting.Builder()
        .name("min-fish-size")
        .description("Only fish at least this long (cm) are saved.")
        .defaultValue(100.0)
        .min(0)
        .sliderMax(300)
        .visible(saveFish::get)
        .build()
    );

    private final Setting<Boolean> detectFlag = sgDetection.add(new BoolSetting.Builder()
        .name("detect-flag")
        .description("Bite signal: the bobber's own fish-caught flag.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectSound = sgDetection.add(new BoolSetting.Builder()
        .name("detect-sound")
        .description("Bite signal: the bobber splash sound played near your bobber.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> detectDip = sgDetection.add(new BoolSetting.Builder()
        .name("detect-dip")
        .description("Bite signal: the bobber suddenly moves down.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> dipThreshold = sgDetection.add(new DoubleSetting.Builder()
        .name("dip-threshold")
        .description("How far (blocks) the bobber must drop in one tick to count as a bite.")
        .defaultValue(0.12)
        .min(0.03)
        .sliderMax(0.5)
        .visible(detectDip::get)
        .build()
    );

    private final Setting<Integer> retryInterval = sgDetection.add(new IntSetting.Builder()
        .name("retry-interval")
        .description("Ticks between reel-in retries while the bobber is still out after a bite.")
        .defaultValue(20)
        .min(5)
        .sliderMax(60)
        .build()
    );

    private final Setting<Integer> maxRetries = sgDetection.add(new IntSetting.Builder()
        .name("max-retries")
        .description("How many times to retry reeling in the same bobber.")
        .defaultValue(5)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<Integer> recastTimeout = sgDetection.add(new IntSetting.Builder()
        .name("recast-timeout")
        .description("Ticks a bobber may stay out without a detected bite before it is reeled in and cast again. 0 disables.")
        .defaultValue(400)
        .min(0)
        .sliderMax(1200)
        .build()
    );

    private final Setting<Boolean> debug = sgDetection.add(new BoolSetting.Builder()
        .name("debug")
        .description("Prints in chat which bite signal fired.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-switch")
        .description("Automatically switch to a fishing rod.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> antiBreak = sgGeneral.add(new BoolSetting.Builder()
        .name("anti-break")
        .description("Avoid using rods that would break if they were cast.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoCast = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-cast")
        .description("Automatically cast the fishing rod.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> waterOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("water-only")
        .description("Only casts when you are aiming at water.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> waterRange = sgGeneral.add(new IntSetting.Builder()
        .name("water-range")
        .description("How far ahead of you the water has to be to cast.")
        .defaultValue(10)
        .min(2)
        .sliderMax(30)
        .visible(waterOnly::get)
        .build()
    );

    private final Setting<Boolean> customCastPitch = sgGeneral.add(new BoolSetting.Builder()
        .name("custom-cast-pitch")
        .description("Sets your look angle before each cast to control how far the bobber lands.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> castPitch = sgGeneral.add(new DoubleSetting.Builder()
        .name("cast-pitch")
        .description("Look angle used for casting. 0 is level (farthest cast), 90 is straight down (bobber drops at your feet).")
        .defaultValue(0.0)
        .min(-90)
        .max(90)
        .sliderMin(-30)
        .sliderMax(90)
        .visible(customCastPitch::get)
        .build()
    );

    private final Setting<Boolean> avoidBoat = sgGeneral.add(new BoolSetting.Builder()
        .name("avoid-boat")
        .description("Does not cast when the bobber would land in or right next to the boat, or in the path the boat is about to drive.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> moveAfterCatch = sgMovement.add(new BoolSetting.Builder()
        .name("move-after-catch")
        .description("Moves the boat after every catch.")
        .defaultValue(true)
        .build()
    );

    private final Setting<MoveMode> moveMode = sgMovement.add(new EnumSetting.Builder<MoveMode>()
        .name("move-mode")
        .description("One block: drive forward the way the boat faces. Look direction: turn the boat to where you look, then drive.")
        .defaultValue(MoveMode.ONE_BLOCK)
        .visible(moveAfterCatch::get)
        .build()
    );

    private final Setting<Integer> moveDistance = sgMovement.add(new IntSetting.Builder()
        .name("move-distance")
        .description("Blocks to move after a catch.")
        .defaultValue(1)
        .min(1)
        .sliderMax(10)
        .visible(moveAfterCatch::get)
        .build()
    );

    private final Setting<Boolean> moveOnNoFish = sgMovement.add(new BoolSetting.Builder()
        .name("move-on-no-fish")
        .description("Moves the boat when the chat says there are no more fish in this area.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> noFishDistance = sgMovement.add(new IntSetting.Builder()
        .name("no-fish-distance")
        .description("Blocks to move when there are no more fish in the area.")
        .defaultValue(3)
        .min(3)
        .sliderMax(10)
        .visible(moveOnNoFish::get)
        .build()
    );

    private final Setting<Boolean> moveOnRecastBlock = sgMovement.add(new BoolSetting.Builder()
        .name("move-on-recast-block")
        .description("Moves one block towards where you look when the server says to move or look around before casting again.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> stayInWater = sgMovement.add(new BoolSetting.Builder()
        .name("stay-in-water")
        .description("Stops moving before the boat would leave the water.")
        .defaultValue(true)
        .build()
    );

    private boolean caught;
    private int reeledHookId = -1;
    private int pendingBlocks;
    private boolean pendingTurn;
    private boolean moving;
    private boolean turnWhileMoving;
    private double moveDistanceTarget;
    private float moveYaw;
    private int moveTicks;
    private Vec3d moveStart = Vec3d.ZERO;
    private boolean warnedNoBoat;

    private static final String SPLASH_PATH = "entity.fishing_bobber.splash";
    private static final double SPLASH_RADIUS_SQ = 4.0 * 4.0;
    private static final int SPLASH_WINDOW_TICKS = 10;
    private static final int DIP_MIN_AGE_TICKS = 20;
    private int tickCounter;
    private static final int MESSAGE_COOLDOWN_TICKS = 60;
    private static final int CAST_COOLDOWN_TICKS = 20;
    private int lastMessageMoveTick = -MESSAGE_COOLDOWN_TICKS;
    private int lastBoatWarnTick = -100;
    private int lastCastTick = -CAST_COOLDOWN_TICKS;
    private volatile int splashTick = Integer.MIN_VALUE;
    private volatile int splashEntityId = -1;
    private volatile double splashX, splashY, splashZ;
    private int biteHookId = -1;
    private int hookAge;
    private double lastHookY = Double.NaN;
    private int lastReelTick;
    private int retries;

    public SixToolsAutoFish() {
        super(SixToolsAddon.CATEGORY, "6tools-auto-fish", "Auto Fish for the 6b6t lobby. Sit in a boat.");
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
        caught = false;
        reeledHookId = -1;
        pendingBlocks = 0;
        moving = false;
        warnedNoBoat = false;
        resetBite();
        splashTick = Integer.MIN_VALUE;
        lastCastTick = tickCounter;
        lastMessageMoveTick = -MESSAGE_COOLDOWN_TICKS;
    }

    private void resetBite() {
        biteHookId = -1;
        hookAge = 0;
        lastHookY = Double.NaN;
        retries = 0;
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (!detectSound.get()) return;
        if (event.packet instanceof PlaySoundFromEntityS2CPacket ep) {
            if (!SPLASH_PATH.equals(ep.getSound().value().id().getPath())) return;
            splashEntityId = ep.getEntityId();
            splashTick = tickCounter;
            return;
        }
        if (!(event.packet instanceof PlaySoundS2CPacket packet)) return;
        if (!SPLASH_PATH.equals(packet.getSound().value().id().getPath())) return;
        splashX = packet.getX();
        splashY = packet.getY();
        splashZ = packet.getZ();
        splashEntityId = -1;
        splashTick = tickCounter;
    }

    @Override
    public void onDeactivate() {
        stopMove();
        pendingBlocks = 0;
    }

    @EventHandler
    private void onMessage(ReceiveMessageEvent event) {
        logFish(event.getMessage().getString());
        if (moving || pendingBlocks > 0) return;
        String message = event.getMessage().getString().toLowerCase();
        boolean recast = message.contains(RECAST_MESSAGE);
        if (!recast && !message.contains(NO_FISH_MESSAGE)) return;
        if (tickCounter - lastMessageMoveTick < MESSAGE_COOLDOWN_TICKS) return;
        lastMessageMoveTick = tickCounter;
        if (moveOnRecastBlock.get() && message.contains(RECAST_MESSAGE)) {
            queueMove(1, true);
            return;
        }
        if (!moveOnNoFish.get() || !message.contains(NO_FISH_MESSAGE)) return;
        queueMove(noFishDistance.get(), moveMode.get() == MoveMode.LOOK_DIRECTION);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        tickCounter++;
        tickMove();
        if (mc.player.fishHook == null) {
            reeledHookId = -1;
            resetBite();
        }

        if (caught && mc.player.fishHook == null) {
            caught = false;
            if (moveAfterCatch.get() && pendingBlocks == 0 && !moving) {
                queueMove(moveDistance.get(), moveMode.get() == MoveMode.LOOK_DIRECTION);
            }
        }

        if (pendingBlocks > 0 && !moving) {
            startMove(pendingBlocks, pendingTurn);
            pendingBlocks = 0;
        }

        if (moving || pendingBlocks > 0) return;

        int bestRodSlot = findBestRod();

        if (autoSwitch.get() && bestRodSlot != -1 && mc.player.getInventory().getSelectedSlot() != bestRodSlot) {
            InvUtils.swap(bestRodSlot, false);
        }

        if (!(mc.player.getMainHandStack().getItem() instanceof FishingRodItem)) return;

        tryCast();
        tryCatch();
    }

    private void logFish(String text) {
        if (!saveFish.get()) return;
        Matcher m = CAUGHT_PATTERN.matcher(text);
        if (!m.find()) return;
        double size;
        try {
            size = Double.parseDouble(m.group(2).replace(',', '.'));
        } catch (NumberFormatException e) {
            return;
        }
        if (size < minFishSize.get()) return;
        String name = m.group(1).trim();
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        try {
            ensureFishFile();
            String row = String.format("<div class=\"fish\"><span class=\"name\">%s</span> <span class=\"size\">%.1f cm</span> <span class=\"time\">%s</span></div>\n",
                escapeHtml(name), size, time);
            Files.writeString(FISH_FILE, row, StandardOpenOption.APPEND);
            if (debug.get()) info("Saved %s (%.1f cm).", name, size);
        } catch (IOException e) {
            error("Failed to save fish: %s", e.getMessage());
        }
    }

    private static void ensureFishFile() throws IOException {
        if (Files.exists(FISH_FILE)) return;
        Files.createDirectories(FISH_DIR);
        String head = "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"UTF-8\">\n<title>My Fish</title>\n<style>\n"
            + "body { background-color: #36393f; color: #dcddde; font-family: sans-serif; padding: 10px; max-width: 700px; margin: auto; }\n"
            + ".fish { background: #2f3136; border-radius: 6px; padding: 10px 14px; margin-bottom: 8px; display: flex; gap: 14px; align-items: baseline; }\n"
            + ".name { font-weight: bold; color: #fff; flex: 1; }\n"
            + ".size { color: #57f287; font-weight: bold; }\n"
            + ".time { font-size: 0.75rem; color: #72767d; }\n"
            + "</style>\n</head>\n<body>\n<h2 style=\"color:#fff;text-align:center;margin-bottom:20px;\">SixToolsAddon My Fish</h2>\n";
        Files.writeString(FISH_FILE, head, StandardOpenOption.CREATE);
    }

    private static String escapeHtml(String t) {
        return t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WButton button = theme.button("My fish");
        button.action = () -> {
            try {
                ensureFishFile();
                Util.getOperatingSystem().open(FISH_FILE);
            } catch (IOException e) {
                error("Failed to open fish file: %s", e.getMessage());
            }
        };
        return button;
    }

    private void queueMove(int blocks, boolean turn) {
        pendingBlocks = blocks;
        pendingTurn = turn;
    }

    private void startMove(int blocks, boolean turn) {
        Entity vehicle = mc.player.getVehicle();
        if (vehicle == null) {
            if (!warnedNoBoat) {
                warnedNoBoat = true;
                warning("Sit in a boat so the module can move.");
            }
            return;
        }
        warnedNoBoat = false;
        moving = true;
        turnWhileMoving = turn;
        moveDistanceTarget = blocks;
        moveYaw = turn ? mc.player.getYaw() : vehicle.getYaw();
        moveStart = vehicle.getEntityPos();
        moveTicks = 0;
    }

    private void tickMove() {
        if (!moving) return;

        Entity vehicle = mc.player.getVehicle();
        if (vehicle == null) {
            stopMove();
            return;
        }

        Vec3d now = vehicle.getEntityPos();
        double travelled = Math.hypot(now.x - moveStart.x, now.z - moveStart.z);
        if (travelled >= moveDistanceTarget || ++moveTicks > MOVE_TIMEOUT_TICKS
            || (stayInWater.get() && !waterAhead(now))) {
            stopMove();
            return;
        }

        if (turnWhileMoving) vehicle.setYaw(moveYaw);
        mc.options.forwardKey.setPressed(true);
    }

    private void stopMove() {
        if (!moving) return;
        moving = false;
        if (mc.options != null) mc.options.forwardKey.setPressed(false);
    }

    private boolean waterAhead(Vec3d position) {
        double rad = Math.toRadians(moveYaw);
        double x = position.x - Math.sin(rad) * WATER_LOOK_AHEAD;
        double z = position.z + Math.cos(rad) * WATER_LOOK_AHEAD;
        return isWater(BlockPos.ofFloored(x, position.y, z)) || isWater(BlockPos.ofFloored(x, position.y - 0.5, z));
    }

    private boolean isWater(BlockPos pos) {
        return mc.world.getFluidState(pos).isIn(FluidTags.WATER);
    }

    private boolean aimedAtWater() {
        Vec3d eye = mc.player.getEyePos();
        Vec3d end = eye.add(mc.player.getRotationVec(1.0f).multiply(waterRange.get()));
        BlockHitResult hit = mc.world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.SOURCE_ONLY, mc.player));
        return hit.getType() == HitResult.Type.BLOCK && isWater(hit.getBlockPos());
    }

    private void warnBoatBlocked(String reason) {
        if (tickCounter - lastBoatWarnTick < 100) return;
        lastBoatWarnTick = tickCounter;
        warning("Not casting: %s (or turn off avoid-boat).", reason);
    }

    private boolean landingNearBoat() {
        Entity boat = mc.player.getVehicle();
        if (boat == null) return false;

        Vec3d eye = mc.player.getEyePos();
        Vec3d end = eye.add(mc.player.getRotationVec(1.0f).multiply(waterRange.get()));
        BlockHitResult hit = mc.world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.SOURCE_ONLY, mc.player));

        if (hit.getType() != HitResult.Type.BLOCK) return false;

        Vec3d land = hit.getPos();
        Vec3d pos = boat.getEntityPos();
        double dx = land.x - pos.x;
        double dz = land.z - pos.z;

        if (Math.hypot(dx, dz) < 2.5) {
            warnBoatBlocked("the landing spot is too close to the boat. Aim further away");
            return true;
        }

        if (moveAfterCatch.get() || moveOnNoFish.get() || moveOnRecastBlock.get()) {
            float yaw = moveMode.get() == MoveMode.LOOK_DIRECTION ? mc.player.getYaw() : boat.getYaw();
            double rad = Math.toRadians(yaw);
            double fx = -Math.sin(rad);
            double fz = Math.cos(rad);
            double along = dx * fx + dz * fz;
            double side = Math.abs(dx * -fz + dz * fx);
            double reach = Math.max(moveAfterCatch.get() ? moveDistance.get() : 0, moveOnNoFish.get() ? noFishDistance.get() : 0) + 1.0;
            if (along > 0 && along < reach && side < 1.5) {
                warnBoatBlocked("the landing spot is in the boat's path. Aim to the side or further out");
                return true;
            }
        }
        return false;
    }

    private void tryCast() {
        if (mc.player.fishHook != null) return;
        if (!autoCast.get()) return;
        if (tickCounter - lastCastTick < CAST_COOLDOWN_TICKS) return;
        if (customCastPitch.get()) mc.player.setPitch(castPitch.get().floatValue());
        if (waterOnly.get() && !aimedAtWater()) return;
        if (avoidBoat.get() && landingNearBoat()) return;

        lastCastTick = tickCounter;
        useRod();
    }

    private void tryCatch() {
        FishingBobberEntity hook = mc.player.fishHook;
        if (hook == null) return;

        if (hook.getId() != biteHookId) {
            resetBite();
            biteHookId = hook.getId();
        }
        hookAge++;

        double y = hook.getY();
        double dy = Double.isNaN(lastHookY) ? 0 : y - lastHookY;
        lastHookY = y;

        if (hook.getHookedEntity() != null) return;

        String signal = null;
        if (detectFlag.get() && ((FishingBobberAccessor) hook).sixtools$hasCaughtFish()) {
            signal = "flag";
        } else if (detectSound.get() && tickCounter - splashTick <= SPLASH_WINDOW_TICKS
            && (splashEntityId == hook.getId()
            || (splashEntityId == -1 && hook.squaredDistanceTo(splashX, splashY, splashZ) <= SPLASH_RADIUS_SQ))) {
            signal = "sound";
        } else if (detectDip.get() && hookAge > DIP_MIN_AGE_TICKS && dy < -dipThreshold.get()
            && (hook.isTouchingWater() || isWater(hook.getBlockPos()))) {
            signal = "dip";
        }

        boolean firstReel = hook.getId() != reeledHookId;
        if (firstReel && signal == null && recastTimeout.get() > 0 && hookAge > recastTimeout.get()) {

            if (debug.get()) info("No bite signal for %d ticks, recasting.", hookAge);
            hookAge = 0;
            caught = true;
            lastReelTick = tickCounter;
            useRod();
            return;
        }
        if (firstReel) {
            if (signal == null) return;
            caught = true;
            reeledHookId = hook.getId();
            retries = 0;
            lastReelTick = tickCounter;
            if (debug.get()) info("Bite detected via %s, reeling in.", signal);
            useRod();
            return;
        }

        if (tickCounter - lastReelTick < retryInterval.get()) return;
        if (retries >= maxRetries.get()) {

            reeledHookId = -1;
            retries = 0;
            hookAge = 0;
            if (debug.get()) info("Reel retries exhausted, restarting detection.");
            return;
        }
        retries++;
        lastReelTick = tickCounter;
        if (debug.get()) info("Bobber still out, retry %d/%d.", retries, maxRetries.get());
        useRod();
    }

    private void useRod() {

        if (mc.interactionManager == null || mc.player == null) return;
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private int findBestRod() {
        int bestSlot = -1;
        int bestScore = -1;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!(stack.getItem() instanceof FishingRodItem)) continue;
            if (antiBreak.get() && stack.getDamage() == stack.getMaxDamage() - 1) continue;

            int score = 0;

            score += Utils.getEnchantmentLevel(stack, Enchantments.LUCK_OF_THE_SEA);
            score += Utils.getEnchantmentLevel(stack, Enchantments.LURE);
            score += Utils.getEnchantmentLevel(stack, Enchantments.MENDING);
            score += Utils.getEnchantmentLevel(stack, Enchantments.UNBREAKING);

            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }

            if (score == 10) break;
        }

        return bestSlot;
    }

    public enum MoveMode {
        ONE_BLOCK("One block"),
        LOOK_DIRECTION("Look direction");

        private final String title;

        MoveMode(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }
}
