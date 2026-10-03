package gamerguy11.sixtoolsaddon.modules.utility;

import com.google.gson.Gson;
import gamerguy11.sixtoolsaddon.SixToolsAddon;
import gamerguy11.sixtoolsaddon.utils.ChatNames;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;

public class DiscordNotifier extends Module {
   private static final Logger LOGGER = Logger.getLogger("6ToolsAddon-DiscordNotifier");
   private static final Gson GSON = new Gson();
   private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10L)).build();
   private static final int FLUSH_INTERVAL_SECONDS = 2;
   private static final int DISCORD_CONTENT_LIMIT = 1900;
   private static final int MAX_QUEUED_LINES = 500;
   private final SettingGroup sgGeneral;
   private final Setting<Boolean> sendChat;
   private final Setting<Boolean> sendCoords;
   private final Setting<Boolean> ignoreOwnMessages;
   private final Setting<String> coordPattern;
   private final Setting<Boolean> sendDeathCoords;
   private final Setting<Boolean> sendKills;
   private final Setting<Boolean> sendKilledBy;
   private final Setting<String> deathPattern;
   private final Setting<String> webhookUrl;
   private Pattern compiledCoordPattern;
   private String compiledCoordFrom;
   private Pattern compiledDeathPattern;
   private String compiledDeathFrom;
   private final Queue<String> queue;
   private ScheduledExecutorService scheduler;

   public DiscordNotifier() {
      super(SixToolsAddon.CATEGORY, "discord-notifier", "Forwards chat and/or coordinates shared by other players to a Discord webhook.");
      this.sgGeneral = this.settings.getDefaultGroup();
      this.sendChat = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("send-chat")).description("Forwards every chat message to your Discord webhook.")).defaultValue(false)).build());
      this.sendCoords = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("send-coords")).description("Forwards messages from other players that contain coordinates to your Discord webhook.")).defaultValue(true)).build());
      this.ignoreOwnMessages = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("ignore-own-messages")).description("Doesn't forward chat messages you sent yourself.")).defaultValue(true)).build());
      this.coordPattern = this.sgGeneral.add(((StringSetting.Builder)((StringSetting.Builder)((StringSetting.Builder)(new StringSetting.Builder()).name("coord-pattern")).description("Regex used to detect coordinates in a chat message.")).defaultValue("-?\\d{1,7}[,\\s]+-?\\d{1,4}[,\\s]+-?\\d{1,7}")).build());
      this.sendDeathCoords = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("send-death-coords")).description("Forwards your coordinates to your Discord webhook whenever you die.")).defaultValue(false)).build());
      this.sendKills = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("send-kills")).description("Forwards the name of any player you killed to your Discord webhook.")).defaultValue(false)).build());
      this.sendKilledBy = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("send-killed-by")).description("Forwards the name of whoever killed you to your Discord webhook.")).defaultValue(false)).build());
      this.deathPattern = this.sgGeneral.add(((StringSetting.Builder)((StringSetting.Builder)((StringSetting.Builder)(new StringSetting.Builder()).name("death-pattern")).description("Regex used to detect death messages. Named group <victim> must be the player who died; named group <killer>, if present, is whoever killed them.")).defaultValue("^(?:\\[[^\\]]*\\]|[^A-Za-z0-9_])*(?<victim>[A-Za-z0-9_]{1,16}) (?:was slain|was shot|was fireballed|was killed|was pummeled|was impaled|was stung to death|was poked to death|was doomed to fall|was blown up|was shot off some vertical surface|walked into a cactus|drowned|died|blew up|hit the ground too hard|fell from a high place|fell off|went up in flames|burned to death|was burned to a crisp|tried to swim in lava|suffocated in a wall|froze to death|starved to death|withered away|was struck by lightning)(?:.*?\\b(?:by|hurt|escape|fighting) (?<killer>[A-Za-z0-9_]{1,16}))?")).build());
      this.webhookUrl = this.sgGeneral.add(((StringSetting.Builder)((StringSetting.Builder)((StringSetting.Builder)((StringSetting.Builder)(new StringSetting.Builder()).name("webhook-url")).description("Discord webhook URL. Use .setdiscord set/clear to manage this.")).defaultValue("")).visible(() -> false)).build());
      this.queue = new ConcurrentLinkedQueue();
   }

   public void onActivate() {
      this.queue.clear();
      this.scheduler = Executors.newSingleThreadScheduledExecutor((r) -> {
         Thread t = new Thread(r, "6ToolsAddon-DiscordNotifier");
         t.setDaemon(true);
         return t;
      });
      this.scheduler.scheduleAtFixedRate(this::flush, 2L, 2L, TimeUnit.SECONDS);
   }

   public void onDeactivate() {
      if (this.scheduler != null) {
         this.scheduler.shutdownNow();
         this.scheduler = null;
      }

      this.queue.clear();
   }

   @EventHandler
   private void onMessage(ReceiveMessageEvent event) {
      String text = event.getMessage().getString();
      if (!text.isEmpty()) {
         boolean own = this.isOwnMessage(text);
         if (!own || !(Boolean)this.ignoreOwnMessages.get()) {
            if ((Boolean)this.sendChat.get()) {
               this.enqueue(text);
            }

            if ((Boolean)this.sendCoords.get() && this.containsCoords(text)) {
               this.enqueue("[Coords] " + text);
            }

            this.checkDeathMessage(text);
         }
      }
   }

   private boolean isOwnMessage(String text) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.getSession() == null) {
         return false;
      } else {
         String username = client.getSession().getUsername();
         if (username != null && !username.isEmpty()) {
            String prefix = "<" + username + ">";
            return text.regionMatches(true, 0, prefix, 0, prefix.length()) || ChatNames.startsWithSender(text, username);
         } else {
            return false;
         }
      }
   }

   private boolean containsCoords(String text) {
      String patternSource = (String)this.coordPattern.get();
      if (this.compiledCoordPattern == null || !patternSource.equals(this.compiledCoordFrom)) {
         try {
            this.compiledCoordPattern = Pattern.compile(patternSource);
            this.compiledCoordFrom = patternSource;
         } catch (RuntimeException e) {
            this.error("Invalid coord-pattern regex: %s", new Object[]{e.getMessage()});
            this.toggle();
            return false;
         }
      }

      return this.compiledCoordPattern.matcher(text).find();
   }

   private void checkDeathMessage(String text) {
      if ((Boolean)this.sendDeathCoords.get() || (Boolean)this.sendKills.get() || (Boolean)this.sendKilledBy.get()) {
         Pattern pattern = this.compileDeathPattern();
         if (pattern != null) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
               String victim = matcher.group("victim");
               if (victim != null && !victim.isEmpty()) {
                  String killer;
                  try {
                     killer = matcher.group("killer");
                  } catch (IllegalArgumentException var9) {
                     killer = null;
                  }

                  String localUsername = this.localUsername();
                  if (localUsername != null) {
                     boolean weAreVictim = victim.equalsIgnoreCase(localUsername);
                     boolean weAreKiller = killer != null && killer.equalsIgnoreCase(localUsername);
                     if (weAreVictim && (Boolean)this.sendDeathCoords.get()) {
                        this.enqueue("[Death] Died at " + this.formatOwnCoords());
                     }

                     if (weAreVictim && killer != null && (Boolean)this.sendKilledBy.get()) {
                        this.enqueue("[Killed By] " + killer);
                     }

                     if (weAreKiller && !weAreVictim && (Boolean)this.sendKills.get()) {
                        this.enqueue("[Kill] " + victim);
                     }

                  }
               }
            }
         }
      }
   }

   private Pattern compileDeathPattern() {
      String patternSource = (String)this.deathPattern.get();
      if (this.compiledDeathPattern == null || !patternSource.equals(this.compiledDeathFrom)) {
         try {
            this.compiledDeathPattern = Pattern.compile(patternSource);
            this.compiledDeathFrom = patternSource;
         } catch (RuntimeException e) {
            this.error("Invalid death-pattern regex: %s", new Object[]{e.getMessage()});
            this.toggle();
            return null;
         }
      }

      return this.compiledDeathPattern;
   }

   private String localUsername() {
      MinecraftClient client = MinecraftClient.getInstance();
      return client.getSession() == null ? null : client.getSession().getUsername();
   }

   private String formatOwnCoords() {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.player == null) {
         return "unknown coordinates";
      } else {
         int var10000 = client.player.getBlockX();
         return var10000 + ", " + client.player.getBlockY() + ", " + client.player.getBlockZ();
      }
   }

   private void enqueue(String line) {
      while(this.queue.size() >= 500) {
         this.queue.poll();
      }

      this.queue.offer(line);
   }

   private void flush() {
      try {
         String url = (String)this.webhookUrl.get();
         if (url == null || url.isBlank()) {
            this.queue.clear();
            return;
         }

         if (this.queue.isEmpty()) {
            return;
         }

         StringBuilder batch;
         String line;
         for(batch = new StringBuilder(); (line = (String)this.queue.peek()) != null; batch.append(line)) {
            int extra = (batch.length() > 0 ? 1 : 0) + line.length();
            if (batch.length() + extra > 1900) {
               if (batch.length() == 0) {
                  this.queue.poll();
                  batch.append(line, 0, 1900);
               }
               break;
            }

            this.queue.poll();
            if (batch.length() > 0) {
               batch.append('\n');
            }
         }

         if (batch.length() > 0) {
            this.send(url, batch.toString());
         }
      } catch (RuntimeException e) {
         LOGGER.warning("DiscordNotifier flush failed: " + e.getMessage());
      }

   }

   private void send(String url, String content) {
      Map<String, String> body = new LinkedHashMap();
      body.put("content", content);
      String json = GSON.toJson(body);
      HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(10L)).header("Content-Type", "application/json").POST(BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build();
      HTTP.sendAsync(request, BodyHandlers.discarding()).thenAccept((response) -> {
         int status = response.statusCode();
         if (status >= 300) {
            LOGGER.warning("Discord webhook returned status " + status);
         }

      }).exceptionally((error) -> {
         LOGGER.warning("Failed to send Discord webhook: " + error.getMessage());
         return null;
      });
   }

   public boolean isValidWebhookUrl(String url) {
      if (url == null) {
         return false;
      } else {
         String lower = url.trim().toLowerCase(Locale.ROOT);
         return lower.startsWith("https://discord.com/api/webhooks/") || lower.startsWith("https://discordapp.com/api/webhooks/") || lower.startsWith("https://ptb.discord.com/api/webhooks/") || lower.startsWith("https://canary.discord.com/api/webhooks/");
      }
   }

   public void setWebhookUrl(String url) {
      this.webhookUrl.set(url.trim());
   }

   public void clearWebhookUrl() {
      this.webhookUrl.set("");
      this.queue.clear();
   }

   public boolean hasWebhook() {
      String url = (String)this.webhookUrl.get();
      return url != null && !url.isBlank();
   }

   public void notifyInfo(String message, Object... args) {
      this.info(message, args);
   }

   public void notifyError(String message, Object... args) {
      this.error(message, args);
   }
}
