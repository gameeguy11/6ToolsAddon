package gamerguy11.sixtoolsaddon.efly.pathing;

import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import net.minecraft.client.option.KeyBinding;

public interface Brain {
    void activate();

    void deactivate();

    void tick(TickEvent.Pre event);

    void move(PlayerMoveEvent event);

    void packet(PacketEvent.Receive event);

    void render(Render3DEvent event);

    boolean holdingKey(KeyBinding key);

    String infoString();

    String statusLine();

    void go(int x, Integer y, int z);
}
