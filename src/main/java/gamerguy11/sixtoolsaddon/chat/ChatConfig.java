package gamerguy11.sixtoolsaddon.chat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;

public final class ChatConfig {
   private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("sixtoolsaddon").resolve("chat.json");
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static ChatConfig instance;

   public String relayUrl = "wss://sixtoolschat2.clientchat.workers.dev/ws";
   public boolean autoConnect = true;
   public boolean echo = true;
   public Set<String> muted = new HashSet<>();

   public static synchronized ChatConfig get() {
      if (instance == null) {
         instance = load();
      }

      return instance;
   }

   private static ChatConfig load() {
      try {
         if (Files.exists(FILE)) {
            ChatConfig loaded = GSON.fromJson(Files.readString(FILE), ChatConfig.class);
            if (loaded != null) {
               if (loaded.muted == null) {
                  loaded.muted = new HashSet<>();
               }

               return loaded;
            }
         }
      } catch (IOException | RuntimeException ignored) {
      }

      return new ChatConfig();
   }

   public synchronized void save() {
      try {
         Files.createDirectories(FILE.getParent());
         Files.writeString(FILE, GSON.toJson(this));
      } catch (IOException ignored) {
      }
   }

   public boolean isMuted(String name) {
      return this.muted.contains(name.toLowerCase(Locale.ROOT));
   }

   public boolean configured() {
      return this.relayUrl.startsWith("ws") && !this.relayUrl.contains("example.workers.dev");
   }
}
