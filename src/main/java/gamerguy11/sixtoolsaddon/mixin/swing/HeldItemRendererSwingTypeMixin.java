package gamerguy11.sixtoolsaddon.mixin.swing;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import gamerguy11.sixtoolsaddon.modules.visual.SwingSpeed;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererSwingTypeMixin {
   @Shadow
   private ItemStack mainHand;

   @Shadow
   private ItemStack offHand;

   @Shadow
   private float equipProgressMainHand;

   @Shadow
   private float equipProgressOffHand;

   @Shadow
   @Final
   private MinecraftClient client;

   @ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/MathHelper;clamp(FFF)F", ordinal = 2))
   private float sixtools$mainHandEquip(float original) {
      SwingSpeed module = Modules.get().get(SwingSpeed.class);
      if (module == null || !module.isActive() || module.type.get() == SwingSpeed.SwingType.Vanilla || this.client.player == null) {
         return original;
      }

      boolean legacy = module.type.get() == SwingSpeed.SwingType.OneEight;
      float strength = legacy ? 1.0F : this.client.player.getAttackCooldownProgress(1.0F);
      boolean changed = !ItemStack.areItemsAndComponentsEqual(this.mainHand, this.client.player.getMainHandStack());
      float min = legacy ? -0.6F : -0.4F;
      return MathHelper.clamp((changed ? 0.0F : strength * strength * strength) - this.equipProgressMainHand, min, 0.4F);
   }

   @ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/MathHelper;clamp(FFF)F", ordinal = 3))
   private float sixtools$offHandEquip(float original) {
      SwingSpeed module = Modules.get().get(SwingSpeed.class);
      if (module == null || !module.isActive() || module.type.get() == SwingSpeed.SwingType.Vanilla || this.client.player == null) {
         return original;
      }

      boolean changed = !ItemStack.areItemsAndComponentsEqual(this.offHand, this.client.player.getOffHandStack());
      if (module.type.get() == SwingSpeed.SwingType.OneTwelve && module.shake.get() && module.consumeShakeTick()) {
         changed = true;
      }

      return MathHelper.clamp((changed ? 0.0F : 1.0F) - this.equipProgressOffHand, -0.4F, 0.4F);
   }
}
