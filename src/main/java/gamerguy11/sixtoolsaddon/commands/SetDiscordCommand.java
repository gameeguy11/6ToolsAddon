package gamerguy11.sixtoolsaddon.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import gamerguy11.sixtoolsaddon.modules.utility.DiscordNotifier;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.command.CommandSource;

public class SetDiscordCommand extends Command {
   public SetDiscordCommand() {
      super("setdiscord", "Manage the Discord webhook used by DiscordNotifier.", new String[0]);
   }

   private DiscordNotifier module() {
      return (DiscordNotifier)Modules.get().get(DiscordNotifier.class);
   }

   public void build(LiteralArgumentBuilder builder) {
      builder.then(literal("set").then(argument("url", StringArgumentType.greedyString()).executes((ctx) -> {
         String url = StringArgumentType.getString(ctx, "url").trim();
         DiscordNotifier module = this.module();
         if (!module.isValidWebhookUrl(url)) {
            module.notifyError("That doesn't look like a Discord webhook URL. It should start with (highlight)https://discord.com/api/webhooks/(default).");
            return 1;
         } else {
            module.setWebhookUrl(url);
            module.notifyInfo("Discord webhook set. Use (highlight).discordnotifier(default) to toggle what gets sent.");
            return 1;
         }
      })));
      builder.then(literal("clear").executes((ctx) -> {
         DiscordNotifier module = this.module();
         if (!module.hasWebhook()) {
            module.notifyInfo("No Discord webhook is set.");
            return 1;
         } else {
            module.clearWebhookUrl();
            module.notifyInfo("Discord webhook cleared.");
            return 1;
         }
      }));
   }
}
