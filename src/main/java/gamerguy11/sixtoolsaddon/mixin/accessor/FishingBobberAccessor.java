package gamerguy11.sixtoolsaddon.mixin.accessor;

import net.minecraft.entity.projectile.FishingBobberEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FishingBobberEntity.class)
public interface FishingBobberAccessor {
    @Accessor("caughtFish")
    boolean sixtools$hasCaughtFish();
}
