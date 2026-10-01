package gamerguy11.sixtoolsaddon.deathlogger;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.MeteorClient;

public class DeathLogStore {
   private static final File FILE;
   public static final int GUI_LIMIT = 10;

   public static void append(DeathRecord record) {
      try {
         Files.writeString(FILE.toPath(), record.toLine() + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
      } catch (IOException e) {
         SixToolsAddon.LOG.error("Failed to save death log", e);
      }

   }

   public static List<DeathRecord> lastEntries(int limit) {
      List<DeathRecord> result = new ArrayList();
      if (!FILE.exists()) {
         return result;
      } else {
         try {
            List<String> lines = Files.readAllLines(FILE.toPath());
            int from = Math.max(0, lines.size() - limit);

            for(int i = lines.size() - 1; i >= from; --i) {
               String line = (String)lines.get(i);
               if (!line.isBlank()) {
                  DeathRecord record = DeathRecord.fromLine(line);
                  if (record != null) {
                     result.add(record);
                  }
               }
            }
         } catch (IOException e) {
            SixToolsAddon.LOG.error("Failed to read death log", e);
         }

         return result;
      }
   }

   static {
      FILE = new File(MeteorClient.FOLDER, "sixtoolsaddon-deaths.txt");
   }
}
