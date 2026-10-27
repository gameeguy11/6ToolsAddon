package gamerguy11.sixtoolsaddon.basehunting;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

public class BaseHuntUtils {
    public static final int COLOR_STASH = 0x2ECC71;
    public static final int COLOR_OLD_CHUNK = 0xE67E22;
    public static final int COLOR_TRAIL = 0x9B59B6;
    public static final int COLOR_INFO = 0x3498DB;

    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public record Field(String name, String value, boolean inline) {}

    public static Field field(String name, Object value, boolean inline) {
        return new Field(name, String.valueOf(value), inline);
    }

    public static String coords(int x, int y, int z) {
        return "`" + x + ", " + y + ", " + z + "`";
    }

    public static Vec3d positionInDirection(Vec3d pos, double yaw, double distance)
    {
        Vec3d offset = yawToDirection(yaw).multiply(distance);
        return pos.add(offset);
    }

    public static Vec3d yawToDirection(double yaw)
    {
        yaw = yaw * Math.PI / 180;
        double x = -Math.sin(yaw);
        double z = Math.cos(yaw);
        return new Vec3d(x, 0, z);
    }

    public static double distancePointToDirection(Vec3d point, Vec3d direction, @Nullable Vec3d start) {
        if (start == null) start = Vec3d.ZERO;

        point = point.multiply(new Vec3d(1, 0, 1));
        start = start.multiply(new Vec3d(1, 0, 1));
        direction = direction.multiply(new Vec3d(1, 0, 1));

        Vec3d directionVec = point.subtract(start);

        double projectionLength = directionVec.dotProduct(direction) / direction.lengthSquared();
        Vec3d projection = direction.multiply(projectionLength);
        Vec3d perp = directionVec.subtract(projection);
        return perp.length();
    }

    public static float smoothRotation(double current, double target, double rotationScaling)
    {
        double difference = angleDifference(target, current);
        return (float) (current + difference * rotationScaling);
    }

    public static double angleDifference(double target, double current)
    {
        double diff = (target - current + 180) % 360 - 180;
        return diff < -180 ? diff + 360 : diff;
    }

    public static void sendWebhook(String webhookURL, String title, String message, String pingID, String playerName)
    {
        sendWebhook(webhookURL, title, message, COLOR_INFO, pingID);
    }

    public static void sendWebhook(String webhookURL, String title, String message, int color, String pingID)
    {
        List<Field> fields = new ArrayList<>();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) fields.add(field("Position", coords(mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ()), false));
        sendWebhook(webhookURL, title, message, color, fields, pingID);
    }

    public static void sendWebhook(String webhookURL, String title, String description, int color, List<Field> fields, String pingID)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        List<Field> all = new ArrayList<>(fields);
        if (mc.world != null) all.add(field("Dimension", dimensionName(mc.world.getRegistryKey().getValue().getPath()), true));
        if (mc.getCurrentServerEntry() != null) all.add(field("Server", mc.getCurrentServerEntry().address, true));

        JsonObject embed = new JsonObject();
        embed.addProperty("title", clip(title, 256));
        if (description != null && !description.isBlank()) embed.addProperty("description", clip(description, 4096));
        embed.addProperty("color", color);
        embed.addProperty("timestamp", Instant.now().toString());

        JsonObject footer = new JsonObject();
        footer.addProperty("text", "6Tools BaseHunting");
        embed.add("footer", footer);

        if (mc.player != null) {
            String name = mc.player.getGameProfile().name();
            String avatar = "https://mc-heads.net/avatar/" + mc.player.getUuid().toString().replace("-", "") + "/64";
            JsonObject author = new JsonObject();
            author.addProperty("name", name);
            author.addProperty("icon_url", avatar);
            embed.add("author", author);

            JsonObject thumbnail = new JsonObject();
            thumbnail.addProperty("url", avatar);
            embed.add("thumbnail", thumbnail);
        }

        JsonArray array = new JsonArray();
        for (Field f : all) {
            if (array.size() >= 25) break;
            JsonObject o = new JsonObject();
            o.addProperty("name", clip(f.name(), 256));
            o.addProperty("value", clip(f.value().isEmpty() ? "-" : f.value(), 1024));
            o.addProperty("inline", f.inline());
            array.add(o);
        }
        if (array.size() > 0) embed.add("fields", array);

        JsonArray embeds = new JsonArray();
        embeds.add(embed);

        JsonObject body = new JsonObject();
        body.add("embeds", embeds);
        if (pingID != null && !pingID.isBlank()) {
            body.addProperty("content", "<@" + pingID.trim() + ">");
            JsonObject mentions = new JsonObject();
            JsonArray parse = new JsonArray();
            parse.add("users");
            mentions.add("parse", parse);
            body.add("allowed_mentions", mentions);
        }

        sendRequest(webhookURL, GSON.toJson(body));
    }

    private static String dimensionName(String path) {
        return switch (path) {
            case "overworld" -> "Overworld";
            case "the_nether" -> "Nether";
            case "the_end" -> "End";
            default -> path;
        };
    }

    private static String clip(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max - 1) + "\u2026";
    }

    private static void sendRequest(String webhookURL, String json) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(webhookURL.trim()))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("User-Agent", "6ToolsAddon")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) {
                Thread.sleep(1500);
                response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            }
            if (response.statusCode() / 100 != 2) System.err.println("[6Tools] Webhook returned " + response.statusCode() + ": " + response.body());
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}
