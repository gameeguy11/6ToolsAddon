package gamerguy11.sixtoolsaddon.mixin.anarchymod;

import gamerguy11.sixtoolsaddon.anarchymod.Domains;
import gamerguy11.sixtoolsaddon.anarchymod.JoinPayload;
import java.util.logging.Logger;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ServerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ClientPlayNetworkHandler.class})
public class JoinNotifierMixin {
   private static final Logger LOGGER = Logger.getLogger("6ToolsAddon-JoinNotifier");

   @Inject(
      method = {"onGameJoin"},
      at = {@At("RETURN")}
   )
   private void afterLogin(GameJoinS2CPacket packet, CallbackInfo ci) {
      MinecraftClient client = MinecraftClient.getInstance();
      ServerInfo server = client.getCurrentServerEntry();
      if (server != null && Domains.contains(server.address)) {
         ClientPlayNetworkHandler handler = (ClientPlayNetworkHandler)(Object)this;
         String address = server.address;
         client.execute(() -> this.trySend(handler, address));
      }
   }

   private void trySend(ClientPlayNetworkHandler handler, String address) {
      try {
         ClientConnection connection = handler.getConnection();
         if (connection == null || !connection.isOpen()) {
            return;
         }

         connection.send(JoinPayload.createPacket());
      } catch (RuntimeException error) {
         LOGGER.warning("Failed to send join notification to " + address + ": " + error.getMessage());
      }

   }
}
