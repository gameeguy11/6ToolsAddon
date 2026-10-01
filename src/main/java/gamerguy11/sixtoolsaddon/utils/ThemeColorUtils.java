package gamerguy11.sixtoolsaddon.utils;

import java.lang.reflect.Field;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

public final class ThemeColorUtils {
   private ThemeColorUtils() {
   }

   public static SettingColor resolve(SettingColor color, boolean useTheme) {
      if (!useTheme) {
         return color;
      } else {
         SettingColor themeColor = getThemeColor();
         return themeColor == null ? color : new SettingColor(themeColor.r, themeColor.g, themeColor.b, color.a);
      }
   }

   private static SettingColor getThemeColor() {
      GuiTheme theme = GuiThemes.get();
      if (theme == null) {
         return null;
      } else {
         for(Class<?> type = theme.getClass(); type != null; type = type.getSuperclass()) {
            try {
               Field field = type.getDeclaredField("accentColor");
               field.setAccessible(true);
               Object value = field.get(theme);
               if (value instanceof Setting) {
                  Setting<?> setting = (Setting)value;
                  Object var6 = setting.get();
                  if (var6 instanceof SettingColor) {
                     SettingColor color = (SettingColor)var6;
                     return color;
                  }
               }
            } catch (ReflectiveOperationException var7) {
            }
         }

         return null;
      }
   }
}
