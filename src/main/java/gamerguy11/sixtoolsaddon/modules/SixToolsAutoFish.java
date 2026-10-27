package gamerguy11.sixtoolsaddon.modules;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import gamerguy11.sixtoolsaddon.mixin.accessor.FishingBobberAccessor;
import gamerguy11.sixtoolsaddon.utils.FishStats;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
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
import meteordevelopment.meteorclient.settings.StringListSetting;
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
import net.minecraft.util.math.MathHelper;
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
    private static final int STALL_TICKS = 10;
    private static final double STALL_DISTANCE = 0.15;
    private static final int ADVICE_INTERVAL_TICKS = 1200;
    private static final int MAX_SPOT_RETRIES = 5;
    private static final double TARGET_TOLERANCE = 1.5;
    private static final float RELOCATE_NUDGE = 3.0f;
    private static final int LOOK_SETTLE_TICKS = 3;
    private static final float MAX_RELOCATE_NUDGE = 12.0f;
    private static final double MIN_CAST_RANGE = 3.0;
    private static final double MAX_CAST_RANGE = 14.0;
    private static final int CLEARANCE_BLOCKS = 8;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgMovement = settings.createGroup("Movement");
    private final SettingGroup sgDetection = settings.createGroup("Detection");
    private final SettingGroup sgFish = settings.createGroup("My Fish");
    private final SettingGroup sgNotify = settings.createGroup("Notifications");


    private final Setting<Boolean> silent = sgNotify.add(new BoolSetting.Builder()
        .name("silent")
        .description("Master switch: turns off every Auto Fish chat message below (and debug).")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> notifyNoRod = sgNotify.add(new BoolSetting.Builder()
        .name("notify-no-rod")
        .description("Tells you when there is no fishing rod in your hand or hotbar.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> notifyIdleReset = sgNotify.add(new BoolSetting.Builder()
        .name("notify-idle-reset")
        .description("Tells you when Auto Fish was idle for a while and reset itself.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> notifyCastBlocked = sgNotify.add(new BoolSetting.Builder()
        .name("notify-cast-blocked")
        .description("Tells you when it will not cast because the bobber would land off the water or too close to the boat.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> notifySpot = sgNotify.add(new BoolSetting.Builder()
        .name("notify-hook-move")
        .description("Tells you when the bobber could not be moved to open water.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> notifyErrors = sgNotify.add(new BoolSetting.Builder()
        .name("notify-errors")
        .description("Tells you when saving or opening the fish file fails.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> trackStats = sgFish.add(new BoolSetting.Builder()
        .name("track-stats")
        .description("Counts every catch by rarity and writes FishStats.html every 30 seconds. Use the Fish stats button to open it.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> statsInterval = sgFish.add(new IntSetting.Builder()
        .name("stats-update-interval")
        .description("How often (seconds) FishStats.html is rewritten and refreshed.")
        .defaultValue(30)
        .min(5)
        .sliderMin(5)
        .sliderMax(600)
        .visible(trackStats::get)
        .build()
    );

    private final Setting<Boolean> scanLaunchers = sgFish.add(new BoolSetting.Builder()
        .name("scan-launchers")
        .description("Import also looks for logs of other Minecraft instances (default folder, Prism, MultiMC, Modrinth, CurseForge).")
        .defaultValue(true)
        .visible(trackStats::get)
        .build()
    );

    private final Setting<java.util.List<String>> extraLogFolders = sgFish.add(new StringListSetting.Builder()
        .name("extra-log-folders")
        .description("Extra folders with Minecraft logs to import (the folder itself or its parent of a logs folder).")
        .defaultValue(new ArrayList<>())
        .visible(trackStats::get)
        .build()
    );

    private final Setting<Integer> serverTotal = sgFish.add(new IntSetting.Builder()
        .name("server-total")
        .description("Your total fish shown by the server (leaderboard). The stats page shows how many are missing from the logs. 0 = off.")
        .defaultValue(0)
        .min(0)
        .sliderMin(0)
        .sliderMax(20000)
        .visible(trackStats::get)
        .build()
    );

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
        .min(100)
        .sliderMin(100)
        .sliderMax(10000)
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
        .defaultValue(600)
        .min(0)
        .sliderMax(1200)
        .build()
    );

    private final Setting<Boolean> debug = sgNotify.add(new BoolSetting.Builder()
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
        .defaultValue(false)
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
        .description("Does not cast when the bobber would land closer than min-cast-distance to the boat.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> minCastDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("min-cast-distance")
        .description("Minimum blocks between the boat and where the bobber lands. Measured from the simulated landing spot, not from where you look.")
        .defaultValue(6.0)
        .min(2.0)
        .sliderMax(14.0)
        .visible(avoidBoat::get)
        .build()
    );

    private final Setting<Boolean> autoAim = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-aim")
        .description("When the cast would land too close to the boat or off the water, adjusts pitch (and turns a little if needed) to a valid spot instead of waiting. Stops the module from stalling when the aim drifts onto land.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> lookAround = sgMovement.add(new BoolSetting.Builder()
        .name("look-around")
        .description("Turns the camera a little, once, when the server says to move or look around before casting again. It swings back and forth so the aim does not drift. The boat does not move.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> lookAroundDegrees = sgMovement.add(new DoubleSetting.Builder()
        .name("look-around-degrees")
        .description("How far the camera turns each time.")
        .defaultValue(67.838)
        .min(2.0)
        .sliderMax(90.0)
        .build()
    );

    private final Setting<Boolean> lookAfterCatch = sgMovement.add(new BoolSetting.Builder()
        .name("look-after-catch")
        .description("Turns the camera a little after every catch. The boat stays where it is. This shifts where the bobber lands, so it is off by default.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> moveHookOnNoFish = sgMovement.add(new BoolSetting.Builder()
        .name("move-hook-on-no-fish")
        .description("When the chat says there are no more fish in this area, reels in and casts the bobber about hook-move-distance + 1 blocks away along X or Z where there is water, and keeps fishing there. When the message comes again it goes back to the spot it left, then picks a new random side. The boat does not move.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> hookMoveDistance = sgMovement.add(new IntSetting.Builder()
        .name("hook-move-distance")
        .description("Minimum blocks between the old and the new bobber position.")
        .defaultValue(3)
        .min(3)
        .sliderMax(10)
        .visible(moveHookOnNoFish::get)
        .build()
    );

    private final Setting<Boolean> idleMove = sgMovement.add(new BoolSetting.Builder()
        .name("idle-move")
        .description("Drives the boat a short way when you have not caught anything for a long time. Until then it stays in place.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> idleMoveTime = sgMovement.add(new IntSetting.Builder()
        .name("idle-move-time")
        .description("Seconds without a catch before the boat moves.")
        .defaultValue(302)
        .min(30)
        .sliderMax(900)
        .visible(idleMove::get)
        .build()
    );

    private final Setting<Integer> idleMoveDistance = sgMovement.add(new IntSetting.Builder()
        .name("idle-move-distance")
        .description("Blocks the boat drives when it has not caught anything for a long time.")
        .defaultValue(3)
        .min(3)
        .sliderMax(10)
        .visible(idleMove::get)
        .build()
    );

    private final Setting<Integer> edgeMargin = sgMovement.add(new IntSetting.Builder()
        .name("edge-margin")
        .description("The boat moves forward, back, left or right at random, but only to a spot with at least this many blocks of open water on every side. Keeps it in the middle of the pond.")
        .defaultValue(4)
        .min(2)
        .max(8)
        .visible(idleMove::get)
        .build()
    );

    private final Setting<Boolean> stayInWater = sgMovement.add(new BoolSetting.Builder()
        .name("stay-in-water")
        .description("Stops moving before the boat would leave the water.")
        .defaultValue(true)
        .visible(idleMove::get)
        .build()
    );

    private final Setting<Boolean> boatAdvice = sgNotify.add(new BoolSetting.Builder()
        .name("boat-advice")
        .description("Tells you in chat to sit in a boat when you fish without one. Turn it off to stop the reminder.")
        .defaultValue(true)
        .build()
    );

    private boolean caught;
    private int reeledHookId = -1;
    private int pendingBlocks;
    private boolean moving;
    private double moveDistanceTarget;
    private float moveYaw;
    private int moveTicks;
    private Vec3d moveStart = Vec3d.ZERO;
    private int progressTicks;
    private Vec3d progressPos = Vec3d.ZERO;
    private float restoreYaw = Float.NaN;
    private int lookSign = 1;
    private int lastLookTick = -60;
    private int lastCatchTick;
    private int lastAdviceTick = -1200;
    private int lastSpotWarnTick = -200;
    private Vec3d lastHookPos;
    private Vec3d spotFrom;
    private Vec3d spotTarget;
    private Vec3d returnSpot;
    private float yawBias;
    private float lastCastYaw = Float.NaN;
    private float lastCastPitch;
    private float settledYaw = Float.NaN;
    private float settledPitch;
    private int aimChangedTick;
    private int castNudgeSign = 1;
    private float relocateNudge = RELOCATE_NUDGE;
    private double rangeBias;
    private boolean needNewSpot;
    private boolean reelForSpot;
    private boolean verifySpot;
    private int spotRetries;

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
    private int hookedTicks;
    private boolean hookTouchedWater;
    private static final int LAND_CHECK_TICKS = 25;
    private int aimSearchBlockedUntil;
    private int lastNoRodWarnTick = -200;
    private int lastStallResetTick;

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
           gamerguy11.sixtoolsaddon.utils.Swallowed.log("SixToolsAutoFish:401", ignored);
        }
    }

    @Override
    public void onActivate() {
        caught = false;
        reeledHookId = -1;
        pendingBlocks = 0;
        moving = false;
        needNewSpot = false;
        reelForSpot = false;
        verifySpot = false;
        spotRetries = 0;
        returnSpot = null;
        lastCastYaw = Float.NaN;
        lastHookPos = null;
        resetBite();
        splashTick = Integer.MIN_VALUE;
        lastCastTick = tickCounter;
        lastMessageMoveTick = -MESSAGE_COOLDOWN_TICKS;
        lastLookTick = -MESSAGE_COOLDOWN_TICKS;
        lastCatchTick = tickCounter;
        lastAdviceTick = -ADVICE_INTERVAL_TICKS;
        aimSearchBlockedUntil = 0;
        lastStallResetTick = tickCounter;
        if (trackStats.get()) FishStats.writeHtml(statsInterval.get(), serverTotal.get());
    }

    private void resetBite() {
        biteHookId = -1;
        hookAge = 0;
        lastHookY = Double.NaN;
        retries = 0;
        hookedTicks = 0;
        hookTouchedWater = false;
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
        needNewSpot = false;
        reelForSpot = false;
        verifySpot = false;
    }

    @EventHandler
    private void onMessage(ReceiveMessageEvent event) {
        String text = event.getMessage().getString();
        if (trackStats.get()) {
            if (mc.getSession() != null) FishStats.recordBroadcast(text, mc.getSession().getUsername());
        }
        logFish(text, event.getMessage());
        if (CAUGHT_PATTERN.matcher(text).find()) lastCatchTick = tickCounter;
        String message = text.toLowerCase();

        if (message.contains(RECAST_MESSAGE) && tickCounter - lastLookTick >= MESSAGE_COOLDOWN_TICKS) {
            lastLookTick = tickCounter;
            if (needNewSpot) relocateNudge = Math.min(MAX_RELOCATE_NUDGE, relocateNudge + 2.0f);
            else if (lookAround.get()) lookAround();
        }

        if (message.contains(NO_FISH_MESSAGE) && moveHookOnNoFish.get() && lastHookPos != null && !needNewSpot
            && tickCounter - lastMessageMoveTick >= MESSAGE_COOLDOWN_TICKS && mc.player != null) {
            lastMessageMoveTick = tickCounter;
            Vec3d from = lastHookPos;
            Vec3d target;
            if (returnSpot != null && usableSpot(returnSpot)) {
                target = returnSpot;
                returnSpot = null;
            } else {
                target = chooseNeighbor(from);
                returnSpot = target == null ? null : from;
            }
            if (target == null) {
                warnSpot();
                return;
            }
            spotFrom = from;
            spotTarget = target;
            needNewSpot = true;
            verifySpot = false;
            spotRetries = 0;
            yawBias = 0;
            rangeBias = 0;
            relocateNudge = RELOCATE_NUDGE;
            reelForSpot = mc.player.fishHook != null;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        tickCounter++;
        if (trackStats.get() && tickCounter % (statsInterval.get() * 20) == 0) FishStats.writeHtml(statsInterval.get(), serverTotal.get());
        tickMove();
        adviseBoat();

        FishingBobberEntity current = mc.player.fishHook;
        if (current == null) {
            reeledHookId = -1;
            resetBite();
        } else if (current.isTouchingWater()) {
            lastHookPos = current.getEntityPos();
        }

        if (caught && current == null) {
            caught = false;
            if (lookAfterCatch.get()) lookAround();
        }

        if (idleMove.get() && !moving && pendingBlocks == 0 && tickCounter - lastCatchTick >= idleMoveTime.get() * 20) {
            lastCatchTick = tickCounter;
            pendingBlocks = idleMoveDistance.get();
        }

        if (pendingBlocks > 0 && !moving) {
            int blocks = pendingBlocks;
            pendingBlocks = 0;
            startMove(blocks);
        }

        if (moving || pendingBlocks > 0) return;

        int bestRodSlot = findBestRod();

        if (autoSwitch.get() && bestRodSlot != -1 && mc.player.getInventory().getSelectedSlot() != bestRodSlot) {
            InvUtils.swap(bestRodSlot, false);
        }

        if (!(mc.player.getMainHandStack().getItem() instanceof FishingRodItem)) {
            if (tickCounter - lastNoRodWarnTick >= 200) {
                lastNoRodWarnTick = tickCounter;
                if (!silent.get() && notifyNoRod.get()) {
                    if (bestRodSlot == -1) warning("No usable fishing rod in your hotbar (anti-break skips rods about to break). Auto fish is waiting.");
                    else warning("Not holding a fishing rod and could not switch to it (auto-switch is off or the server blocked the swap). Auto fish is waiting.");
                }
            }
            return;
        }

        if (autoCast.get() && mc.player.fishHook == null && tickCounter - lastCastTick > 300
            && tickCounter - lastStallResetTick > 300) {
            lastStallResetTick = tickCounter;
            needNewSpot = false;
            reelForSpot = false;
            verifySpot = false;
            aimSearchBlockedUntil = 0;
            settledYaw = Float.NaN;
            if (!silent.get() && notifyIdleReset.get()) warning("Auto fish was idle for a while, resetting and casting again.");
        }

        if (reelForSpot) {
            reelForSpot = false;
            if (mc.player.fishHook != null) {
                lastCastTick = tickCounter;
                useRod();
                return;
            }
        }

        checkSpot();
        tryCast();
        tryCatch();
    }

    private void logFish(String text, net.minecraft.text.Text message) {
        if (!saveFish.get() && !trackStats.get()) return;
        Matcher m = CAUGHT_PATTERN.matcher(text);
        if (!m.find()) return;
        double size;
        try {
            size = Double.parseDouble(m.group(2).replace(',', '.'));
        } catch (NumberFormatException e) {
            return;
        }
        String name = m.group(1).trim();
        if (trackStats.get()) FishStats.record(name, size, System.currentTimeMillis() / 1000, FishStats.colorRarity(message, name));
        if (!saveFish.get() || size < minFishSize.get()) return;
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        try {
            ensureFishFile();
            String row = String.format("<div class=\"fish\"><span class=\"name\">%s</span> <span class=\"size\">%.1f cm</span> <span class=\"time\">%s</span></div>\n",
                escapeHtml(name), size, time);
            Files.writeString(FISH_FILE, row, StandardOpenOption.APPEND);
            if (debug.get() && !silent.get()) info("Saved %s (%.1f cm).", name, size);
        } catch (IOException e) {
            if (!silent.get() && notifyErrors.get()) error("Failed to save fish: %s", e.getMessage());
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
        WHorizontalList list = theme.horizontalList();

        WButton fish = list.add(theme.button("My fish")).widget();
        fish.action = () -> {
            try {
                ensureFishFile();
                Util.getOperatingSystem().open(FISH_FILE);
            } catch (IOException e) {
                if (!silent.get() && notifyErrors.get()) error("Failed to open fish file: %s", e.getMessage());
            }
        };

        WButton stats = list.add(theme.button("Fish stats")).widget();
        stats.action = () -> {
            if (FishStats.writeHtml(statsInterval.get(), serverTotal.get())) Util.getOperatingSystem().open(FishStats.HTML);
            else if (!silent.get() && notifyErrors.get()) error("Failed to write fish stats.");
        };

        WButton old = list.add(theme.button("Import old fish (may be inaccurate)")).widget();
        old.action = () -> {
            warning("Import reads old game logs and may not be 100%% accurate: dates come from log file names, catches in logs that were deleted or cut off are missing, and sessions over midnight can be off by a day.");
            if (!silent.get()) info("Scanning old game logs for fish...");
            Thread t = new Thread(() -> {
                int[] r = FishStats.importLogs(extraLogFolders.get(), scanLaunchers.get());
                FishStats.writeHtml(statsInterval.get(), serverTotal.get());
                mc.execute(() -> {
                    if (!silent.get()) info("Scanned %d folders and %d log files, found %d catch lines, added %d new catches.", r[3], r[0], r[1], r[2]);
                });
            }, "6tools-fish-import");
            t.setDaemon(true);
            t.start();
        };

        return list;
    }

    private void startMove(int blocks) {
        Entity vehicle = mc.player.getVehicle();
        if (vehicle == null) return;
        float yaw = pickPondYaw(vehicle, blocks);
        if (Float.isNaN(yaw)) return;
        if (mc.player.fishHook != null) useRod();
        restoreYaw = vehicle.getYaw();
        moveYaw = yaw;
        moving = true;
        moveDistanceTarget = blocks;
        moveStart = vehicle.getEntityPos();
        moveTicks = 0;
        progressPos = moveStart;
        progressTicks = 0;
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
        if (travelled >= moveDistanceTarget || ++moveTicks > MOVE_TIMEOUT_TICKS) {
            stopMove();
            return;
        }

        boolean blocked = vehicle.horizontalCollision || (stayInWater.get() && !waterAhead(now));
        if (++progressTicks >= STALL_TICKS) {
            if (Math.hypot(now.x - progressPos.x, now.z - progressPos.z) < STALL_DISTANCE) blocked = true;
            progressPos = now;
            progressTicks = 0;
        }

        if (blocked) {
            stopMove();
            return;
        }

        vehicle.setYaw(moveYaw);
        mc.options.forwardKey.setPressed(true);
    }

    private int clearance(Vec3d position, float yaw) {
        double rad = Math.toRadians(yaw);
        double dx = -Math.sin(rad);
        double dz = Math.cos(rad);
        for (int step = 1; step <= CLEARANCE_BLOCKS; step++) {
            double x = position.x + dx * step;
            double z = position.z + dz * step;
            BlockPos feet = BlockPos.ofFloored(x, position.y + 0.5, z);
            boolean water = isWater(BlockPos.ofFloored(x, position.y, z)) || isWater(BlockPos.ofFloored(x, position.y - 0.5, z));
            boolean solid = !mc.world.getBlockState(feet).getCollisionShape(mc.world, feet).isEmpty();
            if (!water || solid) return step - 1;
        }
        return CLEARANCE_BLOCKS;
    }

    private void lookAround() {
        if (mc.player == null) return;
        lookSign = -lookSign;
        mc.player.setYaw(mc.player.getYaw() + lookSign * lookAroundDegrees.get().floatValue());
    }

    private void stopMove() {
        if (!moving) return;
        moving = false;
        if (mc.options != null) mc.options.forwardKey.setPressed(false);
        Entity vehicle = mc.player != null ? mc.player.getVehicle() : null;
        if (vehicle != null && !Float.isNaN(restoreYaw)) vehicle.setYaw(restoreYaw);
        restoreYaw = Float.NaN;
    }

    private float pickPondYaw(Entity vehicle, int blocks) {
        Vec3d pos = vehicle.getEntityPos();
        float base = vehicle.getYaw();
        List<Float> offsets = new ArrayList<>(List.of(0f, 90f, 180f, -90f));
        Collections.shuffle(offsets);
        int needed = Math.min(blocks + 1, CLEARANCE_BLOCKS);
        for (float offset : offsets) {
            float yaw = MathHelper.wrapDegrees(base + offset);
            if (clearance(pos, yaw) < needed) continue;
            double rad = Math.toRadians(yaw);
            Vec3d destination = pos.add(-Math.sin(rad) * blocks, 0, Math.cos(rad) * blocks);
            if (edgeClearance(destination) >= edgeMargin.get()) return yaw;
        }
        return Float.NaN;
    }

    private int edgeClearance(Vec3d position) {
        int min = CLEARANCE_BLOCKS;
        for (int yaw = 0; yaw < 360; yaw += 90) {
            min = Math.min(min, clearance(position, yaw));
        }
        return min;
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
        if (!silent.get() && notifyCastBlocked.get()) warning("Not casting: %s (or turn off avoid-boat).", reason);
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

        return false;
    }

    private double surfaceY() {
        BlockPos base = mc.player.hasVehicle() ? mc.player.getVehicle().getBlockPos() : mc.player.getBlockPos();
        for (int i = -1; i <= 3; i++) {
            BlockPos p = base.down(i);
            var fluid = mc.world.getFluidState(p);
            if (fluid.isIn(FluidTags.WATER)) return p.getY() + fluid.getHeight(mc.world, p);
        }
        return Double.NaN;
    }

    private boolean castValid(float yaw, float pitch, double surface) {
        Vec3d land = landingPoint(yaw, pitch, surface);
        if (land == null) return false;
        double range = Math.hypot(land.x - mc.player.getX(), land.z - mc.player.getZ());
        if (range > Math.max(MAX_CAST_RANGE, waterRange.get())) return false;
        if (avoidBoat.get() && mc.player.hasVehicle() && range < minCastDistance.get()) return false;
        return !waterOnly.get() || isWater(BlockPos.ofFloored(land.x, surface - 0.1, land.z));
    }

    private boolean prepareCast() {
        double surface = surfaceY();
        if (Double.isNaN(surface)) {

            if (waterOnly.get() && !aimedAtWater()) return false;
            return !(avoidBoat.get() && landingNearBoat());
        }
        if (castValid(mc.player.getYaw(), mc.player.getPitch(), surface)) return true;
        if (!autoAim.get()) {
            warnBoatBlocked("the bobber would land off the water or closer than min-cast-distance to the boat. Aim further out");
            return false;
        }
        if (tickCounter < aimSearchBlockedUntil) return false;

        float yaw0 = mc.player.getYaw();
        float pitch0 = mc.player.getPitch();
        for (int i = 0; i < 25; i++) {
            float offset = i == 0 ? 0f : ((i + 1) / 2) * 15f * (i % 2 == 1 ? 1 : -1);
            float yaw = MathHelper.wrapDegrees(yaw0 + offset);
            Float best = null;
            float bestDelta = Float.MAX_VALUE;
            for (float pitch = -35f; pitch <= 70f; pitch += 1f) {
                if (!castValid(yaw, pitch, surface)) continue;
                float delta = Math.abs(pitch - pitch0);
                if (delta < bestDelta) {
                    bestDelta = delta;
                    best = pitch;
                }
            }
            if (best != null) {
                mc.player.setYaw(yaw);
                mc.player.setPitch(best);
                if (debug.get() && !silent.get()) info("Auto-aim: yaw %+.0f, pitch %.0f so the bobber lands %.0f+ blocks out in water.", offset, best, minCastDistance.get());
                return true;
            }
        }
        aimSearchBlockedUntil = tickCounter + 40;
        warnBoatBlocked("no water found far enough from the boat in any direction. Move the boat or lower min-cast-distance");
        return false;
    }

    private void tryCast() {
        if (mc.player.fishHook != null) return;
        if (!autoCast.get()) return;
        if (tickCounter - lastCastTick < CAST_COOLDOWN_TICKS) return;

        boolean relocating = needNewSpot && aimAtTarget();
        if (needNewSpot && !relocating) {
            needNewSpot = false;
            verifySpot = false;
            returnSpot = null;
            warnSpot();
        }
        if (!relocating && customCastPitch.get()) mc.player.setPitch(castPitch.get().floatValue());
        ensureLookChanged(relocating);
        if (!relocating && !prepareCast()) return;

        float yawNow = mc.player.getYaw();
        float pitchNow = mc.player.getPitch();
        if (Float.isNaN(settledYaw) || Math.abs(MathHelper.wrapDegrees(yawNow - settledYaw)) > 0.01f || Math.abs(pitchNow - settledPitch) > 0.01f) {
            settledYaw = yawNow;
            settledPitch = pitchNow;
            aimChangedTick = tickCounter;
        }
        if (tickCounter - aimChangedTick < LOOK_SETTLE_TICKS) return;

        castNudgeSign = -castNudgeSign;
        lastCastTick = tickCounter;
        lastCastYaw = mc.player.getYaw();
        lastCastPitch = mc.player.getPitch();
        if (relocating) verifySpot = true;
        useRod();
    }

    private static float yawTo(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private Vec3d chooseNeighbor(Vec3d from) {
        double d = hookMoveDistance.get() + 1.0;
        List<Vec3d> options = new ArrayList<>(List.of(new Vec3d(d, 0, 0), new Vec3d(-d, 0, 0), new Vec3d(0, 0, d), new Vec3d(0, 0, -d)));
        Collections.shuffle(options);
        for (Vec3d offset : options) {
            Vec3d candidate = from.add(offset);
            if (usableSpot(candidate)) return candidate;
        }
        return null;
    }

    private boolean usableSpot(Vec3d spot) {
        if (!waterAround(spot)) return false;
        double dx = spot.x - mc.player.getX();
        double dz = spot.z - mc.player.getZ();
        double range = Math.hypot(dx, dz);
        if (range < MIN_CAST_RANGE || range > MAX_CAST_RANGE) return false;
        return solvePitch(yawTo(dx, dz), range, spot.y + 0.2) != null;
    }

    private boolean waterAround(Vec3d spot) {
        int x = MathHelper.floor(spot.x);
        int z = MathHelper.floor(spot.z);
        int y = MathHelper.floor(spot.y - 0.2);
        return isWater(new BlockPos(x, y, z)) && isWater(new BlockPos(x + 1, y, z)) && isWater(new BlockPos(x - 1, y, z))
            && isWater(new BlockPos(x, y, z + 1)) && isWater(new BlockPos(x, y, z - 1));
    }

    private void ensureLookChanged(boolean relocating) {
        if (Float.isNaN(lastCastYaw)) return;
        boolean same = Math.abs(MathHelper.wrapDegrees(mc.player.getYaw() - lastCastYaw)) < 0.5f
            && Math.abs(mc.player.getPitch() - lastCastPitch) < 0.5f;
        if (!same) return;
        float amount = relocating ? relocateNudge : lookAroundDegrees.get().floatValue();
        mc.player.setYaw(mc.player.getYaw() + castNudgeSign * amount);
    }

    private boolean aimAtTarget() {
        if (spotTarget == null) return false;
        double dx = spotTarget.x - mc.player.getX();
        double dz = spotTarget.z - mc.player.getZ();
        double range = Math.hypot(dx, dz);
        if (range < MIN_CAST_RANGE || range > MAX_CAST_RANGE) return false;
        float yaw = yawTo(dx, dz) + yawBias;
        Float pitch = solvePitch(yaw, range + rangeBias, spotTarget.y + 0.2);
        if (pitch == null) return false;
        mc.player.setYaw(yaw);
        mc.player.setPitch(pitch);
        return true;
    }

    private Float solvePitch(float yaw, double range, double surfaceY) {
        Float best = null;
        double bestError = 0.6;
        for (float pitch = -35f; pitch <= 70f; pitch += 0.25f) {
            double landed = landingRange(yaw, pitch, surfaceY);
            if (Double.isNaN(landed)) continue;
            double error = Math.abs(landed - range);
            if (error < bestError) {
                bestError = error;
                best = pitch;
            }
        }
        return best;
    }

    private double landingRange(float yaw, float pitch, double surfaceY) {
        Vec3d land = landingPoint(yaw, pitch, surfaceY);
        return land == null ? Double.NaN : Math.hypot(land.x - mc.player.getX(), land.z - mc.player.getZ());
    }

    private Vec3d landingPoint(float yaw, float pitch, double surfaceY) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double vy = MathHelper.clamp(-Math.tan(pitchRad), -5.0, 5.0);
        double speed = 0.6 / Math.sqrt(1.0 + vy * vy) + 0.5;
        Vec3d velocity = new Vec3d(-Math.sin(yawRad) * speed, vy * speed, Math.cos(yawRad) * speed);
        double startX = mc.player.getX();
        double startZ = mc.player.getZ();
        Vec3d pos = new Vec3d(startX - Math.sin(yawRad) * 0.3, mc.player.getEyeY(), startZ + Math.cos(yawRad) * 0.3);
        if (pos.y <= surfaceY) return null;
        for (int i = 0; i < 200; i++) {
            velocity = velocity.add(0.0, -0.03, 0.0);
            Vec3d next = pos.add(velocity);
            if (next.y <= surfaceY) {
                double t = (pos.y - surfaceY) / (pos.y - next.y);
                return new Vec3d(pos.x + (next.x - pos.x) * t, surfaceY, pos.z + (next.z - pos.z) * t);
            }
            pos = next;
            velocity = velocity.multiply(0.92);
        }
        return null;
    }

    private void checkSpot() {
        if (!verifySpot || spotFrom == null || spotTarget == null) return;
        FishingBobberEntity hook = mc.player.fishHook;
        if (hook == null || hook.getHookedEntity() != null) return;
        if (!hook.isTouchingWater()) {
            if (tickCounter - lastCastTick < 40) return;
            if (++spotRetries > MAX_SPOT_RETRIES) {
                needNewSpot = false;
                verifySpot = false;
                returnSpot = null;
                warnSpot();
                return;
            }
            rangeBias += spotRetries % 2 == 0 ? -1.5 : 1.0;
            if (debug.get() && !silent.get()) info("Bobber did not land in water, casting again.");
            lastCastTick = tickCounter;
            useRod();
            return;
        }
        if (tickCounter - lastCastTick < 10) return;

        verifySpot = false;
        Vec3d land = hook.getEntityPos();
        double fromDistance = Math.hypot(land.x - spotFrom.x, land.z - spotFrom.z);
        double targetDistance = Math.hypot(land.x - spotTarget.x, land.z - spotTarget.z);
        boolean farEnough = fromDistance >= hookMoveDistance.get() + 0.5;
        if (farEnough && targetDistance <= TARGET_TOLERANCE) {
            needNewSpot = false;
            relocateNudge = RELOCATE_NUDGE;
            if (debug.get() && !silent.get()) info("Bobber moved %.1f blocks, %.1f from the target.", fromDistance, targetDistance);
            return;
        }
        if (++spotRetries > MAX_SPOT_RETRIES) {
            needNewSpot = false;
            if (!farEnough) {
                returnSpot = null;
                warnSpot();
            }
            return;
        }

        double px = mc.player.getX();
        double pz = mc.player.getZ();
        yawBias += MathHelper.wrapDegrees(yawTo(spotTarget.x - px, spotTarget.z - pz) - yawTo(land.x - px, land.z - pz));
        rangeBias += Math.hypot(spotTarget.x - px, spotTarget.z - pz) - Math.hypot(land.x - px, land.z - pz);
        if (debug.get() && !silent.get()) info("Bobber landed %.1f blocks from the target, casting again.", targetDistance);
        lastCastTick = tickCounter;
        useRod();
    }

    private void warnSpot() {
        if (tickCounter - lastSpotWarnTick < 100) return;
        lastSpotWarnTick = tickCounter;
        if (!silent.get() && notifySpot.get()) warning("Could not move the bobber %d blocks to open water, casting normally.", hookMoveDistance.get());
    }

    private void adviseBoat() {
        if (mc.player.hasVehicle()) {
            lastAdviceTick = -ADVICE_INTERVAL_TICKS;
            return;
        }
        if (silent.get() || !boatAdvice.get() || tickCounter - lastAdviceTick < ADVICE_INTERVAL_TICKS) return;
        lastAdviceTick = tickCounter;
        warning("You are not in a boat. Sit in one to fish, it keeps you in place on the water. Turn this reminder off with boat-advice.");
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

        if (hook.getHookedEntity() != null) {

            if (++hookedTicks >= 10 && tickCounter - lastReelTick >= retryInterval.get()) {
                lastReelTick = tickCounter;
                if (debug.get() && !silent.get()) info("Bobber is hooked on an entity, reeling in.");
                useRod();
            }
            return;
        }
        hookedTicks = 0;

        if (hook.isTouchingWater() || isWater(hook.getBlockPos())) hookTouchedWater = true;

        if (!hookTouchedWater && !verifySpot && hookAge >= LAND_CHECK_TICKS && hook.isOnGround()
            && tickCounter - lastReelTick >= CAST_COOLDOWN_TICKS) {
            if (debug.get() && !silent.get()) info("Bobber landed on solid ground, recasting.");
            lastReelTick = tickCounter;
            lastCastTick = tickCounter;
            useRod();
            return;
        }

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

            if (debug.get() && !silent.get()) info("No bite signal for %d ticks, recasting.", hookAge);
            hookAge = 0;
            caught = true;
            lastReelTick = tickCounter;
            useRod();
            return;
        }
        if (firstReel) {
            if (signal == null) return;
            caught = true;
            lastCatchTick = tickCounter;
            reeledHookId = hook.getId();
            retries = 0;
            lastReelTick = tickCounter;
            if (debug.get() && !silent.get()) info("Bite detected via %s, reeling in.", signal);
            useRod();
            return;
        }

        if (tickCounter - lastReelTick < retryInterval.get()) return;
        if (retries >= maxRetries.get()) {

            reeledHookId = -1;
            retries = 0;
            hookAge = 0;
            if (debug.get() && !silent.get()) info("Reel retries exhausted, restarting detection.");
            return;
        }
        retries++;
        lastReelTick = tickCounter;
        if (debug.get() && !silent.get()) info("Bobber still out, retry %d/%d.", retries, maxRetries.get());
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
}
