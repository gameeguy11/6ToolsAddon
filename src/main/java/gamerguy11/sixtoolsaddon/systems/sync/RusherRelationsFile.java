package gamerguy11.sixtoolsaddon.systems.sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class RusherRelationsFile extends SocialsStore {
   private final Path[] candidates;
   private boolean enemies = true;
   private String enemyState = "ENEMY";

   public RusherRelationsFile(Path minecraftDir) {
      super(minecraftDir.resolve("rusherhack").resolve("config").resolve("relations.json"), "username", "state");
      Path config = minecraftDir.resolve("rusherhack").resolve("config");
      this.candidates = new Path[]{config.resolve("relations.json"), config.resolve("relations").resolve("relations.json")};
   }

   public void setEnemies(boolean enemies) {
      this.enemies = enemies;
   }

   public void setEnemyState(String enemyState) {
      if (enemyState != null && !enemyState.isBlank()) {
         this.enemyState = enemyState.trim();
      }
   }

   public String id() {
      return "rusherhack";
   }

   public boolean exists() {
      if (this.override != null) {
         this.path = this.override;
         return Files.isRegularFile(this.path);
      }

      for (Path candidate : this.candidates) {
         if (Files.isRegularFile(candidate)) {
            this.path = candidate;
            return true;
         }
      }

      this.path = this.defaultPath;
      return false;
   }

   public Map<String, String> project(Map<String, String> state) {
      Map<String, String> projected = new HashMap<>();
      for (Map.Entry<String, String> entry : state.entrySet()) {
         if (FRIEND.equals(entry.getValue()) || this.enemies) {
            projected.put(entry.getKey(), entry.getValue());
         }
      }

      return projected;
   }

   public void parse(JsonElement raw, Map<String, String> roles, Map<String, String> display) throws IOException {
      this.readEntries(this.entries(raw), roles, display);
   }

   public JsonElement build(JsonElement raw, Map<String, String> result, Map<String, String> display) throws IOException {
      return this.rebuild(this.entries(raw), result, display);
   }

   protected String canonicalRole(String stored) {
      if ("FRIEND".equalsIgnoreCase(stored)) {
         return FRIEND;
      }

      return this.enemies && this.enemyState.equalsIgnoreCase(stored) ? ENEMY : null;
   }

   protected String storedRole(String canonical) {
      return FRIEND.equals(canonical) ? "FRIEND" : this.enemyState;
   }

   protected JsonObject newEntry(String name, String canonical) {
      JsonObject object = new JsonObject();
      object.addProperty("username", name);
      object.addProperty("alias", name);
      object.addProperty("state", this.storedRole(canonical));
      return object;
   }

   private JsonArray entries(JsonElement raw) throws IOException {
      if (!raw.isJsonArray()) {
         throw new IOException("relations.json root is not an array");
      }

      return raw.getAsJsonArray();
   }
}
