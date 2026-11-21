package gamerguy11.sixtoolsaddon.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import gamerguy11.sixtoolsaddon.kit.Kit;
import gamerguy11.sixtoolsaddon.kit.KitManager;
import gamerguy11.sixtoolsaddon.modules.KitModule;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.command.CommandSource;

public class KitCommand extends Command {
   private final KitManager kits = KitManager.INSTANCE;

   public KitCommand() {
      super("kit", "Save, load, rename and remove inventory kits.", new String[0]);
   }

   public void build(LiteralArgumentBuilder<CommandSource> builder) {
      builder.executes(ctx -> {
         Kit current = kits.current();
         ChatUtils.info("Selected kit: (highlight)%s(default).", current == null ? "none" : current.getName());
         ChatUtils.info("Available kits: (highlight)%s(default).", kits.all().isEmpty() ? "none" : String.join(", ", kits.names()));
         return 1;
      });

      for (String alias : new String[]{"save", "add"}) {
         builder.then(literal(alias).then(argument("name", StringArgumentType.word()).executes(ctx -> {
            String name = StringArgumentType.getString(ctx, "name");
            if (invalid(name)) return 1;
            if (mc.player == null) return 1;
            boolean overwrote = kits.find(name) != null;
            kits.capture(name, mc.player);
            ChatUtils.info(overwrote ? "Overwrote kit (highlight)%s(default)." : "Saved kit (highlight)%s(default).", name);
            return 1;
         })));
      }

      for (String alias : new String[]{"remove", "delete", "del"}) {
         builder.then(literal(alias).then(argument("name", StringArgumentType.word()).suggests(this::suggest).executes(ctx -> {
            String name = StringArgumentType.getString(ctx, "name");
            if (kits.remove(name)) ChatUtils.info("Removed kit (highlight)%s(default).", name);
            else ChatUtils.error("No kit named (highlight)%s(default).", name);
            return 1;
         })));
      }

      builder.then(literal("load").then(argument("name", StringArgumentType.word()).suggests(this::suggest).executes(ctx -> {
         String name = StringArgumentType.getString(ctx, "name");
         Kit kit = kits.find(name);
         if (kit == null) {
            ChatUtils.error("No kit named (highlight)%s(default). Use (highlight).kit(default) to see your kits.", name);
            return 1;
         }
         kits.setCurrent(kit.getName());
         KitModule module = Modules.get().get(KitModule.class);
         if (module.arrange(kit)) ChatUtils.info("Loading kit (highlight)%s(default)...", kit.getName());
         else ChatUtils.info("Selected kit (highlight)%s(default). Close any open container to arrange it.", kit.getName());
         return 1;
      })));

      builder.then(literal("rename").then(argument("name", StringArgumentType.word()).suggests(this::suggest)
         .then(argument("new-name", StringArgumentType.word()).executes(ctx -> {
            String name = StringArgumentType.getString(ctx, "name");
            String newName = StringArgumentType.getString(ctx, "new-name");
            if (invalid(newName)) return 1;
            if (kits.rename(name, newName)) ChatUtils.info("Renamed kit (highlight)%s(default) to (highlight)%s(default).", name, newName);
            else ChatUtils.error("No kit named (highlight)%s(default).", name);
            return 1;
         }))));

      builder.then(literal("list").executes(ctx -> {
         ChatUtils.info("Available kits: (highlight)%s(default).", kits.all().isEmpty() ? "none" : String.join(", ", kits.names()));
         return 1;
      }));
   }

   private java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggest(CommandContext<CommandSource> context, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
      return CommandSource.suggestMatching(kits.names(), builder);
   }

   private boolean invalid(String name) {
      if (name.equalsIgnoreCase("current")) {
         ChatUtils.error("(highlight)current(default) is reserved, pick another name.");
         return true;
      }
      return false;
   }
}
