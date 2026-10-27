package gamerguy11.sixtoolsaddon.basehunting;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import gamerguy11.sixtoolsaddon.modules.Efly;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BlockPosSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;

public class BaseHunter extends Module {
    public enum Mode { Rectangle, Spiral, Trail }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgArea = settings.createGroup("Area");
    private final SettingGroup sgTrail = settings.createGroup("Trail");
    private final SettingGroup sgEfly = settings.createGroup("Efly");
    private final SettingGroup sgStop = settings.createGroup("Stop");

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Rectangle flies strips over an area, Spiral flies outwards from where you start, Trail follows newly generated chunks.")
        .defaultValue(Mode.Rectangle)
        .build()
    );

    private final Setting<Double> turnSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("turn-speed")
        .description("Most degrees the yaw changes per tick. Lower is smoother and safer for Efly.")
        .defaultValue(4.0)
        .range(0.5, 30.0)
        .sliderRange(0.5, 15.0)
        .build()
    );

    private final Setting<Boolean> holdAltitude = sgGeneral.add(new BoolSetting.Builder()
        .name("hold-altitude")
        .description("Tilts your pitch to stay near the altitude below.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> altitude = sgGeneral.add(new IntSetting.Builder()
        .name("altitude")
        .description("Height to fly at. The Nether roof is at 128, so use something like 120 there.")
        .defaultValue(200)
        .range(-64, 320)
        .sliderRange(0, 320)
        .visible(holdAltitude::get)
        .build()
    );

    private final Setting<Double> maxPitch = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-pitch")
        .description("Most the pitch is tilted up or down while holding altitude.")
        .defaultValue(25.0)
        .range(5.0, 60.0)
        .sliderRange(5.0, 45.0)
        .visible(holdAltitude::get)
        .build()
    );

    private final Setting<BlockPos> startPos = sgArea.add(new BlockPosSetting.Builder()
        .name("start-position")
        .description("One corner of the area. Y is ignored.")
        .defaultValue(new BlockPos(0, 0, 0))
        .visible(() -> mode.get() == Mode.Rectangle)
        .build()
    );

    private final Setting<BlockPos> endPos = sgArea.add(new BlockPosSetting.Builder()
        .name("end-position")
        .description("The opposite corner of the area. Y is ignored.")
        .defaultValue(new BlockPos(0, 0, 0))
        .visible(() -> mode.get() == Mode.Rectangle)
        .build()
    );

    private final Setting<Integer> stripWidth = sgArea.add(new IntSetting.Builder()
        .name("strip-width")
        .description("Blocks between the strips of the rectangle and between the rings of the spiral. Stay below twice your render distance in blocks.")
        .defaultValue(192)
        .range(32, 1000)
        .sliderRange(64, 500)
        .visible(() -> mode.get() != Mode.Trail)
        .build()
    );

    private final Setting<Integer> arriveDistance = sgArea.add(new IntSetting.Builder()
        .name("arrive-distance")
        .description("How close to a waypoint counts as reaching it.")
        .defaultValue(48)
        .range(8, 200)
        .sliderRange(16, 100)
        .visible(() -> mode.get() != Mode.Trail)
        .build()
    );

    private final Setting<Integer> trailRadius = sgTrail.add(new IntSetting.Builder()
        .name("trail-radius")
        .description("Only new chunks within this many chunks of you are used to find the trail.")
        .defaultValue(24)
        .range(4, 64)
        .sliderRange(8, 48)
        .visible(() -> mode.get() == Mode.Trail)
        .build()
    );

    private final Setting<Integer> trailAngle = sgTrail.add(new IntSetting.Builder()
        .name("trail-angle")
        .description("Only chunks within this many degrees of your heading count, so the trail behind you is ignored.")
        .defaultValue(90)
        .range(20, 180)
        .sliderRange(30, 150)
        .visible(() -> mode.get() == Mode.Trail)
        .build()
    );

    private final Setting<Integer> chunksBeforeStarting = sgTrail.add(new IntSetting.Builder()
        .name("chunks-before-starting")
        .description("Amount of new chunks ahead needed before the trail is followed. Useful for waiting for a trail.")
        .defaultValue(8)
        .range(1, 100)
        .sliderRange(1, 50)
        .visible(() -> mode.get() == Mode.Trail)
        .build()
    );

    private final Setting<Integer> lostTrailTimeout = sgTrail.add(new IntSetting.Builder()
        .name("lost-trail-timeout")
        .description("Seconds without a trail before the hunter stops. 0 keeps flying straight forever.")
        .defaultValue(60)
        .range(0, 600)
        .sliderRange(0, 300)
        .visible(() -> mode.get() == Mode.Trail)
        .build()
    );

    private final Setting<Boolean> enableEfly = sgEfly.add(new BoolSetting.Builder()
        .name("enable-efly")
        .description("Turns Efly on with this module.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> setupAutopilot = sgEfly.add(new BoolSetting.Builder()
        .name("efly-autopilot")
        .description("Turns on Efly's auto pilot and fireworks while hunting and puts them back afterwards, so Efly keeps you moving forward.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> maxSpeed = sgEfly.add(new DoubleSetting.Builder()
        .name("max-speed")
        .description("Top speed in blocks per second while hunting, applied to Efly and put back afterwards. 0 uses Efly's own speed settings.")
        .defaultValue(0.0)
        .min(0.0)
        .sliderMax(300.0)
        .build()
    );

    private final Setting<Double> speedRamp = sgEfly.add(new DoubleSetting.Builder()
        .name("speed-ramp")
        .description("How fast the speed climbs to max-speed, in blocks per second gained every second. Jumping straight to a high speed makes servers rubberband you.")
        .defaultValue(60.0)
        .min(5.0)
        .sliderMax(300.0)
        .visible(() -> maxSpeed.get() > 0)
        .build()
    );

    private final Setting<Boolean> autoTakeoff = sgEfly.add(new BoolSetting.Builder()
        .name("auto-takeoff")
        .description("Jumps and opens your elytra if you are not flying yet. Efly only works once you are gliding, so without this nothing moves.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> enableNewChunks = sgEfly.add(new BoolSetting.Builder()
        .name("enable-new-chunks")
        .description("Turns on the Hunt New Chunks module, needed for Trail mode.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> enableStashFinder = sgEfly.add(new BoolSetting.Builder()
        .name("enable-stash-finder")
        .description("Turns on the Hunt Stash Finder.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> stopOnFind = sgStop.add(new BoolSetting.Builder()
        .name("stop-on-find")
        .description("Stops hunting when the stash finder reports a stash.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> waypointOnFind = sgStop.add(new BoolSetting.Builder()
        .name("waypoint-on-find")
        .description("Puts a waypoint on the map (Xaero if installed, otherwise Meteor) when a stash is found. Works in every mode, including Spiral.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> disconnectOnFinish = sgStop.add(new BoolSetting.Builder()
        .name("disconnect-on-finish")
        .description("Disconnects once the rectangle is finished.")
        .defaultValue(false)
        .visible(() -> mode.get() == Mode.Rectangle)
        .build()
    );

    private static final double START_CAP = 0.5;

    private final List<double[]> waypoints = new ArrayList<>();
    private int waypointIndex;
    private double spiralX, spiralZ;
    private int spiralLeg;
    private double[] spiralTarget;
    private int noTrailTicks;
    private Boolean oldAutopilot, oldFireworks;
    private Double oldMinHeight;
    private boolean speedCapped;
    private boolean verticalKeys;
    private double currentCap;
    private double lastX, lastZ;
    private double flightSpeed;
    private int takeoffCooldown;
    private boolean enabledEfly;

    public BaseHunter() {
        super(SixToolsAddon.BASE_HUNTING, "base-hunter", "Flies over an area or follows a chunk trail looking for bases and stashes, using your Efly for the flight itself.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) {
            toggle();
            return;
        }

        Efly efly = Modules.get().get(Efly.class);
        if (efly == null) {
            error("Efly is missing.");
            toggle();
            return;
        }

        if (mode.get() == Mode.Trail && enableNewChunks.get()) {
            BaseHuntNewChunks chunks = Modules.get().get(BaseHuntNewChunks.class);
            if (chunks != null && !chunks.isActive()) chunks.toggle();
        }

        if (enableStashFinder.get()) {
            BaseHuntStashFinder finder = Modules.get().get(BaseHuntStashFinder.class);
            if (finder != null && !finder.isActive()) finder.toggle();
        }

        if (enableEfly.get() && !efly.isActive()) {
            efly.toggle();
            enabledEfly = true;
        }

        if (setupAutopilot.get()) {
            oldAutopilot = efly.autoPilot.get();
            oldFireworks = efly.useFireworks.get();
            efly.autoPilot.set(true);
            efly.useFireworks.set(true);

            oldMinHeight = efly.autoPilotMinimumHeight.get();
            efly.autoPilotMinimumHeight.set(-128.0);
        }

        speedCapped = maxSpeed.get() > 0;
        currentCap = Math.min(maxSpeed.get() / 20.0, START_CAP);
        if (speedCapped) efly.setSpeedOverride(currentCap);
        lastX = mc.player.getX();
        lastZ = mc.player.getZ();
        flightSpeed = 0;

        if (!mc.player.getEquippedStack(EquipmentSlot.CHEST).contains(DataComponentTypes.GLIDER)) {
            warning("No elytra equipped, put one on (or set Efly's chest-swap).");
        }
        takeoffCooldown = 0;

        BaseHuntStashFinder.pollFind();
        noTrailTicks = 0;
        waypoints.clear();
        waypointIndex = 0;
        spiralTarget = null;
        spiralLeg = 0;
        spiralX = mc.player.getX();
        spiralZ = mc.player.getZ();

        if (mode.get() == Mode.Rectangle) {
            buildRectangle();
            if (waypoints.isEmpty()) {
                error("Set a start and end position first.");
                toggle();
            } else {
                info("Flying %d strips.", waypoints.size() / 2);
            }
        }
    }

    @Override
    public void onDeactivate() {
        releaseVerticalKeys();
        Efly efly = Modules.get().get(Efly.class);
        if (efly != null) {
            if (oldAutopilot != null) efly.autoPilot.set(oldAutopilot);
            if (oldFireworks != null) efly.useFireworks.set(oldFireworks);
            if (speedCapped) efly.clearSpeedOverride();
            if (oldMinHeight != null) efly.autoPilotMinimumHeight.set(oldMinHeight);
            if (enabledEfly && efly.isActive()) efly.toggle();
        }
        oldAutopilot = null;
        oldFireworks = null;
        oldMinHeight = null;
        speedCapped = false;
        enabledEfly = false;
    }

    private void buildRectangle() {
        BlockPos a = startPos.get();
        BlockPos b = endPos.get();
        double x1 = Math.min(a.getX(), b.getX());
        double x2 = Math.max(a.getX(), b.getX());
        double z1 = Math.min(a.getZ(), b.getZ());
        double z2 = Math.max(a.getZ(), b.getZ());
        if (x1 == x2 && z1 == z2) return;

        boolean forward = true;
        for (double x = x1; x <= x2 + stripWidth.get() / 2.0; x += stripWidth.get()) {
            double cx = Math.min(x, x2);
            if (forward) {
                waypoints.add(new double[]{cx, z1});
                waypoints.add(new double[]{cx, z2});
            } else {
                waypoints.add(new double[]{cx, z2});
                waypoints.add(new double[]{cx, z1});
            }
            forward = !forward;
        }
    }

    private double[] nextSpiralTarget() {
        int[][] dirs = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
        int[] dir = dirs[spiralLeg % 4];
        double length = (spiralLeg / 2 + 1) * (double) stripWidth.get();
        spiralX += dir[0] * length;
        spiralZ += dir[1] * length;
        spiralLeg++;
        return new double[]{spiralX, spiralZ};
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        BlockPos found = BaseHuntStashFinder.pollFind();
        if (found != null && waypointOnFind.get()) {
            HuntWaypoints.add("Stash " + found.getX() + " " + found.getZ(), found);
        }
        if (found != null && stopOnFind.get()) {
            info("Stopped, stash at %d %d %d.", found.getX(), found.getY(), found.getZ());
            toggle();
            return;
        }

        double px = mc.player.getX();
        double pz = mc.player.getZ();
        flightSpeed = Math.hypot(px - lastX, pz - lastZ);
        lastX = px;
        lastZ = pz;

        if (!mc.player.isGliding()) {
            releaseVerticalKeys();
            if (speedCapped) {
                currentCap = Math.min(maxSpeed.get() / 20.0, START_CAP);
                Efly flying = Modules.get().get(Efly.class);
                if (flying != null) flying.setSpeedOverride(currentCap);
            }
            if (autoTakeoff.get()) tryTakeoff();
            return;
        }

        if (speedCapped) {
            Efly flying = Modules.get().get(Efly.class);
            if (flying != null) {
                currentCap = Math.min(maxSpeed.get() / 20.0, currentCap + speedRamp.get() / 400.0);
                flying.setSpeedOverride(currentCap);
            }
        }
        double[] target = null;

        switch (mode.get()) {
            case Rectangle -> {
                if (waypointIndex >= waypoints.size()) {
                    info("Finished the area.");
                    if (disconnectOnFinish.get() && mc.player.networkHandler != null) {
                        mc.player.networkHandler.getConnection().disconnect(Text.literal("[Base Hunter] Finished the area."));
                    }
                    toggle();
                    return;
                }
                target = waypoints.get(waypointIndex);
                if (reached(target, px, pz)) {
                    waypointIndex++;
                    return;
                }
            }
            case Spiral -> {
                if (spiralTarget == null) spiralTarget = nextSpiralTarget();
                if (reached(spiralTarget, px, pz)) {
                    spiralTarget = nextSpiralTarget();
                }
                target = spiralTarget;
            }
            case Trail -> target = trailTarget(px, pz);
        }

        if (target != null) {
            double dx = target[0] - px;
            double dz = target[1] - pz;
            float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
            float current = mc.player.getYaw();
            float delta = MathHelper.wrapDegrees(targetYaw - current);
            float limit = turnSpeed.get().floatValue();
            mc.player.setYaw(current + MathHelper.clamp(delta, -limit, limit));
        }

        if (holdAltitude.get()) {
            double climb = altitude.get() - mc.player.getY();
            mc.options.jumpKey.setPressed(climb > 1.5);
            mc.options.sneakKey.setPressed(climb < -4.0);
            verticalKeys = true;
            double error = -climb;
            float wanted = (float) MathHelper.clamp(error * 0.4, -maxPitch.get(), maxPitch.get());
            float pitch = mc.player.getPitch();
            mc.player.setPitch(pitch + MathHelper.clamp(wanted - pitch, -3.0f, 3.0f));
        }
    }

    private void releaseVerticalKeys() {
        if (!verticalKeys) return;
        mc.options.jumpKey.setPressed(false);
        mc.options.sneakKey.setPressed(false);
        verticalKeys = false;
    }

    private boolean reached(double[] target, double px, double pz) {
        double distance = Math.hypot(target[0] - px, target[1] - pz);
        double turnRate = Math.toRadians(turnSpeed.get());
        double turnRadius = turnRate > 0 ? flightSpeed / turnRate : 0;
        return distance <= Math.max(arriveDistance.get(), turnRadius * 1.15);
    }

    private void tryTakeoff() {
        if (takeoffCooldown > 0) {
            takeoffCooldown--;
            return;
        }
        if (!mc.player.getEquippedStack(EquipmentSlot.CHEST).contains(DataComponentTypes.GLIDER)) return;
        if (mc.player.isTouchingWater() || mc.player.hasVehicle()) return;

        if (mc.player.isOnGround()) {
            mc.player.jump();
            takeoffCooldown = 6;
        } else if (mc.player.getVelocity().y < 0 && mc.player.networkHandler != null) {
            mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            takeoffCooldown = 10;
        }
    }

    private double[] trailTarget(double px, double pz) {
        BaseHuntNewChunks chunks = Modules.get().get(BaseHuntNewChunks.class);
        if (chunks == null || !chunks.isActive()) {
            if (noTrailTicks++ % 200 == 0) warning("Turn on the new chunks module to follow a trail.");
            return null;
        }

        double heading = Math.toRadians(mc.player.getYaw() + 90.0);
        double hx = Math.cos(heading);
        double hz = Math.sin(heading);
        double minCos = Math.cos(Math.toRadians(trailAngle.get()));
        int playerChunkX = (int) Math.floor(px) >> 4;
        int playerChunkZ = (int) Math.floor(pz) >> 4;
        int radius = trailRadius.get();

        double sumX = 0;
        double sumZ = 0;
        int count = 0;
        for (ChunkPos cp : chunks.snapshotNewChunks()) {
            int ox = cp.x - playerChunkX;
            int oz = cp.z - playerChunkZ;
            double dist = Math.hypot(ox, oz);
            if (dist < 3 || dist > radius) continue;
            if ((ox * hx + oz * hz) / dist < minCos) continue;
            sumX += cp.x * 16 + 8;
            sumZ += cp.z * 16 + 8;
            count++;
        }

        if (count < chunksBeforeStarting.get()) {
            noTrailTicks++;
            int timeout = lostTrailTimeout.get() * 20;
            if (timeout > 0 && noTrailTicks > timeout) {
                info("Lost the trail.");
                toggle();
            }
            return null;
        }

        noTrailTicks = 0;
        return new double[]{sumX / count, sumZ / count};
    }

    @Override
    public String getInfoString() {
        return mode.get().name();
    }
}
