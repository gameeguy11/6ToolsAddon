package gamerguy11.sixtoolsaddon.modules.autowasp;

import gamerguy11.sixtoolsaddon.mixin.accessor.AutoWaspFireworkAccessor;
import gamerguy11.sixtoolsaddon.modules.SixToolsAutoWasp;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPosition;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

public final class AutoWaspGlide {
    private AutoWaspGlide() {}

    public static final class AutoWaspGlideTracker {
        private final SixToolsAutoWasp module;
        private final AtomicInteger corrections = new AtomicInteger();
        private final AtomicReference<PlayerPositionLookS2CPacket> setback = new AtomicReference<>();
        private final AtomicReference<EntityVelocityUpdateS2CPacket> motion = new AtomicReference<>();
        private final AtomicReference<Vec3d> knockback = new AtomicReference<>(Vec3d.ZERO);
        private volatile int playerId = Integer.MIN_VALUE;
        private final AutoWaspServerGlide.Collider collider;
        private Vec3d serverGlide;
        private boolean groundedSinceReset, sentRotation;
        private float sentPitch, sentYaw;
        private int rockets, pendingRocketTicks;

        public AutoWaspGlideTracker(SixToolsAutoWasp module) {
            this.module = module;
            collider = (box, movement) -> {
                var mc = module.client();
                List<VoxelShape> entities = mc.world.getEntityCollisions(mc.player, box.stretch(movement));
                return Entity.adjustMovementForCollisions(mc.player, movement, box, mc.world, entities);
            };
        }

        public void onReceive(Packet<?> packet) {
            if (packet instanceof PlayerPositionLookS2CPacket position) {
                corrections.updateAndGet(n -> n < 1024 ? n + 1 : n);
                setback.set(position);
            } else if (packet instanceof EntityVelocityUpdateS2CPacket push && push.getEntityId() == playerId) {
                motion.set(push);
            } else if (packet instanceof ExplosionS2CPacket explosion) {
                explosion.playerKnockback().ifPresent(push -> knockback.accumulateAndGet(push, Vec3d::add));
            }
        }

        public void onSend(Packet<?> packet) {
            ClientPlayerEntity p = module.client().player;
            if (packet instanceof PlayerMoveC2SPacket move && move.changesLook()) {
                sentPitch = move.getPitch(0);
                sentYaw = move.getYaw(0);
                sentRotation = Float.isFinite(sentPitch) && Float.isFinite(sentYaw);
            } else if (packet instanceof PlayerInteractItemC2SPacket use && p != null && p.isGliding()) {
                ItemStack stack = p.getStackInHand(use.getHand());
                if (stack.isOf(Items.FIREWORK_ROCKET)) {
                    FireworksComponent fireworks = stack.get(DataComponentTypes.FIREWORKS);
                    pendingRocketTicks = Math.max(pendingRocketTicks,
                        10 * (1 + (fireworks == null ? 0 : fireworks.flightDuration())));
                }
            }
        }

        public int takeCorrections() { return corrections.getAndSet(0); }

        public PlayerPositionLookS2CPacket latestSetback() { return setback.get(); }

        public void preTick() {
            var mc = module.client();
            ClientPlayerEntity p = mc.player;
            playerId = p == null ? Integer.MIN_VALUE : p.getId();
            PlayerPositionLookS2CPacket correction = setback.getAndSet(null);
            EntityVelocityUpdateS2CPacket push = motion.getAndSet(null);
            Vec3d blast = knockback.getAndSet(Vec3d.ZERO);
            if (p == null || mc.world == null || !p.isGliding()) {
                serverGlide = null;
                groundedSinceReset = true;
                rockets = pendingRocketTicks = 0;
                return;
            }
            if (serverGlide == null) serverGlide = groundedSinceReset ? finite(p.getVelocity())
                : AutoWaspServerGlide.steady(p.getPitch(), p.getYaw(), p.getFinalGravity(), p.hasStatusEffect(StatusEffects.SLOW_FALLING));
            if (correction != null) serverGlide = finite(EntityPosition.apply(
                new EntityPosition(p.getEntityPos(), serverGlide, p.getYaw(), p.getPitch()),
                correction.change(), correction.relatives()).deltaMovement());
            if (push != null) serverGlide = finite(push.getVelocity());
            if (!blast.equals(Vec3d.ZERO)) serverGlide = finite(serverGlide.add(blast));
            rockets = Math.max(attachedRockets(p), pendingRocketTicks > 0 ? 1 : 0);
        }

        public void postTick() {
            var mc = module.client();
            ClientPlayerEntity p = mc.player;
            if (serverGlide == null || p == null || mc.world == null || !p.isGliding()) return;
            AutoWaspServerGlide.Tick tick = AutoWaspServerGlide.tick(context(p,
                sentRotation ? sentPitch : p.getPitch(), sentRotation ? sentYaw : p.getYaw()), p.getBoundingBox());
            serverGlide = tick.velocity();
            if (pendingRocketTicks > 0) pendingRocketTicks--;
        }

        public AutoWaspServerGlide.Context context(float pitch, float yaw) {
            ClientPlayerEntity p = module.client().player;
            return serverGlide == null || p == null ? null : context(p, pitch, yaw);
        }

        public boolean chunkLoaded(int chunkX, int chunkZ) {
            var level = module.client().world;
            return level != null && level.getChunkManager().isChunkLoaded(chunkX, chunkZ);
        }

        public void reset() {
            corrections.set(0);
            setback.set(null);
            motion.set(null);
            knockback.set(Vec3d.ZERO);
            playerId = Integer.MIN_VALUE;
            serverGlide = null;
            groundedSinceReset = sentRotation = false;
            rockets = pendingRocketTicks = 0;
        }

        private AutoWaspServerGlide.Context context(ClientPlayerEntity p, float pitch, float yaw) {
            return new AutoWaspServerGlide.Context(serverGlide, rockets, pitch, yaw, p.getFinalGravity(),
                p.hasStatusEffect(StatusEffects.SLOW_FALLING), collider);
        }

        private int attachedRockets(ClientPlayerEntity p) {
            int count = 0;
            for (FireworkRocketEntity rocket : module.client().world.getEntitiesByClass(FireworkRocketEntity.class,
                p.getBoundingBox().expand(16), r -> true)) {
                if (rocket.getDataTracker().get(AutoWaspFireworkAccessor.autowasp$attachedTarget())
                    .orElse(Integer.MIN_VALUE) == p.getId()) count++;
            }
            return count;
        }

        private static Vec3d finite(Vec3d vec) {
            return Double.isFinite(vec.x) && Double.isFinite(vec.y) && Double.isFinite(vec.z) ? vec : Vec3d.ZERO;
        }
    }

    public static final class AutoWaspServerGlide {

        public static final double HARMLESS_SPEED = 0.3 - 1e-7;
        static final double REACH_SCALE = 1.05;
        static final double REACH_PADDING = 0.03;
        static final int SEARCH_STEPS = 10;
        static final int STEADY_TICKS = 1200;
        static final double STEER_LOOKAHEAD = 2;
        static final int MAX_CHUNKS = 16;
        private static final float[] STEER_TURNS = {180, 135, -135, 90, -90, 45, -45};
        private static final double HORIZONTAL_DRAG = 0.99F;
        private static final double VERTICAL_DRAG = 0.98F;

        private AutoWaspServerGlide() {}

        public interface Collider {

            Vec3d collide(Box box, Vec3d movement);
        }

        public interface Chunks {

            boolean loaded(int chunkX, int chunkZ);
        }

        public record Context(Vec3d velocity, int rockets, float pitch, float yaw,
                              double gravity, boolean slowFalling, Collider collider) {
            public Context {
                Objects.requireNonNull(velocity, "velocity");
                Objects.requireNonNull(collider, "collider");
                if (!finite(velocity)) throw new IllegalArgumentException("Velocity must be finite.");
                if (rockets < 0) throw new IllegalArgumentException("Rocket count cannot be negative.");
                if (!Float.isFinite(pitch) || !Float.isFinite(yaw)) {
                    throw new IllegalArgumentException("Rotation must be finite.");
                }
                if (!Double.isFinite(gravity)) throw new IllegalArgumentException("Gravity must be finite.");
            }
        }

        public record Tick(Vec3d velocity, double damage, boolean horizontalCollision) {}

        public record Clearance(Vec3d motion, boolean limited, double damage) {}

        public record Rotation(float pitch, float yaw) {}

        public static double damage(double speedBefore, double speedAfter) {
            float damage = (float) ((speedBefore - speedAfter) * 10 - 3);
            return damage > 0 ? damage : 0;
        }

        public static Vec3d glide(Vec3d velocity, float pitch, float yaw, double gravity) {
            Vec3d look = look(pitch, yaw);
            float radians = pitch * 0.017453292F;
            double lookHorizontal = Math.sqrt(look.x * look.x + look.z * look.z);
            double speed = velocity.horizontalLength();
            double lift = Math.cos(radians) * Math.cos(radians);
            double x = velocity.x;
            double y = velocity.y + gravity * (-1 + lift * 0.75);
            double z = velocity.z;
            if (y < 0 && lookHorizontal > 0) {
                double gain = y * -0.1 * lift;
                x += look.x * gain / lookHorizontal;
                y += gain;
                z += look.z * gain / lookHorizontal;
            }
            if (radians < 0 && lookHorizontal > 0) {
                double climb = speed * -(float) Math.sin(radians) * 0.04;
                x -= look.x * climb / lookHorizontal;
                y += climb * 3.2;
                z -= look.z * climb / lookHorizontal;
            }
            if (lookHorizontal > 0) {
                x += (look.x / lookHorizontal * speed - x) * 0.1;
                z += (look.z / lookHorizontal * speed - z) * 0.1;
            }
            return new Vec3d(x * HORIZONTAL_DRAG, y * VERTICAL_DRAG, z * HORIZONTAL_DRAG);
        }

        public static Vec3d rocket(Vec3d velocity, float pitch, float yaw) {
            Vec3d look = look(pitch, yaw);
            return new Vec3d(
                velocity.x + look.x * 0.1 + (look.x * 1.5 - velocity.x) * 0.5,
                velocity.y + look.y * 0.1 + (look.y * 1.5 - velocity.y) * 0.5,
                velocity.z + look.z * 0.1 + (look.z * 1.5 - velocity.z) * 0.5);
        }

        public static Vec3d steady(float pitch, float yaw, double gravity, boolean slowFalling) {
            Vec3d velocity = Vec3d.ZERO;
            for (int tick = 0; tick < STEADY_TICKS; tick++) {
                velocity = settle(velocity);
                velocity = glide(velocity, pitch, yaw, gravity(gravity, slowFalling, velocity));
            }
            return velocity;
        }

        public static Tick tick(Context glide, Box reported) {
            return tick(glide, reported, false);
        }

        public static Vec3d reach(Context glide) {
            Objects.requireNonNull(glide, "glide");
            Vec3d start = start(glide);
            if (start.horizontalLength() <= HARMLESS_SPEED) return Vec3d.ZERO;
            return allowance(glide(start, glide.pitch(), glide.yaw(), gravity(glide, start)));
        }

        public static Clearance clear(Context glide, Box box, Vec3d movement, Chunks chunks) {
            Objects.requireNonNull(glide, "glide");
            Objects.requireNonNull(box, "box");
            Objects.requireNonNull(movement, "movement");
            Vec3d start = start(glide);
            if (start.horizontalLength() <= HARMLESS_SPEED) return new Clearance(movement, false, 0);
            double moving = risk(glide, box, movement, chunks);
            if (moving == 0) return new Clearance(movement, false, 0);

            double idle = risk(glide, box, Vec3d.ZERO, chunks);
            if (idle == 0) {
                double safe = 0;
                double unsafe = 1;
                for (int step = 0; step < SEARCH_STEPS; step++) {
                    double middle = (safe + unsafe) * 0.5;
                    if (risk(glide, box, movement.multiply(middle), chunks) == 0) safe = middle;
                    else unsafe = middle;
                }
                return new Clearance(movement.multiply(safe), true, 0);
            }
            Vec3d next = glide(start, glide.pitch(), glide.yaw(), gravity(glide, start));
            double horizontal = next.horizontalLength();
            if (horizontal == 0) return new Clearance(movement, false, moving);
            Vec3d back = new Vec3d(-next.x / horizontal, 0, -next.z / horizontal);
            double far = horizontal * REACH_SCALE + REACH_PADDING;
            if (risk(glide, box, back.multiply(far), chunks) > 0) return new Clearance(movement, false, moving);
            double unsafe = 0;
            double safe = far;
            for (int step = 0; step < SEARCH_STEPS; step++) {
                double middle = (safe + unsafe) * 0.5;
                if (risk(glide, box, back.multiply(middle), chunks) == 0) safe = middle;
                else unsafe = middle;
            }
            return new Clearance(back.multiply(safe), true, 0);
        }

        public static Rotation steer(Context camera, Box box, Chunks chunks) {
            Objects.requireNonNull(camera, "camera");
            Objects.requireNonNull(box, "box");
            Vec3d level = steady(0, 0, camera.gravity(), camera.slowFalling());
            double speed = level.horizontalLength();
            if (risk(camera, box, Vec3d.ZERO, chunks) == 0 && risk(heading(camera, level.y,
                speed * STEER_LOOKAHEAD, camera.pitch(), camera.yaw()), box, Vec3d.ZERO, chunks) == 0) return null;
            for (float turn : STEER_TURNS) {
                float yaw = MathHelper.wrapDegrees(camera.yaw() + turn);
                if (risk(heading(camera, level.y, speed, 0, yaw), box, Vec3d.ZERO, chunks) == 0) {
                    return new Rotation(0, yaw);
                }
            }
            return null;
        }

        static boolean loadedAlong(Box box, Vec3d movement, Chunks chunks) {
            Box swept = box.stretch(movement);
            if (!finite(new Vec3d(swept.minX, swept.minY, swept.minZ)) || !finite(new Vec3d(swept.maxX, swept.maxY, swept.maxZ))) {
                return false;
            }
            int minX = MathHelper.floor(swept.minX / 16);
            int minZ = MathHelper.floor(swept.minZ / 16);
            int maxX = MathHelper.floor(Math.nextDown(swept.maxX / 16));
            int maxZ = MathHelper.floor(Math.nextDown(swept.maxZ / 16));
            if (maxX < minX || maxZ < minZ || ((long) maxX - minX + 1) * ((long) maxZ - minZ + 1) > MAX_CHUNKS) return false;
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!chunks.loaded(x, z)) return false;
                }
            }
            return true;
        }

        private static Context heading(Context glide, double y, double speed, float pitch, float yaw) {
            Vec3d look = look(0, yaw);
            return new Context(new Vec3d(look.x * speed, y, look.z * speed), glide.rockets(), pitch, yaw,
                glide.gravity(), glide.slowFalling(), glide.collider());
        }

        private static double risk(Context glide, Box box, Vec3d movement, Chunks chunks) {
            Vec3d moved = glide.collider().collide(box, movement);
            Vec3d start = start(glide);
            double worst = damage(start.horizontalLength(), 0);
            if (chunks != null && moved.length() > 0 && !loadedAlong(box, moved, chunks)) return worst;
            Box reported = box.offset(moved.x, moved.y, moved.z);
            Tick tick = tick(glide, reported, true);
            if (tick.damage() > 0) return tick.damage();
            if (chunks != null) {
                Vec3d reach = reach(glide);
                if (!loadedAlong(reported, reach, chunks)) return worst;
            }
            return 0;
        }

        private static Tick tick(Context glide, Box reported, boolean padded) {
            Vec3d velocity = start(glide);
            double before = velocity.horizontalLength();
            Vec3d next = glide(velocity, glide.pitch(), glide.yaw(), gravity(glide, velocity));
            Vec3d probe = padded ? allowance(next) : next;
            Vec3d moved = glide.collider().collide(reported, probe);
            boolean x = !same(probe.x, moved.x);
            boolean z = !same(probe.z, moved.z);
            boolean vertical = probe.y != moved.y;
            Vec3d after = new Vec3d(x ? 0 : next.x, vertical ? 0 : next.y, z ? 0 : next.z);
            return new Tick(after, x || z ? damage(before, after.horizontalLength()) : 0, x || z);
        }

        private static Vec3d start(Context glide) {
            Vec3d velocity = glide.velocity();
            for (int rocket = 0; rocket < glide.rockets(); rocket++) {
                velocity = rocket(velocity, glide.pitch(), glide.yaw());
            }
            return settle(velocity);
        }

        static Vec3d settle(Vec3d velocity) {
            double x = velocity.x;
            double y = velocity.y;
            double z = velocity.z;
            if (x * x + z * z < 9.0E-6) {
                x = 0;
                z = 0;
            }
            if (Math.abs(y) < 0.003) y = 0;
            return new Vec3d(x, y, z);
        }

        static Vec3d look(float pitch, float yaw) {
            float pitchRadians = pitch * 0.017453292F;
            float yawRadians = -yaw * 0.017453292F;
            float yawCos = (float) Math.cos(yawRadians);
            float yawSin = (float) Math.sin(yawRadians);
            float pitchCos = (float) Math.cos(pitchRadians);
            float pitchSin = (float) Math.sin(pitchRadians);
            return new Vec3d(yawSin * pitchCos, -pitchSin, yawCos * pitchCos);
        }

        private static double gravity(Context glide, Vec3d velocity) {
            return gravity(glide.gravity(), glide.slowFalling(), velocity);
        }

        private static double gravity(double gravity, boolean slowFalling, Vec3d velocity) {
            return slowFalling && velocity.y <= 0 ? Math.min(gravity, 0.01) : gravity;
        }

        private static Vec3d allowance(Vec3d next) {
            double horizontal = next.horizontalLength();
            if (horizontal == 0) return next;
            double factor = (horizontal * REACH_SCALE + REACH_PADDING) / horizontal;
            return new Vec3d(next.x * factor, next.y, next.z * factor);
        }

        private static boolean same(double first, double second) {
            return Math.abs(second - first) < 1.0E-5F;
        }

        private static boolean finite(Vec3d vec) {
            return Double.isFinite(vec.x) && Double.isFinite(vec.y) && Double.isFinite(vec.z);
        }
    }
}
