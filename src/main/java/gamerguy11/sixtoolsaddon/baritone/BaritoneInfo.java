package gamerguy11.sixtoolsaddon.baritone;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

public final class BaritoneInfo {
   private BaritoneInfo() {
   }

   public static String loadedBuild() {
      List<String> parts = new ArrayList<>();
      for (String id : new String[]{BaritoneCheck.NEW_ID, BaritoneCheck.OLD_ID}) {
         FabricLoader.getInstance().getModContainer(id).ifPresent(c ->
            parts.add(id + " " + c.getMetadata().getVersion().getFriendlyString()));
      }

      return parts.isEmpty() ? "none" : String.join(" + ", parts);
   }

   public static List<String> settings(String filter) {
      return BaritoneCheck.isPresent() ? Impl.settings(filter.toLowerCase()) : List.of();
   }

   public static boolean run(String command) {
      return BaritoneCheck.isPresent() && Impl.run(command);
   }

   public static List<String> commandAndSettingNames() {
      return BaritoneCheck.isPresent() ? Impl.names() : List.of();
   }

   public static String describeSetting(String name) {
      return BaritoneCheck.isPresent() ? Impl.describe(name) : null;
   }

   public static List<String> complete(String line) {
      return BaritoneCheck.isPresent() ? Impl.complete(line) : List.of();
   }

   public static boolean hasSetting(String name) {
      return BaritoneCheck.isPresent() && Impl.hasSetting(name);
   }

   public static String value(String name) {
      return BaritoneCheck.isPresent() ? Impl.value(name) : null;
   }

   private static final class Impl {
      static List<String> settings(String filter) {
         List<String> out = new ArrayList<>();
         for (baritone.api.Settings.Setting<?> s : baritone.api.BaritoneAPI.getSettings().allSettings) {
            if (s.isJavaOnly()) {
               continue;
            }

            if (s.getName().toLowerCase().contains(filter)) {
               out.add(s.getName() + " = " + s.value);
            }
         }

         return out;
      }

      static List<String> names() {
         List<String> out = new ArrayList<>();
         baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().getRegistry().stream()
            .filter(c -> !c.hiddenFromHelp()).forEach(c -> out.addAll(c.getNames()));
         for (baritone.api.Settings.Setting<?> s : baritone.api.BaritoneAPI.getSettings().allSettings) {
            if (!s.isJavaOnly()) {
               out.add(s.getName());
            }
         }

         return out;
      }

      static String describe(String name) {
         baritone.api.Settings.Setting<?> s = baritone.api.BaritoneAPI.getSettings().byLowerName.get(name.toLowerCase());
         return s == null ? null : s.getName() + " = " + s.value + " (default " + s.defaultValue + ")";
      }

      static List<String> complete(String line) {
         List<String> out = new ArrayList<>();
         try {
            baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().tabComplete(line).forEach(out::add);
         } catch (RuntimeException error) {
            gamerguy11.sixtoolsaddon.utils.Swallowed.log("BaritoneInfo.complete", error);
         }

         if (line.indexOf(' ') < 0) {
            out.addAll(names());
         }

         return out.stream().distinct().toList();
      }

      static boolean hasSetting(String name) {
         return baritone.api.BaritoneAPI.getSettings().byLowerName.containsKey(name.toLowerCase());
      }

      static boolean run(String command) {
         return baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute(command);
      }

      static String value(String name) {
         baritone.api.Settings.Setting<?> s = baritone.api.BaritoneAPI.getSettings().byLowerName.get(name.toLowerCase());
         return s == null ? null : String.valueOf(s.value);
      }
   }
}
