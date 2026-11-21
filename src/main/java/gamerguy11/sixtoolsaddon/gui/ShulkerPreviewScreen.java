package gamerguy11.sixtoolsaddon.gui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

public class ShulkerPreviewScreen extends ShulkerBoxScreen {
    private final Screen parent;

    public ShulkerPreviewScreen(ShulkerBoxScreenHandler handler, PlayerInventory inventory, Text title, Screen parent) {
        super(handler, inventory, title);
        this.parent = parent;
    }

    @Override
    protected void onMouseClick(Slot slot, int slotId, int button, SlotActionType actionType) {
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(this.parent);
    }
}
