package gamerguy11.sixtoolsaddon.modules.utility;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

public class VerificationLink extends Module {
    private static final Pattern LINK = Pattern.compile("https://verify\\.6b6t\\.org/[^\\s`)\\]]+");
    private static final String MARKER = "6b6t Anti Bot Protection";
    private static boolean registered;

    public VerificationLink() {
        super(SixToolsAddon.CATEGORY, "6tools-verification-link", "Shows a button to open the anti-bot verification link.");
        if (!registered) {
            registered = true;
            ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
                if (screen instanceof DisconnectedScreen) addButton(screen, width);
            });
        }
    }

    private static void addButton(Screen screen, int width) {
        VerificationLink module = Modules.get().get(VerificationLink.class);
        if (module == null || !module.isActive()) return;

        String url = null;
        int bottom = 0;
        for (Element element : screen.children()) {
            if (!(element instanceof ClickableWidget widget)) continue;
            bottom = Math.max(bottom, widget.getBottom());
            if (url != null) continue;
            String text = widget.getMessage().getString();
            if (!text.startsWith(MARKER)) continue;
            Matcher matcher = LINK.matcher(text);
            if (matcher.find()) url = matcher.group();
        }
        if (url == null) return;

        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return;
        }

        Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("Open Verification Link"), button -> Util.getOperatingSystem().open(uri))
                .dimensions(width / 2 - 100, bottom + 4, 200, 20)
                .build());
    }
}
