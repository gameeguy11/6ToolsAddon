package gamerguy11.sixtoolsaddon.modules.autowasp;

import gamerguy11.sixtoolsaddon.modules.SixToolsAutoWasp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class AutoWaspPathing {
    private AutoWaspPathing() {}

    public static final class AutoWaspPathfinder {
        private static final int CACHE_SWEEP_TICKS = 1;
        private static final int MAX_CACHE_ENTRIES = 20000;
        private static final double WAYPOINT_REACH = 2.0;
        private static final double TARGET_REPATH_DISTANCE_SQ = 6.25;
        private static final double FLOOR_CLEARANCE = 2.2;
        private static final double CEILING_CLEARANCE = 1.5;
        private static final int SAFETY_SCAN = 14;
        private static final double COLLISION_STEP = 0.55;
        private static final double NETHER_MAX_FLIGHT_Y = 123.0;

        private final SixToolsAutoWasp module;
        private final AutoWaspPathSearch search;
        private final Map<ClearanceKey, Boolean> clearanceCache = new HashMap<>();
        private final Map<ColumnKey, Double> floorCache = new HashMap<>();
        private final Map<ColumnKey, Double> ceilingCache = new HashMap<>();

        private List<Vec3d> currentPath = new ArrayList<>();
        private int currentWaypointIndex;
        private int pathTimer;
        private Vec3d lastPathTarget;
        private int cacheSweepTicks;

        public AutoWaspPathfinder(SixToolsAutoWasp module) {
            this.module = module;
            this.search = new AutoWaspPathSearch(module, this);
        }

        public void onDeactivate() {
            currentPath.clear();
            currentWaypointIndex = 0;
            pathTimer = 0;
            lastPathTarget = null;
            cacheSweepTicks = 0;
            clearCaches();
        }

        public void resetNavigationState() {
            currentPath.clear();
            currentWaypointIndex = 0;
            pathTimer = 0;
            lastPathTarget = null;
            cacheSweepTicks = 0;
            clearCaches();
        }

        public void resetPathState() {
            currentPath.clear();
            currentWaypointIndex = 0;
            pathTimer = 0;
            lastPathTarget = null;
        }

        public void clearTargetState() {
            resetPathState();
        }

        public void tickCaches() {
            if (++cacheSweepTicks >= CACHE_SWEEP_TICKS || shouldTrimCaches()) {
                cacheSweepTicks = 0;
                clearCaches();
            }
        }

        public void tickPathing(Vec3d playerPos, Vec3d targetPos, int stuckTicks) {
            if (playerPos == null || targetPos == null) return;

            pathTimer++;
            boolean targetMovedFar = lastPathTarget == null || lastPathTarget.squaredDistanceTo(targetPos) > TARGET_REPATH_DISTANCE_SQ;
            boolean pathBlocked = currentWaypointIndex < currentPath.size() && !isDirectPathClear(playerPos, currentPath.get(currentWaypointIndex));
            boolean pathDepleted = currentPath.isEmpty() || currentWaypointIndex >= currentPath.size();
            boolean needsRepath = (pathDepleted && (lastPathTarget == null || pathTimer >= AutoWaspPathSearch.PATH_UPDATE_TICKS))
                || (targetMovedFar && pathTimer >= AutoWaspPathSearch.PATH_UPDATE_TICKS)
                || pathTimer >= AutoWaspPathSearch.PATH_FORCE_REFRESH_TICKS
                || (stuckTicks >= AutoWaspFlightController.STUCK_REPATH_TICKS && pathTimer >= AutoWaspPathSearch.PATH_UPDATE_TICKS)
                || (pathBlocked && pathTimer >= AutoWaspPathSearch.PATH_UPDATE_TICKS);

            if (needsRepath) {
                pathTimer = 0;
                lastPathTarget = targetPos;
                currentPath = search.computePath(playerPos, targetPos);
                currentWaypointIndex = 0;
            }

            advanceWaypoints(playerPos);
        }

        public Vec3d steerTarget(Vec3d fallback) {
            Vec3d playerPos = module.client().player.getEntityPos();
            if (isDirectPathClear(playerPos, fallback)) return fallback;
            if (currentWaypointIndex < currentPath.size()) {
                Vec3d waypoint = currentPath.get(currentWaypointIndex);
                if (isDirectPathClear(playerPos, waypoint)) return waypoint;
            }
            return playerPos;
        }

        public Vec3d adjustToSafeCorridor(Vec3d desired, Vec3d fallbackRef) {
            desired = clampToDimensionFlightCap(desired);
            double floorY = nearestSolidBelow(desired, SAFETY_SCAN);
            double ceilingY = nearestSolidAbove(desired, SAFETY_SCAN);

            double minY = floorY + FLOOR_CLEARANCE;
            double maxY = ceilingY - playerHeight() - CEILING_CLEARANCE;
            if (hasNetherFlightCap()) maxY = Math.min(maxY, NETHER_MAX_FLIGHT_Y);
            double y = desired.y;

            if (minY <= maxY) {
                y = MathHelper.clamp(y, minY, maxY);
            } else {
                double fallback = fallbackRef != null ? fallbackRef.y : y;
                y = Math.max(floorY + 1.0, Math.min(ceilingY - playerHeight() - 0.25, fallback));
            }

            Vec3d candidate = new Vec3d(desired.x, y, desired.z);
            if (hasPlayerClearance(candidate)) return candidate;

            for (int i = 1; i <= 12; i++) {
                double shift = i * 0.35;
                Vec3d up = candidate.add(0, shift, 0);
                if (hasPlayerClearance(up)) return up;

                Vec3d down = candidate.add(0, -shift, 0);
                if (hasPlayerClearance(down)) return down;
            }

            return candidate;
        }

        public boolean isDirectPathClear(Vec3d start, Vec3d goal) {
            return isDirectPathClear(start, goal, this::hasPlayerClearance);
        }

        static boolean isDirectPathClear(Vec3d start, Vec3d goal, java.util.function.Predicate<Vec3d> clear) {
            Vec3d diff = goal.subtract(start);
            double distance = diff.length();
            if (!Double.isFinite(distance)) return false;
            int steps = Math.max(1, (int) Math.ceil(distance / COLLISION_STEP));
            for (int step = 0; step <= steps; step++) {
                if (!clear.test(start.add(diff.multiply((double) step / steps)))) return false;
            }
            return true;
        }

        public boolean hasPlayerClearance(Vec3d center) {
            if (module.client().world == null) return false;
            if (hasNetherFlightCap() && center.y > NETHER_MAX_FLIGHT_Y) return false;

            double halfWidth = 0.35;
            double height = playerHeight();
            int minX = MathHelper.floor(center.x - halfWidth);
            int maxX = MathHelper.floor(center.x + halfWidth);
            int minY = MathHelper.floor(center.y);
            int maxY = MathHelper.floor(center.y + height);
            int minZ = MathHelper.floor(center.z - halfWidth);
            int maxZ = MathHelper.floor(center.z + halfWidth);
            ClearanceKey key = new ClearanceKey(minX, minY, minZ, maxX, maxY, maxZ);
            Boolean cached = clearanceCache.get(key);
            if (cached != null) return cached;
            BlockPos.Mutable mutable = new BlockPos.Mutable();

            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    if (module.client().world.isOutOfHeightLimit(y)) {
                        clearanceCache.put(key, false);
                        return false;
                    }
                    for (int z = minZ; z <= maxZ; z++) {
                        mutable.set(x, y, z);
                        if (!module.client().world.isChunkLoaded(mutable)
                            || !module.client().world.getBlockState(mutable).getCollisionShape(module.client().world, mutable).isEmpty()) {
                            clearanceCache.put(key, false);
                            return false;
                        }
                    }
                }
            }

            clearanceCache.put(key, true);
            return true;
        }

        public boolean isSolid(BlockPos pos) {
            if (module.client().world == null || module.client().world.isOutOfHeightLimit(pos.getY())
                || !module.client().world.isChunkLoaded(pos)) return true;
            return !module.client().world.getBlockState(pos).getCollisionShape(module.client().world, pos).isEmpty();
        }

        public double distanceToSolidBelow(Vec3d pos, int maxDepth) {
            return pos.y - nearestSolidBelow(pos, maxDepth);
        }

        public double distanceToSolidAbove(Vec3d pos, int maxHeight) {
            return nearestSolidAbove(pos, maxHeight) - (pos.y + playerHeight());
        }

        public int countLateralOpenings(Vec3d center, double distance) {
            int open = 0;
            for (Direction direction : AutoWaspPathSearch.CARDINAL_DIRECTIONS) {
                Vec3d sample = center.add(direction.getOffsetX() * distance, 0, direction.getOffsetZ() * distance);
                if (hasPlayerClearance(sample)) open++;
            }
            return open;
        }

        public boolean hasNetherFlightCap() {
            return module.client().world != null && module.client().world.getRegistryKey() == net.minecraft.world.World.NETHER;
        }

        public Vec3d clampToDimensionFlightCap(Vec3d pos) {
            if (!hasNetherFlightCap() || pos.y <= NETHER_MAX_FLIGHT_Y) return pos;
            return new Vec3d(pos.x, NETHER_MAX_FLIGHT_Y, pos.z);
        }

        BlockPos clampToDimensionFlightCap(BlockPos pos) {
            if (!hasNetherFlightCap() || pos.getY() <= (int) NETHER_MAX_FLIGHT_Y) return pos;
            return new BlockPos(pos.getX(), (int) NETHER_MAX_FLIGHT_Y, pos.getZ());
        }

        double playerHeight() {
            return 1.8;
        }

        private void advanceWaypoints(Vec3d playerPos) {
            double reachSq = WAYPOINT_REACH * WAYPOINT_REACH;
            while (currentWaypointIndex < currentPath.size()) {
                if (playerPos.squaredDistanceTo(currentPath.get(currentWaypointIndex)) > reachSq) break;
                int next = currentWaypointIndex + 1;
                if (next < currentPath.size() && !isDirectPathClear(playerPos, currentPath.get(next))) break;
                currentWaypointIndex++;
            }
        }

        private void clearCaches() {
            clearanceCache.clear();
            floorCache.clear();
            ceilingCache.clear();
        }

        private record ClearanceKey(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {}

        private record ColumnKey(int x, int y, int z, int range) {}

        private boolean shouldTrimCaches() {
            return clearanceCache.size() > MAX_CACHE_ENTRIES
                || floorCache.size() > MAX_CACHE_ENTRIES
                || ceilingCache.size() > MAX_CACHE_ENTRIES;
        }

        private double nearestSolidBelow(Vec3d pos, int maxDepth) {
            int x = MathHelper.floor(pos.x);
            int z = MathHelper.floor(pos.z);
            int yStart = MathHelper.floor(pos.y);
            Double surface = floorCache.get(new ColumnKey(x, yStart, z, maxDepth));
            if (surface == null) {
                surface = scanBelow(x, yStart, z, maxDepth);
                floorCache.put(new ColumnKey(x, yStart, z, maxDepth), surface);
            }
            return Double.isNaN(surface) ? pos.y - maxDepth : surface;
        }

        private double scanBelow(int x, int yStart, int z, int maxDepth) {
            BlockPos.Mutable mutable = new BlockPos.Mutable(x, yStart, z);
            for (int i = 0; i <= maxDepth; i++) {
                int y = yStart - i;
                if (module.client().world.isOutOfHeightLimit(y)) return y;
                mutable.set(x, y, z);
                if (isSolid(mutable)) return y + 1.0;
            }
            return Double.NaN;
        }

        private double nearestSolidAbove(Vec3d pos, int maxHeight) {
            int x = MathHelper.floor(pos.x);
            int z = MathHelper.floor(pos.z);
            int yStart = MathHelper.floor(pos.y + playerHeight());
            Double surface = ceilingCache.get(new ColumnKey(x, yStart, z, maxHeight));
            if (surface == null) {
                surface = scanAbove(x, yStart, z, maxHeight);
                ceilingCache.put(new ColumnKey(x, yStart, z, maxHeight), surface);
            }
            return Double.isNaN(surface) ? pos.y + maxHeight : surface;
        }

        private double scanAbove(int x, int yStart, int z, int maxHeight) {
            BlockPos.Mutable mutable = new BlockPos.Mutable(x, yStart, z);
            for (int i = 0; i <= maxHeight; i++) {
                int y = yStart + i;
                if (module.client().world.isOutOfHeightLimit(y)) return y;
                mutable.set(x, y, z);
                if (isSolid(mutable)) return y;
            }
            return Double.NaN;
        }
    }

    public static final class AutoWaspPathSearch {
        static final Direction[] CARDINAL_DIRECTIONS = {
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
        };
        static final int PATH_UPDATE_TICKS = 14;
        static final int PATH_FORCE_REFRESH_TICKS = 32;

        private static final NeighborStep[] NEIGHBOR_STEPS = createNeighborSteps();
        private static final int PATH_MAX_NODES = 3500;
        private static final int PATH_RESOLUTION = 1;
        private static final double FLOOR_CLEARANCE = 2.2;
        private static final double CEILING_CLEARANCE = 1.5;
        private static final int SAFETY_SCAN = 14;
        private static final double NETHER_MAX_FLIGHT_Y = 123.0;
        static final long SEARCH_BUDGET_NANOS = 4_000_000L;
        private static final int BUDGET_CHECK_NODES = 32;

        private final SixToolsAutoWasp module;
        private final AutoWaspPathfinder pathfinder;
        private final Map<Long, Boolean> flyableNodes = new HashMap<>();
        private final Map<Long, Double> nodePenalties = new HashMap<>();
        private long deadline;

        AutoWaspPathSearch(SixToolsAutoWasp module, AutoWaspPathfinder pathfinder) {
            this.module = module;
            this.pathfinder = pathfinder;
        }

        List<Vec3d> computePath(Vec3d start, Vec3d goal) {
            flyableNodes.clear();
            nodePenalties.clear();
            deadline = System.nanoTime() + SEARCH_BUDGET_NANOS;
            try {
                return search(start, goal);
            } finally {
                flyableNodes.clear();
                nodePenalties.clear();
            }
        }

        private List<Vec3d> search(Vec3d start, Vec3d goal) {
            Vec3d clampedStart = pathfinder.clampToDimensionFlightCap(start);
            Vec3d clampedGoal = pathfinder.clampToDimensionFlightCap(goal);
            BlockPos startBlock = pathfinder.clampToDimensionFlightCap(toGrid(BlockPos.ofFloored(clampedStart), PATH_RESOLUTION));
            BlockPos goalBlock = pathfinder.clampToDimensionFlightCap(toGrid(BlockPos.ofFloored(clampedGoal), PATH_RESOLUTION));

            startBlock = findNearestPassable(startBlock, PATH_RESOLUTION, 10);
            goalBlock = findNearestPassable(goalBlock, PATH_RESOLUTION, 10);
            if (startBlock == null || goalBlock == null) return new ArrayList<>();

            Vec3d safeGoal = pathfinder.adjustToSafeCorridor(clampedGoal, clampedStart);
            if (pathfinder.isDirectPathClear(clampedStart, safeGoal)) {
                ArrayList<Vec3d> direct = new ArrayList<>();
                direct.add(safeGoal);
                return direct;
            }

            SearchResult bestResult = null;
            for (int attempt = 0; attempt < 2; attempt++) {
                if (attempt > 0 && outOfTime()) break;
                SearchResult result = searchPath(startBlock, goalBlock, safeGoal, createSearchBounds(startBlock, goalBlock, attempt));
                if (result.path != null && result.complete) return result.path;
                if (bestResult == null || result.bestGoalDistance < bestResult.bestGoalDistance) bestResult = result;
            }

            return bestResult != null && bestResult.path != null ? bestResult.path : new ArrayList<>();
        }

        private boolean outOfTime() {
            return System.nanoTime() - deadline > 0;
        }

        private double nodePenalty(BlockPos node) {
            Double known = nodePenalties.get(node.asLong());
            if (known != null) return known;
            double penalty = computeNodePenalty(node);
            nodePenalties.put(node.asLong(), penalty);
            return penalty;
        }

        private double computeNodePenalty(BlockPos node) {
            Vec3d center = Vec3d.ofCenter(node);
            double floorDist = pathfinder.distanceToSolidBelow(center, SAFETY_SCAN);
            double ceilingDist = pathfinder.distanceToSolidAbove(center, SAFETY_SCAN);
            int nearOpen = pathfinder.countLateralOpenings(center, 0.9);
            int farOpen = pathfinder.countLateralOpenings(center, 1.6);

            double penalty = 0;
            if (floorDist < FLOOR_CLEARANCE) penalty += (FLOOR_CLEARANCE - floorDist) * 2.8;
            if (ceilingDist < CEILING_CLEARANCE) penalty += (CEILING_CLEARANCE - ceilingDist) * 2.8;
            if (floorDist > 10) penalty += 0.35;
            if (nearOpen <= 1) penalty += 2.2;
            if (farOpen <= 1) penalty += 1.4;
            return penalty + narrowSpacePenalty(center);
        }

        private double narrowSpacePenalty(Vec3d center) {
            int openSides = pathfinder.countLateralOpenings(center, 1.0);
            return Math.max(0, 4 - openSides) * 0.5;
        }

        private double turnPenalty(AStarNode current, BlockPos neighbor) {
            if (current.parent == null) return 0;

            int ax = current.pos.getX() - current.parent.pos.getX();
            int ay = current.pos.getY() - current.parent.pos.getY();
            int az = current.pos.getZ() - current.parent.pos.getZ();
            int bx = neighbor.getX() - current.pos.getX();
            int by = neighbor.getY() - current.pos.getY();
            int bz = neighbor.getZ() - current.pos.getZ();

            double aLen = Math.sqrt(ax * ax + ay * ay + az * az);
            double bLen = Math.sqrt(bx * bx + by * by + bz * bz);
            if (aLen < 1.0E-6 || bLen < 1.0E-6) return 0;

            double dot = (ax * bx + ay * by + az * bz) / (aLen * bLen);
            return (1.0 - dot) * 0.2;
        }

        private List<Vec3d> reconstructPath(AStarNode endNode, Vec3d finalGoal) {
            List<Vec3d> path = new ArrayList<>();
            AStarNode node = endNode;
            while (node != null) {
                path.add(pathfinder.adjustToSafeCorridor(Vec3d.ofCenter(node.pos), module.client().player != null ? module.client().player.getEntityPos() : null));
                node = node.parent;
            }

            java.util.Collections.reverse(path);
            if (!path.isEmpty() && module.client().player != null) {
                double reachSq = 4.0;
                if (module.client().player.getEntityPos().squaredDistanceTo(path.get(0)) <= reachSq) path.remove(0);
            }

            Vec3d safeGoal = pathfinder.adjustToSafeCorridor(finalGoal, module.client().player != null ? module.client().player.getEntityPos() : null);
            appendReachableGoal(path, safeGoal, pathfinder::isDirectPathClear);
            return simplifyPath(path);
        }

        static void appendReachableGoal(List<Vec3d> path, Vec3d goal,
                java.util.function.BiPredicate<Vec3d, Vec3d> clear) {
            if (path.isEmpty()) return;
            Vec3d last = path.get(path.size() - 1);
            if (last.squaredDistanceTo(goal) > 0.5 && clear.test(last, goal)) path.add(goal);
        }

        private List<Vec3d> simplifyPath(List<Vec3d> path) {
            if (path.size() <= 2) return path;

            List<Vec3d> simplified = new ArrayList<>();
            int index = 0;
            simplified.add(path.get(0));
            while (index < path.size() - 1) {
                int farthest = index + 1;
                for (int j = path.size() - 1; j > index + 1; j--) {
                    if (pathfinder.isDirectPathClear(path.get(index), path.get(j))) {
                        farthest = j;
                        break;
                    }
                }
                simplified.add(path.get(farthest));
                index = farthest;
            }

            return simplified;
        }

        private BlockPos toGrid(BlockPos pos, int resolution) {
            if (resolution <= 1) return pos;
            return new BlockPos(
                Math.floorDiv(pos.getX(), resolution) * resolution,
                Math.floorDiv(pos.getY(), resolution) * resolution,
                Math.floorDiv(pos.getZ(), resolution) * resolution
            );
        }

        private double heuristic(BlockPos a, BlockPos b) {
            double dx = a.getX() - b.getX();
            double dy = a.getY() - b.getY();
            double dz = a.getZ() - b.getZ();
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        private BlockPos findNearestPassable(BlockPos origin, int resolution, int maxRadius) {
            if (isNodeFlyable(origin)) return origin;

            for (int radius = 1; radius <= maxRadius; radius++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dy = -radius; dy <= radius; dy++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            if (Math.abs(dx) != radius && Math.abs(dy) != radius && Math.abs(dz) != radius) continue;

                            BlockPos candidate = origin.add(dx * resolution, dy * resolution, dz * resolution);
                            if (isNodeFlyable(candidate)) return candidate;
                        }
                    }
                }
            }

            return null;
        }

        private boolean isNodeFlyable(BlockPos center) {
            Boolean known = flyableNodes.get(center.asLong());
            if (known != null) return known;
            boolean flyable = computeNodeFlyable(center);
            flyableNodes.put(center.asLong(), flyable);
            return flyable;
        }

        private boolean computeNodeFlyable(BlockPos center) {
            if (pathfinder.hasNetherFlightCap() && center.getY() > (int) NETHER_MAX_FLIGHT_Y) return false;
            Vec3d sample = Vec3d.ofCenter(center);
            if (!pathfinder.hasPlayerClearance(sample)) return false;

            double floorDist = pathfinder.distanceToSolidBelow(sample, Math.max(6, SAFETY_SCAN / 2));
            double ceilingDist = pathfinder.distanceToSolidAbove(sample, Math.max(6, SAFETY_SCAN / 2));
            if (floorDist <= 0.1 || ceilingDist <= 0.1) return false;
            return pathfinder.countLateralOpenings(sample, 0.9) > 0;
        }

        private boolean isEdgeFlyable(BlockPos from, BlockPos to) {
            return pathfinder.isDirectPathClear(Vec3d.ofCenter(from), Vec3d.ofCenter(to));
        }

        private SearchResult searchPath(BlockPos startBlock, BlockPos goalBlock, Vec3d safeGoal, SearchBounds bounds) {
            Map<Long, AStarNode> openMap = new HashMap<>();
            Set<Long> closedSet = new HashSet<>();
            PriorityQueue<AStarNode> openQueue = new PriorityQueue<>(Comparator.comparingDouble(node -> node.f));

            AStarNode startNode = new AStarNode(startBlock, null, 0, heuristic(startBlock, goalBlock));
            openMap.put(startBlock.asLong(), startNode);
            openQueue.add(startNode);

            AStarNode bestNode = startNode;
            double bestDistance = heuristic(startBlock, goalBlock);
            int nodesExplored = 0;

            while (!openQueue.isEmpty() && nodesExplored < PATH_MAX_NODES) {
                if (nodesExplored % BUDGET_CHECK_NODES == 0 && nodesExplored > 0 && outOfTime()) break;
                AStarNode current = openQueue.poll();
                long currentKey = current.pos.asLong();
                if (closedSet.contains(currentKey)) continue;

                closedSet.add(currentKey);
                openMap.remove(currentKey);
                nodesExplored++;

                double currentDistance = heuristic(current.pos, goalBlock);
                if (currentDistance < bestDistance) {
                    bestDistance = currentDistance;
                    bestNode = current;
                }

                if (current.pos.getManhattanDistance(goalBlock) <= PATH_RESOLUTION) {
                    return new SearchResult(reconstructPath(current, safeGoal), true, currentDistance);
                }

                for (NeighborStep step : NEIGHBOR_STEPS) {
                    BlockPos neighbor = current.pos.add(step.dx, step.dy, step.dz);
                    if (!bounds.contains(neighbor)) continue;

                    long neighborKey = neighbor.asLong();
                    if (closedSet.contains(neighborKey)) continue;
                    if (!isNodeFlyable(neighbor)) continue;
                    if (!isEdgeFlyable(current.pos, neighbor)) continue;

                    double g = current.g + step.cost + nodePenalty(neighbor) + turnPenalty(current, neighbor) + Math.abs(step.dy) * 0.18;
                    AStarNode old = openMap.get(neighborKey);
                    if (old != null && old.g <= g) continue;

                    double h = heuristic(neighbor, goalBlock);
                    AStarNode next = new AStarNode(neighbor, current, g, g + h);
                    openMap.put(neighborKey, next);
                    openQueue.add(next);
                }
            }

            List<Vec3d> fallbackPath = bestNode != startNode ? reconstructPath(bestNode, safeGoal) : null;
            return new SearchResult(fallbackPath, false, bestDistance);
        }

        private SearchBounds createSearchBounds(BlockPos start, BlockPos goal, int attempt) {
            int dx = Math.abs(start.getX() - goal.getX());
            int dy = Math.abs(start.getY() - goal.getY());
            int dz = Math.abs(start.getZ() - goal.getZ());
            int horizontalSpan = Math.max(dx, dz);

            int horizontalMargin = attempt == 0
                ? MathHelper.clamp(horizontalSpan / 3 + 8, 8, 20)
                : MathHelper.clamp(horizontalSpan / 2 + 16, 18, 40);
            int verticalMargin = attempt == 0
                ? MathHelper.clamp(dy + 8, 8, 18)
                : MathHelper.clamp(dy + 14, 14, 32);

            int minX = Math.min(start.getX(), goal.getX()) - horizontalMargin;
            int maxX = Math.max(start.getX(), goal.getX()) + horizontalMargin;
            int minY = Math.min(start.getY(), goal.getY()) - verticalMargin;
            int maxY = Math.max(start.getY(), goal.getY()) + verticalMargin;
            int minZ = Math.min(start.getZ(), goal.getZ()) - horizontalMargin;
            int maxZ = Math.max(start.getZ(), goal.getZ()) + horizontalMargin;

            if (pathfinder.hasNetherFlightCap()) maxY = Math.min(maxY, (int) NETHER_MAX_FLIGHT_Y);
            return new SearchBounds(minX, maxX, minY, maxY, minZ, maxZ);
        }

        private static NeighborStep[] createNeighborSteps() {
            List<NeighborStep> steps = new ArrayList<>(26);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        steps.add(new NeighborStep(dx, dy, dz, Math.sqrt(dx * dx + dy * dy + dz * dz)));
                    }
                }
            }
            return steps.toArray(new NeighborStep[0]);
        }

        private static final class AStarNode {
            final BlockPos pos;
            final AStarNode parent;
            final double g;
            final double f;

            AStarNode(BlockPos pos, AStarNode parent, double g, double f) {
                this.pos = pos;
                this.parent = parent;
                this.g = g;
                this.f = f;
            }
        }

        private record NeighborStep(int dx, int dy, int dz, double cost) {
        }

        private record SearchBounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
            boolean contains(BlockPos pos) {
                return pos.getX() >= minX && pos.getX() <= maxX
                    && pos.getY() >= minY && pos.getY() <= maxY
                    && pos.getZ() >= minZ && pos.getZ() <= maxZ;
            }
        }

        private record SearchResult(List<Vec3d> path, boolean complete, double bestGoalDistance) {
        }
    }
}
