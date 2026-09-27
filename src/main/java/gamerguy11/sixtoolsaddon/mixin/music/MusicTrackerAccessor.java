package gamerguy11.sixtoolsaddon.mixin.music;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.client.sound.MusicTracker;
import net.minecraft.client.sound.SoundInstance;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(MusicTracker.class)
public interface MusicTrackerAccessor {
    @Accessor("timeUntilNextSong")
    void setTimeUntilNextSong(int time);
    @Accessor("current")
    @Nullable
    SoundInstance getCurrent();
}