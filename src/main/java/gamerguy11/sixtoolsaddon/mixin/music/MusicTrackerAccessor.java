package gamerguy11.sixtoolsaddon.mixin.music;

import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.MusicTracker;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({MusicTracker.class})
public interface MusicTrackerAccessor {
   @Accessor("timeUntilNextSong")
   void setTimeUntilNextSong(int var1);

   @Accessor("current")
   @Nullable SoundInstance getCurrent();
}
