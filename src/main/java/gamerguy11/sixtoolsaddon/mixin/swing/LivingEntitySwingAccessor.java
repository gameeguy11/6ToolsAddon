package gamerguy11.sixtoolsaddon.mixin.swing;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({LivingEntity.class})
public interface LivingEntitySwingAccessor {
   @Accessor("handSwinging")
   boolean isHandSwinging();
}
