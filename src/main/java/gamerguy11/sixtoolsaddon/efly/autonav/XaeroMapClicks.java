package gamerguy11.sixtoolsaddon.efly.autonav;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.Mouse;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;

import java.nio.DoubleBuffer;

import java.lang.reflect.Field;
import java.util.HashMap;

public final class XaeroMapClicks {
    public record Click(int x, int z) {}

    private static final String MAP_SCREEN_CLASS = "xaero.map.gui.GuiMap";
    private static final String[] HOVER_X_FIELDS = {"mouseBlockPosX"};
    private static final String[] HOVER_Z_FIELDS = {"mouseBlockPosZ"};
    private static final double CLICK_SLOP_PIXELS = 6;

    private boolean pressed;
    private String lastLoggedScreen = "";
    private double pressX, pressY;
    private String error;
    private boolean fieldsLogged;
    private final HashMap<String, Field> fieldCache = new HashMap<>();

    public String takeError() {
        String e = error;
        error = null;
        return e;
    }

    public void reset() {
        pressed = false;
    }

    public Click poll(MinecraftClient mc, int button) {
        Screen screen = mc.currentScreen;
        if (screen == null || !isMapScreen(screen)) {
            pressed = false;
            logUnrecognisedXaeroScreen(screen);
            return null;
        }

        Mouse mouse = mc.mouse;
        boolean glfwDown;
        double glfwCursorX;
        double glfwCursorY;
        long window = mc.getWindow().getHandle();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            DoubleBuffer x = stack.mallocDouble(1);
            DoubleBuffer y = stack.mallocDouble(1);
            GLFW.glfwGetCursorPos(window, x, y);
            glfwCursorX = x.get(0);
            glfwCursorY = y.get(0);
            glfwDown = GLFW.glfwGetMouseButton(window, button) == GLFW.GLFW_PRESS;
        }
        boolean vanillaDown = button == GLFW.GLFW_MOUSE_BUTTON_LEFT ? mouse.wasLeftButtonClicked()
            : button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? mouse.wasRightButtonClicked()
            : mouse.wasMiddleButtonClicked();
        boolean down = glfwDown || vanillaDown;
        double[] cursorX = {glfwCursorX}, cursorY = {glfwCursorY};

        if (down) {
            if (!pressed) {
                pressed = true;
                pressX = cursorX[0];
                pressY = cursorY[0];
            }
            return null;
        }

        if (!pressed) return null;
        pressed = false;
        double moved = Math.hypot(cursorX[0] - pressX, cursorY[0] - pressY);
        if (moved > CLICK_SLOP_PIXELS) return null;

        return readHoveredBlock(screen);
    }

    private void logUnrecognisedXaeroScreen(Screen screen) {
        if (screen == null) return;
        String name = screen.getClass().getName();
        if (!name.toLowerCase().contains("xaero") || name.equals(lastLoggedScreen)) return;
        lastLoggedScreen = name;
        gamerguy11.sixtoolsaddon.SixToolsAddon.LOG.warn("Autonav: a Xaero screen is open ({}) but it is not the world map screen this addon looks for ({}).", name, MAP_SCREEN_CLASS);
    }

    private static boolean isMapScreen(Object screen) {
        for (Class<?> c = screen.getClass(); c != null; c = c.getSuperclass()) {
            if (c.getName().equals(MAP_SCREEN_CLASS)) return true;
        }
        return false;
    }

    private Click readHoveredBlock(Object screen) {
        Integer x = readInt(screen, HOVER_X_FIELDS);
        Integer z = readInt(screen, HOVER_Z_FIELDS);
        if (x == null || z == null) {
            error = "I couldn't read the clicked spot from Xaero's World Map (this version of it may have changed) - check the log for details";
            logFieldsOnce(screen);
            return null;
        }
        return new Click(x, z);
    }

    private Integer readInt(Object screen, String[] names) {
        for (String name : names) {
            Field field = findField(screen.getClass(), name);
            if (field == null) continue;
            try {
                Object value = field.get(screen);
                if (value instanceof Number number) return number.intValue();
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        return null;
    }

    private Field findField(Class<?> type, String name) {
        String cacheKey = type.getName() + "#" + name;
        if (fieldCache.containsKey(cacheKey)) return fieldCache.get(cacheKey);

        Field found = null;
        for (Class<?> c = type; c != null && found == null; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField(name);
                field.setAccessible(true);
                found = field;
            } catch (NoSuchFieldException | RuntimeException ignored) {
            }
        }
        fieldCache.put(cacheKey, found);
        return found;
    }

    private void logFieldsOnce(Object screen) {
        if (fieldsLogged) return;
        fieldsLogged = true;

        StringBuilder sb = new StringBuilder("Could not find the hovered-block fields on ").append(screen.getClass().getName()).append(". Fields found:");
        for (Class<?> c = screen.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                sb.append("\n  ").append(c.getSimpleName()).append('.').append(field.getName()).append(" : ").append(field.getType().getSimpleName());
            }
        }
        gamerguy11.sixtoolsaddon.SixToolsAddon.LOG.warn(sb.toString());
    }
}
