package gamerguy11.sixtoolsaddon.efly.autonav;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.PriorityQueue;

public final class AutonavPlanner {
    private AutonavPlanner() {}

    public interface Grid {
        boolean isChunkUnloaded(int chunkX, int chunkZ);

        boolean isPassable(int x, int y, int z, int clearance);

        boolean lineClear(int x0, int y0, int z0, int x1, int y1, int z1);

        int escapeCost(int x, int y, int z);

        int minY();
        int maxY();
    }

    public enum Status {
        REACHED_DESTINATION,
        REACHED_FRONTIER,
        BEST_EFFORT,
        SEALED,
        ESCAPING
    }

    public static final class Result {
        public final Status status;
        public final List<int[]> cells;
        public final int expanded;

        Result(Status status, List<int[]> cells, int expanded) {
            this.status = status;
            this.cells = cells;
            this.expanded = expanded;
        }
    }

    private static final double HEURISTIC_WEIGHT = 2.0;
    private static final double LEVEL_BIAS = 0.05;

    private static final class Node {
        final int x, y, z;
        double g, f, h;
        Node parent;
        boolean closed;
        boolean blocked;

        Node(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private record Entry(Node node, double f) {}

    private static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | ((long) y & 0xFFFL);
    }

    private static final int[][] DIRECTIONS = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}, {0, -1, 0}};

    public static boolean isFrontier(Grid grid, int x, int z) {
        return grid.isChunkUnloaded((x + 1) >> 4, z >> 4)
            || grid.isChunkUnloaded((x - 1) >> 4, z >> 4)
            || grid.isChunkUnloaded(x >> 4, (z + 1) >> 4)
            || grid.isChunkUnloaded(x >> 4, (z - 1) >> 4);
    }

    public static Result plan(Grid grid, int[] start, double destX, double destZ, double arrivalRadius,
                              int levelY, int clearance, int maxNodes, int band) {
        HashMap<Long, Node> nodes = new HashMap<>();
        PriorityQueue<Entry> open = new PriorityQueue<>(
            Comparator.comparingDouble((Entry e) -> e.f).thenComparingDouble(e -> e.node.h));

        Node startNode = new Node(start[0], start[1], start[2]);
        startNode.h = horizontalDistance(startNode, destX, destZ);
        startNode.f = HEURISTIC_WEIGHT * startNode.h;
        nodes.put(key(start[0], start[1], start[2]), startNode);
        open.add(new Entry(startNode, startNode.f));

        int expanded = 0;
        while (!open.isEmpty()) {
            Entry entry = open.poll();
            Node cur = entry.node;
            if (cur.closed || entry.f != cur.f) continue;
            cur.closed = true;

            if (cur.h <= arrivalRadius) {
                return new Result(Status.REACHED_DESTINATION, path(cur), expanded);
            }
            if (cur.parent != null && isFrontier(grid, cur.x, cur.z)) {
                return new Result(Status.REACHED_FRONTIER, path(cur), expanded);
            }

            if (++expanded > maxNodes) {
                return new Result(Status.BEST_EFFORT, path(cur), expanded);
            }

            for (int[] d : DIRECTIONS) {
                int nx = cur.x + d[0], ny = cur.y + d[1], nz = cur.z + d[2];
                if (ny < grid.minY() || ny > grid.maxY()) continue;
                if (band != Integer.MAX_VALUE && Math.abs(ny - levelY) > band && Math.abs(ny - start[1]) > band) continue;

                long k = key(nx, ny, nz);
                Node next = nodes.get(k);
                if (next == null) {
                    next = new Node(nx, ny, nz);
                    next.blocked = grid.isChunkUnloaded(nx >> 4, nz >> 4) || !grid.isPassable(nx, ny, nz, clearance);
                    next.h = horizontalDistance(next, destX, destZ);
                    nodes.put(k, next);
                }
                if (next.blocked || next.closed) continue;

                double g = cur.g + 1 + LEVEL_BIAS * Math.abs(ny - levelY);
                if (next.parent == null || g < next.g) {
                    next.parent = cur;
                    next.g = g;
                    next.f = g + HEURISTIC_WEIGHT * next.h;
                    open.add(new Entry(next, next.f));
                }
            }
        }

        return new Result(Status.SEALED, Collections.emptyList(), expanded);
    }

    public static Result escape(Grid grid, int[] start, int radius, int maxNodes) {
        HashMap<Long, Node> nodes = new HashMap<>();
        PriorityQueue<Entry> open = new PriorityQueue<>(Comparator.comparingDouble((Entry e) -> e.f));

        Node startNode = new Node(start[0], start[1], start[2]);
        nodes.put(key(start[0], start[1], start[2]), startNode);
        open.add(new Entry(startNode, 0));

        int expanded = 0;
        while (!open.isEmpty()) {
            Entry entry = open.poll();
            Node cur = entry.node;
            if (cur.closed || entry.f != cur.f) continue;
            cur.closed = true;

            if (cur.parent != null && cur.h == 0 && freeNeighbours(grid, cur) >= 2) {
                return new Result(Status.ESCAPING, path(cur), expanded);
            }
            if (++expanded > maxNodes) break;

            for (int[] d : DIRECTIONS) {
                int nx = cur.x + d[0], ny = cur.y + d[1], nz = cur.z + d[2];
                if (ny < grid.minY() || ny > grid.maxY()) continue;
                if (Math.abs(nx - start[0]) > radius || Math.abs(ny - start[1]) > radius || Math.abs(nz - start[2]) > radius) continue;

                long k = key(nx, ny, nz);
                Node next = nodes.get(k);
                if (next == null) {
                    next = new Node(nx, ny, nz);
                    int cost = grid.escapeCost(nx, ny, nz);
                    next.blocked = cost < 0;
                    next.h = Math.max(cost, 0);
                    nodes.put(k, next);
                }
                if (next.blocked || next.closed) continue;

                double g = cur.g + 1 + next.h;
                if (next.parent == null || g < next.g) {
                    next.parent = cur;
                    next.g = g;
                    next.f = g;
                    open.add(new Entry(next, g));
                }
            }
        }

        return new Result(Status.SEALED, Collections.emptyList(), expanded);
    }

    private static int freeNeighbours(Grid grid, Node n) {
        int free = 0;
        for (int[] d : DIRECTIONS) {
            if (grid.escapeCost(n.x + d[0], n.y + d[1], n.z + d[2]) == 0) free++;
        }
        return free;
    }

    public static final int NEAR_BAND = 12;

    public static Result planTwoPass(Grid grid, int[] start, double destX, double destZ, double arrivalRadius,
                                     int levelY, int clearance, int maxNodes) {
        int firstBudget = Math.max(1, maxNodes * 2 / 3);
        Result near = plan(grid, start, destX, destZ, arrivalRadius, levelY, clearance, firstBudget, NEAR_BAND);
        if (near.status == Status.REACHED_DESTINATION || near.status == Status.REACHED_FRONTIER) return near;

        Result full = plan(grid, start, destX, destZ, arrivalRadius, levelY, clearance, Math.max(1, maxNodes - near.expanded), Integer.MAX_VALUE);
        if (full.status == Status.SEALED && near.status == Status.BEST_EFFORT) return near;
        return full;
    }

    private static double horizontalDistance(Node n, double destX, double destZ) {
        return Math.hypot(n.x + 0.5 - destX, n.z + 0.5 - destZ);
    }

    private static List<int[]> path(Node end) {
        ArrayList<int[]> cells = new ArrayList<>();
        for (Node n = end; n != null; n = n.parent) cells.add(new int[]{n.x, n.y, n.z});
        Collections.reverse(cells);
        return cells;
    }

    public static List<int[]> smooth(Grid grid, List<int[]> cells, int lookahead) {
        if (cells.size() <= 2) return cells;

        ArrayList<int[]> out = new ArrayList<>();
        int i = 0;
        out.add(cells.getFirst());
        while (i < cells.size() - 1) {
            int j = Math.min(cells.size() - 1, i + lookahead);
            while (j > i + 1) {
                int[] a = cells.get(i), b = cells.get(j);
                if (grid.lineClear(a[0], a[1], a[2], b[0], b[1], b[2])) break;
                j--;
            }
            out.add(cells.get(j));
            i = j;
        }
        return out;
    }
}
