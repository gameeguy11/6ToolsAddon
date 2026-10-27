package com.maparts.link.command;

import com.maparts.link.util.Links;
import com.maparts.link.util.ModChat;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConfirmLinkScreen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;

public final class MapHelpCommand {
    public static final String URL = "https://6b-maparts.com/";

    private MapHelpCommand() {}

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
                ClientCommandManager.literal("maphelp")
                        .executes(MapHelpCommand::run));
    }

    private static int run(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        MinecraftClient client = source.getClient();

        ModChat.feedback(source, Links.prefixedLink("→ ", "Open 6b-maparts.com", URL));

        client.execute(() -> client.setScreen(new ConfirmLinkScreen(confirmed -> {
            if (confirmed) {
                Util.getOperatingSystem().open(URL);
            }
            client.setScreen(null);
        }, URL, true)));

        return Command.SINGLE_SUCCESS;
    }
}
