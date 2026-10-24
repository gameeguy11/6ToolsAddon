package gamerguy11.sixtoolsaddon.utils;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public final class RangeUtils {
    private RangeUtils() {}

    public static Vec3d nearestPoint(BlockPos pos, Vec3d from) {
        return new Vec3d(
            MathHelper.clamp(from.x, pos.getX(), pos.getX() + 1.0),
            MathHelper.clamp(from.y, pos.getY(), pos.getY() + 1.0),
            MathHelper.clamp(from.z, pos.getZ(), pos.getZ() + 1.0)
        );
    }

    public static Vec3d nearestPoint(BlockPos pos) {
        return nearestPoint(pos, mc.player.getEyePos());
    }

    public static double squaredDistance(BlockPos pos, Vec3d from) {
        return from.squaredDistanceTo(nearestPoint(pos, from));
    }

    public static double distanceTo(BlockPos pos) {
        if (mc.player == null) return Double.MAX_VALUE;
        return Math.sqrt(squaredDistance(pos, mc.player.getEyePos()));
    }

    public static boolean isInRange(double reach, BlockPos pos, Vec3d from) {
        return squaredDistance(pos, from) <= reach * reach;
    }

    public static boolean isInRange(double reach, BlockPos pos) {
        if (mc.player == null) return false;
        return isInRange(reach, pos, mc.player.getEyePos());
    }

    public static boolean isInReach(BlockPos pos) {
        if (mc.player == null) return false;
        double reach = mc.player.getBlockInteractionRange();
        return squaredDistance(pos, mc.player.getEyePos()) < reach * reach;
    }

    public static Direction nearestFace(BlockPos pos, Vec3d from) {
        Direction best = Direction.UP;
        double bestOutside = -Double.MAX_VALUE;

        for (Direction dir : Direction.values()) {
            double outside = outsideDistance(pos, dir, from);

            if (outside > bestOutside) {
                bestOutside = outside;
                best = dir;
            }
        }

        return best;
    }

    public static Direction nearestFace(BlockPos pos) {
        if (mc.player == null) return Direction.UP;
        return nearestFace(pos, mc.player.getEyePos());
    }

    public static Direction nearestExposedFace(BlockPos pos, Vec3d from) {
        if (mc.world == null) return nearestFace(pos, from);

        Direction best = null;
        double bestOutside = -Double.MAX_VALUE;

        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.offset(dir);
            if (mc.world.getBlockState(neighbor).isSolidBlock(mc.world, neighbor)) continue;

            double outside = outsideDistance(pos, dir, from);
            if (outside > bestOutside) {
                bestOutside = outside;
                best = dir;
            }
        }

        return best != null ? best : nearestFace(pos, from);
    }

    public static Direction nearestExposedFace(BlockPos pos) {
        if (mc.player == null) return Direction.UP;
        return nearestExposedFace(pos, mc.player.getEyePos());
    }

    public static Vec3d nearestPointOnFace(BlockPos pos, Direction face, Vec3d from) {
        Vec3d nearest = nearestPoint(pos, from);
        double plane = face.getAxis().choose(pos.getX(), pos.getY(), pos.getZ())
            + (face.getDirection().offset() > 0 ? 1.0 : 0.0);

        return switch (face.getAxis()) {
            case X -> new Vec3d(plane, nearest.y, nearest.z);
            case Y -> new Vec3d(nearest.x, plane, nearest.z);
            case Z -> new Vec3d(nearest.x, nearest.y, plane);
        };
    }

    public static Vec3d nearestPointOnFace(BlockPos pos, Direction face) {
        if (mc.player == null) return pos.toCenterPos();
        return nearestPointOnFace(pos, face, mc.player.getEyePos());
    }

    private static double outsideDistance(BlockPos pos, Direction dir, Vec3d from) {
        double sign = dir.getDirection().offset();
        double plane = dir.getAxis().choose(pos.getX(), pos.getY(), pos.getZ()) + (sign > 0 ? 1.0 : 0.0);
        return (dir.getAxis().choose(from.x, from.y, from.z) - plane) * sign;
    }
}
