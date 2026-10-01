package gamerguy11.sixtoolsaddon.modules.utility;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import gamerguy11.sixtoolsaddon.homes.Home;
import gamerguy11.sixtoolsaddon.homes.HomeStore;
import gamerguy11.sixtoolsaddon.systems.enemies.Enemies;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;

public class AutoTpAccept extends Module {
   private final SettingGroup sgGeneral;
   private final Setting<Boolean> acceptFriends;
   private final Setting<Boolean> acceptEnemies;
   private final Setting<Boolean> acceptEveryone;
   private final Setting<Boolean> denyEnemies;
   private final Setting<Boolean> denyFriends;
   private final Setting<List<String>> enemyNames;
   private final Setting<String> requestPattern;
   private final Setting<Boolean> chatFeedback;
   private final Setting<Boolean> debug;
   private Pattern compiledPattern;
   private String compiledFrom;
   private String lastHandledRequester;
   private long lastHandledAt;

   public AutoTpAccept() {
      super(SixToolsAddon.CATEGORY, "auto-tpy", "Automatically runs /tpy for teleport requests. Respects protected homes.");
      this.sgGeneral = this.settings.getDefaultGroup();
      this.acceptFriends = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("accept-friends")).description("Auto-accept requests from friends.")).defaultValue(true)).build());
      this.acceptEnemies = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("accept-enemies")).description("Auto-accept requests from enemies.")).defaultValue(false)).build());
      this.acceptEveryone = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("accept-everyone")).description("Auto-accept requests from everyone.")).defaultValue(false)).build());
      this.denyEnemies = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("deny-enemies")).description("Answer requests from enemies with /tpn.")).defaultValue(false)).build());
      this.denyFriends = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("deny-friends")).description("Answer requests from friends with /tpn.")).defaultValue(false)).build());
      this.enemyNames = this.sgGeneral.add(((StringListSetting.Builder)((StringListSetting.Builder)(new StringListSetting.Builder()).name("enemy-names")).description("Extra player names treated as enemies (the Enemies tab is always used too).")).build());
      this.requestPattern = this.sgGeneral.add(((StringSetting.Builder)((StringSetting.Builder)((StringSetting.Builder)(new StringSetting.Builder()).name("request-pattern")).description("Regex used to detect teleport requests. Capture group 1 must be the player's name.")).defaultValue("([A-Za-z0-9_.]{2,32}) wants to teleport to you\\.")).build());
      this.chatFeedback = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("chat-feedback")).description("Shows when teleport requests are accepted or ignored.")).defaultValue(true)).build());
      this.debug = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("debug")).description("Shows teleport-related messages exactly as Meteor receives them.")).defaultValue(false)).build());
   }

   @EventHandler
   private void onMessage(ReceiveMessageEvent event) {
      String text = event.getMessage().getString();
      if ((Boolean)this.debug.get() && text.toLowerCase(Locale.ROOT).contains("teleport")) {
         String debugText = text.replace(" ", "_").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
         this.info("TPA DEBUG: [%s] length=%d", new Object[]{debugText, text.length()});
      }

      String patternSource = (String)this.requestPattern.get();
      if (this.compiledPattern == null || !patternSource.equals(this.compiledFrom)) {
         try {
            this.compiledPattern = Pattern.compile(patternSource, 2);
            this.compiledFrom = patternSource;
         } catch (RuntimeException e) {
            this.error("Invalid request-pattern regex: %s", new Object[]{e.getMessage()});
            this.toggle();
            return;
         }
      }

      Matcher matcher = this.compiledPattern.matcher(text);
      if (matcher.find() && matcher.groupCount() >= 1) {
         String requester = matcher.group(1);
         long now = System.currentTimeMillis();
         if (!requester.equalsIgnoreCase(this.lastHandledRequester) || now - this.lastHandledAt >= 2000L) {
            this.lastHandledRequester = requester;
            this.lastHandledAt = now;
            boolean friend = this.isFriend(requester);
            boolean enemy = this.isEnemy(requester);
            Home home = HomeStore.protectedHomeAtPlayer();
            if (home == null || friend && home.allowFriends) {
               if ((!enemy || !(Boolean)this.denyEnemies.get()) && (!friend || !(Boolean)this.denyFriends.get())) {
                  if (!this.shouldAccept(friend, enemy)) {
                     if ((Boolean)this.chatFeedback.get()) {
                        this.info("Ignoring teleport request from (highlight)%s(default) - not allowed by current settings.", new Object[]{requester});
                     }

                  } else {
                     ChatUtils.sendPlayerMsg("/tpy " + requester);
                     if ((Boolean)this.chatFeedback.get()) {
                        this.info("Auto-accepted teleport request from (highlight)%s(default).", new Object[]{requester});
                     }

                  }
               } else {
                  ChatUtils.sendPlayerMsg("/tpn " + requester);
                  if ((Boolean)this.chatFeedback.get()) {
                     this.info("Denied teleport request from (highlight)%s(default).", new Object[]{requester});
                  }

               }
            } else {
               if (home.denyInstead) {
                  ChatUtils.sendPlayerMsg("/tpn " + requester);
               }

               if ((Boolean)this.chatFeedback.get()) {
                  this.info("Home (highlight)%s(default) is protected - %s request from (highlight)%s(default).", new Object[]{home.name, home.denyInstead ? "denied" : "ignored", requester});
               }

            }
         }
      }
   }

   private boolean shouldAccept(boolean friend, boolean enemy) {
      if ((Boolean)this.acceptEveryone.get()) {
         return true;
      } else {
         return friend && (Boolean)this.acceptFriends.get() || enemy && (Boolean)this.acceptEnemies.get();
      }
   }

   private boolean isFriend(String requester) {
      return Friends.get().get(requester) != null;
   }

   private boolean isEnemy(String requester) {
      if (Enemies.get().get(requester) != null) {
         return true;
      } else {
         for(String enemy : (List<String>)this.enemyNames.get()) {
            if (enemy.equalsIgnoreCase(requester)) {
               return true;
            }
         }

         return false;
      }
   }
}
