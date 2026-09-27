package gamerguy11.sixtoolsaddon.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import gamerguy11.sixtoolsaddon.systems.enemies.Enemies;
import gamerguy11.sixtoolsaddon.systems.enemies.Enemy;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.command.CommandSource;

import java.util.ArrayList;
import java.util.List;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class OnlinePlayersCommand extends Command {
    public OnlinePlayersCommand() {
        super("onlineplayers", "Lists your online friends or enemies.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(literal("friends").executes(ctx -> {
            listOnline(true);
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("enemies").executes(ctx -> {
            listOnline(false);
            return SINGLE_SUCCESS;
        }));
    }

    private void listOnline(boolean friendsMode) {
        if (mc.getNetworkHandler() == null) {
            error("§cNot connected!");
            return;
        }

        List<String> online = new ArrayList<>();

        for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
            String name = entry.getProfile().name();
            if (mc.player != null && name.equals(mc.player.getGameProfile().name())) continue;

            if (friendsMode) {
                if (Friends.get().get(name) != null) online.add(name);
            } else {
                Enemy enemy = Enemies.get().get(name);
                if (enemy != null) online.add(name);
            }
        }

        String label = friendsMode ? "friends" : "enemies";

        if (online.isEmpty()) {
            ChatUtils.info("No online %s.", label);
            return;
        }

        StringBuilder names = new StringBuilder();
        for (String name : online) {
            if (!names.isEmpty()) names.append(", ");
            names.append(name);
        }

        ChatUtils.info("Online %s ((highlight)%s(default)): %s", label, online.size(), names);
    }
}
