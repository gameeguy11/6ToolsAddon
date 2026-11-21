package gamerguy11.sixtoolsaddon.modules;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import gamerguy11.sixtoolsaddon.mixin.accessor.FishingBobberAccessor;
import gamerguy11.sixtoolsaddon.utils.FishStats;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BlockPosSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.world.LevelLoadingScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.entity.vehicle.ChestBoatEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.PlaySoundFromEntityS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.tag.FluidTags;
import meteordevelopment.meteorclient.utils.player.Rotations;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public class SixToolsAutoFish extends Module {
    private static final String DISPLAY_TITLE = "6Tools AutoFish";
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
    private final SettingGroup sgFish = settings.createGroup("Fish Stats");
    private final SettingGroup sgRecovery = settings.createGroup("Recovery");
    private final SettingGroup sgNotify = settings.createGroup("Notifications");
    private final SettingGroup sgNoBoats = settings.createGroup("No Boats Around");

    private final Setting<Boolean> silentNormal = sgNotify.add(new BoolSetting.Builder()
            .name("silent")
            .description("Master switch: turns off every Auto Fish chat message below (and debug).")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> notifyNoRodNormal = sgNotify.add(new BoolSetting.Builder()
            .name("notify-no-rod")
            .description("Tells you when there is no fishing rod in your hand or hotbar.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> notifyIdleResetNormal = sgNotify.add(new BoolSetting.Builder()
            .name("notify-idle-reset")
            .description("Tells you when Auto Fish was idle for a while and reset itself.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> notifyCastBlockedNormal = sgNotify.add(new BoolSetting.Builder()
            .name("notify-cast-blocked")
            .description("Tells you when it will not cast because the bobber would land off the water or too close to the boat.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> notifySpotNormal = sgNotify.add(new BoolSetting.Builder()
            .name("notify-hook-move")
            .description("Tells you when the bobber could not be moved to open water.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> notifyErrorsNormal = sgNotify.add(new BoolSetting.Builder()
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

    private final Setting<Boolean> detectFlagNormal = sgDetection.add(new BoolSetting.Builder()
            .name("detect-flag")
            .description("Bite signal: the bobber's own fish-caught flag.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> detectSoundNormal = sgDetection.add(new BoolSetting.Builder()
            .name("detect-sound")
            .description("Bite signal: the bobber splash sound played near your bobber.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> detectDipNormal = sgDetection.add(new BoolSetting.Builder()
            .name("detect-dip")
            .description("Bite signal: the bobber suddenly moves down.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Double> dipThresholdNormal = sgDetection.add(new DoubleSetting.Builder()
            .name("dip-threshold")
            .description("How far (blocks) the bobber must drop in one tick to count as a bite.")
            .defaultValue(0.12)
            .min(0.03)
            .sliderMax(0.5)
            .visible(detectDipNormal::get)
            .build()
    );

    private final Setting<Integer> retryIntervalNormal = sgDetection.add(new IntSetting.Builder()
            .name("retry-interval")
            .description("Ticks between reel-in retries while the bobber is still out after a bite.")
            .defaultValue(20)
            .min(5)
            .sliderMax(60)
            .build()
    );

    private final Setting<Integer> maxRetriesNormal = sgDetection.add(new IntSetting.Builder()
            .name("max-retries")
            .description("How many times to retry reeling in the same bobber.")
            .defaultValue(5)
            .min(0)
            .sliderMax(20)
            .build()
    );

    private final Setting<Integer> recastTimeoutNormal = sgDetection.add(new IntSetting.Builder()
            .name("recast-timeout")
            .description("Ticks a bobber may stay out without a detected bite before it is reeled in and cast again. 0 disables.")
            .defaultValue(600)
            .min(0)
            .sliderMax(1200)
            .build()
    );

    private final Setting<Boolean> debugNormal = sgNotify.add(new BoolSetting.Builder()
            .name("debug")
            .description("Prints in chat which bite signal fired.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> autoSwitchNormal = sgGeneral.add(new BoolSetting.Builder()
            .name("auto-switch")
            .description("Automatically switch to a fishing rod.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> antiBreakNormal = sgGeneral.add(new BoolSetting.Builder()
            .name("anti-break")
            .description("Avoid using rods that would break if they were cast.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> autoCastNormal = sgGeneral.add(new BoolSetting.Builder()
            .name("auto-cast")
            .description("Automatically cast the fishing rod.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> waterOnlyNormal = sgGeneral.add(new BoolSetting.Builder()
            .name("water-only")
            .description("Only casts when you are aiming at water.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Integer> waterRangeNormal = sgGeneral.add(new IntSetting.Builder()
            .name("water-range")
            .description("How far ahead of you the water has to be to cast.")
            .defaultValue(10)
            .min(2)
            .sliderMax(30)
            .visible(waterOnlyNormal::get)
            .build()
    );

    private final Setting<Boolean> customCastPitchNormal = sgGeneral.add(new BoolSetting.Builder()
            .name("custom-cast-pitch")
            .description("Sets your look angle before each cast to control how far the bobber lands.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Double> castPitchNormal = sgGeneral.add(new DoubleSetting.Builder()
            .name("cast-pitch")
            .description("Look angle used for casting. 0 is level (farthest cast), 90 is straight down (bobber drops at your feet).")
            .defaultValue(0.0)
            .min(-90)
            .max(90)
            .sliderMin(-30)
            .sliderMax(90)
            .visible(customCastPitchNormal::get)
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

    private final Setting<Boolean> autoAimNormal = sgGeneral.add(new BoolSetting.Builder()
            .name("auto-aim")
            .description("When the cast would land too close to the boat or off the water, adjusts pitch (and turns a little if needed) to a valid spot instead of waiting. Stops the module from stalling when the aim drifts onto land.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> lookAroundNormal = sgMovement.add(new BoolSetting.Builder()
            .name("look-around")
            .description("Turns the camera a little, once, when the server says to move or look around before casting again. It swings back and forth so the aim does not drift. The boat does not move.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Double> lookAroundDegreesNormal = sgMovement.add(new DoubleSetting.Builder()
            .name("look-around-degrees")
            .description("How far the camera turns each time.")
            .defaultValue(67.838)
            .min(2.0)
            .sliderMax(90.0)
            .build()
    );

    private final Setting<Boolean> lookAfterCatchNormal = sgMovement.add(new BoolSetting.Builder()
            .name("look-after-catch")
            .description("Turns the camera a little after every catch. The boat stays where it is. This shifts where the bobber lands, so it is off by default.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> moveHookOnNoFishNormal = sgMovement.add(new BoolSetting.Builder()
            .name("move-hook-on-no-fish")
            .description("When the chat says there are no more fish in this area, reels in and casts the bobber about hook-move-distance + 1 blocks away along X or Z where there is water, and keeps fishing there. When the message comes again it goes back to the spot it left, then picks a new random side. The boat does not move.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Integer> hookMoveDistanceNormal = sgMovement.add(new IntSetting.Builder()
            .name("hook-move-distance")
            .description("Minimum blocks between the old and the new bobber position.")
            .defaultValue(3)
            .min(3)
            .sliderMax(10)
            .visible(moveHookOnNoFishNormal::get)
            .build()
    );

    private final Setting<Boolean> randomizeHookSpotNormal = sgMovement.add(new BoolSetting.Builder()
            .name("randomize-hook-spot")
            .description("Every cast goes to a random spot on the water (random direction and distance) instead of turning back and forth between the same two spots. The cast path is checked so the bobber never flies into a block, it only lands in water and it never goes back to a spot used recently. After a no-more-fish message the new spot is at least hook-move-distance away (up to hook-move-max-distance + 1). Turns itself on when the bobber hooked an entity more than hooks-before-random times, until you reconnect.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Integer> hookMoveMaxDistanceNormal = sgMovement.add(new IntSetting.Builder()
            .name("hook-move-max-distance")
            .description("Farthest the randomized spot may be from the old bobber position (blocks). It is also limited by your cast range.")
            .defaultValue(8)
            .min(3)
            .sliderMax(14)
            .visible(() -> moveHookOnNoFishNormal.get() && randomizeHookSpotNormal.get())
            .build()
    );

    private final Setting<Integer> hooksBeforeRandomNormal = sgMovement.add(new IntSetting.Builder()
            .name("hooks-before-random")
            .description("When the bobber hooked an entity (boat, player, mob) more than this many times, the spot randomizer turns on at 100% until you reconnect.")
            .defaultValue(3)
            .min(1)
            .sliderMax(10)
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

    private final Setting<Boolean> notifyRecoveryNormal = sgNotify.add(new BoolSetting.Builder()
            .name("notify-recovery")
            .description("Tells you in chat about reconnecting, navigating to the saved position and finding a boat. Messages are sent once per step, not every tick.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> autoReconnectNormal = sgRecovery.add(new BoolSetting.Builder()
            .name("auto-reconnect")
            .description("Reconnects to the same server after a disconnect, goes to the saved position, gets into a boat and only then fishes again. Turning the module off cancels all of it.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Integer> reconnectDelayNormal = sgRecovery.add(new IntSetting.Builder()
            .name("reconnect-delay")
            .description("Seconds to wait before reconnecting. It doubles (up to 2 minutes) after every failed attempt or very short session.")
            .defaultValue(5)
            .min(1)
            .sliderMin(1)
            .sliderMax(60)
            .visible(autoReconnectNormal::get)
            .build()
    );

    private final Setting<Integer> maxReconnectAttemptsNormal = sgRecovery.add(new IntSetting.Builder()
            .name("max-reconnect-attempts")
            .description("Gives up after this many failed reconnects (or disconnects within 30 seconds of joining) in a row. 0 = never give up.")
            .defaultValue(20)
            .min(0)
            .sliderMax(50)
            .visible(autoReconnectNormal::get)
            .build()
    );

    private final Setting<Boolean> ignoreManualDisconnectNormal = sgRecovery.add(new BoolSetting.Builder()
            .name("ignore-manual-disconnect")
            .description("Does not reconnect when you leave with the Disconnect button (only kicks, timeouts and lost connections trigger it).")
            .defaultValue(true)
            .visible(autoReconnectNormal::get)
            .build()
    );

    private final Setting<Boolean> fishWithoutBoat = sgRecovery.add(new BoolSetting.Builder()
            .name("fish-without-boat")
            .description("When no empty boat could be entered within boat-timeout, start fishing from the saved position anyway instead of stopping.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> destinationSetNormal = sgRecovery.add(new BoolSetting.Builder()
            .name("destination-set")
            .description("Whether a recovery position is saved. Use the Set position / Clear position buttons at the top of this module's window.")
            .defaultValue(false)
            .build()
    );

    private final Setting<BlockPos> destinationNormal = sgRecovery.add(new BlockPosSetting.Builder()
            .name("destination")
            .description("Position walked to after reconnecting when no recorded path exists (saved by the Set position button, or set to the end of a recorded path). The dimension is not stored.")
            .defaultValue(new BlockPos(0, 64, 0))
            .visible(destinationSetNormal::get)
            .build()
    );

    private final Setting<Integer> navigationTimeoutNormal = sgRecovery.add(new IntSetting.Builder()
            .name("navigation-timeout")
            .description("Seconds the walk back to the saved position may take before recovery gives up. 0 = no limit.")
            .defaultValue(120)
            .min(0)
            .sliderMax(600)
            .build()
    );

    private final Setting<Double> boatSearchRadius = sgRecovery.add(new DoubleSetting.Builder()
            .name("boat-search-radius")
            .description("Blocks around you (in 3D, so above and below count) to look for an empty boat.")
            .defaultValue(10.0)
            .min(2.0)
            .sliderMax(32.0)
            .build()
    );

    private final Setting<Integer> searchIntervalSeconds = sgRecovery.add(new IntSetting.Builder()
            .name("boat-search-interval")
            .description("Seconds between boat searches while none is found.")
            .defaultValue(3)
            .min(1)
            .sliderMax(30)
            .build()
    );

    private final Setting<Integer> boatTimeout = sgRecovery.add(new IntSetting.Builder()
            .name("boat-timeout")
            .description("Seconds to look for an empty boat at the saved position. When none could be entered in that time, it acts as set by fish-without-boat. 0 = keep searching forever.")
            .defaultValue(40)
            .min(0)
            .sliderMax(1800)
            .build()
    );

    private final Setting<Boolean> noBoatsAround = sgNoBoats.add(new BoolSetting.Builder()
            .name("no-boats-around")
            .description("Made it because OG_Miru and his boyfriend zboi think they own smt. Fish from land. While on, every boat feature is ignored (avoid-boat, idle-move, boat advice, boat search) and the General, Movement, Detection, Notifications and Recovery settings above are ignored too: only the settings in this section are used. Relogging, walking back to the saved position and fish logging still work, with their own saved position / recorded path.")
            .defaultValue(false)
            .build()
    );

    private Setting<Boolean> nbBool(String name, String description, boolean def, java.util.function.BooleanSupplier extra) {
        return sgNoBoats.add(new BoolSetting.Builder()
                .name(name)
                .description(description)
                .defaultValue(def)
                .visible(() -> noBoatsAround.get() && (extra == null || extra.getAsBoolean()))
                .build());
    }

    private Setting<Integer> nbInt(String name, String description, int def, int min, int sliderMax, java.util.function.BooleanSupplier extra) {
        return sgNoBoats.add(new IntSetting.Builder()
                .name(name)
                .description(description)
                .defaultValue(def)
                .min(min)
                .sliderMin(min)
                .sliderMax(sliderMax)
                .visible(() -> noBoatsAround.get() && (extra == null || extra.getAsBoolean()))
                .build());
    }

    private Setting<Double> nbDouble(String name, String description, double def, double min, double sliderMax, java.util.function.BooleanSupplier extra) {
        return sgNoBoats.add(new DoubleSetting.Builder()
                .name(name)
                .description(description)
                .defaultValue(def)
                .min(min)
                .sliderMax(sliderMax)
                .visible(() -> noBoatsAround.get() && (extra == null || extra.getAsBoolean()))
                .build());
    }

    private final Setting<Boolean> nbAutoSwitch = nbBool("nb-auto-switch", "Automatically switch to a fishing rod.", true, null);
    private final Setting<Boolean> nbAntiBreak = nbBool("nb-anti-break", "Avoid using rods that would break if they were cast.", false, null);
    private final Setting<Boolean> nbAutoCast = nbBool("nb-auto-cast", "Automatically cast the fishing rod.", true, null);
    private final Setting<Boolean> nbWaterOnly = nbBool("nb-water-only", "Only casts when you are aiming at water.", true, null);
    private final Setting<Integer> nbWaterRange = nbInt("nb-water-range", "How far ahead of you the water has to be to cast.", 10, 2, 30, () -> nbWaterOnly.get());
    private final Setting<Boolean> nbCustomCastPitch = nbBool("nb-custom-cast-pitch", "Sets your look angle before each cast to control how far the bobber lands.", false, null);
    private final Setting<Double> nbCastPitch = sgNoBoats.add(new DoubleSetting.Builder()
            .name("nb-cast-pitch")
            .description("Look angle used for casting. 0 is level (farthest cast), 90 is straight down (bobber drops at your feet).")
            .defaultValue(0.0)
            .min(-90)
            .max(90)
            .sliderMin(-30)
            .sliderMax(90)
            .visible(() -> noBoatsAround.get() && nbCustomCastPitch.get())
            .build()
    );
    private final Setting<Boolean> nbAutoAim = nbBool("nb-auto-aim", "When the cast would land off the water, adjusts pitch (and turns a little if needed) to a valid water spot instead of waiting. Looks for the nearest water around you, since you are not on the water.", true, null);

    private final Setting<Boolean> nbLookAround = nbBool("nb-look-around", "Turns the camera a little, once, when the server says to move or look around before casting again. It swings back and forth so the aim does not drift.", true, null);
    private final Setting<Double> nbLookAroundDegrees = nbDouble("nb-look-around-degrees", "How far the camera turns each time.", 67.838, 2.0, 90.0, null);
    private final Setting<Boolean> nbLookAfterCatch = nbBool("nb-look-after-catch", "Turns the camera a little after every catch. This shifts where the bobber lands.", true, null);
    private final Setting<Boolean> nbMoveHookOnNoFish = nbBool("nb-move-hook-on-no-fish", "When the chat says there are no more fish in this area, reels in and casts the bobber about nb-hook-move-distance + 1 blocks away along X or Z where there is water, and keeps fishing there. When the message comes again it goes back to the spot it left, then picks a new random side. You do not move.", true, null);
    private final Setting<Integer> nbHookMoveDistance = nbInt("nb-hook-move-distance", "Minimum blocks between the old and the new bobber position.", 3, 3, 10, () -> nbMoveHookOnNoFish.get());
    private final Setting<Boolean> nbRandomizeHookSpot = nbBool("nb-randomize-hook-spot", "Every cast goes to a random spot on the water (random direction and distance) instead of turning back and forth between the same two spots. The cast path is checked so the bobber never flies into a block, it only lands in water and it never goes back to a spot used recently. After a no-more-fish message the new spot is at least nb-hook-move-distance away (up to nb-hook-move-max-distance + 1). Turns itself on when the bobber hooked an entity more than hooks-before-random times, until you reconnect.", false, null);
    private final Setting<Integer> nbHookMoveMaxDistance = nbInt("nb-hook-move-max-distance", "Farthest the randomized spot may be from the old bobber position (blocks). It is also limited by your cast range.", 8, 3, 14, () -> nbMoveHookOnNoFish.get() && nbRandomizeHookSpot.get());

    private final Setting<Boolean> nbDetectFlag = nbBool("nb-detect-flag", "Bite signal: the bobber's own fish-caught flag.", true, null);
    private final Setting<Boolean> nbDetectSound = nbBool("nb-detect-sound", "Bite signal: the bobber splash sound played near your bobber.", true, null);
    private final Setting<Boolean> nbDetectDip = nbBool("nb-detect-dip", "Bite signal: the bobber suddenly moves down.", true, null);
    private final Setting<Double> nbDipThreshold = nbDouble("nb-dip-threshold", "How far (blocks) the bobber must drop in one tick to count as a bite.", 0.12, 0.03, 0.5, () -> nbDetectDip.get());
    private final Setting<Integer> nbRetryInterval = nbInt("nb-retry-interval", "Ticks between reel-in retries while the bobber is still out after a bite.", 20, 5, 60, null);
    private final Setting<Integer> nbMaxRetries = nbInt("nb-max-retries", "How many times to retry reeling in the same bobber.", 5, 0, 20, null);
    private final Setting<Integer> nbRecastTimeout = nbInt("nb-recast-timeout", "Ticks a bobber may stay out without a detected bite before it is reeled in and cast again. 0 disables.", 600, 0, 1200, null);

    private final Setting<Boolean> nbSilent = nbBool("nb-silent", "Master switch: turns off every Auto Fish chat message below (and debug).", false, null);
    private final Setting<Boolean> nbNotifyNoRod = nbBool("nb-notify-no-rod", "Tells you when there is no fishing rod in your hand or hotbar.", true, null);
    private final Setting<Boolean> nbNotifyIdleReset = nbBool("nb-notify-idle-reset", "Tells you when Auto Fish was idle for a while and reset itself.", true, null);
    private final Setting<Boolean> nbNotifyCastBlocked = nbBool("nb-notify-cast-blocked", "Tells you when it will not cast because the bobber would land off the water.", true, null);
    private final Setting<Boolean> nbNotifySpot = nbBool("nb-notify-hook-move", "Tells you when the bobber could not be moved to open water.", true, null);
    private final Setting<Boolean> nbNotifyErrors = nbBool("nb-notify-errors", "Tells you when saving or opening the fish file fails.", true, null);
    private final Setting<Boolean> nbNotifyRecovery = nbBool("nb-notify-recovery", "Tells you in chat about reconnecting and walking to the saved position. Messages are sent once per step, not every tick.", true, null);
    private final Setting<Boolean> nbDebug = nbBool("nb-debug", "Prints in chat which bite signal fired.", false, null);

    private final Setting<Boolean> nbAutoReconnect = nbBool("nb-auto-reconnect", "Reconnects to the same server after a disconnect, walks to the no-boats saved position and fishes again. No boat is looked for. Turning the module off cancels all of it.", true, null);
    private final Setting<Integer> nbReconnectDelay = nbInt("nb-reconnect-delay", "Seconds to wait before reconnecting. It doubles (up to 2 minutes) after every failed attempt or very short session.", 5, 1, 60, () -> nbAutoReconnect.get());
    private final Setting<Integer> nbMaxReconnectAttempts = nbInt("nb-max-reconnect-attempts", "Gives up after this many failed reconnects (or disconnects within 30 seconds of joining) in a row. 0 = never give up.", 20, 0, 50, () -> nbAutoReconnect.get());
    private final Setting<Boolean> nbIgnoreManualDisconnect = nbBool("nb-ignore-manual-disconnect", "Does not reconnect when you leave with the Disconnect button (only kicks, timeouts and lost connections trigger it).", true, () -> nbAutoReconnect.get());
    private final Setting<Boolean> nbDestinationSet = nbBool("nb-destination-set", "Whether a no-boats recovery position is saved. Use the second row of Set position / Clear position buttons at the top of this module's window.", false, null);
    private final Setting<BlockPos> nbDestination = sgNoBoats.add(new BlockPosSetting.Builder()
            .name("nb-destination")
            .description("Position walked to after reconnecting when no recorded no-boats path exists. The dimension is not stored.")
            .defaultValue(new BlockPos(0, 64, 0))
            .visible(() -> noBoatsAround.get() && nbDestinationSet.get())
            .build()
    );
    private final Setting<Integer> nbNavigationTimeout = nbInt("nb-navigation-timeout", "Seconds the walk back to the saved position may take before recovery gives up. 0 = no limit.", 120, 0, 600, null);

    private final Setting<Boolean> nbShoreShuffle = nbBool("nb-shore-shuffle", "Every nb-shore-shuffle-interval seconds (a little random) walks a random number of blocks left or right along the water's edge, then goes back to fishing. It alternates sides (4 blocks left, then about 3 right, ...) so it never drifts far, never steps into water and always stays within nb-shore-edge-distance blocks of the water.", true, null);
    private final Setting<Integer> nbShoreInterval = nbInt("nb-shore-shuffle-interval", "Seconds between moves. How much it varies is set by nb-shore-shuffle-random-percent.", 30, 5, 300, () -> nbShoreShuffle.get());
    private final Setting<Integer> nbShoreMaxBlocks = nbInt("nb-shore-shuffle-max-blocks", "Farthest a single move goes (blocks). Moves alternate left and right, so you never end up more than this far from where you started.", 5, 1, 10, () -> nbShoreShuffle.get());
    private final Setting<Integer> nbShoreEdgeDistance = nbInt("nb-shore-edge-distance", "The new spot must have water within this many blocks, so you stay at the edge.", 3, 1, 6, () -> nbShoreShuffle.get());

    private final Setting<Integer> nbHooksBeforeRandom = nbInt("nb-hooks-before-random", "When the bobber hooked an entity (boat, player, mob) more than this many times, the spot randomizer turns on at 100% (and the shore shuffle becomes fully random and moves sooner) until you reconnect.", 3, 1, 10, null);
    private final Setting<Integer> nbShorePercent = nbInt("nb-shore-shuffle-random-percent", "How random the shore shuffle is, in percent. It sets how much the time between moves varies (+/- this much) and how often a move picks its side at random instead of going back the other way. 100 = fully random. It is 100 by itself after too many hooked entities.", 20, 0, 100, () -> nbShoreShuffle.get());

    private final class Dual<T> {
        private final Setting<T> normal;
        private final Setting<T> noBoats;

        private Dual(Setting<T> normal, Setting<T> noBoats) {
            this.normal = normal;
            this.noBoats = noBoats;
        }

        T get() {
            return noBoatsAround.get() ? noBoats.get() : normal.get();
        }

        boolean set(T value) {
            return (noBoatsAround.get() ? noBoats : normal).set(value);
        }
    }

    private final Dual<Boolean> silent = new Dual<>(silentNormal, nbSilent);
    private final Dual<Boolean> notifyNoRod = new Dual<>(notifyNoRodNormal, nbNotifyNoRod);
    private final Dual<Boolean> notifyIdleReset = new Dual<>(notifyIdleResetNormal, nbNotifyIdleReset);
    private final Dual<Boolean> notifyCastBlocked = new Dual<>(notifyCastBlockedNormal, nbNotifyCastBlocked);
    private final Dual<Boolean> notifySpot = new Dual<>(notifySpotNormal, nbNotifySpot);
    private final Dual<Boolean> notifyErrors = new Dual<>(notifyErrorsNormal, nbNotifyErrors);
    private final Dual<Boolean> notifyRecovery = new Dual<>(notifyRecoveryNormal, nbNotifyRecovery);
    private final Dual<Boolean> debug = new Dual<>(debugNormal, nbDebug);
    private final Dual<Boolean> detectFlag = new Dual<>(detectFlagNormal, nbDetectFlag);
    private final Dual<Boolean> detectSound = new Dual<>(detectSoundNormal, nbDetectSound);
    private final Dual<Boolean> detectDip = new Dual<>(detectDipNormal, nbDetectDip);
    private final Dual<Double> dipThreshold = new Dual<>(dipThresholdNormal, nbDipThreshold);
    private final Dual<Integer> retryInterval = new Dual<>(retryIntervalNormal, nbRetryInterval);
    private final Dual<Integer> maxRetries = new Dual<>(maxRetriesNormal, nbMaxRetries);
    private final Dual<Integer> recastTimeout = new Dual<>(recastTimeoutNormal, nbRecastTimeout);
    private final Dual<Boolean> autoSwitch = new Dual<>(autoSwitchNormal, nbAutoSwitch);
    private final Dual<Boolean> antiBreak = new Dual<>(antiBreakNormal, nbAntiBreak);
    private final Dual<Boolean> autoCast = new Dual<>(autoCastNormal, nbAutoCast);
    private final Dual<Boolean> waterOnly = new Dual<>(waterOnlyNormal, nbWaterOnly);
    private final Dual<Integer> waterRange = new Dual<>(waterRangeNormal, nbWaterRange);
    private final Dual<Boolean> customCastPitch = new Dual<>(customCastPitchNormal, nbCustomCastPitch);
    private final Dual<Double> castPitch = new Dual<>(castPitchNormal, nbCastPitch);
    private final Dual<Boolean> autoAim = new Dual<>(autoAimNormal, nbAutoAim);
    private final Dual<Boolean> lookAround = new Dual<>(lookAroundNormal, nbLookAround);
    private final Dual<Double> lookAroundDegrees = new Dual<>(lookAroundDegreesNormal, nbLookAroundDegrees);
    private final Dual<Boolean> lookAfterCatch = new Dual<>(lookAfterCatchNormal, nbLookAfterCatch);
    private final Dual<Boolean> moveHookOnNoFish = new Dual<>(moveHookOnNoFishNormal, nbMoveHookOnNoFish);
    private final Dual<Integer> hookMoveDistance = new Dual<>(hookMoveDistanceNormal, nbHookMoveDistance);
    private final Dual<Boolean> randomizeHookSpot = new Dual<>(randomizeHookSpotNormal, nbRandomizeHookSpot);
    private final Dual<Integer> hookMoveMaxDistance = new Dual<>(hookMoveMaxDistanceNormal, nbHookMoveMaxDistance);
    private final Dual<Integer> hooksBeforeRandom = new Dual<>(hooksBeforeRandomNormal, nbHooksBeforeRandom);
    private final Dual<Boolean> autoReconnect = new Dual<>(autoReconnectNormal, nbAutoReconnect);
    private final Dual<Integer> reconnectDelay = new Dual<>(reconnectDelayNormal, nbReconnectDelay);
    private final Dual<Integer> maxReconnectAttempts = new Dual<>(maxReconnectAttemptsNormal, nbMaxReconnectAttempts);
    private final Dual<Boolean> ignoreManualDisconnect = new Dual<>(ignoreManualDisconnectNormal, nbIgnoreManualDisconnect);
    private final Dual<Boolean> destinationSet = new Dual<>(destinationSetNormal, nbDestinationSet);
    private final Dual<BlockPos> destination = new Dual<>(destinationNormal, nbDestination);
    private final Dual<Integer> navigationTimeout = new Dual<>(navigationTimeoutNormal, nbNavigationTimeout);

    private boolean noBoatsMode() {
        return noBoatsAround.get();
    }

    private boolean boatAvoid() {
        return !noBoatsMode() && avoidBoat.get();
    }

    private Setting<BlockPos> destSetting(boolean nb) {
        return nb ? nbDestination : destinationNormal;
    }

    private Setting<Boolean> destSetSetting(boolean nb) {
        return nb ? nbDestinationSet : destinationSetNormal;
    }

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
    private final ArrayDeque<Vec3d> recentSpots = new ArrayDeque<>();

    private final List<Vec3d> blockedSpots = new ArrayList<>();
    private int blockedHookId = -1;
    private int blockedRecasts;

    private int playerHooks;
    private int lastHookedMsgTick = -100;
    private boolean randomForced;
    private boolean shoreMoving;
    private Vec3d shoreTarget;
    private Vec3d shoreProgress = Vec3d.ZERO;
    private Vec3d shoreStart;
    private Vec3d shoreDetour;
    private int shoreDetourTicks;
    private int shoreStuckCount;
    private int shoreSideSign = 1;

    private int recastMessages;
    private int lastShoreStartTick = -1000;
    private Vec3d shoreAnchor;
    private Vec3d shoreAxis;
    private int shoreMoveTicks;
    private int nextShoreTick;

    private boolean shoreGotSomething;
    private float shoreRestoreYaw = Float.NaN;
    private float shoreRestorePitch;
    private boolean noFishPending;
    private int noFishTick;
    private boolean randomAimReady;
    private float randomAimYaw;
    private float randomAimPitch;
    private int randomAimTick;
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
    private static final int RELOCATE_CAST_COOLDOWN_TICKS = 8;
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

        runInMainMenu = true;

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            tickRecording();
            if (isActive()) tickRecovery();
        });
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
        resetFishState();
        if (trackStats.get()) FishStats.writeHtml(statsInterval.get(), serverTotal.get());
    }

    @Override
    public void toggle() {
        super.toggle();
        resetRecovery();
        if (isActive() && mc.player != null && mc.world != null) {
            captureServer();
            setState(RecoveryState.FISHING);
        }
    }

    private void resetFishState() {
        caught = false;
        reeledHookId = -1;
        pendingBlocks = 0;
        moving = false;
        needNewSpot = false;
        reelForSpot = false;
        verifySpot = false;
        spotRetries = 0;
        returnSpot = null;
        recentSpots.clear();
        randomAimReady = false;
        noFishPending = false;
        shoreMoving = false;
        shoreGotSomething = false;
        shoreAnchor = null;
        shoreAxis = null;
        scheduleShore();
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
        shoreMoving = false;
        cancelOwnNav();
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
        if (CAUGHT_PATTERN.matcher(text).find()) {
            lastCatchTick = tickCounter;
            recastMessages = 0;
        }
        String message = text.toLowerCase();
        if (message.contains(RECAST_MESSAGE)) recastMessages++;

        if (message.contains(RECAST_MESSAGE) && tickCounter - lastLookTick >= MESSAGE_COOLDOWN_TICKS) {
            lastLookTick = tickCounter;
            if (needNewSpot) relocateNudge = Math.min(MAX_RELOCATE_NUDGE, relocateNudge + 2.0f);
            else if (lookAround.get()) lookAround();
        }

        if (message.contains(NO_FISH_MESSAGE) && moveHookOnNoFish.get() && !needNewSpot
                && tickCounter - lastMessageMoveTick >= MESSAGE_COOLDOWN_TICKS && mc.player != null) {

            noFishPending = true;
            noFishTick = tickCounter;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        tickCounter++;
        flushMessages();
        if (trackStats.get() && tickCounter % (statsInterval.get() * 20) == 0) FishStats.writeHtml(statsInterval.get(), serverTotal.get());
        if (state == RecoveryState.IDLE) {
            resetFishState();
            setState(RecoveryState.FISHING);
        }
        if (state != RecoveryState.FISHING) return;
        tickMove();
        tickShore();
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

        if (!noBoatsMode() && idleMove.get() && !moving && pendingBlocks == 0 && tickCounter - lastCatchTick >= idleMoveTime.get() * 20) {
            lastCatchTick = tickCounter;
            pendingBlocks = idleMoveDistance.get();
        }

        if (pendingBlocks > 0 && !moving) {
            int blocks = pendingBlocks;
            pendingBlocks = 0;
            startMove(blocks);
        }

        if (noBoatsMode() && nbShoreShuffle.get() && !shoreMoving) {

            boolean dead = (recastMessages >= 3 || tickCounter - lastCatchTick > 900) && tickCounter - lastShoreStartTick >= 200;
            if (dead) {
                needNewSpot = false;
                reelForSpot = false;
                verifySpot = false;
                noFishPending = false;
                startShoreMove();
            } else if (shoreGotSomething && tickCounter >= nextShoreTick && !needNewSpot && !reelForSpot && !noFishPending) {
                startShoreMove();
            }
        }

        if (moving || pendingBlocks > 0 || shoreMoving) return;

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

        startHookMove();

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

    private void startHookMove() {
        if (!noFishPending) return;
        if (needNewSpot || !moveHookOnNoFish.get() || tickCounter - noFishTick > 100) {
            noFishPending = false;
            return;
        }
        FishingBobberEntity hook = mc.player.fishHook;
        Vec3d from = lastHookPos;
        if (hook != null && hook.isTouchingWater()) from = hook.getEntityPos();
        else if (hook != null && tickCounter - noFishTick < 40) return;
        if (from == null) return;
        noFishPending = false;
        lastMessageMoveTick = tickCounter;

        Vec3d target = null;
        if (randomizerActive()) {
            target = chooseRandomSpot(from);
            returnSpot = null;
        }
        if (target == null) {
            if (!randomizerActive() && returnSpot != null && usableSpot(returnSpot)) {
                target = returnSpot;
                returnSpot = null;
            } else {
                target = chooseNeighbor(from);
                returnSpot = target == null || randomizerActive() ? null : from;
            }
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

    private void logFish(String text, net.minecraft.text.Text message) {
        if (CAUGHT_PATTERN.matcher(text).find()) shoreGotSomething = true;
        if (!trackStats.get()) return;
        Matcher m = CAUGHT_PATTERN.matcher(text);
        if (!m.find()) return;
        double size;
        try {
            size = Double.parseDouble(m.group(2).replace(',', '.'));
        } catch (NumberFormatException e) {
            return;
        }
        String name = m.group(1).trim();
        FishStats.record(name, size, System.currentTimeMillis() / 1000, FishStats.colorRarity(message, name));
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList root = theme.verticalList();
        WHorizontalList list = root.add(theme.horizontalList()).expandX().widget();

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

        addRecoveryRow(theme, root, false);
        addRecoveryRow(theme, root, true);

        return root;
    }

    private void addRecoveryRow(GuiTheme theme, WVerticalList root, boolean nb) {
        WHorizontalList recovery = root.add(theme.horizontalList()).expandX().widget();
        WLabel positionLabel = recovery.add(theme.label(destinationText(nb))).widget();

        WButton record = recovery.add(theme.button("Record path")).widget();
        record.action = () -> {
            startRecording(nb);
            positionLabel.set("Recording...");
        };

        WButton stopRecord = recovery.add(theme.button("Stop recording")).widget();
        stopRecord.action = () -> {
            stopRecording();
            positionLabel.set(destinationText(nb));
        };

        WButton setPosition = recovery.add(theme.button("Set position")).widget();
        setPosition.action = () -> {
            if (mc.player == null) {
                error("Join a world first: Set position saves where you are standing.");
                return;
            }
            clearPath(nb);
            destSetting(nb).set(mc.player.getBlockPos());
            destSetSetting(nb).set(true);
            positionLabel.set(destinationText(nb));
        };

        WButton clearPosition = recovery.add(theme.button("Clear position")).widget();
        clearPosition.action = () -> {
            clearPath(nb);
            destSetSetting(nb).set(false);
            positionLabel.set(destinationText(nb));
        };
    }

    private int shorePercent() {
        return randomForced ? 100 : Math.min(100, Math.max(0, nbShorePercent.get()));
    }

    private void scheduleShore() {
        double jitter = 1.0 + (java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 2.0 - 1.0) * shorePercent() / 100.0;
        nextShoreTick = tickCounter + Math.max(60, (int) (nbShoreInterval.get() * 20 * jitter));
    }

    private Vec3d shoreAxisFromWater() {
        BlockPos base = mc.player.getBlockPos();
        for (int r = 1; r <= 8; r++) {
            for (int i = 0; i < 16; i++) {
                double angle = Math.toRadians(i * 22.5);
                double dx = -Math.sin(angle) * r;
                double dz = Math.cos(angle) * r;
                int x = base.getX() + (int) Math.round(dx);
                int z = base.getZ() + (int) Math.round(dz);
                for (int dy = 1; dy >= -3; dy--) {
                    if (isWater(new BlockPos(x, base.getY() + dy, z))) {
                        double len = Math.hypot(dx, dz);
                        return new Vec3d(-dz / len, 0, dx / len);
                    }
                }
            }
        }
        double yaw = Math.toRadians(mc.player.getYaw());
        return new Vec3d(-Math.cos(yaw), 0, -Math.sin(yaw));
    }

    private double groundY(double x, double z, double refY) {
        BlockPos col = BlockPos.ofFloored(x, refY, z);
        for (int dy : new int[]{0, 1, -1, -2}) {
            BlockPos feet = col.up(dy);
            BlockPos below = feet.down();
            BlockPos head = feet.up();
            boolean solidBelow = !mc.world.getBlockState(below).getCollisionShape(mc.world, below).isEmpty();
            boolean feetFree = mc.world.getBlockState(feet).getCollisionShape(mc.world, feet).isEmpty();
            boolean headFree = mc.world.getBlockState(head).getCollisionShape(mc.world, head).isEmpty();
            if (solidBelow && feetFree && headFree && !isWater(feet) && !isWater(below)) return feet.getY();
        }
        return Double.NaN;
    }

    private boolean waterNear(Vec3d pos, int radius) {
        int fy = MathHelper.floor(pos.y);
        int px = MathHelper.floor(pos.x);
        int pz = MathHelper.floor(pos.z);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -3; dy <= 0; dy++) {
                    if (isWater(new BlockPos(px + dx, fy + dy, pz + dz))) return true;
                }
            }
        }
        return false;
    }

    private Vec3d shorePath(Vec3d pos, int dir, int blocks) {
        double y = pos.y;
        Vec3d end = null;
        for (int k = 1; k <= blocks * 2; k++) {
            double x = pos.x + shoreAxis.x * dir * 0.5 * k;
            double z = pos.z + shoreAxis.z * dir * 0.5 * k;
            double ground = groundY(x, z, y);
            if (Double.isNaN(ground) || Math.abs(ground - y) > 1.01) return null;
            y = ground;
            end = new Vec3d(x, ground, z);
        }
        if (end == null || !waterNear(end, nbShoreEdgeDistance.get())) return null;
        return end;
    }

    private Vec3d shoreTargetFor(Vec3d pos, int dir, int blocks) {
        for (int n = blocks; n >= Math.min(blocks, 2); n--) {
            Vec3d target = shorePath(pos, dir, n);
            if (target != null) return target;
        }
        return null;
    }

    private double shoreLeash() {
        return nbShoreMaxBlocks.get() * (randomForced ? 2 : 1) + 3.0;
    }

    private boolean withinLeash(Vec3d p, double x, double z) {
        if (shoreAnchor == null) return true;
        double away = Math.hypot(p.x - shoreAnchor.x, p.z - shoreAnchor.z);
        return Math.hypot(x - shoreAnchor.x, z - shoreAnchor.z) <= Math.max(shoreLeash(), away) + 0.5;
    }

    private void startShoreMove() {
        scheduleShore();
        lastShoreStartTick = tickCounter;
        recastMessages = 0;
        if (mc.player.hasVehicle() || !mc.player.isOnGround()) return;
        Vec3d pos = mc.player.getEntityPos();
        if (shoreAnchor == null || shoreAxis == null) {
            shoreAxis = shoreAxisFromWater();
            shoreAnchor = pos;
        }
        double offset = (pos.x - shoreAnchor.x) * shoreAxis.x + (pos.z - shoreAnchor.z) * shoreAxis.z;

        Vec3d homeTarget = Math.hypot(pos.x - shoreAnchor.x, pos.z - shoreAnchor.z) > shoreLeash() - 2.0 ? shoreAnchor : null;
        java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();

        int max = nbShoreMaxBlocks.get() * (randomForced ? 2 : 1);
        int dir = Math.abs(offset) < 0.5 || random.nextInt(100) < shorePercent() ? (random.nextBoolean() ? 1 : -1) : (offset > 0 ? -1 : 1);
        int minBlocks = Math.min(max, 2);
        int blocks = minBlocks + random.nextInt(max - minBlocks + 1);
        if (Math.abs(offset) >= 0.5 && Math.signum(offset) == dir) {
            double room = max - Math.abs(offset);
            if (room < 1) dir = -dir;
            else blocks = Math.min(blocks, (int) room);
        }
        Vec3d target = homeTarget != null ? homeTarget : shoreTargetFor(pos, dir, blocks);
        if (target == null) target = shoreTargetFor(pos, -dir, blocks);
        if (target == null) {
            if (debug.get() && !silent.get()) info("Shore shuffle: no dry spot at the edge, skipping this time.");
            return;
        }
        if (mc.player.fishHook != null) useRod();
        shoreRestoreYaw = mc.player.getYaw();
        shoreRestorePitch = mc.player.getPitch();
        shoreGotSomething = false;
        shoreDetour = null;
        shoreDetourTicks = 0;
        shoreStuckCount = 0;
        shoreStart = pos;
        shoreTarget = target;
        shoreProgress = pos;
        shoreMoveTicks = 0;
        shoreMoving = true;
        if (debug.get() && !silent.get()) info("Shore shuffle: walking %.1f blocks along the shore (offset from start %.1f).", Math.hypot(target.x - pos.x, target.z - pos.z), offset);
    }

    private void tickShore() {
        if (!shoreMoving) return;
        if (mc.player.hasVehicle()) {
            stopShore();
            return;
        }
        Vec3d p = mc.player.getEntityPos();
        if (Math.hypot(p.x - shoreTarget.x, p.z - shoreTarget.z) <= 0.4 || ++shoreMoveTicks > 400) {
            stopShore();
            return;
        }
        if (shoreAnchor != null && shoreTarget != shoreAnchor
                && Math.hypot(p.x - shoreAnchor.x, p.z - shoreAnchor.z) > shoreLeash()) {
            shoreTarget = shoreAnchor;
            shoreDetour = null;
            shoreDetourTicks = 0;
            shoreStuckCount = 0;
        }
        Vec3d blocker = detourAround(p, shoreTarget);
        if (blocker != null) {
            if (Math.hypot(p.x - shoreTarget.x, p.z - shoreTarget.z) < 1.6) {
                stopShore();
                return;
            }
            shoreDetour = blocker;
            shoreDetourTicks = 40;
        }
        if (shoreMoveTicks % 20 == 0) {
            if (p.squaredDistanceTo(shoreProgress) < 0.09) {

                if (++shoreStuckCount > 6) {
                    stopShore();
                    return;
                }
                shoreSideSign = -shoreSideSign;
                double dx = shoreTarget.x - p.x;
                double dz = shoreTarget.z - p.z;
                double len = Math.max(0.001, Math.hypot(dx, dz));
                double reach = 1.5 + shoreStuckCount;
                double x = p.x - dz / len * reach * shoreSideSign + dx / len * 1.5;
                double z = p.z + dx / len * reach * shoreSideSign + dz / len * 1.5;
                double g = groundY(x, z, p.y);
                if (Double.isNaN(g)) {
                    x = p.x + dz / len * reach * shoreSideSign + dx / len * 1.5;
                    z = p.z - dx / len * reach * shoreSideSign + dz / len * 1.5;
                    g = groundY(x, z, p.y);
                }
                if (!Double.isNaN(g) && Math.abs(g - p.y) <= 1.01 && withinLeash(p, x, z)) {
                    shoreDetour = new Vec3d(x, g, z);
                    shoreDetourTicks = 30;
                }
            }
            shoreProgress = p;
        }
        if (shoreDetour != null && shoreDetourTicks > 0 && Math.hypot(p.x - shoreDetour.x, p.z - shoreDetour.z) > 0.5) {
            shoreDetourTicks--;
            walkToward(shoreDetour, false);
        } else {
            shoreDetour = null;
            walkToward(shoreTarget, false);
        }
    }

    private Vec3d detourAround(Vec3d p, Vec3d target) {
        double dx = target.x - p.x;
        double dz = target.z - p.z;
        double len = Math.hypot(dx, dz);
        if (len < 0.3) return null;
        double hx = dx / len;
        double hz = dz / len;
        double reach = Math.min(len, 2.5);
        Box ahead = mc.player.getBoundingBox().stretch(hx * reach, 0, hz * reach).expand(0.2, 0, 0.2);
        Entity blocker = null;
        double best = Double.MAX_VALUE;
        for (Entity e : mc.world.getOtherEntities(mc.player, ahead, en ->
                en != mc.player.getVehicle() && !en.isSpectator()
                        && (en instanceof net.minecraft.entity.vehicle.VehicleEntity || en instanceof net.minecraft.entity.LivingEntity))) {
            double d = e.squaredDistanceTo(mc.player);
            if (d < best) {
                best = d;
                blocker = e;
            }
        }
        if (blocker == null) return null;
        double px = -hz;
        double pz = hx;
        Box bb = blocker.getBoundingBox();
        double half = Math.max(bb.getLengthX(), bb.getLengthZ()) * 0.5;
        double side = half + 1.0;
        double past = half + 1.0;
        Vec3d c = blocker.getEntityPos();
        Vec3d best1 = null;
        for (int s : new int[]{1, -1}) {
            double x = c.x + px * side * s + hx * past;
            double z = c.z + pz * side * s + hz * past;
            double g = groundY(x, z, p.y);
            if (Double.isNaN(g) || Math.abs(g - p.y) > 1.01 || !withinLeash(p, x, z)) continue;
            Vec3d cand = new Vec3d(x, g, z);
            if (!waterNear(cand, 1)) return cand;
            if (best1 == null) best1 = cand;
        }
        return best1;
    }

    private void stopShore() {
        if (shoreMoving && shoreStart != null && mc.player != null) {
            Vec3d now = mc.player.getEntityPos();
            if (Math.hypot(now.x - shoreStart.x, now.z - shoreStart.z) < 1.5) {

                shoreGotSomething = true;
                nextShoreTick = tickCounter + 100;
            }
        }
        shoreMoving = false;
        stopManualWalk();
        if (mc.player != null && !Float.isNaN(shoreRestoreYaw)) {
            mc.player.setYaw(shoreRestoreYaw);
            mc.player.setPitch(shoreRestorePitch);
        }
        shoreRestoreYaw = Float.NaN;

        lastHookPos = null;
        returnSpot = null;
        settledYaw = Float.NaN;
        lastCastTick = tickCounter;
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

    private double surfaceYOnLand() {
        BlockPos base = mc.player.getBlockPos();
        int reach = Math.max(2, waterRange.get());
        for (int r = 0; r <= reach; r++) {
            int samples = r == 0 ? 1 : 16;
            for (int i = 0; i < samples; i++) {
                double angle = Math.toRadians(i * 360.0 / samples);
                int x = base.getX() + (int) Math.round(-Math.sin(angle) * r);
                int z = base.getZ() + (int) Math.round(Math.cos(angle) * r);
                for (int dy = 1; dy >= -4; dy--) {
                    BlockPos p = new BlockPos(x, base.getY() + dy, z);
                    var fluid = mc.world.getFluidState(p);
                    if (fluid.isIn(FluidTags.WATER)) return p.getY() + fluid.getHeight(mc.world, p);
                }
            }
        }
        return Double.NaN;
    }

    private double surfaceY() {
        if (noBoatsMode()) return surfaceYOnLand();
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
        if (boatAvoid() && mc.player.hasVehicle() && range < minCastDistance.get()) return false;
        if (!blockedSpots.isEmpty() && autoAim.get() && nearBlockedSpot(land)) return false;
        return !waterOnly.get() || isWater(BlockPos.ofFloored(land.x, surface - 0.1, land.z));
    }

    private boolean prepareCast() {
        double surface = surfaceY();
        if (Double.isNaN(surface)) {

            if (waterOnly.get() && !aimedAtWater()) return false;
            return !(boatAvoid() && landingNearBoat());
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
        if (tickCounter - lastCastTick < (needNewSpot ? RELOCATE_CAST_COOLDOWN_TICKS : CAST_COOLDOWN_TICKS)) return;

        boolean relocating = needNewSpot && aimAtTarget();
        if (needNewSpot && !relocating) {
            needNewSpot = false;
            verifySpot = false;
            returnSpot = null;
            warnSpot();
        }
        boolean randomAim = !relocating && randomizerActive() && aimRandomSpot();
        if (!relocating && !randomAim && customCastPitch.get()) mc.player.setPitch(castPitch.get().floatValue());
        if (!randomAim) ensureLookChanged(relocating);
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
        randomAimReady = false;
        useRod();
    }

    private boolean randomizerActive() {
        return randomizeHookSpot.get() || randomForced;
    }

    private boolean aimRandomSpot() {
        if (randomAimReady && tickCounter - randomAimTick < 200) {
            mc.player.setYaw(randomAimYaw);
            mc.player.setPitch(randomAimPitch);
            return true;
        }
        randomAimReady = false;
        double surface = surfaceY();
        if (Double.isNaN(surface)) return false;

        double min = Math.max(MIN_CAST_RANGE, boatAvoid() ? minCastDistance.get() : 0.0);
        double max = Math.min(MAX_CAST_RANGE, Math.max(waterRange.get(), min + 2.0));
        java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < 40; i++) {
                float yaw = (float) (random.nextDouble() * 360.0 - 180.0);
                double range = min + random.nextDouble() * (max - min);
                Float pitch = solvePitch(yaw, range, surface);
                if (pitch == null || !castValid(yaw, pitch, surface)) continue;
                Vec3d land = landingPoint(yaw, pitch, surface);
                if (land == null || nearBlockedSpot(land) || !waterAround(land)) continue;
                if (pass == 0 && nearRecentSpot(land)) continue;
                if (!flightClear(yaw, pitch, surface)) continue;
                rememberSpot(land);
                randomAimYaw = yaw;
                randomAimPitch = pitch;
                randomAimTick = tickCounter;
                randomAimReady = true;
                mc.player.setYaw(yaw);
                mc.player.setPitch(pitch);
                if (debug.get() && !silent.get()) info("Random spot: yaw %.0f, pitch %.0f, %.1f blocks out.", yaw, pitch, range);
                return true;
            }
        }
        return false;
    }

    private boolean flightClear(float yaw, float pitch, double surfaceY) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double vy = MathHelper.clamp(-Math.tan(pitchRad), -5.0, 5.0);
        double speed = 0.6 / Math.sqrt(1.0 + vy * vy) + 0.5;
        Vec3d velocity = new Vec3d(-Math.sin(yawRad) * speed, vy * speed, Math.cos(yawRad) * speed);
        Vec3d pos = new Vec3d(mc.player.getX() - Math.sin(yawRad) * 0.3, mc.player.getEyeY(), mc.player.getZ() + Math.cos(yawRad) * 0.3);
        if (pos.y <= surfaceY) return false;
        List<Box> blockers = new ArrayList<>();
        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player || entity == mc.player.getVehicle() || entity.isRemoved() || entity.isSpectator()) continue;
            if (entity instanceof net.minecraft.entity.ItemEntity || entity instanceof net.minecraft.entity.ExperienceOrbEntity
                    || entity instanceof net.minecraft.entity.projectile.ProjectileEntity) continue;
            if (entity.squaredDistanceTo(mc.player) > 24.0 * 24.0) continue;
            blockers.add(entity.getBoundingBox().expand(0.35));
        }
        for (int i = 0; i < 200; i++) {
            velocity = velocity.add(0.0, -0.03, 0.0);
            Vec3d next = pos.add(velocity);
            boolean end = next.y <= surfaceY;
            Vec3d to = next;
            if (end) {
                double t = (pos.y - surfaceY) / (pos.y - next.y);
                to = new Vec3d(pos.x + (next.x - pos.x) * t, surfaceY, pos.z + (next.z - pos.z) * t);
            }
            BlockHitResult hit = mc.world.raycast(new RaycastContext(pos, to, RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE, mc.player));
            if (hit.getType() != HitResult.Type.MISS) return false;
            for (Box box : blockers) {
                if (box.contains(to) || box.raycast(pos, to).isPresent()) return false;
            }
            if (end) return true;
            pos = next;
            velocity = velocity.multiply(0.92);
        }
        return false;
    }

    private static float yawTo(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private static final double RECENT_SPOT_RADIUS = 2.5;
    private static final int RECENT_SPOT_MEMORY = 8;

    private boolean nearRecentSpot(Vec3d spot) {
        for (Vec3d old : recentSpots) {
            if (Math.hypot(spot.x - old.x, spot.z - old.z) < RECENT_SPOT_RADIUS) return true;
        }
        return false;
    }

    private void rememberSpot(Vec3d spot) {
        recentSpots.addLast(spot);
        while (recentSpots.size() > RECENT_SPOT_MEMORY) recentSpots.pollFirst();
    }

    private Vec3d chooseRandomSpot(Vec3d from) {
        double min = hookMoveDistance.get() + 1.0;
        double max = Math.max(min, hookMoveMaxDistance.get() + 1.0);
        java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < 40; i++) {
                double angle = random.nextDouble() * Math.PI * 2.0;
                double distance = min + random.nextDouble() * (max - min);
                Vec3d candidate = from.add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
                if (pass == 0 && (nearRecentSpot(candidate) || Math.hypot(candidate.x - from.x, candidate.z - from.z) < RECENT_SPOT_RADIUS)) continue;
                if (!usableSpot(candidate)) continue;
                double toX = candidate.x - mc.player.getX();
                double toZ = candidate.z - mc.player.getZ();
                float toYaw = yawTo(toX, toZ);
                Float toPitch = solvePitch(toYaw, Math.hypot(toX, toZ), candidate.y + 0.2);
                if (toPitch == null || !flightClear(toYaw, toPitch, candidate.y + 0.2)) continue;
                rememberSpot(from);
                rememberSpot(candidate);
                return candidate;
            }
        }
        return null;
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

    private static final double BLOCKED_SPOT_RADIUS = 3.0;
    private static final int MAX_BLOCKED_SPOTS = 32;
    private static final int MAX_BLOCKED_RECASTS = 4;

    private boolean nearBlockedSpot(Vec3d spot) {
        for (Vec3d blocked : blockedSpots) {
            if (Math.hypot(spot.x - blocked.x, spot.z - blocked.z) < BLOCKED_SPOT_RADIUS) return true;
        }
        return false;
    }

    private void blockSpot(Vec3d spot) {
        if (nearBlockedSpot(spot)) return;
        blockedSpots.add(spot);
        while (blockedSpots.size() > MAX_BLOCKED_SPOTS) blockedSpots.remove(0);
    }

    private boolean usableSpot(Vec3d spot) {
        if (nearBlockedSpot(spot)) return false;
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
        if (noBoatsMode()) return;
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

            Entity hooked = hook.getHookedEntity();
            if (hooked != mc.player && hooked != mc.player.getVehicle() && hook.getId() != blockedHookId) {
                blockedHookId = hook.getId();
                blockSpot(hook.getEntityPos());
                if (lastHookPos != null) blockSpot(lastHookPos);
                playerHooks++;
                if (!silent.get() && notifySpot.get() && tickCounter - lastHookedMsgTick >= 60) {
                    lastHookedMsgTick = tickCounter;
                    info("Hooked %s (%d times) - that spot is blocked until you reconnect.", hooked.getName().getString(), playerHooks);
                }
                if (playerHooks > hooksBeforeRandom.get()) {
                    if (!randomForced) {
                        randomForced = true;
                        if (!silent.get() && notifySpot.get()) info("Hooked entities %d times - moving 100%% randomly until you reconnect.", playerHooks);
                    }

                    if (nextShoreTick - tickCounter > 60) nextShoreTick = tickCounter + 20;
                }
            }
            if (++hookedTicks >= 4 && tickCounter - lastReelTick >= retryInterval.get()) {
                lastReelTick = tickCounter;
                if (debug.get() && !silent.get()) info("Bobber is hooked on an entity, reeling in.");
                useRod();
            }
            return;
        }
        hookedTicks = 0;

        if (hook.isTouchingWater() || isWater(hook.getBlockPos())) hookTouchedWater = true;

        if (!blockedSpots.isEmpty() && !verifySpot && !needNewSpot && hook.getId() != reeledHookId && hookAge >= 10 && hook.isTouchingWater()) {
            if (nearBlockedSpot(hook.getEntityPos())) {
                if (blockedRecasts < MAX_BLOCKED_RECASTS && tickCounter - lastReelTick >= CAST_COOLDOWN_TICKS) {
                    blockedRecasts++;
                    if (debug.get() && !silent.get()) info("Bobber landed on a blocked spot, casting again.");
                    lastReelTick = tickCounter;
                    lastCastTick = tickCounter;
                    useRod();
                    return;
                }
            } else {
                blockedRecasts = 0;
            }
        }

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

    public enum RecoveryState {
        IDLE, FISHING, DISCONNECTED, RECONNECTING, WAITING_FOR_WORLD,
        NAVIGATING, SEARCHING_FOR_BOAT, ENTERING_BOAT, RESUMING_FISHING, ERROR
    }

    private record Msg(int level, String text) {}

    private static final long STABLE_SESSION_MS = 30_000;
    private static final long MAX_RECONNECT_BACKOFF_MS = 120_000;
    private static final long CONNECT_HARD_TIMEOUT_MS = 90_000;
    private static final long CONNECT_SCREEN_GRACE_MS = 1_500;
    private static final long WORLD_WAIT_TIMEOUT_MS = 60_000;
    private static final int WORLD_SETTLE_TICKS = 40;
    private static final double ARRIVAL_HORIZONTAL = 1.5;
    private static final double ARRIVAL_VERTICAL = 2.0;
    private static final double BOAT_REACH = 4.0;
    private static final long ENTER_WAIT_MS = 1_000;
    private static final int MAX_ENTER_ATTEMPTS = 4;
    private static final long BOAT_APPROACH_TIMEOUT_MS = 20_000;
    private static final long BOAT_BLACKLIST_MS = 60_000;
    private static final long REPEAT_MESSAGE_MS = 30_000;
    private static final int RESUME_SETTLE_TICKS = 10;

    private RecoveryState state = RecoveryState.IDLE;
    private long stateSince;
    private long nextActionAt;
    private int recoveryTicks;

    private ServerInfo lastServerInfo;
    private ServerAddress lastServerAddress;
    private int reconnectFailures;
    private long joinedAt;
    private boolean manualCheckPending;
    private boolean announceDisconnect;
    private int leftTick;
    private boolean connectIssued;
    private long connectIssuedAt;

    private int worldReadyTicks;
    private boolean manualWalking;
    private long navStartedAt;
    private boolean recording;
    private boolean recordingNb;
    private final List<double[]> recordedPath = new ArrayList<>();
    private boolean recordJumpHeld;
    private List<double[]> route;
    private int routeIdx;
    private long routeProgressAt;
    private Vec3d routeProgressPos = Vec3d.ZERO;
    private int routeStuck;

    private long boatPhaseStartedAt;
    private int targetBoatId = -1;
    private long targetStartedAt;
    private int enterAttempts;
    private int approachIssues;
    private int approachFailures;
    private final Map<Integer, Long> boatBlacklist = new HashMap<>();
    private long lastRepeatMessageAt;
    private int resumeTicks;
    private int noWorldTicks;
    private boolean noBoatResume;

    private final ArrayDeque<Msg> pendingMessages = new ArrayDeque<>();

    private void setState(RecoveryState next) {
        if (state == next) return;
        SixToolsAddon.LOG.info("[AutoFish recovery] {} -> {}", state, next);
        state = next;
        stateSince = System.currentTimeMillis();
        if (pendingMessages.size() >= 20) pendingMessages.pollFirst();
        pendingMessages.addLast(new Msg(0, "Recovery state: " + next));
        flushMessages();
    }

    private static boolean isBoat(Entity entity) {
        return entity instanceof BoatEntity || entity instanceof ChestBoatEntity;
    }

    private static boolean boatFree(Entity boat) {
        List<Entity> riders = boat.getPassengerList();
        for (Entity rider : riders) {
            if (rider instanceof PlayerEntity) return false;
        }
        return riders.size() < (boat instanceof ChestBoatEntity ? 1 : 2);
    }

    private void notifyUser(int level, String text) {
        SixToolsAddon.LOG.info("[AutoFish recovery] {}", text);
        if (silent.get() || !notifyRecovery.get()) return;
        if (pendingMessages.size() >= 20) pendingMessages.pollFirst();
        pendingMessages.addLast(new Msg(level, text));
        flushMessages();
    }

    private void notifyRepeating(int level, String text, long now) {
        if (now - lastRepeatMessageAt < REPEAT_MESSAGE_MS) return;
        lastRepeatMessageAt = now;
        notifyUser(level, text);
    }

    private void flushMessages() {
        if (pendingMessages.isEmpty() || mc.world == null || mc.player == null) return;
        Msg message;
        while ((message = pendingMessages.pollFirst()) != null) {
            switch (message.level()) {
                case 2 -> error("%s", message.text());
                case 1 -> warning("%s", message.text());
                default -> info("%s", message.text());
            }
        }
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        blockedSpots.clear();
        playerHooks = 0;
        randomForced = false;
        blockedHookId = -1;
        blockedRecasts = 0;
        if (!isActive()) return;
        joinedAt = System.currentTimeMillis();
        captureServer();
        switch (state) {
            case DISCONNECTED, RECONNECTING -> {
                notifyUser(0, "Reconnection successful.");
                beginWaitingForWorld();
            }
            case WAITING_FOR_WORLD -> { }
            case NAVIGATING, SEARCHING_FOR_BOAT, ENTERING_BOAT, RESUMING_FISHING -> beginWaitingForWorld();
            case IDLE, FISHING -> {
                resetFishState();
                setState(RecoveryState.FISHING);
            }
            case ERROR -> { }
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (!isActive()) return;
        handleDisconnect();
    }

    private void handleDisconnect() {

        if (state == RecoveryState.IDLE || state == RecoveryState.DISCONNECTED || state == RecoveryState.RECONNECTING) return;

        long now = System.currentTimeMillis();
        cancelOwnNav();
        stopMove();
        pendingBlocks = 0;
        targetBoatId = -1;

        if (!autoReconnect.get()) {
            setState(RecoveryState.IDLE);
            return;
        }
        if (lastServerInfo == null || lastServerAddress == null) {
            notifyUser(1, "Disconnected, but there is no multiplayer server to reconnect to (single player or unknown server).");
            setState(RecoveryState.IDLE);
            return;
        }

        boolean quickDrop = joinedAt != 0 && now - joinedAt < STABLE_SESSION_MS;
        reconnectFailures = quickDrop ? reconnectFailures + 1 : 0;
        joinedAt = 0;
        int max = maxReconnectAttempts.get();
        if (max > 0 && reconnectFailures >= max) {
            fatal("Disconnected " + reconnectFailures + " times in a row shortly after joining.");
            return;
        }

        setState(RecoveryState.DISCONNECTED);
        nextActionAt = now + backoffMs();
        manualCheckPending = ignoreManualDisconnect.get();
        announceDisconnect = true;
        leftTick = recoveryTicks;
        if (!manualCheckPending) announceDisconnect(now);
    }

    private void announceDisconnect(long now) {
        announceDisconnect = false;
        long seconds = Math.max(0, (nextActionAt - now + 999) / 1000);
        notifyUser(1, "Disconnected - reconnecting in " + seconds + "s.");
    }

    private long backoffMs() {
        long base = reconnectDelay.get() * 1000L;
        long cap = Math.max(base, MAX_RECONNECT_BACKOFF_MS);
        return Math.min(cap, base << Math.min(reconnectFailures, 5));
    }

    private void captureServer() {
        ServerInfo info = mc.getCurrentServerEntry();
        if (info == null) return;
        try {
            lastServerAddress = ServerAddress.parse(info.address);
            lastServerInfo = info;
        } catch (Throwable t) {
            gamerguy11.sixtoolsaddon.utils.Swallowed.log("SixToolsAutoFish:captureServer", t);
        }
    }

    private void beginWaitingForWorld() {
        cancelOwnNav();
        targetBoatId = -1;
        boatBlacklist.clear();
        noBoatResume = false;
        worldReadyTicks = 0;
        navStartedAt = 0;
        stopMove();
        pendingBlocks = 0;
        setState(RecoveryState.WAITING_FOR_WORLD);
    }

    private void fatal(String reason) {
        cancelOwnNav();
        targetBoatId = -1;
        notifyUser(2, reason + " Auto Fish recovery stopped; fishing stays paused until you toggle the module or the next disconnect.");
        setState(RecoveryState.ERROR);
    }

    private void resetRecovery() {
        cancelOwnNav();
        pendingMessages.clear();
        boatBlacklist.clear();
        targetBoatId = -1;
        reconnectFailures = 0;
        joinedAt = 0;
        manualCheckPending = false;
        announceDisconnect = false;
        connectIssued = false;
        navStartedAt = 0;
        recoveryTicks = 0;
        setState(RecoveryState.IDLE);
    }

    private void cancelOwnNav() {
        route = null;
        stopManualWalk();
    }

    private void walkToward(Entity target) {
        walkToward(target.getEntityPos(), false);
    }

    public static volatile boolean forceWalk;
    public static volatile boolean forceJump;

    private void walkToward(Vec3d to, boolean forceJump) {
        Vec3d from = mc.player.getEntityPos();
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        mc.player.setYaw((float) Math.toDegrees(-Math.atan2(dx, dz)));
        boolean grounded = mc.player.isOnGround();
        SixToolsAutoFish.forceJump = (mc.player.horizontalCollision && grounded) || mc.player.isTouchingWater() || forceJump;
        SixToolsAutoFish.forceWalk = true;
        manualWalking = true;
    }

    private void stopManualWalk() {
        manualWalking = false;
        forceWalk = false;
        forceJump = false;
    }

    private static final Path PATH_FILE_NORMAL = FabricLoader.getInstance().getConfigDir().resolve("sixtoolsaddon").resolve("AutoFishPath.txt");
    private static final Path PATH_FILE_NO_BOATS = FabricLoader.getInstance().getConfigDir().resolve("sixtoolsaddon").resolve("AutoFishPathNoBoats.txt");

    private static Path pathFile(boolean nb) {
        return nb ? PATH_FILE_NO_BOATS : PATH_FILE_NORMAL;
    }
    private static final double RECORD_STEP = 0.35;
    private static final double ROUTE_REACH = 0.6;
    private static final long ROUTE_STALL_MS = 4_000;

    private void startRecording(boolean nb) {
        if (mc.player == null) {
            error("Join a world first: recording follows where you walk.");
            return;
        }
        recordedPath.clear();
        recordJumpHeld = false;
        recording = true;
        recordingNb = nb;
        info("Recording started - walk to your fishing spot, then press Stop recording.");
    }

    private void stopRecording() {
        if (!recording) return;
        recording = false;
        if (recordedPath.size() < 2) {
            warning("Recording too short - nothing saved.");
            return;
        }
        savePath(recordedPath, recordingNb);
        double[] last = recordedPath.get(recordedPath.size() - 1);
        destSetting(recordingNb).set(BlockPos.ofFloored(last[0], last[1], last[2]));
        destSetSetting(recordingNb).set(true);
        info("Path saved with %d points. After a reconnect it will be walked again.", recordedPath.size());
    }

    private void tickRecording() {
        if (!recording || mc.player == null || mc.world == null || mc.options == null) return;
        Vec3d p = mc.player.getEntityPos();
        boolean jumpNow = mc.options.jumpKey.isPressed();
        boolean changed = jumpNow != recordJumpHeld;
        recordJumpHeld = jumpNow;
        double j = jumpNow ? 1 : 0;
        if (recordedPath.isEmpty()) {
            recordedPath.add(new double[]{p.x, p.y, p.z, j});
            return;
        }
        double[] last = recordedPath.get(recordedPath.size() - 1);
        double moved = Math.sqrt((p.x - last[0]) * (p.x - last[0]) + (p.y - last[1]) * (p.y - last[1]) + (p.z - last[2]) * (p.z - last[2]));
        if (changed || moved >= RECORD_STEP) recordedPath.add(new double[]{p.x, p.y, p.z, j});
    }

    private void savePath(List<double[]> points, boolean nb) {
        try {
            Files.createDirectories(pathFile(nb).getParent());
            StringBuilder sb = new StringBuilder();
            for (double[] pt : points) sb.append(pt[0]).append(' ').append(pt[1]).append(' ').append(pt[2]).append(' ').append((int) pt[3]).append('\n');
            Files.writeString(pathFile(nb), sb.toString());
        } catch (IOException e) {
            error("Failed to save path: %s", e.getMessage());
        }
    }

    private List<double[]> loadPath(boolean nb) {
        List<double[]> points = new ArrayList<>();
        try {
            if (!Files.exists(pathFile(nb))) return points;
            for (String line : Files.readAllLines(pathFile(nb))) {
                String[] parts = line.trim().split(" ");
                if (parts.length < 4) continue;
                points.add(new double[]{Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3])});
            }
        } catch (IOException | NumberFormatException e) {
            points.clear();
        }
        return points;
    }

    private void clearPath(boolean nb) {
        recording = false;
        recordedPath.clear();
        try {
            Files.deleteIfExists(pathFile(nb));
        } catch (IOException e) {
            error("Failed to delete path: %s", e.getMessage());
        }
    }

    private boolean pathSaved(boolean nb) {
        return Files.exists(pathFile(nb));
    }

    private void buildRoute(long now) {
        List<double[]> points = loadPath(noBoatsMode());
        if (points.size() < 2) {
            BlockPos d = destination.get();
            points = new ArrayList<>();
            points.add(new double[]{d.getX() + 0.5, d.getY(), d.getZ() + 0.5, 0});
        }
        route = points;
        routeIdx = 0;
        routeProgressAt = now;
        routeProgressPos = mc.player.getEntityPos();
        routeStuck = 0;
        double[] first = points.get(routeIdx);
        info("Route: %d points, starting at point %d, %.1f blocks away.", points.size(), routeIdx, Math.hypot(mc.player.getX() - first[0], mc.player.getZ() - first[2]));
    }

    private void tickRecovery() {
        long now = System.currentTimeMillis();
        switch (state) {
            case IDLE, ERROR -> { }
            case FISHING -> {
                if (noWorldDisconnect()) return;
                if (reconnectFailures > 0 && joinedAt != 0 && now - joinedAt >= STABLE_SESSION_MS) reconnectFailures = 0;
            }
            case DISCONNECTED -> { recoveryTicks++; tickDisconnected(now); }
            case RECONNECTING -> { recoveryTicks++; tickReconnecting(now); }
            case WAITING_FOR_WORLD -> { recoveryTicks++; if (!noWorldDisconnect()) tickWaitingForWorld(now); }
            case NAVIGATING -> { recoveryTicks++; if (!noWorldDisconnect()) tickNavigating(now); }
            case SEARCHING_FOR_BOAT -> { recoveryTicks++; if (!noWorldDisconnect()) tickSearching(now); }
            case ENTERING_BOAT -> { recoveryTicks++; if (!noWorldDisconnect()) tickEnteringBoat(now); }
            case RESUMING_FISHING -> { recoveryTicks++; if (!noWorldDisconnect()) tickResuming(); }
        }
    }

    private boolean noWorldDisconnect() {
        if (mc.world != null || mc.player != null) {
            noWorldTicks = 0;
            return false;
        }
        if (++noWorldTicks < 5) return false;
        noWorldTicks = 0;
        SixToolsAddon.LOG.info("[AutoFish recovery] world vanished without a leave event, starting recovery");
        handleDisconnect();
        return true;
    }

    private void tickDisconnected(long now) {
        if (manualCheckPending) {
            if (recoveryTicks - leftTick < 3) return;
            manualCheckPending = false;

            Screen screen = mc.currentScreen;
            if (screen instanceof TitleScreen || (screen != null && screen.getClass().getSimpleName().equals("MultiplayerScreen"))) {
                notifyUser(0, "You disconnected manually - not reconnecting. Rejoin to continue fishing normally.");
                announceDisconnect = false;
                setState(RecoveryState.IDLE);
                return;
            }
        }
        if (announceDisconnect) announceDisconnect(now);
        if (now < nextActionAt) return;
        setState(RecoveryState.RECONNECTING);
        connectIssued = false;
        connectIssuedAt = now;
    }

    private void tickReconnecting(long now) {
        if (!connectIssued) {
            if (mc.world != null && mc.player != null) return;
            if (!(mc.currentScreen instanceof ConnectScreen)) {
                if (lastServerInfo == null || lastServerAddress == null) {
                    onReconnectFailed("no saved server details", now);
                    return;
                }
                try {
                    ConnectScreen.connect(new TitleScreen(), mc, lastServerAddress, lastServerInfo, false, null);
                } catch (Throwable t) {
                    gamerguy11.sixtoolsaddon.utils.Swallowed.log("SixToolsAutoFish:reconnect", t);
                    onReconnectFailed("could not start the connection", now);
                    return;
                }
            }
            connectIssued = true;
            connectIssuedAt = now;
            notifyUser(0, "Reconnecting to " + lastServerInfo.address + "...");
            return;
        }

        if (mc.currentScreen instanceof ConnectScreen) {
            if (now - connectIssuedAt > CONNECT_HARD_TIMEOUT_MS) {
                mc.setScreen(new TitleScreen());
                onReconnectFailed("timed out", now);
            }
            return;
        }

        if (now - connectIssuedAt > CONNECT_SCREEN_GRACE_MS && (mc.world == null || mc.player == null)) {
            onReconnectFailed("connection failed", now);
        }
    }

    private void onReconnectFailed(String reason, long now) {
        reconnectFailures++;
        connectIssued = false;
        int max = maxReconnectAttempts.get();
        if (max > 0 && reconnectFailures >= max) {
            fatal("Reconnection failed " + reconnectFailures + " times (" + reason + ").");
            return;
        }
        nextActionAt = now + backoffMs();
        manualCheckPending = false;
        announceDisconnect = false;
        setState(RecoveryState.DISCONNECTED);
        notifyUser(1, "Reconnection failed (" + reason + "); retrying in " + Math.max(0, (nextActionAt - now) / 1000) + "s"
                + (max > 0 ? " (attempt " + (reconnectFailures + 1) + "/" + max + ")." : "."));
    }

    private boolean worldReady() {
        if (mc.player == null || mc.world == null || !mc.player.isAlive()) return false;
        if (mc.currentScreen instanceof LevelLoadingScreen) return false;
        return mc.world.getChunkManager().isChunkLoaded(mc.player.getBlockX() >> 4, mc.player.getBlockZ() >> 4);
    }

    private void tickWaitingForWorld(long now) {
        if (!worldReady()) {
            worldReadyTicks = 0;
            if (now - stateSince > WORLD_WAIT_TIMEOUT_MS) fatal("The world did not finish loading after reconnecting.");
            return;
        }
        if (++worldReadyTicks < WORLD_SETTLE_TICKS) return;

        Entity ride = mc.player.getVehicle();
        if (!noBoatsMode() && ride != null && isBoat(ride)) {
            notifyUser(0, "Already riding a boat - resuming fishing.");
            resumeTicks = 0;
            setState(RecoveryState.RESUMING_FISHING);
            return;
        }
        boolean nb = noBoatsMode();
        if (!destinationSet.get() && !pathSaved(nb)) {
            if (nb) {
                notifyUser(1, "No no-boats path recorded and no no-boats destination configured - skipping navigation and fishing from here.");
                resumeWithoutBoat();
            } else {
                notifyUser(1, "No path recorded and no destination configured - skipping navigation and searching for a boat near your current position.");
                beginBoatPhase(now);
            }
            return;
        }
        navStartedAt = now;
        route = null;
        notifyUser(0, pathSaved(nb) ? "Walking the recorded path." : "Walking to the saved position " + formatPos(destination.get()) + ".");
        setState(RecoveryState.NAVIGATING);
    }

    private boolean atDestination() {
        BlockPos d = destination.get();
        double dx = mc.player.getX() - (d.getX() + 0.5);
        double dz = mc.player.getZ() - (d.getZ() + 0.5);
        return Math.hypot(dx, dz) <= ARRIVAL_HORIZONTAL && Math.abs(mc.player.getY() - d.getY()) <= ARRIVAL_VERTICAL;
    }

    private void tickNavigating(long now) {
        if (mc.player == null || mc.world == null) return;
        if (atDestination()) {
            cancelOwnNav();
            if (noBoatsMode()) {
                notifyUser(0, "Destination reached - resuming fishing.");
                resumeWithoutBoat();
                return;
            }
            notifyUser(0, "Destination reached - searching for a boat.");
            beginBoatPhase(now);
            return;
        }
        int timeout = navigationTimeout.get();
        if (timeout > 0 && now - navStartedAt > timeout * 1000L) {
            fatal("Navigation timed out after " + timeout + "s.");
            return;
        }
        if (route == null) buildRoute(now);
        Vec3d p = mc.player.getEntityPos();
        while (routeIdx < route.size() - 1) {
            double[] c = route.get(routeIdx);
            if (Math.hypot(p.x - c[0], p.z - c[2]) < ROUTE_REACH && Math.abs(p.y - c[1]) < 1.6) routeIdx++;
            else break;
        }
        if (mc.player.hasVehicle()) mc.player.stopRiding();
        double[] target = route.get(routeIdx);
        boolean nearJump = target[3] > 0.5;
        walkToward(new Vec3d(target[0], target[1], target[2]), nearJump);
        if (now - routeProgressAt >= ROUTE_STALL_MS) {
            boolean stuck = p.squaredDistanceTo(routeProgressPos) < 0.25;
            routeProgressAt = now;
            routeProgressPos = p;
            if (stuck) {
                routeStuck++;
                SixToolsAddon.LOG.info("[AutoFish recovery] stuck #{} pos={} target={} idx={}/{} screen={} vehicle={}", routeStuck, p, java.util.Arrays.toString(target), routeIdx, route.size(), mc.currentScreen, mc.player.getVehicle());
                if (routeStuck >= 6) {
                    fatal("Got stuck while walking back to the saved position.");
                    return;
                }
                if (routeIdx < route.size() - 1) routeIdx++;
                forceJump = true;
            } else {
                routeStuck = 0;
            }
        }
    }

    private void resumeWithoutBoat() {
        cancelOwnNav();
        targetBoatId = -1;
        noBoatResume = true;
        resumeTicks = 0;
        setState(RecoveryState.RESUMING_FISHING);
    }

    private void beginBoatPhase(long now) {
        boatPhaseStartedAt = now;
        noBoatResume = false;
        boatBlacklist.clear();
        targetBoatId = -1;
        nextActionAt = 0;
        setState(RecoveryState.SEARCHING_FOR_BOAT);
    }

    private boolean boatPhaseTimedOut(long now) {
        int timeout = boatTimeout.get();
        if (timeout > 0 && now - boatPhaseStartedAt > timeout * 1000L) {
            if (!fishWithoutBoat.get()) {
                fatal("No boat could be entered within " + timeout + "s.");
                return true;
            }
            cancelOwnNav();
            targetBoatId = -1;
            noBoatResume = true;
            resumeTicks = 0;
            notifyUser(1, "No empty boat could be entered - fishing from the saved position without one.");
            setState(RecoveryState.RESUMING_FISHING);
            return true;
        }
        return false;
    }

    private void tickSearching(long now) {
        if (mc.player == null || mc.world == null) return;
        Entity ride = mc.player.getVehicle();
        if (ride != null && isBoat(ride)) {
            boatEntered();
            return;
        }
        if (boatPhaseTimedOut(now) || now < nextActionAt) return;
        nextActionAt = now + searchIntervalSeconds.get() * 1000L;

        Entity boat = findBoat(now);
        if (boat == null) {
            notifyRepeating(0, "No suitable boat found - continuing search.", now);
            return;
        }
        targetBoatId = boat.getId();
        targetStartedAt = now;
        enterAttempts = 0;
        approachIssues = 0;
        approachFailures = 0;
        nextActionAt = 0;
        notifyUser(0, "Boat found - attempting to enter.");
        setState(RecoveryState.ENTERING_BOAT);
    }

    private Entity findBoat(long now) {
        double radius = boatSearchRadius.get();
        double radiusSq = radius * radius;
        boatBlacklist.values().removeIf(until -> until <= now);
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : mc.world.getEntities()) {
            if (!isBoat(entity) || entity.isRemoved() || !boatFree(entity)) continue;
            if (boatBlacklist.containsKey(entity.getId())) continue;
            double distance = entity.squaredDistanceTo(mc.player);
            if (distance > radiusSq || distance >= bestDistance) continue;
            best = entity;
            bestDistance = distance;
        }
        return best;
    }

    private double reachDistance(Entity entity) {
        Box box = entity.getBoundingBox();
        Vec3d eye = mc.player.getEyePos();
        double dx = Math.max(Math.max(box.minX - eye.x, 0.0), eye.x - box.maxX);
        double dy = Math.max(Math.max(box.minY - eye.y, 0.0), eye.y - box.maxY);
        double dz = Math.max(Math.max(box.minZ - eye.z, 0.0), eye.z - box.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private void dropTarget(boolean blacklist, long now) {
        if (blacklist && targetBoatId != -1) boatBlacklist.put(targetBoatId, now + BOAT_BLACKLIST_MS);
        cancelOwnNav();
        targetBoatId = -1;
        nextActionAt = now + searchIntervalSeconds.get() * 1000L;
        if (blacklist) notifyRepeating(1, "Could not enter that boat - searching for another one.", now);
        setState(RecoveryState.SEARCHING_FOR_BOAT);
    }

    private void tickEnteringBoat(long now) {
        if (mc.player == null || mc.world == null) return;
        Entity ride = mc.player.getVehicle();
        if (ride != null && isBoat(ride)) {
            boatEntered();
            return;
        }
        if (boatPhaseTimedOut(now)) return;

        Entity boat = mc.world.getEntityById(targetBoatId);
        if (boat == null || boat.isRemoved() || !boatFree(boat)) {
            dropTarget(false, now);
            return;
        }
        if (now < nextActionAt) return;

        if (reachDistance(boat) <= BOAT_REACH) {
            cancelOwnNav();
            if (enterAttempts >= MAX_ENTER_ATTEMPTS) {
                dropTarget(true, now);
                return;
            }
            enterAttempts++;
            Rotations.rotate(Rotations.getYaw(boat), Rotations.getPitch(boat), -100, () -> {
                net.minecraft.util.hit.EntityHitResult location = new net.minecraft.util.hit.EntityHitResult(boat, boat.getBoundingBox().getCenter());
                mc.interactionManager.interactEntityAtLocation(mc.player, boat, location, Hand.MAIN_HAND);
                mc.interactionManager.interactEntity(mc.player, boat, Hand.MAIN_HAND);
            });
            nextActionAt = now + ENTER_WAIT_MS;
            return;
        }

        if (now - targetStartedAt > BOAT_APPROACH_TIMEOUT_MS) {
            dropTarget(true, now);
            return;
        }
        walkToward(boat);
    }

    private void boatEntered() {
        cancelOwnNav();
        targetBoatId = -1;
        notifyUser(0, "Boat entered - resuming fishing.");
        noBoatResume = false;
        resumeTicks = 0;
        setState(RecoveryState.RESUMING_FISHING);
    }

    private void tickResuming() {
        if (mc.player == null || mc.world == null) return;
        Entity ride = mc.player.getVehicle();
        if (!noBoatResume && !noBoatsMode() && (ride == null || !isBoat(ride))) {
            beginBoatPhase(System.currentTimeMillis());
            return;
        }
        if (++resumeTicks < RESUME_SETTLE_TICKS) return;
        resetFishState();
        setState(RecoveryState.FISHING);
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private String destinationText(boolean nb) {
        String label = nb ? "No-boats recovery" : "Recovery";
        return pathSaved(nb) ? label + ": recorded path (" + loadPath(nb).size() + " points)"
                : destSetSetting(nb).get() ? label + " position: " + formatPos(destSetting(nb).get()) : label + " position: not set";
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