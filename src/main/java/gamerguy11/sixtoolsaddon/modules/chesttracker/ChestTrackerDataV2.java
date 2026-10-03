package gamerguy11.sixtoolsaddon.modules.chesttracker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.Registries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChestTrackerDataV2 {
   private static final Logger LOGGER = LoggerFactory.getLogger("ChestTracker");
   private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().create();
   private static final int CURRENT_VERSION = 2;
   private final Map<String, Map<BlockPos, TrackedContainer>> containers = new ConcurrentHashMap();
   private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
   private final MinecraftClient mc = MinecraftClient.getInstance();
   private File dataFile;
   private File backupFile;
   private File tempFile;
   private long lastSaveTime = 0L;
   private int saveFailures = 0;

   public ChestTrackerDataV2() {
      this.initializeFiles();
   }

   private void initializeFiles() {
      try {
         File folder = new File(MeteorClient.FOLDER, "ChestTracker");
         if (!folder.exists() && !folder.mkdirs()) {
            LOGGER.error("Failed to create ChestTracker folder");
         }

         this.dataFile = new File(folder, "tracked_containers.json");
         this.backupFile = new File(folder, "tracked_containers.backup.json");
         this.tempFile = new File(folder, "tracked_containers.tmp");
      } catch (Exception e) {
         LOGGER.error("Failed to initialize files", e);
      }

   }

   public void trackContainer(BlockPos pos, String dimension, String containerType, List<ItemStack> contents) {
      this.trackContainer(pos, dimension, containerType, contents, contents);
   }

   public void trackContainer(BlockPos pos, String dimension, String containerType, List<ItemStack> contents, List<ItemStack> topLevelContents) {
      this.lock.writeLock().lock();

      try {
         Map<BlockPos, TrackedContainer> dimContainers = (Map)this.containers.computeIfAbsent(dimension, (k) -> new ConcurrentHashMap());
         TrackedContainer container = (TrackedContainer)dimContainers.get(pos);
         if (container == null) {
            container = new TrackedContainer(pos, dimension, containerType);
            dimContainers.put(pos, container);
         } else {
            container.setContainerType(containerType);
         }

         container.updateContents(contents, topLevelContents);
      } finally {
         this.lock.writeLock().unlock();
      }

   }

   public TrackedContainer getContainer(BlockPos pos, String dimension) {
      this.lock.readLock().lock();

      TrackedContainer var4;
      try {
         Map<BlockPos, TrackedContainer> dimContainers = (Map)this.containers.get(dimension);
         var4 = dimContainers != null ? (TrackedContainer)dimContainers.get(pos) : null;
      } finally {
         this.lock.readLock().unlock();
      }

      return var4;
   }

   public List<TrackedContainer> searchItem(Item item) {
      String currentDim = this.getCurrentDimension();
      return this.searchItem(item, currentDim);
   }

   public List<TrackedContainer> searchItem(Item item, String dimension) {
      this.lock.readLock().lock();

      List var4;
      try {
         Map<BlockPos, TrackedContainer> dimContainers = (Map)this.containers.get(dimension);
         if (dimContainers != null) {
            var4 = (List)dimContainers.values().stream().filter((c) -> c.containsItem(item)).sorted((a, b) -> {
               String itemId = Registries.ITEM.getId(item).toString();
               return Integer.compare(b.getItemCount(itemId), a.getItemCount(itemId));
            }).collect(Collectors.toList());
            return var4;
         }

         var4 = new ArrayList();
      } finally {
         this.lock.readLock().unlock();
      }

      return var4;
   }

   public List<TrackedContainer> getAllContainers() {
      return this.getAllContainers(this.getCurrentDimension());
   }

   public List<TrackedContainer> getAllContainers(String dimension) {
      this.lock.readLock().lock();

      ArrayList var3;
      try {
         Map<BlockPos, TrackedContainer> dimContainers = (Map)this.containers.get(dimension);
         var3 = dimContainers != null ? new ArrayList(dimContainers.values()) : new ArrayList();
      } finally {
         this.lock.readLock().unlock();
      }

      return var3;
   }

   public int getTotalContainerCount() {
      this.lock.readLock().lock();

      int var1;
      try {
         var1 = this.containers.values().stream().mapToInt(Map::size).sum();
      } finally {
         this.lock.readLock().unlock();
      }

      return var1;
   }

   public int getCurrentDimensionContainerCount() {
      String dimension = this.getCurrentDimension();
      this.lock.readLock().lock();

      int var3;
      try {
         Map<BlockPos, TrackedContainer> dimContainers = (Map)this.containers.get(dimension);
         var3 = dimContainers != null ? dimContainers.size() : 0;
      } finally {
         this.lock.readLock().unlock();
      }

      return var3;
   }

   public boolean removeContainer(BlockPos pos, String dimension) {
      this.lock.writeLock().lock();

      try {
         Map<BlockPos, TrackedContainer> dimContainers = this.containers.get(dimension);
         return dimContainers != null && dimContainers.remove(pos) != null;
      } finally {
         this.lock.writeLock().unlock();
      }
   }

   public int removeEmptyContainers() {
      this.lock.writeLock().lock();

      int var8;
      try {
         int removed = 0;

         for(Map<BlockPos, TrackedContainer> dimContainers : this.containers.values()) {
            Iterator<Map.Entry<BlockPos, TrackedContainer>> it = dimContainers.entrySet().iterator();

            while(it.hasNext()) {
               if (((TrackedContainer)((Map.Entry)it.next()).getValue()).isEmpty()) {
                  it.remove();
                  ++removed;
               }
            }
         }

         var8 = removed;
      } finally {
         this.lock.writeLock().unlock();
      }

      return var8;
   }

   public int removeOldContainers(int days) {
      this.lock.writeLock().lock();

      int var11;
      try {
         long cutoff = System.currentTimeMillis() - (long)days * 24L * 60L * 60L * 1000L;
         int removed = 0;

         for(Map<BlockPos, TrackedContainer> dimContainers : this.containers.values()) {
            Iterator<Map.Entry<BlockPos, TrackedContainer>> it = dimContainers.entrySet().iterator();

            while(it.hasNext()) {
               if (((TrackedContainer)((Map.Entry)it.next()).getValue()).getLastUpdated() < cutoff) {
                  it.remove();
                  ++removed;
               }
            }
         }

         var11 = removed;
      } finally {
         this.lock.writeLock().unlock();
      }

      return var11;
   }

   public void clearAll() {
      this.lock.writeLock().lock();

      try {
         this.containers.clear();
      } finally {
         this.lock.writeLock().unlock();
      }

   }

   public void clearCurrentDimension() {
      String dimension = this.getCurrentDimension();
      this.lock.writeLock().lock();

      try {
         this.containers.remove(dimension);
      } finally {
         this.lock.writeLock().unlock();
      }

   }

   public void saveData() {
      this.lock.readLock().lock();

      try {
         JsonObject root = new JsonObject();
         root.addProperty("version", 2);
         root.addProperty("saveTime", System.currentTimeMillis());
         JsonObject dimensions = new JsonObject();

         for(Map.Entry dimEntry : this.containers.entrySet()) {
            JsonArray dimArray = new JsonArray();

            for(TrackedContainer container : ((Map<BlockPos, TrackedContainer>)dimEntry.getValue()).values()) {
               dimArray.add(container.toJson());
            }

            dimensions.add((String)dimEntry.getKey(), dimArray);
         }

         root.add("dimensions", dimensions);
         Writer writer = new OutputStreamWriter(new FileOutputStream(this.tempFile), StandardCharsets.UTF_8);

         try {
            GSON.toJson(root, writer);
         } catch (Throwable var14) {
            try {
               writer.close();
            } catch (Throwable var13) {
               var14.addSuppressed(var13);
            }

            throw var14;
         }

         writer.close();
         if (this.dataFile.exists() && this.dataFile.length() > 0L) {
            Files.copy(this.dataFile.toPath(), this.backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
         }

         Files.move(this.tempFile.toPath(), this.dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
         this.lastSaveTime = System.currentTimeMillis();
         this.saveFailures = 0;
      } catch (Exception e) {
         ++this.saveFailures;
         LOGGER.error("Failed to save data (attempt {})", this.saveFailures, e);
         if (this.saveFailures > 3) {
            LOGGER.error("Multiple save failures, data may be lost!");
         }
      } finally {
         this.lock.readLock().unlock();
      }

   }

   public void saveBackup() throws IOException {
      this.lock.readLock().lock();

      try {
         JsonObject root = new JsonObject();
         root.addProperty("version", 2);
         root.addProperty("backupTime", System.currentTimeMillis());
         JsonObject dimensions = new JsonObject();

         for(Map.Entry dimEntry : this.containers.entrySet()) {
            JsonArray dimArray = new JsonArray();

            for(TrackedContainer container : ((Map<BlockPos, TrackedContainer>)dimEntry.getValue()).values()) {
               dimArray.add(container.toJson());
            }

            dimensions.add((String)dimEntry.getKey(), dimArray);
         }

         root.add("dimensions", dimensions);
         Writer writer = new OutputStreamWriter(new FileOutputStream(this.backupFile), StandardCharsets.UTF_8);

         try {
            GSON.toJson(root, writer);
         } catch (Throwable var13) {
            try {
               writer.close();
            } catch (Throwable var12) {
               var13.addSuppressed(var12);
            }

            throw var13;
         }

         writer.close();
      } finally {
         this.lock.readLock().unlock();
      }

   }

   public void loadData() {
      this.lock.writeLock().lock();

      try {
         this.containers.clear();
         if (!this.loadFromFile(this.dataFile)) {
            if (this.loadFromFile(this.backupFile)) {
               LOGGER.warn("Main file corrupted, loaded from backup");
               this.saveData();
               return;
            }

            File oldFile = new File(MeteorClient.FOLDER, "ChestTracker/tracked_containers.json");
            if (oldFile.exists() && this.loadFromFile(oldFile)) {
               LOGGER.info("Migrated data from old format");
               this.saveData();
               return;
            }

            LOGGER.info("No existing data found, starting fresh");
            return;
         }

         LOGGER.info("Loaded data from main file");
      } finally {
         this.lock.writeLock().unlock();
      }

   }

   private boolean loadFromFile(File file) {
      if (file.exists() && file.length() != 0L) {
         try {
            String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("version")) {
               root.get("version").getAsInt();
            } else {
               boolean var10000 = true;
            }

            if (root.has("dimensions")) {
               JsonObject dimensions = root.getAsJsonObject("dimensions");

               for(Map.Entry dimEntry : dimensions.entrySet()) {
                  String dimension = (String)dimEntry.getKey();
                  JsonArray dimArray = ((JsonElement)dimEntry.getValue()).getAsJsonArray();
                  Map<BlockPos, TrackedContainer> dimContainers = new ConcurrentHashMap();

                  for(JsonElement element : dimArray) {
                     try {
                        TrackedContainer container = TrackedContainer.fromJson(element.getAsJsonObject());
                        dimContainers.put(container.getPosition(), container);
                     } catch (Exception e) {
                        LOGGER.warn("Skipped corrupted container entry", e);
                     }
                  }

                  if (!dimContainers.isEmpty()) {
                     this.containers.put(dimension, dimContainers);
                  }
               }
            }

            return true;
         } catch (Exception e) {
            LOGGER.error("Failed to load from file: {}", file.getName(), e);
            return false;
         }
      } else {
         return false;
      }
   }

   public void exportData(String filename) throws IOException {
      this.lock.readLock().lock();

      try {
         File exportFile = new File(new File(MeteorClient.FOLDER, "ChestTracker"), filename);
         JsonObject export = new JsonObject();
         export.addProperty("version", 2);
         export.addProperty("exportTime", System.currentTimeMillis());
         export.addProperty("totalContainers", this.getTotalContainerCount());
         JsonObject dimensions = new JsonObject();

         for(Map.Entry dimEntry : this.containers.entrySet()) {
            JsonArray dimArray = new JsonArray();

            for(TrackedContainer container : ((Map<BlockPos, TrackedContainer>)dimEntry.getValue()).values()) {
               dimArray.add(container.toJson());
            }

            dimensions.add((String)dimEntry.getKey(), dimArray);
         }

         export.add("dimensions", dimensions);
         Writer writer = new OutputStreamWriter(new FileOutputStream(exportFile), StandardCharsets.UTF_8);

         try {
            GSON.toJson(export, writer);
         } catch (Throwable var15) {
            try {
               writer.close();
            } catch (Throwable var14) {
               var15.addSuppressed(var14);
            }

            throw var15;
         }

         writer.close();
      } finally {
         this.lock.readLock().unlock();
      }

   }

   private String getCurrentDimension() {
      if (this.mc.world == null) {
         return "unknown";
      } else {
         RegistryKey<World> key = this.mc.world.getRegistryKey();
         return key.getValue().toString();
      }
   }
}
