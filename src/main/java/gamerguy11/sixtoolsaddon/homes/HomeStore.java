package gamerguy11.sixtoolsaddon.homes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import gamerguy11.sixtoolsaddon.SixToolsAddon;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.world.Dimension;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class HomeStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<LinkedHashMap<String, Home>>() {}.getType();
    private static final File FILE = new File(MeteorClient.FOLDER, "sixtoolsaddon-homes.json");

    private static final File SETTINGS_FILE = new File(MeteorClient.FOLDER, "sixtoolsaddon-homes-radius.txt");

    private static Map<String, Home> cache;
    private static int defaultRadius = -1;

    public static int defaultRadius() {
        if (defaultRadius > 0) return defaultRadius;

        defaultRadius = 50;
        try {
            if (SETTINGS_FILE.exists()) {
                int v = Integer.parseInt(Files.readString(SETTINGS_FILE.toPath()).trim());
                if (v > 0) defaultRadius = v;
            }
        } catch (IOException | NumberFormatException e) {
            SixToolsAddon.LOG.error("Failed to read default home radius", e);
        }
        return defaultRadius;
    }

    public static void setDefaultRadius(int radius) {
        if (radius < 1) return;
        defaultRadius = radius;
        try {
            Files.writeString(SETTINGS_FILE.toPath(), Integer.toString(radius));
        } catch (IOException e) {
            SixToolsAddon.LOG.error("Failed to save default home radius", e);
        }
    }

    public static void applyRadiusToAll(int radius) {
        for (Home home : all().values()) home.radius = radius;
        write();
    }

    public static Map<String, Home> all() {
        if (cache != null) return cache;

        cache = new LinkedHashMap<>();
        if (!FILE.exists()) return cache;

        try {
            String content = Files.readString(FILE.toPath()).trim();
            if (content.isEmpty()) return cache;
            Map<String, Home> read = GSON.fromJson(content, TYPE);
            if (read != null) cache = read;
        } catch (IOException | JsonParseException e) {
            SixToolsAddon.LOG.error("Failed to read homes", e);
        }
        return cache;
    }

    public static void save(String id, Home home) {
        all().put(id, home);
        write();
    }

    public static void remove(String id) {
        all().remove(id);
        write();
    }

    private static void write() {
        try {
            Files.writeString(FILE.toPath(), GSON.toJson(all(), TYPE));
        } catch (IOException e) {
            SixToolsAddon.LOG.error("Failed to save homes", e);
        }
    }

    public static Home protectedHomeAtPlayer() {
        return homeAtPlayer(true);
    }

    public static Home anyHomeAtPlayer() {
        return homeAtPlayer(false);
    }

    private static Home homeAtPlayer(boolean protectedOnly) {
        if (mc.player == null || all().isEmpty()) return null;

        Dimension here = PlayerUtils.getDimension();
        int px = mc.player.getBlockX();
        int pz = mc.player.getBlockZ();

        for (Home home : all().values()) {
            if (protectedOnly && !home.protect) continue;

            long[] p = convert(px, pz, here, home.dimension);
            if (p == null) continue;

            long dx = p[0] - home.x;
            long dz = p[1] - home.z;
            if (dx * dx + dz * dz <= (long) home.radius * home.radius) return home;
        }
        return null;
    }

    private static long[] convert(int x, int z, Dimension from, Dimension to) {
        if (from == to) return new long[]{x, z};
        if (from == Dimension.Nether && to == Dimension.Overworld) return new long[]{x * 8L, z * 8L};
        if (from == Dimension.Overworld && to == Dimension.Nether) return new long[]{Math.floorDiv(x, 8), Math.floorDiv(z, 8)};
        return null;
    }
}
