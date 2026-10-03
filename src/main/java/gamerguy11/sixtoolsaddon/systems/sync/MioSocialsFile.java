package gamerguy11.sixtoolsaddon.systems.sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public final class MioSocialsFile extends SocialsStore {
   public MioSocialsFile(Path minecraftDir) {
      super(minecraftDir.resolve("mio-fabric").resolve("socials.json"), "name", "role");
   }

   public String id() {
      return "mio";
   }

   public void parse(JsonElement raw, Map<String, String> roles, Map<String, String> display) throws IOException {
      this.readEntries(this.socials(raw), roles, display);
   }

   public JsonElement build(JsonElement raw, Map<String, String> result, Map<String, String> display) throws IOException {
      JsonObject root = raw.getAsJsonObject().deepCopy();
      root.add("socials", this.rebuild(this.socials(raw), result, display));
      return root;
   }

   protected String canonicalRole(String stored) {
      return FRIEND.equals(stored) || ENEMY.equals(stored) ? stored : null;
   }

   protected String storedRole(String canonical) {
      return canonical;
   }

   protected JsonObject newEntry(String name, String canonical) {
      JsonObject object = new JsonObject();
      object.addProperty("name", name);
      object.addProperty("role", canonical);
      return object;
   }

   private JsonArray socials(JsonElement raw) throws IOException {
      if (!raw.isJsonObject()) {
         throw new IOException("socials.json root is not an object");
      }

      JsonElement socials = raw.getAsJsonObject().get("socials");
      if (socials == null || !socials.isJsonArray()) {
         throw new IOException("socials.json has no socials array");
      }

      return socials.getAsJsonArray();
   }
}
