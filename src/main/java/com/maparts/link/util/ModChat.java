package com.maparts.link.util;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

public final class ModChat {

    private static final int PREFIX_COLOR = 0xFFA500;

    private ModChat() {}

    public static MutableText prefixed(Text message) {
        return Text.literal("[6bMapArts] ")
                .styled(style -> style.withColor(PREFIX_COLOR))
                .append(message);
    }

    public static void feedback(FabricClientCommandSource source, Text message) {
        source.sendFeedback(prefixed(message));
    }

    public static void error(FabricClientCommandSource source, Text message) {
        source.sendError(prefixed(message));
    }

    public static void message(ClientPlayerEntity player, Text message) {
        player.sendMessage(prefixed(message), false);
    }
}
