package com.maparts.link.capture;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import javax.imageio.ImageIO;

import com.maparts.link.util.TitleGuesser;

import net.minecraft.block.MapColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.map.MapState;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Direction;

public final class MapGridCapture {
    private static final int TILE = 128;
    private static final int MAX_GRID = 64;
    private static final double SEARCH_RADIUS = 70.0;

    public record TileInfo(int col, int row, int mapId, String name) {}

    public record Result(
            byte[] png, int mapsWide, int mapsTall, List<TileInfo> tiles, String error) {
        public static Result error(String message) {
            return new Result(null, 0, 0, List.of(), message);
        }

        public static Result success(
                byte[] png, int mapsWide, int mapsTall, List<TileInfo> tiles) {
            return new Result(png, mapsWide, mapsTall, tiles, null);
        }

        public boolean failed() {
            return error != null;
        }
    }

    private MapGridCapture() {}

    public static ItemFrameEntity targetedMapFrame(MinecraftClient client) {
        if (client.world == null
                || !(client.crosshairTarget instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof ItemFrameEntity frame)
                || !holdsFilledMap(frame)) {
            return null;
        }

        return frame;
    }

    public static String validateWallFrame(MinecraftClient client, ItemFrameEntity frame) {
        if (frame == null || !holdsFilledMap(frame)) {
            return "Look at an item frame holding a filled map first.";
        }

        if (frame.getHorizontalFacing() == Direction.DOWN) {
            return "Ceiling maparts aren't supported yet, place the maps on a wall or floor.";
        }

        return null;
    }

    public static String validateSameWall(
            ItemFrameEntity first, ItemFrameEntity second) {
        if (first.getHorizontalFacing() != second.getHorizontalFacing()) {
            return "Both corners must be on the same wall or floor (their facing differs).";
        }

        if (!onSamePlane(first, second, first.getHorizontalFacing())) {
            return "Both corners must be at the same depth (wall) or level (floor).";
        }

        return null;
    }

    public static int[] gridSize(ItemFrameEntity first, ItemFrameEntity second) {
        Direction facing = first.getHorizontalFacing();
        Direction right = rightOf(facing);
        int[] cell = cellOf(first, second, facing, right);
        return new int[] {Math.abs(cell[0]) + 1, Math.abs(cell[1]) + 1};
    }

    private static Direction rightOf(Direction facing) {
        return facing == Direction.UP ? Direction.EAST : facing.getOpposite().rotateYClockwise();
    }

    public static void capture(MinecraftClient client, Consumer<Result> callback) {
        ItemFrameEntity start = targetedMapFrame(client);
        String invalid = validateWallFrame(client, start);

        if (invalid != null) {
            callback.accept(Result.error(invalid));
            return;
        }

        Direction facing = start.getHorizontalFacing();
        Direction right = rightOf(facing);
        Map<Long, ItemFrameEntity> byCell =
                collectCells(client, start, facing, right, SEARCH_RADIUS);

        Map<Long, ItemFrameEntity> component = floodFillFrom(byCell, 0, 0);
        int[] bounds = boundsOf(component.keySet());

        awaitMapData(
                client,
                component,
                () ->
                        callback.accept(assemble(
                                client, component, bounds[0], bounds[1], bounds[2], bounds[3])),
                missing -> callback.accept(Result.error(loadTimeoutMessage(missing))));
    }

    public static void captureCorners(
            MinecraftClient client, ItemFrameEntity first, ItemFrameEntity second,
            Consumer<Result> callback) {
        String invalid = validateWallFrame(client, first);

        if (invalid == null) {
            invalid = validateWallFrame(client, second);
        }

        if (invalid == null) {
            invalid = validateSameWall(first, second);
        }

        if (invalid != null) {
            callback.accept(Result.error(invalid));
            return;
        }

        Direction facing = first.getHorizontalFacing();
        Direction right = rightOf(facing);

        double reach = Math.abs(first.getX() - second.getX())
                + Math.abs(first.getY() - second.getY())
                + Math.abs(first.getZ() - second.getZ()) + 2.0;
        Map<Long, ItemFrameEntity> byCell =
                collectCells(client, first, facing, right, reach);

        int[] cornerCell = cellOf(first, second, facing, right);
        int minCol = Math.min(0, cornerCell[0]);
        int maxCol = Math.max(0, cornerCell[0]);
        int minRow = Math.min(0, cornerCell[1]);
        int maxRow = Math.max(0, cornerCell[1]);

        Map<Long, ItemFrameEntity> selected = new HashMap<>();

        for (int col = minCol; col <= maxCol; col++) {
            for (int row = minRow; row <= maxRow; row++) {
                ItemFrameEntity frame = byCell.get(cellKey(col, row));

                if (frame != null) {
                    selected.put(cellKey(col, row), frame);
                }
            }
        }

        awaitMapData(
                client,
                selected,
                () -> callback.accept(assemble(client, selected, minCol, maxCol, minRow, maxRow)),
                missing -> callback.accept(Result.error(loadTimeoutMessage(missing))));
    }

    public record MultiResult(List<Result> segments, int skippedIrregular, String error) {
        public static MultiResult error(String message) {
            return new MultiResult(List.of(), 0, message);
        }

        public boolean failed() {
            return error != null;
        }
    }

    private static final int MAX_SEGMENTS = 12;

    private static final double SEAM_FACTOR = 3.0;
    private static final double SEAM_NOISE_FLOOR = 400.0;
    private static final double UNSET_MISMATCH_DISTANCE = 3 * 255.0 * 255.0;

    private record SegmentCandidate(int minRow, int minCol, Result result) {}

    public static void captureAll(MinecraftClient client, Consumer<MultiResult> callback) {
        ItemFrameEntity start = targetedMapFrame(client);
        String invalid = validateWallFrame(client, start);

        if (invalid != null) {
            callback.accept(MultiResult.error(invalid));
            return;
        }

        Direction facing = start.getHorizontalFacing();
        Direction right = rightOf(facing);
        Map<Long, ItemFrameEntity> byCell =
                collectCells(client, start, facing, right, SEARCH_RADIUS);

        awaitMapData(
                client,
                byCell,
                () -> callback.accept(segmentWall(client, byCell)),
                missing -> callback.accept(MultiResult.error(loadTimeoutMessage(missing))));
    }

    private static String loadTimeoutMessage(int missing) {
        return missing + " map" + (missing == 1 ? "" : "s")
                + " didn't finish loading from the server in time, wait a moment"
                + " (or move closer) and try again.";
    }

    private static void awaitMapData(
            MinecraftClient client,
            Map<Long, ItemFrameEntity> byCell,
            Runnable onReady,
            IntConsumer onTimeout) {
        if (allMapStatesReady(client, byCell)) {
            onReady.run();
            return;
        }

        FrameDataWaiter.waitUntil(
                () -> allMapStatesReady(client, byCell),
                onReady,
                () -> {
                    int missing = 0;

                    for (ItemFrameEntity frame : byCell.values()) {
                        if (mapState(client, frame) == null) {
                            missing++;
                        }
                    }

                    onTimeout.accept(missing);
                });
    }

    private static boolean allMapStatesReady(
            MinecraftClient client, Map<Long, ItemFrameEntity> byCell) {
        for (ItemFrameEntity frame : byCell.values()) {
            if (mapState(client, frame) == null) {
                return false;
            }
        }

        return true;
    }

    private static MultiResult segmentWall(
            MinecraftClient client, Map<Long, ItemFrameEntity> byCell) {
        Map<Long, String> cleanedNames = new HashMap<>();

        for (Map.Entry<Long, ItemFrameEntity> entry : byCell.entrySet()) {
            Text customName = entry.getValue().getHeldItemStack().getCustomName();
            cleanedNames.put(entry.getKey(),
                    TitleGuesser.cleanName(
                            customName != null ? customName.getString() : null));
        }

        Set<Long> visited = new HashSet<>();
        List<Map<Long, ItemFrameEntity>> components = new ArrayList<>();

        for (Long startKey : byCell.keySet()) {
            if (visited.contains(startKey)) {
                continue;
            }

            Map<Long, ItemFrameEntity> component = new HashMap<>();
            ArrayDeque<Long> queue = new ArrayDeque<>();
            queue.add(startKey);
            visited.add(startKey);

            while (!queue.isEmpty()) {
                long key = queue.poll();
                component.put(key, byCell.get(key));
                int col = (int) (key >> 32);
                int row = (int) key;

                int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

                for (int[] offset : offsets) {
                    long neighborKey = cellKey(col + offset[0], row + offset[1]);

                    if (visited.contains(neighborKey)
                            || !byCell.containsKey(neighborKey)) {
                        continue;
                    }

                    boolean forward = offset[0] > 0 || offset[1] > 0;
                    long aKey = forward ? key : neighborKey;
                    long bKey = forward ? neighborKey : key;

                    if (!sameArt(client,
                            byCell.get(aKey), byCell.get(bKey),
                            cleanedNames.get(aKey), cleanedNames.get(bKey),
                            offset[1] == 0)) {
                        continue;
                    }

                    visited.add(neighborKey);
                    queue.add(neighborKey);
                }
            }

            components.add(component);
        }

        List<SegmentCandidate> candidates = new ArrayList<>();
        int skipped = 0;

        for (Map<Long, ItemFrameEntity> component : components) {
            int[] bounds = boundsOf(component.keySet());
            int minCol = bounds[0];
            int maxCol = bounds[1];
            int minRow = bounds[2];
            int maxRow = bounds[3];
            int wide = maxCol - minCol + 1;
            int tall = maxRow - minRow + 1;

            if (component.size() != wide * tall
                    || wide > MAX_GRID || tall > MAX_GRID) {
                skipped++;
                continue;
            }

            Result result = assemble(client, component, minCol, maxCol, minRow, maxRow);

            if (result.failed()) {
                skipped++;
                continue;
            }

            candidates.add(new SegmentCandidate(minRow, minCol, result));
        }

        if (candidates.isEmpty()) {
            return MultiResult.error(
                    "No complete rectangular maparts found on this wall"
                            + (skipped > 0
                                    ? " (" + skipped + " irregular group"
                                            + (skipped == 1 ? "" : "s") + " skipped)"
                                    : "")
                            + ".");
        }

        if (candidates.size() > MAX_SEGMENTS) {
            return MultiResult.error(
                    "Found " + candidates.size() + " maparts, more than the "
                            + MAX_SEGMENTS + " per run limit. Upload sections with"
                            + " /mapselect + /mapupload instead.");
        }

        candidates.sort(Comparator
                .comparingInt(SegmentCandidate::minRow)
                .thenComparingInt(SegmentCandidate::minCol));

        return new MultiResult(
                candidates.stream().map(SegmentCandidate::result).toList(),
                skipped, null);
    }

    private static boolean sameArt(
            MinecraftClient client,
            ItemFrameEntity a, ItemFrameEntity b,
            String nameA, String nameB,
            boolean horizontal) {
        if (nameA != null || nameB != null) {
            return nameA != null && nameB != null && nameA.equalsIgnoreCase(nameB);
        }

        MapState stateA = mapState(client, a);
        MapState stateB = mapState(client, b);

        if (stateA == null || stateB == null) {
            return false;
        }

        return !seamCut(stateA, a.getRotation() % 4,
                stateB, b.getRotation() % 4, horizontal);
    }

    private static boolean seamCut(
            MapState a, int rotA, MapState b, int rotB, boolean horizontal) {
        double seam = 0;
        double internalA = 0;
        double internalB = 0;

        for (int i = 0; i < TILE; i++) {
            int edgeA;
            int innerA;
            int edgeB;
            int innerB;

            if (horizontal) {

                edgeA = rotatedPacked(a, rotA, TILE - 1, i);
                innerA = rotatedPacked(a, rotA, TILE - 2, i);
                edgeB = rotatedPacked(b, rotB, 0, i);
                innerB = rotatedPacked(b, rotB, 1, i);
            } else {

                edgeA = rotatedPacked(a, rotA, i, TILE - 1);
                innerA = rotatedPacked(a, rotA, i, TILE - 2);
                edgeB = rotatedPacked(b, rotB, i, 0);
                innerB = rotatedPacked(b, rotB, i, 1);
            }

            seam += colorDistance(edgeA, edgeB);
            internalA += colorDistance(edgeA, innerA);
            internalB += colorDistance(edgeB, innerB);
        }

        seam /= TILE;
        internalA /= TILE;
        internalB /= TILE;

        double internal = Math.max(Math.max(internalA, internalB), SEAM_NOISE_FLOOR);
        return seam > internal * SEAM_FACTOR;
    }

    private static double colorDistance(int packedA, int packedB) {
        boolean unsetA = packedA >> 2 == 0;
        boolean unsetB = packedB >> 2 == 0;

        if (unsetA || unsetB) {

            return unsetA == unsetB ? 0 : UNSET_MISMATCH_DISTANCE;
        }

        int argbA = MapColor.getRenderColor(packedA);
        int argbB = MapColor.getRenderColor(packedB);
        int dr = ((argbA >> 16) & 0xFF) - ((argbB >> 16) & 0xFF);
        int dg = ((argbA >> 8) & 0xFF) - ((argbB >> 8) & 0xFF);
        int db = (argbA & 0xFF) - (argbB & 0xFF);
        return dr * dr + dg * dg + db * db;
    }

    private static Result assemble(
            MinecraftClient client,
            Map<Long, ItemFrameEntity> cells,
            int minCol, int maxCol, int minRow, int maxRow) {
        int mapsWide = maxCol - minCol + 1;
        int mapsTall = maxRow - minRow + 1;

        if (mapsWide > MAX_GRID || mapsTall > MAX_GRID) {
            return Result.error(
                    "This mapart is larger than " + MAX_GRID + "x" + MAX_GRID
                            + " maps, which is the upload limit.");
        }

        if (cells.size() != mapsWide * mapsTall) {
            return Result.error(
                    "The selection isn't a complete rectangle of maps ("
                            + cells.size() + " maps found for a "
                            + mapsWide + "x" + mapsTall
                            + " grid). Fill the gaps and try again.");
        }

        BufferedImage image = new BufferedImage(
                mapsWide * TILE, mapsTall * TILE, BufferedImage.TYPE_INT_ARGB);
        List<TileInfo> tiles = new ArrayList<>(cells.size());

        for (Map.Entry<Long, ItemFrameEntity> entry : cells.entrySet()) {
            int col = (int) (entry.getKey() >> 32) - minCol;
            int row = (int) entry.getKey().longValue() - minRow;
            ItemFrameEntity frame = entry.getValue();
            MapState state = mapState(client, frame);

            if (state == null) {
                return Result.error("A map unloaded mid-capture. Try again.");
            }

            drawTile(image, col * TILE, row * TILE, state, frame.getRotation() % 4);
            tiles.add(tileInfo(frame, col, row));
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            return Result.error("Encoding the capture as PNG failed.");
        }

        return Result.success(out.toByteArray(), mapsWide, mapsTall, tiles);
    }

    private static TileInfo tileInfo(ItemFrameEntity frame, int col, int row) {
        ItemStack stack = frame.getHeldItemStack();
        MapIdComponent mapId = frame.getMapId(stack);
        Text customName = stack.getCustomName();

        return new TileInfo(
                col,
                row,
                mapId != null ? mapId.id() : -1,
                customName != null ? customName.getString() : null);
    }

    private static boolean holdsFilledMap(ItemFrameEntity frame) {
        return frame.getHeldItemStack().getItem() instanceof FilledMapItem;
    }

    private static Map<Long, ItemFrameEntity> collectCells(
            MinecraftClient client,
            ItemFrameEntity base,
            Direction facing,
            Direction right,
            double radius) {
        List<ItemFrameEntity> candidates = client.world.getEntitiesByClass(
                ItemFrameEntity.class,
                base.getBoundingBox().expand(radius),
                frame -> frame.getHorizontalFacing() == facing
                        && holdsFilledMap(frame)
                        && onSamePlane(base, frame, facing));

        Map<Long, ItemFrameEntity> byCell = new HashMap<>();

        for (ItemFrameEntity frame : candidates) {
            int[] cell = cellOf(base, frame, facing, right);
            byCell.put(cellKey(cell[0], cell[1]), frame);
        }

        return byCell;
    }

    private static Map<Long, ItemFrameEntity> floodFillFrom(
            Map<Long, ItemFrameEntity> byCell, int startCol, int startRow) {
        Map<Long, ItemFrameEntity> component = new HashMap<>();
        ArrayDeque<long[]> queue = new ArrayDeque<>();
        queue.add(new long[] {startCol, startRow});

        while (!queue.isEmpty()) {
            long[] cell = queue.poll();
            int col = (int) cell[0];
            int row = (int) cell[1];
            long key = cellKey(col, row);

            if (component.containsKey(key) || !byCell.containsKey(key)) {
                continue;
            }

            component.put(key, byCell.get(key));
            queue.add(new long[] {col + 1, row});
            queue.add(new long[] {col - 1, row});
            queue.add(new long[] {col, row + 1});
            queue.add(new long[] {col, row - 1});
        }

        return component;
    }

    private static int[] boundsOf(Set<Long> keys) {
        int minCol = Integer.MAX_VALUE;
        int maxCol = Integer.MIN_VALUE;
        int minRow = Integer.MAX_VALUE;
        int maxRow = Integer.MIN_VALUE;

        for (long key : keys) {
            int col = (int) (key >> 32);
            int row = (int) key;
            minCol = Math.min(minCol, col);
            maxCol = Math.max(maxCol, col);
            minRow = Math.min(minRow, row);
            maxRow = Math.max(maxRow, row);
        }

        return new int[] {minCol, maxCol, minRow, maxRow};
    }

    private static int[] cellOf(
            ItemFrameEntity base, ItemFrameEntity frame, Direction facing, Direction right) {
        int col = (int) Math.round(
                (frame.getX() - base.getX()) * right.getOffsetX()
                        + (frame.getZ() - base.getZ()) * right.getOffsetZ());

        if (facing == Direction.UP) {
            Direction down = right.rotateYClockwise();
            int row = (int) Math.round(
                    (frame.getX() - base.getX()) * down.getOffsetX()
                            + (frame.getZ() - base.getZ()) * down.getOffsetZ());
            return new int[] {col, row};
        }

        int row = (int) Math.round(base.getY() - frame.getY());
        return new int[] {col, row};
    }

    private static long cellKey(int col, int row) {
        return ((long) col << 32) | (row & 0xFFFFFFFFL);
    }

    private static boolean onSamePlane(
            ItemFrameEntity base, ItemFrameEntity other, Direction facing) {
        if (facing == Direction.UP) {
            return Math.abs(other.getY() - base.getY()) < 0.01;
        }

        double delta = (other.getX() - base.getX()) * facing.getOffsetX()
                + (other.getZ() - base.getZ()) * facing.getOffsetZ();
        return Math.abs(delta) < 0.01;
    }

    private static MapState mapState(MinecraftClient client, ItemFrameEntity frame) {
        ItemStack stack = frame.getHeldItemStack();

        if (client.world == null || !(stack.getItem() instanceof FilledMapItem)) {
            return null;
        }

        return FilledMapItem.getMapState(stack, client.world);
    }

    private static int rotatedPacked(MapState state, int rotation, int x, int y) {
        int srcX;
        int srcY;

        switch (rotation) {
            case 1 -> {
                srcX = y;
                srcY = TILE - 1 - x;
            }
            case 2 -> {
                srcX = TILE - 1 - x;
                srcY = TILE - 1 - y;
            }
            case 3 -> {
                srcX = TILE - 1 - y;
                srcY = x;
            }
            default -> {
                srcX = x;
                srcY = y;
            }
        }

        return state.colors[srcX + srcY * TILE] & 0xFF;
    }

    private static void drawTile(
            BufferedImage image, int destX, int destY, MapState state, int rotation) {
        for (int y = 0; y < TILE; y++) {
            for (int x = 0; x < TILE; x++) {
                int packed = rotatedPacked(state, rotation, x, y);

                if (packed >> 2 == 0) {

                    continue;
                }

                image.setRGB(destX + x, destY + y, MapColor.getRenderColor(packed));
            }
        }
    }
}
