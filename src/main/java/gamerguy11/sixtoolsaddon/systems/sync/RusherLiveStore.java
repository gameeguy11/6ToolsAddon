package gamerguy11.sixtoolsaddon.systems.sync;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RusherLiveStore extends SocialsStore {
   private static final String API = "org.rusherhack.client.api.RusherHackAPI";
   private static final String MANAGER = "org.rusherhack.client.api.system.IRelationManager";

   private boolean initialised;
   private boolean available;
   private Method getManager;
   private Method getFriends;
   private Method getEnemies;
   private Method addFriend;
   private Method addEnemy;
   private Method removeFriend;
   private Method removeEnemy;

   public RusherLiveStore(Path dummy) {
      super(dummy, "name", "role");
   }

   public String id() {
      return "rusherhack";
   }

   private void init() {
      if (this.initialised) {
         return;
      }

      this.initialised = true;
      try {
         ClassLoader loader = RusherLiveStore.class.getClassLoader();
         Class<?> api = Class.forName(API, true, loader);
         Class<?> manager = Class.forName(MANAGER, true, loader);
         this.getManager = api.getMethod("getRelationManager");
         this.getFriends = manager.getMethod("getFriends");
         this.getEnemies = manager.getMethod("getEnemies");
         this.addFriend = manager.getMethod("addFriend", String.class);
         this.addEnemy = manager.getMethod("addEnemy", String.class);
         this.removeFriend = manager.getMethod("removeFriend", String.class);
         this.removeEnemy = manager.getMethod("removeEnemy", String.class);
         this.available = true;
      } catch (Throwable t) {
         this.available = false;
      }
   }

   @Override
   public boolean exists() {
      this.init();
      return this.available && this.manager() != null;
   }

   private Object manager() {
      try {
         return this.getManager.invoke(null);
      } catch (Throwable t) {
         return null;
      }
   }

   private Map<String, String> snapshot(Map<String, String> display) {
      Map<String, String> roles = new HashMap<>();
      Object manager = this.manager();
      if (manager == null) {
         return roles;
      }

      this.collect(this.getEnemies, manager, ENEMY, roles, display);
      this.collect(this.getFriends, manager, FRIEND, roles, display);
      return roles;
   }

   private void collect(Method getter, Object manager, String role, Map<String, String> roles, Map<String, String> display) {
      try {
         Object value = getter.invoke(manager);
         if (!(value instanceof List<?> list)) {
            return;
         }

         for (Object element : list) {
            String name = nameOf(element);
            if (name != null && !name.isEmpty()) {
               String key = name.toLowerCase(Locale.ROOT);
               roles.put(key, role);
               display.put(key, name);
            }
         }
      } catch (Throwable ignored) {
      }
   }

   private static String nameOf(Object element) {
      if (element == null) {
         return null;
      }

      if (element instanceof String text) {
         return text;
      }

      for (String getter : new String[]{"getUsername", "getName", "username", "name", "getDisplayName"}) {
         try {
            Object value = element.getClass().getMethod(getter).invoke(element);
            if (value instanceof String text && !text.isEmpty()) {
               return text;
            }
         } catch (Throwable ignored) {
         }
      }

      return element.toString();
   }

   @Override
   public long lastModified() {
      return new HashMap<>(this.snapshot(new HashMap<>())).hashCode();
   }

   @Override
   public JsonElement read() {
      Map<String, String> display = new HashMap<>();
      Map<String, String> roles = this.snapshot(display);
      JsonObject object = new JsonObject();
      for (Map.Entry<String, String> entry : roles.entrySet()) {
         object.addProperty(display.getOrDefault(entry.getKey(), entry.getKey()), entry.getValue());
      }

      return object;
   }

   @Override
   public void parse(JsonElement raw, Map<String, String> roles, Map<String, String> display) {
      if (!raw.isJsonObject()) {
         return;
      }

      for (Map.Entry<String, JsonElement> entry : raw.getAsJsonObject().entrySet()) {
         String key = entry.getKey().toLowerCase(Locale.ROOT);
         roles.putIfAbsent(key, entry.getValue().getAsString());
         display.putIfAbsent(key, entry.getKey());
      }
   }

   @Override
   public JsonElement build(JsonElement raw, Map<String, String> result, Map<String, String> display) {
      JsonObject object = new JsonObject();
      for (Map.Entry<String, String> entry : result.entrySet()) {
         object.addProperty(display.getOrDefault(entry.getKey(), entry.getKey()), entry.getValue());
      }

      return object;
   }

   @Override
   public void write(JsonElement content) {
      Object manager = this.manager();
      if (manager == null || !content.isJsonObject()) {
         return;
      }

      Map<String, String> display = new HashMap<>();
      Map<String, String> current = this.snapshot(display);
      Map<String, String> wanted = new HashMap<>();
      Map<String, String> wantedNames = new HashMap<>();
      for (Map.Entry<String, JsonElement> entry : content.getAsJsonObject().entrySet()) {
         String key = entry.getKey().toLowerCase(Locale.ROOT);
         wanted.put(key, entry.getValue().getAsString());
         wantedNames.put(key, entry.getKey());
      }

      for (Map.Entry<String, String> entry : current.entrySet()) {
         String name = display.getOrDefault(entry.getKey(), entry.getKey());
         if (!wanted.containsKey(entry.getKey())) {
            this.call(FRIEND.equals(entry.getValue()) ? this.removeFriend : this.removeEnemy, manager, name);
         }
      }

      for (Map.Entry<String, String> entry : wanted.entrySet()) {
         String have = current.get(entry.getKey());
         if (entry.getValue().equals(have)) {
            continue;
         }

         String name = wantedNames.get(entry.getKey());
         boolean friend = FRIEND.equals(entry.getValue());
         if (have != null) {
            this.call(friend ? this.removeEnemy : this.removeFriend, manager, display.getOrDefault(entry.getKey(), name));
         }

         this.call(friend ? this.addFriend : this.addEnemy, manager, name);
      }
   }

   private void call(Method method, Object manager, String name) {
      try {
         method.invoke(manager, name);
      } catch (Throwable ignored) {
      }
   }

   @Override
   public void backup() {
   }

   @Override
   protected String canonicalRole(String stored) {
      return stored;
   }

   @Override
   protected String storedRole(String canonical) {
      return canonical;
   }

   @Override
   protected JsonObject newEntry(String name, String canonical) {
      JsonObject object = new JsonObject();
      object.addProperty("name", name);
      return object;
   }
}
