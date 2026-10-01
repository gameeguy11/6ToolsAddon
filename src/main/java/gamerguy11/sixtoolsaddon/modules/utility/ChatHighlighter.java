package gamerguy11.sixtoolsaddon.modules.utility;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import gamerguy11.sixtoolsaddon.systems.enemies.Enemies;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.Style;
import net.minecraft.text.MutableText;
import net.minecraft.text.TextColor;

public class ChatHighlighter extends Module {
   private final SettingGroup sgGeneral;
   private final SettingGroup sgColors;
   private final Setting<Boolean> highlightSelf;
   private final Setting<Boolean> highlightFriends;
   private final Setting<Boolean> highlightEnemies;
   private final Setting<Boolean> highlightNearby;
   private final Setting<Double> nearbyRange;
   private final Setting<List<String>> customPlayers;
   private final Setting<String> usernamePattern;
   private final Setting<SettingColor> selfColor;
   private final Setting<SettingColor> friendColor;
   private final Setting<SettingColor> enemyColor;
   private final Setting<SettingColor> nearbyColor;
   private final Setting<Boolean> debug;
   private static final SettingColor DEFAULT_CUSTOM_COLOR = new SettingColor(200, 100, 255);
   private Pattern compiledPattern;
   private String compiledFrom;

   public ChatHighlighter() {
      super(SixToolsAddon.CATEGORY, "chat-highlight", "Colors player names in chat: yourself, friends, enemies, nearby players, and custom players.");
      this.sgGeneral = this.settings.getDefaultGroup();
      this.sgColors = this.settings.createGroup("Colors");
      this.highlightSelf = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("highlight-self")).description("Colors your own name in chat.")).defaultValue(true)).build());
      this.highlightFriends = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("highlight-friends")).description("Colors the names of players on your Meteor friends list.")).defaultValue(true)).build());
      this.highlightEnemies = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("highlight-enemies")).description("Colors the names of players on your enemies list (.enemy add/remove/list).")).defaultValue(true)).build());
      this.highlightNearby = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("highlight-nearby")).description("Colors the names of players currently within range of you.")).defaultValue(false)).build());
      SettingGroup var10001 = this.sgGeneral;
      DoubleSetting.Builder var10002 = ((DoubleSetting.Builder)((DoubleSetting.Builder)(new DoubleSetting.Builder()).name("nearby-range")).description("How close (in blocks) a player must be to count as nearby.")).defaultValue((double)64.0F).min((double)1.0F).sliderRange((double)8.0F, (double)256.0F);
      Setting<Boolean> var10003 = this.highlightNearby;
      Objects.requireNonNull(var10003);
      this.nearbyRange = var10001.add(((DoubleSetting.Builder)var10002.visible(var10003::get)).build());
      this.customPlayers = this.sgGeneral.add(((StringListSetting.Builder)((StringListSetting.Builder)((StringListSetting.Builder)(new StringListSetting.Builder()).name("custom-players")).description("Specific players to highlight. Use \"Name:RRGGBB\" (e.g. Steve:FF8800) for a specific color, or just \"Name\" for a default purple. Overrides every other highlight.")).defaultValue(new ArrayList())).build());
      this.usernamePattern = this.sgGeneral.add(((StringSetting.Builder)((StringSetting.Builder)((StringSetting.Builder)(new StringSetting.Builder()).name("username-pattern")).description("Regex used to find the sender's name at the start of a chat line. Capture group 1 must be the name.")).defaultValue("([A-Za-z0-9_]{2,16})")).build());
      this.selfColor = this.sgColors.add(((ColorSetting.Builder)((ColorSetting.Builder)(new ColorSetting.Builder()).name("self-color")).description("Color for your own name.")).defaultValue(new SettingColor(SixToolsAddon.THEME_COLOR.r, SixToolsAddon.THEME_COLOR.g, SixToolsAddon.THEME_COLOR.b)).build());
      this.friendColor = this.sgColors.add(((ColorSetting.Builder)((ColorSetting.Builder)(new ColorSetting.Builder()).name("friend-color")).description("Color for players on your Meteor friends list.")).defaultValue(new SettingColor(75, 225, 75)).build());
      this.enemyColor = this.sgColors.add(((ColorSetting.Builder)((ColorSetting.Builder)(new ColorSetting.Builder()).name("enemy-color")).description("Color for players on your enemies list.")).defaultValue(new SettingColor(225, 75, 75)).build());
      this.nearbyColor = this.sgColors.add(((ColorSetting.Builder)((ColorSetting.Builder)(new ColorSetting.Builder()).name("nearby-color")).description("Color for nearby players.")).defaultValue(new SettingColor(255, 200, 60)).build());
      this.debug = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("debug")).description("Prints each chat line's exact characters (as unicode escapes) and whether the pattern matched.")).defaultValue(false)).build());
   }

   @EventHandler
   private void onMessageReceive(ReceiveMessageEvent event) {
      Text message = event.getMessage();
      List<Segment> segments = this.collectSegments(message);
      String plain = this.joinSegments(segments);
      if (!plain.isEmpty()) {
         if (!plain.contains("[Chat Highlight]")) {
            Pattern pattern = this.compilePattern();
            if (pattern != null) {
               Matcher matcher = pattern.matcher(plain);
               boolean matched = matcher.find() && matcher.groupCount() >= 1;
               if ((Boolean)this.debug.get()) {
                  this.info("HL DEBUG: [%s] matched=%s", new Object[]{this.escapeNonAscii(plain), matched});
               }

               if (matched) {
                  String name = matcher.group(1);
                  if (name != null && !name.isEmpty()) {
                     SettingColor color = this.colorFor(name);
                     if ((Boolean)this.debug.get()) {
                        String selfName = this.mc.player == null ? "null" : this.mc.player.getName().getString();
                        this.info("HL DEBUG: name='%s' isSelf=%s selfName='%s' isFriend=%s isEnemy=%s color=%s", new Object[]{name, this.isSelf(name), selfName, Friends.get().get(name) != null, Enemies.get().isEnemy(name), color == null ? "null" : color.r + "," + color.g + "," + color.b});
                     }

                     if (color != null) {
                        try {
                           event.setMessage(this.highlightRange(segments, matcher.start(1), matcher.end(1), color));
                        } catch (RuntimeException e) {
                           this.error("Failed to highlight name '%s': %s", new Object[]{name, e.toString()});
                        }

                     }
                  }
               }
            }
         }
      }
   }

   private String escapeNonAscii(String text) {
      StringBuilder sb = new StringBuilder();

      for(int i = 0; i < text.length(); ++i) {
         char c = text.charAt(i);
         if (c == ' ') {
            sb.append('_');
         } else if (c >= ' ' && c < 127) {
            sb.append(c);
         } else {
            sb.append(String.format("\\u%04X", c));
         }
      }

      return sb.toString();
   }

   private Pattern compilePattern() {
      String patternSource = (String)this.usernamePattern.get();
      if (this.compiledPattern == null || !patternSource.equals(this.compiledFrom)) {
         try {
            this.compiledPattern = Pattern.compile(patternSource);
            this.compiledFrom = patternSource;
         } catch (RuntimeException e) {
            this.error("Invalid username-pattern regex: %s", new Object[]{e.getMessage()});
            this.toggle();
            return null;
         }
      }

      return this.compiledPattern;
   }

   private SettingColor colorFor(String name) {
      SettingColor custom = this.customColorFor(name);
      if (custom != null) {
         return custom;
      } else if ((Boolean)this.highlightSelf.get() && this.isSelf(name)) {
         return (SettingColor)this.selfColor.get();
      } else if ((Boolean)this.highlightFriends.get() && Friends.get().get(name) != null) {
         return (SettingColor)this.friendColor.get();
      } else if ((Boolean)this.highlightEnemies.get() && Enemies.get().isEnemy(name)) {
         return (SettingColor)this.enemyColor.get();
      } else {
         return (Boolean)this.highlightNearby.get() && this.isNearby(name) ? (SettingColor)this.nearbyColor.get() : null;
      }
   }

   private SettingColor customColorFor(String name) {
      for(String entry : (List<String>)this.customPlayers.get()) {
         if (entry != null) {
            String trimmed = entry.trim();
            if (!trimmed.isEmpty()) {
               String entryName = trimmed;
               SettingColor entryColor = null;
               int split = trimmed.lastIndexOf(58);
               if (split > 0) {
                  SettingColor parsed = this.parseHex(trimmed.substring(split + 1).trim());
                  if (parsed != null) {
                     entryName = trimmed.substring(0, split).trim();
                     entryColor = parsed;
                  }
               }

               if (entryName.equalsIgnoreCase(name)) {
                  return entryColor != null ? entryColor : DEFAULT_CUSTOM_COLOR;
               }
            }
         }
      }

      return null;
   }

   private SettingColor parseHex(String hex) {
      if (hex.startsWith("#")) {
         hex = hex.substring(1);
      }

      if (hex.length() != 6) {
         return null;
      } else {
         try {
            int rgb = Integer.parseInt(hex.toLowerCase(Locale.ROOT), 16);
            return new SettingColor(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255);
         } catch (NumberFormatException var3) {
            return null;
         }
      }
   }

   private boolean isNearby(String name) {
      if (this.mc.player != null && this.mc.world != null) {
         double maxSq = (Double)this.nearbyRange.get() * (Double)this.nearbyRange.get();

         for(PlayerEntity other : this.mc.world.getPlayers()) {
            if (other != this.mc.player && other.getName().getString().equalsIgnoreCase(name)) {
               return this.mc.player.squaredDistanceTo(other) <= maxSq;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private boolean isSelf(String name) {
      return this.mc.player == null ? false : this.mc.player.getName().getString().equalsIgnoreCase(name);
   }

   private List<Segment> collectSegments(Text message) {
      List<Segment> segments = new ArrayList();
      message.visit((style, text) -> {
         if (!text.isEmpty()) {
            segments.add(new Segment(text, style));
         }

         return Optional.empty();
      }, Style.EMPTY);
      return segments;
   }

   private String joinSegments(List<Segment> segments) {
      StringBuilder sb = new StringBuilder();

      for(Segment segment : segments) {
         sb.append(segment.text());
      }

      return sb.toString();
   }

   private Text highlightRange(List<Segment> segments, int start, int end, SettingColor color) {
      TextColor textColor = TextColor.fromRgb((color.r & 255) << 16 | (color.g & 255) << 8 | color.b & 255);
      MutableText result = Text.empty();
      int pos = 0;

      for(Segment segment : segments) {
         String text = segment.text();
         Style style = segment.style();
         int segStart = pos;
         int segEnd = pos + text.length();
         pos = segEnd;
         int overlapStart = Math.max(segStart, start);
         int overlapEnd = Math.min(segEnd, end);
         if (overlapEnd <= overlapStart) {
            result.append(Text.literal(text).setStyle(style));
         } else {
            if (overlapStart > segStart) {
               result.append(Text.literal(text.substring(0, overlapStart - segStart)).setStyle(style));
            }

            result.append(Text.literal(text.substring(overlapStart - segStart, overlapEnd - segStart)).setStyle(style.withColor(textColor)));
            if (overlapEnd < segEnd) {
               result.append(Text.literal(text.substring(overlapEnd - segStart)).setStyle(style));
            }
         }
      }

      return result;
   }

   private static record Segment(String text, Style style) {
   }
}
