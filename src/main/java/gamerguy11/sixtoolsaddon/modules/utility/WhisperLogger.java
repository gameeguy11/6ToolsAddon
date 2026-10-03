package gamerguy11.sixtoolsaddon.modules.utility;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import gamerguy11.sixtoolsaddon.utils.ChatNames;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.loader.api.FabricLoader;

public class WhisperLogger extends Module {
   private static final Path LOGS_DIR = FabricLoader.getInstance().getConfigDir().resolve("sixtoolsaddon").resolve("WhisperLogs");
   private final SettingGroup sgGeneral;
   private final Setting<String> receiveFormat;
   private final Setting<String> sendFormat;
   private final Setting<TimeFormat> timeFormat;

   public WhisperLogger() {
      super(SixToolsAddon.CATEGORY, "whisper-logger", "Logs whispers to a Discord-styled HTML archive, saved under .minecraft\\config\\sixtoolsaddon\\WhisperLogs");
      this.sgGeneral = this.settings.getDefaultGroup();
      this.receiveFormat = this.sgGeneral.add(((StringSetting.Builder)((StringSetting.Builder)((StringSetting.Builder)(new StringSetting.Builder()).name("receive-format")).description("Format for received whispers. Use {player} for the sender and {message} for the content.")).defaultValue("{player} whispers: {message}")).build());
      this.sendFormat = this.sgGeneral.add(((StringSetting.Builder)((StringSetting.Builder)((StringSetting.Builder)(new StringSetting.Builder()).name("send-format")).description("Format for sent whispers. Use {player} for the receiver and {message} for the content.")).defaultValue("You whisper to {player}: {message}")).build());
      this.timeFormat = this.sgGeneral.add(((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)(new EnumSetting.Builder()).name("time-format")).description("Time format for the log timestamp.")).defaultValue(WhisperLogger.TimeFormat.DDMMYYYY_HHMM)).build());
   }

   @EventHandler
   private void onMessage(ReceiveMessageEvent event) {
      String message = event.getMessage().getString();
      if (!this.checkAndLog(message, (String)this.receiveFormat.get(), true)) {
         this.checkAndLog(message, (String)this.sendFormat.get(), false);
      }
   }

   private boolean checkAndLog(String content, String format, boolean isReceive) {
      try {
         String var10000 = Pattern.quote(format).replace("{player}", "\\E(?<player>.*?)\\Q");
         String regex = "^" + var10000.replace("{message}", "\\E(?<message>.*?)\\Q") + "$";
         Pattern pattern = Pattern.compile(regex);
         Matcher matcher = pattern.matcher(content);
         if (matcher.find()) {
            String otherPlayer = ChatNames.singleName(matcher.group("player"));
            if (otherPlayer == null) {
               return false;
            }

            String messageContent = matcher.group("message");
            String myName = this.mc.player != null ? this.mc.player.getName().getString() : "Unknown";
            String sender = isReceive ? otherPlayer : myName;
            this.logHtml(otherPlayer, sender, messageContent);
            return true;
         }
      } catch (RuntimeException e) {
         this.error("Failed to match whisper format: %s", new Object[]{e.getMessage()});
      }

      return false;
   }

   private void logHtml(String otherPlayer, String sender, String message) {
      Path logFile = LOGS_DIR.resolve(otherPlayer + ".html");
      DateTimeFormatter formatter = DateTimeFormatter.ofPattern(((TimeFormat)this.timeFormat.get()).getPattern());
      String timestamp = LocalDateTime.now().format(formatter);

      try {
         if (!Files.exists(LOGS_DIR, new LinkOption[0])) {
            Files.createDirectories(LOGS_DIR);
         }

         boolean isNewFile = !Files.exists(logFile, new LinkOption[0]);
         StringBuilder content = new StringBuilder();
         if (isNewFile) {
            content.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"UTF-8\">\n<style>\n");
            content.append("body { background-color: #36393f; color: #dcddde; font-family: sans-serif; padding: 10px; }\n");
            content.append(".msg { display: flex; margin-bottom: 15px; }\n");
            content.append(".avatar { width: 40px; height: 40px; border-radius: 50%; margin-right: 15px; }\n");
            content.append(".content { display: flex; flex-direction: column; }\n");
            content.append(".header { display: flex; align-items: baseline; }\n");
            content.append(".username { font-weight: bold; margin-right: 5px; color: #fff; }\n");
            content.append(".timestamp { font-size: 0.75rem; color: #72767d; }\n");
            content.append(".text { margin-top: 2px; line-height: 1.4; color: #dcddde; }\n");
            content.append("</style>\n</head>\n<body>\n");
            content.append("<h2 style=\"color: #fff; text-align: center; margin-bottom: 20px;\">SixToolsAddon Whisper Logger</h2>\n");
         }

         String avatarUrl = "https://mc-heads.net/avatar/" + sender + "/32";
         content.append("<div class=\"msg\">\n");
         content.append(String.format("<img class=\"avatar\" src=\"%s\">\n", avatarUrl));
         content.append("<div class=\"content\">\n");
         content.append("<div class=\"header\">\n");
         content.append(String.format("<span class=\"username\">%s</span>\n", sender));
         content.append(String.format("<span class=\"timestamp\">%s</span>\n", timestamp));
         content.append("</div>\n");
         content.append(String.format("<div class=\"text\">%s</div>\n", this.escapeHtml(message)));
         content.append("</div>\n</div>\n");
         Files.writeString(logFile, content.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
      } catch (IOException e) {
         this.error("Failed to write whisper log: %s", new Object[]{e.getMessage()});
      }

   }

   private String escapeHtml(String text) {
      return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
   }

   public static enum TimeFormat {
      DDMMYYYY_HHMM("DD/MM/YYYY HH:MM", "dd/MM/yyyy HH:mm"),
      MMDDYYYY_HHMM("MM/DD/YYYY HH:MM", "MM/dd/yyyy HH:mm"),
      YYYYMMDD_HHMM("YYYY-MM-DD HH:MM", "yyyy-MM-dd HH:mm");

      private final String title;
      private final String pattern;

      private TimeFormat(String title, String pattern) {
         this.title = title;
         this.pattern = pattern;
      }

      public String getPattern() {
         return this.pattern;
      }

      public String toString() {
         return this.title;
      }

      private static TimeFormat[] $values() {
         return new TimeFormat[]{DDMMYYYY_HHMM, MMDDYYYY_HHMM, YYYYMMDD_HHMM};
      }
   }
}
