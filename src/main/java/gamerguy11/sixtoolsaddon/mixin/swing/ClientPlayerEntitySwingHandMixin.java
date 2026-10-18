package gamerguy11.sixtoolsaddon.mixin.swing;

import com.mojang.authlib.GameProfile;
import gamerguy11.sixtoolsaddon.modules.visual.SwingSpeed;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntitySwingHandMixin extends AbstractClientPlayerEntity {
   @Shadow
   @Final
   public ClientPlayNetworkHandler networkHandler;

   public ClientPlayerEntitySwingHandMixin(ClientWorld world, GameProfile profile) {
      super(world, profile);
   }

   @Inject(method = "swingHand", at = @At("HEAD"), cancellable = true)
   private void sixtools$swingHand(Hand hand, CallbackInfo ci) {
      SwingSpeed module = Modules.get().get(SwingSpeed.class);
      if (module == null || !module.isActive()) {
         return;
      }

      SwingSpeed.HandMode mode = module.hand.get();
      if (mode != SwingSpeed.HandMode.None) {
         switch (mode) {
            case Default -> super.swingHand(hand);
            case Mainhand -> super.swingHand(Hand.MAIN_HAND);
            case Offhand -> super.swingHand(Hand.OFF_HAND);
            case Both -> {
               super.swingHand(Hand.MAIN_HAND);
               super.swingHand(Hand.OFF_HAND);
            }
            default -> {
            }
         }

         if (mode == SwingSpeed.HandMode.Packet || !module.noPacket.get()) {
            this.networkHandler.sendPacket(new HandSwingC2SPacket(hand));
         }
      }

      ci.cancel();
   }
}
