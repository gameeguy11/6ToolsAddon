package gamerguy11.sixtoolsaddon.efly.pathing;

import gamerguy11.sixtoolsaddon.basehunting.HuntWaypoints;
import java.lang.reflect.Field;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.math.BlockPos;
import xaero.map.gui.IRightClickableElement;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

public class XaeroGotoOption extends RightClickOption {
   private final Object map;
   private final Integer fixedX, fixedZ;

   public XaeroGotoOption(IRightClickableElement map, int index) {
      this(map, index, null, null);
   }

   public XaeroGotoOption(IRightClickableElement map, int index, Integer fixedX, Integer fixedZ) {
      super("Efly goto", index, map);
      this.map = map;
      this.fixedX = fixedX;
      this.fixedZ = fixedZ;
   }

   @SuppressWarnings({"rawtypes", "unchecked"})
   public static void insertAtTop(java.util.List list, IRightClickableElement map) {
      insertAtTop(list, map, null, null);
   }

   @SuppressWarnings({"rawtypes", "unchecked"})
   public static void insertAtTop(java.util.List list, IRightClickableElement map, Integer x, Integer z) {
      int at = Math.min(1, list.size());
      list.add(at, new XaeroGotoOption(map, at, x, z));
      try {
         Field index = RightClickOption.class.getDeclaredField("index");
         index.setAccessible(true);
         for (int i = 0; i < list.size(); i++) {
            Object o = list.get(i);
            if (o instanceof RightClickOption) index.setInt(o, i);
         }
      } catch (ReflectiveOperationException | RuntimeException e) {
         gamerguy11.sixtoolsaddon.SixToolsAddon.LOG.warn("Efly goto: could not renumber Xaero's right-click options", e);
      }
   }

   @Override
   public void onAction(Screen screen) {
      MinecraftClient mc = MinecraftClient.getInstance();
      Integer x = fixedX != null ? fixedX : readInt("rightClickX");
      Integer z = fixedZ != null ? fixedZ : readInt("rightClickZ");
      if (x == null || z == null) {
         x = readInt("mouseBlockPosX");
         z = readInt("mouseBlockPosZ");
      }
      if (x == null || z == null) {
         gamerguy11.sixtoolsaddon.SixToolsAddon.LOG.warn("Efly goto: could not read the right-clicked block from Xaero's map.");
         return;
      }
      EflyGoto goTo = Modules.get().get(EflyGoto.class);
      if (goTo == null || mc.player == null) return;
      goTo.go(x, null, z);
      if (goTo.isActive()) HuntWaypoints.setAutonav(new BlockPos(x, (int) Math.floor(mc.player.getY()), z));
      mc.setScreen(null);
   }

   private Integer readInt(String name) {
      for (Class<?> c = map.getClass(); c != null; c = c.getSuperclass()) {
         try {
            Field f = c.getDeclaredField(name);
            f.setAccessible(true);
            Object v = f.get(map);
            if (v instanceof Number n) return n.intValue();
         } catch (ReflectiveOperationException | RuntimeException ignored) {
         }
      }
      return null;
   }
}
