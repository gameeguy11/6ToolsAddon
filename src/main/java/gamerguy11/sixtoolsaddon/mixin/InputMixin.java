package gamerguy11.sixtoolsaddon.mixin;

import gamerguy11.sixtoolsaddon.accessor.InputAccessor;
import net.minecraft.util.math.Vec2f;
import net.minecraft.client.input.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin({Input.class})
public abstract class InputMixin implements InputAccessor {
   @Shadow
   public abstract Vec2f getMovementInput();

   public float getMovementForward() {
      return this.getMovementInput().y;
   }

   public void setMovementForward(float value) {
   }

   public float getMovementSideways() {
      return this.getMovementInput().x;
   }

   public void setMovementSideways(float value) {
   }
}
