package gamerguy11.sixtoolsaddon.mixin.accessor;

import java.util.OptionalInt;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FireworkRocketEntity.class)
public interface AutoWaspFireworkAccessor {
    @Accessor("SHOOTER_ENTITY_ID")
    static TrackedData<OptionalInt> autowasp$attachedTarget() {
        throw new AssertionError();
    }
}
