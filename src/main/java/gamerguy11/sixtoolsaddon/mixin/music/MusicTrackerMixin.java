package gamerguy11.sixtoolsaddon.mixin.music;

import gamerguy11.sixtoolsaddon.modules.MusicTweaks;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.sound.MusicTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({MusicTracker.class})
public class MusicTrackerMixin {
   @Inject(
      method = {"tick"},
      at = {@At("TAIL")}
   )
   private void mixinTick(CallbackInfo ci) {
      Modules modules = Modules.get();
      if (modules != null) {
         MusicTweaks tweaks = (MusicTweaks)modules.get(MusicTweaks.class);
         if (tweaks != null && tweaks.isActive()) {
            boolean currentlyPlaying = ((MusicTrackerAccessor)(Object)this).getCurrent() != null;
            if (!currentlyPlaying) {
               tweaks.nullifyCurrentType();
            }

            if (tweaks.overrideDelay() && currentlyPlaying) {
               ((MusicTrackerAccessor)(Object)this).setTimeUntilNextSong(tweaks.getTimeUntilNextSong());
            }

         }
      }
   }
}
