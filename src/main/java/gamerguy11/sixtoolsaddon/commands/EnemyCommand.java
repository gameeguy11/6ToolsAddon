package gamerguy11.sixtoolsaddon.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import gamerguy11.sixtoolsaddon.systems.enemies.Enemies;
import gamerguy11.sixtoolsaddon.systems.enemies.Enemy;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.command.CommandSource;
import net.minecraft.client.network.PlayerListEntry;

public class EnemyCommand extends Command {
   public EnemyCommand() {
      super("enemy", "Manages your enemies list.", new String[0]);
   }

   public void build(LiteralArgumentBuilder builder) {
      builder.then(literal("add").then(argument("name", StringArgumentType.word()).suggests((context, suggestionsBuilder) -> {
         if (mc.getNetworkHandler() == null) {
            return suggestionsBuilder.buildFuture();
         } else {
            List<String> names = new ArrayList();

            for(PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
               names.add(entry.getProfile().name());
            }

            return CommandSource.suggestMatching(names.stream(), suggestionsBuilder);
         }
      }).executes((ctx) -> {
         String name = StringArgumentType.getString(ctx, "name");
         if (Enemies.get().add(new Enemy(name))) {
            ChatUtils.info("Added (highlight)%s(default) to enemies.", new Object[]{name});
         } else {
            ChatUtils.info("(highlight)%s(default) is already on your enemies list.", new Object[]{name});
         }

         return 1;
      })));
      builder.then(literal("remove").then(argument("name", StringArgumentType.word()).suggests((context, suggestionsBuilder) -> {
         List<String> names = new ArrayList();

         for(Enemy enemy : Enemies.get()) {
            names.add(enemy.getName());
         }

         return CommandSource.suggestMatching(names.stream(), suggestionsBuilder);
      }).executes((ctx) -> {
         String name = StringArgumentType.getString(ctx, "name");
         Enemy enemy = Enemies.get().get(name);
         if (enemy != null && Enemies.get().remove(enemy)) {
            ChatUtils.info("Removed (highlight)%s(default) from enemies.", new Object[]{name});
         } else {
            ChatUtils.info("(highlight)%s(default) is not on your enemies list.", new Object[]{name});
         }

         return 1;
      })));
      builder.then(literal("list").executes((ctx) -> {
         if (Enemies.get().isEmpty()) {
            ChatUtils.info("Your enemies list is empty.", new Object[0]);
            return 1;
         } else {
            StringBuilder names = new StringBuilder();

            for(Enemy enemy : Enemies.get()) {
               if (!names.isEmpty()) {
                  names.append(", ");
               }

               names.append(enemy.getName());
            }

            ChatUtils.info("Enemies ((highlight)%s(default)): %s", new Object[]{Enemies.get().count(), names});
            return 1;
         }
      }));
   }
}
