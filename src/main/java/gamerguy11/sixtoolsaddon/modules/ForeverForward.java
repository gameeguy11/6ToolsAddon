package gamerguy11.sixtoolsaddon.modules;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.InputUtil.Type;

public class ForeverForward extends Module {
   private final SettingGroup sgGeneral;
   private final Setting<Boolean> sprint;

   public ForeverForward() {
      super(SixToolsAddon.CATEGORY, "forever-forward", "Holds the forward-movement key down for you.");
      this.sgGeneral = this.settings.getDefaultGroup();
      this.sprint = this.sgGeneral.add(((BoolSetting.Builder)((BoolSetting.Builder)((BoolSetting.Builder)(new BoolSetting.Builder()).name("sprint")).description("Also holds the sprint key down while active.")).defaultValue(true)).build());
   }

   public void onDeactivate() {
      if (!this.isKeyPhysicallyPressed(this.mc.options.forwardKey)) {
         this.mc.options.forwardKey.setPressed(false);
      }

      if (!this.isKeyPhysicallyPressed(this.mc.options.sprintKey)) {
         this.mc.options.sprintKey.setPressed(false);
      }

   }

   public void onActivate() {
      this.press();
   }

   @EventHandler(
      priority = 100
   )
   private void onTickPre(TickEvent.Pre event) {
      this.press();
   }

   @EventHandler
   private void onTickPost(TickEvent.Post event) {
      this.press();
   }

   private void press() {
      if (this.mc.player != null) {
         this.mc.options.forwardKey.setPressed(true);
         this.mc.options.sprintKey.setPressed((Boolean)this.sprint.get() || this.isKeyPhysicallyPressed(this.mc.options.sprintKey));
      }
   }

   public static boolean isHolding(KeyBinding binding) {
      ForeverForward module = (ForeverForward)Modules.get().get(ForeverForward.class);
      if (module != null && module.isActive()) {
         return binding == module.mc.options.forwardKey || binding == module.mc.options.sprintKey && (Boolean)module.sprint.get();
      } else {
         return false;
      }
   }

   private boolean isKeyPhysicallyPressed(KeyBinding binding) {
      if (this.mc.getWindow() == null) {
         return binding.isPressed();
      } else {
         InputUtil.Key key = InputUtil.fromTranslationKey(binding.getBoundKeyTranslationKey());
         return key.getCategory() != Type.KEYSYM ? binding.isPressed() : InputUtil.isKeyPressed(this.mc.getWindow(), key.getCode());
      }
   }
}
