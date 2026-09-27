package gamerguy11.sixtoolsaddon.modules.utility;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import gamerguy11.sixtoolsaddon.homes.Home;
import gamerguy11.sixtoolsaddon.homes.HomeStore;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;

public class AutoReturnHome extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> defaultCooldown = sgGeneral.add(new IntSetting.Builder()
        .name("default-cooldown")
        .description("Default seconds to wait, after landing in a home, before auto-running \"/home <name>\" again. Individual homes can override this.")
        .defaultValue(60)
        .min(1)
        .sliderRange(1, 600)
        .build()
    );

    private final Setting<Boolean> protectedOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("protected-homes-only")
        .description("Only trigger for homes marked as protected. Turn off to trigger for any saved home, protected or not.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> chatFeedback = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-feedback")
        .description("Shows a countdown message when the timer starts and when it fires.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> homeCommand = sgGeneral.add(new meteordevelopment.meteorclient.settings.StringSetting.Builder()
        .name("home-command")
        .description("Command sent to return home. %name% is replaced with the home's name.")
        .defaultValue("/home %name%")
        .build()
    );

    private Home armedHome;
    private long armedAt;

    public AutoReturnHome() {
        super(
            SixToolsAddon.CATEGORY,
            "auto-return-home",
            "Auto-runs /home again after a cooldown if you get teleported into one of your saved homes."
        );
    }

    @Override
    public void onDeactivate() {
        armedHome = null;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        Home here = protectedOnly.get() ? HomeStore.protectedHomeAtPlayer() : HomeStore.anyHomeAtPlayer();

        if (here == null) {
            armedHome = null;
            return;
        }

        if (armedHome != here) {
            armedHome = here;
            armedAt = System.currentTimeMillis();

            if (chatFeedback.get()) {
                info("Landed in (highlight)%s(default) - returning in %ds unless you leave.",
                    here.name, cooldownFor(here));
            }
            return;
        }

        long elapsed = (System.currentTimeMillis() - armedAt) / 1000;
        if (elapsed < cooldownFor(here)) return;

        String cmd = homeCommand.get().replace("%name%", here.name);
        ChatUtils.sendPlayerMsg(cmd);

        if (chatFeedback.get()) {
            info("Auto-returning home from (highlight)%s(default).", here.name);
        }

        armedHome = null;
    }

    private int cooldownFor(Home home) {
        return home.overrideCooldown ? Math.max(0, home.cooldown) : defaultCooldown.get();
    }
}
