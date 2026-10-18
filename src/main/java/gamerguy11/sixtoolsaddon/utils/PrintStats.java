package gamerguy11.sixtoolsaddon.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.loader.api.FabricLoader;

public final class PrintStats {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("sixtoolsaddon").resolve("print-stats.json");
   private static Map<String, Integer> totals;
   private static final Map<String, Integer> session = new TreeMap<>();

   private PrintStats() {
   }

   private static synchronized Map<String, Integer> totals() {
      if (totals == null) {
         totals = new TreeMap<>();
         try {
            if (Files.exists(FILE)) {
               Map<String, Integer> loaded = GSON.fromJson(Files.readString(FILE), new TypeToken<Map<String, Integer>>() { }.getType());
               if (loaded != null) {
                  totals.putAll(loaded);
               }
            }
         } catch (Exception ignored) {
         }
      }

      return totals;
   }

   public static synchronized void record(String player) {
      totals().merge(player, 1, Integer::sum);
      session.merge(player, 1, Integer::sum);
      save();
   }

   public static synchronized int total(String player) {
      return totals().getOrDefault(player, 0);
   }

   public static synchronized int session(String player) {
      return session.getOrDefault(player, 0);
   }

   public static synchronized Map<String, Integer> all() {
      return new TreeMap<>(totals());
   }

   public static synchronized void reset(String player) {
      totals().remove(player);
      session.remove(player);
      save();
   }

   private static void save() {
      try {
         Files.createDirectories(FILE.getParent());
         Files.writeString(FILE, GSON.toJson(totals()));
      } catch (IOException ignored) {
      }
   }
}
