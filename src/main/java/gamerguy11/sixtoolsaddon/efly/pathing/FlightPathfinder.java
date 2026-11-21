package gamerguy11.sixtoolsaddon.efly.pathing;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class FlightPathfinder {
    public enum Status { RUNNING, FOUND, PARTIAL, FAILED }

    private static final byte UNSET = 0, FREE = 1, BLOCKED = 2, UNLOADED = 3, LOW = 4, MINE = 5;
    private static final double LOW_COST = 3;
    private static final double HEURISTIC_WEIGHT = 1.4;
    private static final double HEURISTIC_WEIGHT_PARTIAL = 2.5;
    private final double hWeight;

    private static final Set<Block> AVOID = Set.of(
        Blocks.COBWEB, Blocks.SWEET_BERRY_BUSH, Blocks.POWDER_SNOW, Blocks.FIRE, Blocks.SOUL_FIRE, Blocks.WITHER_ROSE,
        Blocks.NETHER_PORTAL, Blocks.END_PORTAL, Blocks.END_GATEWAY, Blocks.BUBBLE_COLUMN, Blocks.SCAFFOLDING
    );

    private final World world;
    private final BlockPos start;
    private final BlockPos goal;
    private final double tx, tz;
    private final double desiredY;
    private final double yWeight;
    private final int horizon;
    private final boolean unloadedIsFrontier;
    private final int minY, maxY;
    private final int maxNodes;
    private final double wallCost;
    private final boolean allowLowGaps;
    private final double mineCost;
    private final LongSet noMine;
    private boolean lavaOk;

    public FlightPathfinder lavaOk(boolean ok) {
        this.lavaOk = ok;
        return this;
    }

    private static final class Sec {
        final int sx, sy, sz;
        final short[] info = new short[4096];
        Node[] nodes;

        Sec(int sx, int sy, int sz) {
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
        }
    }

    private static final int I_CELL = 7;
    private static final int I_CLASS_SHIFT = 3, I_CLASS = 3 << 3;
    private static final int I_FALL_SHIFT = 5, I_FALL = 31 << 5;
    private static final int I_NODE_SHIFT = 10, I_NODE = 7 << 10;
    private static final int I_CLOSED = 1 << 13;

    private final Long2ObjectOpenHashMap<Sec> secs = new Long2ObjectOpenHashMap<>();
    private final Sec[] hot = new Sec[8];
    private double[] openF = new double[1024];
    private Node[] openNode = new Node[1024];
    private int openSize;
    private final BlockPos.Mutable mpos = new BlockPos.Mutable();
    private final BlockPos.Mutable qpos = new BlockPos.Mutable();
    private final byte[] cube = new byte[27];

    private Node startNode, best;
    private int expanded;
    private Status status = Status.RUNNING;
    private List<BlockPos> result = List.of();
    private boolean endsAtFrontier;

    private static final class Node {
        final int x, y, z;
        double g, h, f;
        Node parent;
        boolean closed, frontier;
        boolean costed;
        double cost1, cost2, cost3;

        Node(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final int[] NDX = new int[27], NDY = new int[27], NDZ = new int[27], AXES = new int[27];
    private static final int[][] CORNERS = new int[27][];
    private static final double[] STEP = {0, Math.sqrt(1), Math.sqrt(2), Math.sqrt(3)};

    static {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int i = (dx + 1) * 9 + (dy + 1) * 3 + (dz + 1);
                    NDX[i] = dx;
                    NDY[i] = dy;
                    NDZ[i] = dz;
                    AXES[i] = (dx != 0 ? 1 : 0) + (dy != 0 ? 1 : 0) + (dz != 0 ? 1 : 0);

                    int[] c = new int[6];
                    int count = 0;
                    for (int mask = 1; mask < 7; mask++) {
                        int mx = (mask & 1) != 0 ? dx : 0, my = (mask & 2) != 0 ? dy : 0, mz = (mask & 4) != 0 ? dz : 0;
                        if ((mx | my | mz) == 0 || (mx == dx && my == dy && mz == dz)) continue;
                        int ci = (mx + 1) * 9 + (my + 1) * 3 + (mz + 1);
                        boolean seen = false;
                        for (int j = 0; j < count; j++) seen |= c[j] == ci;
                        if (!seen) c[count++] = ci;
                    }
                    CORNERS[i] = java.util.Arrays.copyOf(c, count);
                }
            }
        }
    }

    public FlightPathfinder(World world, BlockPos start, BlockPos goal, double tx, double tz, double desiredY, double yWeight,
                            int horizon, boolean unloadedIsFrontier, int minY, int maxY, int maxNodes, double wallCost,
                            boolean allowLowGaps, double mineCost, LongSet noMine) {
        this.world = world;
        this.start = start.toImmutable();
        this.goal = goal == null ? null : goal.toImmutable();
        this.tx = tx;
        this.tz = tz;
        this.desiredY = desiredY;
        this.yWeight = yWeight;
        this.horizon = horizon;
        this.unloadedIsFrontier = unloadedIsFrontier;
        this.minY = minY;
        this.maxY = maxY;
        this.maxNodes = maxNodes;
        this.wallCost = wallCost;
        this.allowLowGaps = allowLowGaps;
        this.hWeight = goal == null ? HEURISTIC_WEIGHT_PARTIAL : HEURISTIC_WEIGHT;
        this.mineCost = mineCost;
        this.noMine = noMine;

        startNode = node(start.getX(), start.getY(), start.getZ());
        startNode.g = 0;
        startNode.h = heuristic(startNode.x, startNode.y, startNode.z);
        startNode.f = hWeight * startNode.h;
        best = startNode;
        openAdd(startNode, startNode.f);
    }

    public Status status() {
        return status;
    }

    public List<BlockPos> path() {
        return result;
    }

    public boolean plannedMine(BlockPos b) {
        return mineCost > 0 && nodeState(b.getX(), b.getY(), b.getZ()) == MINE;
    }

    public boolean endsAtFrontier() {
        return endsAtFrontier;
    }

    public int expanded() {
        return expanded;
    }

    public Status step(long budgetNanos) {
        if (status != Status.RUNNING) return status;
        long end = System.nanoTime() + budgetNanos;
        int i = 0;

        while (openSize > 0) {
            if ((++i & 31) == 0 && System.nanoTime() > end) return status;

            double f = openF[0];
            Node n = openPoll();
            if (n.closed || f != n.f) continue;
            n.closed = true;
            sec(n.x >> 4, n.y >> 4, n.z >> 4).info[idx(n.x, n.y, n.z)] |= I_CLOSED;
            expanded++;

            if (n.h < best.h) best = n;

            if (goal != null && n.x == goal.getX() && n.y == goal.getY() && n.z == goal.getZ()) return finish(n, Status.FOUND, false);
            if (n.frontier) return finish(n, Status.PARTIAL, true);
            if (goal == null && n != startNode && horizontalDist(n.x, n.z, start.getX(), start.getZ()) >= horizon) {
                return finish(n, Status.PARTIAL, true);
            }
            if (expanded >= maxNodes) break;

            expand(n);
        }

        if (best != startNode) return finish(best, Status.PARTIAL, false);
        status = Status.FAILED;
        return status;
    }

    public double bestGain() {
        return startNode.h - best.h;
    }

    public Status finishNow() {
        if (status != Status.RUNNING) return status;
        if (best != startNode) return finish(best, Status.PARTIAL, false);
        status = Status.FAILED;
        return status;
    }

    private Status finish(Node n, Status s, boolean frontier) {
        ArrayList<BlockPos> list = new ArrayList<>();
        for (Node c = n; c != null; c = c.parent) list.add(new BlockPos(c.x, c.y, c.z));
        Collections.reverse(list);
        result = list;
        endsAtFrontier = frontier;
        status = s;
        return s;
    }

    private void expand(Node n) {
        final int x = n.x, y = n.y, z = n.z;
        boolean fromMine = mineCost > 0 && nodeState(x, y, z) == MINE;
        final byte[] cube = this.cube;
        int closed = 0;
        for (int i = 0; i < 27; i++) {
            if (i == 13) {
                cube[i] = FREE;
                continue;
            }
            int v = nodeInfo(x + NDX[i], y + NDY[i], z + NDZ[i]);
            cube[i] = (byte) (v & 7);
            closed |= (v >> 3) << i;
        }

        next:
        for (int i = 0; i < 27; i++) {
            if (i == 13 || (closed >> i & 1) != 0) continue;
            byte s = cube[i];
            if (s == BLOCKED) continue;
            int axes = AXES[i];
            if ((s == MINE || fromMine) && axes != 1) continue;

            for (int ci : CORNERS[i]) {
                byte c = cube[ci];
                if (c == BLOCKED || c == MINE) continue next;
            }

            int nx = x + NDX[i], ny = y + NDY[i], nz = z + NDZ[i];
            Node m = node(nx, ny, nz);
            if (!m.costed) cost(m, s);
            double g = n.g + (axes == 1 ? m.cost1 : axes == 2 ? m.cost2 : m.cost3);
            if (m.parent != null || m == startNode) {
                if (g >= m.g) continue;
            }
            else {
                m.h = heuristic(nx, ny, nz);
            }
            m.g = g;
            m.parent = n;
            m.frontier = s == UNLOADED;
            m.f = g + hWeight * m.h;
            openAdd(m, m.f);
        }
    }

    private void openAdd(Node node, double f) {
        int k = openSize;
        if (k >= openF.length) {
            openF = java.util.Arrays.copyOf(openF, k * 2);
            openNode = java.util.Arrays.copyOf(openNode, k * 2);
        }
        openSize = k + 1;
        final double[] fs = openF;
        final Node[] ns = openNode;
        while (k > 0) {
            int parent = (k - 1) >>> 1;
            double pf = fs[parent];
            if (Double.compare(f, pf) >= 0) break;
            fs[k] = pf;
            ns[k] = ns[parent];
            k = parent;
        }
        fs[k] = f;
        ns[k] = node;
    }

    private Node openPoll() {
        final double[] fs = openF;
        final Node[] ns = openNode;
        Node result = ns[0];
        int n = --openSize;
        double f = fs[n];
        Node node = ns[n];
        ns[n] = null;
        if (n > 0) {
            int k = 0, half = n >>> 1;
            while (k < half) {
                int child = (k << 1) + 1, right = child + 1;
                double cf = fs[child];
                if (right < n && Double.compare(cf, fs[right]) > 0) cf = fs[child = right];
                if (Double.compare(f, cf) <= 0) break;
                fs[k] = cf;
                ns[k] = ns[child];
                k = child;
            }
            fs[k] = f;
            ns[k] = node;
        }
        return result;
    }

    private void cost(Node m, byte s) {
        final int x = m.x, y = m.y, z = m.z;
        double c1 = STEP[1], c2 = STEP[2], c3 = STEP[3];
        if (s == MINE) {
            c1 += mineCost;
            c2 += mineCost;
            c3 += mineCost;
        }
        else if (s == LOW) {
            c1 += LOW_COST;
            c2 += LOW_COST;
            c3 += LOW_COST;
        }
        else if (s == FREE && wallCost > 0) {
            double p = wallCost * (walls(x, y, z) + nearWall(x, y, z));
            c1 += p;
            c2 += p;
            c3 += p;
        }
        if (!lavaOk && nearLava(x, y, z)) {
            c1 += LAVA_COST;
            c2 += LAVA_COST;
            c3 += LAVA_COST;
        }
        final double[] dg = DANGERS;
        if (dg.length > 0 && DANGER_COST > 0) {
            final double r2 = DANGER_RADIUS * DANGER_RADIUS;
            for (int i = 0; i + 2 < dg.length; i += 3) {
                double ddx = x + 0.5 - dg[i], ddy = y + 0.3 - dg[i + 1], ddz = z + 0.5 - dg[i + 2];
                if (ddx * ddx + ddy * ddy + ddz * ddz < r2) {
                    c1 += DANGER_COST;
                    c2 += DANGER_COST;
                    c3 += DANGER_COST;
                    break;
                }
            }
        }
        if (lavaOk && LAVA_OK_COST > 0 && (isLava(x, y, z) || isLava(x, y + 1, z))) {
            c1 += LAVA_OK_COST;
            c2 += LAVA_OK_COST;
            c3 += LAVA_OK_COST;
        }
        if (s == FREE && wallCost > 0) {
            double p = wallCost * heightPenalty(x, y, z);
            c1 += p;
            c2 += p;
            c3 += p;
        }
        m.cost1 = c1;
        m.cost2 = c2;
        m.cost3 = c3;
        m.costed = true;
    }

    private Node node(int x, int y, int z) {
        Sec sec = sec(x >> 4, y >> 4, z >> 4);
        Node[] a = sec.nodes;
        if (a == null) a = sec.nodes = new Node[4096];
        int i = idx(x, y, z);
        Node n = a[i];
        if (n == null) a[i] = n = new Node(x, y, z);
        return n;
    }

    private static int idx(int x, int y, int z) {
        return ((x & 15) << 8) | ((z & 15) << 4) | (y & 15);
    }

    private Sec sec(int sx, int sy, int sz) {
        Sec s = hot[((sx & 1) << 2) | ((sy & 1) << 1) | (sz & 1)];
        if (s != null && s.sx == sx && s.sy == sy && s.sz == sz) return s;
        return secSlow(sx, sy, sz);
    }

    private Sec secSlow(int sx, int sy, int sz) {
        long k = ((long) sx & 0x3FFFFF) << 42 | ((long) sz & 0x3FFFFF) << 20 | ((long) sy & 0xFFFFF);
        Sec s = secs.get(k);
        if (s == null) {
            s = new Sec(sx, sy, sz);
            secs.put(k, s);
        }
        hot[((sx & 1) << 2) | ((sy & 1) << 1) | (sz & 1)] = s;
        return s;
    }

    private double heuristic(int x, int y, int z) {
        if (goal != null) {
            double dx = x - goal.getX(), dy = y - goal.getY(), dz = z - goal.getZ();
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        return horizontalDist(x + 0.5, z + 0.5, tx, tz) + yWeight * Math.abs(y - desiredY);
    }

    public static double LAVA_COST = 20;
    public static double LAVA_OK_COST = 6;
    public static double[] DANGERS = new double[0];
    public static double DANGER_RADIUS = 5, DANGER_COST = 12;
    public static int FLOOR_CLEARANCE = 10;
    public static double FLOOR_WEIGHT = 0.5, CEILING_WEIGHT = 0.3;
    public static double FLOOR_TOUCH = 6;

    private double heightPenalty(int x, int y, int z) {
        int clearance = FLOOR_CLEARANCE;
        int below = clearance;
        for (int d = 1; d <= clearance; d++) {
            byte s = cell(x, y - d, z);
            if (s == BLOCKED || s == MINE) {
                below = d - 1;
                break;
            }
        }
        int above = 4;
        for (int d = 2; d <= 5; d++) {
            byte s = cell(x, y + d, z);
            if (s == BLOCKED || s == MINE) {
                above = d - 2;
                break;
            }
        }
        byte c = (byte) (below * 8 + above);
        below = c / 8;
        above = c % 8;
        return FLOOR_WEIGHT * (clearance - below) + CEILING_WEIGHT * (4 - above) + (below == 0 ? FLOOR_TOUCH : below == 1 ? FLOOR_TOUCH / 3 : 0);
    }

    private static final int[] LAVA_NEAR = {1, 0, 0, -1, 0, 0, 0, 0, 1, 0, 0, -1, 0, -1, 0, 1, 1, 0, -1, 1, 0, 0, 1, 1, 0, 1, -1, 0, 2, 0, 0, -2, 0, 0, -3, 0,
        1, 0, 1, 1, 0, -1, -1, 0, 1, -1, 0, -1, 1, -1, 0, -1, -1, 0, 0, -1, 1, 0, -1, -1, 1, -1, 1, 1, -1, -1, -1, -1, 1, -1, -1, -1};

    private boolean nearLava(int x, int y, int z) {
        final int[] o = LAVA_NEAR;
        for (int i = 0; i < o.length; i += 3) {
            if (isLava(x + o[i], y + o[i + 1], z + o[i + 2])) return true;
        }
        return false;
    }

    private static final int CL_PASS = 1, CL_STOP = 2, CL_LAVA = 3;
    private static final int LAVA_RANGE = 24;
    private static final int FALL_NONE = LAVA_RANGE + 1;

    private int lavaClass(BlockState st, BlockPos pos) {
        if (st.isAir()) return CL_PASS;
        if (st.isOf(Blocks.FIRE) || st.isOf(Blocks.SOUL_FIRE)) return CL_LAVA;
        if (st.getFluidState().isIn(net.minecraft.registry.tag.FluidTags.LAVA)) return CL_LAVA;
        if (!st.getCollisionShape(world, pos).isEmpty() || !st.getFluidState().isEmpty()) return CL_STOP;
        return CL_PASS;
    }

    private boolean isLava(int x, int y, int z) {
        short[] a = sec(x >> 4, y >> 4, z >> 4).info;
        int i = idx(x, y, z);
        int b = a[i], cls = (b & I_CLASS) >> I_CLASS_SHIFT;
        if (cls == 0) {
            if (!world.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) return false;
            qpos.set(x, y, z);
            cls = lavaClass(world.getBlockState(qpos), qpos);
            a[i] = (short) (b | cls << I_CLASS_SHIFT);
        }
        return cls == CL_LAVA;
    }

    private boolean lavaFall(int x, int y, int z) {
        final int top = world.getTopYInclusive(), sx = x >> 4, sz = z >> 4, col = idx(x, 0, z);
        Sec sec = null;
        short[] a = null;

        int v = FALL_NONE, j = 1;
        for (; j <= LAVA_RANGE; j++) {
            int yy = y + j;
            if (yy > top) break;
            if (sec == null || (yy & 15) == 0) {
                sec = sec(sx, yy >> 4, sz);
                a = sec.info;
            }
            int i = col | (yy & 15);
            int b = a[i], cls = (b & I_CLASS) >> I_CLASS_SHIFT;
            if (cls == 0) {
                qpos.set(x, yy, z);
                cls = lavaClass(world.getBlockState(qpos), qpos);
                a[i] = (short) (b | cls << I_CLASS_SHIFT);
            }
            if (cls == CL_LAVA) {
                v = 1;
                break;
            }
            if (cls == CL_STOP) break;
            int f = (b & I_FALL) >> I_FALL_SHIFT;
            if (f != 0) {
                v = f >= LAVA_RANGE ? FALL_NONE : f + 1;
                break;
            }
        }
        if (j > LAVA_RANGE) j = 1;

        for (int yy = y + j - 1; ; yy--) {
            if (sec == null || sec.sy != yy >> 4) {
                sec = sec(sx, yy >> 4, sz);
                a = sec.info;
            }
            int i = col | (yy & 15);
            a[i] = (short) ((a[i] & ~I_FALL) | (v << I_FALL_SHIFT));
            if (yy == y) return v != FALL_NONE;
            if (v != FALL_NONE && ++v > LAVA_RANGE) v = FALL_NONE;
        }
    }

    private static double horizontalDist(double x1, double z1, double x2, double z2) {
        return Math.hypot(x1 - x2, z1 - z2);
    }

    private byte nodeState(int x, int y, int z) {
        return (byte) (nodeInfo(x, y, z) & 7);
    }

    private int nodeInfo(int x, int y, int z) {
        short[] a = sec(x >> 4, y >> 4, z >> 4).info;
        int i = idx(x, y, z);
        int v = a[i] >> I_NODE_SHIFT;
        if ((v & 7) != UNSET) return v;

        byte s;
        byte feet = cell(x, y, z), head = cell(x, y + 1, z);
        if (feet == BLOCKED) s = BLOCKED;
        else if (feet == MINE) s = MINE;
        else if (head == BLOCKED || head == MINE) s = (allowLowGaps || mineCost > 0) && feet == FREE ? LOW : BLOCKED;
        else if (feet == UNLOADED || head == UNLOADED) s = unloadedIsFrontier ? UNLOADED : FREE;
        else s = FREE;

        a[i] |= s << I_NODE_SHIFT;
        return v | s;
    }

    private int walls(int x, int y, int z) {
        int c = 0;
        for (int dy = 0; dy <= 1; dy++) {
            if (solid(cell(x + 1, y + dy, z))) c++;
            if (solid(cell(x - 1, y + dy, z))) c++;
            if (solid(cell(x, y + dy, z + 1))) c++;
            if (solid(cell(x, y + dy, z - 1))) c++;
        }
        if (solid(cell(x, y - 1, z))) c++;
        if (solid(cell(x, y + 2, z))) c++;
        return c;
    }

    private double nearWall(int x, int y, int z) {
        for (int r = 2; r <= 3; r++) {
            for (int i = 0; i < 27; i++) {
                if (i == 13) continue;
                int dy = NDY[i];
                int sy = (dy > 0 ? y + 1 : y) + dy * r;
                if (solid(cell(x + NDX[i] * r, sy, z + NDZ[i] * r))) return r == 2 ? 1.5 : 0.5;
            }
        }
        return 0;
    }

    private byte cell(int x, int y, int z) {
        if (y < minY || y > maxY) return BLOCKED;
        short[] a = sec(x >> 4, y >> 4, z >> 4).info;
        int i = idx(x, y, z);
        int s = a[i] & I_CELL;
        if (s != UNSET) return (byte) s;
        return readCell(a, i, x, y, z);
    }

    private byte readCell(short[] a, int i, int x, int y, int z) {
        byte s;
        if (!world.getWorldBorder().contains(x + 0.5, z + 0.5)) s = BLOCKED;
        else if (!world.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) s = UNLOADED;
        else {
            mpos.set(x, y, z);
            BlockState st = world.getBlockState(mpos);
            if (!lavaOk) {
                int b = a[i];
                if ((b & I_CLASS) == 0) a[i] = (short) (b | lavaClass(st, mpos) << I_CLASS_SHIFT);
            }
            if (lavaOk && st.getFluidState().isIn(net.minecraft.registry.tag.FluidTags.LAVA) && st.getCollisionShape(world, mpos).isEmpty()) s = FREE;
            else if (!blocks(world, mpos, st)) s = !lavaOk && lavaFall(x, y, z) ? BLOCKED : FREE;
            else s = mineCost > 0 && !noMine.contains(BlockPos.asLong(x, y, z)) && minable(world, mpos.toImmutable(), st) ? MINE : BLOCKED;
        }
        a[i] |= s;
        return s;
    }

    public static boolean blocks(World world, BlockPos pos, BlockState state) {
        if (state.isAir()) return false;
        if (!state.getFluidState().isEmpty()) return true;
        if (AVOID.contains(state.getBlock())) return true;
        return !state.getCollisionShape(world, pos).isEmpty();
    }

    public static boolean lavaAbove(World world, int x, int y, int z, int range) {
        BlockPos.Mutable q = new BlockPos.Mutable();
        int top = Math.min(world.getTopYInclusive(), y + range);
        for (int yy = y + 1; yy <= top; yy++) {
            q.set(x, yy, z);
            BlockState st = world.getBlockState(q);
            if (st.getFluidState().isIn(net.minecraft.registry.tag.FluidTags.LAVA)) return true;
            if (!st.getCollisionShape(world, q).isEmpty() || !st.getFluidState().isEmpty()) return false;
        }
        return false;
    }

    private static boolean solid(byte c) {
        return c == BLOCKED || c == MINE;
    }

    public static boolean minable(World world, BlockPos pos, BlockState state) {
        if (!state.getFluidState().isEmpty() || AVOID.contains(state.getBlock()) || state.hasBlockEntity()) return false;
        float h = state.getHardness(world, pos);
        if (h < 0 || h >= 20) return false;
        for (Direction d : Direction.values()) {
            if (!world.getBlockState(pos.offset(d)).getFluidState().isEmpty()) return false;
        }
        return !(world.getBlockState(pos.up()).getBlock() instanceof FallingBlock);
    }

    public static boolean open(World world, int x, int y, int z) {
        if (y < world.getBottomY() || y + 1 > world.getTopYInclusive()) return false;
        if (!world.getChunkManager().isChunkLoaded(x >> 4, z >> 4) || !world.getWorldBorder().contains(x + 0.5, z + 0.5)) return false;
        BlockPos.Mutable p = new BlockPos.Mutable(x, y, z);
        if (blocks(world, p, world.getBlockState(p))) return false;
        p.setY(y + 1);
        return !blocks(world, p, world.getBlockState(p));
    }
}
