package gamerguy11.sixtoolsaddon.debug;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class SyncDebug {
   private static final Pattern JAR_NAME = Pattern.compile("(?i)(^|[^a-z])(mio|rusher)");
   private static final Pattern CLASS_HINT = Pattern.compile("(?i)(social|friend|relation|enem|ally|allies)");
   private static final Pattern GETTER_HINT = Pattern.compile("(?i)(relation|friend|social|enem)");
   private static final Pattern PREFERRED = Pattern.compile("(?i)(manager|handler|store|registry|system|socials?|friends?|relations?)$");
   private static final String RUSHER_API = "org.rusherhack.client.api.RusherHackAPI";
   private static final int MAX_CLASS_NAMES = 120;
   private static final int MAX_REFLECTED = 14;

   private SyncDebug() {
   }

   public static Path run(Path runDir, ClassLoader loader) throws IOException {
      List<String> out = new ArrayList<>();
      out.add("=== sixtools sync debug ===");
      out.add("run dir: " + runDir);
      out.add("");

      rusherApi(loader, out);
      modJars(runDir.resolve("mods"), loader, out);
      dataFiles(runDir, out);

      Path file = runDir.resolve("sixtools-debug.txt");
      Files.write(file, out);
      return file;
   }


   private static void rusherApi(ClassLoader loader, List<String> out) {
      out.add("--- RusherHack plugin API ---");
      Class<?> api = load(RUSHER_API, loader);
      if (api == null) {
         out.add("not loaded: " + RUSHER_API);
         out.add("");
         return;
      }

      out.add("loaded " + RUSHER_API);
      List<Method> getters = new ArrayList<>();
      for (Method method : api.getMethods()) {
         if (!Modifier.isStatic(method.getModifiers())) {
            continue;
         }

         out.add("  static " + describe(method));
         if (method.getParameterCount() == 0 && method.getName().startsWith("get") && GETTER_HINT.matcher(method.getName()).find()) {
            getters.add(method);
         }
      }

      for (Method getter : getters) {
         out.add("");
         out.add("  calling " + getter.getName() + "()");
         try {
            Object value = getter.invoke(null);
            if (value == null) {
               out.add("    -> null");
               continue;
            }

            out.add("    -> " + value.getClass().getName());
            for (Class<?> type : interfacesAndSelf(value.getClass())) {
               out.add("    type " + type.getName());
               for (Method method : type.getMethods()) {
                  if (method.getDeclaringClass() != Object.class) {
                     out.add("      " + describe(method));
                  }
               }
            }
         } catch (Throwable t) {
            out.add("    -> failed: " + t);
         }
      }

      out.add("");
   }


   private static void modJars(Path mods, ClassLoader loader, List<String> out) {
      out.add("--- jars in mods folder matching mio / rusher ---");
      if (!Files.isDirectory(mods)) {
         out.add("no mods folder at " + mods);
         out.add("");
         return;
      }

      List<Path> jars = new ArrayList<>();
      try (Stream<Path> stream = Files.list(mods)) {
         stream.filter(path -> path.getFileName().toString().toLowerCase().endsWith(".jar"))
            .filter(path -> JAR_NAME.matcher(path.getFileName().toString()).find())
            .sorted(Comparator.comparing(path -> path.getFileName().toString()))
            .forEach(jars::add);
      } catch (IOException e) {
         out.add("could not list mods: " + e);
         return;
      }

      if (jars.isEmpty()) {
         out.add("none found (jar file names do not contain mio/rusher)");
      }

      for (Path jar : jars) {
         out.add("");
         out.add("jar: " + jar.getFileName());
         List<String> candidates = classNames(jar, out);
         int reflected = 0;
         for (String name : candidates) {
            if (reflected >= MAX_REFLECTED) {
               break;
            }

            Class<?> type = load(name, loader);
            if (type == null) {
               continue;
            }

            reflected++;
            reflect(type, out);
         }
      }

      out.add("");
   }

   private static List<String> classNames(Path jar, List<String> out) {
      List<String> hits = new ArrayList<>();
      int total = 0;
      try (ZipFile zip = new ZipFile(jar.toFile())) {
         Enumeration<? extends ZipEntry> entries = zip.entries();
         while (entries.hasMoreElements()) {
            String entry = entries.nextElement().getName();
            if (!entry.endsWith(".class") || entry.contains("$") || entry.contains("/mixin")) {
               continue;
            }

            total++;
            String name = entry.substring(0, entry.length() - 6).replace('/', '.');
            String simple = name.substring(name.lastIndexOf('.') + 1);
            if (CLASS_HINT.matcher(simple).find() || (CLASS_HINT.matcher(name).find() && PREFERRED.matcher(simple).find())) {
               hits.add(name);
            }
         }
      } catch (IOException e) {
         out.add("  could not read jar: " + e);
         return hits;
      }

      hits.sort(Comparator.comparing((String name) -> !PREFERRED.matcher(name.substring(name.lastIndexOf('.') + 1)).find()).thenComparing(name -> name));
      out.add("  " + total + " top-level classes, " + hits.size() + " with social/friend/relation/enemy in the name");
      if (total > 0 && hits.isEmpty()) {
         out.add("  (nothing matched; the names may be obfuscated)");
      }

      List<String> shown = hits.size() > MAX_CLASS_NAMES ? hits.subList(0, MAX_CLASS_NAMES) : hits;
      for (String name : shown) {
         out.add("    " + name);
      }

      return hits;
   }

   private static void reflect(Class<?> type, List<String> out) {
      out.add("");
      out.add("  class " + type.getName());
      try {
         for (Field field : type.getDeclaredFields()) {
            out.add("    field " + Modifier.toString(field.getModifiers()) + " " + field.getType().getSimpleName() + " " + field.getName());
         }

         for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            out.add("    ctor  " + Modifier.toString(constructor.getModifiers()) + " (" + params(constructor.getParameterTypes()) + ")");
         }

         for (Method method : type.getDeclaredMethods()) {
            if (!method.isSynthetic()) {
               out.add("    " + describe(method));
            }
         }
      } catch (Throwable t) {
         out.add("    reflection failed: " + t);
      }
   }


   private static void dataFiles(Path runDir, List<String> out) {
      out.add("--- data folders ---");
      for (String name : new String[]{"mio-fabric", "mio", "rusherhack"}) {
         Path dir = runDir.resolve(name);
         if (!Files.isDirectory(dir)) {
            continue;
         }

         out.add(name + "/");
         try (Stream<Path> stream = Files.walk(dir, 3)) {
            stream.filter(Files::isRegularFile).sorted().limit(60).forEach(path -> {
               long size = 0;
               try {
                  size = Files.size(path);
               } catch (IOException ignored) {
               }

               out.add("  " + dir.relativize(path) + "  (" + size + " bytes)");
            });
         } catch (IOException e) {
            out.add("  could not list: " + e);
         }
      }

      out.add("");
   }


   private static Class<?> load(String name, ClassLoader loader) {
      try {
         return Class.forName(name, false, loader);
      } catch (Throwable t) {
         return null;
      }
   }

   private static List<Class<?>> interfacesAndSelf(Class<?> type) {
      List<Class<?>> list = new ArrayList<>();
      list.add(type);
      for (Class<?> iface : type.getInterfaces()) {
         list.add(iface);
      }

      return list;
   }

   private static String describe(Method method) {
      return Modifier.toString(method.getModifiers()) + " " + method.getReturnType().getSimpleName() + " " + method.getName() + "(" + params(method.getParameterTypes()) + ")";
   }

   private static String params(Class<?>[] types) {
      StringBuilder builder = new StringBuilder();
      for (int i = 0; i < types.length; i++) {
         if (i > 0) {
            builder.append(", ");
         }

         builder.append(types[i].getSimpleName());
      }

      return builder.toString();
   }
}
