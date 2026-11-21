package gamerguy11.sixtoolsaddon.efly.autonav;

import meteordevelopment.meteorclient.settings.IVisible;
import meteordevelopment.meteorclient.settings.Setting;
import net.minecraft.nbt.NbtCompound;

public class ButtonSetting extends Setting<Boolean> {
    public Runnable action = () -> {};
    public final String buttonText;

    public ButtonSetting(String name, String description, String buttonText, IVisible visible) {
        super(name, description, false, null, null, visible);
        this.buttonText = buttonText;
    }

    @Override
    public Boolean parseImpl(String str) {
        return false;
    }

    @Override
    public boolean isValueValid(Boolean value) {
        return true;
    }

    @Override
    public NbtCompound save(NbtCompound tag) {
        return tag;
    }

    @Override
    public Boolean load(NbtCompound tag) {
        return false;
    }
}
