package gamerguy11.sixtoolsaddon.deathlogger;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DeathRecord {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern COORD_PATTERN = Pattern.compile("X: (-?\\d+) Y: (-?\\d+) Z: (-?\\d+)");

    public final String timestamp;
    public final int x, y, z;
    public final String dimension;

    public DeathRecord(String timestamp, int x, int y, int z, String dimension) {
        this.timestamp = timestamp;
        this.x = x;
        this.y = y;
        this.z = z;
        this.dimension = dimension;
    }

    public static DeathRecord now(int x, int y, int z, String dimension) {
        return new DeathRecord(LocalDateTime.now().format(FORMATTER), x, y, z, dimension);
    }

    public String toLine() {
        return timestamp + " | X: " + x + " Y: " + y + " Z: " + z + " | Dimension: " + dimension;
    }

    public static DeathRecord fromLine(String line) {
        try {
            String[] parts = line.split("\\|");
            if (parts.length < 3) return null;

            String timestamp = parts[0].trim();
            String coordsPart = parts[1].trim();
            String dimension = parts[2].replace("Dimension:", "").trim();

            Matcher matcher = COORD_PATTERN.matcher(coordsPart);
            if (!matcher.find()) return null;

            int x = Integer.parseInt(matcher.group(1));
            int y = Integer.parseInt(matcher.group(2));
            int z = Integer.parseInt(matcher.group(3));

            return new DeathRecord(timestamp, x, y, z, dimension);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public String coordsString() {
        return x + ", " + y + ", " + z;
    }
}
