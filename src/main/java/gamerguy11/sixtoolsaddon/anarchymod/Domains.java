package gamerguy11.sixtoolsaddon.anarchymod;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.net.IDN;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public final class Domains {
   private static final Logger LOGGER = Logger.getLogger("6ToolsAddon-Domains");
   private static final String DOMAINS_URL = "https://www.6b6t.org/api/anarchy-mod.json";
   private static final int MAX_RESPONSE_LENGTH = 1048576;
   private static final int MAX_REMOTE_DOMAINS = 4096;
   private static final Set<String> DEFAULT = Collections.unmodifiableSet(new HashSet(Arrays.asList("*.6b6t.org", "*.10b10t.org", "*.6b6t.cc", "*.6b6t.me", "*.7b7t.me", "*.8b8t.org", "*.8b8t.xyz", "*.alacity.net", "*.anarchypvp.pw", "*.l2x9.org", "*.simpleanarchy.org")));
   private static final Gson GSON = new Gson();
   private static final AtomicBoolean REMOTE_LOAD_STARTED = new AtomicBoolean();
   private static volatile Set<String> domains;

   private Domains() {
   }

   public static void initialize() {
      if (REMOTE_LOAD_STARTED.compareAndSet(false, true)) {
         loadRemoteAsync();
      }

   }

   private static void loadRemoteAsync() {
      try {
         HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5L)).build();
         HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://www.6b6t.org/api/anarchy-mod.json")).timeout(Duration.ofSeconds(10L)).header("Accept", "application/json").GET().build();
         client.sendAsync(request, BodyHandlers.ofString(StandardCharsets.UTF_8)).thenAccept((response) -> applyRemoteResponse(response.statusCode(), (String)response.body())).exceptionally((errorx) -> {
            LOGGER.warning("Failed to load domains from remote, using defaults: " + errorx.getMessage());
            return null;
         });
      } catch (RuntimeException error) {
         LOGGER.warning("Failed to load domains from remote, using defaults: " + error.getMessage());
      }

   }

   private static void applyRemoteResponse(int statusCode, String body) {
      if (statusCode != 200) {
         LOGGER.warning("Failed to load domains from remote, using defaults: HTTP " + statusCode);
      } else if (body != null && !body.trim().isEmpty() && body.length() <= 1048576) {
         JsonObject json = (JsonObject)GSON.fromJson(body, JsonObject.class);
         JsonArray array = json == null ? null : json.getAsJsonArray("domains");
         if (array == null) {
            LOGGER.warning("Failed to load domains from remote, using defaults: missing domains array");
         } else {
            Set<String> updated = new HashSet(DEFAULT);
            int count = 0;

            for(JsonElement element : array) {
               if (count++ >= 4096) {
                  LOGGER.warning("Remote domain list exceeded the entry limit; remaining entries were ignored");
                  break;
               }

               if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                  String entry = normalizeEntry(element.getAsString());
                  if (entry != null) {
                     updated.add(entry);
                  }
               }
            }

            domains = Collections.unmodifiableSet(updated);
         }
      } else {
         LOGGER.warning("Failed to load domains from remote, using defaults: response is empty or too large");
      }
   }

   public static boolean contains(String input) {
      String domain = normalizeHost(input);
      if (domain == null) {
         return false;
      } else {
         for(String entry : domains) {
            if (matchesNormalized(domain, entry)) {
               return true;
            }
         }

         return false;
      }
   }

   private static boolean matchesNormalized(String domain, String entry) {
      if (!entry.startsWith("*.")) {
         return domain.equals(entry);
      } else {
         String base = entry.substring(2);
         return domain.equals(base) || domain.endsWith("." + base);
      }
   }

   private static String normalizeEntry(String input) {
      if (input == null) {
         return null;
      } else {
         String trimmed = input.trim();
         boolean wildcard = trimmed.startsWith("*.");
         String host = normalizeHost(wildcard ? trimmed.substring(2) : trimmed);
         return host == null ? null : (wildcard ? "*." : "") + host;
      }
   }

   static String normalizeHost(String input) {
      if (input == null) {
         return null;
      } else {
         String host = input.trim();
         if (host.startsWith("*.")) {
            host = host.substring(2);
         }

         if (host.isEmpty()) {
            return null;
         } else {
            if (host.startsWith("[")) {
               int closingBracket = host.indexOf(93);
               if (closingBracket < 0 || !isValidPortSuffix(host.substring(closingBracket + 1))) {
                  return null;
               }

               host = host.substring(1, closingBracket);
            } else {
               int firstColon = host.indexOf(58);
               int lastColon = host.lastIndexOf(58);
               if (firstColon >= 0 && firstColon == lastColon) {
                  if (!isValidPortSuffix(host.substring(firstColon))) {
                     return null;
                  }

                  host = host.substring(0, firstColon);
               }
            }

            while(host.endsWith(".")) {
               host = host.substring(0, host.length() - 1);
            }

            if (host.isEmpty()) {
               return null;
            } else {
               try {
                  if (host.indexOf(58) >= 0) {
                     return host.toLowerCase(Locale.ROOT);
                  } else {
                     String ascii = IDN.toASCII(host, 2).toLowerCase(Locale.ROOT);
                     return !ascii.isEmpty() && ascii.length() <= 253 ? ascii : null;
                  }
               } catch (IllegalArgumentException var4) {
                  return null;
               }
            }
         }
      }
   }

   private static boolean isValidPortSuffix(String suffix) {
      if (suffix.isEmpty()) {
         return true;
      } else if (suffix.charAt(0) == ':' && suffix.length() != 1) {
         try {
            int port = Integer.parseInt(suffix.substring(1));
            return port >= 1 && port <= 65535;
         } catch (NumberFormatException var2) {
            return false;
         }
      } else {
         return false;
      }
   }

   static {
      domains = DEFAULT;
   }
}
