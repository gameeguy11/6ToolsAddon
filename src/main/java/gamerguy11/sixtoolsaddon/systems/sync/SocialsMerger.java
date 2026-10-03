package gamerguy11.sixtoolsaddon.systems.sync;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class SocialsMerger {
   private SocialsMerger() {
   }

   public record Source(Map<String, String> roles, Map<String, String> base) {
   }

   public static Map<String, String> merge(List<Source> sources, Map<String, String> base) {
      Set<String> names = new HashSet<>(base.keySet());

      for (Source source : sources) {
         names.addAll(source.roles().keySet());
         names.addAll(source.base().keySet());
      }

      Map<String, String> result = new LinkedHashMap<>();

      for (String name : names) {
         String resolved = base.get(name);

         for (Source source : sources) {
            String current = source.roles().get(name);
            if (!Objects.equals(current, source.base().get(name))) {
               resolved = current;
               break;
            }
         }

         if (resolved != null) {
            result.put(name, resolved);
         }
      }

      return result;
   }

   public static Map<String, String> mirror(Map<String, String> primary, Map<String, String> merged, Map<String, String> visible) {
      Map<String, String> result = new HashMap<>(primary);

      for (Map.Entry<String, String> entry : merged.entrySet()) {
         if (!visible.containsKey(entry.getKey())) {
            result.putIfAbsent(entry.getKey(), entry.getValue());
         }
      }

      return result;
   }
}
