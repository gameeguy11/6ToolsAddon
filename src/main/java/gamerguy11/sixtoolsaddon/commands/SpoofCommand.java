package gamerguy11.sixtoolsaddon.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import gamerguy11.sixtoolsaddon.modules.utility.Spoofer;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.command.CommandSource;
import net.minecraft.util.math.BlockPos;

public class SpoofCommand extends Command {
   public SpoofCommand() {
      super("spoof", "Controls the coordinate spoofer.", new String[0]);
   }

   private Spoofer spoofer() {
      return Modules.get().get(Spoofer.class);
   }

   public void build(LiteralArgumentBuilder<CommandSource> builder) {
      builder.executes(ctx -> {
         this.sendStatus();
         return 1;
      });

      builder.then(literal("status").executes(ctx -> {
         this.sendStatus();
         return 1;
      }));

      builder.then(literal("on").executes(ctx -> {
         Spoofer spoofer = this.spoofer();
         if (!spoofer.isActive()) {
            spoofer.toggle();
         }

         ChatUtils.info("Coordinate spoofing (highlight)enabled(default).");
         return 1;
      }));

      builder.then(literal("off").executes(ctx -> {
         Spoofer spoofer = this.spoofer();
         if (spoofer.isActive()) {
            spoofer.toggle();
         }

         ChatUtils.info("Coordinate spoofing (highlight)disabled(default).");
         return 1;
      }));

      builder.then(literal("reroll").executes(ctx -> {
         this.spoofer().reroll();
         ChatUtils.info("Picked a new random position.");
         return 1;
      }));

      builder.then(literal("mode")
         .then(literal("offset").executes(ctx -> {
            this.spoofer().setMode(Spoofer.Mode.Offset);
            ChatUtils.info("Spoof mode set to (highlight)offset(default).");
            return 1;
         }))
         .then(literal("fixed").executes(ctx -> {
            this.spoofer().setMode(Spoofer.Mode.Fixed);
            ChatUtils.info("Spoof mode set to (highlight)fixed(default).");
            return 1;
         }))
         .then(literal("random").executes(ctx -> {
            this.spoofer().setMode(Spoofer.Mode.Random);
            ChatUtils.info("Spoof mode set to (highlight)random(default). The offset is hidden and re-rolled every time you leave a server.");
            return 1;
         })));

      builder.then(literal("range").then(argument("blocks", IntegerArgumentType.integer(1, 29000000)).executes(ctx -> {
         int blocks = IntegerArgumentType.getInteger(ctx, "blocks");
         this.spoofer().setRange(blocks);
         ChatUtils.info("Spoof range set to (highlight)%d(default) blocks and a new position was picked.", new Object[]{blocks});
         return 1;
      })));

      builder.then(literal("y")
         .then(literal("on").executes(ctx -> {
            this.spoofer().setSpoofY(true);
            ChatUtils.info("Y level spoofing (highlight)enabled(default).");
            return 1;
         }))
         .then(literal("off").executes(ctx -> {
            this.spoofer().setSpoofY(false);
            ChatUtils.info("Y level spoofing (highlight)disabled(default).");
            return 1;
         })));

      builder.then(literal("set").then(argument("x", IntegerArgumentType.integer())
         .then(argument("z", IntegerArgumentType.integer()).executes(ctx -> {
            int x = IntegerArgumentType.getInteger(ctx, "x");
            int z = IntegerArgumentType.getInteger(ctx, "z");
            this.spoofer().setFixed(x, 64, z);
            ChatUtils.info("Showing fixed coords (highlight)%d 64 %d(default).", new Object[]{x, z});
            return 1;
         }))));

      builder.then(literal("set").then(argument("x", IntegerArgumentType.integer())
         .then(argument("y", IntegerArgumentType.integer())
            .then(argument("z", IntegerArgumentType.integer()).executes(ctx -> {
               int x = IntegerArgumentType.getInteger(ctx, "x");
               int y = IntegerArgumentType.getInteger(ctx, "y");
               int z = IntegerArgumentType.getInteger(ctx, "z");
               this.spoofer().setFixed(x, y, z);
               this.spoofer().setSpoofY(true);
               ChatUtils.info("Showing fixed coords (highlight)%d %d %d(default).", new Object[]{x, y, z});
               return 1;
            })))));
   }

   private void sendStatus() {
      Spoofer spoofer = this.spoofer();
      if (spoofer.getMode() == Spoofer.Mode.Random) {
         ChatUtils.info("Spoofer is (highlight)%s(default), mode (highlight)random(default), Y spoofing (highlight)%s(default).", new Object[]{
            spoofer.isActive() ? "on" : "off",
            spoofer.isSpoofingY() ? "on" : "off"
         });
         ChatUtils.info("Offset is hidden and is re-rolled every time you leave a server.");
         return;
      }

      BlockPos fake = spoofer.getFakePosition();
      ChatUtils.info("Spoofer is (highlight)%s(default), mode (highlight)%s(default), range (highlight)%d(default), Y spoofing (highlight)%s(default).", new Object[]{
         spoofer.isActive() ? "on" : "off",
         spoofer.getMode().name().toLowerCase(),
         spoofer.getRange(),
         spoofer.isSpoofingY() ? "on" : "off"
      });
      ChatUtils.info("Current %s: (highlight)%d %d %d(default).", new Object[]{
         spoofer.getMode() == Spoofer.Mode.Fixed ? "fixed position" : "offset",
         fake.getX(),
         fake.getY(),
         fake.getZ()
      });
   }
}
