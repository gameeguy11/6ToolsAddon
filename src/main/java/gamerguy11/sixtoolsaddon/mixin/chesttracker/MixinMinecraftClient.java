package gamerguy11.sixtoolsaddon.mixin.chesttracker;

import gamerguy11.sixtoolsaddon.modules.chesttracker.ChestTrackerModule;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({MinecraftClient.class})
public class MixinMinecraftClient {
   @Inject(
      method = {"setScreen"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void sixtools$silentChestOpen(Screen screen, CallbackInfo ci) {
      if (screen != null) {
         Modules modules = Modules.get();
         if (modules != null) {
            ChestTrackerModule module = (ChestTrackerModule)modules.get(ChestTrackerModule.class);
            if (module != null && module.shouldSuppressScreen(screen)) {
               ci.cancel();
            }

         }
      }
   }
}
