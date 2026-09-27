package gamerguy11.sixtoolsaddon.modules.utility;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import gamerguy11.sixtoolsaddon.deathlogger.DeathLogStore;
import gamerguy11.sixtoolsaddon.deathlogger.DeathRecord;
import gamerguy11.sixtoolsaddon.gui.DeathLogScreen;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class DeathLogger extends Module {
    private int lastX, lastY, lastZ;
    private String lastDimension = "Overworld";
    private boolean hasPosition;
    private boolean wasAlive = true;

    public DeathLogger() {
        super(
            SixToolsAddon.CATEGORY,
            "death-logger",
            "Logs the coordinates and dimension of every death to a text file."
        );
    }

    @Override
    public void onActivate() {
        hasPosition = false;
        wasAlive = true;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        boolean alive = mc.player.getHealth() > 0 && !mc.player.isDead();

        if (alive) {
            lastX = mc.player.getBlockX();
            lastY = mc.player.getBlockY();
            lastZ = mc.player.getBlockZ();
            lastDimension = PlayerUtils.getDimension().name();
            hasPosition = true;
        } else if (wasAlive && hasPosition) {

            DeathLogStore.append(DeathRecord.now(lastX, lastY, lastZ, lastDimension));
        }

        wasAlive = alive;
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WButton button = theme.button("View Death Log");
        button.action = () -> mc.setScreen(new DeathLogScreen(theme));
        return button;
    }
}
