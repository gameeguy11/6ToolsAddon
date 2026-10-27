package gamerguy11.sixtoolsaddon.utils;

import com.mojang.logging.LogUtils;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;

public final class Swallowed {
   private static final Logger LOG = LogUtils.getLogger();
   private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();

   private Swallowed() {
   }

   public static void log(String site, Throwable error) {
      if (SEEN.add(site)) {
         LOG.warn("[6Tools] Ignored error at {} (further occurrences logged at debug level)", site, error);
      } else {
         LOG.debug("[6Tools] Ignored error at {}: {}", site, String.valueOf(error));
      }
   }
}
