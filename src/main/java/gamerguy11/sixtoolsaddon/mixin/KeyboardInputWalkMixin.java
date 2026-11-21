package gamerguy11.sixtoolsaddon.mixin;

import gamerguy11.sixtoolsaddon.modules.SixToolsAutoFish;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputWalkMixin extends Input {
    @Inject(method = "tick", at = @At("TAIL"))
    private void sixtools$forceWalk(CallbackInfo ci) {
        if (!SixToolsAutoFish.forceWalk) return;
        boolean jump = SixToolsAutoFish.forceJump || this.playerInput.jump();
        this.playerInput = new PlayerInput(true, false, false, false, jump, false, true);
        this.movementVector = new Vec2f(0.0f, 1.0f);
    }
}
