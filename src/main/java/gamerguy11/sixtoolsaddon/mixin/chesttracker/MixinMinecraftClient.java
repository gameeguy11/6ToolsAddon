package gamerguy11.sixtoolsaddon.mixin.chesttracker;

import gamerguy11.sixtoolsaddon.modules.chesttracker.ChestTrackerModule;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MixinMinecraftClient {

    /**
     * Chest Tracker silent mode: when a container was opened by the auto-opener, skip showing its screen.
     * The screen handler is already assigned to the player by then, so contents still sync and get tracked.
     */
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void sixtools$silentChestOpen(Screen screen, CallbackInfo ci) {
        if (screen == null) return;

        // setScreen runs during Minecraft's constructor (title screen), before Meteor's Modules system exists.
        Modules modules = Modules.get();
        if (modules == null) return;

        ChestTrackerModule module = modules.get(ChestTrackerModule.class);
        if (module != null && module.shouldSuppressScreen(screen)) ci.cancel();
    }
}
