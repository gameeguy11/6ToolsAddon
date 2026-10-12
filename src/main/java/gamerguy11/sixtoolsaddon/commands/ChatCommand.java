package gamerguy11.sixtoolsaddon.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import gamerguy11.sixtoolsaddon.chat.ChatConfig;
import gamerguy11.sixtoolsaddon.chat.ChatService;
import java.util.Locale;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;

public class ChatCommand extends Command {
   public ChatCommand() {
      super("chat", "6Tools chat window and channels.", new String[]{"6c"});
   }

   public void build(LiteralArgumentBuilder<CommandSource> builder) {
      ChatService service = ChatService.INSTANCE;
      ChatConfig config = ChatConfig.get();

      builder.executes((ctx) -> {
         service.openScreen();
         return SINGLE_SUCCESS;
      });

      builder.then(literal("general").executes((ctx) -> {
         service.openGeneral();
         return SINGLE_SUCCESS;
      }).then(argument("message", StringArgumentType.greedyString()).executes((ctx) -> {
         service.sendGeneral(StringArgumentType.getString(ctx, "message"));
         return SINGLE_SUCCESS;
      })));

      builder.then(literal("connect").executes((ctx) -> {
         service.connect();
         return SINGLE_SUCCESS;
      }));

      builder.then(literal("disconnect").executes((ctx) -> {
         service.disconnect();
         return SINGLE_SUCCESS;
      }));

      builder.then(literal("echo").executes((ctx) -> {
         config.echo = !config.echo;
         config.save();
         this.info(config.echo ? "Main chat echo on." : "Main chat echo off.");
         return SINGLE_SUCCESS;
      }));

      builder.then(literal("who").executes((ctx) -> {
         this.info("Online: " + String.join(", ", service.onlineAll()));
         return SINGLE_SUCCESS;
      }));

      builder.then(literal("ban").then(argument("name", StringArgumentType.word()).executes((ctx) -> {
         service.sendAdmin("ban", StringArgumentType.getString(ctx, "name"));
         return SINGLE_SUCCESS;
      })));

      builder.then(literal("unban").then(argument("name", StringArgumentType.word()).executes((ctx) -> {
         service.sendAdmin("unban", StringArgumentType.getString(ctx, "name"));
         return SINGLE_SUCCESS;
      })));

      builder.then(literal("bans").executes((ctx) -> {
         service.sendAdmin("bans", null);
         return SINGLE_SUCCESS;
      }));

      builder.then(literal("mute").then(argument("name", StringArgumentType.word()).executes((ctx) -> {
         config.muted.add(StringArgumentType.getString(ctx, "name").toLowerCase(Locale.ROOT));
         config.save();
         this.info("Muted.");
         return SINGLE_SUCCESS;
      })));

      builder.then(literal("unmute").then(argument("name", StringArgumentType.word()).suggests((ctx, suggestions) -> CommandSource.suggestMatching(config.muted, suggestions)).executes((ctx) -> {
         config.muted.remove(StringArgumentType.getString(ctx, "name").toLowerCase(Locale.ROOT));
         config.save();
         this.info("Unmuted.");
         return SINGLE_SUCCESS;
      })));

      builder.then(literal("url").then(argument("url", StringArgumentType.greedyString()).executes((ctx) -> {
         config.relayUrl = StringArgumentType.getString(ctx, "url").trim();
         config.save();
         service.disconnect();
         service.connect();
         this.info("Relay updated.");
         return SINGLE_SUCCESS;
      })));

      builder.then(argument("friend", StringArgumentType.word()).suggests((ctx, suggestions) -> CommandSource.suggestMatching(service.onlineFriends(), suggestions)).executes((ctx) -> {
         if (!service.openDm(StringArgumentType.getString(ctx, "friend"))) {
            this.error("That player is not on your friends list.");
         }

         return SINGLE_SUCCESS;
      }).then(argument("message", StringArgumentType.greedyString()).executes((ctx) -> {
         if (!service.sendDm(StringArgumentType.getString(ctx, "friend"), StringArgumentType.getString(ctx, "message"))) {
            this.error("That player is not on your friends list.");
         }

         return SINGLE_SUCCESS;
      })));
   }
}
