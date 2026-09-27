package gamerguy11.sixtoolsaddon.gui;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import net.minecraft.client.gui.screen.Screen;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class RiskyBlocksConfirmScreen extends WindowScreen {
    private final Runnable onConfirm;
    private final Runnable onCancel;
    private final Screen previousScreen;

    public RiskyBlocksConfirmScreen(GuiTheme theme, Screen previousScreen, Runnable onConfirm, Runnable onCancel) {
        super(theme, "Are you sure?");
        this.previousScreen = previousScreen;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
    }

    @Override
    public void initWidgets() {
        add(theme.label("You're about to allow ESP/tracers on ANY block, not just storage and beds.")).expandX();
        add(theme.label("Common blocks (dirt, stone, etc.) can appear by the thousands, which can")).expandX();
        add(theme.label("hurt your FPS and makes it obvious something isn't normal vanilla behavior.")).expandX();
        add(theme.label("Only continue if you know exactly which blocks you want to add.")).expandX();

        add(theme.horizontalSeparator()).expandX();

        WHorizontalList buttons = add(theme.horizontalList()).expandX().widget();

        WButton confirm = buttons.add(theme.button("I understand, continue")).expandX().widget();
        confirm.action = () -> {
            onConfirm.run();
            mc.setScreen(previousScreen);
        };

        WButton cancel = buttons.add(theme.button("Cancel")).expandX().widget();
        cancel.action = () -> {
            onCancel.run();
            mc.setScreen(previousScreen);
        };
    }
}
