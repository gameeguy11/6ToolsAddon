package com.maparts.link.command;

import com.maparts.link.capture.MapGridCapture;
import com.maparts.link.capture.SelectionState;
import com.maparts.link.util.ModChat;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class MapSelectCommand {
    private MapSelectCommand() {}

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
                ClientCommandManager.literal("mapselect")
                        .executes(MapSelectCommand::run));
    }

    private static int run(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        MinecraftClient client = source.getClient();

        ItemFrameEntity frame = MapGridCapture.targetedMapFrame(client);
        String invalid = MapGridCapture.validateWallFrame(client, frame);

        if (invalid != null) {
            ModChat.error(source, Text.literal(invalid));
            return 0;
        }

        if (!SelectionState.hasPendingFirst()) {
            SelectionState.setFirst(frame.getId());
            ModChat.feedback(source, Text.literal(
                    "Corner 1 set. Look at the opposite corner frame and run"
                            + " /mapselect again.").formatted(Formatting.GRAY));
            return Command.SINGLE_SUCCESS;
        }

        Entity first = client.world.getEntityById(SelectionState.firstId());

        if (!(first instanceof ItemFrameEntity firstFrame)
                || MapGridCapture.validateWallFrame(client, firstFrame) != null) {
            SelectionState.setFirst(frame.getId());
            ModChat.feedback(source, Text.literal(
                    "The first corner is gone — starting over. Corner 1 set;"
                            + " look at the opposite corner and run /mapselect"
                            + " again.").formatted(Formatting.GRAY));
            return Command.SINGLE_SUCCESS;
        }

        String mismatch = MapGridCapture.validateSameWall(firstFrame, frame);

        if (mismatch != null) {
            ModChat.error(source, Text.literal(mismatch));
            return 0;
        }

        SelectionState.setSecond(frame.getId());
        int[] size = MapGridCapture.gridSize(firstFrame, frame);
        ModChat.feedback(source, Text.literal(
                "Selected a " + size[0] + "x" + size[1]
                        + " mapart — run /mapupload [title] to upload it.")
                .formatted(Formatting.GREEN));

        return Command.SINGLE_SUCCESS;
    }
}
