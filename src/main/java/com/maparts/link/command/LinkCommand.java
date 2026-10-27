package com.maparts.link.command;

import com.maparts.link.config.TokenStore;
import com.maparts.link.http.BackendClient;
import com.maparts.link.util.Links;
import com.maparts.link.util.ModChat;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class LinkCommand {

    public static final String BACKEND_URL = "https://6b-maparts.com";

    private static final long POLL_INTERVAL_MS = 2000;
    private static final long POLL_TIMEOUT_MS = 10 * 60 * 1000;

    private LinkCommand() {}

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
                ClientCommandManager.literal("maplink")
                        .then(ClientCommandManager
                                .argument("code", StringArgumentType.word())
                                .executes(LinkCommand::run)));
    }

    private static int run(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        String code = StringArgumentType.getString(context, "code");

        MinecraftClient client = source.getClient();
        String username = client.getSession().getUsername();

        ModChat.feedback(source, Text.literal("Verifying your code…").formatted(Formatting.GRAY));

        BackendClient backend = new BackendClient(BACKEND_URL);

        backend.verify(code, username)
                .thenAccept(result -> client.execute(() -> {
                    if (!result.success()) {
                        ModChat.error(source, Text.literal(result.message()));
                        return;
                    }

                    if (result.modToken() != null) {

                        onLinked(source, username, result.created(), result.mcName(), result.modToken());
                        return;
                    }

                    String instruction = result.passwordSetupRequired()
                            ? "Almost done — open 6bMapArts in your browser (the"
                                    + " tab you got the code in) and CREATE a"
                                    + " password to finish."
                            : "Almost done — open 6bMapArts in your browser and"
                                    + " ENTER your password to finish.";
                    ModChat.feedback(source, Text.literal(instruction).formatted(Formatting.YELLOW));
                    ModChat.feedback(source, Links.prefixedLink(
                            "→ ", "Finish signing in",
                            BACKEND_URL + "/login"));

                    schedulePasswordPoll(
                            source, client, backend, code, username,
                            System.currentTimeMillis() + POLL_TIMEOUT_MS);
                }));

        return Command.SINGLE_SUCCESS;
    }

    private static void schedulePasswordPoll(
            FabricClientCommandSource source, MinecraftClient client, BackendClient backend,
            String code, String username, long deadlineMillis) {
        CompletableFuture.delayedExecutor(POLL_INTERVAL_MS, TimeUnit.MILLISECONDS)
                .execute(() -> backend.pollChallengeToken(code).thenAccept(result -> client.execute(() -> {
                    switch (result.status()) {
                        case "confirmed" -> onLinked(source, username, false, username, result.modToken());
                        case "expired" -> ModChat.error(source, Text.literal(
                                "That link attempt expired. Run /maplink again."));
                        case "error" -> ModChat.error(source, Text.literal(result.errorMessage()));
                        default -> {
                            if (System.currentTimeMillis() >= deadlineMillis) {
                                ModChat.error(source, Text.literal(
                                        "Timed out waiting for the password. Run /maplink again."));
                            } else {
                                schedulePasswordPoll(source, client, backend, code, username, deadlineMillis);
                            }
                        }
                    }
                })));
    }

    private static void onLinked(
            FabricClientCommandSource source, String username, boolean created,
            String mcName, String modToken) {
        if (modToken != null) {
            TokenStore.save(modToken);
        }

        String name = mcName != null ? mcName : username;
        String message = created
                ? "Account created and linked as " + name
                        + " — you're signed in on the website."
                : "Welcome back, " + name
                        + " — you're signed in on the website.";
        ModChat.feedback(source, Text.literal(message).formatted(Formatting.GREEN));
        ModChat.feedback(source, Text.literal(
                "Upload maparts with /mapselect (two corners) +"
                        + " /mapupload, or just /mapupload while looking"
                        + " at the wall or floor. /maphelp opens the website.").formatted(Formatting.GRAY));
        ModChat.feedback(source, Links.prefixedLink(
                "→ ", "Open your dashboard",
                BACKEND_URL + "/dashboard"));
    }
}
