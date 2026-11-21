

package gamerguy11.sixtoolsaddon.modules;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.meteor.KeyEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.ChestSwap;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import gamerguy11.sixtoolsaddon.efly.autonav.AutonavPlanner;
import gamerguy11.sixtoolsaddon.efly.autonav.ButtonSetting;
import gamerguy11.sixtoolsaddon.efly.autonav.XaeroMapClicks;
import meteordevelopment.orbit.EventHandler;
import org.lwjgl.glfw.GLFW;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.ParrotEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.WitherSkullEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Formatting;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLongArray;

public class Efly extends Module {
    private final SettingGroup sgAutopilot = settings.createGroup("Autopilot");
    private final SettingGroup sgAutohighway = settings.createGroup("Autohighway");
    private final SettingGroup sgAutonav = settings.createGroup("Autonav");
    private final SettingGroup sgOverdrive = settings.createGroup("Overdrive");
    private final SettingGroup sgMapping = settings.createGroup("Mapping");
    private final SettingGroup sgBuildingMode = settings.createGroup("Building Mode");
    private final SettingGroup sgBypassMode = settings.createGroup("Bypass Mode");
    private final SettingGroup sgPlayerAvoidance = settings.createGroup("Player Avoidance System");
    private final SettingGroup sgLanding = settings.createGroup("Landing");
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgInventory = settings.createGroup("Inventory");
    private final SettingGroup sgKeybinds = settings.createGroup("Keybinds");

    public final Setting<Boolean> mappingMode = sgMapping.add(new BoolSetting.Builder()
        .name("mapping-mode")
        .description("Pauses the player's horizontal movement until nearby chunks are loaded")
        .defaultValue(false)
        .build()
    );

    public final Setting<Integer> mappingRenderRadius = sgMapping.add(new IntSetting.Builder()
        .name("render-radius")
        .description("How many chunks around you need to load before you start moving again")
        .defaultValue(9)
        .min(0)
        .sliderMax(32)
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

    public final Setting<Boolean> bypassMode = sgBypassMode.add(new BoolSetting.Builder()
        .name("bypass-mode")
        .description("Switches out acceleration with additive flight to behave more like normal elytra flight")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> playerAvoidance = sgPlayerAvoidance.add(new BoolSetting.Builder()
        .name("player-avoidance")
        .description("Distances you from threats when they get close")
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

    public final Setting<Set<EntityType<?>>> otherAvoidedEntities = sgPlayerAvoidance.add(new EntityTypeListSetting.Builder()
        .name("other-entities")
        .description("Extra entities to avoid")
        .filter(type -> !isAvoidanceBuiltInEntity(type))
        .visible(playerAvoidance::get)
        .build()
    );

    public final Setting<Double> otherEntitiesRadius = sgPlayerAvoidance.add(new DoubleSetting.Builder()
        .name("other-entities-radius")
        .description("Distance at which the extra entities trigger avoidance")
        .defaultValue(10.0)
        .min(0)
        .sliderMax(50)
        .visible(() -> playerAvoidance.get() && !otherAvoidedEntities.get().isEmpty())
        .build()
    );

    public final Setting<Boolean> avoidBlocks = sgPlayerAvoidance.add(new BoolSetting.Builder()
        .name("avoid-blocks")
        .description("Also avoids nearby blocks while moving away from threats")
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

    public final Setting<Boolean> avoidanceLateral = sgPlayerAvoidance.add(new BoolSetting.Builder()
        .name("sidestep")
        .description("Tries to move sideways to an incoming threat rather than just away")
        .defaultValue(true)
        .visible(playerAvoidance::get)
        .build()
    );

    public final Setting<Boolean> antiSlam = sgLanding.add(new BoolSetting.Builder()
        .name("anti-slam")
        .description("Slows you down when landing to prevent fall damage")
        .defaultValue(false)
        .build()
    );

    public final Setting<Double> antiSlamDistance = sgLanding.add(new DoubleSetting.Builder()
        .name("anti-slam-distance")
        .description("Distance from the ground where slowing begins")
        .defaultValue(20.0)
        .min(0.1)
        .sliderMax(30)
        .visible(antiSlam::get)
        .build()
    );

    public final Setting<Double> antiSlamMinDistance = sgLanding.add(new DoubleSetting.Builder()
        .name("anti-slam-min-distance")
        .description("Distance from the ground where speed reaches the minimum value")
        .defaultValue(0.5)
        .min(0)
        .sliderMax(5)
        .visible(antiSlam::get)
        .build()
    );

    public final Setting<Double> antiSlamMinSpeed = sgLanding.add(new DoubleSetting.Builder()
        .name("anti-slam-min-speed")
        .description("The speed to slow down to before landing")
        .defaultValue(0.2)
        .min(0)
        .sliderMax(1)
        .visible(antiSlam::get)
        .build()
    );

    public final Setting<Double> startSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("minimum-horizontal-speed")
        .description("The speed you start at when moving horizontally (before acceleration starts)")
        .min(0)
        .defaultValue(2.999)
        .build()
    );

    public final Setting<Double> horizontalSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("maximum-horizontal-speed")
        .description("The fastest horizontal speed will go (blocks per tick)")
        .defaultValue(14.999)
        .min(0)
        .build()
    );

    public final Setting<Double> accelerationPlateau = sgGeneral.add(new DoubleSetting.Builder()
        .name("horizontal-acceleration-plateau")
        .description("The horizontal speed where acceleration will tend to 0")
        .min(0.01)
        .defaultValue(14.999)
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

    public final Setting<Double> verticalStartSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("minimum-vertical-speed")
        .description("The speed you start at when moving vertically, (before acceleration starts)")
        .min(0)
        .defaultValue(5.999)
        .build()
    );

    public final Setting<Double> verticalSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("maximum-vertical-speed")
        .description("The fastest vertical speed will go (blocks per tick)")
        .defaultValue(5.999)
        .min(0)
        .build()
    );

    public final Setting<Double> verticalAccelerationPlateau = sgGeneral.add(new DoubleSetting.Builder()
        .name("vertical-acceleration-plateau")
        .description("The vertical speed where acceleration will tend to 0")
        .min(0.01)
        .defaultValue(29.999)
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

    public final Setting<Double> fallMultiplier = sgGeneral.add(new DoubleSetting.Builder()
        .name("fall-multiplier")
        .description("Multiplier for how fast you fall naturally")
        .defaultValue(0)
        .min(0)
        .build()
    );

    public final Setting<Boolean> accelerateUpward = sgGeneral.add(new BoolSetting.Builder()
        .name("accelerate-upward")
        .description("Also accelerates upwards. Not recommended if vertical speed goes above 5.999")
        .defaultValue(false)
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

    public final Setting<Boolean> rubberbandSpeedCap = sgGeneral.add(new BoolSetting.Builder()
        .name("rubberband-speed-cap")
        .description("Checks if rubberbanding is happening frequently near this value. If it is, it'll limit your speed for a little while for smoother flight.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Double> rubberbandSpeedCapThreshold = sgGeneral.add(new DoubleSetting.Builder()
        .name("rubberband-speed-cap-threshold")
        .description("The speed you think rubberbands are happening frequently at and to limit your speed to if so")
        .defaultValue(200.0)
        .min(0)
        .sliderMax(400)
        .visible(rubberbandSpeedCap::get)
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

    public final Setting<Boolean> autohighway = sgAutohighway.add(new BoolSetting.Builder()
        .name("autohighway")
        .description("Uses a mixture of mob pathfinding and custom navigation to find highways and travel along them")
        .defaultValue(false)
        .build()
    );

    public final Setting<HighwayDirection> highwayDirection = sgAutohighway.add(new EnumSetting.Builder<HighwayDirection>()
        .name("highway-direction")
        .description("Which way to fly along the highway, relative to the world centre (0, 0)")
        .defaultValue(HighwayDirection.AwayFromCentre)
        .visible(autohighway::get)
        .build()
    );

    public final Setting<Intelligence> highwayIntelligence = sgAutohighway.add(new EnumSetting.Builder<Intelligence>()
        .name("intelligence")
        .description("How smart Autohighway is when finding paths. Higher intelligence will use more CPU power.")
        .defaultValue(Intelligence.High)
        .visible(autohighway::get)
        .build()
    );

    public final Setting<Boolean> overdrive = sgOverdrive.add(new BoolSetting.Builder()
        .name("overdrive")
        .description("Applies dark magic to force near-instant acceleration to max speed")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> autonav = sgAutonav.add(new BoolSetting.Builder()
        .name("autonav")
        .description("Input coords or click on a position in Xaero's world map and autonav will take you there!")
        .defaultValue(false)
        .build()
    );

    public final Setting<AutonavEngine> autonavEngine = sgAutonav.add(new EnumSetting.Builder<AutonavEngine>()
        .name("autonav-engine")
        .description("Classic: the built-in autonav. Pathfinder: the Goto flight brain, which plans a 3D route through loaded chunks (caves, Nether), re-plans as chunks load, lands at the target and can dig through walls. Its extra settings are in the efly-goto module.")
        .defaultValue(AutonavEngine.Classic)
        .visible(autonav::get)
        .build()
    );

    public final Setting<Boolean> autonavRecover = sgAutonav.add(new BoolSetting.Builder()
        .name("autonav-recover")
        .description("If the server rubberbands you out of flight (into a block or onto the ground) during Autonav, take off again and plan a route around the obstacle instead of stopping.")
        .defaultValue(true)
        .visible(autonav::get)
        .build()
    );

    public final Setting<Integer> autonavGotoX = sgAutonav.add(new IntSetting.Builder()
        .name("x-coords")
        .description("X coord to fly to")
        .defaultValue(0)
        .min(-30000000)
        .max(30000000)
        .noSlider()
        .visible(autonav::get)
        .build()
    );

    public final Setting<Integer> autonavGotoZ = sgAutonav.add(new IntSetting.Builder()
        .name("z-coords")
        .description("Z coord to fly to")
        .defaultValue(0)
        .min(-30000000)
        .max(30000000)
        .noSlider()
        .visible(autonav::get)
        .build()
    );

    public final ButtonSetting autonavGo = sgAutonav.add(new ButtonSetting(
        "go", "Start flying to the coords input above", "Go", autonav::get));

    public final Setting<MapClickButton> autonavButton = sgAutonav.add(new EnumSetting.Builder<MapClickButton>()
        .name("click-button")
        .description("Which button to use when selecting coords on Xaero's world map")
        .defaultValue(MapClickButton.Middle)
        .visible(autonav::get)
        .build()
    );

    public final Setting<Boolean> autonavCloseMap = sgAutonav.add(new BoolSetting.Builder()
        .name("close-map-on-click")
        .description("Closes the map once you've selected a location")
        .defaultValue(true)
        .visible(autonav::get)
        .build()
    );

    public final Setting<Intelligence> autonavIntelligence = sgAutonav.add(new EnumSetting.Builder<Intelligence>()
        .name("intelligence")
        .description("How well autonav plots its route around things. Higher intelligence uses more CPU power")
        .defaultValue(Intelligence.High)
        .visible(autonav::get)
        .build()
    );

    public final Setting<Boolean> autonavAvoidLava = sgAutonav.add(new BoolSetting.Builder()
        .name("avoid-lava")
        .description("Autonav will try to plot its way around lava")
        .defaultValue(true)
        .visible(autonav::get)
        .build()
    );

    public final Setting<Boolean> autonavAvoidWater = sgAutonav.add(new BoolSetting.Builder()
        .name("avoid-water")
        .description("Autonav will try to plot its way around water")
        .defaultValue(false)
        .visible(autonav::get)
        .build()
    );

    public final Setting<Boolean> autonavWaypoint = sgAutonav.add(new BoolSetting.Builder()
        .name("autonav-waypoint")
        .description("Puts a waypoint on your Xaero map (Meteor waypoint if Xaero is missing) at the Autonav destination and removes it when the trip ends.")
        .defaultValue(true)
        .visible(autonav::get)
        .build());

    public final Setting<Double> autonavArrivalRadius = sgAutonav.add(new DoubleSetting.Builder()
        .name("arrival-radius")
        .description("How far from the coords is considered an acceptable point to end the route")
        .defaultValue(30.0)
        .min(1.0)
        .sliderMax(50.0)
        .visible(autonav::get)
        .build()
    );

    public final Setting<Boolean> autonavPrioritiseHighways = sgAutonav.add(new BoolSetting.Builder()
        .name("prioritise-highways")
        .description("Autonav will identify highways that can get you to your location quicker")
        .defaultValue(false)
        .visible(autonav::get)
        .build()
    );

    public final Setting<Keybind> mappingModeKeybind = sgKeybinds.add(new KeybindSetting.Builder()
        .name("mapping-mode-keybind")
        .description("Key to toggle Mapping Mode")
        .defaultValue(Keybind.none())
        .build()
    );

    public final Setting<Keybind> buildingModeKeybind = sgKeybinds.add(new KeybindSetting.Builder()
        .name("building-mode-keybind")
        .description("Key to toggle Building Mode")
        .defaultValue(Keybind.none())
        .build()
    );

    public final Setting<Keybind> bypassModeKeybind = sgKeybinds.add(new KeybindSetting.Builder()
        .name("bypass-mode-keybind")
        .description("Key to toggle Bypass Mode")
        .defaultValue(Keybind.none())
        .build()
    );

    public final Setting<Keybind> playerAvoidanceKeybind = sgKeybinds.add(new KeybindSetting.Builder()
        .name("player-avoidance-keybind")
        .description("Key to toggle Player Avoidance.")
        .defaultValue(Keybind.none())
        .build()
    );

    public final Setting<Keybind> antiSlamKeybind = sgKeybinds.add(new KeybindSetting.Builder()
        .name("anti-slam-keybind")
        .description("Key to toggle anti-slam")
        .defaultValue(Keybind.none())
        .build()
    );

    public final Setting<Keybind> autoPilotKeybind = sgKeybinds.add(new KeybindSetting.Builder()
        .name("auto-pilot-keybind")
        .description("Key to toggle Autopilot.")
        .defaultValue(Keybind.none())
        .build()
    );

    public final Setting<Keybind> autohighwayKeybind = sgKeybinds.add(new KeybindSetting.Builder()
        .name("autohighway-keybind")
        .description("Key to toggle Autohighway")
        .defaultValue(Keybind.none())
        .build()
    );

    public final Setting<Keybind> overdriveKeybind = sgKeybinds.add(new KeybindSetting.Builder()
        .name("overdrive-keybind")
        .description("Key to toggle Overdrive")
        .defaultValue(Keybind.none())
        .build()
    );

    public final Setting<Keybind> autonavKeybind = sgKeybinds.add(new KeybindSetting.Builder()
        .name("autonav-keybind")
        .description("Key to toggle Autonav")
        .defaultValue(Keybind.none())
        .build()
    );

    public final Setting<Keybind> autonavCancelKeybind = sgKeybinds.add(new KeybindSetting.Builder()
        .name("cancel-keybind")
        .description("Key to cancel autonav")
        .defaultValue(Keybind.none())
        .visible(autonav::get)
        .build()
    );

    private static final double RUBBERBAND_CAP_TOLERANCE_PERCENT = 5.0;
    private static final double RUBBERBAND_CAP_DURATION_SECONDS = 10.0;

    private static final int RUBBERBAND_REWIND_TICKS = 15;

    private static final int ACCELERATION_DELAY_TICKS = 1;

    private static final int AVOIDANCE_STUCK_TICKS = 3;

    private static final double HIGHWAY_BLOCK_CLEARANCE = 0.1;

    private static final double HIGHWAY_TRUE_MARGIN = -0.001;

    private boolean lastJumpPressed;
    private boolean incrementJumpTimer;
    private boolean lastForwardPressed;
    private int jumpTimer;
    private double velX, velY, velZ;
    private double ticksLeft;
    private Vec3d forward, right;
    private double acceleration;
    private boolean atMaxSpeed;

    private Vec3d lastGlidePos;
    private double lastGlideIntendedSpeed;

    private double intendedDistanceAccum;
    private volatile double intendedDistanceLastTick;
    private int accelerationDelayTicks;
    private int bypassAccelerationDelayTicks;
    private double verticalAcceleration;
    private boolean atMaxVerticalSpeed;
    private int verticalAccelerationDelayTicks;
    private boolean mappingWaitingForChunks;

    private static final double OVERDRIVE_MULTIPLIER = 50.0;
    private static final double OVERDRIVE_OFF_FRACTION = 0.995;

    private static final double OVERDRIVE_REARM_SPEED = 2.999;

    private boolean overdriveSpent;

    private boolean overdriveExtraTick;

    private boolean inCobweb;

    private boolean glidingLastTick;

    private int reglideTicksLeft;

    private static final int REGLIDE_WINDOW_TICKS = 10;

    private boolean highwayRelaunchArmed;
    private int relaunchCooldown;
    private int relaunchPause;
    private int relaunchAttempts;
    private int relaunchGlidingTicks;

    private int unstickTicksLeft;
    private Vec3d unstickAnchor;
    private int unstickWindowTicks;
    private static final double UNSTICK_MIN_SPEED = 1.0;
    private static final double UNSTICK_RADIUS = 16.0;
    private static final int UNSTICK_WINDOW_TICKS = 60;
    private static final int UNSTICK_SLOW_TICKS = 80;

    private static final int MAP_ABOVE_LIMIT_ROOM = 32;

    private static final int RELAUNCH_RETRY_TICKS = 2;
    private static final int RELAUNCH_MAX_ATTEMPTS = 15;
    private static final int RELAUNCH_PAUSE_TICKS = 60;
    private static final int RELAUNCH_STABLE_TICKS = 20;

    private double overdriveCarry;

    private boolean bypassRamping;
    private int rubberbandCapTicksLeft;
    private double rubberbandCapSpeed;

    private static final int RUBBERBAND_CAP_TRIGGER_COUNT = 3;
    private static final long RUBBERBAND_CAP_TRIGGER_WINDOW_NANOS = 3_000_000_000L;

    private final ArrayDeque<Long> rubberbandQualifyingTimestamps = new ArrayDeque<>();

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

    private Vec3d highwayAxis;

    private double highwayCruiseY = Double.NaN;

    private ParrotEntity highwayPathMob;
    private ClientWorld highwayPathMobWorld;

    private final ArrayList<Vec3d> highwayPathNodes = new ArrayList<>();
    private int highwayPathIndex;

    private Vec3d highwayGoal;
    private int highwayTicksSinceRepath;

    private ExecutorService highwayPathExecutor;
    private HighwayPathRequest highwayPathRequest;
    private final AtomicInteger highwayPathGeneration = new AtomicInteger();

    private Vec3d highwaySteerTarget;

    private double highwayTargetY = Double.NaN;

    private Vec3d highwayLastPos;

    private static final double HIGHWAY_CENTRE_RADIUS = 1.0;
    private int highwayStuckTicks;
    private int highwayMovingTicks;
    private int highwayEscalation;
    private static final double HIGHWAY_STUCK_MOVE_EPSILON = 0.05;
    private static final double HIGHWAY_NODE_HEIGHT = 0.2;

    private static final double HIGHWAY_HARD_MARGIN = 0.02;

    private static final double HIGHWAY_HEIGHT_BAND = 0.3;

    private static final int HIGHWAY_MAX_LOOKAHEAD_NODES = 48;

    private static final int HIGHWAY_MAX_ROUTE_NODES = 96;

    private static final int HIGHWAY_HEALTHY_ROUTE_NODES = 60;
    private static final int HIGHWAY_HEALTHY_REPATH_FACTOR = 3;

    private volatile boolean autonavActive;
    private volatile int autonavDestX, autonavDestZ;

    private double mapDestX, mapDestZ, mapCruiseY;

    private double mapGoalX, mapGoalZ, mapGoalRadius;

    private static final int MAP_HIGHWAY_NONE = 0, MAP_HIGHWAY_JOINING = 1, MAP_HIGHWAY_FOLLOWING = 2;
    private int mapHighwayPhase;

    private double mapHighwayUx, mapHighwayUz, mapHighwayEntryX, mapHighwayEntryZ;

    private int mapHighwayCooldown;
    private static final double MAP_HIGHWAY_DIAGONAL = Math.sqrt(0.5);
    private static final double[][] MAP_HIGHWAY_DIRECTIONS = {
        {1, 0}, {-1, 0}, {0, 1}, {0, -1},
        {MAP_HIGHWAY_DIAGONAL, MAP_HIGHWAY_DIAGONAL}, {-MAP_HIGHWAY_DIAGONAL, -MAP_HIGHWAY_DIAGONAL},
        {MAP_HIGHWAY_DIAGONAL, -MAP_HIGHWAY_DIAGONAL}, {-MAP_HIGHWAY_DIAGONAL, MAP_HIGHWAY_DIAGONAL}
    };

    private static final int[] MAP_HIGHWAY_LINES = {0, 2, 4, 6};

    private static final double MAP_HIGHWAY_MIN_FOLLOW = 96.0;

    private static final double MAP_HIGHWAY_JOIN_DISTANCE = 4.0;

    private static final double MAP_HIGHWAY_LOOKAHEAD = 128.0;
    private static final double MAP_HIGHWAY_GOAL_RADIUS = 3.0;
    private static final int MAP_HIGHWAY_COOLDOWN_TICKS = 100;
    private static final int MAP_HIGHWAY_CHECK_TICKS = 10;

    private boolean mapDetouring;

    private int mapClearStreak;
    private static final int MAP_STRAIGHT_CONFIRM_TICKS = 3;

    private static final int MAP_STRAIGHT_CONFIRM_TICKS_AFTER_STUCK = 20;
    private final ArrayList<Vec3d> mapRouteNodes = new ArrayList<>();
    private int mapRouteIndex;
    private int mapTicksSinceRepath;

    private boolean mapNoRoute;

    private boolean mapRouteEndsAtFrontier;

    private int mapAwaitingPlanTicks;

    private static final int MAP_AWAITING_PLAN_GRACE_TICKS = 60;

    private static final double MAP_OBSTACLE_STOP_DISTANCE = 8.0;
    private ExecutorService mapRouteExecutor;
    private MapRouteRequest mapRouteRequest;
    private final AtomicInteger mapRouteGeneration = new AtomicInteger();
    private Vec3d mapLastPos;
    private int mapStuckTicks;
    private int mapMovingTicks;
    private int mapEscalation;
    private final XaeroMapClicks mapClicks = new XaeroMapClicks();

    private static final double MAP_FAT_MARGIN = 0.4;

    private static final double MAP_LAVA_MARGIN = 1.5;
    private static final double MAP_WATER_MARGIN = 0.75;

    private static final double MAP_FLUID_HARD_MARGIN = 0.15;

    private boolean mapInLava, mapInWater;

    private static final int MAP_ESCAPE_RADIUS = 16;
    private static final int MAP_ESCAPE_MAX_NODES = 30_000;
    private static final int MAP_ESCAPE_SOLID_COST = 12;
    private static final int MAP_ESCAPE_FLUID_COST = 3;

    private final AtomicInteger positionCorrections = new AtomicInteger();

    public int getPositionCorrections() {
        return positionCorrections.get();
    }

    public Efly() {
        super(SixToolsAddon.CATEGORY, "efly", "Specifically designed to maximise elytrafly capabilities and speed on 6b6t");
    }

    @Override
    public void onActivate() {
        cancelPendingActions();
        trackedWorld = mc.world;

        PlayerEntity player = mc.player;

        atMaxSpeed = false;
        lastJumpPressed = false;
        jumpTimer = 0;
        ticksLeft = 0;

        accelerationDelayTicks = 0;
        bypassAccelerationDelayTicks = 0;
        lastGlidePos = null;
        lastGlideIntendedSpeed = 0;
        intendedDistanceAccum = 0;
        intendedDistanceLastTick = 0;
        overdriveCarry = 0;
        overdriveSpent = false;
        bypassRamping = false;

        double currentHorizontalSpeed = player != null
            ? Math.hypot(player.getVelocity().x, player.getVelocity().z)
            : 0;
        acceleration = Math.max(minHorizontalSpeed(), Math.min(currentHorizontalSpeed, horizontalSpeed.get()));
        if (acceleration >= horizontalSpeed.get()) atMaxSpeed = true;

        resetVerticalAcceleration();

        rubberbandCapTicksLeft = 0;
        rubberbandQualifyingTimestamps.clear();
        unstickTicksLeft = 0;
        unstickAnchor = null;

        buildingModeEngaged = false;

        resetHighwayState();
        resetAutonavState();

        glidingLastTick = false;
        reglideTicksLeft = 0;
        inCobweb = false;

        if (player == null) return;

        if ((chestSwap.get() == ChestSwapMode.Always || chestSwap.get() == ChestSwapMode.WaitForGround)
            && player.getEquippedStack(EquipmentSlot.CHEST).getItem() != Items.ELYTRA && isActive()) {
            swapToChestSwap();
        }
    }

    @Override
    public void onDeactivate() {

        cancelPendingActions();
        mappingWaitingForChunks = false;

        if ((autoPilot.get() || autohighway.get() || autonavActive) && !gamerguy11.sixtoolsaddon.modules.ForeverForward.isHolding(mc.options.forwardKey)) mc.options.forwardKey.setPressed(false);
        releaseAvoidance();
        releaseVerticalStep();
        releaseHighwayKeys();
        resetHighwayState();
        highwayPathMob = null;
        highwayPathMobWorld = null;
        if (highwayPathExecutor != null) {
            highwayPathExecutor.shutdownNow();
            highwayPathExecutor = null;
        }
        resetAutonavState();
        if (mapRouteExecutor != null) {
            mapRouteExecutor.shutdownNow();
            mapRouteExecutor = null;
        }

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

    @SuppressWarnings("unused")
    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        cancelPendingActions();
        trackedWorld = null;

        resetAutonavState();
        resetHighwayState();
        unstickTicksLeft = 0;
        unstickAnchor = null;
        relaunchCooldown = 0;
        relaunchPause = 0;
        relaunchAttempts = 0;
        relaunchGlidingTicks = 0;
        highwayRelaunchArmed = false;
        reglideTicksLeft = 0;
    }

    @SuppressWarnings("unused")
    @EventHandler
    private void onPlayerMove(PlayerMoveEvent event) {
        if (externalControl) return;
        PlayerEntity player = mc.player;
        ClientWorld world = mc.world;

        if (world != trackedWorld) {
            if (trackedWorld != null) {
                cancelPendingActions();

                if (autonavActive) cancelAutonav();
                resetHighwayState();
            }
            trackedWorld = world;
        }
        if (player == null || world == null) return;

        if (freecamActive()) {
            zeroAcceleration();
            if (player.isGliding()) {
                player.setVelocity(0.0, 0.0, 0.0);
                ((IVec3d) event.movement).meteor$set(0.0, 0.0, 0.0);
            }
            return;
        }

        if (!(player.getEquippedStack(EquipmentSlot.CHEST).contains(DataComponentTypes.GLIDER))) return;

        autoTakeoff();
        updatePlayerAvoidance();
        inCobweb = isInCobweb(player, world);

        if (player.isGliding()) {
            boolean bypass = isBypassActive();

            bypassRamping = false;

            if (bypass) {

                double horizontalSpeedNow = Math.hypot(event.movement.x, event.movement.z);
                if (horizontalSpeedNow > 1.0E-4) {
                    double newSpeed;

                    if (bypassAccelerationDelayTicks < ACCELERATION_DELAY_TICKS) {
                        bypassAccelerationDelayTicks++;
                        newSpeed = horizontalSpeedNow;
                    } else {
                        double cap = horizontalSpeedCap();
                        newSpeed = nextSpeedFor(horizontalSpeedNow, accelerationStep.get(), accelerationPlateau.get(), cap);
                        bypassRamping = newSpeed < cap * OVERDRIVE_OFF_FRACTION;
                    }

                    double scale = newSpeed / horizontalSpeedNow;
                    velX = event.movement.x * scale;
                    velZ = event.movement.z * scale;
                } else {

                    bypassAccelerationDelayTicks = 0;
                    velX = event.movement.x;
                    velZ = event.movement.z;
                }
            } else {
                velX = 0;
                velZ = 0;
            }
            velY = event.movement.y;

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

            if (!bypass) {
                resetRampOnCollision(player);
                handleAcceleration();
                handleHorizontalSpeed();
            }
            applyHighwaySteering();

            if (!overdriveExtraTick) handleVerticalAcceleration();
            handleVerticalSpeed();
            limitHighwayVerticalStep();
            applyHighwaySlopeCoupling();
            handleAntiSlam();
            handleBuildingMode();
            handleMaxHeight();
            applyHighwayClearanceGuard(player);

            if (overdriveExtraTick) velY = 0;

            handleMappingMode();

            int chunkX = (int) Math.floor((player.getX() + velX) / 16.0);
            int chunkZ = (int) Math.floor((player.getZ() + velZ) / 16.0);
            if (dontGoIntoUnloadedChunks.get() && !world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {

                ((IVec3d) event.movement).meteor$set(0, velY, 0);
            } else {
                ((IVec3d) event.movement).meteor$set(velX, velY, velZ);
            }
        } else {
            mappingWaitingForChunks = false;
            bypassRamping = false;

            if (lastForwardPressed && !gamerguy11.sixtoolsaddon.modules.ForeverForward.isHolding(mc.options.forwardKey)) {
                mc.options.forwardKey.setPressed(false);
                lastForwardPressed = false;
            }
        }

        if (noCrash.get() && player.isGliding()) {
            Vec3d lookAheadPos = player.getEntityPos().add(player.getVelocity().normalize().multiply(crashLookAhead.get()));
            RaycastContext raycastContext = new RaycastContext(player.getEntityPos(), new Vec3d(lookAheadPos.x, player.getY(), lookAheadPos.z), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player);
            BlockHitResult hitResult = world.raycast(raycastContext);
            if (hitResult.getType() == HitResult.Type.BLOCK) {
                ((IVec3d) event.movement).meteor$set(0, velY, 0);
            }
        }

        if (player.isGliding()) {
            lastGlidePos = player.getEntityPos();
            lastGlideIntendedSpeed = Math.hypot(event.movement.x, event.movement.z);
            intendedDistanceAccum += lastGlideIntendedSpeed;
        } else {
            lastGlidePos = null;
            lastGlideIntendedSpeed = 0;
        }

    }

    @SuppressWarnings("unused")
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (externalControl) return;
        if (rubberbandCapTicksLeft > 0) rubberbandCapTicksLeft--;

        pollMapClick();
        if (mc.player != null && mc.world != null) autonavRecoverIfDown(mc.player);

        if (autoReplenish.get()) {
            FindItemResult fireworks = InvUtils.find(Items.FIREWORK_ROCKET);

            if (fireworks.found() && !fireworks.isHotbar()) {
                InvUtils.move().from(fireworks.slot()).toHotbar(replenishSlot.get() - 1);
            }
        }

        PlayerEntity player = mc.player;
        if (player != null) {
            boolean glidingNow = player.isGliding();

            boolean wasFlying = glidingNow || glidingLastTick;

            if (replace.get()) {
                var chestStack = player.getEquippedStack(EquipmentSlot.CHEST);
                boolean wearingElytra = chestStack.getItem() == Items.ELYTRA;

                boolean needsReplacing = wearingElytra
                    ? chestStack.getMaxDamage() - chestStack.getDamage() <= replaceDurability.get()
                    : wasFlying && chestStack.isEmpty();

                if (needsReplacing) {
                    FindItemResult elytra = InvUtils.find(stack -> stack.getMaxDamage() - stack.getDamage() > replaceDurability.get() && stack.getItem() == Items.ELYTRA);

                    if (elytra.found()) {
                        InvUtils.move().from(elytra.slot()).toArmor(2);

                        if (wasFlying) reglideTicksLeft = REGLIDE_WINDOW_TICKS;
                    }
                }
            }

            continueGlideAfterSwap(player);
            relaunchAfterLanding(player);
            updateUnstick(player);
            glidingLastTick = player.isGliding();
        }

        runOverdrive();

        intendedDistanceLastTick = intendedDistanceAccum;
        intendedDistanceAccum = 0;
    }

    private double minHorizontalSpeed() {
        return unstickTicksLeft > 0 ? Math.min(UNSTICK_MIN_SPEED, startSpeed.get()) : startSpeed.get();
    }

    private double minVerticalSpeed() {
        return unstickTicksLeft > 0 ? Math.min(UNSTICK_MIN_SPEED, verticalStartSpeed.get()) : verticalStartSpeed.get();
    }

    private void updateUnstick(PlayerEntity player) {
        if (unstickTicksLeft > 0) {
            unstickTicksLeft--;
            unstickAnchor = null;
            return;
        }

        boolean autonavFlying = autonavActive && autonav.get()
            && Math.hypot(mapDestX - player.getX(), mapDestZ - player.getZ()) > autonavArrivalRadius.get();
        if (!player.isGliding() || !(autonavFlying || autohighway.get())) {
            unstickAnchor = null;
            return;
        }

        Vec3d pos = player.getEntityPos();
        if (unstickAnchor == null || pos.distanceTo(unstickAnchor) > UNSTICK_RADIUS) {
            unstickAnchor = pos;
            unstickWindowTicks = 0;
            return;
        }

        if (++unstickWindowTicks < UNSTICK_WINDOW_TICKS) return;

        unstickAnchor = pos;
        unstickWindowTicks = 0;
        unstickTicksLeft = UNSTICK_SLOW_TICKS;
        zeroAcceleration();
    }

    private double flightCeiling() {
        return limitMaxHeight.get() ? maxHeight.get() : Double.POSITIVE_INFINITY;
    }

    private int flightCeilingBlock() {
        return limitMaxHeight.get() ? (int) Math.floor(maxHeight.get()) : 100_000;
    }

    private void relaunchAfterLanding(PlayerEntity player) {
        if (relaunchCooldown > 0) relaunchCooldown--;
        if (relaunchPause > 0) relaunchPause--;

        if (!autohighway.get()) highwayRelaunchArmed = false;

        if (player.isGliding()) {

            if (++relaunchGlidingTicks >= RELAUNCH_STABLE_TICKS) relaunchAttempts = 0;

            if (autohighway.get()) highwayRelaunchArmed = true;
            return;
        }
        relaunchGlidingTicks = 0;

        boolean autonavMode = autonavActive && autonav.get();
        boolean highwayMode = autohighway.get() && highwayRelaunchArmed;

        if ((!autonavMode && !highwayMode) || mc.world == null || mc.currentScreen != null || mc.isPaused()) {
            relaunchAttempts = 0;
            return;
        }
        if (!player.isAlive() || player.hasVehicle() || player.getAbilities().flying
            || player.isTouchingWater() || player.isInLava()
            || !player.getEquippedStack(EquipmentSlot.CHEST).contains(DataComponentTypes.GLIDER)) return;
        if (isMovementKeyPhysicallyPressed() || isKeyPhysicallyPressed(mc.options.sneakKey)) {

            if (!autonavMode && player.isOnGround()) highwayRelaunchArmed = false;
            return;
        }
        if (autonavMode) {

            if (Math.hypot(mapDestX - player.getX(), mapDestZ - player.getZ()) <= autonavArrivalRadius.get()) return;
        } else if (highwayDirection.get() == HighwayDirection.TowardsCentre
            && Math.hypot(player.getX(), player.getZ()) < HIGHWAY_CENTRE_RADIUS) {

            return;
        }
        if (relaunchPause > 0) return;

        if (relaunchAttempts >= RELAUNCH_MAX_ATTEMPTS) {
            relaunchAttempts = 0;
            relaunchPause = RELAUNCH_PAUSE_TICKS;
            return;
        }

        ClientPlayNetworkHandler networkHandler = mc.getNetworkHandler();
        if (networkHandler == null) return;

        if (player.isOnGround()) {
            if (relaunchCooldown > 0) return;
            releaseHighwayKeys();
            resetAutonavRamp();
            player.setJumping(false);
            player.setSprinting(true);
            player.jump();
            networkHandler.sendPacket(new ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            relaunchAttempts++;
            relaunchCooldown = RELAUNCH_RETRY_TICKS;
        } else if (player.checkGliding()) {

            networkHandler.sendPacket(new ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            relaunchAttempts++;
        }
    }

    private void continueGlideAfterSwap(PlayerEntity player) {
        if (reglideTicksLeft <= 0) return;
        reglideTicksLeft--;

        if (player.isOnGround() || player.isTouchingWater()) {
            reglideTicksLeft = 0;
            return;
        }
        if (player.isGliding()) return;
        if (!player.getEquippedStack(EquipmentSlot.CHEST).contains(DataComponentTypes.GLIDER)) return;

        if (player.checkGliding()) {
            ClientPlayNetworkHandler networkHandler = mc.getNetworkHandler();
            if (networkHandler != null) {
                networkHandler.sendPacket(new ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            }
        }
    }

    public double getIntendedDistanceLastTick() {
        return intendedDistanceLastTick;
    }

    private boolean autonavPathfinding() {
        return autonavActive && autonav.get();
    }

    private boolean autohighwayPathfinding() {
        return autohighway.get();
    }

    private void runOverdrive() {
        ClientPlayerEntity player = mc.player;

        updateOverdriveSpent(player);

        if (!overdrive.get() || player == null || mc.world == null || mc.getNetworkHandler() == null
            || mc.isPaused() || autonavPathfinding() || autohighwayPathfinding() || unstickTicksLeft > 0
            || isHorizontalRampStalled(player)) {
            overdriveCarry = 0;
            return;
        }

        overdriveCarry += OVERDRIVE_MULTIPLIER - 1.0;
        int extraTicks = (int) overdriveCarry;
        overdriveCarry -= extraTicks;

        double budget = horizontalSpeedCap();
        double movedThisGameTick = lastGlideIntendedSpeed;

        for (int i = 0; i < extraTicks; i++) {
            if (isHorizontalRampStalled(player)) {
                overdriveCarry = 0;

                if (overdriveSpeed() >= horizontalSpeed.get() * OVERDRIVE_OFF_FRACTION) overdriveSpent = true;
                break;
            }

            double currentSpeed = isBypassActive() ? lastGlideIntendedSpeed : acceleration;
            double nextTickSpeed = nextSpeedFor(currentSpeed, accelerationStep.get(), accelerationPlateau.get(), budget);
            if (movedThisGameTick + nextTickSpeed > budget) {
                overdriveCarry = 0;
                break;
            }

            double savedVelocityY = player.getVelocity().y;

            if (!isOverdrivePacketSane(player)) {

                overdriveCarry = 0;
                break;
            }
            overdriveExtraTick = true;
            try {
                player.age++;
                player.tick();
            } finally {
                overdriveExtraTick = false;
            }
            Vec3d after = player.getVelocity();
            player.setVelocity(after.x, savedVelocityY, after.z);
            movedThisGameTick += lastGlideIntendedSpeed;
        }
    }

    private double overdriveSpeed() {
        return isBypassActive() ? lastGlideIntendedSpeed : acceleration;
    }

    private void updateOverdriveSpent(PlayerEntity player) {
        if (player == null || !player.isGliding()) {
            overdriveSpent = false;
            return;
        }

        double speed = overdriveSpeed();
        double top = horizontalSpeed.get();

        if (speed <= OVERDRIVE_REARM_SPEED) overdriveSpent = false;
        else if (top > 0 && speed >= top * OVERDRIVE_OFF_FRACTION) overdriveSpent = true;
    }

    private boolean isHorizontalRampStalled(PlayerEntity player) {
        if (!player.isAlive() || !player.isGliding()) return true;
        if (noMovementKeyPressed()) return true;
        if (mappingWaitingForChunks || buildingModeEngaged || rubberbandCapTicksLeft > 0) return true;

        if (overdriveSpent) return true;

        if (isBypassActive()) return !bypassRamping;

        if (atMaxSpeed) return true;
        if (accelerationDelayTicks < ACCELERATION_DELAY_TICKS) return true;
        return acceleration >= horizontalSpeed.get() * OVERDRIVE_OFF_FRACTION;
    }

    @SuppressWarnings("unused")
    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof PlayerPositionLookS2CPacket) {
            positionCorrections.incrementAndGet();
            PlayerEntity player = mc.player;
            ClientWorld world = mc.world;

            boolean inFlight = player != null && world != null && player.isGliding();

            if (inFlight && rubberbandSpeedCap.get()) {
                double speedBps = Math.hypot(player.getVelocity().x, player.getVelocity().z) * 20;
                double thresholdBps = rubberbandSpeedCapThreshold.get();
                double toleranceBps = thresholdBps * (RUBBERBAND_CAP_TOLERANCE_PERCENT / 100.0);

                if (speedBps >= thresholdBps && speedBps <= thresholdBps + toleranceBps) {
                    long now = System.nanoTime();
                    rubberbandQualifyingTimestamps.addLast(now);
                    while (!rubberbandQualifyingTimestamps.isEmpty()
                        && now - rubberbandQualifyingTimestamps.peekFirst() > RUBBERBAND_CAP_TRIGGER_WINDOW_NANOS) {
                        rubberbandQualifyingTimestamps.pollFirst();
                    }

                    if (rubberbandQualifyingTimestamps.size() >= RUBBERBAND_CAP_TRIGGER_COUNT) {
                        rubberbandCapTicksLeft = (int) Math.round(RUBBERBAND_CAP_DURATION_SECONDS * 20);
                        rubberbandCapSpeed = thresholdBps / 20;

                        rubberbandQualifyingTimestamps.clear();
                    }
                }
            }

            if (inFlight) {

                int rewindTicks = RUBBERBAND_REWIND_TICKS;
                double keptSpeed = rewindSpeedFor(acceleration, accelerationStep.get(), accelerationPlateau.get(), minHorizontalSpeed(), rewindTicks);

                if (keptSpeed < acceleration) {
                    acceleration = keptSpeed;
                    atMaxSpeed = false;
                    accelerationDelayTicks = 0;

                    verticalAcceleration = rewindSpeedFor(verticalAcceleration, verticalAccelerationStep.get(), verticalAccelerationPlateau.get(), minVerticalSpeed(), rewindTicks);
                    atMaxVerticalSpeed = false;
                    verticalAccelerationDelayTicks = 0;
                }

                return;
            }

            zeroAcceleration();
        }
    }

    private boolean isBypassActive() {
        return bypassMode.get() || inCobweb;
    }

    private static boolean isInCobweb(PlayerEntity player, ClientWorld world) {
        Box box = player.getBoundingBox();
        int minX = (int) Math.floor(box.minX + 1.0E-5), maxX = (int) Math.floor(box.maxX - 1.0E-5);
        int minY = (int) Math.floor(box.minY + 1.0E-5), maxY = (int) Math.floor(box.maxY - 1.0E-5);
        int minZ = (int) Math.floor(box.minZ + 1.0E-5), maxZ = (int) Math.floor(box.maxZ - 1.0E-5);

        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (world.getBlockState(pos.set(x, y, z)).isOf(Blocks.COBWEB)) return true;
                }
            }
        }
        return false;
    }

    private void toggleWithFeedback(Setting<Boolean> setting, String label) {
        setting.set(!setting.get());
        sendToggleFeedback(label, setting.get());
    }

    private void sendToggleFeedback(String label, boolean on) {
        if (!Config.get().chatFeedback.get() || !chatFeedback) return;
        info("Toggled (highlight)%s(default) %s(default).", label, on ? Formatting.GREEN + "on" : Formatting.RED + "off");
    }

    @SuppressWarnings("unused")
    @EventHandler
    private void onKeyEvent(KeyEvent event) {
        if (event.action != KeyAction.Press) return;

        if (autoPilotKeybind.get().matches(event.input)) toggleWithFeedback(autoPilot, "Autopilot");
        if (autohighwayKeybind.get().matches(event.input)) toggleWithFeedback(autohighway, "Autohighway");
        if (mappingModeKeybind.get().matches(event.input)) toggleWithFeedback(mappingMode, "Mapping Mode");
        if (buildingModeKeybind.get().matches(event.input)) toggleWithFeedback(buildingMode, "Building Mode");
        if (bypassModeKeybind.get().matches(event.input)) toggleWithFeedback(bypassMode, "Bypass Mode");
        if (playerAvoidanceKeybind.get().matches(event.input)) toggleWithFeedback(playerAvoidance, "Player Avoidance");
        if (antiSlamKeybind.get().matches(event.input)) toggleWithFeedback(antiSlam, "Anti Slam");
        if (overdriveKeybind.get().matches(event.input)) toggleWithFeedback(overdrive, "Overdrive");
        if (autonavKeybind.get().matches(event.input)) {
            toggleWithFeedback(autonav, "Autonav");
            if (!autonav.get() && autonavActive) cancelAutonav();
        }
        if (autonavCancelKeybind.get().matches(event.input)) {
            gamerguy11.sixtoolsaddon.efly.pathing.EflyGoto goTo = Modules.get().get(gamerguy11.sixtoolsaddon.efly.pathing.EflyGoto.class);
            if (goTo != null && goTo.isActive()) {
                goTo.toggle();
                if (Config.get().chatFeedback.get() && chatFeedback) info("(highlight)Autonav(default) cancelled.");
            }
        }
        if (autonavActive && autonavCancelKeybind.get().matches(event.input)) {
            cancelAutonav();
            if (Config.get().chatFeedback.get() && chatFeedback) info("(highlight)Autonav(default) cancelled.");
        }
    }

    private static boolean isOverdrivePacketSane(PlayerEntity p) {
        double x = p.getX(), y = p.getY(), z = p.getZ();
        float yaw = p.getYaw(), pitch = p.getPitch();
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
            && Float.isFinite(yaw) && Float.isFinite(pitch)
            && Math.abs(x) < 3.0E7 && Math.abs(z) < 3.0E7 && Math.abs(y) < 2.0E7;
    }

    private static void swingHand(PlayerEntity player, Hand hand) {
        player.swingHand(hand, false);
    }

    private void autoTakeoff() {
        PlayerEntity player = mc.player;
        if (player == null) return;

        if (incrementJumpTimer) jumpTimer++;

        boolean jumpPressed = keyDown(mc.options.jumpKey);

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

    private int autonavDownTicks;

    private void autonavRecoverIfDown(PlayerEntity player) {
        if (!autonavRecover.get() || !autonavActive || !autonav.get() || player.isGliding()
            || player.isTouchingWater() || player.hasVehicle() || inCobweb
            || !player.getEquippedStack(EquipmentSlot.CHEST).contains(DataComponentTypes.GLIDER)) {
            autonavDownTicks = 0;
            return;
        }

        autonavDownTicks++;
        if (autonavDownTicks < 10) return;

        if (autonavDownTicks == 10) {
            mapEscalation = Math.min(mapEscalation + 1, 4);
            mapDetouring = true;
            mapClearStreak = 0;
            mapStuckTicks = 0;
            mapLastPos = null;
            clearMapRoute();
        }

        if ((autonavDownTicks - 10) % 8 == 0) {
            if (player.isOnGround()) {
                player.setSprinting(true);
                player.jump();
            }
            ClientPlayNetworkHandler networkHandler = mc.getNetworkHandler();
            if (networkHandler != null) {
                networkHandler.sendPacket(new ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            }
        }
    }

    private void handleAutopilot() {
        PlayerEntity player = mc.player;
        if (player == null || !player.isGliding()) return;

        if (overdriveExtraTick) return;

        if (!autohighway.get()) resetHighwayState();

        highwaySteerTarget = null;
        highwayTargetY = Double.NaN;

        if (!avoidanceSteering) {
            if (autonavActive && autonav.get()) {
                driveAutonav(player);
            } else if (autohighway.get()) {
                driveAutohighway(player);
            } else {
                releaseHighwayKeys();
                if (autoPilot.get() && player.getY() > autoPilotMinimumHeight.get()) {
                    mc.options.forwardKey.setPressed(true);
                    lastForwardPressed = true;
                }
            }
        } else if (autohighway.get() || autonavActive) {
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
                    swingHand(player, Hand.OFF_HAND);
                } else {
                    InvUtils.swap(itemResult.slot(), true);

                    interactionManager.interactItem(player, Hand.MAIN_HAND);
                    swingHand(player, Hand.MAIN_HAND);

                    InvUtils.swapBack();
                }
            }
            ticksLeft--;
        }
    }

    private void driveAutohighway(PlayerEntity player) {
        ClientWorld world = mc.world;
        if (world == null) {
            releaseHighwayKeys();
            return;
        }

        if (highwayAxis == null) armHighwayAxis(player);
        Vec3d pos = player.getEntityPos();

        if (highwayDirection.get() == HighwayDirection.TowardsCentre
            && Math.hypot(pos.x, pos.z) < HIGHWAY_CENTRE_RADIUS) {
            highwayStuckTicks = 0;
            highwayMovingTicks = 0;
            highwayEscalation = 0;
            highwayLastPos = pos;

            Vec3d centre = new Vec3d(0, Double.isNaN(highwayCruiseY) ? pos.y : highwayCruiseY, 0);

            highwaySteerTarget = centre;
            holdHighwayDrivingKeys();
            applyHighwayVerticalHold(player, centre.y);
            return;
        }

        if (highwayLastPos != null && !mappingWaitingForChunks) {
            if (pos.distanceTo(highwayLastPos) < HIGHWAY_STUCK_MOVE_EPSILON) {
                highwayStuckTicks++;
                highwayMovingTicks = 0;
            } else {
                highwayStuckTicks = 0;
                highwayMovingTicks++;
                if (highwayMovingTicks >= 60) highwayEscalation = 0;
            }
        }
        highwayLastPos = pos;
        if (highwayStuckTicks >= Math.round(highwayIntelligence.get().stuckTimeout * 20)) {
            highwayStuckTicks = 0;
            highwayEscalation = Math.min(highwayEscalation + 1, 6);
            highwayPathNodes.clear();
            highwayPathGeneration.incrementAndGet();
            highwayPathRequest = null;

            if (highwayEscalation == 1) {
            } else if (highwayEscalation == 2) {
            } else {
            }
        }

        applyHighwayPathResult(world, pos);
        highwayTicksSinceRepath++;
        boolean routeLeft = highwayPathNodes.size() >= 2 && highwayPathIndex < highwayPathNodes.size() - 1;
        int interval = highwayIntelligence.get().repathInterval;

        boolean routeHealthy = routeLeft
            && highwayPathNodes.size() - 1 - highwayPathIndex >= HIGHWAY_HEALTHY_ROUTE_NODES
            && highwayEscalation == 0;
        boolean due = highwayTicksSinceRepath >= (routeHealthy ? interval * HIGHWAY_HEALTHY_REPATH_FACTOR : interval);
        if (!routeLeft || due) {
            computeHighwayPath(world, pos);
        }

        Vec3d target = null;
        if (highwayPathNodes.size() >= 2) {
            advanceHighwayPathIndex(pos);
            target = pickHighwayWaypoint(player, world, pos);
        }
        if (target == null) {

            target = highwayGoal != null ? highwayGoal : goalOnHighway(pos, 0, 0);

            if (highwayPathMob == null) {
            } else {
            }
        } else {
        }

        highwaySteerTarget = target;
        holdHighwayDrivingKeys();
        applyHighwayVerticalHold(player, target.y);
    }

    private ParrotEntity highwayPathMob(ClientWorld world) {
        if (highwayPathMob == null || highwayPathMobWorld != world) {
            try {
                highwayPathMob = new ParrotEntity(EntityType.PARROT, world);
                highwayPathMobWorld = world;
            } catch (RuntimeException e) {
                highwayPathMob = null;
                highwayPathMobWorld = null;
            }
        }
        return highwayPathMob;
    }

    private Vec3d goalOnHighway(Vec3d pos, double lateralOffset, double heightOffset) {
        Vec3d axis = highwayAxis;
        Vec3d lateral = new Vec3d(-axis.z, 0, axis.x);
        double lat = pos.x * lateral.x + pos.z * lateral.z;
        double along = pos.x * axis.x + pos.z * axis.z;

        double range = highwayIntelligence.get().pathRange - 2;
        double ahead = Math.abs(lat) < 2.0 ? range : Math.min(range, Math.abs(lat) + 8.0);

        double reach = along + ahead;
        if (highwayDirection.get() == HighwayDirection.TowardsCentre) reach = Math.min(reach, 0);
        double gx = axis.x * reach + lateral.x * lateralOffset;
        double gz = axis.z * reach + lateral.z * lateralOffset;

        double dx = gx - pos.x, dz = gz - pos.z;
        double dist = Math.hypot(dx, dz);
        if (dist > range) {
            gx = pos.x + dx / dist * range;
            gz = pos.z + dz / dist * range;
        }

        double cruiseY = Double.isNaN(highwayCruiseY) ? pos.y : highwayCruiseY;
        ClientWorld world = mc.world;
        double gy = cruiseY + heightOffset;
        if (world != null) gy = Math.max(world.getBottomY() + 2, Math.min(flightCeiling(), gy));
        return new Vec3d(gx, gy, gz);
    }

    private static final class HighwayPathRequest {
        final int generation;
        final ParrotEntity mob;
        final Vec3d start;
        final int range;
        final List<Vec3d> goals;
        final int firstGoal;
        volatile boolean done;
        int goalIndex = -1;
        ArrayList<Vec3d> nodes;

        HighwayPathRequest(int generation, ParrotEntity mob, Vec3d start, int range, List<Vec3d> goals, int firstGoal) {
            this.generation = generation;
            this.mob = mob;
            this.start = start;
            this.range = range;
            this.goals = goals;
            this.firstGoal = firstGoal;
        }
    }

    private void computeHighwayPath(ClientWorld world, Vec3d pos) {
        highwayTicksSinceRepath = 0;

        if (highwayPathRequest != null && !highwayPathRequest.done && highwayPathRequest.generation == highwayPathGeneration.get()) return;

        ParrotEntity mob = highwayPathMob(world);
        if (mob == null) return;

        ArrayList<Vec3d> goals = new ArrayList<>(6);
        double step = 6.0 * Math.max(1, highwayEscalation);
        double firstSide = (highwayEscalation % 2 == 1) ? 1 : -1;
        if (highwayEscalation == 0) goals.add(goalOnHighway(pos, 0, 0));
        goals.add(goalOnHighway(pos, firstSide * step, 0));
        goals.add(goalOnHighway(pos, -firstSide * step, 0));
        goals.add(goalOnHighway(pos, 0, step));
        if (highwayEscalation > 0) goals.add(goalOnHighway(pos, 0, 0));

        submitHighwayPath(new HighwayPathRequest(highwayPathGeneration.get(), mob, pos, highwayIntelligence.get().pathRange, goals, 0));
    }

    private void submitHighwayPath(HighwayPathRequest request) {
        if (highwayPathExecutor == null) {
            highwayPathExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "Efly-Pathfinder");
                thread.setDaemon(true);
                thread.setPriority(Thread.NORM_PRIORITY - 1);
                return thread;
            });
        }

        highwayPathRequest = request;
        try {
            highwayPathExecutor.execute(() -> runHighwayPathSearch(request));
        } catch (RejectedExecutionException e) {
            highwayPathRequest = null;
        }
    }

    private void runHighwayPathSearch(HighwayPathRequest request) {
        try {
            ParrotEntity mob = request.mob;

            EntityAttributeInstance followRange = mob.getAttributeInstance(EntityAttributes.FOLLOW_RANGE);
            if (followRange != null && followRange.getBaseValue() != request.range) {
                followRange.setBaseValue(request.range);
            }

            mob.setPosition(request.start.x, request.start.y, request.start.z);
            EntityNavigation navigation = mob.getNavigation();

            for (int i = request.firstGoal; i < request.goals.size(); i++) {
                if (request.generation != highwayPathGeneration.get()) return;

                Vec3d goal = request.goals.get(i);
                Path path;
                try {
                    path = navigation.findPathTo(BlockPos.ofFloored(goal.x, goal.y, goal.z), 1);
                } catch (Throwable t) {
                    continue;
                }
                if (path == null || path.getLength() < 2) continue;

                BlockPos end = path.getNodePos(path.getLength() - 1);
                Vec3d endPos = new Vec3d(end.getX() + 0.5, end.getY() + HIGHWAY_NODE_HEIGHT, end.getZ() + 0.5);
                if (endPos.distanceTo(request.start) < 2.0) continue;

                int nodeCount = Math.min(path.getLength(), HIGHWAY_MAX_ROUTE_NODES);
                ArrayList<Vec3d> nodes = new ArrayList<>(nodeCount);
                for (int n = 0; n < nodeCount; n++) {
                    BlockPos node = path.getNodePos(n);
                    nodes.add(new Vec3d(node.getX() + 0.5, node.getY() + HIGHWAY_NODE_HEIGHT, node.getZ() + 0.5));
                }

                request.goalIndex = i;
                request.nodes = nodes;
                return;
            }
        } catch (Throwable ignored) {

        } finally {
            request.done = true;
        }
    }

    private void applyHighwayPathResult(ClientWorld world, Vec3d pos) {
        HighwayPathRequest request = highwayPathRequest;
        if (request == null || !request.done) return;
        highwayPathRequest = null;
        if (request.generation != highwayPathGeneration.get() || request.nodes == null) return;

        ArrayList<Vec3d> nodes = request.nodes;

        PlayerEntity player = mc.player;
        if (player != null && (!repairHighwayPath(world, player, nodes) || nodes.getLast().distanceTo(pos) < 2.0)) {
            if (request.goalIndex + 1 < request.goals.size()) {
                submitHighwayPath(new HighwayPathRequest(request.generation, request.mob, pos, request.range, request.goals, request.goalIndex + 1));
            }
            return;
        }

        highwayPathNodes.clear();
        highwayPathNodes.addAll(nodes);
        highwayPathIndex = 0;
        highwayGoal = request.goals.get(request.goalIndex);
    }

    private static final int HIGHWAY_DETOUR_RADIUS = 3;
    private static final int HIGHWAY_DETOUR_MAX_CELLS = 4000;

    private boolean repairHighwayPath(ClientWorld world, PlayerEntity player, ArrayList<Vec3d> nodes) {
        double loose = HIGHWAY_HARD_MARGIN;
        double strict = HIGHWAY_BLOCK_CLEARANCE - 1.0E-4;

        int n = nodes.size();
        boolean[] free = new boolean[n];
        boolean[] bad = new boolean[n];
        boolean anyBad = false;
        free[0] = true;
        for (int i = 1; i < n; i++) free[i] = highwayPointFree(world, player, nodes.get(i), loose);
        for (int i = 1; i < n; i++) {
            bad[i] = !free[i] || (free[i - 1] && highwayLineBlocked(world, player, nodes.get(i - 1), nodes.get(i), loose));
            anyBad |= bad[i];
        }
        if (!anyBad) return true;

        ArrayList<Vec3d> out = new ArrayList<>(n + 8);
        int i = 0;
        while (i < n) {
            if (!bad[i]) {
                out.add(nodes.get(i));
                i++;
                continue;
            }

            int j = i;
            while (j < n && bad[j]) j++;
            if (j == n) break;

            List<BlockPos> detour = highwayDetour(world, player, highwayNodeCell(out.getLast()), highwayNodeCell(nodes.get(j)), strict);
            if (detour == null) detour = highwayDetour(world, player, highwayNodeCell(out.getLast()), highwayNodeCell(nodes.get(j)), loose);
            if (detour == null) return false;

            for (BlockPos cell : detour) {
                out.add(new Vec3d(cell.getX() + 0.5, cell.getY() + HIGHWAY_NODE_HEIGHT, cell.getZ() + 0.5));
            }
            i = j;
        }

        if (out.size() < 2) return false;
        nodes.clear();
        nodes.addAll(out);
        return true;
    }

    private static BlockPos highwayNodeCell(Vec3d node) {
        return new BlockPos((int) Math.floor(node.x), (int) Math.round(node.y - HIGHWAY_NODE_HEIGHT), (int) Math.floor(node.z));
    }

    private boolean highwayPointFree(ClientWorld world, PlayerEntity player, Vec3d p, double margin) {
        return highestBlockingY(world, highwayHitboxBox(player, p.x, p.y, p.z, margin), null) == Integer.MIN_VALUE;
    }

    private boolean highwayLineBlocked(ClientWorld world, PlayerEntity player, Vec3d from, Vec3d to, double margin) {
        Vec3d delta = to.subtract(from);
        int steps = Math.max(1, (int) Math.ceil(delta.length() / 0.5));
        for (int k = 1; k <= steps; k++) {
            double t = (double) k / steps;
            if (!highwayPointFree(world, player, from.add(delta.multiply(t)), margin)) return true;
        }
        return false;
    }

    private List<BlockPos> highwayDetour(ClientWorld world, PlayerEntity player, BlockPos from, BlockPos to, double margin) {
        int minX = Math.min(from.getX(), to.getX()) - HIGHWAY_DETOUR_RADIUS, maxX = Math.max(from.getX(), to.getX()) + HIGHWAY_DETOUR_RADIUS;
        int minY = Math.max(world.getBottomY(), Math.min(from.getY(), to.getY()) - HIGHWAY_DETOUR_RADIUS);
        int hi = Math.max(from.getY(), to.getY());

        int maxY = Math.min(flightCeilingBlock(), hi + HIGHWAY_DETOUR_RADIUS);
        int minZ = Math.min(from.getZ(), to.getZ()) - HIGHWAY_DETOUR_RADIUS, maxZ = Math.max(from.getZ(), to.getZ()) + HIGHWAY_DETOUR_RADIUS;

        HashMap<BlockPos, BlockPos> cameFrom = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        cameFrom.put(from, from);
        queue.add(from);

        while (!queue.isEmpty() && cameFrom.size() < HIGHWAY_DETOUR_MAX_CELLS) {
            BlockPos cur = queue.poll();
            Vec3d curPoint = new Vec3d(cur.getX() + 0.5, cur.getY() + HIGHWAY_NODE_HEIGHT, cur.getZ() + 0.5);

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos next = cur.add(dx, dy, dz);
                        if (next.getX() < minX || next.getX() > maxX || next.getY() < minY || next.getY() > maxY
                            || next.getZ() < minZ || next.getZ() > maxZ || cameFrom.containsKey(next)) continue;

                        Vec3d nextPoint = new Vec3d(next.getX() + 0.5, next.getY() + HIGHWAY_NODE_HEIGHT, next.getZ() + 0.5);
                        if (!highwayPointFree(world, player, nextPoint, margin)) continue;
                        if (highwayLineBlocked(world, player, curPoint, nextPoint, margin)) continue;

                        cameFrom.put(next, cur);
                        if (next.equals(to)) {
                            LinkedList<BlockPos> cells = new LinkedList<>();
                            for (BlockPos c = cameFrom.get(next); !c.equals(from); c = cameFrom.get(c)) cells.addFirst(c);
                            return cells;
                        }
                        queue.add(next);
                    }
                }
            }
        }
        return null;
    }

    private void advanceHighwayPathIndex(Vec3d pos) {
        int last = highwayPathNodes.size() - 1;
        while (highwayPathIndex < last) {
            Vec3d cur = highwayPathNodes.get(highwayPathIndex);
            Vec3d seg = highwayPathNodes.get(highwayPathIndex + 1).subtract(cur);
            double len2 = seg.lengthSquared();
            if (len2 < 1.0E-6 || pos.subtract(cur).dotProduct(seg) / len2 >= 1.0) highwayPathIndex++;
            else break;
        }
    }

    private Vec3d pickHighwayWaypoint(PlayerEntity player, ClientWorld world, Vec3d pos) {
        int first = highwayPathIndex + 1;
        if (first >= highwayPathNodes.size()) return null;
        int last = Math.min(highwayPathNodes.size() - 1, highwayPathIndex + HIGHWAY_MAX_LOOKAHEAD_NODES);

        Box startBox = highwayHitboxBox(player, pos.x, pos.y, pos.z, HIGHWAY_BLOCK_CLEARANCE);
        for (int k = last; k > first; k--) {
            Vec3d candidate = highwayPathNodes.get(k);
            if (highwayRouteClear(world, player, pos, candidate, startBox)) return candidate;
        }
        return highwayPathNodes.get(first);
    }

    private boolean highwayRouteClear(ClientWorld world, PlayerEntity player, Vec3d from, Vec3d to, Box startBox) {
        return highwayRouteClear(world, player, from, to, startBox, false);
    }

    private boolean highwayRouteClear(ClientWorld world, PlayerEntity player, Vec3d from, Vec3d to, Box startBox, boolean mapHazards) {
        if (Math.abs(to.y - from.y) > HIGHWAY_HEIGHT_BAND) return highwaySweepClear(world, player, from, to, startBox, mapHazards);

        Vec3d above = new Vec3d(from.x, to.y, from.z);
        return highwaySweepClear(world, player, from, above, startBox, mapHazards)
            && highwaySweepClear(world, player, above, to, startBox, mapHazards);
    }

    private boolean highwaySweepClear(ClientWorld world, PlayerEntity player, Vec3d from, Vec3d to, Box startBox, boolean mapHazards) {
        Box trueStart = highwayHitboxBox(player, from.x, from.y, from.z, HIGHWAY_TRUE_MARGIN);
        Vec3d delta = to.subtract(from);
        int steps = Math.max(1, (int) Math.ceil(delta.length() / 0.5));
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            Vec3d point = new Vec3d(from.x + delta.x * t, from.y + delta.y * t, from.z + delta.z * t);
            Box box = highwayHitboxBox(player, point.x, point.y, point.z, HIGHWAY_BLOCK_CLEARANCE);
            if (highestBlockingY(world, box, startBox) != Integer.MIN_VALUE) return false;
            if (highestBlockingY(world, highwayHitboxBox(player, point.x, point.y, point.z, HIGHWAY_TRUE_MARGIN), trueStart) != Integer.MIN_VALUE) return false;
            if (mapHazards && mapFluidNear(world, player, point, from)) return false;
        }
        return true;
    }

    private boolean mapFluidNear(ClientWorld world, PlayerEntity player, Vec3d p, Vec3d start) {
        boolean lava = autonavAvoidLava.get(), water = autonavAvoidWater.get();
        if (!lava && !water) return false;

        if (lava && touchesFluid(world, highwayHitboxBox(player, p.x, p.y, p.z, MAP_LAVA_MARGIN),
            start == null ? null : highwayHitboxBox(player, start.x, start.y, start.z, MAP_LAVA_MARGIN), true)) return true;
        return water && touchesFluid(world, highwayHitboxBox(player, p.x, p.y, p.z, MAP_WATER_MARGIN),
            start == null ? null : highwayHitboxBox(player, start.x, start.y, start.z, MAP_WATER_MARGIN), false);
    }

    private boolean mapTouchesAvoidedFluid(ClientWorld world, PlayerEntity player, double x, double y, double z, boolean lava) {
        if (!(lava ? autonavAvoidLava.get() : autonavAvoidWater.get())) return false;
        return touchesFluid(world, highwayHitboxBox(player, x, y, z, MAP_FLUID_HARD_MARGIN), null, lava);
    }

    private boolean touchesFluid(ClientWorld world, Box box, Box ignoreBox, boolean lava) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int maxX = (int) Math.floor(box.maxX), maxY = (int) Math.floor(box.maxY), maxZ = (int) Math.floor(box.maxZ);
        for (int bx = (int) Math.floor(box.minX); bx <= maxX; bx++) {
            for (int by = (int) Math.floor(box.minY); by <= maxY; by++) {
                for (int bz = (int) Math.floor(box.minZ); bz <= maxZ; bz++) {
                    pos.set(bx, by, bz);
                    var fluid = world.getFluidState(pos);
                    if (fluid.isEmpty() || !fluid.isIn(lava ? FluidTags.LAVA : FluidTags.WATER)) continue;
                    if (ignoreBox != null && ignoreBox.intersects(bx, by, bz, bx + 1, by + 1, bz + 1)) continue;
                    return true;
                }
            }
        }
        return false;
    }

    private void holdHighwayDrivingKeys() {
        if (!isKeyPhysicallyPressed(mc.options.forwardKey)) mc.options.forwardKey.setPressed(true);
        if (!isKeyPhysicallyPressed(mc.options.backKey)) mc.options.backKey.setPressed(false);
        if (!isKeyPhysicallyPressed(mc.options.leftKey)) mc.options.leftKey.setPressed(false);
        if (!isKeyPhysicallyPressed(mc.options.rightKey)) mc.options.rightKey.setPressed(false);
        lastForwardPressed = mc.options.forwardKey.isPressed();
    }

    private void applyHighwayVerticalHold(PlayerEntity player, double targetY) {
        highwayTargetY = targetY;

        double verticalError = targetY - player.getY();
        mc.options.jumpKey.setPressed(verticalError > HIGHWAY_HEIGHT_BAND);
        mc.options.sneakKey.setPressed(verticalError < -HIGHWAY_HEIGHT_BAND);
        if (Math.abs(verticalError) <= HIGHWAY_HEIGHT_BAND) {

            velY = verticalError;
        }
    }

    private void applyHighwaySteering() {
        if (highwaySteerTarget == null || mc.player == null) return;

        double speed = Math.hypot(velX, velZ);
        if (speed < 1.0E-6) return;

        Vec3d pos = mc.player.getEntityPos();
        double dx = highwaySteerTarget.x - pos.x;
        double dz = highwaySteerTarget.z - pos.z;
        double horizontalDistance = Math.hypot(dx, dz);
        if (horizontalDistance < 1.0E-3) {

            velX = 0;
            velZ = 0;
            return;
        }

        double step = Math.min(speed, horizontalDistance);
        velX = dx / horizontalDistance * step;
        velZ = dz / horizontalDistance * step;
    }

    private void applyHighwaySlopeCoupling() {
        if (highwaySteerTarget == null || mc.player == null) return;

        Vec3d pos = mc.player.getEntityPos();
        double dy = highwaySteerTarget.y - pos.y;
        if (Math.abs(dy) <= HIGHWAY_HEIGHT_BAND || Math.abs(velY) < 1.0E-6) return;

        double horizontalSpeed = Math.hypot(velX, velZ);
        if (horizontalSpeed < 1.0E-6) return;

        double horizontalDistance = Math.hypot(highwaySteerTarget.x - pos.x, highwaySteerTarget.z - pos.z);
        double share = Math.min(1.0, Math.abs(velY) / Math.abs(dy));
        double allowed = horizontalDistance * share;
        if (allowed < horizontalSpeed) {
            double scale = allowed / horizontalSpeed;
            velX *= scale;
            velZ *= scale;
        }
    }

    private void pollMapClick() {
        if (!autonav.get()) {
            mapClicks.reset();

            if (autonavActive) cancelAutonav();
            return;
        }

        XaeroMapClicks.Click click = mapClicks.poll(mc, autonavButton.get().button);
        if (click != null) startAutonav(click.x(), click.z());

        String problem = mapClicks.takeError();
        if (problem != null) warning(problem);
    }

    public void goToTypedCoords() {
        if (mc.player == null || mc.world == null) {
            warning("Join a world first.");
            return;
        }
        if (!isActive()) {
            warning("Turn Efly on first.");
            return;
        }
        if (!autonav.get()) {
            warning("Turn Autonav on first.");
            return;
        }
        startAutonav(autonavGotoX.get(), autonavGotoZ.get());
    }

    public enum AutonavEngine {
        Classic,
        Pathfinder
    }

    private void startAutonav(int blockX, int blockZ) {
        PlayerEntity player = mc.player;
        ClientWorld world = mc.world;
        if (player == null || world == null) return;

        if (autonavEngine.get() == AutonavEngine.Pathfinder) {
            if (autonavActive) cancelAutonav();
            gamerguy11.sixtoolsaddon.efly.pathing.EflyGoto goTo = Modules.get().get(gamerguy11.sixtoolsaddon.efly.pathing.EflyGoto.class);
            if (goTo == null) return;
            goTo.go(blockX, null, blockZ);
            if (goTo.isActive() && autonavWaypoint.get()) {
                gamerguy11.sixtoolsaddon.basehunting.HuntWaypoints.setAutonav(new BlockPos(blockX, (int) Math.floor(player.getY()), blockZ));
            }
            if (autonavCloseMap.get() && mc.currentScreen != null) mc.setScreen(null);
            return;
        }

        resetMapRouteState();
        resetAutonavRamp();
        mapDestX = blockX + 0.5;
        mapDestZ = blockZ + 0.5;
        autonavDestX = blockX;
        autonavDestZ = blockZ;
        mapGoalX = mapDestX;
        mapGoalZ = mapDestZ;
        mapCruiseY = Math.max(world.getBottomY() + 2, Math.min(flightCeiling(), player.getY()));
        mapDetouring = false;
        autonavActive = true;
        if (autonavWaypoint.get()) {
            gamerguy11.sixtoolsaddon.basehunting.HuntWaypoints.setAutonav(new BlockPos(blockX, (int) Math.floor(player.getY()), blockZ));
        }

        if (autonavCloseMap.get() && mc.currentScreen != null) mc.setScreen(null);
    }

    private void cancelAutonav() {
        boolean wasActive = autonavActive;
        resetAutonavState();
        releaseHighwayKeys();
    }

    private void resetAutonavRamp() {
        zeroAcceleration();
        overdriveCarry = 0;
        overdriveSpent = false;
        bypassRamping = false;
    }

    private void resetAutonavState() {
        if (autonavActive) gamerguy11.sixtoolsaddon.basehunting.HuntWaypoints.clearAutonav();
        autonavActive = false;
        autonavDestX = 0;
        autonavDestZ = 0;
        mapDestX = 0;
        mapDestZ = 0;
        mapCruiseY = 0;
        resetMapRouteState();
    }

    private void resetMapRouteState() {
        mapRouteGeneration.incrementAndGet();
        mapRouteRequest = null;
        mapRouteNodes.clear();
        mapRouteIndex = 0;
        mapTicksSinceRepath = 0;
        mapNoRoute = false;
        mapRouteEndsAtFrontier = false;
        mapAwaitingPlanTicks = 0;
        mapHighwayPhase = MAP_HIGHWAY_NONE;
        mapHighwayCooldown = 0;
        mapDetouring = false;
        mapClearStreak = 0;
        mapLastPos = null;
        mapStuckTicks = 0;
        mapMovingTicks = 0;
        mapEscalation = 0;
    }

    private void driveAutonav(PlayerEntity player) {
        ClientWorld world = mc.world;
        if (world == null) {
            releaseHighwayKeys();
            return;
        }

        Vec3d pos = player.getEntityPos();
        double dx = mapDestX - pos.x, dz = mapDestZ - pos.z;
        double distToDest = Math.hypot(dx, dz);
        Vec3d destPoint = new Vec3d(mapDestX, mapCruiseY, mapDestZ);

        if (distToDest <= autonavArrivalRadius.get()) {
            if (isMovementKeyPhysicallyPressed()) {
                cancelAutonav();
                return;
            }
            resetAutonavRamp();
            highwaySteerTarget = destPoint;
            holdHighwayDrivingKeys();
            applyHighwayVerticalHold(player, mapCruiseY);
            return;
        }

        updateHighwayGoal(world, pos);
        double gdx = mapGoalX - pos.x, gdz = mapGoalZ - pos.z;
        double distToGoal = Math.max(Math.hypot(gdx, gdz), 1.0E-6);
        Vec3d goalPoint = new Vec3d(mapGoalX, mapCruiseY, mapGoalZ);

        boolean waitingOnChunks = !world.getChunkManager().isChunkLoaded(
            ((int) Math.floor(pos.x + gdx / distToGoal * 6.0)) >> 4,
            ((int) Math.floor(pos.z + gdz / distToGoal * 6.0)) >> 4);

        boolean awaitingPlan = mapAwaitingPlanTicks > 0 && mapAwaitingPlanTicks <= MAP_AWAITING_PLAN_GRACE_TICKS;
        if (mapLastPos != null && !waitingOnChunks && !mappingWaitingForChunks && !awaitingPlan) {
            if (pos.distanceTo(mapLastPos) < HIGHWAY_STUCK_MOVE_EPSILON) {
                mapStuckTicks++;
                mapMovingTicks = 0;
            } else {
                mapStuckTicks = 0;
                mapMovingTicks++;
                if (mapMovingTicks >= 60) mapEscalation = 0;
            }
        } else if (waitingOnChunks || awaitingPlan) {
            mapStuckTicks = 0;
        }
        mapLastPos = pos;
        if (mapStuckTicks >= Math.round(autonavIntelligence.get().stuckTimeout * 20)) {
            mapStuckTicks = 0;
            mapEscalation = Math.min(mapEscalation + 1, 4);

            mapDetouring = true;
            mapClearStreak = 0;
            clearMapRoute();
            if (mapEscalation == 1) {
            } else {
            }
        }

        double lineRange = Math.min(distToGoal, Math.max(96, Math.min(400, horizontalSpeed.get() * 32)));
        double dirX = gdx / distToGoal, dirZ = gdz / distToGoal;
        double blockedAt = mapStraightBlockedAt(world, player, pos, dirX, dirZ, lineRange);

        boolean inHazard = highestBlockingY(world, highwayHitboxBox(player, pos.x, pos.y, pos.z, HIGHWAY_TRUE_MARGIN), null) != Integer.MIN_VALUE
            || mapTouchesAvoidedFluid(world, player, pos.x, pos.y, pos.z, true)
            || mapTouchesAvoidedFluid(world, player, pos.x, pos.y, pos.z, false);
        boolean straightClear = blockedAt < 0 && !inHazard;
        mapClearStreak = straightClear ? mapClearStreak + 1 : 0;

        if (!mapDetouring && !straightClear) {
            mapDetouring = true;
            clearMapRoute();
            mapTicksSinceRepath = Integer.MAX_VALUE / 2;
        } else if (mapDetouring && mapStuckTicks == 0
            && mapClearStreak >= (mapEscalation > 0 ? MAP_STRAIGHT_CONFIRM_TICKS_AFTER_STUCK : MAP_STRAIGHT_CONFIRM_TICKS)) {

            mapDetouring = false;
            mapCruiseY = Math.max(world.getBottomY() + 2, Math.min(flightCeiling(), pos.y));
            clearMapRoute();
            goalPoint = new Vec3d(mapGoalX, mapCruiseY, mapGoalZ);
        }

        Vec3d target = null;
        boolean awaitingRoute = false;
        if (mapDetouring) {
            applyMapRouteResult();
            mapTicksSinceRepath++;
            boolean routeLeft = mapRouteNodes.size() >= 2 && mapRouteIndex < mapRouteNodes.size() - 1;
            int interval = autonavIntelligence.get().repathInterval;

            boolean routeHealthy = routeLeft && mapRouteNodes.size() - 1 - mapRouteIndex >= 8 && mapEscalation == 0;

            boolean endLoaded = routeLeft && mapRouteEndsAtFrontier && mapRouteEndHasLoaded(world, player, pos);
            boolean due = endLoaded || mapTicksSinceRepath >= (routeHealthy ? interval * 3 : interval);
            if ((!routeLeft && !mapNoRoute) || due) submitMapPlan(world, player, pos);

            if (mapRouteNodes.size() >= 2) {
                advanceMapRouteIndex(pos);
                target = pickMapWaypoint(player, world, pos);
            }
            if (target == null && mapNoRoute) target = pos;
            if (target == null) {

                awaitingRoute = true;
                target = mapApproachTarget(pos, dirX, dirZ, blockedAt);
            }
        }
        mapAwaitingPlanTicks = awaitingRoute ? mapAwaitingPlanTicks + 1 : 0;
        if (target == null) target = goalPoint;

        highwaySteerTarget = target;
        holdHighwayDrivingKeys();
        applyHighwayVerticalHold(player, target.y);
    }

    private void updateHighwayGoal(ClientWorld world, Vec3d pos) {
        mapGoalX = mapDestX;
        mapGoalZ = mapDestZ;
        mapGoalRadius = autonavArrivalRadius.get();

        if (!autonavPrioritiseHighways.get()) {
            if (mapHighwayPhase != MAP_HIGHWAY_NONE) endHighway(0);
            return;
        }
        if (mapHighwayCooldown > 0) mapHighwayCooldown--;

        if (mapHighwayPhase == MAP_HIGHWAY_NONE) {
            if (mapHighwayCooldown > 0) return;
            if (!chooseHighway(world, pos)) {
                mapHighwayCooldown = MAP_HIGHWAY_CHECK_TICKS;
                return;
            }
        }

        double ux = mapHighwayUx, uz = mapHighwayUz;
        double along = pos.x * ux + pos.z * uz;
        double footX = ux * along, footZ = uz * along;
        double lateral = Math.hypot(pos.x - footX, pos.z - footZ);

        if (mapHighwayUsable(ux, uz, footX, footZ) < 0) {
            endHighway(MAP_HIGHWAY_COOLDOWN_TICKS);
            return;
        }
        if (mapHighwayPhase == MAP_HIGHWAY_JOINING && lateral <= MAP_HIGHWAY_JOIN_DISTANCE) {
            mapHighwayPhase = MAP_HIGHWAY_FOLLOWING;
            resetHighwayRoute();
        }

        mapGoalRadius = MAP_HIGHWAY_GOAL_RADIUS;
        if (mapHighwayPhase == MAP_HIGHWAY_FOLLOWING) {
            mapGoalX = footX + ux * MAP_HIGHWAY_LOOKAHEAD;
            mapGoalZ = footZ + uz * MAP_HIGHWAY_LOOKAHEAD;
        } else {
            mapGoalX = mapHighwayEntryX;
            mapGoalZ = mapHighwayEntryZ;
        }
    }

    private double mapHighwayUsable(double ux, double uz, double fx, double fz) {
        double rx = mapDestX - fx, rz = mapDestZ - fz;
        double across = Math.abs(rx * uz - rz * ux);
        double along = rx * ux + rz * uz;
        return along - across;
    }

    private boolean chooseHighway(ClientWorld world, Vec3d pos) {
        double px = pos.x, pz = pos.z;
        double distToDest = Math.hypot(mapDestX - px, mapDestZ - pz);

        int best = -1;
        double bestLateral = Double.MAX_VALUE;
        double bestFootX = 0, bestFootZ = 0;
        for (int i = 0; i < MAP_HIGHWAY_DIRECTIONS.length; i++) {
            double ux = MAP_HIGHWAY_DIRECTIONS[i][0], uz = MAP_HIGHWAY_DIRECTIONS[i][1];
            double along = px * ux + pz * uz;
            double footX = ux * along, footZ = uz * along;
            double lateral = Math.hypot(px - footX, pz - footZ);
            if (lateral >= distToDest || lateral >= bestLateral) continue;
            if (!world.getChunkManager().isChunkLoaded(((int) Math.floor(footX)) >> 4, ((int) Math.floor(footZ)) >> 4)) continue;
            if (mapHighwayUsable(ux, uz, footX, footZ) < MAP_HIGHWAY_MIN_FOLLOW) continue;
            best = i;
            bestLateral = lateral;
            bestFootX = footX;
            bestFootZ = footZ;
        }

        double ux, uz, footX, footZ;
        if (best >= 0) {
            ux = MAP_HIGHWAY_DIRECTIONS[best][0];
            uz = MAP_HIGHWAY_DIRECTIONS[best][1];
            footX = bestFootX;
            footZ = bestFootZ;
        } else {

            int line = MAP_HIGHWAY_LINES[0];
            double nearest = Double.MAX_VALUE;
            for (int i : MAP_HIGHWAY_LINES) {
                double distance = Math.abs(mapDestX * MAP_HIGHWAY_DIRECTIONS[i][1] - mapDestZ * MAP_HIGHWAY_DIRECTIONS[i][0]);
                if (distance < nearest) {
                    nearest = distance;
                    line = i;
                }
            }
            ux = MAP_HIGHWAY_DIRECTIONS[line][0];
            uz = MAP_HIGHWAY_DIRECTIONS[line][1];
            double along = px * ux + pz * uz;
            footX = ux * along;
            footZ = uz * along;

            if ((mapDestX - footX) * ux + (mapDestZ - footZ) * uz < 0) {
                ux = -ux;
                uz = -uz;
            }
            double lateral = Math.hypot(px - footX, pz - footZ);
            if (lateral >= distToDest || mapHighwayUsable(ux, uz, footX, footZ) < MAP_HIGHWAY_MIN_FOLLOW) return false;
        }

        mapHighwayUx = ux;
        mapHighwayUz = uz;
        mapHighwayEntryX = footX;
        mapHighwayEntryZ = footZ;
        mapHighwayPhase = MAP_HIGHWAY_JOINING;
        resetHighwayRoute();
        return true;
    }

    private void endHighway(int cooldownTicks) {
        mapHighwayPhase = MAP_HIGHWAY_NONE;
        mapHighwayCooldown = cooldownTicks;
        resetHighwayRoute();
    }

    private void resetHighwayRoute() {
        clearMapRoute();
        mapDetouring = false;
        mapClearStreak = 0;
    }

    private void clearMapRoute() {
        mapRouteGeneration.incrementAndGet();
        mapRouteRequest = null;
        mapRouteNodes.clear();
        mapRouteIndex = 0;
        mapNoRoute = false;
        mapRouteEndsAtFrontier = false;
    }

    private double mapStraightBlockedAt(ClientWorld world, PlayerEntity player, Vec3d from, double dirX, double dirZ, double range) {

        double reach = 0;
        for (double d = 4; ; d += 4) {
            double along = Math.min(d, range);
            int chunkX = ((int) Math.floor(from.x + dirX * along)) >> 4;
            int chunkZ = ((int) Math.floor(from.z + dirZ * along)) >> 4;
            if (!world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) break;
            reach = along;
            if (along >= range) break;
        }
        if (reach < 1.0) return -1;

        Vec3d to = new Vec3d(from.x + dirX * reach, from.y, from.z + dirZ * reach);
        Box startBox = highwayHitboxBox(player, from.x, from.y, from.z, HIGHWAY_BLOCK_CLEARANCE);
        Box trueStart = highwayHitboxBox(player, from.x, from.y, from.z, HIGHWAY_TRUE_MARGIN);
        int steps = Math.max(1, (int) Math.ceil(reach / 0.5));
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            Vec3d point = new Vec3d(from.x + (to.x - from.x) * t, from.y, from.z + (to.z - from.z) * t);
            Box box = highwayHitboxBox(player, point.x, point.y, point.z, HIGHWAY_BLOCK_CLEARANCE);
            if (highestBlockingY(world, box, startBox) != Integer.MIN_VALUE
                || highestBlockingY(world, highwayHitboxBox(player, point.x, point.y, point.z, HIGHWAY_TRUE_MARGIN), trueStart) != Integer.MIN_VALUE
                || mapFluidNear(world, player, point, from)) {
                return reach * t;
            }
        }
        return -1;
    }

    private Vec3d mapApproachTarget(Vec3d pos, double dirX, double dirZ, double blockedAt) {
        if (blockedAt < 0) return pos;
        double go = blockedAt - MAP_OBSTACLE_STOP_DISTANCE;
        if (go < 1.0) return pos;
        return new Vec3d(pos.x + dirX * go, mapCruiseY, pos.z + dirZ * go);
    }

    private boolean mapRouteEndHasLoaded(ClientWorld world, PlayerEntity player, Vec3d pos) {
        if (mapRouteNodes.isEmpty()) return false;
        Vec3d end = mapRouteNodes.getLast();
        return !AutonavPlanner.isFrontier(new MapGrid(world, player, pos), (int) Math.floor(end.x), (int) Math.floor(end.z));
    }

    private static final class MapRouteRequest {
        final int generation;
        final ClientWorld world;
        final PlayerEntity player;
        final Vec3d start;
        final double destX, destZ;
        final double arrivalRadius;
        final int escalation;
        final int maxNodes;
        volatile boolean done;
        AutonavPlanner.Status status;
        ArrayList<Vec3d> nodes;

        MapRouteRequest(int generation, ClientWorld world, PlayerEntity player, Vec3d start, double destX, double destZ,
                        double arrivalRadius, int escalation, int maxNodes) {
            this.generation = generation;
            this.world = world;
            this.player = player;
            this.start = start;
            this.destX = destX;
            this.destZ = destZ;
            this.arrivalRadius = arrivalRadius;
            this.escalation = escalation;
            this.maxNodes = maxNodes;
        }
    }

    private void submitMapPlan(ClientWorld world, PlayerEntity player, Vec3d pos) {
        mapTicksSinceRepath = 0;

        if (mapRouteRequest != null && !mapRouteRequest.done && mapRouteRequest.generation == mapRouteGeneration.get()) return;

        if (mapRouteExecutor == null) {
            mapRouteExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "Efly-MapPlanner");
                thread.setDaemon(true);
                thread.setPriority(Thread.NORM_PRIORITY - 1);
                return thread;
            });
        }

        int budget = autonavIntelligence.get().mapSearchNodes * (1 << Math.min(mapEscalation, 2));
        MapRouteRequest request = new MapRouteRequest(mapRouteGeneration.get(), world, player, pos, mapGoalX, mapGoalZ,
            mapGoalRadius, mapEscalation, budget);
        mapRouteRequest = request;
        try {
            mapRouteExecutor.execute(() -> runMapPlan(request));
        } catch (RejectedExecutionException e) {
            mapRouteRequest = null;
        }
    }

    private void runMapPlan(MapRouteRequest request) {
        try {
            AutonavPlanner.Grid grid = new MapGrid(request.world, request.player, request.start);
            int[] start = {(int) Math.floor(request.start.x), (int) Math.floor(request.start.y), (int) Math.floor(request.start.z)};

            AutonavPlanner.Result best = null;
            for (int clearance = request.escalation > 0 ? 1 : 0; clearance >= 0; clearance--) {
                if (request.generation != mapRouteGeneration.get()) return;
                AutonavPlanner.Result result = AutonavPlanner.planTwoPass(grid, start, request.destX, request.destZ,
                    request.arrivalRadius, start[1], clearance, request.maxNodes);
                boolean found = result.status == AutonavPlanner.Status.REACHED_DESTINATION
                    || result.status == AutonavPlanner.Status.REACHED_FRONTIER;
                if (best == null || found || (best.status == AutonavPlanner.Status.SEALED)) best = result;
                if (found) break;
            }

            if (best.status == AutonavPlanner.Status.SEALED && grid.escapeCost(start[0], start[1], start[2]) != 0) {
                AutonavPlanner.Result out = AutonavPlanner.escape(grid, start, MAP_ESCAPE_RADIUS, MAP_ESCAPE_MAX_NODES);
                if (out.status != AutonavPlanner.Status.SEALED) best = out;
            }
            request.status = best.status;
            if (best.status != AutonavPlanner.Status.SEALED) {

                List<int[]> cells = best.status == AutonavPlanner.Status.ESCAPING ? best.cells : AutonavPlanner.smooth(grid, best.cells, 64);
                ArrayList<Vec3d> nodes = new ArrayList<>(cells.size());
                for (int[] cell : cells) nodes.add(new Vec3d(cell[0] + 0.5, cell[1] + HIGHWAY_NODE_HEIGHT, cell[2] + 0.5));

                if (!nodes.isEmpty()) nodes.set(0, request.start);
                request.nodes = nodes;
            }
        } catch (Throwable ignored) {

        } finally {
            request.done = true;
        }
    }

    private void applyMapRouteResult() {
        MapRouteRequest request = mapRouteRequest;
        if (request == null || !request.done) return;
        mapRouteRequest = null;
        if (request.generation != mapRouteGeneration.get() || request.status == null) return;

        if (request.status == AutonavPlanner.Status.SEALED) {
            mapRouteNodes.clear();
            mapRouteIndex = 0;
            mapNoRoute = true;
            return;
        }
        if (request.nodes == null || request.nodes.size() < 2) return;

        mapNoRoute = false;
        mapRouteNodes.clear();
        mapRouteNodes.addAll(request.nodes);
        mapRouteIndex = 0;
        mapRouteEndsAtFrontier = request.status == AutonavPlanner.Status.REACHED_FRONTIER;
    }

    private void advanceMapRouteIndex(Vec3d pos) {
        int last = mapRouteNodes.size() - 1;
        while (mapRouteIndex < last) {
            Vec3d cur = mapRouteNodes.get(mapRouteIndex);
            Vec3d seg = mapRouteNodes.get(mapRouteIndex + 1).subtract(cur);
            double len2 = seg.lengthSquared();
            if (len2 < 1.0E-6 || pos.subtract(cur).dotProduct(seg) / len2 >= 1.0) mapRouteIndex++;
            else break;
        }
    }

    private Vec3d pickMapWaypoint(PlayerEntity player, ClientWorld world, Vec3d pos) {
        int first = mapRouteIndex + 1;
        if (first >= mapRouteNodes.size()) return null;
        int last = Math.min(mapRouteNodes.size() - 1, mapRouteIndex + HIGHWAY_MAX_LOOKAHEAD_NODES);

        Box startBox = highwayHitboxBox(player, pos.x, pos.y, pos.z, HIGHWAY_BLOCK_CLEARANCE);
        for (int k = last; k > first; k--) {
            Vec3d candidate = mapRouteNodes.get(k);
            if (highwayRouteClear(world, player, pos, candidate, startBox, true)) return candidate;
        }
        return mapRouteNodes.get(first);
    }

    private final class MapGrid implements AutonavPlanner.Grid {
        private final ClientWorld world;
        private final PlayerEntity player;
        private final Vec3d start;

        MapGrid(ClientWorld world, PlayerEntity player, Vec3d start) {
            this.world = world;
            this.player = player;
            this.start = start;
        }

        @Override
        public boolean isChunkUnloaded(int chunkX, int chunkZ) {
            return !world.getChunkManager().isChunkLoaded(chunkX, chunkZ);
        }

        @Override
        public boolean isPassable(int x, int y, int z, int clearance) {
            if (isChunkUnloaded(x >> 4, z >> 4)) return false;

            double margin = clearance > 0 ? MAP_FAT_MARGIN : HIGHWAY_BLOCK_CLEARANCE - 1.0E-4;
            Vec3d point = new Vec3d(x + 0.5, y + HIGHWAY_NODE_HEIGHT, z + 0.5);
            return highwayPointFree(world, player, point, margin) && !mapFluidNear(world, player, point, start);
        }

        @Override
        public boolean lineClear(int x0, int y0, int z0, int x1, int y1, int z1) {
            Vec3d from = new Vec3d(x0 + 0.5, y0 + HIGHWAY_NODE_HEIGHT, z0 + 0.5);
            Vec3d to = new Vec3d(x1 + 0.5, y1 + HIGHWAY_NODE_HEIGHT, z1 + 0.5);
            Box startBox = highwayHitboxBox(player, from.x, from.y, from.z, HIGHWAY_BLOCK_CLEARANCE);
            return highwayRouteClear(world, player, from, to, startBox, true);
        }

        @Override
        public int escapeCost(int x, int y, int z) {
            if (isChunkUnloaded(x >> 4, z >> 4)) return -1;
            Vec3d point = new Vec3d(x + 0.5, y + HIGHWAY_NODE_HEIGHT, z + 0.5);
            if (!highwayPointFree(world, player, point, HIGHWAY_BLOCK_CLEARANCE - 1.0E-4)) return MAP_ESCAPE_SOLID_COST;
            if (mapFluidNear(world, player, point, null)) return MAP_ESCAPE_FLUID_COST;
            return 0;
        }

        @Override
        public int minY() {
            return world.getBottomY() + 1;
        }

        @Override
        public int maxY() {
            int startY = (int) Math.floor(start.y);

            if (limitMaxHeight.get()) return Math.max(flightCeilingBlock(), startY);

            int top = world.getBottomY() + world.getHeight() - 3;
            return startY >= top ? startY + MAP_ABOVE_LIMIT_ROOM : top;
        }
    }

    private static String highwayHeadingName(Vec3d axis) {
        String ns = axis.z < -0.1 ? "north" : axis.z > 0.1 ? "south" : "";
        String ew = axis.x > 0.1 ? "east" : axis.x < -0.1 ? "west" : "";
        if (ns.isEmpty()) return ew;
        if (ew.isEmpty()) return ns;
        return ns + "-" + ew;
    }

    private static Vec3d nearestHighwayDirection(double x, double z) {
        double distToXAxis = Math.abs(z);
        double distToZAxis = Math.abs(x);
        double distToDiag1 = Math.abs(z - x) / Math.sqrt(2);
        double distToDiag2 = Math.abs(z + x) / Math.sqrt(2);

        double nearest = Math.min(Math.min(distToXAxis, distToZAxis), Math.min(distToDiag1, distToDiag2));
        if (nearest == distToXAxis) return new Vec3d(1, 0, 0);
        if (nearest == distToZAxis) return new Vec3d(0, 0, 1);
        if (nearest == distToDiag1) return new Vec3d(1, 0, 1);
        return new Vec3d(1, 0, -1);
    }

    private void armHighwayAxis(PlayerEntity player) {
        double x = player.getX();
        double z = player.getZ();

        Vec3d dir = nearestHighwayDirection(x, z);

        double dot = x * dir.x + z * dir.z;
        boolean awayFromCentre = highwayDirection.get() == HighwayDirection.AwayFromCentre;
        double sign = (dot >= 0) == awayFromCentre ? 1 : -1;

        highwayAxis = new Vec3d(dir.x * sign, 0, dir.z * sign).normalize();
        highwayCruiseY = player.getY();

    }

    private void resetHighwayState() {
        highwayAxis = null;
        highwayCruiseY = Double.NaN;
        highwayGoal = null;
        highwaySteerTarget = null;
        highwayTargetY = Double.NaN;
        highwayPathGeneration.incrementAndGet();
        highwayPathRequest = null;
        highwayPathNodes.clear();
        highwayPathIndex = 0;
        highwayTicksSinceRepath = 0;
        highwayStuckTicks = 0;
        highwayMovingTicks = 0;
        highwayEscalation = 0;
        highwayLastPos = null;
        highwayRelaunchArmed = false;
    }

    private int highestBlockingY(ClientWorld world, Box box, Box ignoreBox) {
        int top = Integer.MIN_VALUE;
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int maxX = (int) Math.floor(box.maxX), maxY = (int) Math.floor(box.maxY), maxZ = (int) Math.floor(box.maxZ);
        for (int bx = (int) Math.floor(box.minX); bx <= maxX; bx++) {
            for (int by = (int) Math.floor(box.minY) - 1; by <= maxY; by++) {
                for (int bz = (int) Math.floor(box.minZ); bz <= maxZ; bz++) {
                    pos.set(bx, by, bz);
                    var shape = world.getBlockState(pos).getCollisionShape(world, pos);
                    if (shape.isEmpty()) continue;
                    Box blockBox = shape.getBoundingBox().offset(pos);
                    if (!box.intersects(blockBox)) continue;
                    if (ignoreBox != null && ignoreBox.intersects(blockBox)) continue;
                    top = Math.max(top, by);
                }
            }
        }
        return top;
    }

    private void limitHighwayVerticalStep() {
        if (Double.isNaN(highwayTargetY) || !(autohighway.get() || autonavActive) || mc.player == null) return;

        double error = highwayTargetY - mc.player.getY();
        if (velY > 0) velY = Math.min(velY, Math.max(0, error));
        else if (velY < 0) velY = Math.max(velY, Math.min(0, error));
    }

    private void applyHighwayClearanceGuard(PlayerEntity player) {
        if (!autonavActive && (!autohighway.get() || highwayAxis == null)) return;
        ClientWorld world = mc.world;
        if (world == null) return;
        if (Math.abs(velX) + Math.abs(velY) + Math.abs(velZ) < 1.0E-6) return;

        double x = player.getX(), y = player.getY(), z = player.getZ();
        Box start = highwayHitboxBox(player, x, y, z, HIGHWAY_BLOCK_CLEARANCE);
        Box hardStart = highwayHitboxBox(player, x, y, z, HIGHWAY_HARD_MARGIN);
        mapInLava = autonavActive && mapTouchesAvoidedFluid(world, player, x, y, z, true);
        mapInWater = autonavActive && mapTouchesAvoidedFluid(world, player, x, y, z, false);
        double horizontalBefore = Math.hypot(velX, velZ);

        double allowedY = clampHighwayAxis(world, player, x, y, z, 1, velY, start, hardStart);
        y += allowedY;

        double allowedX, allowedZ;
        if (Math.abs(velX) < Math.abs(velZ)) {
            allowedZ = clampHighwayAxis(world, player, x, y, z, 2, velZ, start, hardStart);
            z += allowedZ;
            allowedX = clampHighwayAxis(world, player, x, y, z, 0, velX, start, hardStart);
        } else {
            allowedX = clampHighwayAxis(world, player, x, y, z, 0, velX, start, hardStart);
            x += allowedX;
            allowedZ = clampHighwayAxis(world, player, x, y, z, 2, velZ, start, hardStart);
        }

        velX = allowedX;
        velY = allowedY;
        velZ = allowedZ;

        double horizontalAfter = Math.hypot(velX, velZ);
        if (horizontalAfter < horizontalBefore - 1.0E-3) {
            double kept = Math.max(minHorizontalSpeed(), horizontalAfter);
            if (kept < acceleration) acceleration = kept;
            atMaxSpeed = acceleration >= horizontalSpeed.get();
            accelerationDelayTicks = 0;
        }
    }

    private double clampHighwayAxis(ClientWorld world, PlayerEntity player, double x, double y, double z, int axis, double delta, Box start, Box hardStart) {
        if (Math.abs(delta) < 1.0E-9) return delta;

        int steps = Math.max(1, (int) Math.ceil(Math.abs(delta) / 0.25));
        for (int i = 1; i <= steps; i++) {
            if (!highwayAxisMoveSafe(world, player, x, y, z, axis, delta * i / steps, start, hardStart)) {
                double lo = (double) (i - 1) / steps, hi = (double) i / steps;
                for (int k = 0; k < 6; k++) {
                    double mid = (lo + hi) / 2;
                    if (highwayAxisMoveSafe(world, player, x, y, z, axis, delta * mid, start, hardStart)) lo = mid;
                    else hi = mid;
                }
                return delta * lo;
            }
        }
        return delta;
    }

    private boolean highwayAxisMoveSafe(ClientWorld world, PlayerEntity player, double x, double y, double z, int axis, double move, Box start, Box hardStart) {
        double nx = x + (axis == 0 ? move : 0), ny = y + (axis == 1 ? move : 0), nz = z + (axis == 2 ? move : 0);
        if (highestBlockingY(world, highwayHitboxBox(player, nx, ny, nz, HIGHWAY_BLOCK_CLEARANCE), start) != Integer.MIN_VALUE) return false;

        if (autonavActive && (autonavAvoidLava.get() || autonavAvoidWater.get())) {
            Box fluidBox = highwayHitboxBox(player, nx, ny, nz, MAP_FLUID_HARD_MARGIN);
            if (autonavAvoidLava.get() && !mapInLava && touchesFluid(world, fluidBox, start, true)) return false;
            if (autonavAvoidWater.get() && !mapInWater && touchesFluid(world, fluidBox, start, false)) return false;
        }

        if (highestBlockingY(world, highwayHitboxBox(player, nx, ny, nz, HIGHWAY_HARD_MARGIN), hardStart) != Integer.MIN_VALUE) return false;

        Box trueStart = highwayHitboxBox(player, x, y, z, HIGHWAY_TRUE_MARGIN);
        return highestBlockingY(world, highwayHitboxBox(player, nx, ny, nz, HIGHWAY_TRUE_MARGIN), trueStart) == Integer.MIN_VALUE;
    }

    private Box highwayHitboxBox(PlayerEntity player, double x, double y, double z, double margin) {
        Box base = player.getBoundingBox();
        double halfWidth = (base.maxX - base.minX) / 2;
        double halfDepth = (base.maxZ - base.minZ) / 2;
        double height = base.maxY - base.minY;
        return new Box(x - halfWidth, y, z - halfDepth, x + halfWidth, y + height, z + halfDepth).expand(margin);
    }

    private void releaseHighwayKeys() {
        releaseHorizontalKeys();
        if (!isKeyPhysicallyPressed(mc.options.jumpKey)) mc.options.jumpKey.setPressed(false);
        if (!isKeyPhysicallyPressed(mc.options.sneakKey)) mc.options.sneakKey.setPressed(false);
    }

    private void handleHorizontalSpeed() {
        boolean a = false;
        boolean b = false;

        if (keyDown(mc.options.forwardKey)) {
            velX += forward.x * getSpeed() * 10;
            velZ += forward.z * getSpeed() * 10;
            a = true;
        } else if (keyDown(mc.options.backKey)) {
            velX -= forward.x * getSpeed() * 10;
            velZ -= forward.z * getSpeed() * 10;
            a = true;
        }

        if (keyDown(mc.options.rightKey)) {
            velX += right.x * getSpeed() * 10;
            velZ += right.z * getSpeed() * 10;
            b = true;
        } else if (keyDown(mc.options.leftKey)) {
            velX -= right.x * getSpeed() * 10;
            velZ -= right.z * getSpeed() * 10;
            b = true;
        }

        if (a && b) {
            double diagonal = 1 / Math.sqrt(2);
            velX *= diagonal;
            velZ *= diagonal;
        }

    }

    private void handleMappingMode() {
        if (mappingMode.get()) {
            mappingWaitingForChunks = !areChunksLoadedInRadius(mappingRenderRadius.get());

            if (mappingWaitingForChunks) {
                velX = 0;
                velZ = 0;

                resetHorizontalAcceleration();
                bypassAccelerationDelayTicks = 0;
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
        if (keyDown(mc.options.jumpKey)) velY += 0.5 * verticalAcceleration;
        else if (keyDown(mc.options.sneakKey)) velY -= 0.5 * verticalAcceleration;
    }

    private void handleFallMultiplier() {
        if (velY < 0) velY *= fallMultiplier.get();
        else if (velY > 0) velY = 0;
    }

    private void handleAntiSlam() {
        if (!antiSlam.get() || velY >= 0) return;

        double currentSpeed = -velY;
        double minSpeed = antiSlamMinSpeed.get();
        if (currentSpeed <= minSpeed) return;

        double startDist = antiSlamDistance.get();
        double minDist = Math.min(antiSlamMinDistance.get(), startDist);

        double distanceToGround = distanceToGroundBelow(startDist);
        if (distanceToGround >= startDist) return;

        double allowedSpeed;
        if (distanceToGround <= minDist) {
            allowedSpeed = minSpeed;
        } else {
            double t = (distanceToGround - minDist) / Math.max(startDist - minDist, 0.0001);
            allowedSpeed = minSpeed + t * (currentSpeed - minSpeed);
        }

        if (allowedSpeed < currentSpeed) {
            velY = -allowedSpeed;
        }
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

                if (hitResult.getType() != HitResult.Type.BLOCK) continue;

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

    private boolean noMovementKeyPressed() {
        return !(keyDown(mc.options.forwardKey) || keyDown(mc.options.backKey)
            || keyDown(mc.options.leftKey) || keyDown(mc.options.rightKey));
    }

    private void resetRampOnCollision(PlayerEntity player) {
        if (lastGlidePos == null) return;
        if (!player.horizontalCollision) return;
        if (lastGlideIntendedSpeed < 1.0E-3) return;

        Vec3d now = player.getEntityPos();
        double moved = Math.hypot(now.x - lastGlidePos.x, now.z - lastGlidePos.z);

        if (moved >= lastGlideIntendedSpeed - 1.0E-3) return;

        double kept = Math.max(minHorizontalSpeed(), moved);
        if (kept < acceleration) acceleration = kept;
        atMaxSpeed = acceleration >= horizontalSpeed.get();
        accelerationDelayTicks = 0;
    }

    private void handleAcceleration() {
        if (noMovementKeyPressed()) {

            resetHorizontalAcceleration();
            return;
        }

        if (atMaxSpeed) return;

        if (accelerationDelayTicks < ACCELERATION_DELAY_TICKS) {

            accelerationDelayTicks++;
            return;
        }

        double cap = horizontalSpeedCap();
        acceleration = nextSpeedFor(acceleration, accelerationStep.get(), accelerationPlateau.get(), cap);

        if (acceleration >= horizontalSpeed.get()) {
            atMaxSpeed = true;
        }
    }

    private static double curveGain(double currentSpeed, double step, double plateau) {
        if (plateau <= 0) return 0;
        double remainingToPlateau = Math.max(0, plateau - currentSpeed);
        return step * (remainingToPlateau / plateau);
    }

    private static double nextSpeedFor(double currentSpeed, double step, double plateau, double cap) {
        return Math.min(currentSpeed + curveGain(currentSpeed, step, plateau), cap);
    }

    private static double rewindSpeedFor(double currentSpeed, double step, double plateau, double floor, int ticks) {
        double speed = currentSpeed;
        for (int i = 0; i < ticks && speed > floor; i++) {
            speed = Math.max(floor, speed - curveGain(speed, step, plateau));
        }
        return speed;
    }

    private Double speedOverride;
    private boolean externalControl;

    private double getSpeed() {
        return speedOverride != null ? speedOverride : acceleration;
    }

    public void setSpeedOverride(double speed) {
        speedOverride = Math.max(0.0, speed);
    }

    public void clearSpeedOverride() {
        if (speedOverride != null) {
            speedOverride = null;
            resetHorizontalAcceleration();
        }
    }

    public boolean hasSpeedOverride() {
        return speedOverride != null;
    }

    public void setExternalControl(boolean external) {
        externalControl = external;
        if (!external) resetHorizontalAcceleration();
    }

    public boolean isExternallyControlled() {
        return externalControl;
    }

    private void zeroAcceleration() {
        resetHorizontalAcceleration();
        resetVerticalAcceleration();
    }

    private void resetVerticalAcceleration() {
        atMaxVerticalSpeed = false;
        verticalAccelerationDelayTicks = 0;
        verticalAcceleration = minVerticalSpeed();
    }

    private void resetHorizontalAcceleration() {
        atMaxSpeed = false;
        accelerationDelayTicks = 0;
        acceleration = minHorizontalSpeed();
        bypassAccelerationDelayTicks = 0;
    }

    private double horizontalSpeedCap() {
        double cap = horizontalSpeed.get();
        if (rubberbandCapTicksLeft > 0) cap = Math.min(cap, rubberbandCapSpeed);
        return cap;
    }

    private void handleVerticalAcceleration() {
        boolean movingUp = keyDown(mc.options.jumpKey);
        boolean movingDown = keyDown(mc.options.sneakKey);

        if (!movingUp && !movingDown) {

            resetVerticalAcceleration();
            return;
        }

        if (movingUp && !accelerateUpward.get()) {

            resetVerticalAcceleration();
            return;
        }

        if (atMaxVerticalSpeed) return;

        if (verticalAccelerationDelayTicks < ACCELERATION_DELAY_TICKS) {

            verticalAccelerationDelayTicks++;
            return;
        }

        verticalAcceleration = nextSpeedFor(verticalAcceleration, verticalAccelerationStep.get(), verticalAccelerationPlateau.get(), verticalSpeed.get());

        if (verticalAcceleration >= verticalSpeed.get()) {
            atMaxVerticalSpeed = true;
        }
    }

    private void updatePlayerAvoidance() {
        if (!playerAvoidance.get()) {
            releaseAvoidance();
            releaseVerticalStep();
            return;
        }

        if (isMovementKeyPhysicallyPressed() || isKeyPhysicallyPressed(mc.options.sneakKey)) {

            releaseAvoidance();
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

        updateAvoidanceStuckDetection();
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

    private static boolean isAvoidanceBuiltInEntity(EntityType<?> type) {
        String path = Registries.ENTITY_TYPE.getId(type).getPath();
        return path.equals("player") || path.equals("wither_skull") || path.equals("arrow");
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

        Set<EntityType<?>> otherEntities = otherAvoidedEntities.get();
        if (avoidWitherSkulls.get() || avoidArrows.get() || !otherEntities.isEmpty()) {
            for (Entity entity : world.getEntities()) {
                if (avoidWitherSkulls.get() && entity instanceof WitherSkullEntity) {
                    if (accumulateThreat(entity.getEntityPos(), witherSkullRadius.get(), acc)) foundThreat = true;
                } else if (avoidArrows.get() && entity instanceof ArrowEntity) {
                    if (accumulateThreat(entity.getEntityPos(), arrowRadius.get(), acc)) foundThreat = true;
                } else if (!otherEntities.isEmpty() && entity != self && otherEntities.contains(entity.getType())) {
                    if (accumulateThreat(entity.getEntityPos(), otherEntitiesRadius.get(), acc)) foundThreat = true;
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

    private boolean isBlockAvoidanceTriggered() {
        PlayerEntity self = mc.player;
        ClientWorld world = mc.world;
        if (self == null || world == null) return false;

        Vec3d selfPos = self.getEntityPos();

        double radius = avoidanceRadius.get();
        if (radius > 0) {
            double radiusSq = radius * radius;
            for (PlayerEntity player : world.getPlayers()) {
                if (player == self) continue;
                if (avoidanceIgnoreFriends.get() && Friends.get().isFriend(player)) continue;
                if (selfPos.squaredDistanceTo(player.getEntityPos()) < radiusSq) return true;
            }
        }

        double skullRadius = witherSkullRadius.get();
        if (avoidWitherSkulls.get() && skullRadius > 0) {
            double skullRadiusSq = skullRadius * skullRadius;
            for (Entity entity : world.getEntities()) {
                if (entity instanceof WitherSkullEntity && selfPos.squaredDistanceTo(entity.getEntityPos()) < skullRadiusSq) return true;
            }
        }

        return false;
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
        if (avoidanceStuckTicksCount < AVOIDANCE_STUCK_TICKS) return;

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
        releaseHorizontalKeys();
        avoidanceSteering = false;
    }

    private boolean isMovementKeyPhysicallyPressed() {
        return isKeyPhysicallyPressed(mc.options.forwardKey)
            || isKeyPhysicallyPressed(mc.options.backKey)
            || isKeyPhysicallyPressed(mc.options.leftKey)
            || isKeyPhysicallyPressed(mc.options.rightKey);
    }

    private boolean freecamActive() {
        if (Modules.get().isActive(meteordevelopment.meteorclient.systems.modules.render.Freecam.class)) return true;
        if (mc.player == null) return false;
        if (mc.getCameraEntity() != null && mc.getCameraEntity() != mc.player) return true;

        boolean keysDown = mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed() || mc.options.leftKey.isPressed()
            || mc.options.rightKey.isPressed() || mc.options.jumpKey.isPressed() || mc.options.sneakKey.isPressed();
        if (!keysDown || mc.player.input == null) return false;

        net.minecraft.util.math.Vec2f movement = mc.player.input.getMovementInput();
        return movement.x == 0.0F && movement.y == 0.0F && mc.currentScreen == null
            && !mc.player.input.playerInput.forward() && !mc.player.input.playerInput.backward()
            && !mc.player.input.playerInput.left() && !mc.player.input.playerInput.right()
            && !mc.player.input.playerInput.jump() && !mc.player.input.playerInput.sneak();
    }

    private boolean keyDown(KeyBinding binding) {
        return !freecamActive() && binding.isPressed();
    }

    private boolean isKeyPhysicallyPressed(KeyBinding binding) {
        if (freecamActive()) return false;
        if (gamerguy11.sixtoolsaddon.efly.pathing.EflyGoto.isHoldingKey(binding)) return true;
        if (gamerguy11.sixtoolsaddon.modules.ForeverForward.isHolding(binding)) return true;
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

    private ClientWorld trackedWorld;

    private void cancelPendingActions() {
        disableGroundListener();
        disableInstaDropListener();
    }

    private class StaticGroundListener {
        @SuppressWarnings("unused")
        @EventHandler
        private void onGameLeft(GameLeftEvent event) {
            disableGroundListener();
        }

        @SuppressWarnings("unused")
        @EventHandler
        private void chestSwapGroundListener(PlayerMoveEvent event) {
            if (mc.world != groundListenerWorld) {
                disableGroundListener();
                return;
            }

            PlayerEntity player = mc.player;
            if (player == null || !player.isOnGround()) return;

            if (player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {
                swapToChestSwap();
            }
            disableGroundListener();
        }
    }

    private final StaticGroundListener staticGroundListener = new StaticGroundListener();
    private boolean groundListenerActive;
    private ClientWorld groundListenerWorld;

    protected void enableGroundListener() {
        if (groundListenerActive) return;
        groundListenerActive = true;
        groundListenerWorld = mc.world;
        MeteorClient.EVENT_BUS.subscribe(staticGroundListener);
    }

    protected void disableGroundListener() {
        if (!groundListenerActive) return;
        groundListenerActive = false;
        groundListenerWorld = null;
        MeteorClient.EVENT_BUS.unsubscribe(staticGroundListener);
    }

    private class StaticInstaDropListener {
        @SuppressWarnings("unused")
        @EventHandler
        private void onGameLeft(GameLeftEvent event) {
            disableInstaDropListener();
        }

        @SuppressWarnings("unused")
        @EventHandler
        private void onInstadropTick(TickEvent.Post event) {
            if (mc.world != instaDropListenerWorld) {
                disableInstaDropListener();
                return;
            }

            ClientPlayerEntity player = mc.player;
            if (player != null && player.isGliding()) {
                player.setVelocity(0, 0, 0);
                player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true, player.horizontalCollision));
            } else {
                disableInstaDropListener();
            }
        }
    }

    private final StaticInstaDropListener staticInstadropListener = new StaticInstaDropListener();
    private boolean instaDropListenerActive;
    private ClientWorld instaDropListenerWorld;

    protected void enableInstaDropListener() {
        if (instaDropListenerActive) return;
        instaDropListenerActive = true;
        instaDropListenerWorld = mc.world;
        MeteorClient.EVENT_BUS.subscribe(staticInstadropListener);
    }

    protected void disableInstaDropListener() {
        if (!instaDropListenerActive) return;
        instaDropListenerActive = false;
        instaDropListenerWorld = null;
        MeteorClient.EVENT_BUS.unsubscribe(staticInstadropListener);
    }

    public enum ChestSwapMode {
        Always,
        Never,
        WaitForGround
    }

    public enum Intelligence {
        Low(8, 20, 5.0, 25_000),
        Medium(64, 8, 3.0, 80_000),
        High(256, 6, 2.0, 200_000);

        final int pathRange;
        final int repathInterval;
        final double stuckTimeout;

        final int mapSearchNodes;

        Intelligence(int pathRange, int repathInterval, double stuckTimeout, int mapSearchNodes) {
            this.pathRange = pathRange;
            this.repathInterval = repathInterval;
            this.stuckTimeout = stuckTimeout;
            this.mapSearchNodes = mapSearchNodes;
        }
    }

    public enum MapClickButton {
        Middle(GLFW.GLFW_MOUSE_BUTTON_MIDDLE),
        Right(GLFW.GLFW_MOUSE_BUTTON_RIGHT),
        Left(GLFW.GLFW_MOUSE_BUTTON_LEFT);

        final int button;

        MapClickButton(int button) {
            this.button = button;
        }
    }

    public enum HighwayDirection {
        AwayFromCentre,
        TowardsCentre
    }

}
