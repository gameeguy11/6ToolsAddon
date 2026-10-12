package gamerguy11.sixtoolsaddon.chat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;

public final class PremiumCheck {
   private PremiumCheck() {
   }

   public static boolean hasOnlineSession() {
      Session session = MinecraftClient.getInstance().getSession();
      String token = session.getAccessToken();
      return session.getUuidOrNull() != null && token != null && token.length() >= 20;
   }
}
