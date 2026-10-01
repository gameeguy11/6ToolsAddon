package gamerguy11.sixtoolsaddon.sound;

public enum SoundType {
   GUI_HOVER("gui_hover", "GUI Hover", "Plays when the mouse moves onto a module button in Meteor's click GUI.", true, 60, 0),
   GUI_CLICK_LEFT("gui_click_left", "GUI Left Click", "Plays when you left-click a module button in Meteor's click GUI.", true, 80, 0),
   GUI_CLICK_RIGHT("gui_click_right", "GUI Right Click", "Plays when you right-click a module button in Meteor's click GUI.", true, 80, 0),
   MODULE_ON("module_on", "Module Enabled", "Plays when you switch any module on (from the GUI, a keybind or a command).", true, 80, 30),
   MODULE_OFF("module_off", "Module Disabled", "Plays when you switch any module off (from the GUI, a keybind or a command).", true, 80, 30),
   TYPING("typing", "Typing", "Plays on every key press while the chat box is open.", false, 80, 0),
   CHAT_KEYWORD("chat_keyword", "Chat Keyword", "Plays when an incoming chat message contains one of your keywords.", true, 100, 1000),
   ENEMY_SPOTTED("enemy_spotted", "Enemy Spotted", "Plays when a player on your enemies list renders in.", true, 100, 500),
   DEATH("death", "Death", "Plays when you die.", true, 80, 0);

   public final String folder;
   public final String display;
   public final String description;
   public final boolean defaultEnabled;
   public final int defaultVolume;
   public final int defaultCooldownMs;

   private SoundType(String folder, String display, String description, boolean defaultEnabled, int defaultVolume, int defaultCooldownMs) {
      this.folder = folder;
      this.display = display;
      this.description = description;
      this.defaultEnabled = defaultEnabled;
      this.defaultVolume = defaultVolume;
      this.defaultCooldownMs = defaultCooldownMs;
   }

   private static SoundType[] $values() {
      return new SoundType[]{GUI_HOVER, GUI_CLICK_LEFT, GUI_CLICK_RIGHT, MODULE_ON, MODULE_OFF, TYPING, CHAT_KEYWORD, ENEMY_SPOTTED, DEATH};
   }
}
