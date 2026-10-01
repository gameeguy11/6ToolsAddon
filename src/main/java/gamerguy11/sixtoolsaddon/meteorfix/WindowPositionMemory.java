package gamerguy11.sixtoolsaddon.meteorfix;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import gamerguy11.sixtoolsaddon.mixin.meteorfix.WWindowDragStateAccessor;
import gamerguy11.sixtoolsaddon.mixin.meteorfix.WWindowTitleAccessor;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import meteordevelopment.meteorclient.utils.Utils;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WindowPositionMemory {
   private static final Logger LOGGER = LoggerFactory.getLogger("meteorfix");
   private static final AtomicBoolean LOGGED_FIRST_LAYOUT = new AtomicBoolean(false);
   private static final AtomicBoolean LOGGED_FIRST_MOVE = new AtomicBoolean(false);
   private static final AtomicBoolean LOGGED_FIRST_RESOLVE = new AtomicBoolean(false);
   private static final ThreadLocal<Boolean> CLAMPING = ThreadLocal.withInitial(() -> false);
   private static final Gson GSON = new Gson();
   private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("meteorfix.json");
   private static final Map<String, double[]> POSITIONS = new ConcurrentHashMap();
   private static volatile boolean loaded = false;
   private static final Set<WWindow> ACTIVE_WINDOWS = Collections.newSetFromMap(new WeakHashMap());
   private static final double GAP = (double)4.0F;
   private static Field EXPANDED_FIELD;
   private static Field ANIM_PROGRESS_FIELD;
   private static Field HEADER_FIELD;
   private static Field DRAGGING_FIELD;
   private static Field CATPPUCCIN_ANIMATION_FIELD;
   private static Method CATPPUCCIN_ANIM_PROGRESS_METHOD;
   private static boolean reflectionReady = false;
   private static boolean catppuccinReflectionReady = false;

   private WindowPositionMemory() {
   }

   public static boolean isClamping() {
      return Boolean.TRUE.equals(CLAMPING.get());
   }

   public static void afterLayout(WWindow window) {
      ensureLoaded();
      ACTIVE_WINDOWS.add(window);
      if (LOGGED_FIRST_LAYOUT.compareAndSet(false, true)) {
         LOGGER.info("[MeteorFix] Window layout hook is active.");
      }

      String key = keyFor(window);
      if (key != null) {
         double[] saved = (double[])POSITIONS.get(key);
         if (saved != null) {
            double dx = saved[0] - window.x;
            double dy = saved[1] - window.y;
            if (dx != (double)0.0F || dy != (double)0.0F) {
               CLAMPING.set(true);

               try {
                  window.move(dx, dy);
               } finally {
                  CLAMPING.set(false);
               }
            }
         } else {
            POSITIONS.put(key, new double[]{window.x, window.y});
            save();
         }
      }

      clampToScreen(window);
   }

   public static void afterMove(WWindow window) {
      if (!isClamping()) {
         if (LOGGED_FIRST_MOVE.compareAndSet(false, true)) {
            LOGGER.info("[MeteorFix] Window move hook is active.");
         }

         clampToScreen(window);
         String key = keyFor(window);
         if (key != null) {
            POSITIONS.put(key, new double[]{window.x, window.y});
         }
      }
   }

   public static void afterDragEnd(WWindow window) {
      if (!isClamping()) {
         if (LOGGED_FIRST_RESOLVE.compareAndSet(false, true)) {
            LOGGER.info("[MeteorFix] Overlap-resolve-on-release is active.");
         }

         resolveOverlaps(window);
         clampToScreen(window);
         ((WWindowDragStateAccessor)window).meteorfix$setMoved(false);
         ((WWindowDragStateAccessor)window).meteorfix$setMovedX(window.x);
         ((WWindowDragStateAccessor)window).meteorfix$setMovedY(window.y);
         String key = keyFor(window);
         if (key != null) {
            POSITIONS.put(key, new double[]{window.x, window.y});
            save();
         }
      }
   }

   public static void clampToScreen(WWindow window) {
      double topBar = (double)40.0F;
      double headerH = headerHeight(window);
      double maxX = (double)Utils.getWindowWidth() - window.width;
      double maxY = (double)Utils.getWindowHeight() - headerH;
      double newX = window.x;
      double newY = window.y;
      if (newX > maxX) {
         newX = Math.max((double)0.0F, maxX);
      }

      if (newY > maxY) {
         newY = Math.max((double)40.0F, maxY);
      }

      if (newX < (double)0.0F) {
         newX = (double)0.0F;
      }

      if (newY < (double)40.0F) {
         newY = (double)40.0F;
      }

      double dx = newX - window.x;
      double dy = newY - window.y;
      if (dx != (double)0.0F || dy != (double)0.0F) {
         CLAMPING.set(true);

         try {
            window.move(dx, dy);
         } finally {
            CLAMPING.set(false);
         }

      }
   }

   public static void resolveOverlaps(WWindow window) {
      List<WWindow> others = collectSiblings(window);
      if (!others.isEmpty()) {
         double originX = window.x;
         double originY = window.y;
         double ww = window.width;
         double wh = effectiveHeight(window);
         if (!isFree(originX, originY, ww, wh, others)) {
            double topBar = (double)40.0F;
            double screenW = (double)Utils.getWindowWidth();
            double screenH = (double)Utils.getWindowHeight();
            double minX = (double)0.0F;
            double minY = (double)40.0F;
            double maxX = Math.max((double)0.0F, screenW - ww);
            double maxY = Math.max((double)40.0F, screenH - headerHeight(window));
            double bestX = originX;
            double bestY = originY;
            double bestDist = Double.POSITIVE_INFINITY;
            boolean found = false;

            for(WWindow other : others) {
               double ox = other.x;
               double oy = other.y;
               double ow = other.width;
               double oh = effectiveHeight(other);
               double[][] sideCandidates = new double[][]{{ox + ow + (double)4.0F, originY}, {ox - ww - (double)4.0F, originY}, {originX, oy + oh + (double)4.0F}, {originX, oy - wh - (double)4.0F}, {ox + ow + (double)4.0F, oy}, {ox - ww - (double)4.0F, oy}, {ox, oy + oh + (double)4.0F}, {ox, oy - wh - (double)4.0F}, {ox + ow + (double)4.0F, oy + oh - wh}, {ox - ww - (double)4.0F, oy + oh - wh}, {ox + ow - ww, oy + oh + (double)4.0F}, {ox + ow - ww, oy - wh - (double)4.0F}};

               for(double[] c : sideCandidates) {
                  double cx = clamp(c[0], (double)0.0F, maxX);
                  double cy = clamp(c[1], (double)40.0F, maxY);
                  if (isFree(cx, cy, ww, wh, others)) {
                     double d = dist2(cx, cy, originX, originY);
                     if (d < bestDist) {
                        bestDist = d;
                        bestX = cx;
                        bestY = cy;
                        found = true;
                     }
                  }
               }
            }

            double step = (double)8.0F;
            int maxRings = 80;

            for(int ring = 1; ring <= 80; ++ring) {
               double radius = (double)ring * (double)8.0F;
               int samples = Math.max(8, (int)((Math.PI * 2D) * radius / (double)8.0F));

               for(int s = 0; s < samples; ++s) {
                  double angle = (Math.PI * 2D) * (double)s / (double)samples;
                  double cx = clamp(originX + Math.cos(angle) * radius, (double)0.0F, maxX);
                  double cy = clamp(originY + Math.sin(angle) * radius, (double)40.0F, maxY);
                  if (isFree(cx, cy, ww, wh, others)) {
                     double d = dist2(cx, cy, originX, originY);
                     if (d < bestDist) {
                        bestDist = d;
                        bestX = cx;
                        bestY = cy;
                        found = true;
                     }
                  }
               }

               if (found && bestDist <= radius * radius) {
                  break;
               }
            }

            if (found) {
               double dx = bestX - window.x;
               double dy = bestY - window.y;
               if (!(Math.abs(dx) < (double)0.5F) || !(Math.abs(dy) < (double)0.5F)) {
                  CLAMPING.set(true);

                  try {
                     window.move(dx, dy);
                  } finally {
                     CLAMPING.set(false);
                  }

               }
            }
         }
      }
   }

   private static List<WWindow> collectSiblings(WWindow window) {
      List<WWindow> others = new ArrayList();

      for(WWindow other : ACTIVE_WINDOWS) {
         if (other != null && other != window && other.parent != null && (window.parent == null || other.parent == window.parent)) {
            others.add(other);
         }
      }

      return others;
   }

   private static boolean isFree(double x, double y, double w, double h, List<WWindow> others) {
      for(WWindow other : others) {
         double ox = other.x;
         double oy = other.y;
         double ow = other.width;
         double oh = effectiveHeight(other);
         if (x < ox + ow + (double)4.0F && x + w > ox - (double)4.0F && y < oy + oh + (double)4.0F && y + h > oy - (double)4.0F) {
            return false;
         }
      }

      return true;
   }

   private static double dist2(double x1, double y1, double x2, double y2) {
      double dx = x1 - x2;
      double dy = y1 - y2;
      return dx * dx + dy * dy;
   }

   private static double clamp(double v, double lo, double hi) {
      if (v < lo) {
         return lo;
      } else {
         return v > hi ? hi : v;
      }
   }

   private static double headerHeight(WWindow window) {
      if (!reflectionReady) {
         return (double)28.0F;
      } else {
         try {
            Object rawHeader = HEADER_FIELD.get(window);
            if (rawHeader instanceof WWidget) {
               WWidget header = (WWidget)rawHeader;
               if (header.height > (double)0.0F) {
                  return header.height;
               }
            }
         } catch (Throwable var3) {
         }

         return Math.min((double)28.0F, window.height > (double)0.0F ? window.height : (double)28.0F);
      }
   }

   public static double effectiveHeight(WWindow window) {
      if (isCatppuccinWindow(window) && catppuccinReflectionReady) {
         try {
            Object anim = CATPPUCCIN_ANIMATION_FIELD.get(window);
            double progress = ((Number)CATPPUCCIN_ANIM_PROGRESS_METHOD.invoke(anim)).doubleValue();
            double hh = headerHeight(window);
            if (progress >= 0.999) {
               return window.height;
            }

            return Math.max(hh, hh + (window.height - hh) * Math.max((double)0.0F, Math.min((double)1.0F, progress)));
         } catch (Throwable var7) {
         }
      }

      if (!reflectionReady) {
         return Math.min((double)28.0F, window.height > (double)0.0F ? window.height : (double)28.0F);
      } else {
         try {
            boolean expanded = EXPANDED_FIELD.getBoolean(window);
            double animProgress = ANIM_PROGRESS_FIELD.getDouble(window);
            double hh = headerHeight(window);
            return expanded && animProgress >= 0.999 ? window.height : (window.height - hh) * animProgress + hh;
         } catch (Throwable var6) {
            return Math.min((double)28.0F, window.height > (double)0.0F ? window.height : (double)28.0F);
         }
      }
   }

   private static boolean isCatppuccinWindow(WWindow window) {
      return window.getClass().getName().contains("Catppuccin");
   }

   private static String keyFor(WWindow window) {
      String id = window.id;
      if (id != null && !id.isBlank()) {
         return "id:" + id;
      } else {
         String title = safeTitle(window);
         return title != null && !title.isBlank() ? "title:" + title : null;
      }
   }

   private static String safeTitle(WWindow window) {
      try {
         return ((WWindowTitleAccessor)window).meteorfix$getTitle();
      } catch (Throwable var2) {
         return null;
      }
   }

   private static synchronized void ensureLoaded() {
      if (!loaded) {
         loaded = true;

         try {
            if (Files.exists(FILE, new LinkOption[0])) {
               String json = Files.readString(FILE, StandardCharsets.UTF_8);
               Type type = (new TypeToken() {
               }).getType();
               Map<String, double[]> onDisk = (Map)GSON.fromJson(json, type);
               if (onDisk != null) {
                  POSITIONS.putAll(onDisk);
                  LOGGER.info("[MeteorFix] Loaded {} saved window position(s).", onDisk.size());
               }
            }
         } catch (Exception e) {
            LOGGER.warn("[MeteorFix] Could not read saved positions, starting fresh.", e);
         }

      }
   }

   private static synchronized void save() {
      try {
         Files.createDirectories(FILE.getParent());
         Files.writeString(FILE, GSON.toJson(POSITIONS), StandardCharsets.UTF_8);
      } catch (IOException e) {
         LOGGER.warn("[MeteorFix] Could not save window positions.", e);
      }

   }

   static {
      try {
         EXPANDED_FIELD = WWindow.class.getDeclaredField("expanded");
         EXPANDED_FIELD.setAccessible(true);
         ANIM_PROGRESS_FIELD = WWindow.class.getDeclaredField("animProgress");
         ANIM_PROGRESS_FIELD.setAccessible(true);
         HEADER_FIELD = WWindow.class.getDeclaredField("header");
         HEADER_FIELD.setAccessible(true);
         DRAGGING_FIELD = WWindow.class.getDeclaredField("dragging");
         DRAGGING_FIELD.setAccessible(true);
         reflectionReady = true;
      } catch (Throwable t) {
         LOGGER.warn("[MeteorFix] Could not prepare reflection for WWindow state.", t);
      }

      try {
         Class<?> catWindow = Class.forName("me.pindour.catppuccin.gui.themes.catppuccin.widgets.container.WCatppuccinWindow");
         CATPPUCCIN_ANIMATION_FIELD = catWindow.getDeclaredField("animation");
         CATPPUCCIN_ANIMATION_FIELD.setAccessible(true);
         Class<?> animClass = Class.forName("me.pindour.catppuccin.api.animation.Animation");
         CATPPUCCIN_ANIM_PROGRESS_METHOD = animClass.getMethod("getProgress");
         catppuccinReflectionReady = true;
      } catch (Throwable var2) {
      }

   }
}
