package gamerguy11.sixtoolsaddon.chat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;

final class RelayClient implements WebSocket.Listener {
   private final ChatService service;
   private final HttpClient http = HttpClient.newHttpClient();
   private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor((task) -> {
      Thread thread = new Thread(task, "6toolschat");
      thread.setDaemon(true);
      return thread;
   });
   private final AtomicBoolean connecting = new AtomicBoolean();
   private final StringBuilder buffer = new StringBuilder();
   private volatile WebSocket socket;
   private volatile boolean wanted;
   private volatile boolean ready;
   private volatile String url = "";
   private ScheduledFuture<?> pending;
   private int attempt;

   RelayClient(ChatService service) {
      this.service = service;
      this.executor.scheduleAtFixedRate(() -> {
         if (this.ready) {
            this.sendRaw("{\"t\":\"ping\"}");
         }
      }, 25L, 25L, TimeUnit.SECONDS);
   }

   boolean isWanted() {
      return this.wanted;
   }

   boolean isReady() {
      return this.ready;
   }

   void connect(String target) {
      this.url = target;
      this.wanted = true;
      this.attempt = 0;
      this.executor.execute(this::open);
   }

   synchronized void disconnect() {
      this.wanted = false;
      this.ready = false;
      if (this.pending != null) {
         this.pending.cancel(false);
      }

      WebSocket current = this.socket;
      this.socket = null;
      if (current != null) {
         current.sendClose(1000, "bye");
      }
   }

   boolean send(String json) {
      return this.ready && this.sendRaw(json);
   }

   private boolean sendRaw(String json) {
      WebSocket current = this.socket;
      if (current == null) {
         return false;
      }

      current.sendText(json, true);
      return true;
   }

   private void open() {
      if (!this.wanted || this.socket != null || !this.connecting.compareAndSet(false, true)) {
         return;
      }

      try {
         this.http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(8L)).buildAsync(URI.create(this.url), this).whenComplete((ws, error) -> {
            if (error != null) {
               this.connecting.set(false);
               this.scheduleReconnect();
            }
         });
      } catch (RuntimeException error) {
         this.connecting.set(false);
         this.service.fail("Bad relay url");
      }
   }

   private synchronized void scheduleReconnect() {
      if (!this.wanted) {
         return;
      }

      if (this.pending != null && !this.pending.isDone()) {
         return;
      }

      long delay = Math.min(60L, 2L << Math.min(this.attempt++, 5));
      this.pending = this.executor.schedule(this::open, delay, TimeUnit.SECONDS);
   }

   private void drop(WebSocket ws) {
      if (this.socket != ws) {
         return;
      }

      this.socket = null;
      this.ready = false;
      this.service.onDisconnected();
      this.scheduleReconnect();
   }

   private void dispatch(String raw) {
      JsonObject object;
      try {
         object = JsonParser.parseString(raw).getAsJsonObject();
      } catch (RuntimeException error) {
         return;
      }

      String type = object.has("t") ? object.get("t").getAsString() : "";
      if (type.equals("challenge")) {
         this.authenticate(object.get("nonce").getAsString());
         return;
      }

      if (type.equals("ready")) {
         this.ready = true;
      }

      this.service.onPacket(object);
   }

   private void authenticate(String nonce) {
      MinecraftClient mc = MinecraftClient.getInstance();
      try {
         Session session = mc.getSession();
         UUID id = session.getUuidOrNull();
         if (PremiumCheck.hasOnlineSession()) {
            try {
               mc.getApiServices().sessionService().joinServer(id, session.getAccessToken(), nonce);
            } catch (AuthenticationUnavailableException error) {
               throw error;
            } catch (AuthenticationException error) {
               this.service.fail("Mojang session rejected: " + error.getMessage());
            }
         }

         JsonObject auth = new JsonObject();
         auth.addProperty("t", "auth");
         auth.addProperty("name", session.getUsername());
         auth.addProperty("key", this.service.publicKey());
         this.sendRaw(auth.toString());
      } catch (AuthenticationUnavailableException error) {
         this.service.fail("Mojang authentication is unavailable, try again later.");
         this.disconnect();
      } catch (Exception error) {
         this.service.fail("Auth failed: " + error.getMessage());
         this.disconnect();
      }
   }

   public void onOpen(WebSocket ws) {
      this.socket = ws;
      this.connecting.set(false);
      this.attempt = 0;
      ws.request(1L);
   }

   public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
      this.buffer.append(data);
      if (last) {
         String message = this.buffer.toString();
         this.buffer.setLength(0);
         this.executor.execute(() -> this.dispatch(message));
      }

      ws.request(1L);
      return null;
   }

   public CompletionStage<?> onClose(WebSocket ws, int code, String reason) {
      if (code == 1008) {
         this.wanted = false;
         this.service.fail("Relay refused connection: " + reason + ". If you are on a cracked/offline account, the relay must be set up to accept unverified names.");
      }

      this.drop(ws);
      return null;
   }

   public void onError(WebSocket ws, Throwable error) {
      this.drop(ws);
   }

}
