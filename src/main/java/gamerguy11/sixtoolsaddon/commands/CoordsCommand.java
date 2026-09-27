package gamerguy11.sixtoolsaddon.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.command.CommandSource;
import net.minecraft.util.math.BlockPos;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class CoordsCommand extends Command {
    public CoordsCommand() {
        super("coords", "Copies your current coordinates to the clipboard.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(ctx -> {
            if (mc.player == null) {
                ChatUtils.error("You must be in-game to use this command.");
                return SINGLE_SUCCESS;
            }

            BlockPos pos = mc.player.getBlockPos();
            String dimension = mc.world.getRegistryKey().getValue().toString();
            String coords = pos.getX() + " " + pos.getY() + " " + pos.getZ();

            mc.keyboard.setClipboard(coords);

            ChatUtils.info(
                    "Copied (highlight)%s(default) to your clipboard. (%s)",
                    coords,
                    dimension
            );

            return SINGLE_SUCCESS;
        });

        builder.then(literal("raw").executes(ctx -> {
            if (mc.player == null) {
                ChatUtils.error("You must be in-game to use this command.");
                return SINGLE_SUCCESS;
            }

            BlockPos pos = mc.player.getBlockPos();
            String coords = pos.getX() + "," + pos.getY() + "," + pos.getZ();

            mc.keyboard.setClipboard(coords);
            ChatUtils.info("Copied (highlight)%s(default) to your clipboard.", coords);

            return SINGLE_SUCCESS;
        }));
    }
}