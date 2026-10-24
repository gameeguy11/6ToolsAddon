package gamerguy11.sixtoolsaddon.baritone;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.loader.api.FabricLoader;

public final class BaritoneCheck {
   public static final String OLD_ID = "baritone";
   public static final String NEW_ID = "baritone-meteor";
   private static final BaritoneCheck INSTANCE = new BaritoneCheck();

   private BaritoneCheck() {
   }

   public static void init() {
      MeteorClient.EVENT_BUS.subscribe(INSTANCE);
   }

   public static boolean hasOld() {
      return FabricLoader.getInstance().isModLoaded(OLD_ID);
   }

   public static boolean hasNew() {
      return FabricLoader.getInstance().isModLoaded(NEW_ID);
   }

   public static boolean isPresent() {
      return hasNew() || hasOld();
   }

   public static String warningMessage() {
      if (!isPresent()) {
         return "Baritone is not installed. 6Tools modules that use Baritone (Stash Sorter, Stash Mover, Chest Tracker auto-open) will not work. Put baritone-api-fabric-1.3.0-1.21.11.jar in your mods folder.";
      }

      return null;
   }

   @EventHandler
   private void onGameJoined(GameJoinedEvent event) {
      String message = warningMessage();
      if (message != null) {
         ChatUtils.warning(message, new Object[0]);
      }
   }
}
