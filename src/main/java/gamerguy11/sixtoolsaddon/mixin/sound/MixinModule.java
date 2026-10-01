package gamerguy11.sixtoolsaddon.mixin.sound;

import gamerguy11.sixtoolsaddon.modules.utility.SoundEditor;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
   value = {Module.class},
   remap = false
)
public class MixinModule {
   @Inject(
      method = {"toggle"},
      at = {@At("TAIL")}
   )
   private void sixtoolsaddon$onToggle(CallbackInfo ci) {
      Modules modules = Modules.get();
      if (modules != null) {
         SoundEditor editor = (SoundEditor)modules.get(SoundEditor.class);
         if (editor != null) {
            editor.onModuleToggled((Module)(Object)this);
         }

      }
   }
}
