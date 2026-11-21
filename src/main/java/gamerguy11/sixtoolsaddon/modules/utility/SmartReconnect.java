package gamerguy11.sixtoolsaddon.modules.utility;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.GameMenuScreen;

public class SmartReconnect extends Module {
    private boolean menuOpenLastTick;
    private boolean pausedByUs;

    public SmartReconnect() {
        super(SixToolsAddon.CATEGORY, "smart-reconnect", "Auto Reconnect only reconnects after a kick or lost connection, not when you leave the world yourself.");
    }

    @Override
    public void onDeactivate() {
        restore();
        menuOpenLastTick = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {

        if (mc.world != null) menuOpenLastTick = mc.currentScreen instanceof GameMenuScreen;
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (!menuOpenLastTick) return;
        menuOpenLastTick = false;
        AutoReconnect reconnect = Modules.get().get(AutoReconnect.class);
        if (reconnect != null && reconnect.isActive()) {
            pausedByUs = true;
            reconnect.toggle();
        }
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        menuOpenLastTick = false;
        restore();
    }

    private void restore() {
        if (!pausedByUs) return;
        pausedByUs = false;
        AutoReconnect reconnect = Modules.get().get(AutoReconnect.class);
        if (reconnect != null && !reconnect.isActive()) reconnect.toggle();
    }
}
