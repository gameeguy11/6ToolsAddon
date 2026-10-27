package gamerguy11.sixtoolsaddon.basehunting;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StorageBlockListSetting;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

public class BaseHuntStashFinder extends Module {
    private static volatile BlockPos lastFind;

    public static BlockPos pollFind() {
        BlockPos found = lastFind;
        lastFind = null;
        return found;
    }

    public enum Notify { Chat, ChatAndSound, Off }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgFilter = settings.createGroup("Filter");
    private final SettingGroup sgNotify = settings.createGroup("Notify");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<List<BlockEntityType<?>>> storageBlocks = sgGeneral.add(new StorageBlockListSetting.Builder()
        .name("storage-blocks")
        .description("Storage blocks that count towards a stash.")
        .defaultValue(StorageBlockListSetting.STORAGE_BLOCKS)
        .build()
    );

    private final Setting<Integer> minStorage = sgGeneral.add(new IntSetting.Builder()
        .name("minimum-storage")
        .description("Storage blocks needed in a single chunk to count as a stash.")
        .defaultValue(4)
        .range(1, 500)
        .sliderRange(1, 50)
        .build()
    );

    private final Setting<Integer> clusterRadius = sgGeneral.add(new IntSetting.Builder()
        .name("cluster-radius")
        .description("Also adds up neighbouring chunks within this many chunks, so a stash split over chunk borders still counts. 0 disables.")
        .defaultValue(1)
        .range(0, 4)
        .sliderRange(0, 4)
        .build()
    );

    private final Setting<Integer> clusterStorage = sgGeneral.add(new IntSetting.Builder()
        .name("cluster-minimum-storage")
        .description("Storage blocks needed across the whole cluster.")
        .defaultValue(8)
        .range(1, 2000)
        .sliderRange(1, 100)
        .visible(() -> clusterRadius.get() > 0)
        .build()
    );

    private final Setting<Double> minDensity = sgFilter.add(new DoubleSetting.Builder()
        .name("minimum-density")
        .description("Storage blocks per block of the box around them in the chunk. High values only match tightly packed stashes. 0 disables.")
        .defaultValue(0.0)
        .range(0.0, 1.0)
        .sliderRange(0.0, 0.5)
        .build()
    );

    private final Setting<Boolean> ignoreInHomes = sgFilter.add(new BoolSetting.Builder()
        .name("ignore-in-homes")
        .description("Does nothing while you are standing inside one of your Homes.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> maxVolume = sgFilter.add(new IntSetting.Builder()
        .name("maximum-volume")
        .description("Ignores chunks where the storage blocks are spread over a box bigger than this many blocks. 0 disables.")
        .defaultValue(0)
        .range(0, 100000)
        .sliderRange(0, 20000)
        .build()
    );

    private final Setting<Boolean> ignoreSpawnerChunks = sgFilter.add(new BoolSetting.Builder()
        .name("ignore-spawner-chunks")
        .description("Skips chunks with a spawner, which are almost always dungeons, mineshafts or strongholds.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Notify> notify = sgNotify.add(new EnumSetting.Builder<Notify>()
        .name("notify")
        .description("How you are told about a find.")
        .defaultValue(Notify.ChatAndSound)
        .build()
    );

    private final Setting<Boolean> saveWaypoint = sgNotify.add(new BoolSetting.Builder()
        .name("save-to-waypoints")
        .description("Puts a waypoint on the map (Xaero if installed, otherwise Meteor) for every find, even without the Base Hunter.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> saveLog = sgNotify.add(new BoolSetting.Builder()
        .name("save-log")
        .description("Appends every find to 6ToolsAddon/BaseHunting/stashes.csv in the game folder.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> webhookUrl = sgNotify.add(new StringSetting.Builder()
        .name("discord-webhook-url")
        .description("Sends every find to this Discord webhook. Leave empty to disable.")
        .defaultValue("")
        .build()
    );

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Draws a box around the storage of each find.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .defaultValue(ShapeMode.Both)
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
        .name("side-color")
        .defaultValue(new SettingColor(255, 170, 0, 40))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
        .name("line-color")
        .defaultValue(new SettingColor(255, 170, 0, 255))
        .visible(render::get)
        .build()
    );

    private final Map<Long, Integer> counts = new HashMap<>();
    private final Map<Long, Box> finds = new HashMap<>();
    private final Set<Long> alerted = new HashSet<>();

    public BaseHuntStashFinder() {
        super(SixToolsAddon.BASE_HUNTING, "hunt-stash-finder", "Finds chunks with lots of storage blocks while you fly past. Works alongside the Base Hunter.");
    }

    @Override
    public void onDeactivate() {
        counts.clear();
        alerted.clear();
        finds.clear();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        counts.clear();
        alerted.clear();
        finds.clear();
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        WorldChunk chunk = event.chunk();
        if (chunk == null) return;
        if (ignoreInHomes.get() && gamerguy11.sixtoolsaddon.homes.HomeStore.anyHomeAtPlayer() != null) return;

        List<BlockPos> storage = new ArrayList<>();
        boolean spawner = false;
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            if (be instanceof MobSpawnerBlockEntity) spawner = true;
            if (storageBlocks.get().contains(be.getType())) storage.add(be.getPos());
        }

        ChunkPos cp = chunk.getPos();
        long key = cp.toLong();

        if (storage.isEmpty() || (spawner && ignoreSpawnerChunks.get())) {
            counts.remove(key);
            return;
        }

        counts.put(key, storage.size());
        if (alerted.contains(key)) return;

        int own = storage.size();
        int cluster = own;
        int r = clusterRadius.get();
        if (r > 0) {
            cluster = 0;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    cluster += counts.getOrDefault(new ChunkPos(cp.x + dx, cp.z + dz).toLong(), 0);
                }
            }
        }

        boolean hit = own >= minStorage.get() || (r > 0 && cluster >= Math.max(clusterStorage.get(), minStorage.get()));
        if (!hit) return;

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        long sx = 0, sy = 0, sz = 0;
        for (BlockPos p : storage) {
            minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
            minY = Math.min(minY, p.getY()); maxY = Math.max(maxY, p.getY());
            minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
            sx += p.getX(); sy += p.getY(); sz += p.getZ();
        }

        double volume = Math.max(1.0, (maxX - minX + 1.0) * (maxY - minY + 1.0) * (maxZ - minZ + 1.0));
        double density = storage.size() / volume;
        if (minDensity.get() > 0 && density < minDensity.get()) return;
        if (maxVolume.get() > 0 && volume > maxVolume.get()) return;

        alerted.add(key);
        finds.put(key, new Box(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1));

        BlockPos center = new BlockPos((int) (sx / storage.size()), (int) (sy / storage.size()), (int) (sz / storage.size()));
        lastFind = center;
        report(center, own, cluster, density);
    }

    private void report(BlockPos center, int own, int cluster, double density) {
        String dimension = mc.world != null ? mc.world.getRegistryKey().getValue().toString() : "unknown";
        String text = String.format("Stash found at %d %d %d (%s): %d storage blocks in the chunk, %d in the cluster, density %.3f",
            center.getX(), center.getY(), center.getZ(), dimension, own, cluster, density);

        if (notify.get() != Notify.Off) {
            info("%s", text);
            if (notify.get() == Notify.ChatAndSound && mc.player != null) {
                mc.player.playSound(net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.0f, 1.0f);
            }
        }

        if (saveWaypoint.get()) {
            HuntWaypoints.add("Stash " + center.getX() + " " + center.getZ(), center);
        }

        if (saveLog.get()) {
            try {
                Path dir = FabricLoader.getInstance().getGameDir().resolve("6ToolsAddon").resolve("BaseHunting");
                Files.createDirectories(dir);
                String server = mc.getCurrentServerEntry() != null ? mc.getCurrentServerEntry().address : "singleplayer";
                String line = String.format("%s,%s,%d,%d,%d,%d,%d,%.3f%n", server, dimension, center.getX(), center.getY(), center.getZ(), own, cluster, density);
                Files.writeString(dir.resolve("stashes.csv"), line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException ignored) {
               gamerguy11.sixtoolsaddon.utils.Swallowed.log("BaseHuntStashFinder:285", ignored);
            }
        }

        String url = webhookUrl.get();
        if (url != null && !url.isBlank()) {
            List<BaseHuntUtils.Field> fields = new ArrayList<>();
            fields.add(BaseHuntUtils.field("Coordinates", BaseHuntUtils.coords(center.getX(), center.getY(), center.getZ()), false));
            fields.add(BaseHuntUtils.field("In chunk", own, true));
            fields.add(BaseHuntUtils.field("In cluster", cluster, true));
            fields.add(BaseHuntUtils.field("Density", String.format("%.3f", density), true));
            String link = url.trim();
            new Thread(() -> BaseHuntUtils.sendWebhook(link, "Stash Found", null, BaseHuntUtils.COLOR_STASH, fields, null)).start();
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get()) return;
        for (Box box : finds.values()) {
            event.renderer.box(box, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
        }
    }

    @Override
    public String getInfoString() {
        return String.valueOf(finds.size());
    }
}
