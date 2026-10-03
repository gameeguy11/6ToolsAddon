package gamerguy11.sixtoolsaddon.systems.sync;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import gamerguy11.sixtoolsaddon.SixToolsAddon;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;

public final class LambdaFriendsStore extends SocialsStore {
   private static final String HANDLER = "com.lambda.interaction.handlers.FriendHandler";
   private static final long RETRY_MS = 10 * 60 * 1000L;
   private static final Gson GSON = new Gson();

   private final File cacheFile;
   private final Map<UUID, String> names = new ConcurrentHashMap<>();
   private final Map<String, UUID> uuids = new ConcurrentHashMap<>();
   private final Map<String, Long> failed = new ConcurrentHashMap<>();
   private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
   private final Set<String> pending = new LinkedHashSet<>();
   private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
      Thread thread = new Thread(runnable, "sixtools-uuid-lookup");
      thread.setDaemon(true);
      return thread;
   });
   private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

   private boolean initialised;
   private boolean available;
   private Object handler;
   private Method getFriends;
   private Method befriend;
   private Method unfriend;
   private Method profileByUuid;
   private Method profileByName;
   private volatile int resolveVersion;

   public LambdaFriendsStore(Path dummy, File cacheFile) {
      super(dummy, "name", "role");
      this.cacheFile = cacheFile;
      this.loadCache();
   }

   public String id() {
      return "lambda";
   }

   private void init() {
      if (this.initialised) {
         return;
      }

      this.initialised = true;
      try {
         Class<?> type = Class.forName(HANDLER);
         this.handler = type.getField("INSTANCE").get(null);
         this.getFriends = type.getMethod("getFriends");
         this.befriend = type.getMethod("befriend", UUID.class);
         this.unfriend = type.getMethod("unfriend", UUID.class);
         this.profileByUuid = type.getMethod("gameProfile", UUID.class);
         this.profileByName = type.getMethod("gameProfile", String.class);
         this.available = true;
      } catch (Throwable t) {
         this.available = false;
      }
   }

   @Override
   public boolean exists() {
      this.init();
      return this.available;
   }

   @Override
   public Map<String, String> project(Map<String, String> state) {
      Map<String, String> projected = new HashMap<>();
      for (Map.Entry<String, String> entry : state.entrySet()) {
         if (FRIEND.equals(entry.getValue())) {
            projected.put(entry.getKey(), entry.getValue());
         }
      }

      return projected;
   }

   @Override
   public long lastModified() {
      return new HashSet<>(this.friendUuids()).hashCode() * 31L + this.resolveVersion * 1_000_003L;
   }

   @Override
   public JsonElement read() {
      JsonArray array = new JsonArray();
      for (UUID uuid : this.friendUuids()) {
         array.add(uuid.toString());
      }

      return array;
   }

   @Override
   public void parse(JsonElement raw, Map<String, String> roles, Map<String, String> display) {
      if (raw.isJsonArray()) {
         for (JsonElement element : raw.getAsJsonArray()) {
            UUID uuid = parseUuid(element.getAsString());
            String name = uuid == null ? null : this.nameOf(uuid);
            if (name != null) {
               String key = name.toLowerCase(Locale.ROOT);
               roles.putIfAbsent(key, FRIEND);
               display.putIfAbsent(key, name);
            }
         }
      }

      synchronized (this.pending) {
         for (String name : this.pending) {
            roles.putIfAbsent(name, FRIEND);
         }
      }
   }

   @Override
   public JsonElement build(JsonElement raw, Map<String, String> result, Map<String, String> display) {
      JsonArray array = new JsonArray();
      for (String key : result.keySet()) {
         array.add(display.getOrDefault(key, key));
      }

      return array;
   }

   @Override
   public void write(JsonElement content) {
      Set<String> wanted = new HashSet<>();
      Set<String> unresolved = new LinkedHashSet<>();
      Set<UUID> current = new HashSet<>(this.friendUuids());

      if (content.isJsonArray()) {
         for (JsonElement element : content.getAsJsonArray()) {
            String name = element.getAsString();
            String key = name.toLowerCase(Locale.ROOT);
            wanted.add(key);
            UUID uuid = this.uuidOf(name);
            if (uuid == null) {
               unresolved.add(key);
            } else if (!current.contains(uuid)) {
               this.invoke(this.befriend, uuid);
            }
         }
      }

      for (UUID uuid : current) {
         String name = this.nameOf(uuid);
         if (name != null && !wanted.contains(name.toLowerCase(Locale.ROOT))) {
            this.invoke(this.unfriend, uuid);
         }
      }

      synchronized (this.pending) {
         this.pending.clear();
         this.pending.addAll(unresolved);
      }

      this.saveCache();
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

   public boolean hasPending() {
      synchronized (this.pending) {
         return !this.pending.isEmpty();
      }
   }

   public void flushPending() {
      if (!this.exists()) {
         return;
      }

      List<String> waiting;
      synchronized (this.pending) {
         waiting = new ArrayList<>(this.pending);
      }

      Set<UUID> current = new HashSet<>(this.friendUuids());
      boolean changed = false;
      for (String name : waiting) {
         UUID uuid = this.uuidOf(name);
         if (uuid == null) {
            continue;
         }

         if (!current.contains(uuid)) {
            this.invoke(this.befriend, uuid);
         }

         synchronized (this.pending) {
            this.pending.remove(name);
         }

         changed = true;
      }

      if (changed) {
         this.saveCache();
      }
   }

   private List<UUID> friendUuids() {
      List<UUID> list = new ArrayList<>();
      if (!this.exists()) {
         return list;
      }

      try {
         Object value = this.getFriends.invoke(this.handler);
         if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
               if (item instanceof UUID uuid) {
                  list.add(uuid);
               }
            }
         }
      } catch (Throwable t) {
         SixToolsAddon.LOG.warn("social-sync could not read Lambda friends", t);
      }

      return list;
   }

   private void invoke(Method method, UUID uuid) {
      try {
         method.invoke(this.handler, uuid);
      } catch (Throwable t) {
         SixToolsAddon.LOG.warn("social-sync could not update Lambda friends", t);
      }
   }

   private String nameOf(UUID uuid) {
      String cached = this.names.get(uuid);
      if (cached != null) {
         return cached;
      }

      GameProfile profile = this.onlineProfile(uuid, null);
      if (profile == null) {
         profile = this.lambdaProfile(this.profileByUuid, uuid);
      }

      if (profile != null && profile.name() != null) {
         this.remember(profile.id(), profile.name());
         return profile.name();
      }

      this.lookupAsync("u:" + uuid, "https://sessionserver.mojang.com/session/minecraft/profile/" + uuid.toString().replace("-", ""));
      return null;
   }

   private UUID uuidOf(String name) {
      String key = name.toLowerCase(Locale.ROOT);
      UUID cached = this.uuids.get(key);
      if (cached != null) {
         return cached;
      }

      GameProfile profile = this.onlineProfile(null, key);
      if (profile == null) {
         profile = this.lambdaProfile(this.profileByName, name);
      }

      if (profile != null && profile.id() != null) {
         this.remember(profile.id(), profile.name() != null ? profile.name() : name);
         return profile.id();
      }

      this.lookupAsync("n:" + key, "https://api.mojang.com/users/profiles/minecraft/" + URLEncoder.encode(name, StandardCharsets.UTF_8));
      return null;
   }

   private GameProfile onlineProfile(UUID uuid, String lowerName) {
      try {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc == null || mc.getNetworkHandler() == null) {
            return null;
         }

         for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
            GameProfile profile = entry.getProfile();
            if (uuid != null && uuid.equals(profile.id())) {
               return profile;
            }

            if (lowerName != null && profile.name() != null && lowerName.equals(profile.name().toLowerCase(Locale.ROOT))) {
               return profile;
            }
         }
      } catch (Throwable ignored) {
      }

      return null;
   }

   private GameProfile lambdaProfile(Method method, Object argument) {
      if (!this.available) {
         return null;
      }

      try {
         Object value = method.invoke(this.handler, argument);
         return value instanceof GameProfile profile ? profile : null;
      } catch (Throwable t) {
         return null;
      }
   }

   private void remember(UUID uuid, String name) {
      String previous = this.names.put(uuid, name);
      this.uuids.put(name.toLowerCase(Locale.ROOT), uuid);
      if (!name.equals(previous)) {
         this.resolveVersion++;
         this.saveCache();
      }
   }

   private void lookupAsync(String key, String url) {
      Long last = this.failed.get(key);
      if (last != null && System.currentTimeMillis() - last < RETRY_MS) {
         return;
      }

      if (!this.inFlight.add(key)) {
         return;
      }

      this.executor.submit(() -> {
         try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).GET().build();
            HttpResponse<String> response = this.http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && !response.body().isBlank()) {
               JsonObject object = JsonParser.parseString(response.body()).getAsJsonObject();
               UUID uuid = parseUuid(object.get("id").getAsString());
               String name = object.get("name").getAsString();
               if (uuid != null) {
                  this.remember(uuid, name);
                  this.failed.remove(key);
                  return;
               }
            }

            this.failed.put(key, System.currentTimeMillis());
         } catch (Throwable t) {
            this.failed.put(key, System.currentTimeMillis());
         } finally {
            this.inFlight.remove(key);
         }
      });
   }

   private static UUID parseUuid(String text) {
      if (text == null) {
         return null;
      }

      try {
         String value = text.trim();
         if (value.length() == 32) {
            value = value.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5");
         }

         return UUID.fromString(value);
      } catch (Exception e) {
         return null;
      }
   }

   private synchronized void loadCache() {
      if (!this.cacheFile.isFile()) {
         return;
      }

      try {
         JsonObject root = JsonParser.parseString(Files.readString(this.cacheFile.toPath())).getAsJsonObject();
         if (root.has("names") && root.get("names").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("names").entrySet()) {
               UUID uuid = parseUuid(entry.getKey());
               if (uuid != null) {
                  this.names.put(uuid, entry.getValue().getAsString());
                  this.uuids.put(entry.getValue().getAsString().toLowerCase(Locale.ROOT), uuid);
               }
            }
         }

         if (root.has("pending") && root.get("pending").isJsonArray()) {
            synchronized (this.pending) {
               for (JsonElement element : root.getAsJsonArray("pending")) {
                  this.pending.add(element.getAsString().toLowerCase(Locale.ROOT));
               }
            }
         }
      } catch (Exception e) {
         SixToolsAddon.LOG.warn("social-sync could not read its uuid cache", e);
      }
   }

   private synchronized void saveCache() {
      try {
         JsonObject namesObject = new JsonObject();
         for (Map.Entry<UUID, String> entry : this.names.entrySet()) {
            namesObject.addProperty(entry.getKey().toString(), entry.getValue());
         }

         JsonArray pendingArray = new JsonArray();
         synchronized (this.pending) {
            for (String name : this.pending) {
               pendingArray.add(name);
            }
         }

         JsonObject root = new JsonObject();
         root.add("names", namesObject);
         root.add("pending", pendingArray);
         Files.writeString(this.cacheFile.toPath(), GSON.toJson(root));
      } catch (IOException e) {
         SixToolsAddon.LOG.warn("social-sync could not save its uuid cache", e);
      }
   }
}
