package gamerguy11.sixtoolsaddon.modules.stashsorter.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import meteordevelopment.meteorclient.MeteorClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SortAreaStore {
   private static final Logger LOGGER = LoggerFactory.getLogger("AutoStashSorter");
   private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().create();
   private static final String FILE_NAME = "sort_areas.json";

   public SortArea load(String server, String dimension) {
      JsonObject areas = this.readAreas();
      String key = key(server, dimension);
      if (!areas.has(key)) {
         return null;
      } else {
         try {
            SortArea area = (SortArea)GSON.fromJson(areas.get(key), SortArea.class);
            return area != null && dimension.equals(area.dimension) ? area : null;
         } catch (RuntimeException exception) {
            LOGGER.warn("Failed to read sort area for {} in {}", new Object[]{server, dimension, exception});
            return null;
         }
      }
   }

   public void save(String server, String dimension, SortArea area) {
      JsonObject areas = this.readAreas();
      areas.add(key(server, dimension), GSON.toJsonTree(area));
      this.writeAreas(areas);
   }

   public void clear(String server, String dimension) {
      JsonObject areas = this.readAreas();
      areas.remove(key(server, dimension));
      this.writeAreas(areas);
   }

   private JsonObject readAreas() {
      File file = this.dataFile();
      if (!file.isFile()) {
         return new JsonObject();
      } else {
         try {
            JsonObject root = JsonParser.parseString(Files.readString(file.toPath(), StandardCharsets.UTF_8)).getAsJsonObject();
            return root.has("areas") ? root.getAsJsonObject("areas") : new JsonObject();
         } catch (Exception exception) {
            LOGGER.warn("Failed to load saved sort areas", exception);
            return new JsonObject();
         }
      }
   }

   private void writeAreas(JsonObject areas) {
      File file = this.dataFile();
      File temporary = new File(file.getParentFile(), "sort_areas.json.tmp");
      JsonObject root = new JsonObject();
      root.addProperty("version", 1);
      root.add("areas", areas);

      try {
         Files.createDirectories(file.getParentFile().toPath());
         Files.writeString(temporary.toPath(), GSON.toJson(root), StandardCharsets.UTF_8);

         try {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
         } catch (AtomicMoveNotSupportedException var6) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
         }
      } catch (IOException exception) {
         LOGGER.error("Failed to save sort areas", exception);
      }

   }

   private File dataFile() {
      return new File(new File(MeteorClient.FOLDER, "StashSorter"), "sort_areas.json");
   }

   private static String key(String server, String dimension) {
      String var10000 = server.toLowerCase(Locale.ROOT);
      return var10000 + "|" + dimension;
   }
}
