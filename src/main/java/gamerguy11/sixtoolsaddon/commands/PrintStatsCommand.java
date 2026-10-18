package gamerguy11.sixtoolsaddon.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import gamerguy11.sixtoolsaddon.utils.PrintStats;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.command.CommandSource;

public class PrintStatsCommand extends Command {
   public PrintStatsCommand() {
      super("printstats", "Shows how many withers a player has printed with Wither Print.", new String[0]);
   }

   public void build(LiteralArgumentBuilder builder) {
      builder.executes((ctx) -> {
         this.show(mc.getSession().getUsername());
         return 1;
      });
      builder.then(literal("reset").executes((ctx) -> {
         String name = mc.getSession().getUsername();
         PrintStats.reset(name);
         ChatUtils.info("Reset print stats for (highlight)%s(default).", new Object[]{name});
         return 1;
      }));
      builder.then(literal("all").executes((ctx) -> {
         if (PrintStats.all().isEmpty()) {
            ChatUtils.info("No withers printed yet.");
         }

         PrintStats.all().forEach((name, total) -> ChatUtils.info("(highlight)%s(default): (highlight)%d(default) withers", new Object[]{name, total}));
         return 1;
      }));
      builder.then(argument("player", StringArgumentType.word()).suggests((context, suggestions) -> CommandSource.suggestMatching(PrintStats.all().keySet(), suggestions)).executes((ctx) -> {
         this.show(StringArgumentType.getString(ctx, "player"));
         return 1;
      }));
   }

   private void show(String name) {
      ChatUtils.info("(highlight)%s(default) printed (highlight)%d(default) withers (this session: (highlight)%d(default)).", new Object[]{name, PrintStats.total(name), PrintStats.session(name)});
   }
}
