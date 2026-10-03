package gamerguy11.sixtoolsaddon.splash;

import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CustomSplashes {
   public record Splash(String text, String url) {
   }

   private static final String ADDON_ID = "sixtoolsaddon";
   private static final String GITHUB_URL = "https://github.com/gameeguy11/6ToolsAddon-";
   private static final String LATEST_API = "https://api.github.com/repos/gameeguy11/6ToolsAddon-/releases/latest";
   private static final Random RANDOM = new Random();
   private static final Map<Object, Splash> RENDERED = Collections.synchronizedMap(new WeakHashMap<>());
   private static final AtomicBoolean CHECK_STARTED = new AtomicBoolean();
   private static final String VERSION = FabricLoader.getInstance()
      .getModContainer(ADDON_ID)
      .map(container -> container.getMetadata().getVersion().getFriendlyString())
      .orElse("unknown");
   private static final List<Splash> FIXED = List.of(
      new Splash("JOIN CHICKEN CULT \u00a7#00B6B6\u00a7l\u00a7nCLICK ME", "https://discord.gg/HX6rSFg3k"),
      new Splash("Report issues here", "https://github.com/gameeguy11/6ToolsAddon-/issues"),
      new Splash("Skidded by Lucky1821", "https://www.youtube.com/watch?v=dQw4w9WgXcQ"),
      new Splash("Based anarchy mod.", null)
   );

   private static volatile Boolean upToDate;
   private static volatile Splash current;
   private static volatile long nextChange;

   private CustomSplashes() {
   }

   public static Splash pick() {
      startCheck();
      List<Splash> pool = new ArrayList<>(FIXED);
      Boolean state = upToDate;
      if (state != null) {
         pool.add(versionSplash(state));
      }

      Splash chosen = pool.get(RANDOM.nextInt(pool.size()));
      for (int attempt = 0; attempt < 10 && current != null && chosen.text().equals(current.text()); attempt++) {
         chosen = pool.get(RANDOM.nextInt(pool.size()));
      }

      current = chosen;
      nextChange = System.currentTimeMillis() + 15000L + RANDOM.nextInt(15001);
      return chosen;
   }

   public static boolean due() {
      return current != null && System.currentTimeMillis() >= nextChange;
   }

   public static Text toText(String raw) {
      MutableText result = Text.empty();
      Style style = Style.EMPTY;
      StringBuilder buffer = new StringBuilder();
      int i = 0;
      while (i < raw.length()) {
         char c = raw.charAt(i);
         if (c == '\u00a7' && i + 1 < raw.length()) {
            char next = raw.charAt(i + 1);
            Style updated = null;
            int skip = 2;
            if (next == '#' && i + 8 <= raw.length()) {
               try {
                  updated = style.withColor(Integer.parseInt(raw.substring(i + 2, i + 8), 16));
                  skip = 8;
               } catch (NumberFormatException e) {
                  updated = null;
               }
            } else {
               Formatting formatting = Formatting.byCode(next);
               if (formatting == Formatting.RESET) {
                  updated = Style.EMPTY;
               } else if (formatting != null && formatting.isColor()) {
                  updated = Style.EMPTY.withColor(formatting);
               } else if (formatting != null) {
                  updated = style.withFormatting(formatting);
               }
            }

            if (updated != null) {
               if (buffer.length() > 0) {
                  result.append(Text.literal(buffer.toString()).setStyle(style));
                  buffer.setLength(0);
               }

               style = updated;
               i += skip;
               continue;
            }
         }

         buffer.append(c);
         i++;
      }

      if (buffer.length() > 0) {
         result.append(Text.literal(buffer.toString()).setStyle(style));
      }

      return result;
   }

   public static void bind(Object renderer, Splash splash) {
      RENDERED.put(renderer, splash);
   }

   public static Splash splashFor(Object renderer) {
      return renderer == null ? null : RENDERED.get(renderer);
   }

   private static Splash versionSplash(boolean latest) {
      if (latest) {
         return new Splash("\u00a7aYou are on " + VERSION + ", it's the latest!", GITHUB_URL);
      }

      return new Splash("\u00a7cYou are on " + VERSION + ", get the newest on GitHub!", GITHUB_URL);
   }

   private static void startCheck() {
      if (!CHECK_STARTED.compareAndSet(false, true)) {
         return;
      }

      HttpRequest request = HttpRequest.newBuilder(URI.create(LATEST_API))
         .timeout(Duration.ofSeconds(5))
         .header("User-Agent", "6ToolsAddon")
         .header("Accept", "application/vnd.github+json")
         .build();

      HttpClient.newHttpClient()
         .sendAsync(request, HttpResponse.BodyHandlers.ofString())
         .thenAccept(response -> {
            if (response.statusCode() != 200) {
               return;
            }

            String tag = JsonParser.parseString(response.body()).getAsJsonObject().get("tag_name").getAsString();
            upToDate = !isNewer(tag, VERSION);
         })
         .exceptionally(error -> null);
   }

   private static boolean isNewer(String remote, String local) {
      int[] a = parts(remote);
      int[] b = parts(local);
      int length = Math.max(a.length, b.length);
      for (int i = 0; i < length; i++) {
         int x = i < a.length ? a[i] : 0;
         int y = i < b.length ? b[i] : 0;
         if (x != y) {
            return x > y;
         }
      }

      return false;
   }

   private static int[] parts(String version) {
      String[] raw = version.replaceFirst("^[^0-9]*", "").split("[^0-9]+");
      int[] out = new int[raw.length];
      for (int i = 0; i < raw.length; i++) {
         try {
            out[i] = raw[i].isEmpty() ? 0 : Integer.parseInt(raw[i]);
         } catch (NumberFormatException e) {
            out[i] = 0;
         }
      }

      return out;
   }
}
