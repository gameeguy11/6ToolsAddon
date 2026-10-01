package gamerguy11.sixtoolsaddon.deathlogger;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DeathRecord {
   private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
   private static final Pattern COORD_PATTERN = Pattern.compile("X: (-?\\d+) Y: (-?\\d+) Z: (-?\\d+)");
   public final String timestamp;
   public final int x;
   public final int y;
   public final int z;
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
      return this.timestamp + " | X: " + this.x + " Y: " + this.y + " Z: " + this.z + " | Dimension: " + this.dimension;
   }

   public static DeathRecord fromLine(String line) {
      try {
         String[] parts = line.split("\\|");
         if (parts.length < 3) {
            return null;
         } else {
            String timestamp = parts[0].trim();
            String coordsPart = parts[1].trim();
            String dimension = parts[2].replace("Dimension:", "").trim();
            Matcher matcher = COORD_PATTERN.matcher(coordsPart);
            if (!matcher.find()) {
               return null;
            } else {
               int x = Integer.parseInt(matcher.group(1));
               int y = Integer.parseInt(matcher.group(2));
               int z = Integer.parseInt(matcher.group(3));
               return new DeathRecord(timestamp, x, y, z, dimension);
            }
         }
      } catch (RuntimeException var9) {
         return null;
      }
   }

   public String coordsString() {
      return this.x + ", " + this.y + ", " + this.z;
   }
}
