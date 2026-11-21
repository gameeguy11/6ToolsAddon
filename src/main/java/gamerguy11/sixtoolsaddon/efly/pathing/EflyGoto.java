package gamerguy11.sixtoolsaddon.efly.pathing;

import gamerguy11.sixtoolsaddon.modules.Efly;
import gamerguy11.sixtoolsaddon.efly.pathing.BrainImpl;
import gamerguy11.sixtoolsaddon.efly.pathing.Brain;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import meteordevelopment.meteorclient.MeteorClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.component.EnchantmentEffectComponentTypes;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

public class EflyGoto extends Module {
    public enum Arrive { Land, Hover }

    public enum Rotate { ServerOnly, Camera, None }

    public static EflyGoto INSTANCE;
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("EflyGoto");

    public final SettingGroup sgGeneral = settings.getDefaultGroup();
    public final SettingGroup sgFlight = settings.createGroup("Flight");
    public final SettingGroup sgPathing = settings.createGroup("Pathing");
    public final SettingGroup sgRender = settings.createGroup("Render");

    public final Setting<Integer> targetX = sgGeneral.add(new IntSetting.Builder()
        .name("x")
        .description("Where to fly to (X). Or use .vgoto x z")
        .defaultValue(0)
        .range(-30_000_000, 30_000_000)
        .noSlider()
        .build()
    );

    public final Setting<Integer> targetZ = sgGeneral.add(new IntSetting.Builder()
        .name("z")
        .description("Where to fly to (Z).")
        .defaultValue(0)
        .range(-30_000_000, 30_000_000)
        .noSlider()
        .build()
    );

    public final Setting<Arrive> arrive = sgGeneral.add(new EnumSetting.Builder<Arrive>()
        .name("arrive")
        .description("Land: touch down on the ground at x z. Hover: stop in the air at x y z (.vgoto x y z sets this).")
        .defaultValue(Arrive.Land)
        .build()
    );

    public final Setting<Integer> targetY = sgGeneral.add(new IntSetting.Builder()
        .name("y")
        .description("Height to stop at when arrive is Hover.")
        .defaultValue(120)
        .range(-64, 320)
        .sliderRange(-64, 320)
        .visible(() -> arrive.get() == Arrive.Hover)
        .build()
    );

    public final Setting<Integer> cruiseHeight = sgFlight.add(new IntSetting.Builder()
        .name("cruise-height")
        .description("Height to travel at in the Overworld and the End while the destination is still far away (climbs higher over anything taller). The Nether keeps the height you start at.")
        .defaultValue(200)
        .range(-64, 320)
        .sliderRange(64, 320)
        .build()
    );

    public final Setting<Double> maxSpeed = sgFlight.add(new DoubleSetting.Builder()
        .name("max-speed")
        .description("Caps the horizontal speed (blocks per tick). 0 = whatever Efly is set to.")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 15)
        .build()
    );

    public final Setting<Boolean> waitForChunks = sgFlight.add(new BoolSetting.Builder()
        .name("wait-for-chunks")
        .description("Never fly into chunks that haven't loaded yet: stops at the edge until they do. Off = treat unloaded chunks as empty air (you can crash into terrain you can't see).")
        .defaultValue(true)
        .build()
    );

    public final Setting<Rotate> rotate = sgFlight.add(new EnumSetting.Builder<Rotate>()
        .name("rotate")
        .description("ServerOnly: the server sees you facing the way you fly, your camera stays free (like Baritone's free look). Camera: turn your camera too. None: don't touch rotation.")
        .defaultValue(Rotate.ServerOnly)
        .build()
    );

    public final Setting<Integer> cruiseDistance = sgFlight.add(new IntSetting.Builder()
        .name("cruise-distance")
        .description("Only climb to cruise-height when the destination is at least this far away; shorter trips stay near the height you're at.")
        .defaultValue(500)
        .range(0, 100_000)
        .sliderRange(0, 2000)
        .build()
    );

    public final Setting<Double> hitboxPadding = sgFlight.add(new DoubleSetting.Builder()
        .name("hitbox-padding")
        .description("Extra room kept around you when flying straight between path points. Raise it (~1) if the server pulls you back near walls.")
        .defaultValue(0.2)
        .range(0, 1.5)
        .sliderRange(0, 1.5)
        .build()
    );

    public final Setting<Integer> setbackPause = sgFlight.add(new IntSetting.Builder()
        .name("setback-pause")
        .description("When the server pulls you back (rubberband), hover this many ticks before carrying on.")
        .defaultValue(10)
        .range(0, 100)
        .sliderRange(0, 40)
        .build()
    );

    public final Setting<Boolean> slowOnSetback = sgFlight.add(new BoolSetting.Builder()
        .name("slow-on-setback")
        .description("Each rubberband lowers the speed a bit (it creeps back up after a while with none).")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> enableEfly = sgFlight.add(new BoolSetting.Builder()
        .name("enable-efly")
        .description("Turn Efly on when a goto starts (and off again after landing, if it was off before).")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> logoutOnArrival = sgFlight.add(new BoolSetting.Builder()
        .name("logout-on-arrival")
        .description("Disconnect once it has landed at (or reached, in hover mode) the destination. Not after an emergency landing. Turns Auto Reconnect off so it stays logged out.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> portalMessages = sgFlight.add(new BoolSetting.Builder()
        .name("portal-messages")
        .description("Print a chat message for every Nether portal it flies past (only when portal-stop is off).")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> portalStop = sgFlight.add(new BoolSetting.Builder()
        .name("portal-stop")
        .description("If a Nether portal shows up on the way, land next to it and log out instead of carrying on to the destination.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Integer> portalIgnoreRadius = sgFlight.add(new IntSetting.Builder()
        .name("portal-ignore-radius")
        .description("Ignore portals this close to where the flight started (the one you just came out of).")
        .defaultValue(64)
        .range(0, 1000)
        .sliderRange(0, 300)
        .visible(portalStop::get)
        .build()
    );

    public final SettingGroup sgElytra = settings.createGroup("Elytra");

    public final Setting<Integer> minDurability = sgElytra.add(new IntSetting.Builder()
        .name("min-durability")
        .description("Elytra durability to act at (gliding uses 1 per second).")
        .defaultValue(15)
        .range(2, 400)
        .sliderRange(2, 100)
        .build()
    );

    public final Setting<Boolean> swapElytra = sgElytra.add(new BoolSetting.Builder()
        .name("swap-elytra")
        .description("Put on a fresher elytra from your inventory when the worn one gets to min-durability.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> mendWithXp = sgElytra.add(new BoolSetting.Builder()
        .name("mend-with-xp")
        .description("Elytra has Mending and you carry XP bottles: at mend-at durability, stop a few blocks above the ground and throw bottles until it's repaired (tried before swapping elytras).")
        .defaultValue(true)
        .build()
    );

    public final Setting<Integer> mendAt = sgElytra.add(new IntSetting.Builder()
        .name("mend-at")
        .description("Durability to start mending at. Leave room above min-durability: it may have to go down to the ground first.")
        .defaultValue(80)
        .range(2, 430)
        .sliderRange(20, 300)
        .visible(mendWithXp::get)
        .build()
    );

    public final Setting<Boolean> emergencyLand = sgElytra.add(new BoolSetting.Builder()
        .name("emergency-land")
        .description("No spare elytra: land where you are instead of flying on until it breaks mid-air.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Integer> searchRadius = sgPathing.add(new IntSetting.Builder()
        .name("search-radius")
        .description("How far ahead each path is planned (also capped by your render distance).")
        .defaultValue(160)
        .range(32, 512)
        .sliderRange(32, 256)
        .build()
    );

    public final Setting<Integer> maxNodes = sgPathing.add(new IntSetting.Builder()
        .name("max-nodes")
        .description("Most blocks one search may look at before it settles for the closest it got.")
        .defaultValue(250_000)
        .range(10_000, 3_000_000)
        .sliderRange(50_000, 1_000_000)
        .build()
    );

    public final Setting<Integer> computeMs = sgPathing.add(new IntSetting.Builder()
        .name("ms-per-tick")
        .description("Milliseconds of each game tick spent path searching (more = faster paths, lower FPS while searching).")
        .defaultValue(8)
        .range(1, 40)
        .sliderRange(1, 25)
        .build()
    );

    public final Setting<Boolean> allowLowGaps = sgPathing.add(new BoolSetting.Builder()
        .name("allow-1-high-gaps")
        .description("Let paths go through 1x1 holes and 1-high crawl gaps (an open elytra is only 0.6 tall). Only used when there's no roomier way. If the elytra closes in one you drop to the floor and have to crawl out - it can't take off there.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Double> wallAvoidance = sgPathing.add(new DoubleSetting.Builder()
        .name("wall-avoidance")
        .description("How hard paths try to keep a block away from walls, floors and ceilings.")
        .defaultValue(0.6)
        .range(0, 5)
        .sliderRange(0, 3)
        .build()
    );

    public final Setting<Boolean> mineThrough = sgPathing.add(new BoolSetting.Builder()
        .name("mine-through-walls")
        .description("When there's no way around (no route found, or stuck), dig 1x1 holes through walls. Never next to water/lava, under sand/gravel, or obsidian-hard blocks. Mining while flying is 5x slower than on the ground.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Integer> mineCost = sgPathing.add(new IntSetting.Builder()
        .name("mine-cost")
        .description("While digging is allowed: how many blocks of detour digging one block is worth (higher = only dig when going around is really long).")
        .defaultValue(40)
        .range(1, 1000)
        .sliderRange(5, 200)
        .visible(mineThrough::get)
        .build()
    );

    public final Setting<Double> braking = sgFlight.add(new DoubleSetting.Builder()
        .name("braking")
        .description("Speed allowed per block of straight, clear way ahead (blocks/tick per block). Lower = slows down earlier before turns, walls and the end of the path; higher = keeps speed longer.")
        .defaultValue(0.25)
        .range(0.02, 2)
        .sliderRange(0.05, 1)
        .build()
    );

    public final Setting<Boolean> keepVfSink = sgFlight.add(new BoolSetting.Builder()
        .name("keep-efly-sink")
        .description("Efly always sinks a tiny bit while gliding (its fall-multiplier). Keep that while flying level or hovering instead of holding perfectly still - an elytra that never drops looks like flying to the server.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Double> wallDistance = sgFlight.add(new DoubleSetting.Builder()
        .name("wall-distance")
        .description("Room to keep between you and blocks when cutting corners, where there's space for it (tight spots still squeeze through with hitbox-padding).")
        .defaultValue(1.0)
        .range(0, 4)
        .sliderRange(0, 3)
        .build()
    );

    public final Setting<Boolean> followVfRamp = sgFlight.add(new BoolSetting.Builder()
        .name("follow-efly-ramp")
        .description("Never go faster than Efly itself would right now, so speed builds up the way Efly's does (after take-off, after bumping into things). acceleration and braking still apply on top.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Double> acceleration = sgFlight.add(new DoubleSetting.Builder()
        .name("acceleration")
        .description("How fast it speeds back up on straight stretches (blocks/tick gained per tick), up to Efly's horizontal speed / max-speed.")
        .defaultValue(0.2)
        .range(0.01, 5)
        .sliderRange(0.05, 2)
        .build()
    );

    public final Setting<Boolean> renderPath = sgRender.add(new BoolSetting.Builder()
        .name("render-path")
        .description("Draw the planned path.")
        .defaultValue(true)
        .build()
    );

    public final Setting<SettingColor> pathColor = sgRender.add(new ColorSetting.Builder()
        .name("path-color")
        .defaultValue(new SettingColor(255, 128, 0, 255))
        .visible(renderPath::get)
        .build()
    );

    public final Setting<SettingColor> goalColor = sgRender.add(new ColorSetting.Builder()
        .name("goal-color")
        .defaultValue(new SettingColor(0, 255, 70, 255))
        .visible(renderPath::get)
        .build()
    );

    public final SettingGroup sgDebug = settings.createGroup("Debug");

    public final Setting<Boolean> debugLog = sgDebug.add(new BoolSetting.Builder()
        .name("debug-log")
        .description("Write what the flight logic is doing to latest.log (lines starting with [VG]), once a second and at every event. For bug reports.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> resumeAfterReconnect = sgFlight.add(new BoolSetting.Builder()
        .name("resume-after-reconnect")
        .description("If you get disconnected (kicked, lag) mid-flight, fly to the same destination again once you are back on the same server and in the same dimension. Does nothing if you stopped the flight yourself.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> preflightCheck = sgFlight.add(new BoolSetting.Builder()
        .name("preflight-check")
        .description("Before a flight, warn about a worn or missing elytra, no spare elytra and no food in your inventory.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Integer> preflightMinDurability = sgFlight.add(new IntSetting.Builder()
        .name("preflight-min-durability")
        .description("Warn if the elytra you wear has less than this much durability left (percent).")
        .defaultValue(25)
        .min(1)
        .max(100)
        .sliderRange(1, 100)
        .visible(preflightCheck::get)
        .build()
    );

    public boolean weEnabledVf;

    private Brain brain;
    private int brainErrors;
    private long brainErrorMsgAt;

    public EflyGoto() {
        super(gamerguy11.sixtoolsaddon.SixToolsAddon.CATEGORY, "efly-goto", "Baritone-style goto that flies with Efly. Use .vgoto x z (land) or .vgoto x y z (hover), .vgoto stop.");
        INSTANCE = this;
        MeteorClient.EVENT_BUS.subscribe(resumer);
    }

    private Brain brain() {
        if (brain == null) brain = new BrainImpl(this);
        return brain;
    }

    private boolean safe(String what, Runnable r) {
        try {
            r.run();
            return true;
        }
        catch (Throwable t) {
            LOG.error("[VG] error in " + what, t);
            long now = System.currentTimeMillis();
            if (now - brainErrorMsgAt > 5000) {
                brainErrorMsgAt = now;
                error("Error in %s: %s (details in latest.log)", what, t.toString());
            }
            if (++brainErrors > 40 && isActive()) {
                brainErrors = 0;
                error("Too many errors - stopping.");
                toggle();
            }
            return false;
        }
    }

    public static boolean isHoldingKey(KeyBinding key) {
        EflyGoto g = INSTANCE;
        if (g == null || !g.isActive() || g.brain == null) return false;
        try {
            return g.brain.holdingKey(key);
        }
        catch (Throwable t) {
            return false;
        }
    }

    public void go(int x, Integer y, int z) {
        if (preflightCheck.get() && !isActive()) preflight();
        resumeTarget = null;
        flightX = x;
        flightY = y;
        flightZ = z;
        safe("go", () -> brain().go(x, y, z));
    }

    public String statusLine() {
        try {
            return brain().statusLine();
        }
        catch (Throwable t) {
            return "error: " + t;
        }
    }

    @Override
    public void onActivate() {
        brainErrors = 0;
        rememberFlightWorld();
        if (!safe("activate", () -> brain().activate()) && isActive()) toggle();
    }

    @Override
    public void onDeactivate() {
        safe("deactivate", () -> brain().deactivate());
        gamerguy11.sixtoolsaddon.basehunting.HuntWaypoints.clearAutonav();
        KeyBinding fwd = mc.options.forwardKey;
        fwd.setPressed(Input.isPressed(fwd));
    }

    @Override
    public String getInfoString() {
        try {
            return brain().infoString();
        }
        catch (Throwable t) {
            return null;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (safe("tick", () -> brain().tick(event)) && brainErrors > 0 && mc.player != null && mc.player.age % 20 == 0) brainErrors--;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onMove(PlayerMoveEvent event) {
        safe("move", () -> brain().move(event));
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        safe("packet", () -> brain().packet(event));
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        safe("render", () -> brain().render(event));
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (isActive()) {
            if (resumeAfterReconnect.get() && flightServer != null) {
                resumeTarget = new int[]{flightX, flightY == null ? Integer.MIN_VALUE : flightY, flightZ};
                resumeServer = flightServer;
                resumeDimension = flightDimension;
                resumeWaitTicks = 0;
                resumeLeftAt = System.currentTimeMillis();
            }
            toggle();
        }
    }

    private int flightX, flightZ;
    private Integer flightY;
    private String flightServer, flightDimension;
    private int[] resumeTarget;
    private String resumeServer, resumeDimension;
    private int resumeWaitTicks;
    private long resumeLeftAt;

    private static String serverAddress() {
        var entry = MinecraftClient.getInstance().getCurrentServerEntry();
        return entry == null ? null : entry.address;
    }

    private void rememberFlightWorld() {
        flightServer = serverAddress();
        flightDimension = mc.world == null ? null : mc.world.getRegistryKey().getValue().toString();
    }

    public final Object resumer = new Object() {
        @EventHandler
        private void onTick(TickEvent.Post event) {
            if (resumeTarget == null) return;
            if (isActive()) {
                resumeTarget = null;
                return;
            }
            if (mc.player == null || mc.world == null) return;
            if (++resumeWaitTicks < 100) return;
            int[] t = resumeTarget;
            resumeTarget = null;
            if (System.currentTimeMillis() - resumeLeftAt > 5 * 60_000L) return;
            String dim = mc.world.getRegistryKey().getValue().toString();
            if (!java.util.Objects.equals(serverAddress(), resumeServer) || !dim.equals(resumeDimension)) {
                warning("Not resuming the flight: you are on a different server or in a different dimension now.");
                return;
            }
            info("Resuming the flight to %d %d.", t[0], t[2]);
            go(t[0], t[1] == Integer.MIN_VALUE ? null : t[1], t[2]);
        }
    };

    private void preflight() {
        if (mc.player == null) return;
        var inv = mc.player.getInventory();
        ItemStack worn = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        boolean wearing = worn.isOf(Items.ELYTRA);
        int spares = 0;
        boolean food = false;
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty()) continue;
            if (s.isOf(Items.ELYTRA) && s.getMaxDamage() - s.getDamage() > 1) spares++;
            if (s.contains(net.minecraft.component.DataComponentTypes.FOOD)) food = true;
        }
        if (mc.player.getOffHandStack().contains(net.minecraft.component.DataComponentTypes.FOOD)) food = true;

        if (!wearing && spares == 0) warning("Pre-flight: you have no elytra.");
        else if (wearing) {
            int max = Math.max(1, worn.getMaxDamage());
            int left = (max - worn.getDamage()) * 100 / max;
            if (left < preflightMinDurability.get()) warning("Pre-flight: your elytra is at %d%% durability.", left);
        }
        if (spares == 0 && wearing) warning("Pre-flight: no spare elytra in your inventory.");
        if (!food) warning("Pre-flight: no food in your inventory.");
    }
}
