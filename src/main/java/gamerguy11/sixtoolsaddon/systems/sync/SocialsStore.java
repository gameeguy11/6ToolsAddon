package gamerguy11.sixtoolsaddon.systems.sync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public abstract class SocialsStore {
   public static final String FRIEND = "friend";
   public static final String ENEMY = "enemy";
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   protected Path path;
   protected final Path defaultPath;
   protected Path override;
   private final String nameField;
   private final String roleField;

   protected SocialsStore(Path path, String nameField, String roleField) {
      this.path = path;
      this.defaultPath = path;
      this.nameField = nameField;
      this.roleField = roleField;
   }

   public abstract String id();

   public abstract void parse(JsonElement raw, Map<String, String> roles, Map<String, String> display) throws IOException;

   public abstract JsonElement build(JsonElement raw, Map<String, String> result, Map<String, String> display) throws IOException;

   protected abstract String canonicalRole(String stored);

   protected abstract String storedRole(String canonical);

   protected abstract JsonObject newEntry(String name, String canonical);

   public Map<String, String> project(Map<String, String> state) {
      return new HashMap<>(state);
   }

   public void setOverride(String raw) {
      String text = raw == null ? "" : raw.trim();
      if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
         text = text.substring(1, text.length() - 1).trim();
      }

      Path next = null;
      if (!text.isEmpty()) {
         try {
            next = Path.of(text);
         } catch (Exception ignored) {
         }
      }

      this.override = next;
      this.path = next != null ? next : this.defaultPath;
   }

   public Path path() {
      return this.path;
   }

   public boolean exists() {
      if (this.override != null) {
         this.path = this.override;
      }

      return Files.isRegularFile(this.path);
   }

   public long lastModified() throws IOException {
      return Files.getLastModifiedTime(this.path).toMillis();
   }

   public JsonElement read() throws IOException {
      return JsonParser.parseString(Files.readString(this.path));
   }

   public void write(JsonElement content) throws IOException {
      Path temp = this.path.resolveSibling(this.path.getFileName() + ".tmp");
      Files.writeString(temp, GSON.toJson(content));
      try {
         Files.move(temp, this.path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (IOException atomicFailed) {
         Files.move(temp, this.path, StandardCopyOption.REPLACE_EXISTING);
      }
   }

   public void backup() throws IOException {
      Files.copy(this.path, this.path.resolveSibling(this.path.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
   }

   protected final void readEntries(JsonArray entries, Map<String, String> roles, Map<String, String> display) {
      for (JsonElement element : entries) {
         if (!element.isJsonObject()) {
            continue;
         }

         JsonObject object = element.getAsJsonObject();
         String name = text(object, this.nameField);
         String stored = text(object, this.roleField);
         if (name == null || name.isEmpty() || stored == null) {
            continue;
         }

         String canonical = this.canonicalRole(stored);
         if (canonical == null) {
            continue;
         }

         String key = name.toLowerCase(Locale.ROOT);
         roles.putIfAbsent(key, canonical);
         display.putIfAbsent(key, name);
      }
   }

   protected final JsonArray rebuild(JsonArray original, Map<String, String> result, Map<String, String> display) {
      JsonArray output = new JsonArray();
      Set<String> emitted = new HashSet<>();

      for (JsonElement element : original) {
         if (!element.isJsonObject()) {
            output.add(element);
            continue;
         }

         JsonObject object = element.getAsJsonObject();
         String name = text(object, this.nameField);
         if (name == null) {
            output.add(object);
            continue;
         }

         String key = name.toLowerCase(Locale.ROOT);
         String stored = text(object, this.roleField);
         String canonical = stored == null ? null : this.canonicalRole(stored);
         if (canonical == null) {
            emitted.add(key);
            output.add(object);
            continue;
         }

         String wanted = result.get(key);
         if (wanted == null || !emitted.add(key)) {
            continue;
         }

         object.addProperty(this.roleField, this.storedRole(wanted));
         output.add(object);
      }

      for (Map.Entry<String, String> entry : result.entrySet()) {
         if (!emitted.contains(entry.getKey())) {
            output.add(this.newEntry(display.getOrDefault(entry.getKey(), entry.getKey()), entry.getValue()));
         }
      }

      return output;
   }

   private static String text(JsonObject object, String field) {
      JsonElement value = object.get(field);
      return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
   }
}
