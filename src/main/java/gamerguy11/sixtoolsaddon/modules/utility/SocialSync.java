package gamerguy11.sixtoolsaddon.modules.utility;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gamerguy11.sixtoolsaddon.SixToolsAddon;
import gamerguy11.sixtoolsaddon.systems.enemies.Enemies;
import gamerguy11.sixtoolsaddon.systems.enemies.Enemy;
import gamerguy11.sixtoolsaddon.systems.sync.LambdaFriendsStore;
import gamerguy11.sixtoolsaddon.systems.sync.MioSocialsFile;
import gamerguy11.sixtoolsaddon.systems.sync.RusherLiveStore;
import gamerguy11.sixtoolsaddon.systems.sync.RusherRelationsFile;
import gamerguy11.sixtoolsaddon.systems.sync.SocialsMerger;
import gamerguy11.sixtoolsaddon.systems.sync.SocialsStore;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.friends.Friend;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

public class SocialSync extends Module {
   private static final Gson GSON = new Gson();
   private static final String METEOR = "meteor";
   private final SettingGroup sgGeneral;
   private final Setting<Priority> priority;
   private final Setting<Boolean> overwriteOthers;
   private final Setting<Integer> interval;
   private final Setting<Boolean> syncOnExit;
   private final Setting<Boolean> syncMio;
   private final Setting<Boolean> syncRusher;
   private final Setting<Boolean> syncLambda;
   private final Setting<String> mioPath;
   private final Setting<String> rusherPath;
   private final MioSocialsFile mio;
   private final RusherRelationsFile rusherFile;
   private final RusherLiveStore rusherLive;
   private final LambdaFriendsStore lambda;
   private final File baseFile;
   private final File legacyBaseFile;
   private Map<String, String> lastResult;
   private Map<String, Long> lastStamps;
   private Set<String> backedUp;
   private int timer;
   private boolean firstPass;
   private boolean warned;
   private boolean noStoresWarned;
   private boolean forceNow;
   private int changes;
   private volatile boolean exiting;
   private static boolean hookRegistered;

   public SocialSync() {
      super(SixToolsAddon.CATEGORY, "social-sync", "Keeps your friends and enemies in sync across Meteor, Mio, RusherHack and Lambda.");
      this.runInMainMenu = true;
      this.sgGeneral = this.settings.getDefaultGroup();
      this.priority = this.sgGeneral.add(new EnumSetting.Builder<Priority>()
         .name("priority")
         .description("Who wins when the same name is changed in several places at once. Off keeps a fixed default order. A change in the chosen client always goes through everywhere.")
         .defaultValue(Priority.Off)
         .build());
      this.overwriteOthers = this.sgGeneral.add(new BoolSetting.Builder()
         .name("overwrite-others")
         .description("Make the priority client the source of truth: the other lists are overwritten to match it exactly (also removes entries they only have).")
         .defaultValue(false)
         .visible(() -> this.priority.get() != Priority.Off)
         .build());
      this.interval = this.sgGeneral.add(new IntSetting.Builder()
         .name("interval-ticks")
         .description("How often to check for changes, in ticks.")
         .defaultValue(40)
         .min(10)
         .sliderRange(10, 200)
         .build());
      this.syncOnExit = this.sgGeneral.add(new BoolSetting.Builder()
         .name("sync-on-exit")
         .description("Mio rewrites its socials file from memory when the game closes, undoing live edits. This waits for that write, then merges everything back into Mio's file so the next launch has the right list.")
         .defaultValue(true)
         .build());
      this.syncMio = this.sgGeneral.add(new BoolSetting.Builder()
         .name("mio")
         .description("Sync with the Mio client.")
         .defaultValue(true)
         .build());
      this.syncRusher = this.sgGeneral.add(new BoolSetting.Builder()
         .name("rusherhack")
         .description("Sync friends with RusherHack.")
         .defaultValue(true)
         .build());
      this.syncLambda = this.sgGeneral.add(new BoolSetting.Builder()
         .name("lambda")
         .description("Sync friends with the Lambda client, live in memory (Lambda only has friends, no enemies).")
         .defaultValue(true)
         .build());
      this.mioPath = this.sgGeneral.add(new StringSetting.Builder()
         .name("mio-file")
         .description("Full path to Mio's socials.json. Leave empty to auto-detect in this instance's folder.")
         .defaultValue("")
         .visible(this.syncMio::get)
         .build());
      this.rusherPath = this.sgGeneral.add(new StringSetting.Builder()
         .name("rusherhack-file")
         .description("Full path to RusherHack's relations.json. Leave empty to auto-detect in this instance's folder.")
         .defaultValue("")
         .visible(this.syncRusher::get)
         .build());
      this.mio = new MioSocialsFile(this.mc.runDirectory.toPath());
      this.rusherFile = new RusherRelationsFile(this.mc.runDirectory.toPath());
      this.rusherLive = new RusherLiveStore(this.mc.runDirectory.toPath().resolve("rusherhack"));
      this.lambda = new LambdaFriendsStore(this.mc.runDirectory.toPath().resolve("lambda"), new File(MeteorClient.FOLDER, "sixtoolsaddon-uuid-cache.json"));
      this.baseFile = new File(MeteorClient.FOLDER, "sixtoolsaddon-social-sync.json");
      this.legacyBaseFile = new File(MeteorClient.FOLDER, "sixtoolsaddon-mio-sync.json");
      this.reset();
   }

   public WWidget getWidget(GuiTheme theme) {
      WButton button = theme.button("Sync now");
      button.action = this::syncNow;
      return button;
   }

   public void syncNow() {
      this.forceNow = true;
      this.changes = 0;

      try {
         this.sync();
         Priority mode = this.priority.get();
         String how = mode == Priority.Off ? "merged all lists" : "synced everything to " + mode.name();
         this.report(this.changes == 0 ? "Sync now: " + how + ", nothing needed changing." : "Sync now: " + how + ", " + this.changes + " list(s) updated.");
      } catch (Exception e) {
         SixToolsAddon.LOG.warn("social-sync manual sync failed", e);
         this.warning("Sync now failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
      } finally {
         this.forceNow = false;
      }
   }

   public void onActivate() {
      this.reset();
      this.applyPaths();
      this.registerExitHook();
      this.report(this.statusLine("Mio", this.syncMio.get(), this.mio));
      this.report(this.syncRusher.get() && this.rusherLive.exists() ? "RusherHack: found, syncing live via its API" : this.statusLine("RusherHack", this.syncRusher.get(), this.rusherFile));
      this.report(!this.syncLambda.get() ? "Lambda: disabled in settings" : this.lambda.exists() ? "Lambda: found, syncing live (friends only)" : "Lambda: NOT found (is the Lambda client installed?)");
   }

   private synchronized void registerExitHook() {
      if (hookRegistered) {
         return;
      }

      hookRegistered = true;
      Runtime.getRuntime().addShutdownHook(new Thread(this::exitSync, "sixtools-mio-exit-sync"));
   }

   private void exitSync() {
      try {
         if (!this.isActive() || !this.syncOnExit.get() || !this.syncMio.get()) {
            return;
         }

         this.exiting = true;
         this.applyPaths();
         if (!this.mio.exists()) {
            return;
         }

         long before = this.mio.lastModified();
         long deadline = System.currentTimeMillis() + 3000L;
         while (System.currentTimeMillis() < deadline && this.mio.lastModified() == before) {
            Thread.sleep(100L);
         }

         Thread.sleep(400L);
         this.forceNow = true;
         this.sync();
         SixToolsAddon.LOG.info("social-sync: exit sync finished");
      } catch (Throwable t) {
         SixToolsAddon.LOG.warn("social-sync exit sync failed", t);
      }
   }

   private void say(String message) {
      if (!this.exiting) {
         this.info(message);
      }
   }

   private void applyPaths() {
      this.mio.setOverride(this.mioPath.get());
      this.rusherFile.setOverride(this.rusherPath.get());
   }

   private SocialsStore rusher() {
      return this.rusherLive.exists() ? this.rusherLive : this.rusherFile;
   }

   private String statusLine(String label, boolean enabled, SocialsStore store) {
      if (!enabled) {
         return label + ": disabled in settings";
      }

      return store.exists() ? label + ": found at " + store.path() : label + ": NOT found (looked at " + store.path() + ")";
   }

   private void report(String message) {
      SixToolsAddon.LOG.info("social-sync: {}", message);
      this.info(message);
   }

   private void reset() {
      this.lastResult = null;
      this.lastStamps = new HashMap<>();
      this.backedUp = new HashSet<>();
      this.timer = 0;
      this.firstPass = true;
      this.warned = false;
      this.noStoresWarned = false;
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (this.syncLambda.get() && this.lambda.hasPending()) {
         try {
            this.lambda.flushPending();
         } catch (Exception ignored) {
         }
      }

      if (--this.timer > 0) {
         return;
      }

      this.timer = this.interval.get();

      try {
         this.sync();
      } catch (Exception e) {
         if (!this.warned) {
            this.warned = true;
            SixToolsAddon.LOG.warn("social-sync failed, will retry", e);
            this.warning("Sync failed: " + e.getClass().getSimpleName() + ": " + e.getMessage() + " (see latest.log)");
         }
      }
   }

   private void sync() throws IOException {
      this.applyPaths();
      List<SocialsStore> stores = new ArrayList<>();
      if (this.syncMio.get() && this.mio.exists()) {
         stores.add(this.mio);
      }

      if (this.syncRusher.get() && this.rusher().exists()) {
         stores.add(this.rusher());
      }

      if (this.syncLambda.get() && this.lambda.exists()) {
         stores.add(this.lambda);
      }

      if (stores.isEmpty()) {
         if (!this.noStoresWarned) {
            this.noStoresWarned = true;
            this.warning("Nothing to sync with: no Mio file, RusherHack file or Lambda client found. Set 'mio-file' / 'rusherhack-file' to the real paths.");
         }

         return;
      }

      this.noStoresWarned = false;
      Map<String, String> display = new HashMap<>();
      Map<String, String> meteor = this.readMeteor(display);
      Map<String, Long> stamps = this.readStamps(stores);
      if (!this.forceNow && !this.firstPass && stamps.equals(this.lastStamps) && meteor.equals(this.lastResult)) {
         return;
      }

      BaseState base = this.readBase();
      Map<String, JsonElement> raws = new HashMap<>();
      Map<String, Map<String, String>> roles = new HashMap<>();
      List<SocialsMerger.Source> sources = new ArrayList<>();
      List<String> sourceIds = new ArrayList<>();
      boolean suspicious = false;

      for (SocialsStore store : stores) {
         JsonElement raw = store.read();
         Map<String, String> parsed = new HashMap<>();
         store.parse(raw, parsed, display);
         boolean participant = base.participants.contains(store.id());
         Map<String, String> storeBase = participant ? store.project(base.state) : new HashMap<>();
         if (parsed.isEmpty() && !storeBase.isEmpty()) {
            suspicious = true;
         }

         raws.put(store.id(), raw);
         roles.put(store.id(), parsed);
         sources.add(new SocialsMerger.Source(parsed, storeBase));
         sourceIds.add(store.id());
      }

      boolean meteorParticipant = base.participants.contains(METEOR);
      if (meteor.isEmpty() && meteorParticipant && !base.state.isEmpty()) {
         suspicious = true;
      }

      sources.add(new SocialsMerger.Source(meteor, meteorParticipant ? base.state : new HashMap<>()));
      sourceIds.add(METEOR);

      String priorityId = this.priority.get().id;
      int priorityIndex = priorityId == null ? -1 : sourceIds.indexOf(priorityId);
      if (priorityIndex > 0) {
         sources.add(0, sources.remove(priorityIndex));
      }

      if (this.firstPass && suspicious && !this.forceNow) {
         this.firstPass = false;
         this.lastStamps = stamps;
         this.lastResult = meteor;
         SixToolsAddon.LOG.warn("social-sync skipped the first pass because one list was unexpectedly empty");
         return;
      }

      Map<String, String> merged = SocialsMerger.merge(sources, base.state);
      Map<String, String> result = this.applyPrimary(merged, meteor, roles);

      for (SocialsStore store : stores) {
         Map<String, String> projected = store.project(result);
         if (!projected.equals(roles.get(store.id()))) {
            if (this.backedUp.add(store.id())) {
               store.backup();
            }

            store.write(store.build(raws.get(store.id()), projected, display));
            this.changes++;
            this.say("Updated " + store.id() + " (" + projected.size() + " entries).");
         }
      }

      if (!result.equals(meteor)) {
         this.applyToMeteor(result, meteor, display);
         this.changes++;
         this.say("Updated Meteor friends/enemies from other clients.");
      }

      Set<String> participants = new HashSet<>();
      participants.add(METEOR);
      for (SocialsStore store : stores) {
         participants.add(store.id());
      }

      this.writeBase(result, participants);
      this.lastResult = result;
      this.lastStamps = this.readStamps(stores);
      this.firstPass = false;
      this.warned = false;
   }

   private Map<String, String> applyPrimary(Map<String, String> merged, Map<String, String> meteor, Map<String, Map<String, String>> roles) {
      Priority mode = this.priority.get();
      if (mode == Priority.Off || !(this.overwriteOthers.get() || (this.forceNow && !this.exiting))) {
         return merged;
      }

      if (mode == Priority.Meteor) {
         return new HashMap<>(meteor);
      }

      SocialsStore store = mode == Priority.Mio ? this.mio : mode == Priority.RusherHack ? this.rusher() : this.lambda;
      Map<String, String> primaryRoles = roles.get(store.id());
      if (primaryRoles == null) {
         return merged;
      }

      return SocialsMerger.mirror(primaryRoles, merged, store.project(merged));
   }

   private Map<String, Long> readStamps(List<SocialsStore> stores) throws IOException {
      Map<String, Long> stamps = new HashMap<>();
      for (SocialsStore store : stores) {
         stamps.put(store.id(), store.lastModified());
      }

      return stamps;
   }

   private Map<String, String> readMeteor(Map<String, String> display) {
      Map<String, String> meteor = new HashMap<>();

      for (Enemy enemy : Enemies.get()) {
         String key = enemy.getName().toLowerCase(Locale.ROOT);
         meteor.put(key, SocialsStore.ENEMY);
         display.put(key, enemy.getName());
      }

      for (Friend friend : Friends.get()) {
         String key = friend.getName().toLowerCase(Locale.ROOT);
         meteor.put(key, SocialsStore.FRIEND);
         display.put(key, friend.getName());
      }

      return meteor;
   }

   private void applyToMeteor(Map<String, String> result, Map<String, String> meteor, Map<String, String> display) {
      for (Map.Entry<String, String> entry : result.entrySet()) {
         String name = display.getOrDefault(entry.getKey(), entry.getKey());
         if (SocialsStore.FRIEND.equals(entry.getValue())) {
            this.removeEnemy(name);
            if (Friends.get().get(name) == null) {
               Friends.get().add(new Friend(name));
            }
         } else {
            this.removeFriend(name);
            if (!Enemies.get().isEnemy(name)) {
               Enemies.get().add(new Enemy(name));
            }
         }
      }

      List<String> gone = new ArrayList<>();
      for (String key : meteor.keySet()) {
         if (!result.containsKey(key)) {
            gone.add(key);
         }
      }

      for (String key : gone) {
         String name = display.getOrDefault(key, key);
         this.removeFriend(name);
         this.removeEnemy(name);
      }
   }

   private void removeFriend(String name) {
      Friend friend = Friends.get().get(name);
      if (friend != null) {
         Friends.get().remove(friend);
      }
   }

   private void removeEnemy(String name) {
      Enemy enemy = Enemies.get().get(name);
      if (enemy != null) {
         Enemies.get().remove(enemy);
      }
   }

   private BaseState readBase() {
      BaseState base = new BaseState();
      File stateFile = this.baseFile.isFile() ? this.baseFile : this.legacyBaseFile;
      if (!stateFile.isFile()) {
         return base;
      }

      try {
         JsonElement root = JsonParser.parseString(Files.readString(stateFile.toPath()));
         if (!root.isJsonObject()) {
            return base;
         }

         JsonObject object = root.getAsJsonObject();
         if (object.has("state") && object.get("state").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : object.getAsJsonObject("state").entrySet()) {
               base.state.put(entry.getKey(), entry.getValue().getAsString());
            }

            if (object.has("participants") && object.get("participants").isJsonArray()) {
               for (JsonElement element : object.getAsJsonArray("participants")) {
                  base.participants.add(element.getAsString());
               }
            }
         } else {
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
               base.state.put(entry.getKey(), entry.getValue().getAsString());
            }

            base.participants.add(METEOR);
            base.participants.add("mio");
         }
      } catch (Exception e) {
         SixToolsAddon.LOG.warn("social-sync could not read its state file", e);
         return new BaseState();
      }

      return base;
   }

   private void writeBase(Map<String, String> state, Set<String> participants) throws IOException {
      JsonObject stateObject = new JsonObject();
      for (Map.Entry<String, String> entry : state.entrySet()) {
         stateObject.addProperty(entry.getKey(), entry.getValue());
      }

      JsonArray participantArray = new JsonArray();
      for (String participant : participants) {
         participantArray.add(participant);
      }

      JsonObject root = new JsonObject();
      root.add("state", stateObject);
      root.add("participants", participantArray);
      Files.writeString(this.baseFile.toPath(), GSON.toJson(root));
   }

   public enum Priority {
      Off(null),
      Meteor(METEOR),
      Mio("mio"),
      RusherHack("rusherhack"),
      Lambda("lambda");

      private final String id;

      Priority(String id) {
         this.id = id;
      }
   }

   private static final class BaseState {
      private final Map<String, String> state = new HashMap<>();
      private final Set<String> participants = new HashSet<>();
   }
}
