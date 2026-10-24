package gamerguy11.sixtoolsaddon.modules.autowasp;

import java.util.Objects;
import net.minecraft.util.math.Vec3d;

public final class AutoWaspLogic {
    private AutoWaspLogic() {}

    public static final class AutoWaspChestSwap {

        public record Candidate(int slot, boolean glider, int durability, double armor, double toughness) {}

        private AutoWaspChestSwap() {}

        public static int best(Iterable<Candidate> inventory, boolean glider) {
            Candidate best = null;
            for (Candidate candidate : inventory) {
                if (candidate.glider() != glider || candidate.durability() <= (glider ? 1 : 0)) continue;
                if (best == null || better(candidate, best, glider)) best = candidate;
            }
            return best == null ? -1 : best.slot();
        }

        public static boolean wearChestplate(boolean wearingChestplate, double horizontal, double vertical, double ground,
                                             double horizontalRange, double verticalRange, double groundRange,
                                             double hysteresis) {
            double extra = wearingChestplate ? Math.max(0, hysteresis) : 0;
            return horizontal <= horizontalRange + extra && vertical <= verticalRange + extra
                && ground <= groundRange + extra;
        }

        private static boolean better(Candidate candidate, Candidate current, boolean glider) {
            if (!glider) {
                int armor = Double.compare(candidate.armor(), current.armor());
                if (armor != 0) return armor > 0;
                int toughness = Double.compare(candidate.toughness(), current.toughness());
                if (toughness != 0) return toughness > 0;
            }
            return candidate.durability() > current.durability();
        }
    }

    public static final class AutoWaspRamp {
        public record Settings(double start, double plateau, double step, int rewindTicks, int holdTicks, double limit) {
            public Settings {
                if (!Double.isFinite(start) || start < 0 || !Double.isFinite(plateau) || plateau < 0) {
                    throw new IllegalArgumentException("Ramp speeds must be finite and nonnegative.");
                }
                if (!Double.isFinite(limit) || limit < 0) {
                    throw new IllegalArgumentException("Ramp limit must be finite and nonnegative.");
                }
                if (!Double.isFinite(step) || step <= 0) {
                    throw new IllegalArgumentException("Ramp step must be finite and positive.");
                }
                if (rewindTicks < 0) throw new IllegalArgumentException("Rewind ticks cannot be negative.");
                if (holdTicks < 0) throw new IllegalArgumentException("Hold ticks cannot be negative.");
            }
        }

        private Settings settings;
        private boolean engaged;
        private double speed;
        private int hold;

        public AutoWaspRamp(Settings settings) {
            configure(settings);
        }

        public void configure(Settings settings) {
            this.settings = Objects.requireNonNull(settings, "settings");
            if (engaged) speed = Math.min(speed, ceiling());
        }

        public void engage(double currentSpeed) {
            engaged = true;
            hold = 0;
            double current = Double.isFinite(currentSpeed) ? Math.max(0, currentSpeed) : 0;
            speed = Math.min(Math.max(floor(), current), ceiling());
        }

        public void advance() {
            if (!engaged) return;
            if (hold > 0) {
                hold--;
                return;
            }
            speed = next(speed, settings.step(), settings.plateau(), ceiling());
        }

        public void limitTo(double actual) {
            if (!engaged || !(actual >= 0)) return;
            speed = Math.max(floor(), Math.min(speed, actual));
        }

        public void disengage() { engaged = false; }

        public void corrected() {
            if (!engaged) return;
            speed = rewind(speed, settings.step(), settings.plateau(), floor(), settings.rewindTicks());
            hold = settings.holdTicks();
        }

        public void reset() {
            engaged = false;
            speed = 0;
            hold = 0;
        }

        public boolean engaged() { return engaged; }

        public double speed() { return engaged ? speed : 0; }

        public double ceiling() { return Math.min(settings.plateau(), settings.limit()); }

        private double floor() { return Math.min(settings.start(), ceiling()); }

        static double gain(double speed, double step, double plateau) {
            if (!(plateau > 0)) return 0;
            return step * Math.max(0, plateau - speed) / plateau;
        }

        static double next(double speed, double step, double plateau, double ceiling) {
            return Math.min(speed + gain(speed, step, plateau), ceiling);
        }

        static double rewind(double speed, double step, double plateau, double floor, int ticks) {
            for (int tick = 0; tick < ticks && speed > floor; tick++) {
                speed = Math.max(floor, speed - gain(speed, step, plateau));
            }
            return speed;
        }
    }

    public static final class AutoWaspSetbacks {
        public enum Kind { SETBACK, PINNED, TELEPORT }

        public static final double TELEPORT_DISTANCE = 64;

        public static final int PINNED_WINDOW_TICKS = 40;
        private static final double SAME_SPOT_SQR = .01;

        private Vec3d lastTarget;
        private long lastTick;
        private int pinnedTicks;

        public Kind classify(long tick, Vec3d target, Vec3d from) {
            boolean teleport = from == null || target.squaredDistanceTo(from) > TELEPORT_DISTANCE * TELEPORT_DISTANCE;
            boolean pinned = !teleport && lastTarget != null && tick - lastTick <= PINNED_WINDOW_TICKS
                && target.squaredDistanceTo(lastTarget) < SAME_SPOT_SQR;
            lastTarget = teleport ? null : target;
            lastTick = tick;
            return teleport ? Kind.TELEPORT : pinned ? Kind.PINNED : Kind.SETBACK;
        }

        public void pin(int ticks) {
            pinnedTicks = Math.max(0, ticks);
        }

        public void tick() {
            if (pinnedTicks > 0) pinnedTicks--;
        }

        public boolean pinned() { return pinnedTicks > 0; }

        public void reset() {
            lastTarget = null;
            lastTick = 0;
            pinnedTicks = 0;
        }
    }

    public static final class AutoWaspSpeed {

        public static final double BUNCHED_SAFE = 12;

        public static final double HORIZONTAL_START = 3;

        public static final double HORIZONTAL_TOP = 15;

        public static final double VERTICAL_START = 1.5;

        public static final double VERTICAL_STEP = .5;
        public static final double VERTICAL_TOP = 3;

        public static final double TICKS_PER_SECOND = 20;

        static final double CLIMB_SPEED = .05;

        public static final int LIMIT_3D_HOLD_TICKS = 10;
        private static final double EPSILON = 1e-9;

        private AutoWaspSpeed() {}

        public static boolean acceptedWhenBunched(double speed) {
            return (2 * speed) * (2 * speed) <= 2 * 300;
        }

        public static Vec3d capLength(Vec3d velocity, double max) {
            if (!(max >= 0)) return Vec3d.ZERO;
            double length = velocity.length();
            if (length <= max || length < EPSILON) return velocity;
            return velocity.multiply(max / length);
        }

        public static void holdRampsTo(AutoWaspRamp horizontal, AutoWaspRamp vertical, double cap) {
            double ramps = Math.hypot(horizontal.speed(), vertical.speed());
            if (!(cap >= 0) || ramps <= cap) return;
            vertical.limitTo(vertical.speed() * cap / ramps);
            horizontal.limitTo(Math.sqrt(Math.max(0, cap * cap - vertical.speed() * vertical.speed())));
        }

        public static Vec3d capHorizontal(Vec3d velocity, double max) {
            if (!(max >= 0)) return new Vec3d(0, velocity.y, 0);
            double horizontal = velocity.horizontalLength();
            if (horizontal <= max || horizontal < EPSILON) return velocity;
            double scale = max / horizontal;
            return new Vec3d(velocity.x * scale, velocity.y, velocity.z * scale);
        }

        public static final class Limit3d {
            private int ticks;

            public boolean update(double verticalSpeed) {
                if (Math.abs(verticalSpeed) > CLIMB_SPEED) ticks = LIMIT_3D_HOLD_TICKS;
                else if (ticks > 0) ticks--;
                return ticks > 0;
            }

            public boolean active() { return ticks > 0; }

            public void reset() { ticks = 0; }
        }
    }

    public static final class AutoWaspSwapDelay {

        public static final int SETTLE_TICKS = 5;
        private static final long NOT_GLIDING = Long.MIN_VALUE;

        private boolean observed, wearingElytra, delayed;
        private long glideStarted = NOT_GLIDING;

        public void swapped() {
            delayed = true;
            glideStarted = NOT_GLIDING;
        }

        public void observe(long tick, boolean elytraEquipped, boolean gliding) {
            if (observed && elytraEquipped && !wearingElytra) swapped();
            observed = true;
            wearingElytra = elytraEquipped;
            if (!elytraEquipped) {
                delayed = false;
                glideStarted = NOT_GLIDING;
                return;
            }
            if (!delayed) return;
            if (!gliding) {
                glideStarted = NOT_GLIDING;
                return;
            }
            if (glideStarted == NOT_GLIDING) glideStarted = tick;
            if (tick - glideStarted >= SETTLE_TICKS) {
                delayed = false;
                glideStarted = NOT_GLIDING;
            }
        }

        public boolean ready() { return !delayed; }

        public void reset() {
            observed = wearingElytra = delayed = false;
            glideStarted = NOT_GLIDING;
        }
    }

    public static final class AutoWaspTakeoff {
        public enum Step { WAIT, JUMP, OPEN }

        static final double OPEN_BELOW = 0.1;

        public static final int REOPEN_TICKS = 10;

        public static final int LIQUID_REOPEN_TICKS = 2;
        private static final long NEVER = Long.MIN_VALUE;

        private long lastOpen = NEVER;

        public Step next(long tick, boolean onGround, boolean fallFlying, double verticalSpeed, boolean inLiquid) {
            if (fallFlying) return Step.WAIT;
            if (onGround) return Step.JUMP;
            if (!inLiquid && verticalSpeed > OPEN_BELOW) return Step.WAIT;
            if (lastOpen != NEVER && tick - lastOpen < (inLiquid ? LIQUID_REOPEN_TICKS : REOPEN_TICKS)) return Step.WAIT;
            lastOpen = tick;
            return Step.OPEN;
        }

        public void reset() {
            lastOpen = NEVER;
        }
    }

    public static final class AutoWaspTargetTracker {

        static final double SMOOTHING = .5;

        static final double TELEPORT_DISTANCE = 16;

        static final int INTERPOLATION_TICKS = 3;

        static final double MAX_LEAD_TICKS = 8;

        private int id = Integer.MIN_VALUE;
        private Vec3d last;
        private Vec3d velocity = Vec3d.ZERO;

        public void sample(int id, Vec3d position) {
            if (position == null || !Double.isFinite(position.x) || !Double.isFinite(position.y)
                || !Double.isFinite(position.z)) {
                reset();
                return;
            }
            if (id != this.id || last == null) {
                this.id = id;
                last = position;
                velocity = Vec3d.ZERO;
                return;
            }
            Vec3d moved = position.subtract(last);
            last = position;
            if (moved.lengthSquared() > TELEPORT_DISTANCE * TELEPORT_DISTANCE) {
                velocity = Vec3d.ZERO;
                return;
            }
            velocity = velocity.add(moved.subtract(velocity).multiply(SMOOTHING));
        }

        public Vec3d velocity() { return velocity; }

        public Vec3d predict(Vec3d position, int pingMs) {
            return position.add(velocity.multiply(leadTicks(pingMs)));
        }

        public static double leadTicks(int pingMs) {
            return Math.min(MAX_LEAD_TICKS, INTERPOLATION_TICKS + Math.max(0, pingMs) / 50.0);
        }

        public void reset() {
            id = Integer.MIN_VALUE;
            last = null;
            velocity = Vec3d.ZERO;
        }
    }
}
