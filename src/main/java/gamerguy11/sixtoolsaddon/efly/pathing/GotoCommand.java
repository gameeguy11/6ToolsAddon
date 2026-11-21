package gamerguy11.sixtoolsaddon.efly.pathing;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import gamerguy11.sixtoolsaddon.efly.pathing.EflyGoto;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.command.CommandSource;
import net.minecraft.world.World;

public class GotoCommand extends Command {
    public GotoCommand() {
        super("vgoto", "Fly somewhere with Efly, Baritone-style: .vgoto x z (land), .vgoto x y z (hover), .vgoto stop.", "vg");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(ctx -> {
            EflyGoto g = Modules.get().get(EflyGoto.class);
            if (g.isActive()) info("Flying to %d %d: %s.", g.targetX.get(), g.targetZ.get(), g.statusLine());
            else info("Not flying anywhere. Usage: .vgoto x z | .vgoto x y z | .vgoto ow x z | .vgoto stop");
            return SINGLE_SUCCESS;
        });

        builder.then(literal("stop").executes(ctx -> {
            EflyGoto g = Modules.get().get(EflyGoto.class);
            if (g.isActive()) {
                g.toggle();
                info("Stopped.");
            }
            else info("Not flying anywhere.");
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("ow").then(argument("x", IntegerArgumentType.integer()).then(argument("z", IntegerArgumentType.integer())
            .executes(ctx -> {
                int x = IntegerArgumentType.getInteger(ctx, "x"), z = IntegerArgumentType.getInteger(ctx, "z");
                if (mc.world != null && mc.world.getRegistryKey() == World.NETHER) {
                    info("Overworld %d %d = Nether %d %d.", x, z, Math.floorDiv(x, 8), Math.floorDiv(z, 8));
                    x = Math.floorDiv(x, 8);
                    z = Math.floorDiv(z, 8);
                }
                Modules.get().get(EflyGoto.class).go(x, null, z);
                return SINGLE_SUCCESS;
            }))));

        builder.then(argument("x", IntegerArgumentType.integer())
            .then(argument("a", IntegerArgumentType.integer())
                .executes(ctx -> {
                    Modules.get().get(EflyGoto.class).go(IntegerArgumentType.getInteger(ctx, "x"), null, IntegerArgumentType.getInteger(ctx, "a"));
                    return SINGLE_SUCCESS;
                })
                .then(argument("b", IntegerArgumentType.integer())
                    .executes(ctx -> {
                        Modules.get().get(EflyGoto.class).go(IntegerArgumentType.getInteger(ctx, "x"), IntegerArgumentType.getInteger(ctx, "a"), IntegerArgumentType.getInteger(ctx, "b"));
                        return SINGLE_SUCCESS;
                    })
                )
            )
        );
    }
}
