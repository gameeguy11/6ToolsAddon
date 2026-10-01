package gamerguy11.sixtoolsaddon.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import gamerguy11.sixtoolsaddon.systems.enemies.Enemies;
import gamerguy11.sixtoolsaddon.systems.enemies.Enemy;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.command.CommandSource;
import net.minecraft.client.network.PlayerListEntry;

public class OnlinePlayersCommand extends Command {
   public OnlinePlayersCommand() {
      super("onlineplayers", "Lists your online friends or enemies.", new String[0]);
   }

   public void build(LiteralArgumentBuilder builder) {
      builder.then(literal("friends").executes((ctx) -> {
         this.listOnline(true);
         return 1;
      }));
      builder.then(literal("enemies").executes((ctx) -> {
         this.listOnline(false);
         return 1;
      }));
   }

   private void listOnline(boolean friendsMode) {
      if (mc.getNetworkHandler() == null) {
         this.error("§cNot connected!", new Object[0]);
      } else {
         List<String> online = new ArrayList();

         for(PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
            String name = entry.getProfile().name();
            if (mc.player == null || !name.equals(mc.player.getGameProfile().name())) {
               if (friendsMode) {
                  if (Friends.get().get(name) != null) {
                     online.add(name);
                  }
               } else {
                  Enemy enemy = Enemies.get().get(name);
                  if (enemy != null) {
                     online.add(name);
                  }
               }
            }
         }

         String label = friendsMode ? "friends" : "enemies";
         if (online.isEmpty()) {
            ChatUtils.info("No online %s.", new Object[]{label});
         } else {
            StringBuilder names = new StringBuilder();

            for(String name : online) {
               if (!names.isEmpty()) {
                  names.append(", ");
               }

               names.append(name);
            }

            ChatUtils.info("Online %s ((highlight)%s(default)): %s", new Object[]{label, online.size(), names});
         }
      }
   }
}
