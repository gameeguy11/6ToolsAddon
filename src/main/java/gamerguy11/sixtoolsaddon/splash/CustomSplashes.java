package gamerguy11.sixtoolsaddon.splash;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class CustomSplashes {
   private static final List<String> SPLASHES = new ArrayList<>();
   private static final Random RANDOM;

   private CustomSplashes() {
   }

   public static String pick() {
      return SPLASHES.isEmpty() ? null : (String)SPLASHES.get(RANDOM.nextInt(SPLASHES.size()));
   }

   static {
      SPLASHES.add("JOIN CHICKEN CULT https://discord.gg/HX6rSFg3k");
      SPLASHES.add("FUCK MOJANG");
      SPLASHES.add("Skidded by Lucky1821");
      SPLASHES.add("Based anarchy mod.");
      RANDOM = new Random();
   }
}
