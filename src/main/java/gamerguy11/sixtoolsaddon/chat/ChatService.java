package gamerguy11.sixtoolsaddon.chat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import gamerguy11.sixtoolsaddon.gui.ChatScreen;
import gamerguy11.sixtoolsaddon.gui.ChatTab;
import gamerguy11.sixtoolsaddon.systems.enemies.Enemies;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.tabs.Tabs;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class ChatService {
   public static final ChatService INSTANCE = new ChatService();
   public static final String GENERAL = "general";
   private static final int MAX_HISTORY = 200;
   private static final int MAX_LENGTH = 256;

   public static final class Conversation {
      public final String key;
      public final String label;
      public final String peer;
      public final List<ChatMessage> messages = new ArrayList<>();
      public int unread;

      Conversation(String key, String label, String peer) {
         this.key = key;
         this.label = label;
         this.peer = peer;
      }
   }

   private final MinecraftClient mc = MinecraftClient.getInstance();
   private final Map<String, Conversation> conversations = new LinkedHashMap<>();
   private final Set<String> online = ConcurrentHashMap.newKeySet();
   private final RelayClient client = new RelayClient(this);
   private final DmCrypto crypto = new DmCrypto();
   private final Map<String, PublicKey> keys = new ConcurrentHashMap<>();
   private String activeKey = GENERAL;
   private volatile String selfName = "";
   private boolean wasConnected;

   private ChatService() {
      this.conversations.put(GENERAL, new Conversation(GENERAL, "General", null));
   }

   String publicKey() {
      return this.crypto.publicKey();
   }

   public void init() {
      MeteorClient.EVENT_BUS.subscribe(this);
   }

   @EventHandler
   private void onGameJoined(GameJoinedEvent event) {
      if (ChatConfig.get().autoConnect && ChatConfig.get().configured() && !this.client.isWanted()) {
         this.connect();
      }
   }

   public void connect() {
      if (!ChatConfig.get().configured()) {
         this.addSystem("Set the relay first: .chat url <wss://...>");
         return;
      }

      this.addSystem("Connecting to relay...");
      this.client.connect(ChatConfig.get().relayUrl);
   }

   public void disconnect() {
      this.client.disconnect();
      this.online.clear();
      this.keys.clear();
      this.selfName = "";
      this.wasConnected = false;
      this.addSystem("Disconnected");
   }

   private void ensureConnected() {
      if (!this.client.isWanted()) {
         this.connect();
      }
   }

   public Collection<Conversation> conversations() {
      return this.conversations.values();
   }

   public Conversation active() {
      return this.conversations.get(this.activeKey);
   }

   public String activeKey() {
      return this.activeKey;
   }

   public void select(String key) {
      Conversation target = this.conversations.get(key);
      if (target != null) {
         this.activeKey = key;
         target.unread = 0;
      }
   }

   public String status() {
      if (this.client.isReady()) {
         return "online (" + this.online.size() + ")";
      }

      return this.client.isWanted() ? "connecting..." : "offline";
   }

   public List<String> onlineFriends() {
      List<String> names = new ArrayList<>();
      for (String name : this.online) {
         if (!name.equalsIgnoreCase(this.selfName) && Friends.get().get(name) != null) {
            names.add(name);
         }
      }

      names.sort(String.CASE_INSENSITIVE_ORDER);
      return names;
   }

   public List<String> onlineAll() {
      List<String> names = new ArrayList<>(this.online);
      names.sort(String.CASE_INSENSITIVE_ORDER);
      return names;
   }

   public void openScreen() {
      this.mc.send(() -> this.mc.setScreen(new ChatScreen(GuiThemes.get(), Tabs.get(ChatTab.class))));
   }

   public void openGeneral() {
      this.select(GENERAL);
      this.ensureConnected();
      this.openScreen();
   }

   public boolean openDm(String name) {
      if (Friends.get().get(name) == null) {
         return false;
      }

      Conversation c = this.conversation(name);
      this.select(c.key);
      this.ensureConnected();
      this.openScreen();
      return true;
   }

   public void sendGeneral(String text) {
      this.ensureConnected();
      this.send(GENERAL, text);
   }

   public boolean sendDm(String name, String text) {
      if (Friends.get().get(name) == null) {
         return false;
      }

      this.ensureConnected();
      this.send(this.conversation(name).key, text);
      return true;
   }

   public void send(String key, String text) {
      String clean = sanitize(text);
      Conversation c = this.conversations.get(key);
      if (clean.isEmpty() || c == null) {
         return;
      }

      JsonObject packet = new JsonObject();
      packet.addProperty("t", "msg");
      packet.addProperty("ch", c.peer == null ? "general" : "dm");
      if (c.peer != null) {
         PublicKey peerKey = this.keys.get(c.peer.toLowerCase(Locale.ROOT));
         if (peerKey == null) {
            this.addSystem(c.peer + " is not online or has no encryption key");
            return;
         }

         try {
            packet.addProperty("to", c.peer);
            packet.addProperty("enc", this.crypto.encrypt(peerKey, clean));
         } catch (GeneralSecurityException error) {
            this.addSystem("Could not encrypt message");
            return;
         }
      } else {
         packet.addProperty("text", clean);
      }

      if (!this.client.send(packet.toString())) {
         this.addSystem("Not connected yet. Use .chat connect");
         return;
      }

      if (c.peer != null) {
         this.append(c, new ChatMessage(this.selfName, clean, System.currentTimeMillis(), true, false));
         this.refreshScreen();
      }
   }

   public void sendAdmin(String op, String name) {
      JsonObject packet = new JsonObject();
      packet.addProperty("t", "admin");
      packet.addProperty("op", op);
      if (name != null) {
         packet.addProperty("name", name);
      }

      if (!this.client.send(packet.toString())) {
         this.addSystem("Not connected yet. Use .chat connect");
      }
   }

   void onPacket(JsonObject packet) {
      this.mc.execute(() -> this.process(packet));
   }

   void onDisconnected() {
      this.mc.execute(() -> {
         this.online.clear();
         this.selfName = "";
         if (this.wasConnected) {
            this.wasConnected = false;
            this.addSystem("Disconnected");
         } else {
            this.refreshScreen();
         }
      });
   }

   void fail(String message) {
      this.mc.execute(() -> this.addSystem(message));
   }

   private void process(JsonObject packet) {
      switch (str(packet, "t")) {
         case "ready" -> {
            this.selfName = str(packet, "name");
            this.replaceOnline(packet);
            this.wasConnected = true;
            this.addSystem("Connected as " + this.selfName + " (" + this.online.size() + " online)");
         }
         case "presence" -> {
            this.replaceOnline(packet);
            this.refreshScreen();
         }
         case "msg" -> this.handleMessage(packet);
         case "error", "info" -> this.addSystem(str(packet, "msg"));
         default -> {
         }
      }
   }

   private void replaceOnline(JsonObject packet) {
      this.online.clear();
      JsonArray array = packet.has("online") && packet.get("online").isJsonArray() ? packet.getAsJsonArray("online") : null;
      if (array != null) {
         for (JsonElement element : array) {
            this.online.add(element.getAsString());
         }
      }

      this.keys.clear();
      if (packet.has("keys") && packet.get("keys").isJsonObject()) {
         for (Map.Entry<String, JsonElement> entry : packet.getAsJsonObject("keys").entrySet()) {
            try {
               this.keys.put(entry.getKey().toLowerCase(Locale.ROOT), DmCrypto.parse(entry.getValue().getAsString()));
            } catch (GeneralSecurityException | RuntimeException ignored) {
               gamerguy11.sixtoolsaddon.utils.Swallowed.log("ChatService:293", ignored);
            }
         }
      }
   }

   private void handleMessage(JsonObject packet) {
      String channel = str(packet, "ch");
      String from = str(packet, "from");
      String text = channel.equals("dm") ? this.decryptDm(str(packet, "enc")) : sanitize(str(packet, "text"));
      if (from.isEmpty() || text.isEmpty()) {
         return;
      }

      boolean self = from.equalsIgnoreCase(this.selfName);
      Conversation c;
      String label;
      if (channel.equals("dm")) {
         String peer = self ? str(packet, "to") : from;
         if (peer.isEmpty()) {
            return;
         }

         if (!self && (ChatConfig.get().isMuted(from) || Friends.get().get(from) == null)) {
            return;
         }

         c = this.conversation(peer);
         label = "friend";
      } else {
         if (!self && (ChatConfig.get().isMuted(from) || Enemies.get().get(from) != null)) {
            return;
         }

         c = this.conversations.get(GENERAL);
         label = "general";
      }

      this.append(c, new ChatMessage(from, text, System.currentTimeMillis(), self, false));
      boolean open = this.mc.currentScreen instanceof ChatScreen;
      if (open && c.key.equals(this.activeKey)) {
         this.refreshScreen();
         return;
      }

      if (!self) {
         c.unread++;
      }

      if (!open && ChatConfig.get().echo) {
         this.echo(label, from, text);
      }

      this.refreshScreen();
   }

   private String decryptDm(String encoded) {
      if (encoded.isEmpty()) {
         return "";
      }

      try {
         return sanitize(this.crypto.decrypt(encoded));
      } catch (GeneralSecurityException | RuntimeException error) {
         return "";
      }
   }

   private Conversation conversation(String peer) {
      String key = "dm:" + peer.toLowerCase(Locale.ROOT);
      return this.conversations.computeIfAbsent(key, (k) -> new Conversation(k, peer, peer));
   }

   private void append(Conversation c, ChatMessage message) {
      c.messages.add(message);
      if (c.messages.size() > MAX_HISTORY) {
         c.messages.remove(0);
      }
   }

   private void addSystem(String text) {
      this.append(this.active(), new ChatMessage("", text, System.currentTimeMillis(), false, true));
      if (!(this.mc.currentScreen instanceof ChatScreen) && ChatConfig.get().echo) {
         MutableText line = Text.literal("[6toolschat] ").formatted(Formatting.AQUA).append(Text.literal(text).formatted(Formatting.GRAY));
         this.mc.inGameHud.getChatHud().addMessage(line);
      }

      this.refreshScreen();
   }

   private void echo(String label, String from, String text) {
      MutableText line = Text.literal("[6toolschat] ").formatted(Formatting.AQUA)
         .append(Text.literal("[" + label + "] ").formatted(Formatting.GRAY))
         .append(Text.literal("<" + from + "> ").formatted(Formatting.WHITE))
         .append(Text.literal(text).formatted(Formatting.WHITE));
      this.mc.inGameHud.getChatHud().addMessage(line);
   }

   private void refreshScreen() {
      if (this.mc.currentScreen instanceof ChatScreen screen) {
         screen.refresh();
      }
   }

   private static String str(JsonObject object, String key) {
      return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
   }

   public static String sanitize(String text) {
      if (text == null) {
         return "";
      }

      String clean = text.replaceAll("[\\p{Cntrl}\u00a7]", "").trim();
      return clean.length() > MAX_LENGTH ? clean.substring(0, MAX_LENGTH) : clean;
   }
}
