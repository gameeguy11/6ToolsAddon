package gamerguy11.sixtoolsaddon.efly.pathing;

import gamerguy11.sixtoolsaddon.modules.Efly;
import gamerguy11.sixtoolsaddon.efly.pathing.Brain;
import gamerguy11.sixtoolsaddon.efly.pathing.FlightPathfinder;
import gamerguy11.sixtoolsaddon.efly.pathing.EflyGoto.Arrive;
import gamerguy11.sixtoolsaddon.efly.pathing.EflyGoto.Rotate;
import gamerguy11.sixtoolsaddon.efly.pathing.EflyGoto;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.mixin.PlayerMoveC2SPacketAccessor;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
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
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
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

public class BrainImpl implements Brain {
    private static final Set<Block> BAD_FLOOR = Set.of(Blocks.MAGMA_BLOCK, Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE, Blocks.CACTUS,
        Blocks.SWEET_BERRY_BUSH, Blocks.POWDER_SNOW, Blocks.POINTED_DRIPSTONE, Blocks.WITHER_ROSE, Blocks.FIRE, Blocks.SOUL_FIRE,
        Blocks.LAVA_CAULDRON, Blocks.NETHER_PORTAL, Blocks.END_PORTAL, Blocks.END_GATEWAY, Blocks.SCAFFOLDING, Blocks.COBWEB);

    private enum Phase { TakeOff, Flying, Landing }

    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private final EflyGoto m;

    private final Setting<Integer> targetX;
    private final Setting<Integer> targetZ;
    private final Setting<Arrive> arrive;
    private final Setting<Integer> targetY;
    private final Setting<Integer> cruiseHeight;
    private final Setting<Double> maxSpeed;
    private final Setting<Boolean> waitForChunks;
    private final Setting<Rotate> rotate;
    private final Setting<Integer> cruiseDistance;
    private final Setting<Double> hitboxPadding;
    private final Setting<Integer> setbackPause;
    private final Setting<Boolean> slowOnSetback;
    private final Setting<Boolean> enableEfly;
    private final Setting<Boolean> logoutOnArrival;
    private final Setting<Boolean> portalStop;
    private final Setting<Integer> portalIgnoreRadius;
    private final Setting<Integer> minDurability;
    private final Setting<Boolean> swapElytra;
    private final Setting<Boolean> mendWithXp;
    private final Setting<Integer> mendAt;
    private final Setting<Boolean> emergencyLand;
    private final Setting<Integer> searchRadius;
    private final Setting<Integer> maxNodes;
    private final Setting<Integer> computeMs;
    private final Setting<Boolean> allowLowGaps;
    private final Setting<Double> wallAvoidance;
    private final Setting<Boolean> mineThrough;
    private final Setting<Integer> mineCost;
    private final Setting<Double> braking;
    private final Setting<Boolean> keepVfSink;
    private final Setting<Double> wallDistance;
    private final Setting<Boolean> followVfRamp;
    private final Setting<Double> acceleration;
    private final Setting<Boolean> renderPath;
    private final Setting<SettingColor> pathColor;
    private final Setting<SettingColor> goalColor;

    private Efly vf;
    private RegistryKey<World> dimension;
    private Phase phase;
    private boolean holdingForward;

    private FlightPathfinder search;
    private List<Vec3d> path = new ArrayList<>();
    private boolean pathReachesGoal, pathEndsAtFrontier;
    private int pathIdx;
    private BlockPos goalNode;
    private int goalCheckAge, ticksSinceSearch, failures, retryIn, noCarrotTicks, waitMsgAge, takeoffWait, groundTicks;
    private boolean replanNow, landingWarned, hoverOnly, emergencyLanding;
    private int setbackTicks, swapCooldown, jumps, lowCeilingWarned;
    private double setbackCap = Double.POSITIVE_INFINITY, setbackCapV = Double.POSITIVE_INFINITY;
    private double curV;
    private static final double MIN_V = 0.5;
    private static final DebugLog LOG = new DebugLog();
    private Vec3d dbgVfMove = Vec3d.ZERO, dbgOurMove = Vec3d.ZERO, dbgCarrot;
    private double dbgSH;
    private int dbgSetbacks;
    private int ticksSinceSetback, setbackMsgAge = -10000;
    private double bestProgress;
    private int noProgressTicks, stuckStrikes, exemptTicks, lineupTicks;
    private volatile int srvGlide = -1;
    private volatile boolean srvSeenTrue;
    private int pendingStartAge = -1, startSentAge = -100000, desyncFixes, unconfirmedTicks;
    private boolean glidingLastTick;
    private static final double NODE_Y = 0.18;
    private int loopLogAge = -10000, tunnelEndAge = -100000;
    private float camYaw = Float.NaN, camPitch;
    private long lastTickNano;
    private double tickDtMs = 50;
    private final float[] dtRing = new float[60];
    private int dtIdx, catchups, pairedWarnAge = -100000;
    private boolean pairedTicks;
    private final Vec3d[] posRing = new Vec3d[30];
    private java.lang.reflect.Field minimizedField;
    private boolean minimizedLookupFailed;
    private int minimizedLogAge = -100000;

    private void keepFrameRate() {
        try {
            net.minecraft.client.util.Window w = mc.getWindow();
            if (!w.isMinimized() || minimizedLookupFailed) return;
            if (minimizedField == null) {
                for (String n : new String[] {"field_52735", "minimized"}) {
                    try {
                        java.lang.reflect.Field f = net.minecraft.client.util.Window.class.getDeclaredField(n);
                        if (f.getType() != boolean.class) continue;
                        f.setAccessible(true);
                        minimizedField = f;
                        break;
                    }
                    catch (NoSuchFieldException ignored) {
                    }
                }
                if (minimizedField == null) {
                    minimizedLookupFailed = true;
                    LOG.warn("[VG] can't find the window's minimised flag - the game will run at 10 FPS while minimised");
                    return;
                }
            }
            if (!minimizedField.getBoolean(w)) return;
            minimizedField.setBoolean(w, false);
            if (w.isMinimized()) {
                minimizedField.setBoolean(w, true);
                minimizedLookupFailed = true;
                return;
            }
            if (mc.player.age - minimizedLogAge > 6000) {
                minimizedLogAge = mc.player.age;
                LOG.info("[VG] window minimised: keeping the game at its normal tick pace for the flight (no 10 FPS throttle)");
            }
        }
        catch (Throwable t) {
            minimizedLookupFailed = true;
            LOG.warn("[VG] couldn't lift the minimised-window throttle", t);
        }
    }

    private void restoreFrameRate() {
        try {
            if (minimizedField == null) return;
            net.minecraft.client.util.Window w = mc.getWindow();
            boolean real = org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(w.getHandle(), org.lwjgl.glfw.GLFW.GLFW_ICONIFIED) == org.lwjgl.glfw.GLFW.GLFW_TRUE;
            minimizedField.setBoolean(w, real);
        }
        catch (Throwable ignored) {
        }
    }
    private int posIdx;

    private void logHeatSource() {
        double best = Double.MAX_VALUE;
        String what = null;
        BlockPos.Mutable q = new BlockPos.Mutable();
        for (int k = 0; k < posRing.length; k++) {
            Vec3d pp = posRing[((posIdx - 1 - k) % posRing.length + posRing.length) % posRing.length];
            if (pp == null) continue;
            int bx = MathHelper.floor(pp.x), by = MathHelper.floor(pp.y), bz = MathHelper.floor(pp.z);
            for (int dx = -2; dx <= 2; dx++) for (int dy = -2; dy <= 3; dy++) for (int dz = -2; dz <= 2; dz++) {
                q.set(bx + dx, by + dy, bz + dz);
                BlockState st = mc.world.getBlockState(q);
                boolean lava = st.getFluidState().isIn(FluidTags.LAVA), fire = st.isOf(Blocks.FIRE) || st.isOf(Blocks.SOUL_FIRE);
                if (!lava && !fire) continue;
                double gx = Math.max(0, Math.max(q.getX() - (pp.x + 0.3), (pp.x - 0.3) - (q.getX() + 1)));
                double gy = Math.max(0, Math.max(q.getY() - (pp.y + 0.6), pp.y - (q.getY() + 1)));
                double gz = Math.max(0, Math.max(q.getZ() - (pp.z + 0.3), (pp.z - 0.3) - (q.getZ() + 1)));
                double g = Math.sqrt(gx * gx + gy * gy + gz * gz);
                if (g < best) {
                    best = g;
                    what = String.format("%s at %d %d %d, %.2f from the hitbox %d ticks ago (side %.2f, above %.2f, below %.2f)", lava ? "lava" : "fire",
                        q.getX(), q.getY(), q.getZ(), g, k, Math.hypot(gx, gz), Math.max(0, q.getY() - (pp.y + 0.6)), Math.max(0, pp.y - (q.getY() + 1)));
                }
            }
        }
        LOG.info("[VG] heat source: {}", what == null ? "no lava or fire within 2 blocks of the last 30 positions" : what);
    }
    private Vec3d takeoffAnchor;
    private int takeoffStill, takeoffForce, footSetbacks, footSetbackAge = -100000;
    private boolean lowTakeoff;
    private int footTicks;
    private String takeoffDoing = "nothing";
    private final LongOpenHashSet plannedMine = new LongOpenHashSet();
    private int unplannedLogAge = -10000;
    private boolean instantOff, ahSuspended;
    private int lastGroundDigAge = -100000;

    private boolean weEnabledAntiHunger, weEnabledSpeed;

    private void disableHelpers() {
        try {
            if (weEnabledAntiHunger) {
                var ah = Modules.get().get(meteordevelopment.meteorclient.systems.modules.player.AntiHunger.class);
                if (ah != null && ah.isActive()) ah.toggle();
            }
            if (weEnabledSpeed) {
                var sp = Modules.get().get(meteordevelopment.meteorclient.systems.modules.movement.speed.Speed.class);
                if (sp != null && sp.isActive()) sp.toggle();
            }
        }
        catch (Throwable t) {
            LOG.warn("[VG] couldn't turn Anti-Hunger / Speed off", t);
        }
        weEnabledAntiHunger = false;
        weEnabledSpeed = false;
    }

    private void enableHelpers() {
        weEnabledAntiHunger = false;
        weEnabledSpeed = false;
        if (!Tuning.b("helpers.enable", true)) return;
        try {
            var ah = Modules.get().get(meteordevelopment.meteorclient.systems.modules.player.AntiHunger.class);
            if (ah != null && !ah.isActive()) {
                ah.toggle();
                weEnabledAntiHunger = true;
                m.info("Turned Anti-Hunger on.");
            }
            var sp = Modules.get().get(meteordevelopment.meteorclient.systems.modules.movement.speed.Speed.class);
            if (sp != null && !sp.isActive()) {
                sp.toggle();
                weEnabledSpeed = true;
                m.info("Turned Speed on.");
            }
        }
        catch (Throwable t) {
            LOG.warn("[VG] couldn't turn Anti-Hunger / Speed on", t);
        }
    }

    private double walkSpeed() {
        double def = Tuning.d("tunnel.walk.speed", 0.2);
        try {
            var sp = Modules.get().get(meteordevelopment.meteorclient.systems.modules.movement.speed.Speed.class);
            if (sp == null || !sp.isActive() || !Tuning.b("walk.use.speed", true)) return def;
            Setting<?> v = sp.settings.get("vanilla-speed");
            if (v != null && v.get() instanceof Number n) return MathHelper.clamp(n.doubleValue() / 20.0, def, Tuning.d("tunnel.walk.max", 0.4));
        }
        catch (Throwable ignored) {
        }
        return def;
    }

    @SuppressWarnings("unchecked")
    private Setting<Boolean> antiHungerOnGround() {
        try {
            var ah = Modules.get().get(meteordevelopment.meteorclient.systems.modules.player.AntiHunger.class);
            Setting<?> on = ah == null ? null : ah.settings.get("on-ground");
            return on != null && on.get() instanceof Boolean ? (Setting<Boolean>) on : null;
        }
        catch (Throwable t) {
            return null;
        }
    }

    private void groundTruthForDigging() {
        lastGroundDigAge = mc.player.age;
        if (!groundSpoofed()) return;
        Setting<Boolean> on = antiHungerOnGround();
        if (on == null) return;
        on.set(false);
        ahSuspended = true;
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true, mc.player.horizontalCollision));
        LOG.info("[VG] digging on foot: Anti-Hunger's on-ground trick paused");
    }

    private void restoreAntiHunger() {
        if (!ahSuspended) return;
        ahSuspended = false;
        Setting<Boolean> on = antiHungerOnGround();
        if (on != null) on.set(true);
        LOG.info("[VG] Anti-Hunger's on-ground trick back on");
    }
    private int instantFails, instantBreaks, instantOffAge = -100000;

    private boolean groundSpoofed() {
        try {
            var ah = Modules.get().get(meteordevelopment.meteorclient.systems.modules.player.AntiHunger.class);
            if (ah == null || !ah.isActive()) return false;
            Setting<?> on = ah.settings.get("on-ground");
            return on == null || Boolean.TRUE.equals(on.get());
        }
        catch (Throwable t) {
            return true;
        }
    }
    private float travelYaw = Float.NaN;
    private int mineModeTicks, stallTicks, creepTicks, mineTicks, searchTicks;
    private int carrotIdx = -1;
    private boolean carrotTight;
    private boolean mending;
    private final java.util.ArrayDeque<String> history = new java.util.ArrayDeque<>();
    private int snapAge = -10000;
    private int lastMoveEventAge = -1;
    private double lastCmdSpeed;
    private Vec3d lastCmd = Vec3d.ZERO;
    private int slowAfterSetback, dvLimited, lavaTicks, takeoffFails, hardBrakes, creepEscapes, wallClips;
    private boolean lavaOkNow;
    private Object hpWorld;
    private static final Vec3d[] AXES6 = {new Vec3d(1, 0, 0), new Vec3d(-1, 0, 0), new Vec3d(0, 1, 0), new Vec3d(0, -1, 0), new Vec3d(0, 0, 1), new Vec3d(0, 0, -1)};

    private int fireResTicks() {
        var e = mc.player.getStatusEffect(net.minecraft.entity.effect.StatusEffects.FIRE_RESISTANCE);
        if (e == null) return 0;
        return e.isInfinite() ? Integer.MAX_VALUE : e.getDuration();
    }
    private float lastHp = -1;
    private volatile String lastDamageType = "?";
    private volatile long lastDamageAt;
    private long damageTotalMilli;
    private int lastHurtAge = -10000;
    private BlockPos fixedGoal, portalFound;
    private boolean divertedToPortal;
    private Vec3d flightStart;
    private final LongOpenHashSet scannedChunks = new LongOpenHashSet();
    private final List<BlockPos> ignoredPortals = new ArrayList<>();
    private int mendTicks, mendCooldown, throwCooldown, mendBelow = -1;
    private BlockPos digOutTarget, digging;
    private Vec3d stillAnchor;
    private int stillTicks, digTicks;
    private final java.util.ArrayDeque<double[]> progress = new java.util.ArrayDeque<>();
    private double curSpeed, dbgTargetSpeed, dbgClearAhead;
    private Vec3d lastMovePos, lastWant = Vec3d.ZERO;
    private BlockPos mineTarget;
    private Vec3d mineFrom;
    private final LongOpenHashSet noMine = new LongOpenHashSet();

    public BrainImpl(EflyGoto m) {
        this.m = m;
        this.targetX = m.targetX;
        this.targetZ = m.targetZ;
        this.arrive = m.arrive;
        this.targetY = m.targetY;
        this.cruiseHeight = m.cruiseHeight;
        this.maxSpeed = m.maxSpeed;
        this.waitForChunks = m.waitForChunks;
        this.rotate = m.rotate;
        this.cruiseDistance = m.cruiseDistance;
        this.hitboxPadding = m.hitboxPadding;
        this.setbackPause = m.setbackPause;
        this.slowOnSetback = m.slowOnSetback;
        this.enableEfly = m.enableEfly;
        this.logoutOnArrival = m.logoutOnArrival;
        this.portalStop = m.portalStop;
        this.portalIgnoreRadius = m.portalIgnoreRadius;
        this.minDurability = m.minDurability;
        this.swapElytra = m.swapElytra;
        this.mendWithXp = m.mendWithXp;
        this.mendAt = m.mendAt;
        this.emergencyLand = m.emergencyLand;
        this.searchRadius = m.searchRadius;
        this.maxNodes = m.maxNodes;
        this.computeMs = m.computeMs;
        this.allowLowGaps = m.allowLowGaps;
        this.wallAvoidance = m.wallAvoidance;
        this.mineThrough = m.mineThrough;
        this.mineCost = m.mineCost;
        this.braking = m.braking;
        this.keepVfSink = m.keepVfSink;
        this.wallDistance = m.wallDistance;
        this.followVfRamp = m.followVfRamp;
        this.acceleration = m.acceleration;
        this.renderPath = m.renderPath;
        this.pathColor = m.pathColor;
        this.goalColor = m.goalColor;
    }

    @Override
    public void go(int x, Integer y, int z) {
        if (mc.world != null && !mc.world.getWorldBorder().contains(x + 0.5, z + 0.5)) {
            m.error("%d %d is outside the world border.", x, z);
            return;
        }
        if (y != null && mc.world != null) {
            int clamped = MathHelper.clamp(y, mc.world.getBottomY() + 1, mc.world.getTopYInclusive() - 2);
            if (clamped != y) m.warning("Y %d is outside the world, using %d.", y, clamped);
            y = clamped;
        }
        fixedGoal = null;
        portalFound = null;
        divertedToPortal = false;
        targetX.set(x);
        targetZ.set(z);
        if (y != null) {
            arrive.set(Arrive.Hover);
            targetY.set(y);
        }
        else arrive.set(Arrive.Land);

        if (m.isActive()) {
            resetRoute();
            scannedChunks.clear();
            ignoredPortals.clear();
            if (mc.player != null) flightStart = mc.player.getEntityPos();
            m.info("Now flying to %s.", targetString());
        }
        else m.toggle();
    }

    private String targetString() {
        return arrive.get() == Arrive.Hover ? targetX.get() + " " + targetY.get() + " " + targetZ.get() : targetX.get() + " " + targetZ.get();
    }

    @Override
    public void activate() {
        if (mc.player == null || mc.world == null) {
            m.toggle();
            return;
        }
        vf = Modules.get().get(Efly.class);
        if (vf == null) {
            m.error("Efly isn't loaded.");
            m.toggle();
            return;
        }
        resetAgeStamps();
        if (!hasElytra()) {
            FindItemResult packed = InvUtils.find(st -> LivingEntity.canGlideWith(st, EquipmentSlot.CHEST), 0, 35);
            if (packed.found()) {
                InvUtils.move().from(packed.slot()).toArmor(2);
                m.info("Put the elytra from your inventory on.");
            }
        }
        if (!hasElytra() && !InvUtils.find(st -> LivingEntity.canGlideWith(st, EquipmentSlot.CHEST), 0, 35).found()) {
            m.error("Put an elytra on first.");
            m.toggle();
            return;
        }
        m.weEnabledVf = false;
        if (!vf.isActive()) {
            if (!enableEfly.get()) {
                m.error("Efly is off (turn it on, or turn on enable-efly).");
                m.toggle();
                return;
            }
            vf.toggle();
            m.weEnabledVf = true;
        }
        enableHelpers();
        LOG.info("[VG] flight settings: portal-stop={} (tuning override {}), portal-ignore-radius={}, logout-on-arrival={}, mine-through-walls={}",
            portalStop.get(), Tuning.b("portal.stop", false), portalIgnoreRadius.get(), logoutOnArrival.get(), mineThrough.get());

        dimension = mc.world.getRegistryKey();
        phase = mc.player.isGliding() ? Phase.Flying : Phase.TakeOff;
        takeoffWait = 0;
        groundTicks = 0;
        jumps = 0;
        lowCeilingWarned = 0;
        setbackTicks = 0;
        setbackCap = Double.POSITIVE_INFINITY;
        setbackCapV = Double.POSITIVE_INFINITY;
        ticksSinceSetback = 0;
        swapCooldown = 0;
        emergencyLanding = false;
        fixedGoal = null;
        portalFound = null;
        divertedToPortal = false;
        flightStart = mc.player.getEntityPos();
        scannedChunks.clear();
        ignoredPortals.clear();
        travelYaw = Float.NaN;
        resetRoute();

        Vec3d p = mc.player.getEntityPos();
        double dist = Math.hypot(targetX.get() + 0.5 - p.x, targetZ.get() + 0.5 - p.z);
        m.info("Flying to %s (%.0f blocks).", targetString(), dist);
        if (mc.world.getRegistryKey() == World.NETHER && dist > 20000) {
            m.warning("You're in the Nether. If those are Overworld coords, use .vgoto ow %d %d instead (flies to %d %d here).",
                targetX.get(), targetZ.get(), Math.floorDiv(targetX.get(), 8), Math.floorDiv(targetZ.get(), 8));
        }
        progress.clear();
    }

    private void resetRoute() {
        search = null;
        path = new ArrayList<>();
        pathReachesGoal = pathEndsAtFrontier = false;
        pathIdx = 0;
        goalNode = null;
        goalCheckAge = 0;
        ticksSinceSearch = 0;
        failures = 0;
        retryIn = 0;
        noCarrotTicks = 0;
        replanNow = true;
        landingWarned = false;
        hoverOnly = false;
        bestProgress = Double.MAX_VALUE;
        noProgressTicks = 0;
        stuckStrikes = 0;
        mineModeTicks = 0;
        tunneling = false;
        stallTicks = 0;
        creepTicks = 0;
        mineTicks = 0;
        mineTarget = null;
        lastMovePos = null;
        digOutTarget = null;
        digging = null;
        mending = false;
        stillAnchor = null;
        stillTicks = 0;
        noMine.clear();
        curSpeed = 0;
        curV = 0;
        if (phase == Phase.Landing) phase = Phase.Flying;
    }

    @Override
    public void deactivate() {
        restoreFrameRate();
        restoreAntiHunger();
        disableHelpers();
        releaseSwimKeys();
        if (stowedElytraSlot >= 0 && mc.player != null && !mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)
            && mc.player.getInventory().getStack(stowedElytraSlot).isOf(Items.ELYTRA)) InvUtils.move().from(stowedElytraSlot).toArmor(2);
        stowedElytraSlot = -1;
        releaseForward();
        search = null;
        path = new ArrayList<>();
        if (m.weEnabledVf && vf != null && vf.isActive() && mc.player != null && mc.player.isOnGround()) vf.toggle();
        m.weEnabledVf = false;
    }

    private void releaseForward() {
        if (!holdingForward) return;
        holdingForward = false;
        KeyBinding fwd = mc.options.forwardKey;
        fwd.setPressed(Input.isPressed(fwd));
    }

    @Override
    public void packet(PacketEvent.Receive event) {
        if (event.packet instanceof EntityDamageS2CPacket dp && mc.player != null && dp.entityId() == mc.player.getId()) {
            lastDamageType = dp.sourceType().getIdAsString() + (dp.sourcePosition().isPresent() ? "@" + v(dp.sourcePosition().get()) : "")
                + (dp.sourceCauseId() > 0 ? " cause#" + dp.sourceCauseId() : "");
            lastDamageAt = System.currentTimeMillis();
        }
        if (event.packet instanceof net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket tp && mc.player != null && tp.id() == mc.player.getId()) {
            for (var e : tp.trackedValues()) {
                if (e.id() == 0 && e.value() instanceof Byte fb) {
                    int g = (fb & 0x80) != 0 ? 1 : 0;
                    if (g != srvGlide) LOG.info("[VG] server says gliding={} (flags {}, client gliding={}, {} ticks after asking)", g == 1, Integer.toHexString(fb & 0xff), mc.player.isGliding(), mc.player.age - startSentAge);
                    srvGlide = g;
                    if (g == 1) srvSeenTrue = true;
                }
            }
        }
        if (!(event.packet instanceof PlayerPositionLookS2CPacket pk) || mc.player == null || vf == null) return;
        Vec3d toPos = pk.change().position();
        String rel = String.valueOf(pk.relatives());
        mc.execute(() -> {
            try {
                if (m.isActive() && mc.player != null && vf != null) onSetback(toPos, rel);
            }
            catch (Throwable t) {
                LOG.error("[VG] setback handling failed", t);
            }
        });
    }

    private void onSetback(Vec3d to, String relatives) {
        Vec3d at = mc.player.getEntityPos();
        dbgSetbacks++;
        snapshot("server setback #" + dbgSetbacks + " -> server pos " + v(to));
        {
            StringBuilder sb = new StringBuilder();
            int fast = 0, slow = 0;
            for (int i = 0; i < dtRing.length; i++) {
                float d = dtRing[(dtIdx + i) % dtRing.length];
                if (d < 30) fast++;
                if (d > 80) slow++;
                sb.append(Math.round(d)).append(' ');
            }
            LOG.info("[VG] tick spacing before setback #{} (ms, oldest first; {} short, {} long, paired={}, fps {}, server tps {}, {} s since its last tick): {}", dbgSetbacks, fast, slow, pairedTicks, mc.getCurrentFps(),
                String.format("%.1f", meteordevelopment.meteorclient.utils.world.TickRate.INSTANCE.getTickRate()),
                String.format("%.1f", meteordevelopment.meteorclient.utils.world.TickRate.INSTANCE.getTimeSinceLastTick()), sb.toString().trim());
        }
        LOG.info("[VG] SETBACK #{} phase={} gliding={} ticksSinceLast={} at=({}) server=({}) dist={} relatives={} vfMove=({}) ourMove=({}) sH={} cap={} vfStart={} vfH={} vfV={} yaw={} pitch={} travelYaw={} carrot=({}) setbackTicksLeft={} pathSize={} idx={} srvGlide={}",
            dbgSetbacks, phase, mc.player.isGliding(), ticksSinceSetback, v(at), v(to), String.format("%.2f", at.distanceTo(to)), relatives,
            v(dbgVfMove), v(dbgOurMove), String.format("%.2f", dbgSH), String.format("%.2f", setbackCap),
            vf.startSpeed.get(), vf.horizontalSpeed.get(), vf.verticalSpeed.get(),
            String.format("%.1f", mc.player.getYaw()), String.format("%.1f", mc.player.getPitch()), String.format("%.1f", travelYaw),
            dbgCarrot == null ? "none" : v(dbgCarrot), setbackTicks, path.size(), pathIdx, srvGlide);
        setbackTicks = setbackPause.get();
        ticksSinceSetback = 0;
        mc.player.setVelocity(0, 0, 0);
        if (!mc.player.isGliding()) {
            if (mc.player.age - footSetbackAge > 100) footSetbacks = 0;
            footSetbackAge = mc.player.age;
            if (++footSetbacks == Tuning.i("foot.setbacks.max", 5)) {
                LOG.info("[VG] {} pull-backs on foot at ({}) - not walking on, taking off from here", footSetbacks, v(mc.player.getEntityPos()));
                m.warning("The server won't let the bot walk on here - taking off instead.");
                takeoffForce = Tuning.i("takeoff.force.ticks", 100) * 2;
                tunneling = false;
                noRoomTicks = 0;
                groundNudge = null;
                replanNow = true;
                footSetbacks = 0;
            }
        }
        if (!Float.isNaN(camYaw) && Tuning.b("setback.keep.camera", true)) {
            mc.player.setYaw(camYaw);
            mc.player.setPitch(camPitch);
        }
        lastMovePos = null;
        curSpeed = 0;
        curV = 0;
        lastCmdSpeed = 0;
        lastCmd = Vec3d.ZERO;
        slowAfterSetback = Tuning.i("setback.slow.ticks", 0);
        if (slowOnSetback.get() && mc.player.isGliding()) {
            double floor = Math.max(1.0, vf.startSpeed.get());
            double now = Math.min(setbackCap, Math.max(vf.startSpeed.get(), vf.horizontalSpeed.get()));
            setbackCap = Math.max(floor, now * Tuning.d("setback.cut", 0.8));
            double nowV = Math.min(setbackCapV, Math.max(MIN_V, vf.verticalSpeed.get()));
            setbackCapV = Math.max(MIN_V, nowV * Tuning.d("setback.cut", 0.8));
        }
        if (mc.player.age - setbackMsgAge > 200) {
            setbackMsgAge = mc.player.age;
            if (setbackCap < 100) m.warning("Server pulled you back - slowing to %.1f across, %.1f up/down (blocks/tick).", setbackCap, setbackCapV);
            else m.warning("Server pulled you back.");
        }
    }

    @Override
    public String statusLine() {
        if (mc.player == null) return "";
        Vec3d p = mc.player.getEntityPos();
        double left = Math.hypot(targetX.get() + 0.5 - p.x, targetZ.get() + 0.5 - p.z);
        String s = String.format("%.0f blocks left", left);
        if (phase == Phase.Landing) return s + ", landing";
        double[] first = progress.peekFirst();
        if (first != null && mc.player.age - first[0] >= 200) {
            double perSec = (first[1] - left) / ((mc.player.age - first[0]) / 20.0);
            if (perSec > 0.1) {
                long secs = (long) (left / perSec);
                s += String.format(", %.1f blocks/s, ~%s", perSec, secs >= 3600 ? (secs / 3600) + "h " + (secs % 3600 / 60) + "m" : (secs / 60) + "m " + (secs % 60) + "s");
            }
            else s += ", not getting closer right now";
        }
        if (mineTarget != null) s += ", digging";
        return s;
    }

    @Override
    public String infoString() {
        if (mc.player == null) return null;
        if (phase == Phase.Landing) return "landing";
        Vec3d p = mc.player.getEntityPos();
        return String.format("%.0fm", Math.hypot(targetX.get() + 0.5 - p.x, targetZ.get() + 0.5 - p.z));
    }

    @Override
    public void tick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        camYaw = mc.player.getYaw();
        camPitch = mc.player.getPitch();
        if (Tuning.b("fps.keep.minimized", true)) keepFrameRate();
        long nowNano = System.nanoTime();
        tickDtMs = lastTickNano == 0 ? 50 : (nowNano - lastTickNano) / 1e6;
        lastTickNano = nowNano;
        dtRing[dtIdx++ % dtRing.length] = (float) tickDtMs;
        posRing[posIdx++ % posRing.length] = mc.player.getEntityPos();
        int shortTicks = 0;
        for (int i = 1; i <= 20; i++) if (dtRing[((dtIdx - i) % dtRing.length + dtRing.length) % dtRing.length] < 30) shortTicks++;
        boolean nowPaired = shortTicks >= (pairedTicks ? 3 : 7) && Tuning.b("paired.adapt", true);
        if (nowPaired != pairedTicks) {
            pairedTicks = nowPaired;
            boolean minimised = false;
            try {
                minimised = mc.getWindow().isMinimized();
            }
            catch (Throwable ignored) {
            }
            LOG.info("[VG] ticks {} (fps {}, window minimised={})", nowPaired ? "are coming in pairs - flying at half pace" : "are even again - normal pace", mc.getCurrentFps(), minimised);
            if (nowPaired && mc.player.age - pairedWarnAge > 6000) {
                pairedWarnAge = mc.player.age;
                m.warning("The game is running at ~%d FPS%s: packets go out in bunches and the server pulls back more. Flying at half pace until the frame rate is back - keep the Minecraft window open (it can be behind other windows) for full speed.", mc.getCurrentFps(), minimised ? " (window minimised)" : "");
            }
        }
        if (ahSuspended && mc.player.age - lastGroundDigAge > Tuning.i("dig.truth.hold", 60)) restoreAntiHunger();

        if (mc.world.getRegistryKey() != dimension) {
            m.info("Changed dimension - stopping.");
            m.toggle();
            return;
        }
        if (!vf.isActive()) {
            m.info("Efly was turned off - stopping.");
            m.weEnabledVf = false;
            m.toggle();
            return;
        }
        int fr = fireResTicks();
        boolean ok = Tuning.b("fireres.lava.paths", false) && fr > Tuning.i("fireres.path.ticks", 1500);
        if (ok != lavaOkNow) {
            lavaOkNow = ok;
            replanNow = true;
            LOG.info("[VG] fire resistance {} ({} ticks left) - lava {}", ok ? "on" : "off/ending", fr == Integer.MAX_VALUE ? "inf" : fr, ok ? "allowed" : "avoided");
        }

        float hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        if (hpWorld != mc.world) {
            hpWorld = mc.world;
            lastHp = -1;
            resetAgeStamps();
        }
        if (lastHp >= 0 && hp < lastHp - 0.01f && System.currentTimeMillis() - lastDamageAt > 1500) {
            LOG.info("[VG] hp {} -> {} without a damage packet (absorption ended / resync) - not counted", String.format("%.1f", lastHp), String.format("%.1f", hp));
            lastHp = hp;
        }
        if (lastHp >= 0 && hp < lastHp - 0.01f) {
            float lost = lastHp - hp;
            damageTotalMilli += Math.round(lost * 1000);
            boolean fresh = System.currentTimeMillis() - lastDamageAt < 1500;
            LOG.info("[VG] DAMAGE -{} hp (now {}) type={} fire={} lava={} fall={} gliding={} hCollide={} vCollide={} speed={} total={}",
                String.format("%.1f", lost), String.format("%.1f", hp), fresh ? lastDamageType : "unknown(" + lastDamageType + ")",
                mc.player.isOnFire(), mc.player.isInLava(), String.format("%.1f", mc.player.fallDistance), mc.player.isGliding(),
                mc.player.horizontalCollision, mc.player.verticalCollision, String.format("%.2f", lastCmdSpeed),
                String.format("%.1f", damageTotalMilli / 1000.0));
            snapAge = -10000;
            snapshot("damage -" + String.format("%.1f", lost) + " " + (fresh ? lastDamageType : "?"));
            lastHurtAge = mc.player.age;
            if (fresh && (lastDamageType.contains("lava") || lastDamageType.contains("fire"))) logHeatSource();
        }
        lastHp = hp;
        if (hp <= Tuning.d("logout.hp", 8) && mc.player.age - lastHurtAge < 40 && !mc.player.isCreative()) {
            logout(String.format("Health down to %.1f and still taking damage (%s) - logged out to stay alive.", hp, lastDamageType));
            m.toggle();
            return;
        }

        if (lavaSwim()) {
            if (lavaTicks == 0 && mc.player.isInLava()) {
                m.warning("In lava - swimming out.");
                snapAge = -10000;
                snapshot("in lava");
            }
            if (mc.player.isInLava()) lavaTicks++;
            if (phase != Phase.TakeOff) phase = Phase.TakeOff;
            search = null;
            lastHpKeep();
            return;
        }
        if (lavaTicks > 0 && !mc.player.isInLava()) {
            LOG.info("[VG] out of lava after {} ticks", lavaTicks);
            lavaTicks = 0;
            resetRoute();
        }
        if (!hasElytra()) {
            FindItemResult packed = InvUtils.find(st -> LivingEntity.canGlideWith(st, EquipmentSlot.CHEST), 0, 35);
            if (packed.found()) {
                InvUtils.move().from(packed.slot()).toArmor(2);
                stowedElytraSlot = -1;
                LOG.info("[VG] elytra wasn't on - put the one from slot {} back on", packed.slot());
                lastHpKeep();
                return;
            }
            if (!mc.player.isOnGround()) {
                logout("Elytra gone mid-air - logged out before the fall.");
                m.toggle();
                return;
            }
            m.error("No elytra on - stopping.");
            m.toggle();
            return;
        }
        if (mc.player.isInLava()) {
            if (lavaTicks == 0) {
                m.warning("In lava - climbing out.");
                snapAge = -10000;
                snapshot("in lava");
            }
            lavaTicks++;
            if (lavaTicks % 20 == 0) replanNow = true;
        }
        else if (lavaTicks > 0) {
            LOG.info("[VG] out of lava after {} ticks", lavaTicks);
            lavaTicks = 0;
            replanNow = true;
        }
        if (mc.player.isTouchingWater()) {
            m.warning("In water - stopping (the elytra can't fly in water).");
            m.toggle();
            return;
        }

        if (mc.player.age % 20 == 0) {
            if (mc.player.age % 20 == 0) LOG.info("[VG] dig mode={} creep={} stall={} mineTarget={} mineTicks={} speed={} target={} clearAhead={} vSpeed={} capV={} dvLimited={} hardBrakes={} wallClips={}",
            mineModeTicks, creepTicks, stallTicks, mineTarget, mineTicks, String.format("%.2f", curSpeed),
            String.format("%.2f", dbgTargetSpeed), String.format("%.1f", dbgClearAhead), String.format("%.2f", curV), String.format("%.2f", setbackCapV), dvLimited, hardBrakes, wallClips);
        if (mc.player.age % 20 == 0) LOG.info("[VG] tick phase={} gliding={} onGround={} pos=({}) vel=({}) vfMove=({}) ourMove=({}) sH={} cap={} setbackTicks={} path={}/{} reachesGoal={} frontier={} search={} noProgress={} strikes={} fwdHeld={} vfOn={}",
                phase, mc.player.isGliding(), mc.player.isOnGround(), v(mc.player.getEntityPos()), v(mc.player.getVelocity()),
                v(dbgVfMove), v(dbgOurMove), String.format("%.2f", dbgSH), String.format("%.2f", setbackCap), setbackTicks,
                pathIdx, path.size(), pathReachesGoal, pathEndsAtFrontier, search != null, noProgressTicks, stuckStrikes,
                mc.options.forwardKey.isPressed(), vf.isActive());
        }
        if (mc.player.age % 20 == 0) {
            Vec3d pp = mc.player.getEntityPos();
            progress.addLast(new double[] {mc.player.age, Math.hypot(targetX.get() + 0.5 - pp.x, targetZ.get() + 0.5 - pp.z)});
            while (progress.size() > 61) progress.pollFirst();
        }
        if (setbackTicks > 0) setbackTicks--;
        if (mineModeTicks > 0) mineModeTicks--;
        if (creepTicks > 0) creepTicks--;
        int recoverEvery = Math.max(1, Tuning.i("setback.recover.ticks", 100));
        if (++ticksSinceSetback % recoverEvery == 0 && setbackCapV != Double.POSITIVE_INFINITY) {
            setbackCapV *= Tuning.d("setback.recover", 1.15);
            if (setbackCapV >= Math.max(vf.verticalSpeed.get(), MIN_V)) setbackCapV = Double.POSITIVE_INFINITY;
        }
        if (ticksSinceSetback % recoverEvery == 0 && setbackCap != Double.POSITIVE_INFINITY) {
            setbackCap *= Tuning.d("setback.recover", 1.15);
            if (setbackCap >= Math.max(vf.horizontalSpeed.get(), 1)) setbackCap = Double.POSITIVE_INFINITY;
        }
        if (swapCooldown > 0) swapCooldown--;
        if (!checkElytra()) return;

        updateGoal();

        boolean glNow = mc.player.isGliding();
        if (!glNow && !mc.player.isOnGround() && !mc.player.isInLava() && !mc.player.isTouchingWater() && stowedElytraSlot < 0 && Tuning.b("air.drop.velocity", true)) {
            Vec3d vel = mc.player.getVelocity();
            double hv = Math.hypot(vel.x, vel.z);
            if (glidingLastTick || hv > Tuning.d("air.drift.max", 0.05)) {
                if (glidingLastTick && (hv > 0.3 || vel.y < -0.5)) LOG.info("[VG] elytra closed at ({}) - dropped the leftover velocity ({})", v(mc.player.getEntityPos()), v(vel));
                mc.player.setVelocity(0, glidingLastTick ? MathHelper.clamp(vel.y, -0.1, 0) : vel.y, 0);
            }
        }
        glidingLastTick = glNow;
        if (pendingStartAge >= 0 && mc.player.age > pendingStartAge) {
            if (mc.player.isGliding() && mc.player.isInLava()) {
                mc.player.stopGliding();
                LOG.info("[VG] elytra: not opening - in lava");
            }
            else if (mc.player.isGliding()) {
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                startSentAge = mc.player.age;
                if (srvGlide != 1) srvGlide = 0;
                LOG.info("[VG] elytra: open sent at ({}) vy={} fall={}", v(mc.player.getEntityPos()), String.format("%.2f", mc.player.getVelocity().y), String.format("%.1f", mc.player.fallDistance));
            }
            pendingStartAge = -1;
        }
        if (mc.player.isGliding() && srvGlide == 0 && srvSeenTrue && pendingStartAge < 0 && Tuning.b("glide.desync.fix", true)
            && mc.player.age - startSentAge > Tuning.i("glide.confirm.ticks", 20)) {
            desyncFixes++;
            LOG.info("[VG] glide desync #{} at ({}): the server doesn't have us gliding - closing the elytra here and opening it again", desyncFixes, v(mc.player.getEntityPos()));
            mc.player.stopGliding();
            startSentAge = mc.player.age;
        }

        boolean gliding = mc.player.isGliding();
        Vec3d pos = mc.player.getEntityPos();

        boolean portalStopOn = portalStop.get() || Tuning.b("portal.stop", false);
        if (!divertedToPortal && !emergencyLanding && (phase == Phase.Flying || phase == Phase.TakeOff)) scanForPortals(pos, portalStopOn);
        if (gliding && phase == Phase.Flying && lastMoveEventAge >= 0 && mc.player.age - lastMoveEventAge > 1) {
            history.addLast("t" + mc.player.age + " NO MOVE EVENT since t" + lastMoveEventAge + " pos(" + v(pos) + ")");
            while (history.size() > 40) history.pollFirst();
        }
        if (mending) updateMend(pos, gliding);
        if (digging != null && !FlightPathfinder.blocks(mc.world, digging, mc.world.getBlockState(digging))) {
            boolean byServer = pendingBreak.containsKey(digging.asLong());
            brokeAge.put(digging.asLong(), byServer ? mc.player.age - 1000 : mc.player.age);
            if (!byServer) lastBrokeAge = mc.player.age;
            digging = null;
            if (pendingBreak.size() > 200) pendingBreak.clear();
        }
        if (brokeAge.size() > 400) brokeAge.clear();
        updateMining(pos, gliding);
        updateDigOut(pos, gliding);
        if (rotate.get() == Rotate.ServerOnly && gliding) {
            double simPitch = Tuning.d("sim.pitch", 90);
            Rotations.rotate(Float.isNaN(travelYaw) ? mc.player.getYaw() : travelYaw, simPitch < -90.5 ? mc.player.getPitch() : simPitch);
        }
        if (gliding && phase == Phase.Flying && !watchdog(pos)) return;

        if (phase == Phase.Flying && pathReachesGoal && !path.isEmpty() && pos.distanceTo(path.getLast()) < 0.9) {
            if (arrive.get() == Arrive.Hover || hoverOnly) {
                if (hoverOnly) m.warning("Hovering above %s - nowhere safe to land there.", targetString());
                m.info("Arrived at %s.", targetString());
                m.toggle();
                if (logoutOnArrival.get() && !emergencyLanding) logout("Arrived at " + targetString() + ".");
                return;
            }
            phase = Phase.Landing;
        }
        if (phase == Phase.Landing) {
            groundTicks = mc.player.isOnGround() ? groundTicks + 1 : 0;
            if (groundTicks >= 2) {
                BlockPos b = mc.player.getBlockPos();
                m.info("Landed at %d %d %d.", b.getX(), b.getY(), b.getZ());
                m.toggle();
                if (emergencyLanding) logout(String.format("Elytra worn out, no spare - landed at %d %d %d and logged out.", b.getX(), b.getY(), b.getZ()));
                else if (divertedToPortal) logout(String.format("Landed next to a Nether portal at %d %d %d.", portalFound.getX(), portalFound.getY(), portalFound.getZ()));
                else if (logoutOnArrival.get() && !emergencyLanding) logout(String.format("Arrived at %d %d %d.", b.getX(), b.getY(), b.getZ()));
                return;
            }
        }

        boolean wantForward = gliding && phase == Phase.Flying;
        if (wantForward) {
            holdingForward = true;
            mc.options.forwardKey.setPressed(true);
        }
        else releaseForward();

        if (!gliding && phase != Phase.Landing) takeOff();
        else if (gliding && phase == Phase.TakeOff) {
            phase = Phase.Flying;
            jumps = 0;
            takeoffFails = 0;
        }

        ticksSinceSearch++;
        if (retryIn > 0) retryIn--;
        if (search != null) {
            boolean idle = path.isEmpty() || pathIdx >= path.size() - 3 || dbgOurMove.lengthSquared() < 1e-4;
            int ms = idle ? Math.max(computeMs.get(), Tuning.i("search.ms.idle", 20)) : computeMs.get();
            FlightPathfinder.Status s = search.step(ms * 1_000_000L);
            if (s == FlightPathfinder.Status.RUNNING && ++searchTicks > (goalNode == null ? Tuning.i("search.ticks.partial", 30) : Tuning.i("search.ticks.goal", 100))
                && (search.bestGain() >= Tuning.d("search.min.gain", 16) || searchTicks > Tuning.i("search.ticks.max", 160))) {
                s = search.finishNow();
                LOG.info("[VG] search out of time after {} ticks ({} nodes) - using the closest so far", searchTicks, search.expanded());
            }
            if (s != FlightPathfinder.Status.RUNNING) searchDone(s);
        }
        else if (phase != Phase.Landing && retryIn == 0 && needReplan(pos)) startSearch();
    }

    private static int durability(ItemStack s) {
        return s.getMaxDamage() - s.getDamage();
    }

    private boolean checkElytra() {
        ItemStack chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (mendCooldown > 0) mendCooldown--;
        if (mending) return true;
        if (!chest.isOf(Items.ELYTRA) || !chest.isDamageable()) return true;
        int dur = durability(chest);
        if (mendWithXp.get() && dur <= mendAt.get() && mendCooldown == 0 && phase == Phase.Flying && mc.player.isGliding()
            && EnchantmentHelper.hasAnyEnchantmentsWith(chest, EnchantmentEffectComponentTypes.REPAIR_WITH_XP)
            && InvUtils.find(Items.EXPERIENCE_BOTTLE).found()) {
            mending = true;
            mendTicks = 0;
            throwCooldown = 0;
            m.info("Elytra at %d durability - mending it with XP bottles.", dur);
            return true;
        }
        if (dur > minDurability.get()) return true;
        if (swapCooldown > 0) return true;

        if (swapElytra.get()) {
            int min = minDurability.get(), best = -1, bestDur = min * 2;
            for (int i = 0; i < 36; i++) {
                ItemStack st = mc.player.getInventory().getStack(i);
                if (!st.isOf(Items.ELYTRA)) continue;
                int d = st.isDamageable() ? durability(st) : Integer.MAX_VALUE;
                if (d > bestDur) {
                    best = i;
                    bestDur = d;
                }
            }
            if (best >= 0) {
                InvUtils.move().from(best).toArmor(2);
                swapCooldown = 20;
                m.info("Elytra at %d durability - put on a fresh one.", dur);
                return true;
            }
        }

        if (!mc.player.isGliding() && phase == Phase.TakeOff) {
            m.error("Elytra is at %d durability and there's no spare - not taking off.", durability(chest));
            m.toggle();
            return false;
        }
        if (emergencyLand.get() && !emergencyLanding && phase != Phase.Landing) {
            emergencyLanding = true;
            String was = targetString();
            Vec3d p = mc.player.getEntityPos();
            m.warning("Elytra at %d durability and no spare - landing here. Use .vgoto %s to carry on afterwards.", durability(chest), was);
            go(MathHelper.floor(p.x), null, MathHelper.floor(p.z));
        }
        return true;
    }

    private boolean watchdog(Vec3d pos) {
        double d = goalNode != null ? pos.distanceTo(Vec3d.ofBottomCenter(goalNode)) : Math.hypot(targetX.get() + 0.5 - pos.x, targetZ.get() + 0.5 - pos.z);
        if (d < bestProgress - 2) {
            bestProgress = d;
            noProgressTicks = 0;
            stuckStrikes = 0;
            exemptTicks = 0;
            return true;
        }
        if (mending) return true;
        if (mineTarget != null || digOutTarget != null || tunneling) {
            if (++exemptTicks < Tuning.i("watchdog.exempt.ticks", 500)) return true;
            exemptTicks = 0;
            snapAge = -10000;
            snapshot("no progress for 25 s while digging - starting the route over");
            m.warning("No progress for 25 seconds while digging - starting the route over.");
            BlockPos bad = mineTarget != null ? mineTarget : digOutTarget;
            resetRoute();
            if (bad != null) noMine.add(bad.asLong());
            return true;
        }
        exemptTicks = 0;
        if (++noProgressTicks < Tuning.i("watchdog.ticks", 200)) return true;
        noProgressTicks = 0;
        replanNow = true;
        boolean waitingForChunks = pathEndsAtFrontier && !path.isEmpty() && pos.distanceTo(path.getLast()) < 4;
        if (waitingForChunks || setbackTicks > 0) return true;
        if (++stuckStrikes >= 4) {
            snapAge = -10000;
            snapshot("stuck 40 s - starting over");
            m.warning("Stuck for 40 seconds - starting the route over (digging allowed).");
            stuckStrikes = 0;
            resetRoute();
            if (mineThrough.get()) startDigging("Digging allowed while getting unstuck.");
            return true;
        }
        if (mineThrough.get() && mineModeTicks == 0) {
            startDigging("Not getting any closer - planning a new route (digging through if there's no way around).");
            return true;
        }
        snapshot("not getting any closer (strike " + stuckStrikes + ")");
        m.warning("Not getting any closer - planning a new route.");
        return true;
    }

    private boolean hasElytra() {
        return LivingEntity.canGlideWith(mc.player.getEquippedStack(EquipmentSlot.CHEST), EquipmentSlot.CHEST);
    }

    private boolean swimKeys;
    private boolean tunneling;
    private final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap brokeAge = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
    private int lastBrokeAge = -10000;
    private Vec3d digHold;
    private Vec3d groundNudge;
    private int centerTicks;
    private List<Vec3d> extendPath;
    private int extendIdx;
    private int trappedSearches;
    private double takeoffGroundY = Double.NaN, lastAirY = Double.NaN;
    private int airStuckTicks, noRoomTicks;
    private int stowedElytraSlot = -1, outOfLavaTicks;

    private void releaseSwimKeys() {
        if (!swimKeys) return;
        swimKeys = false;
        mc.options.jumpKey.setPressed(meteordevelopment.meteorclient.utils.misc.input.Input.isPressed(mc.options.jumpKey));
        mc.options.forwardKey.setPressed(meteordevelopment.meteorclient.utils.misc.input.Input.isPressed(mc.options.forwardKey));
    }

    private boolean glideBugged, wasGliding, exitStandable;
    private Vec3d bugAnchor;
    private int bugTicks, bugAnchorSetbacks, stowFailAge = -10000;

    private void resetAgeStamps() {
        lastHurtAge = lastBrokeAge = glideCloseAge = lavaLogAge = startSentAge = snapAge = setbackMsgAge = loopLogAge = tunnelEndAge = footSetbackAge
            = unplannedLogAge = pairedWarnAge = exitCacheAge = -100000;
        pendingStartAge = -1;
        srvGlide = -1;
        srvSeenTrue = false;
        brokeAge.clear();
        pendingBreak.clear();
        pmTarget = null;
        digging = null;
        takeoffAnchor = null;
        takeoffStill = takeoffForce = footSetbacks = 0;
        wasGliding = glidingLastTick = mc.player != null && mc.player.isGliding();
        if (mc.player != null && (mc.player.isOnFire() || mc.player.isInLava())
            && mc.player.getHealth() + mc.player.getAbsorptionAmount() <= Tuning.d("logout.hp", 8)) lastHurtAge = mc.player.age;
    }

    private Vec3d lavaAnchor;
    private int lavaStillTicks, glideCloseAge = -10000, lavaLogAge = -10000;

    private boolean lavaSwim() {
        boolean inLava = mc.player.isInLava(), gliding = mc.player.isGliding();
        Vec3d pos = mc.player.getEntityPos();
        stowedElytraSlot = -1;

        if (!inLava || lavaOkNow) {
            wasGliding = gliding;
            lavaAnchor = null;
            lavaStillTicks = 0;
            if (swimKeys) {
                mc.options.jumpKey.setPressed(false);
                if (mc.player.isOnGround() || gliding || lavaOkNow || ++outOfLavaTicks > Tuning.i("lava.hop.ticks", 8)) releaseSwimKeys();
            }
            return false;
        }
        outOfLavaTicks = 0;

        if (gliding) {
            boolean openedHere = !wasGliding;
            wasGliding = true;
            if (lavaAnchor == null || pos.distanceTo(lavaAnchor) > 1.0) {
                lavaAnchor = pos;
                lavaStillTicks = 0;
            }
            else lavaStillTicks++;
            if (!openedHere && lavaStillTicks <= Tuning.i("lava.still.ticks", 20)) {
                releaseSwimKeys();
                return false;
            }
            if (mc.player.age - lavaLogAge > 20) {
                lavaLogAge = mc.player.age;
                LOG.info("[VG] lava: glide {} at ({}) - closing it and swimming (server gliding={})", openedHere ? "was opened in the lava" : "stuck for " + lavaStillTicks + " ticks", v(pos), srvGlide);
            }
            mc.player.stopGliding();
            pendingStartAge = -1;
            lavaAnchor = null;
            lavaStillTicks = 0;
        }
        wasGliding = false;
        if (srvGlide == 1 && mc.player.age - glideCloseAge > 20) {
            glideCloseAge = mc.player.age;
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            LOG.info("[VG] lava: asked the server to close its glide");
        }

        Vec3d exit = lavaExit(pos);
        Vec3d aim = exit;
        if (exit == null || !exitStandable) {
            double dx = targetX.get() + 0.5 - pos.x, dz = targetZ.get() + 0.5 - pos.z, l = Math.hypot(dx, dz);
            if (exit != null && exit.y > pos.y + 1.2) aim = exit;
            else if (l > 1) aim = new Vec3d(pos.x + dx / l * 6, pos.y, pos.z + dz / l * 6);
        }
        BlockPos dig = null;
        if (aim != null) {
            Vec3d d = aim.subtract(pos);
            double l = Math.hypot(d.x, d.z);
            if (l > 0.05) mc.player.setYaw((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
            if (mc.player.horizontalCollision && l > 0.6) {
                for (double h : new double[] {1.0, 0.2}) {
                    BlockPos b = BlockPos.ofFloored(pos.x + d.x / l * 0.8, pos.y + h, pos.z + d.z / l * 0.8);
                    if (exitStandable && exit != null && b.getY() < MathHelper.floor(exit.y)) continue;
                    if (swimDiggable(b)) {
                        dig = b;
                        break;
                    }
                }
            }
        }
        if (dig == null && (aim == null || aim.y > pos.y + 1.5)) {
            BlockPos above = BlockPos.ofFloored(pos.x, pos.y + 1.9, pos.z);
            if (swimDiggable(above)) dig = above;
        }
        boolean eating = mc.player.isUsingItem() || mc.options.useKey.isPressed();
        if (dig != null && !eating) dig(dig);
        swimKeys = true;
        BlockPos feetCell = BlockPos.ofFloored(pos.x, pos.y + 0.1, pos.z);
        boolean submerged = !mc.world.getBlockState(feetCell).getFluidState().isEmpty();
        mc.options.jumpKey.setPressed((submerged || aim == null || aim.y > pos.y + 0.6) && mc.player.age % 6 != 0);
        mc.options.forwardKey.setPressed(aim != null && Math.hypot(aim.x - pos.x, aim.z - pos.z) > 0.06);
        if (mc.player.age % 20 == 0) LOG.info("[VG] lava swim: pos=({}) exit={} standable={} dig={} eating={} onGround={} hp={}", v(pos), exit == null ? "none" : v(exit),
            exitStandable, dig, eating, mc.player.isOnGround(), String.format("%.1f", mc.player.getHealth()));
        return true;
    }

    private boolean surfaceBelow(Vec3d pos, int n) {
        BlockPos.Mutable q = new BlockPos.Mutable();
        for (int i = 0; i <= n; i++) {
            q.set(MathHelper.floor(pos.x), MathHelper.floor(pos.y) - i, MathHelper.floor(pos.z));
            BlockState st = mc.world.getBlockState(q);
            if (!st.getFluidState().isEmpty() || !st.getCollisionShape(mc.world, q).isEmpty()) return true;
        }
        return false;
    }

    private void lastHpKeep() {
    }

    private boolean stuckDiggable(BlockPos b) {
        BlockState st = mc.world.getBlockState(b);
        if (st.getCollisionShape(mc.world, b).isEmpty() || st.hasBlockEntity() || noMine.contains(b.asLong())) return false;
        float h = st.getHardness(mc.world, b);
        if (h < 0 || h >= 20) return false;
        for (BlockPos n : new BlockPos[] {b.up(), b.north(), b.south(), b.east(), b.west()}) {
            if (!mc.world.getBlockState(n).getFluidState().isEmpty()) return false;
        }
        return !(mc.world.getBlockState(b.up()).getBlock() instanceof net.minecraft.block.FallingBlock);
    }

    private boolean roomToTakeOff(BlockPos feet) {
        for (int i = 0; i < 3; i++) {
            BlockPos c = feet.up(i);
            if (FlightPathfinder.blocks(mc.world, c, mc.world.getBlockState(c))) return false;
        }
        int open = 0;
        for (net.minecraft.util.math.Direction d : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
            BlockPos a = feet.offset(d).up();
            if (!FlightPathfinder.blocks(mc.world, a, mc.world.getBlockState(a)) && !FlightPathfinder.blocks(mc.world, a.up(), mc.world.getBlockState(a.up()))) open++;
        }
        return open >= 1;
    }

    private BlockPos roomDigTarget(BlockPos feet) {
        for (int i = 1; i <= 2; i++) if (stuckDiggable(feet.up(i))) return feet.up(i);
        double dx = targetX.get() + 0.5 - mc.player.getX(), dz = targetZ.get() + 0.5 - mc.player.getZ();
        net.minecraft.util.math.Direction first = Math.abs(dx) > Math.abs(dz)
            ? (dx > 0 ? net.minecraft.util.math.Direction.EAST : net.minecraft.util.math.Direction.WEST)
            : (dz > 0 ? net.minecraft.util.math.Direction.SOUTH : net.minecraft.util.math.Direction.NORTH);
        net.minecraft.util.math.Direction[] order = {first, first.rotateYClockwise(), first.rotateYCounterclockwise(), first.getOpposite()};
        for (net.minecraft.util.math.Direction d : order) {
            for (int i = 2; i >= 1; i--) {
                BlockPos c = feet.offset(d).up(i);
                if (stuckDiggable(c)) return c;
            }
        }
        return null;
    }

    private boolean swimDiggable(BlockPos b) {
        BlockState st = mc.world.getBlockState(b);
        if (st.getCollisionShape(mc.world, b).isEmpty() || st.hasBlockEntity()) return false;
        float h = st.getHardness(mc.world, b);
        return h >= 0 && h < 50 && !noMine.contains(b.asLong());
    }

    private Vec3d exitCache;
    private int exitCacheAge = -1000;

    private Vec3d lavaExit(Vec3d pos) {
        if (mc.player.age - exitCacheAge < 20 && mc.player.age >= exitCacheAge) return exitCache;
        exitCacheAge = mc.player.age;
        BlockPos f = BlockPos.ofFloored(pos);
        BlockPos.Mutable q = new BlockPos.Mutable();
        Vec3d best = null;
        boolean bestStand = false;
        double bestScore = Double.MAX_VALUE;
        int surfaceY = f.getY();
        for (int up = 0; up < 30; up++) {
            q.set(f.getX(), f.getY() + up, f.getZ());
            if (!mc.world.getBlockState(q).getFluidState().isIn(FluidTags.LAVA)) break;
            surfaceY = f.getY() + up + 1;
        }
        for (int dy = -2; dy <= 30; dy++) {
            int r = dy >= -1 && dy <= 4 ? 28 : 12;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    double dist = Math.sqrt(dx * dx + dz * dz + dy * dy);
                    if (dist + 0 >= bestScore) continue;
                    int x = f.getX() + dx, y = f.getY() + dy, z = f.getZ() + dz;
                    q.set(x, y, z);
                    BlockState a = mc.world.getBlockState(q);
                    if (!a.getFluidState().isEmpty() || !a.getCollisionShape(mc.world, q).isEmpty()) continue;
                    q.set(x, y + 1, z);
                    BlockState h = mc.world.getBlockState(q);
                    if (!h.getFluidState().isEmpty() || !h.getCollisionShape(mc.world, q).isEmpty()) continue;
                    q.set(x, y - 1, z);
                    BlockState below = mc.world.getBlockState(q);
                    boolean stand = below.getFluidState().isEmpty() && !below.getCollisionShape(mc.world, q).isEmpty() && y <= surfaceY + 1;
                    double score = dist + (stand ? 0 : Tuning.d("lava.exit.air.penalty", 40)) + (dy < 0 ? 4 : 0);
                    if (stand) {
                        for (net.minecraft.util.math.Direction dd : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
                            q.set(x + dd.getOffsetX(), y, z + dd.getOffsetZ());
                            if (!mc.world.getBlockState(q).getFluidState().isEmpty()) {
                                score += 3;
                                break;
                            }
                        }
                    }
                    if (score < bestScore) {
                        bestScore = score;
                        best = new Vec3d(x + 0.5, y + 0.1, z + 0.5);
                        bestStand = stand;
                    }
                }
            }
        }
        exitCache = best;
        exitStandable = bestStand;
        return best;
    }

    private boolean clearSolid(Vec3d a, Vec3d b) {
        Box box = new Box(a.x - 0.3, a.y, a.z - 0.3, a.x + 0.3, a.y + 0.6, a.z + 0.3);
        Vec3d d = b.subtract(a);
        int n = Math.max(1, (int) Math.ceil(d.length() / 0.3));
        BlockPos.Mutable q = new BlockPos.Mutable();
        for (int i = 1; i <= n; i++) {
            Box bb = box.offset(d.multiply(i / (double) n));
            for (int x = MathHelper.floor(bb.minX); x <= MathHelper.ceil(bb.maxX) - 1; x++)
                for (int y = MathHelper.floor(bb.minY); y <= MathHelper.ceil(bb.maxY) - 1; y++)
                    for (int z = MathHelper.floor(bb.minZ); z <= MathHelper.ceil(bb.maxZ) - 1; z++) {
                        q.set(x, y, z);
                        if (!mc.world.getBlockState(q).getCollisionShape(mc.world, q).isEmpty()) return false;
                    }
        }
        return true;
    }

    private void takeOff() {
        if (phase != Phase.TakeOff) phase = Phase.TakeOff;
        if (takeoffWait > 0) takeoffWait--;
        Vec3d tp = mc.player.getEntityPos();
        if (takeoffAnchor != null && tp.distanceTo(takeoffAnchor) < Tuning.d("takeoff.stuck.radius", 1.5)) {
            if (mc.player.isOnGround()) takeoffStill++;
        }
        else {
            takeoffAnchor = tp;
            takeoffStill = 0;
        }
        if (takeoffStill > 0 && takeoffStill % 40 == 0 && mc.player.isOnGround()) LOG.info("[VG] takeoff: on the ground at ({}) for {} ticks - {}", v(tp), takeoffStill, takeoffDoing);
        if (takeoffStill == Tuning.i("takeoff.stuck.ticks", 60) && mc.player.isOnGround()) {
            takeoffStill++;
            snapAge = -10000;
            snapshot("standing still in take-off: " + takeoffDoing);
            m.warning("Standing still (%s) - just taking off.", takeoffDoing);
            takeoffForce = Tuning.i("takeoff.force.ticks", 100);
            tunneling = false;
            noRoomTicks = 0;
            replanNow = true;
        }
        if (takeoffForce > 0) takeoffForce--;
        boolean force = takeoffForce > 0;
        takeoffDoing = "nothing";
        if (mc.player.isOnGround()) {
            BlockPos fc = BlockPos.ofFloored(mc.player.getX(), mc.player.getY() + 0.2, mc.player.getZ());
            boolean roomHere = mc.world.isSpaceEmpty(mc.player, mc.player.getBoundingBox().stretch(0, 1.3, 0)) && roomToTakeOff(fc);
            noRoomTicks = roomHere ? 0 : noRoomTicks + 1;
        }
        boolean crawlingNow = mc.player.getBoundingBox().getLengthY() < 1.0;
        boolean rockAhead = false;
        if (mc.player.isOnGround() && mineThrough.get() && !force && Tuning.b("walk.rock.ahead", false) && path.size() - pathIdx > 1) {
            int i0 = nearestIndex(tp, Math.max(0, pathIdx - 4), Math.min(path.size(), pathIdx + 8));
            for (int k = i0 + 1; k <= Math.min(path.size() - 1, i0 + Tuning.i("walk.rock.nodes", 4)); k++) {
                BlockPos c = BlockPos.ofFloored(path.get(k));
                if (plannedMine.contains(c.asLong()) && FlightPathfinder.blocks(mc.world, c, mc.world.getBlockState(c))) {
                    rockAhead = true;
                    break;
                }
            }
        }
        footTicks = mc.player.isOnGround() ? footTicks + 1 : 0;
        boolean settled = footTicks >= Tuning.i("walk.settle.ticks", 5);
        if (!settled && mc.player.isOnGround() && (tunneling || crawlingNow)) {
            takeoffDoing = "settling on the ground before walking";
            return;
        }
        if ((!force || crawlingNow) && (tunneling || crawlingNow || rockAhead || noRoomTicks > Tuning.i("walk.noroom.ticks", 20)) && mineThrough.get() && mc.player.isOnGround() && walkTunnel()) {
            takeoffDoing = "walking the tunnel (path " + pathIdx + "/" + path.size() + ", tunneling=" + tunneling + ", crawling=" + crawlingNow + ", noRoom=" + noRoomTicks + ")";
            return;
        }
        if (crawlingNow) {
            takeoffDoing = "crawling: opening the block above";
            if (mc.player.isOnGround()) {
                double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
                BlockPos feetC = BlockPos.ofFloored(px, py + 0.2, pz);
                BlockPos t = null;
                for (BlockPos b : BlockPos.iterate(MathHelper.floor(px - 0.3), MathHelper.floor(py + 0.61), MathHelper.floor(pz - 0.3),
                    MathHelper.floor(px + 0.3), MathHelper.floor(py + 1.79), MathHelper.floor(pz + 0.3))) {
                    if (FlightPathfinder.blocks(mc.world, b, mc.world.getBlockState(b)) && stuckDiggable(b)) {
                        t = b.toImmutable();
                        break;
                    }
                }
                double cx = feetC.getX() + 0.5, cz = feetC.getZ() + 0.5, off = Math.hypot(cx - px, cz - pz);
                BlockPos over = feetC.up();
                if (!FlightPathfinder.blocks(mc.world, over, mc.world.getBlockState(over)) && off > 0.05) {
                    double sp = Math.min(0.1, off);
                    groundNudge = new Vec3d((cx - px) / off * sp, 0, (cz - pz) / off * sp);
                    takeoffDoing = "crawling: moving under the open column";
                }
                else if (t != null && mineThrough.get()) {
                    dig(t);
                    takeoffDoing = "crawling: digging " + t.toShortString() + " to stand up";
                }
                else takeoffDoing = "crawling: nothing overhead is safe to dig";
            }
            return;
        }

        if (mc.player.isOnGround()) {
            BlockPos feet = BlockPos.ofFloored(mc.player.getX(), mc.player.getY() + 0.2, mc.player.getZ());
            takeoffGroundY = mc.player.getY();
            Box body = mc.player.getBoundingBox();
            boolean headroom = mc.world.isSpaceEmpty(mc.player, body.stretch(0, 1.3, 0));
            boolean lowJump = (!headroom || !roomToTakeOff(feet)) && takeoffFails < 1 && Tuning.b("takeoff.low", false)
                && mc.world.isSpaceEmpty(mc.player, body.stretch(0, 0.15, 0));
            if ((!headroom || !roomToTakeOff(feet)) && !lowJump) {
                double cx = feet.getX() + 0.5, cz = feet.getZ() + 0.5, off = Math.hypot(cx - mc.player.getX(), cz - mc.player.getZ());
                Box centred = body.offset(cx - mc.player.getX(), 0, cz - mc.player.getZ());
                if (off > 0.05 && centerTicks < 60 && roomToTakeOff(feet) && mc.world.isSpaceEmpty(mc.player, centred.stretch(0, 1.3, 0))) {
                    double sp = Math.min(0.12, off);
                    groundNudge = new Vec3d((cx - mc.player.getX()) / off * sp, 0, (cz - mc.player.getZ()) / off * sp);
                    centerTicks++;
                    takeoffDoing = "stepping to the middle of the block";
                    return;
                }
                if (mineThrough.get()) {
                    BlockPos t = null;
                    Box up = body.stretch(0, 1.3, 0);
                    for (BlockPos b : BlockPos.iterate(MathHelper.floor(up.minX), MathHelper.floor(body.maxY), MathHelper.floor(up.minZ),
                        MathHelper.floor(up.maxX), MathHelper.floor(up.maxY), MathHelper.floor(up.maxZ))) {
                        if (stuckDiggable(b)) {
                            t = b.toImmutable();
                            break;
                        }
                    }
                    if (t == null) t = roomDigTarget(feet);
                    if (t != null && dig(t)) {
                        if (mc.player.age % 40 == 0) LOG.info("[VG] takeoff: no room at {} - digging {}", feet.toShortString(), t.toShortString());
                        takeoffDoing = "no headroom: digging " + t.toShortString();
                        return;
                    }
                }
                if (mc.player.age % 100 == 0) {
                    snapAge = -10000;
                    snapshot("no room to take off, nothing to dig");
                }
            }
            if (headroom) centerTicks = 0;
            if (takeoffWait == 0) {
                if (++jumps > 8) {
                    jumps = 0;
                    takeoffFails++;
                    m.warning("Couldn't take off after 8 tries (%d) - trying again%s.", takeoffFails, mineThrough.get() ? ", digging upwards" : "");
                    snapAge = -10000;
                    snapshot("takeoff failed x" + takeoffFails);
                }
                BlockPos head = mc.player.getBlockPos().up(2);
                if (FlightPathfinder.blocks(mc.world, head, mc.world.getBlockState(head)) && lowCeilingWarned++ == 0) {
                    m.warning("Low ceiling - taking off may not work here.");
                }
                mc.player.jump();
                takeoffWait = 10;
                lowTakeoff = lowJump && Tuning.b("takeoff.low", false);
                takeoffDoing = "jumped (headroom=" + headroom + (lowJump ? ", low hop" : "") + ")";
            }
            else takeoffDoing = "waiting to jump";
        }
        else {
            double yNow = mc.player.getY();
            airStuckTicks = !Double.isNaN(lastAirY) && Math.abs(yNow - lastAirY) < 0.02 ? airStuckTicks + 1 : 0;
            lastAirY = yNow;
            if (airStuckTicks >= Tuning.i("takeoff.airstuck.ticks", 10) && !mc.player.isInLava()
                && mc.world.isSpaceEmpty(mc.player, new Box(mc.player.getX() - 0.3, mc.player.getY(), mc.player.getZ() - 0.3,
                    mc.player.getX() + 0.3, mc.player.getY() + 3.0, mc.player.getZ() + 0.3))) {
                airStuckTicks = 0;
                takeoffGroundY = yNow;
                takeoffWait = 10;
                mc.player.jump();
                LOG.info("[VG] takeoff: held in mid-air at ({}) - jumping off the unseen ground", v(mc.player.getEntityPos()));
                return;
            }
            double spaceH = takeoffFails >= 1 ? 1.0 : lowTakeoff ? 1.8 : Tuning.d("takeoff.space", 1.85);
            Box standing = new Box(mc.player.getX() - 0.3, mc.player.getY(), mc.player.getZ() - 0.3, mc.player.getX() + 0.3, mc.player.getY() + spaceH, mc.player.getZ() + 0.3);
            boolean space = mc.world.isSpaceEmpty(mc.player, standing);
            boolean falling = mc.player.getVelocity().y < 0, lava = mc.player.isInLava();
            double clearBelow = -net.minecraft.entity.Entity.adjustMovementForCollisions(mc.player, new Vec3d(0, -1.0, 0), mc.player.getBoundingBox(), mc.world, java.util.List.of()).y;
            boolean lavaUnder = false;
            if (takeoffFails < 1) {
                Box bb = mc.player.getBoundingBox();
                BlockPos.Mutable q = new BlockPos.Mutable();
                int yTop = MathHelper.floor(bb.minY), yBot = MathHelper.floor(bb.minY - Tuning.d("takeoff.lava.below", 0));
                for (int lx = MathHelper.floor(bb.minX); lx <= MathHelper.floor(bb.maxX) && !lavaUnder; lx++) {
                    for (int lz = MathHelper.floor(bb.minZ); lz <= MathHelper.floor(bb.maxZ) && !lavaUnder; lz++) {
                        for (int ly = yTop; ly >= yBot; ly--) {
                            q.set(lx, ly, lz);
                            BlockState ls = mc.world.getBlockState(q);
                            if (ls.getFluidState().isIn(FluidTags.LAVA)) {
                                lavaUnder = true;
                                break;
                            }
                            if (!ls.getCollisionShape(mc.world, q).isEmpty()) break;
                        }
                    }
                }
            }
            boolean room = clearBelow >= (takeoffFails >= 1 ? 0.4 : lowTakeoff ? Tuning.d("takeoff.low.air", 0.7) : Tuning.d("takeoff.min.air", 0.7)) && !lavaUnder;
            boolean tried = false, ok = false;
            if (falling && takeoffWait <= (lowTakeoff ? 9 : 7) && !lava && room && space) {
                tried = true;
                if (srvGlide == 1 && Tuning.b("glide.desync.fix", true)) {
                    mc.player.startGliding();
                    ok = true;
                    LOG.info("[VG] elytra: the server already has us gliding - joining it");
                }
                else {
                    ok = mc.player.checkGliding();
                    if (ok && Tuning.b("takeoff.two.step", true)) pendingStartAge = mc.player.age;
                    else if (ok) {
                        mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                        startSentAge = mc.player.age;
                        if (srvGlide != 1) srvGlide = 0;
                    }
                }
            }
            if (Tuning.b("log.takeoff", false)) LOG.info("[VG] takeoff air: y={} vy={} wait={} lava={} room={} tried={} ok={} gliding={} chest={}",
                String.format("%.2f", mc.player.getY()), String.format("%.2f", mc.player.getVelocity().y), takeoffWait, lava, room, tried, ok,
                mc.player.isGliding(), mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem());
        }
    }

    private void updateGoal() {
        if (--goalCheckAge > 0) return;
        goalCheckAge = 20;

        int tx = targetX.get(), tz = targetZ.get();
        BlockPos g = null;
        if (fixedGoal != null) {
            hoverOnly = false;
            g = fixedGoal;
        }
        else if (mc.world.getChunkManager().isChunkLoaded(tx >> 4, tz >> 4)) {
            hoverOnly = false;
            if (arrive.get() == Arrive.Hover) g = nearestOpen(tx, targetY.get(), tz);
            else {
                g = safeLandingNear(tx, tz, 8);
                if (g == null) {
                    int y = mc.world.getDimension().hasCeiling() ? mc.player.getBlockY() : mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, tx, tz) + 3;
                    g = nearestOpen(tx, y, tz);
                    hoverOnly = g != null;
                }
            }
            if (!landingWarned && (g == null || hoverOnly)) {
                landingWarned = true;
                if (g == null) m.warning(arrive.get() == Arrive.Hover ? "%s is inside blocks - pick another height." : "Nowhere to land or hover at %s.", targetString());
                else m.warning("Nowhere safe to land within 8 blocks of %s (water/lava?) - will hover above it.", targetString());
            }
        }
        if (g != null && !g.equals(goalNode)) {
            boolean first = goalNode == null;
            goalNode = g;
            replanNow = true;
            if (first && arrive.get() == Arrive.Land) m.info("Landing spot: %d %d %d.", g.getX(), g.getY(), g.getZ());
        }
    }

    private BlockPos nearestOpen(int x, int y, int z) {
        for (int r = 0; r <= 4; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dy), Math.max(Math.abs(dx), Math.abs(dz))) != r) continue;
                        if (FlightPathfinder.open(mc.world, x + dx, y + dy, z + dz)) return new BlockPos(x + dx, y + dy, z + dz);
                    }
                }
            }
        }
        return null;
    }

    private BlockPos safeLandingNear(int x, int z, int r) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double dd = dx * dx + dz * dz;
                if (dd > r * r || dd >= bestD) continue;
                if (!mc.world.getChunkManager().isChunkLoaded((x + dx) >> 4, (z + dz) >> 4)) continue;
                BlockPos s = landingSpot(x + dx, z + dz);
                if (s != null && safeFloor(s.down())) {
                    best = s;
                    bestD = dd;
                }
            }
        }
        return best;
    }

    private boolean safeFloor(BlockPos floor) {
        BlockState st = mc.world.getBlockState(floor);
        if (!st.getFluidState().isEmpty() || BAD_FLOOR.contains(st.getBlock())) return false;
        if (st.getCollisionShape(mc.world, floor).isEmpty()) return false;
        BlockPos up = floor.up();
        for (BlockPos n : new BlockPos[] {up.north(), up.south(), up.east(), up.west()}) {
            BlockState ns = mc.world.getBlockState(n);
            FluidState fs = ns.getFluidState();
            if (fs.isIn(FluidTags.LAVA) || ns.isOf(Blocks.FIRE) || ns.isOf(Blocks.SOUL_FIRE)) return false;
        }
        return true;
    }

    private BlockPos landingSpot(int x, int z) {
        int bottom = mc.world.getBottomY(), top = mc.world.getTopYInclusive() - 1;
        if (!mc.world.getDimension().hasCeiling()) {
            int y = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
            return standable(x, y, z) ? new BlockPos(x, y, z) : null;
        }
        int from = MathHelper.clamp(mc.player.getBlockY(), bottom + 1, top);
        for (int y = from; y > bottom; y--) if (standable(x, y, z)) return new BlockPos(x, y, z);
        for (int y = from + 1; y <= top; y++) if (standable(x, y, z)) return new BlockPos(x, y, z);
        return null;
    }

    private boolean standable(int x, int y, int z) {
        if (!FlightPathfinder.open(mc.world, x, y, z)) return false;
        BlockPos below = new BlockPos(x, y - 1, z);
        return !mc.world.getBlockState(below).getCollisionShape(mc.world, below).isEmpty();
    }

    private boolean needReplan(Vec3d pos) {
        if (tunneling && !path.isEmpty()) {
            if (pathIdx < path.size() - 2) return false;
            tunneling = false;
            trappedSearches = 0;
            tunnelEndAge = mc.player.age;
            return true;
        }
        if (replanNow || path.isEmpty()) return true;
        if (ticksSinceSearch >= Tuning.i("replan.ticks", 100)) return true;
        if (pathEndsAtFrontier && ticksSinceSearch >= Tuning.i("frontier.replan.ticks", 5) && pos.distanceTo(path.getLast()) < Tuning.d("frontier.replan.distance", 24)) return true;
        if (!pathReachesGoal) {
            double left = 0;
            for (int i = Math.max(pathIdx, 0); i < path.size() - 1 && left < 64; i++) left += path.get(i).distanceTo(path.get(i + 1));
            if (left < Math.min(64, effectiveRadius() / 2.0) && ticksSinceSearch >= 10) return true;
        }
        return false;
    }

    private int effectiveRadius() {
        int view = Math.max(2, mc.options.getClampedViewDistance()) * 16 - 16;
        return Math.max(32, Math.min(searchRadius.get(), view));
    }

    private void startSearch() {
        searchTicks = 0;
        boolean urgent = replanNow || path.isEmpty();
        replanNow = false;
        ticksSinceSearch = 0;

        BlockPos start = startBlock();
        extendPath = null;
        if (!urgent && Tuning.b("search.extend", true) && !tunneling && mineModeTicks == 0 && path.size() - pathIdx > 3) {
            for (int ei = path.size() - 1; ei >= Math.max(pathIdx + 2, path.size() - 4); ei--) {
                BlockPos c = BlockPos.ofFloored(path.get(ei));
                if (FlightPathfinder.open(mc.world, c.getX(), c.getY(), c.getZ())) {
                    start = c;
                    extendPath = path;
                    extendIdx = ei;
                    break;
                }
            }
        }
        int bottom = mc.world.getBottomY(), top = mc.world.getTopYInclusive() - 1;
        if (vf.limitMaxHeight.get()) top = Math.min(top, (int) Math.floor(vf.maxHeight.get()) - 1);

        double desiredY;
        double yWeight;
        double tripLeft = Math.hypot(targetX.get() + 0.5 - (start.getX() + 0.5), targetZ.get() + 0.5 - (start.getZ() + 0.5));
        FlightPathfinder.FLOOR_CLEARANCE = Math.max(0, Tuning.i("path.floor.clearance", 10));
        FlightPathfinder.FLOOR_WEIGHT = Tuning.d("path.floor.weight", 0.5);
        FlightPathfinder.CEILING_WEIGHT = Tuning.d("path.ceiling.weight", 0.3);
        FlightPathfinder.LAVA_COST = Tuning.d("path.lava.cost", 20);
        FlightPathfinder.FLOOR_TOUCH = Tuning.d("path.floor.touch", 15);
        FlightPathfinder.LAVA_OK_COST = Tuning.d("path.lavaok.cost", 6);
        if (mc.world.getDimension().hasCeiling() && tripLeft >= cruiseDistance.get()) {
            desiredY = MathHelper.clamp(Tuning.i("nether.cruise.y", 95), bottom + 1, top - 1);
            yWeight = Tuning.d("nether.cruise.weight", 0.3);
        }
        else if (mc.world.getDimension().hasCeiling() || tripLeft < cruiseDistance.get()) {
            desiredY = MathHelper.clamp(start.getY(), bottom + 1, top - 1);
            yWeight = 0.25;
        }
        else {
            desiredY = MathHelper.clamp(cruiseHeight.get(), bottom + 1, top - 1);
            yWeight = 1.0;
        }

        try {
            java.util.ArrayList<net.minecraft.entity.Entity> mobs = new java.util.ArrayList<>();
            for (net.minecraft.entity.Entity e : mc.world.getEntities()) {
                if (e instanceof net.minecraft.entity.mob.HostileEntity && e.isAlive() && e.squaredDistanceTo(mc.player) < 64 * 64) mobs.add(e);
            }
            mobs.sort(java.util.Comparator.comparingDouble(e -> e.squaredDistanceTo(mc.player)));
            int nMobs = Math.min(mobs.size(), Tuning.i("path.mob.max", 40));
            double[] dg = new double[nMobs * 3];
            for (int mi = 0; mi < nMobs; mi++) {
                net.minecraft.entity.Entity e = mobs.get(mi);
                dg[mi * 3] = e.getX();
                dg[mi * 3 + 1] = e.getY() + e.getHeight() / 2;
                dg[mi * 3 + 2] = e.getZ();
            }
            FlightPathfinder.DANGERS = dg;
            FlightPathfinder.DANGER_RADIUS = Tuning.d("path.mob.radius", 5);
            FlightPathfinder.DANGER_COST = Tuning.d("path.mob.cost", 12);
        }
        catch (Throwable t) {
            FlightPathfinder.DANGERS = new double[0];
        }
        search = new FlightPathfinder(mc.world, start, goalNode, targetX.get() + 0.5, targetZ.get() + 0.5, desiredY, yWeight,
            effectiveRadius(), waitForChunks.get(), bottom, top, maxNodes.get(), wallAvoidance.get(), allowLowGaps.get(),
            mineModeTicks > 0 ? mineCost.get() : 0, noMine).lavaOk(lavaOkNow);
    }

    private BlockPos startBlock() {
        Vec3d p = mc.player.getEntityPos();
        BlockPos b = BlockPos.ofFloored(p.x, p.y + 0.05, p.z);
        if (FlightPathfinder.open(mc.world, b.getX(), b.getY(), b.getZ())) return b;
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos q : BlockPos.iterate(b.add(-1, -1, -1), b.add(1, 1, 1))) {
            if (!FlightPathfinder.open(mc.world, q.getX(), q.getY(), q.getZ())) continue;
            double d = Vec3d.ofBottomCenter(q).squaredDistanceTo(p);
            if (d < bestD) {
                bestD = d;
                best = q.toImmutable();
            }
        }
        return best != null ? best : b;
    }

    private void searchDone(FlightPathfinder.Status s) {
        FlightPathfinder done = search;
        search = null;
        LOG.info("[VG] search {} expanded={} pathLen={} from=({})", s, done.expanded(), done.path().size(), v(mc.player.getEntityPos()));

        if (tunneling) return;
        List<Vec3d> ext = extendPath;
        extendPath = null;
        if (ext != null && (ext != path || s == FlightPathfinder.Status.FAILED || done.path().size() < 2
            || (s == FlightPathfinder.Status.PARTIAL && !done.endsAtFrontier() && done.bestGain() < Tuning.d("trapped.gain", 8)))) {
            if (ext == path && pathIdx >= path.size() - 6) replanNow = true;
            return;
        }
        boolean nowhere = s == FlightPathfinder.Status.FAILED || done.path().size() < 2
            || (s == FlightPathfinder.Status.PARTIAL && !done.endsAtFrontier() && done.bestGain() < Tuning.d("trapped.gain", 8));
        trappedSearches = nowhere ? trappedSearches + 1 : 0;
        if (nowhere && trappedSearches >= Tuning.i("trapped.searches", 2) && mineThrough.get() && startTunnel()) return;
        if (s == FlightPathfinder.Status.FAILED || done.path().size() < 2) {
            if (mineThrough.get() && mineModeTicks == 0) {
                startDigging("No way around - digging through.");
                return;
            }
            failures++;
            if (failures >= 8) {
                failures = 0;
                m.warning("Can't find a way from here yet - retrying%s.", mineThrough.get() ? " with digging" : "");
                if (mineThrough.get()) startDigging("Digging allowed: no route found.");
                retryIn = 40;
                return;
            }
            if (failures == 1 || failures % 3 == 0) m.warning("No way forward found yet (tried %d blocks), trying again...", done.expanded());
            retryIn = 20;
            replanNow = true;
            return;
        }

        if (mc.player.age - tunnelEndAge < Tuning.i("tunnel.end.window", 200) && mineThrough.get()) {
            BlockPos lastB = done.path().get(done.path().size() - 1);
            double gx = targetX.get() + 0.5, gz = targetZ.get() + 0.5;
            double dEnd = Math.hypot(gx - (lastB.getX() + 0.5), gz - (lastB.getZ() + 0.5));
            double dHere = Math.hypot(gx - mc.player.getX(), gz - mc.player.getZ());
            if (dEnd > dHere - Tuning.d("tunnel.end.gain", 16)) {
                LOG.info("[VG] the route from the tunnel's end gets no nearer ({} vs {} blocks from the destination) - tunnelling on", String.format("%.0f", dEnd), String.format("%.0f", dHere));
                if (startTunnel()) return;
            }
        }
        failures = 0;
        ticksSinceSearch = 0;
        List<Vec3d> oldPath = path;
        int oldIdx = pathIdx;
        ArrayList<Vec3d> pts = new ArrayList<>(done.path().size());
        for (BlockPos b : done.path()) pts.add(new Vec3d(b.getX() + 0.5, b.getY() + NODE_Y, b.getZ() + 0.5));
        if (plannedMine.size() > 20000) plannedMine.clear();
        for (BlockPos b : done.path()) if (done.plannedMine(b)) plannedMine.add(b.asLong());
        boolean extended = false;
        if (ext != null && extendIdx < oldPath.size()) {
            int from = Math.max(0, oldIdx - 8);
            ArrayList<Vec3d> joined = new ArrayList<>(oldPath.subList(from, Math.max(from, extendIdx)));
            int keep = joined.size();
            while (!joined.isEmpty() && !pts.isEmpty() && joined.get(joined.size() - 1).distanceTo(pts.get(0)) < 0.01) pts.remove(0);
            joined.addAll(pts);
            pts = joined;
            extended = true;
            LOG.info("[VG] path extended: kept {} + {} new nodes", keep, pts.size() - keep);
        }
        path = pts;
        pathReachesGoal = s == FlightPathfinder.Status.FOUND;
        pathEndsAtFrontier = done.endsAtFrontier();
        pathIdx = 0;
        pathIdx = nearestIndex(mc.player.getEntityPos(), 0, extended ? Math.min(path.size(), 56) : path.size());
        noCarrotTicks = 0;
        if (!extended && !oldPath.isEmpty() && oldPath.size() - oldIdx > 12 && path.size() - pathIdx > 12) {
            Vec3d newAhead = path.get(Math.min(path.size() - 1, pathIdx + 12));
            double best = Double.MAX_VALUE;
            for (int i = oldIdx; i < Math.min(oldPath.size(), oldIdx + 40); i++) best = Math.min(best, oldPath.get(i).distanceTo(newAhead));
            if (best > Tuning.d("route.change.dist", 4)) {
                Vec3d oldAhead = oldPath.get(Math.min(oldPath.size() - 1, oldIdx + 12));
                LOG.info("[VG] ROUTE CHANGE at ({}): was heading to ({}), now ({}) - {} apart; search {} {} nodes",
                    v(mc.player.getEntityPos()), v(oldAhead), v(newAhead), String.format("%.1f", best), s, done.expanded());
            }
        }
    }

    @Override
    public void move(PlayerMoveEvent event) {
        if (event.type == MovementType.SELF && mc.player != null && groundNudge != null && !mc.player.isGliding()) {
            ((IVec3d) event.movement).meteor$set(groundNudge.x, event.movement.y, groundNudge.z);
            groundNudge = null;
            return;
        }
        if (event.type != MovementType.SELF || mc.player == null || !mc.player.isGliding() || vf == null || !vf.isActive()) return;
        boolean confirmed = srvGlide == 1 || !srvSeenTrue || !Tuning.b("glide.wait.confirm", true);
        if (phase == Phase.TakeOff || !confirmed) {
            Vec3d sinkMove = new Vec3d(0, -Tuning.d("glide.wait.sink", 0.0), 0);
            Vec3d adj = net.minecraft.entity.Entity.adjustMovementForCollisions(mc.player, sinkMove, mc.player.getBoundingBox(), mc.world, java.util.List.of());
            ((IVec3d) event.movement).meteor$set(0, adj.y, 0);
            dbgOurMove = new Vec3d(0, adj.y, 0);
            lastCmd = dbgOurMove;
            lastCmdSpeed = Math.abs(adj.y);
            curSpeed = 0;
            curV = 0;
            lastMovePos = null;
            lastMoveEventAge = mc.player.age;
            unconfirmedTicks++;
            mc.player.setVelocity(0, 0, 0);
            history.addLast("t" + mc.player.age + " pos(" + v(mc.player.getEntityPos()) + ") waiting for the server to open the elytra (srv=" + srvGlide + ")");
            while (history.size() > 40) history.pollFirst();
            return;
        }
        if (lastMoveEventAge != mc.player.age - 1) {
            lastCmd = new Vec3d(event.movement.x, event.movement.y, event.movement.z);
            if (lastCmd.length() > 1.0) lastCmd = lastCmd.multiply(1.0 / lastCmd.length());
            lastCmdSpeed = lastCmd.length();
            lastMovePos = null;
            stallTicks = 0;
        }
        lastMoveEventAge = mc.player.age;
        dbgVfMove = new Vec3d(event.movement.x, event.movement.y, event.movement.z);
        double sink = keepVfSink.get() && dbgVfMove.y < 0 && dbgVfMove.y > -Tuning.d("sink.max", 0.5) ? dbgVfMove.y : 0;
        sink = Math.min(sink, -Tuning.d("sink.min", 0));
        if (setbackTicks > 0) {
            ((IVec3d) event.movement).meteor$set(0, sink, 0);
            dbgOurMove = new Vec3d(0, sink, 0);
            lastCmdSpeed = Math.abs(sink);
            lastCmd = new Vec3d(0, sink, 0);
            history.addLast("t" + mc.player.age + " pos(" + v(mc.player.getEntityPos()) + ") setback pause" + (mc.player.horizontalCollision ? " HC" : ""));
            while (history.size() > 40) history.pollFirst();
            return;
        }

        Vec3d pos = mc.player.getEntityPos();
        Vec3d mv = event.movement;

        if (lastMovePos != null && lastWant.length() > 0.3) {
            if (pos.distanceTo(lastMovePos) < 0.3 * lastWant.length()) {
                if (++stallTicks >= Tuning.i("stall.ticks", 4)) {
                    stallTicks = 0;
                    if (creepTicks == 0) {
                        LOG.info("[VG] caught on something at ({}) - lining up on the grid", v(pos));
                        snapshot("caught on something (pushing, not moving)");
                    }
                    creepTicks = Tuning.i("creep.ticks", 60);
                }
            }
            else stallTicks = 0;
        }

        double vMin = Math.max(0.5, vf.startSpeed.get());
        double vTop = Math.max(vMin, vf.horizontalSpeed.get());
        if (maxSpeed.get() > 0) vTop = Math.min(vTop, maxSpeed.get());
        vTop = Math.min(vTop, setbackCap);
        if (pairedTicks) vTop = Math.min(vTop, Tuning.d("paired.speed.cap", 1.5));
        double capT = Tuning.d("speed.cap", 0);
        if (capT > 0) vTop = Math.min(vTop, capT);
        double sH = MathHelper.clamp(curSpeed, Math.min(Math.min(vMin, vTop), Tuning.d("speed.turn.min", 0.8)), vTop);
        double vTopV = Math.min(Math.max(MIN_V, vf.verticalSpeed.get()), setbackCapV);
        if (pairedTicks) vTopV = Math.min(vTopV, Tuning.d("paired.speed.cap", 1.5));
        double sV = MathHelper.clamp(curV, Math.min(MIN_V, vTopV), vTopV);

        Vec3d want;
        double creepSpeed = Math.min(sH, Tuning.d("creep.speed", 0.6));
        if (mineTarget != null || digOutTarget != null || creepTicks > 0 || mending) {
            curSpeed = Math.min(curSpeed, vMin);
            curV = Math.min(curV, MIN_V);
        }
        if (phase == Phase.Landing) curSpeed = Math.min(curSpeed, vMin);
        boolean escaping = mc.player.isInLava() && !lavaOkNow;
        if (escaping) {
            if (phase == Phase.Landing) {
                phase = Phase.Flying;
                replanNow = true;
            }
            double es = Tuning.d("lava.escape.speed", 0.8);
            Vec3d exit = lavaExit(pos);
            Vec3d up = new Vec3d(0, es, 0);
            if (exit != null) {
                Vec3d d = exit.subtract(pos);
                Vec3d direct = d.length() > es ? d.multiply(es / d.length()) : d;
                if (clearSolid(pos, pos.add(direct))) want = direct;
                else if (d.y > 0.2 && clearSolid(pos, pos.add(up))) want = up;
                else {
                    want = Vec3d.ZERO;
                    double[] c = {d.x, d.y, d.z};
                    for (int a = 0; a < 3 && want == Vec3d.ZERO; a++) {
                        double st = MathHelper.clamp(c[a], -es, es);
                        if (Math.abs(st) < 1e-3) continue;
                        Vec3d stepV = a == 0 ? new Vec3d(st, 0, 0) : a == 1 ? new Vec3d(0, st, 0) : new Vec3d(0, 0, st);
                        if (clearSolid(pos, pos.add(stepV))) want = stepV;
                    }
                }
            }
            else want = clearSolid(pos, pos.add(up)) ? up : Vec3d.ZERO;
        }
        else if (phase == Phase.Landing) {
            Vec3d spot = goalNode != null ? Vec3d.ofBottomCenter(goalNode) : (path.isEmpty() ? pos : path.getLast());
            sV = rampV(Math.abs(pos.y - spot.y), vTopV);
            want = landingMove(pos, sV);
        }
        else if (mending) {
            want = Vec3d.ZERO;
            if (mendBelow > 3) {
                Vec3d down = new Vec3d(0, -Math.min(sV, Math.max(0.2, (mendBelow - 2.5) * 0.4)), 0);
                if (clearLine(pos, pos.add(down), -0.01)) want = down;
            }
        }
        else if (digOutTarget != null) want = Vec3d.ZERO;
        else if (mineTarget != null) want = pos.distanceTo(mineFrom) > 0.02 ? creep(pos, mineFrom, creepSpeed) : Vec3d.ZERO;
        else if (digHold != null) want = pos.distanceTo(digHold) > 0.02 ? creep(pos, digHold, creepSpeed) : Vec3d.ZERO;
        else if (creepTicks > 0) want = creepAlong(pos, creepSpeed);
        else {
            Vec3d carrot = pickCarrot(pos);
            dbgCarrot = carrot;
            if (carrot != null) {
                double toCarrot = carrot.distanceTo(pos);
                double ahead = toCarrot + straightPast(carrot.subtract(pos));
                double turnMin = Math.min(Math.min(vMin, vTop), Tuning.d("speed.turn.min", 0.8));
                double decelPlan = Tuning.d("decel.plan", 0.15);
                double target = Math.min(vTop, Math.min(turnMin + braking.get() * ahead, Math.sqrt(turnMin * turnMin + 2 * decelPlan * ahead)));
                double vfNow = Math.hypot(dbgVfMove.x, dbgVfMove.z);
                if (followVfRamp.get() && vfNow > 1e-3) target = Math.min(target, Math.max(Math.min(vMin, vTop), vfNow));
                curSpeed = curSpeed < target ? Math.min(target, Math.max(curSpeed, turnMin) + acceleration.get()) : target;
                sH = curSpeed;
                double vShare = toCarrot > 1e-4 ? Math.abs(carrot.y - pos.y) / toCarrot : 0;
                sV = rampV(ahead * vShare, vTopV);
                dbgTargetSpeed = target;
                dbgClearAhead = ahead;
            }
            else {
                curSpeed = Math.min(curSpeed, vMin);
                curV = Math.min(curV, MIN_V);
            }
            if (carrot == null) {
                boolean atFrontierEnd = pathEndsAtFrontier && !path.isEmpty() && pos.distanceTo(path.getLast()) < 3;
                if (!atFrontierEnd && noCarrotTicks > 3) want = creepAlong(pos, creepSpeed);
                else want = Vec3d.ZERO;
                if (atFrontierEnd && mc.player.age - waitMsgAge > 200) {
                    waitMsgAge = mc.player.age;
                    m.info("Waiting for chunks ahead to load...");
                }
            }
            else if (carrotTight) want = creepAlong(pos, Math.min(sH, 1.0));
            else {
                Vec3d d = carrot.subtract(pos);
                double dh = Math.hypot(d.x, d.z), dy = Math.abs(d.y);
                double t = 1;
                if (dh > 1e-4) t = Math.min(t, sH / dh);
                if (dy > 1e-4) t = Math.min(t, sV / dy);
                want = d.multiply(t);
            }
        }

        if (Math.hypot(want.x, want.z) > 0.05) {
            travelYaw = (float) Math.toDegrees(Math.atan2(-want.x, want.z));
            if (rotate.get() == Rotate.Camera) mc.player.setYaw(travelYaw);
        }
        if (!escaping && sink < 0 && Math.abs(want.y) < -sink) want = new Vec3d(want.x, sink, want.z);
        if (!lavaOkNow && Math.abs(want.y) <= 0.05 && Math.hypot(want.x, want.z) < 0.05) {
            Box under = mc.player.getBoundingBox();
            boolean lavaUnder = false;
            BlockPos.Mutable lq = new BlockPos.Mutable();
            for (int lx = MathHelper.floor(under.minX); lx <= MathHelper.floor(under.maxX) && !lavaUnder; lx++) {
                for (int lz = MathHelper.floor(under.minZ); lz <= MathHelper.floor(under.maxZ) && !lavaUnder; lz++) {
                    for (int ly = MathHelper.floor(under.minY); ly >= MathHelper.floor(under.minY - Tuning.d("hover.lava.clear", 1.5)); ly--) {
                        lq.set(lx, ly, lz);
                        BlockState ls = mc.world.getBlockState(lq);
                        if (ls.getFluidState().isIn(FluidTags.LAVA)) {
                            lavaUnder = true;
                            break;
                        }
                        if (!ls.getCollisionShape(mc.world, lq).isEmpty()) break;
                    }
                }
            }
            if (lavaUnder) want = new Vec3d(want.x, Tuning.d("hover.lava.rise", 0.15), want.z);
        }
        double len = want.length();
        double ps = pairedTicks ? 0.5 : 1.0;
        double freeSpeed = Tuning.d("accel.free.speed", 2.95), accHigh = Tuning.d("accel.high", 0.1) * ps;
        double lim = lastCmdSpeed < freeSpeed
            ? Math.min(lastCmdSpeed + Tuning.d("accel.max", 0.1) * ps, Math.max(freeSpeed, lastCmdSpeed + accHigh))
            : lastCmdSpeed + accHigh;
        if (slowAfterSetback > 0) {
            slowAfterSetback--;
            lim = Math.min(lim, Tuning.d("setback.slow.speed", 1.5));
        }
        lim = Math.max(lim, Tuning.d("accel.floor", 0.2));
        if (len > lim && len > 1e-6) want = want.multiply(lim / len);
        double hPrev = Math.hypot(lastCmd.x, lastCmd.z), hWantNow = Math.hypot(want.x, want.z);
        double limH = hPrev < freeSpeed
            ? Math.min(hPrev + Tuning.d("accel.max", 0.1) * ps, Math.max(freeSpeed, hPrev + accHigh))
            : hPrev + accHigh;
        limH = Math.max(limH, Tuning.d("accel.floor", 0.2));
        if (hWantNow > limH && hWantNow > 1e-6 && Tuning.b("accel.horizontal", true)) want = new Vec3d(want.x * limH / hWantNow, want.y, want.z * limH / hWantNow);
        double dvMax = Tuning.d("dv.max", 0.2) * ps;
        Vec3d dv = want.subtract(lastCmd);
        if (!escaping && dvMax > 0 && dv.length() > dvMax && lastCmd.length() > Tuning.d("dv.free.below", 0.25)) {
            Vec3d limited = lastCmd.add(dv.multiply(dvMax / dv.length()));
            if (!clearLine(pos, pos.add(limited), -0.01)) {
                double ls = lastCmd.length(), dec = Math.min(dvMax, Tuning.d("decel.max", 0.2));
                limited = ls > dec ? lastCmd.multiply((ls - dec) / ls) : Vec3d.ZERO;
            }
            want = limited;
            dvLimited++;
        }
        double hNow = Math.hypot(want.x, want.z), hLast = Math.hypot(lastCmd.x, lastCmd.z);
        double maxDrop = Tuning.d("decel.max", 0.2) * ps;
        if (!escaping && hLast - hNow > maxDrop && hLast > 1e-6) {
            double hNew = hLast - maxDrop;
            boolean reversing = want.x * lastCmd.x + want.z * lastCmd.z < 0;
            double ux = hNow > 1e-4 && !reversing ? want.x / hNow : lastCmd.x / hLast, uz = hNow > 1e-4 && !reversing ? want.z / hNow : lastCmd.z / hLast;
            Vec3d cand = new Vec3d(ux * hNew, want.y, uz * hNew);
            if (clearLine(pos, pos.add(cand), -0.01)) want = cand;
            else {
                Vec3d old = new Vec3d(lastCmd.x / hLast * hNew, want.y, lastCmd.z / hLast * hNew);
                if (clearLine(pos, pos.add(old), -0.01)) want = old;
                else hardBrakes++;
            }
        }
        if (Tuning.b("collide.resolve", true) && want.lengthSquared() > 1e-10) {
            Vec3d adj = net.minecraft.entity.Entity.adjustMovementForCollisions(mc.player, want, mc.player.getBoundingBox(), mc.world, java.util.List.of());
            if (Math.abs(adj.x - want.x) > 1e-6 || Math.abs(adj.z - want.z) > 1e-6) {
                wallClips++;
                double lost = Math.hypot(want.x, want.z) - Math.hypot(adj.x, adj.z);
                if (lost > 0.3) LOG.info("[VG] clip avoided at ({}): wanted ({}) -> ({}), {} b/t lost", v(pos), v(want), v(adj), String.format("%.2f", lost));
                double fy = adj.y > want.y + 1e-6 && want.y < 0 && phase != Phase.Landing ? adj.y + Tuning.d("floor.gap", 0.12) : adj.y;
                want = new Vec3d(adj.x * 0.98, fy, adj.z * 0.98);
                creepTicks = Math.max(creepTicks, 10);
            }
            else if (Math.abs(adj.y - want.y) > 1e-6) {
                double fy = adj.y > want.y && want.y < 0 && phase != Phase.Landing ? adj.y + Tuning.d("floor.gap", 0.12) : adj.y;
                want = new Vec3d(want.x, fy, want.z);
            }
        }
        if (!lavaOkNow && !escaping && want.lengthSquared() > 1e-8 && !mc.player.isInLava() && lavaTouches(mc.player.getBoundingBox().offset(want))) {
            Vec3d pick = Vec3d.ZERO;
            for (Vec3d alt : new Vec3d[] {new Vec3d(want.x * 0.5, Math.max(want.y, 0), want.z * 0.5), new Vec3d(0, Math.max(want.y, 0), 0), new Vec3d(0, 0.15, 0)}) {
                Vec3d adj2 = net.minecraft.entity.Entity.adjustMovementForCollisions(mc.player, alt, mc.player.getBoundingBox(), mc.world, java.util.List.of());
                if (!lavaTouches(mc.player.getBoundingBox().offset(adj2))) {
                    pick = adj2;
                    break;
                }
            }
            if (mc.player.age - lavaLogAge > 20) {
                lavaLogAge = mc.player.age;
                LOG.info("[VG] lava dodge at ({}): ({}) would have ended in lava -> ({})", v(pos), v(want), v(pick));
            }
            want = pick;
            creepTicks = Math.max(creepTicks, 10);
            replanNow = true;
        }
        lastCmdSpeed = want.length();
        lastCmd = want;
        if (Tuning.b("tps.sync", false)) {
            double tps = meteordevelopment.meteorclient.utils.world.TickRate.INSTANCE.getTickRate();
            if (tps > 1 && tps < Tuning.d("tps.sync.below", 19.0)) want = want.multiply(Math.max(Tuning.d("tps.sync.min", 0.5), tps / 20.0));
        }
        if (tickDtMs < Tuning.d("tick.catchup.ms", 30) && Tuning.b("tick.catchup.scale", false)) {
            want = want.multiply(Math.max(Tuning.d("tick.catchup.min", 0.0), tickDtMs / 50.0));
            catchups++;
        }
        ((IVec3d) event.movement).meteor$set(want.x, want.y, want.z);
        history.addLast(String.format("t%d pos(%.2f %.2f %.2f) want(%.2f %.4f %.2f) vf(%.2f %.4f %.2f) sH=%.2f sV=%.2f%s%s%s",
            mc.player.age, pos.x, pos.y, pos.z, want.x, want.y, want.z, dbgVfMove.x, dbgVfMove.y, dbgVfMove.z, sH, sV,
            creepTicks > 0 ? " creep" : "", carrotTight ? " tight" : "", mineTarget != null ? " mine" : "")
            + (mc.player.horizontalCollision ? " HC" : "") + (mc.player.verticalCollision ? " VC" : "") + (setbackTicks > 0 ? " sb" : ""));
        while (history.size() > 40) history.pollFirst();
        lastMovePos = pos;
        lastWant = want;
        dbgOurMove = want;
        dbgSH = sH;
    }

    private double rampV(double verticalAhead, double vTopV) {
        double lo = Math.min(MIN_V, vTopV);
        double target = Math.min(vTopV, lo + braking.get() * verticalAhead);
        curV = curV < target ? Math.min(target, Math.max(curV, lo) + acceleration.get()) : target;
        return Math.max(curV, lo);
    }

    private double straightPast(Vec3d dir) {
        if (carrotIdx < 0 || dir.lengthSquared() < 1e-6) return 0;
        Vec3d u = dir.normalize();
        double run = 0;
        Vec3d from = path.get(Math.min(carrotIdx, path.size() - 1));
        for (int i = carrotIdx + 1; i < path.size() && run < 160; i++) {
            Vec3d d = path.get(i).subtract(from);
            double len = d.length();
            if (len < 1e-6) continue;
            if (d.multiply(1 / len).dotProduct(u) < 0.94) break;
            run = len;
        }
        return run;
    }

    private Vec3d creepAlong(Vec3d pos, double speed) {
        if (path.isEmpty()) return Vec3d.ZERO;
        pathIdx = nearestIndex(pos, Math.max(0, pathIdx - 16), Math.min(path.size(), pathIdx + 96));
        Vec3d node = path.get(pathIdx);
        if (pathIdx + 1 >= path.size()) return creep(pos, node, speed);
        int ni = pathIdx + 1;
        while (ni + 1 < path.size() && path.get(ni).distanceTo(node) < 0.05) ni++;
        Vec3d next = path.get(ni);
        if (pos.distanceTo(node) <= 0.08 || segDist(pos, node, next) <= 0.08) return creep(pos, next, speed);
        if (pathIdx > 0 && segDist(pos, path.get(pathIdx - 1), node) <= 0.08) return creep(pos, node, speed);
        return creep(pos, node, speed);
    }

    private static double segDist(Vec3d p, Vec3d a, Vec3d b) {
        Vec3d ab = b.subtract(a);
        double l2 = ab.lengthSquared();
        if (l2 < 1e-9) return p.distanceTo(a);
        double t = MathHelper.clamp(p.subtract(a).dotProduct(ab) / l2, 0, 1);
        return p.distanceTo(a.add(ab.multiply(t)));
    }

    private Vec3d creep(Vec3d pos, Vec3d target, double speed) {
        Vec3d d = target.subtract(pos);
        double len = d.length();
        if (len < 1e-4) return Vec3d.ZERO;
        Vec3d direct = len > speed ? d.multiply(speed / len) : d;
        if (clearLine(pos, pos.add(direct), -0.01)) return direct;

        double[] c = {d.x, d.y, d.z};
        Integer[] order = {0, 1, 2};
        java.util.Arrays.sort(order, (a, b) -> Double.compare(Math.abs(c[b]), Math.abs(c[a])));
        for (double scale : new double[] {1, 0.25}) {
            for (int a : order) {
                double st = MathHelper.clamp(c[a], -speed, speed) * scale;
                if (Math.abs(st) < 1e-4) continue;
                Vec3d mv = a == 0 ? new Vec3d(st, 0, 0) : a == 1 ? new Vec3d(0, st, 0) : new Vec3d(0, 0, st);
                if (clearLine(pos, pos.add(mv), -0.01)) return mv;
            }
        }
        Box here = new Box(pos.x - 0.3, pos.y, pos.z - 0.3, pos.x + 0.3, pos.y + 0.6, pos.z + 0.3);
        Vec3d best = Vec3d.ZERO;
        double bestD = Double.MAX_VALUE;
        for (Vec3d ax : AXES6) {
            Vec3d mv = ax.multiply(0.25);
            if (!clearBox(here.offset(mv).contract(0.01))) continue;
            double dd = pos.add(mv).distanceTo(target);
            if (dd >= len - 0.02) continue;
            if (lastCmd.lengthSquared() > 1e-4 && mv.dotProduct(lastCmd) < -0.9 * mv.length() * lastCmd.length()) continue;
            if (dd < bestD) {
                bestD = dd;
                best = mv;
            }
        }
        if (best != Vec3d.ZERO) creepEscapes++;
        return best;
    }

    private void startDigging(String msg) {
        mineModeTicks = Tuning.i("dig.mode.ticks", 600);
        retryIn = 0;
        replanNow = true;
        m.warning(msg);
        LOG.info("[VG] dig mode on at ({})", v(mc.player.getEntityPos()));
    }

    private void snapshot(String why) {
        if (mc.player == null || mc.world == null || mc.player.age - snapAge < Tuning.i("snapshot.interval", 40)) return;
        snapAge = mc.player.age;
        Vec3d pos = mc.player.getEntityPos();
        BlockPos f = BlockPos.ofFloored(pos.x, pos.y + 0.05, pos.z);
        StringBuilder sb = new StringBuilder();
        sb.append("\n[VG] snap ===== ").append(why).append(" =====");
        sb.append(String.format("\n[VG] snap pos(%.2f %.2f %.2f) feetBlock=%s vel(%s) gliding=%s onGround=%s hCollide=%s vCollide=%s phase=%s",
            pos.x, pos.y, pos.z, f.toShortString(), v(mc.player.getVelocity()), mc.player.isGliding(), mc.player.isOnGround(),
            mc.player.horizontalCollision, mc.player.verticalCollision, phase));
        sb.append(String.format("\n[VG] snap speed=%.2f vSpeed=%.2f cap=%.2f capV=%.2f vfH=%.2f vfStart=%.2f vfV=%.2f creep=%d stall=%d tight=%s noCarrot=%d setbackTicks=%d dig=%d mine=%s digOut=%s mending=%s",
            curSpeed, curV, setbackCap, setbackCapV, vf.horizontalSpeed.get(), vf.startSpeed.get(), vf.verticalSpeed.get(),
            creepTicks, stallTicks, carrotTight, noCarrotTicks, setbackTicks, mineModeTicks, mineTarget, digOutTarget, mending));
        sb.append(String.format("\n[VG] snap carrot=%s path=%d/%d reachesGoal=%s frontier=%s search=%s goal=%s",
            dbgCarrot == null ? "none" : v(dbgCarrot), pathIdx, path.size(), pathReachesGoal, pathEndsAtFrontier, search != null, goalNode));
        StringBuilder next = new StringBuilder();
        java.util.HashSet<Long> pathCells = new java.util.HashSet<>();
        for (int i = Math.max(0, pathIdx - 2); i < Math.min(path.size(), pathIdx + 12); i++) {
            BlockPos b = BlockPos.ofFloored(path.get(i));
            pathCells.add(b.asLong());
            next.append(i == pathIdx ? " [" : " ").append(b.getX() - f.getX()).append(',').append(b.getY() - f.getY()).append(',').append(b.getZ() - f.getZ()).append(i == pathIdx ? "]" : "");
        }
        sb.append("\n[VG] snap path near (relative to feet block, [current]):").append(next);
        sb.append("\n[VG] snap last ticks:");
        for (String h : history) sb.append("\n[VG] snap   ").append(h);
        sb.append("\n[VG] snap blocks (x -3..3 across, z -3..3 down) @=you o=path X=dig #=solid _=partial .=air ~=water L=lava P=portal F=fire W=web ?=unloaded");
        for (int dy = 3; dy >= -2; dy--) {
            sb.append("\n[VG] snap  y").append(dy >= 0 ? "+" : "").append(dy).append(" (").append(f.getY() + dy).append("):");
            for (int dz = -3; dz <= 3; dz++) {
                sb.append("\n[VG] snap     ");
                for (int dx = -3; dx <= 3; dx++) {
                    BlockPos b = f.add(dx, dy, dz);
                    sb.append(snapChar(b, dx == 0 && dz == 0 && (dy == 0 || dy == 1 && !mc.player.isGliding()), pathCells.contains(b.asLong())));
                }
            }
        }
        LOG.info(sb.toString());
    }

    private char snapChar(BlockPos b, boolean you, boolean onPath) {
        if (!mc.world.getChunkManager().isChunkLoaded(b.getX() >> 4, b.getZ() >> 4)) return '?';
        if (b.equals(mineTarget) || b.equals(digOutTarget)) return 'X';
        BlockState st = mc.world.getBlockState(b);
        if (st.getFluidState().isIn(FluidTags.LAVA)) return 'L';
        if (!st.getFluidState().isEmpty()) return '~';
        if (st.isOf(Blocks.NETHER_PORTAL)) return 'P';
        if (st.isOf(Blocks.FIRE) || st.isOf(Blocks.SOUL_FIRE)) return 'F';
        if (st.isOf(Blocks.COBWEB)) return 'W';
        net.minecraft.util.shape.VoxelShape shape = st.getCollisionShape(mc.world, b);
        if (!shape.isEmpty()) {
            if (you) return '!';
            Box bb = shape.getBoundingBox();
            boolean full = bb.minX <= 0 && bb.minY <= 0 && bb.minZ <= 0 && bb.maxX >= 1 && bb.maxY >= 1 && bb.maxZ >= 1;
            return full ? '#' : '_';
        }
        if (you) return '@';
        return onPath ? 'o' : '.';
    }

    private void logout(String why) {
        if (mc.player == null) return;
        AutoReconnect ar = Modules.get().get(AutoReconnect.class);
        if (ar != null && ar.isActive()) ar.toggle();
        LOG.info("[VG] logging out: {}", why);
        mc.player.networkHandler.onDisconnect(new DisconnectS2CPacket(Text.literal("[Volytra Goto] " + why)));
    }

    private void scanForPortals(Vec3d pos, boolean divert) {
        int pcx = MathHelper.floor(pos.x) >> 4, pcz = MathHelper.floor(pos.z) >> 4, r = Tuning.i("portal.scan.chunks", 6), budget = Tuning.i("portal.scan.budget", 6);
        for (int dx = -r; dx <= r && budget > 0; dx++) {
            for (int dz = -r; dz <= r && budget > 0; dz++) {
                int cx = pcx + dx, cz = pcz + dz;
                long key = ((long) cx << 32) ^ (cz & 0xffffffffL);
                if (scannedChunks.contains(key) || !mc.world.getChunkManager().isChunkLoaded(cx, cz)) continue;
                scannedChunks.add(key);
                budget--;
                BlockPos portal = findPortal(mc.world.getChunk(cx, cz));
                if (portal != null) considerPortal(portal, divert);
                if (divertedToPortal) return;
            }
        }
    }

    private BlockPos findPortal(WorldChunk chunk) {
        ChunkSection[] sections = chunk.getSectionArray();
        for (int i = 0; i < sections.length; i++) {
            ChunkSection sec = sections[i];
            if (sec == null || sec.isEmpty() || !sec.getBlockStateContainer().hasAny(st -> st.isOf(Blocks.NETHER_PORTAL))) continue;
            int baseY = mc.world.sectionIndexToCoord(i) << 4;
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        if (sec.getBlockState(x, y, z).isOf(Blocks.NETHER_PORTAL)) {
                            return new BlockPos(chunk.getPos().getStartX() + x, baseY + y, chunk.getPos().getStartZ() + z);
                        }
                    }
                }
            }
        }
        return null;
    }

    private void considerPortal(BlockPos portal, boolean divert) {
        BlockPos.Mutable b = portal.mutableCopy();
        while (mc.world.getBlockState(b.down()).isOf(Blocks.NETHER_PORTAL)) b.move(0, -1, 0);
        BlockPos bottom = b.toImmutable();
        for (BlockPos ig : ignoredPortals) if (ig.isWithinDistance(bottom, 12)) return;
        if (flightStart != null && Math.hypot(bottom.getX() + 0.5 - flightStart.x, bottom.getZ() + 0.5 - flightStart.z) < portalIgnoreRadius.get()) {
            ignoredPortals.add(bottom);
            LOG.info("[VG] portal at {} - within {} blocks of where the flight started, ignored", bottom.toShortString(), portalIgnoreRadius.get());
            return;
        }
        if (!divert) {
            ignoredPortals.add(bottom);
            LOG.info("[VG] portal seen at {} ({} blocks away) - not stopping: portal-stop is off", bottom.toShortString(), String.format("%.0f", Math.sqrt(bottom.getSquaredDistance(mc.player.getBlockPos()))));
            if (m.portalMessages.get()) m.info("Passed a Nether portal at %d %d %d (portal-stop is off).", bottom.getX(), bottom.getY(), bottom.getZ());
            return;
        }

        BlockPos spot = null;
        search:
        for (int r = 1; r <= 5; r++) {
            for (int dy : new int[] {0, 1, -1}) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                        int x = bottom.getX() + dx, y = bottom.getY() + dy, z = bottom.getZ() + dz;
                        if (!standable(x, y, z) || !safeFloor(new BlockPos(x, y - 1, z))) continue;
                        spot = new BlockPos(x, y, z);
                        break search;
                    }
                }
            }
        }
        if (spot == null) {
            ignoredPortals.add(bottom);
            LOG.info("[VG] portal at {} but nowhere safe to land next to it", bottom);
            return;
        }

        divertedToPortal = true;
        portalFound = bottom;
        fixedGoal = spot;
        targetX.set(spot.getX());
        targetZ.set(spot.getZ());
        arrive.set(Arrive.Land);
        resetRoute();
        m.warning("Found a Nether portal at %d %d %d - landing next to it (then logging out).", bottom.getX(), bottom.getY(), bottom.getZ());
    }

    private int groundBelow(Vec3d pos, int max) {
        int fy = MathHelper.floor(pos.y);
        BlockPos.Mutable b = new BlockPos.Mutable();
        for (int i = 1; i <= max; i++) {
            b.set(MathHelper.floor(pos.x), fy - i, MathHelper.floor(pos.z));
            BlockState st = mc.world.getBlockState(b);
            if (st.getFluidState().isIn(FluidTags.LAVA) || st.isOf(Blocks.FIRE) || st.isOf(Blocks.SOUL_FIRE)) return -1;
            if (!st.getFluidState().isEmpty() || !st.getCollisionShape(mc.world, b).isEmpty()) return i - 1;
        }
        return -1;
    }

    private void updateMend(Vec3d pos, boolean gliding) {
        if (mc.player.isUsingItem() || mc.options.useKey.isPressed()) return;
        ItemStack chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        FindItemResult xp = InvUtils.find(Items.EXPERIENCE_BOTTLE);
        boolean repaired = chest.isOf(Items.ELYTRA) && chest.isDamageable() && durability(chest) >= chest.getMaxDamage() * 0.95;
        boolean worn = chest.isOf(Items.ELYTRA) && chest.isDamageable() && durability(chest) <= minDurability.get();
        if (repaired || worn || !chest.isOf(Items.ELYTRA) || !xp.found() || !gliding || ++mendTicks > 1200) {
            mending = false;
            mendCooldown = repaired ? 100 : 600;
            InvUtils.swapBack();
            replanNow = true;
            if (chest.isOf(Items.ELYTRA) && chest.isDamageable()) {
                if (repaired) m.info("Elytra mended (%d durability).", durability(chest));
                else m.warning("Stopped mending at %d durability (%s).", durability(chest), !xp.found() ? "out of XP bottles" : mendTicks > 1200 ? "took too long" : "not flying");
            }
            return;
        }

        mendBelow = groundBelow(pos, 48);
        if (mendBelow < 0) {
            mending = false;
            mendCooldown = 200;
            InvUtils.swapBack();
            LOG.info("[VG] mend: no ground below ({}) - later", v(pos));
            return;
        }
        if (mendBelow > 4) return;

        if (throwCooldown > 0) {
            throwCooldown--;
            return;
        }
        if (!xp.isHotbar()) {
            InvUtils.move().from(xp.slot()).toHotbar(8);
            throwCooldown = 4;
            return;
        }
        InvUtils.swap(xp.slot(), true);
        throwCooldown = 2;
        Rotations.rotate(mc.player.getYaw(), 90, 60, () -> mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND));
    }

    private boolean walkTunnel() {
        if (path.size() < 2) return false;
        Vec3d pos = mc.player.getEntityPos();
        pathIdx = nearestIndex(pos, Math.max(0, pathIdx - 4), Math.min(path.size(), pathIdx + 8));
        if (pathIdx >= path.size() - 1) {
            if (tunneling) tunnelEndAge = mc.player.age;
            tunneling = false;
            replanNow = true;
            return false;
        }
        Vec3d next = path.get(pathIdx + 1);
        if (next.y > path.get(pathIdx).y + 0.5) return false;
        if (next.y < path.get(pathIdx).y - 0.5 && Math.hypot(next.x - path.get(pathIdx).x, next.z - path.get(pathIdx).z) < 0.5) {
            Vec3d node = path.get(pathIdx);
            double cdx = node.x - pos.x, cdz = node.z - pos.z, cl = Math.hypot(cdx, cdz);
            if (cl > 0.12) {
                double sp = Math.min(0.15, cl);
                groundNudge = new Vec3d(cdx / cl * sp, 0, cdz / cl * sp);
                return true;
            }
            BlockPos below = BlockPos.ofFloored(pos.x, pos.y - 0.5, pos.z);
            BlockState bs = mc.world.getBlockState(below);
            if (!FlightPathfinder.blocks(mc.world, below, bs)) return true;
            if (pendingBreak.containsKey(below.asLong()) && mc.player.age - pendingBreak.get(below.asLong()) < Tuning.i("dig.pending.ticks", 16)) return true;
            if (noMine.contains(below.asLong()) || !FlightPathfinder.minable(mc.world, below, bs)) {
                LOG.info("[VG] digging down: {} isn't safe - turning", below.toShortString());
                noMine.add(below.asLong());
                if (tunneling && startTunnel()) return true;
                tunneling = false;
                replanNow = true;
                return false;
            }
            dig(below);
            return true;
        }
        if (Math.hypot(next.x - pos.x, next.z - pos.z) < 0.15 && pathIdx + 2 < path.size()) next = path.get(pathIdx + 2);
        BlockPos here = BlockPos.ofFloored(pos.x, pos.y + 0.2, pos.z);
        BlockPos step = BlockPos.ofFloored(next.x, pos.y + 0.2, next.z);
        int confirm = Tuning.i("dig.confirm.ticks", 8);
        boolean crawling = mc.player.getBoundingBox().getLengthY() < 1.0;
        boolean waiting = false;
        boolean standUp = false;
        if (crawling && !instantOff && Tuning.b("tunnel.stand", false)) {
            standUp = true;
            for (BlockPos c : new BlockPos[] {here.up(), step.up()}) {
                BlockState cs = mc.world.getBlockState(c);
                if (FlightPathfinder.blocks(mc.world, c, cs) && (noMine.contains(c.asLong()) || !FlightPathfinder.minable(mc.world, c, cs))) standUp = false;
            }
        }
        boolean low = crawling && !standUp;
        for (BlockPos c : low ? new BlockPos[] {step} : new BlockPos[] {here.up(), step.up(), step}) {
            BlockState st = mc.world.getBlockState(c);
            if (!FlightPathfinder.blocks(mc.world, c, st)) {
                if (brokeAge.containsKey(c.asLong()) && mc.player.age - brokeAge.get(c.asLong()) < confirm) return true;
                continue;
            }
            brokeAge.remove(c.asLong());
            if (pendingBreak.containsKey(c.asLong()) && mc.player.age - pendingBreak.get(c.asLong()) < Tuning.i("dig.pending.ticks", 16)) {
                waiting = true;
                continue;
            }
            if (noMine.contains(c.asLong()) || !FlightPathfinder.minable(mc.world, c, st)) {
                LOG.info("[VG] tunnel on foot: {} isn't safe to dig - turning", c.toShortString());
                noMine.add(c.asLong());
                if (tunneling && startTunnel()) return true;
                tunneling = false;
                replanNow = true;
                return false;
            }
            dig(c);
            return true;
        }
        int aheadSteps = Tuning.i("dig.ahead.steps", 5);
        if (aheadSteps > 1 && !mc.player.isUsingItem()) {
            Vec3d eye = mc.player.getEyePos();
            ahead:
            for (int k = 2; k <= aheadSteps; k++) {
                if (pathIdx + k >= path.size()) break;
                Vec3d nk = path.get(pathIdx + k);
                if (Math.abs(nk.y - path.get(pathIdx).y) > 0.5) break;
                BlockPos sk = BlockPos.ofFloored(nk.x, pos.y + 0.2, nk.z);
                for (BlockPos c : low ? new BlockPos[] {sk} : new BlockPos[] {sk.up(), sk}) {
                    BlockState st = mc.world.getBlockState(c);
                    if (!FlightPathfinder.blocks(mc.world, c, st)) continue;
                    if (pendingBreak.containsKey(c.asLong()) && mc.player.age - pendingBreak.get(c.asLong()) < Tuning.i("dig.pending.ticks", 16)) continue;
                    if (eye.squaredDistanceTo(Vec3d.ofCenter(c)) > 20.25 || noMine.contains(c.asLong()) || !FlightPathfinder.minable(mc.world, c, st)) break ahead;
                    dig(c);
                    break ahead;
                }
            }
        }
        if (waiting) return true;
        double dx = next.x - pos.x, dz = next.z - pos.z, l = Math.hypot(dx, dz);
        if (l > 0.05) {
            double sp = Math.min(crawling && !Tuning.b("walk.use.speed", true) ? Tuning.d("tunnel.crawl.speed", 0.1) : walkSpeed(), l);
            groundNudge = new Vec3d(dx / l * sp, 0, dz / l * sp);
            mc.player.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
        }
        if (mc.player.age % 40 == 0) LOG.info("[VG] tunnel on foot: at node {}/{} pos=({})", pathIdx, path.size(), v(pos));
        return true;
    }

    private boolean startTunnel() {
        BlockPos start = startBlock();
        double tdx = targetX.get() + 0.5 - mc.player.getX(), tdz = targetZ.get() + 0.5 - mc.player.getZ();
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}, {0, 1, 0}};
        Integer[] order = {0, 1, 2, 3, 4, 5};
        java.util.Arrays.sort(order, (a, b) -> Double.compare(dirs[b][0] * tdx + dirs[b][2] * tdz - Math.abs(dirs[b][1]) * 1e6,
            dirs[a][0] * tdx + dirs[a][2] * tdz - Math.abs(dirs[a][1]) * 1e6));
        int maxLen = Tuning.i("tunnel.max", 40);
        List<Vec3d> best = null;
        int bestDir = -1;
        for (int oi : order) {
            int[] d = dirs[oi];
            ArrayList<Vec3d> pts = new ArrayList<>();
            pts.add(new Vec3d(start.getX() + 0.5, start.getY() + NODE_Y, start.getZ() + 0.5));
            int rock = 0, airRun = 0;
            boolean ok = false;
            for (int i = 1; i <= maxLen; i++) {
                BlockPos c = start.add(d[0] * i, d[1] * i, d[2] * i);
                if (c.getY() <= mc.world.getBottomY() + 2 || c.getY() >= mc.world.getTopYInclusive() - 2) break;
                if (!mc.world.getChunkManager().isChunkLoaded(c.getX() >> 4, c.getZ() >> 4)) {
                    ok = rock > 0;
                    break;
                }
                BlockState st = mc.world.getBlockState(c);
                if (!st.getFluidState().isEmpty()) break;
                if (d[1] == 0) {
                    BlockPos head = c.up();
                    BlockState hs = mc.world.getBlockState(head);
                    if (!hs.getFluidState().isEmpty()) break;
                    if (FlightPathfinder.blocks(mc.world, head, hs) && (noMine.contains(head.asLong()) || !FlightPathfinder.minable(mc.world, head, hs))) break;
                }
                if (FlightPathfinder.blocks(mc.world, c, st)) {
                    if (noMine.contains(c.asLong()) || !FlightPathfinder.minable(mc.world, c, st)) break;
                    rock++;
                    airRun = 0;
                }
                else if (FlightPathfinder.lavaAbove(mc.world, c.getX(), c.getY(), c.getZ(), 24)) break;
                else airRun++;
                pts.add(new Vec3d(c.getX() + 0.5, c.getY() + NODE_Y, c.getZ() + 0.5));
                if (rock > 0 && airRun >= 3) {
                    ok = true;
                    break;
                }
                if (i == maxLen) ok = rock > 0;
            }
            if (ok && pts.size() > 3) {
                best = pts;
                bestDir = oi;
                break;
            }
        }
        if (best == null) {
            LOG.info("[VG] trapped at ({}) but no direction is safe to tunnel", v(mc.player.getEntityPos()));
            return false;
        }
        path = best;
        for (Vec3d node : best) plannedMine.add(BlockPos.ofFloored(node).asLong());
        pathIdx = 0;
        pathReachesGoal = false;
        pathEndsAtFrontier = false;
        noCarrotTicks = 0;
        tunneling = true;
        mineModeTicks = Math.max(mineModeTicks, 1200);
        search = null;
        ticksSinceSearch = 0;
        int[] d = dirs[bestDir];
        m.warning("No way out of here - tunnelling %d blocks %s.", best.size() - 1,
            d[1] > 0 ? "up" : d[1] < 0 ? "down" : d[0] > 0 ? "east" : d[0] < 0 ? "west" : d[2] > 0 ? "south" : "north");
        LOG.info("[VG] TUNNEL from {} dir=({},{},{}) len={}", start.toShortString(), d[0], d[1], d[2], best.size() - 1);
        return true;
    }

    private BlockPos pmTarget;
    private int pmStart;
    private final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap pendingBreak = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();

    private void mineTick(BlockPos t) {
        if (mc.player.isUsingItem() || mc.options.useKey.isPressed()) return;
        BlockState st = mc.world.getBlockState(t);
        FindItemResult tool = InvUtils.findFastestTool(st);
        if (tool.found() && tool.isHotbar()) InvUtils.swap(tool.slot(), false);
        float delta = st.calcBlockBreakingDelta(mc.player, mc.world, t);
        if (mc.player.isOnGround() && Tuning.b("dig.ground.truth", true)) groundTruthForDigging();
        boolean truth = mc.player.isOnGround() && !groundSpoofed() && Tuning.b("dig.ground.truth", true);
        if (delta < Tuning.d("dig.packet.above", 0.5)) {
            pmTarget = null;
            lookDo(t, () -> BlockUtils.breakBlock(t, true));
            return;
        }
        if (instantOff && mc.player.age - instantOffAge > 1200) instantOff = false;
        boolean instant = truth && delta >= 1.0f && !instantOff && Tuning.b("dig.instant", true);
        int need = instant ? 0 : Math.max(Tuning.i("dig.min.ticks", 4), (int) Math.ceil(1.0 / Math.max(delta, 0.01)) + 1);
        int retry = instant ? Tuning.i("dig.instant.retry", 8) : need + 20;
        BlockPos target = t.toImmutable();
        if (!target.equals(pmTarget) || mc.player.age - pmStart > retry) {
            if (instant && !target.equals(pmTarget)) instantFails = 0;
            if (instant && target.equals(pmTarget) && ++instantFails >= Tuning.i("dig.instant.fails", 4)) {
                instantOff = true;
                instantOffAge = mc.player.age;
                instantFails = 0;
                LOG.info("[VG] the server isn't taking one-hit breaks ({} sent) - timed breaks for a minute", instantBreaks);
            }
            if (instant) {
                instantBreaks++;
                pendingBreak.put(target.asLong(), mc.player.age);
                if (instantBreaks == 1 || instantBreaks % 200 == 0) LOG.info("[VG] one-hit breaks: {} sent, {} retried", instantBreaks, instantFails);
            }
            pmTarget = target;
            pmStart = mc.player.age;
            lookDo(target, () -> {
                mc.getNetworkHandler().sendPacket(new net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket(
                    net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, target, net.minecraft.util.math.Direction.UP));
                mc.player.swingHand(Hand.MAIN_HAND);
            });
        }
        else if (!instant && mc.player.age - pmStart == need) {
            lookDo(target, () -> {
                mc.getNetworkHandler().sendPacket(new net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket(
                    net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, target, net.minecraft.util.math.Direction.UP));
                mc.player.swingHand(Hand.MAIN_HAND);
            });
            pendingBreak.put(target.asLong(), mc.player.age);
        }
    }

    private void lookDo(BlockPos t, Runnable r) {
        if (mc.player.isGliding() && !Tuning.b("dig.rotate.gliding", false)) r.run();
        else Rotations.rotate(Rotations.getYaw(t), Rotations.getPitch(t), 50, r);
    }

    private BlockPos blockerTowards(Vec3d to) {
        Vec3d pos = mc.player.getEntityPos();
        Box box = mc.player.getBoundingBox().stretch(to.subtract(pos)).expand(0.05);
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos b : BlockPos.iterate(MathHelper.floor(box.minX), MathHelper.floor(box.minY), MathHelper.floor(box.minZ),
            MathHelper.floor(box.maxX), MathHelper.floor(box.maxY), MathHelper.floor(box.maxZ))) {
            BlockState st = mc.world.getBlockState(b);
            if (!FlightPathfinder.blocks(mc.world, b, st) || !stuckDiggable(b) || noMine.contains(b.asLong())) continue;
            double d = Vec3d.ofCenter(b).squaredDistanceTo(pos);
            if (d < bestD) {
                bestD = d;
                best = b.toImmutable();
            }
        }
        return best;
    }

    private boolean dig(BlockPos target) {
        if (mc.player.age - lastBrokeAge < Tuning.i("dig.gap.ticks", 1)) return true;
        if (!target.equals(digging)) {
            digging = target.toImmutable();
            digTicks = 0;
            LOG.info("[VG] digging out {} ({}) at ({})", digging, mc.world.getBlockState(digging).getBlock(), v(mc.player.getEntityPos()));
        }
        if (++digTicks > 400) {
            m.warning("Couldn't break %d %d %d in 20 seconds - leaving it.", target.getX(), target.getY(), target.getZ());
            noMine.add(target.asLong());
            digging = null;
            return false;
        }
        mineTick(digging);
        return true;
    }

    private void updateDigOut(Vec3d pos, boolean gliding) {
        if (digOutTarget != null && (mineTarget != null || mending || phase != Phase.Flying)) {
            digOutTarget = null;
            stillTicks = 0;
            return;
        }
        if (digOutTarget != null) {
            BlockState st = mc.world.getBlockState(digOutTarget);
            if (!FlightPathfinder.blocks(mc.world, digOutTarget, st)) {
                digOutTarget = null;
                stillTicks = 20;
                replanNow = true;
            }
            else if (!dig(digOutTarget)) {
                digOutTarget = null;
                stillTicks = 20;
            }
            return;
        }

        boolean atFrontierEnd = pathEndsAtFrontier && !path.isEmpty() && pos.distanceTo(path.getLast()) < 4;
        boolean tryingToMove = dbgOurMove.length() > 0.05 || (!path.isEmpty() && noCarrotTicks > 3);
        if (!gliding || phase != Phase.Flying || mineTarget != null || mending || setbackTicks > 0 || path.isEmpty() || atFrontierEnd || !tryingToMove) {
            stillAnchor = pos;
            stillTicks = 0;
            return;
        }
        if (stillAnchor == null || pos.distanceTo(stillAnchor) > 1) {
            stillAnchor = pos;
            stillTicks = 0;
            return;
        }
        ++stillTicks;
        if (stillTicks == Tuning.i("still.creep.ticks", 20) && creepTicks == 0) {
            snapshot("not moving for 1 s while trying to");
            creepTicks = Tuning.i("creep.ticks", 60);
            replanNow = true;
        }
        if (stillTicks < Tuning.i("still.dig.ticks", 60) || !mineThrough.get()) return;
        stillTicks = 0;

        Vec3d toward = path.get(Math.min(path.size() - 1, pathIdx + 1));
        if (toward.distanceTo(pos) < 0.3) toward = path.get(Math.min(path.size() - 1, pathIdx + 2));
        Vec3d dir = toward.subtract(pos);
        if (dir.lengthSquared() > 1e-6) dir = dir.normalize().multiply(0.6);
        Box box = mc.player.getBoundingBox().expand(0.2).stretch(dir);
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos b : BlockPos.iterate(MathHelper.floor(box.minX), MathHelper.floor(box.minY), MathHelper.floor(box.minZ),
            MathHelper.floor(box.maxX), MathHelper.floor(box.maxY), MathHelper.floor(box.maxZ))) {
            BlockState st = mc.world.getBlockState(b);
            if (!FlightPathfinder.blocks(mc.world, b, st) || !stuckDiggable(b)) continue;
            double d = Vec3d.ofCenter(b).squaredDistanceTo(toward);
            if (d < bestD) {
                bestD = d;
                best = b.toImmutable();
            }
        }
        if (best == null) {
            LOG.info("[VG] really stuck at ({}) but nothing around is safe to dig", v(pos));
            snapshot("really stuck, nothing safe to dig");
            return;
        }
        snapshot("really stuck, digging out " + best.toShortString());
        digOutTarget = best;
        m.warning("Really stuck - digging out %d %d %d.", best.getX(), best.getY(), best.getZ());
    }

    private void updateMining(Vec3d pos, boolean gliding) {
        BlockPos prev = mineTarget;
        mineTarget = null;
        digHold = null;
        if (path.isEmpty() || phase != Phase.Flying || !gliding || mending) {
            mineTicks = 0;
            return;
        }
        int last = path.size() - 1;
        for (int k = Math.max(1, pathIdx); k <= Math.min(last, pathIdx + 2); k++) {
            BlockPos feet = BlockPos.ofFloored(path.get(k));
            BlockState st = mc.world.getBlockState(feet);
            if (lavaOkNow && st.getFluidState().isIn(FluidTags.LAVA) && st.getCollisionShape(mc.world, feet).isEmpty()) continue;
            if (!FlightPathfinder.blocks(mc.world, feet, st)) {
                if (brokeAge.containsKey(feet.asLong()) && mc.player.age - brokeAge.get(feet.asLong()) < Tuning.i("dig.confirm.ticks", 8)) {
                    digHold = path.get(k - 1);
                    break;
                }
                continue;
            }
            brokeAge.remove(feet.asLong());
            if (!tunneling && !plannedMine.contains(feet.asLong()) && Tuning.b("mine.planned.only", true)) {
                if (mc.player.age - unplannedLogAge > 40) {
                    unplannedLogAge = mc.player.age;
                    LOG.info("[VG] {} on the route is solid but was planned as open air - planning again", feet.toShortString());
                }
                replanNow = true;
                return;
            }
            if (!mineThrough.get() || noMine.contains(feet.asLong()) || !FlightPathfinder.minable(mc.world, feet, st)) {
                replanNow = true;
                tunneling = false;
                return;
            }
            mineTarget = feet.toImmutable();
            mineFrom = path.get(k - 1);
            break;
        }
        if (mineTarget == null) {
            mineTicks = 0;
            lineupTicks = 0;
            return;
        }
        if (Tuning.b("dig.on.foot", true) && !mc.player.isInLava() && mc.player.age - glideCloseAge > 40) {
            double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
            double drop = -net.minecraft.entity.Entity.adjustMovementForCollisions(mc.player, new Vec3d(0, -0.5, 0), mc.player.getBoundingBox(), mc.world, java.util.List.of()).y;
            boolean cantStand = !mc.world.isSpaceEmpty(mc.player, new Box(px - 0.3, py, pz - 0.3, px + 0.3, py + 1.8, pz + 0.3));
            if (drop < 0.49 && cantStand) {
                glideCloseAge = mc.player.age;
                if (srvGlide == 1) mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                mc.player.stopGliding();
                pendingStartAge = -1;
                LOG.info("[VG] in the hole at ({}) - elytra closed, digging on foot from here", v(pos));
                return;
            }
        }
        if (!mineTarget.equals(prev)) {
            mineTicks = 0;
            lineupTicks = 0;
            LOG.info("[VG] digging {} ({}) from ({})", mineTarget, mc.world.getBlockState(mineTarget).getBlock(), v(mineFrom));
        }
        if (pos.distanceTo(mineFrom) > 0.3) {
            if (++lineupTicks > Tuning.i("mine.lineup.ticks", 40)) {
                BlockPos blk = blockerTowards(mineFrom);
                if (lineupTicks == Tuning.i("mine.lineup.ticks", 40) + 1) {
                    LOG.info("[VG] can't line up at ({}) to dig {} from ({}) - clearing {}", v(pos), mineTarget.toShortString(), v(mineFrom), blk == null ? "nothing (no diggable block in between)" : blk.toShortString());
                    snapshot("can't line up to dig");
                }
                if (blk != null && lineupTicks < 400) {
                    if (!dig(blk)) noMine.add(blk.asLong());
                }
                else if (blk == null && lineupTicks < Tuning.i("mine.lineup.giveup", 120)) {  }
                else {
                    m.warning("Can't get to %d %d %d to dig - looking for another way.", mineTarget.getX(), mineTarget.getY(), mineTarget.getZ());
                    noMine.add(mineTarget.asLong());
                    mineTarget = null;
                    mineTicks = 0;
                    lineupTicks = 0;
                    replanNow = true;
                    tunneling = false;
                }
            }
            return;
        }
        lineupTicks = 0;

        if (++mineTicks > 400) {
            m.warning("Couldn't dig through %d %d %d in 20 seconds - looking for another way.", mineTarget.getX(), mineTarget.getY(), mineTarget.getZ());
            noMine.add(mineTarget.asLong());
            mineTarget = null;
            mineTicks = 0;
            replanNow = true;
            return;
        }
        BlockPos target = mineTarget;
        mineTick(target);
    }

    private static String v(Vec3d a) {
        return String.format("%.2f %.2f %.2f", a.x, a.y, a.z);
    }

    private Vec3d landingMove(Vec3d pos, double sV) {
        Vec3d spot = goalNode != null ? Vec3d.ofBottomCenter(goalNode) : (path.isEmpty() ? pos : path.getLast());
        double dx = MathHelper.clamp(spot.x - pos.x, -0.5, 0.5), dz = MathHelper.clamp(spot.z - pos.z, -0.5, 0.5);
        double ground = Math.max(0, pos.y - spot.y);
        double down = MathHelper.clamp(ground * 0.4, 0.1, sV);
        return new Vec3d(dx, -Math.min(down, ground + 0.05), dz);
    }

    private Vec3d pickCarrot(Vec3d pos) {
        if (path.isEmpty()) return null;
        pathIdx = nearestIndex(pos, Math.max(0, pathIdx - 16), Math.min(path.size(), pathIdx + 96));

        int last = path.size() - 1;
        int[] ahead = {80, 64, 48, 32, 24, 16, 12, 8, 6, 4, 3, 2, 1};

        double roomyPad = Math.max(hitboxPadding.get(), wallDistance.get());
        if (roomyPad > hitboxPadding.get() + 1e-3) {
            int t = -1;
            for (int a : ahead) {
                int j = Math.min(last, pathIdx + a);
                if (j == t) continue;
                t = j;
                if (a < 2) break;
                Vec3d p = path.get(j);
                if (p.distanceTo(pos) > 96) continue;
                if (j < last && p.distanceTo(pos) < 0.05) continue;
                if (clearLine(pos, p, roomyPad)) {
                    noCarrotTicks = 0;
                    carrotIdx = j;
                    carrotTight = false;
                    return p;
                }
            }
        }

        int tried = -1;
        for (int a : ahead) {
            int j = Math.min(last, pathIdx + a);
            if (j == tried) continue;
            tried = j;
            Vec3d p = path.get(j);
            if (p.distanceTo(pos) > 96) continue;
            if (j < last && p.distanceTo(pos) < 0.05) continue;
            boolean roomy = clearLine(pos, p, hitboxPadding.get());
            if (roomy || (a <= 4 && clearLine(pos, p, 0.05))) {
                noCarrotTicks = 0;
                carrotIdx = j;
                carrotTight = !roomy;
                return p;
            }
        }

        noCarrotTicks++;
        if (noCarrotTicks == 6) replanNow = true;
        return null;
    }

    private int nearestIndex(Vec3d pos, int from, int to) {
        int best = from;
        double bestD = Double.MAX_VALUE;
        for (int i = from; i < to; i++) {
            double d = path.get(i).squaredDistanceTo(pos);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        double lim = (Math.sqrt(bestD) + 0.3) * (Math.sqrt(bestD) + 0.3);
        for (int i = to - 1; i > best; i--) {
            if (path.get(i).squaredDistanceTo(pos) <= lim) {
                if (i - best > 3 && mc.player.age - loopLogAge > 100) {
                    loopLogAge = mc.player.age;
                    LOG.info("[VG] route passes here twice (nodes {} and {}) - skipping the loop", best, i);
                }
                return i;
            }
        }
        return best;
    }

    private boolean clearLine(Vec3d a, Vec3d b, double pad) {
        double below = Math.min(pad, Tuning.d("pad.below.max", 0.05));
        Box box = new Box(a.x - 0.3 - pad, a.y - below, a.z - 0.3 - pad, a.x + 0.3 + pad, a.y + 0.6 + pad, a.z + 0.3 + pad);
        Vec3d d = b.subtract(a);
        if (d.lengthSquared() < 1e-8) return true;
        int n = Math.max(1, (int) Math.ceil(d.length() / 0.3));
        double lm = lavaOkNow ? 0 : pad < 0 ? Tuning.d("lava.margin.creep", 0.0) : pad <= 0.051 ? Tuning.d("lava.margin.tight", 0.7) : Tuning.d("lava.margin", 1.2);
        Box lavaBox = new Box(a.x - 0.3 - lm, a.y - lm, a.z - 0.3 - lm, a.x + 0.3 + lm, a.y + 0.6 + lm, a.z + 0.3 + lm);
        for (int i = 1; i <= n; i++) {
            Vec3d off = d.multiply(i / (double) n);
            if (!clearBox(box.offset(off))) return false;
            if (lm > 0 && lavaIn(lavaBox.offset(off))) return false;
        }
        return true;
    }

    private boolean lavaTouches(Box b) {
        BlockPos.Mutable q = new BlockPos.Mutable();
        for (int x = MathHelper.floor(b.minX + 1e-3); x <= MathHelper.floor(b.maxX - 1e-3); x++) {
            for (int y = MathHelper.floor(b.minY + 1e-3); y <= MathHelper.floor(b.maxY - 1e-3); y++) {
                for (int z = MathHelper.floor(b.minZ + 1e-3); z <= MathHelper.floor(b.maxZ - 1e-3); z++) {
                    q.set(x, y, z);
                    if (mc.world.getBlockState(q).getFluidState().isIn(FluidTags.LAVA)) return true;
                }
            }
        }
        return false;
    }

    private boolean lavaIn(Box b) {
        BlockPos.Mutable q = new BlockPos.Mutable();
        for (int x = MathHelper.floor(b.minX); x <= MathHelper.ceil(b.maxX) - 1; x++) {
            for (int z = MathHelper.floor(b.minZ); z <= MathHelper.ceil(b.maxZ) - 1; z++) {
                for (int y = MathHelper.floor(b.minY); y <= MathHelper.ceil(b.maxY) - 1; y++) {
                    q.set(x, y, z);
                    if (mc.world.getBlockState(q).getFluidState().isIn(FluidTags.LAVA)) return true;
                }
                if (FlightPathfinder.lavaAbove(mc.world, x, MathHelper.ceil(b.maxY) - 1, z, Tuning.i("lava.fall.range", 24))) return true;
            }
        }
        return false;
    }

    private final BlockPos.Mutable cpos = new BlockPos.Mutable();

    private boolean clearBox(Box b) {
        int x1 = MathHelper.floor(b.minX), y1 = MathHelper.floor(b.minY), z1 = MathHelper.floor(b.minZ);
        int x2 = MathHelper.ceil(b.maxX) - 1, y2 = MathHelper.ceil(b.maxY) - 1, z2 = MathHelper.ceil(b.maxZ) - 1;
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                if (waitForChunks.get() && !mc.world.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) return false;
                for (int y = y1; y <= y2; y++) {
                    cpos.set(x, y, z);
                    BlockState cs = mc.world.getBlockState(cpos);
                    if (lavaOkNow && cs.getFluidState().isIn(FluidTags.LAVA) && cs.getCollisionShape(mc.world, cpos).isEmpty()) continue;
                    if (FlightPathfinder.blocks(mc.world, cpos, cs)) return false;
                }
            }
        }
        return true;
    }

    @Override
    public void render(Render3DEvent event) {
        if (!renderPath.get() || path.isEmpty()) return;
        int end = Math.min(path.size() - 1, pathIdx + 600);
        for (int i = Math.max(0, pathIdx); i < end; i++) {
            Vec3d a = path.get(i), b = path.get(i + 1);
            event.renderer.line(a.x, a.y + 0.2, a.z, b.x, b.y + 0.2, b.z, pathColor.get());
        }
        if (goalNode != null) event.renderer.box(goalNode, goalColor.get(), goalColor.get(), ShapeMode.Lines, 0);
        if (mineTarget != null) event.renderer.box(mineTarget, pathColor.get(), pathColor.get(), ShapeMode.Lines, 0);
        if (digOutTarget != null) event.renderer.box(digOutTarget, pathColor.get(), pathColor.get(), ShapeMode.Lines, 0);
        if (portalFound != null) event.renderer.box(portalFound, goalColor.get(), goalColor.get(), ShapeMode.Lines, 0);
    }

    @Override
    public boolean holdingKey(KeyBinding key) {
        return holdingForward && key == mc.options.forwardKey;
    }

}
