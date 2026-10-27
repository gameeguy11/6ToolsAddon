package gamerguy11.sixtoolsaddon.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import gamerguy11.sixtoolsaddon.baritone.BaritoneCheck;
import gamerguy11.sixtoolsaddon.baritone.BaritoneInfo;
import java.net.URI;
import java.util.List;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class BaritoneCommand extends Command {
   private static final int MAX_LINES = 40;

   public BaritoneCommand() {
      super("baritone", "Runs Baritone commands and shows Baritone settings.", new String[]{"bar"});
   }

   public void build(LiteralArgumentBuilder builder) {
      builder.executes(context -> {
         overview();
         return 1;
      });

      builder.then(argument("command, module", StringArgumentType.greedyString()).suggests((context, suggestions) -> {
         String remaining = suggestions.getRemaining();
         var offset = suggestions.createOffset(suggestions.getStart() + remaining.lastIndexOf(' ') + 1);
         return CommandSource.suggestMatching(BaritoneInfo.complete(remaining), offset);
      }).executes(context -> {
         runOrShow(StringArgumentType.getString(context, "command, module").trim());
         return 1;
      }));
      builder.then(literal("settings").executes(context -> {
         list("");
         return 1;
      }).then(argument("filter", StringArgumentType.word()).executes(context -> {
         list(StringArgumentType.getString(context, "filter"));
         return 1;
      })));
   }

   private void runOrShow(String input) {
      if (!BaritoneCheck.isPresent()) {
         warning(BaritoneCheck.warningMessage());
         return;
      }

      String setting = input.contains(" ") ? null : BaritoneInfo.describeSetting(input);
      if (setting != null) {
         info(setting);
         info("Change it with §f.baritone set " + input + " <value>");
         return;
      }

      if (!BaritoneInfo.run(input)) {
         warning("Baritone did not recognise '" + input + "'. Try .baritone help");
      }
   }

   private void overview() {
      info("§bBaritone§r: " + BaritoneInfo.loadedBuild());
      if (!BaritoneCheck.isPresent()) {
         warning(BaritoneCheck.warningMessage());
         sendLink();
         return;
      }

      if (BaritoneInfo.hasSetting("elytraStandingTakeoff")) {
         info("Build: §adekrom fork§r (recommended): standing takeoff, crash recovery, vanilla-accurate flight.");
      } else {
         warning("Build: not the dekrom fork. Elytra takeoff without a ledge, crash recovery and the lookahead setting are missing.");
         sendLink();
      }

      String conflict = BaritoneCheck.hasOld() && BaritoneCheck.hasNew() ? BaritoneCheck.warningMessage() : null;
      if (conflict != null) {
         warning(conflict);
      }

      info("Usage: §f.baritone <command, module>§r, e.g. §f.baritone goto 100 64 100§r, §f.baritone elytra 1000 1000§r or §f.baritone allowSprint§r. Also §f.baritone settings [filter]§r");
   }

   private void list(String filter) {
      if (!BaritoneCheck.isPresent()) {
         warning(BaritoneCheck.warningMessage());
         return;
      }

      List<String> lines = BaritoneInfo.settings(filter);
      info("§b" + lines.size() + " setting(s)§r" + (filter.isEmpty() ? "" : " matching '" + filter + "'") + ":");
      for (int i = 0; i < Math.min(lines.size(), MAX_LINES); i++) {
         info(" " + lines.get(i));
      }

      if (lines.size() > MAX_LINES) {
         info("... " + (lines.size() - MAX_LINES) + " more. Add a filter: .baritone settings elytra");
      }
   }

   private void sendLink() {
      Text link = Text.literal("[Download dekrom Baritone for 1.21.11]").setStyle(Style.EMPTY
         .withColor(Formatting.AQUA).withUnderline(true)
         .withClickEvent(new ClickEvent.OpenUrl(URI.create(BaritoneCheck.DEKROM_URL)))
         .withHoverEvent(new HoverEvent.ShowText(Text.literal(BaritoneCheck.DEKROM_URL))));
      mc.inGameHud.getChatHud().addMessage(link);
   }
}
