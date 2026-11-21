package gamerguy11.sixtoolsaddon.modules;

import gamerguy11.sixtoolsaddon.SixToolsAddon;
import gamerguy11.sixtoolsaddon.systems.enemies.Enemies;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.game.SendMessageEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;

public class Ez extends Module {
    public enum Order { Random, Sequential }

    private static final int MAX_CHAT_LENGTH = 256;
    private static final long HIT_WINDOW_MS = 5000;
    private static final long CLAIM_WINDOW_MS = 3000;
    private static final Pattern NAME_TAG = Pattern.compile("\\{name}|<name>|<n>", Pattern.CASE_INSENSITIVE);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgChat = settings.createGroup("Chat");
    private final SettingGroup sgKill = settings.createGroup("Kill");
    private final SettingGroup sgPop = settings.createGroup("Pop");

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
            .name("range")
            .description("Only trigger for players within this distance of you.")
            .defaultValue(25)
            .min(0)
            .sliderRange(0, 50)
            .build());

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
            .name("delay")
            .description("Ticks to wait between sending queued messages.")
            .defaultValue(50)
            .min(0)
            .sliderRange(0, 100)
            .build());

    private final Setting<Boolean> ignoreFriends = sgGeneral.add(new BoolSetting.Builder()
            .name("ignore-friends")
            .description("Do not react to friends.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> onlyEnemies = sgGeneral.add(new BoolSetting.Builder()
            .name("only-enemies")
            .description("Only react to players on your enemies list.")
            .defaultValue(false)
            .build());

    private final Setting<String> prefix = sgChat.add(new StringSetting.Builder()
            .name("prefix")
            .description("Text added before every chat message you send. Leave empty to disable.")
            .defaultValue("")
            .build());

    private final Setting<String> suffix = sgChat.add(new StringSetting.Builder()
            .name("suffix")
            .description("Text added after every chat message you send. Leave empty to disable.")
            .defaultValue("")
            .build());

    private final Setting<Boolean> stayActive = sgChat.add(new BoolSetting.Builder()
            .name("stay-active")
            .description("Keeps adding the prefix and suffix to your chat messages while this module is turned off.")
            .defaultValue(false)
            .build());

    private final Setting<List<String>> ignoredStarts = sgChat.add(new StringListSetting.Builder()
            .name("ignored-starts")
            .description("Messages starting with any of these are sent untouched.")
            .defaultValue(List.of("/", ".", "#", "!"))
            .build());

    private final Setting<Boolean> killEnabled = sgKill.add(new BoolSetting.Builder()
            .name("enabled")
            .description("Sends a message when a nearby player dies.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> onlyMyKills = sgKill.add(new BoolSetting.Builder()
            .name("only-my-kills")
            .description("Only sends kill messages for players you killed. Counts players you hit in the last 5 seconds, or a death message in chat that names both of you.")
            .defaultValue(false)
            .build());

    private final Setting<List<String>> killMessages = sgKill.add(new StringListSetting.Builder()
            .name("kill-messages")
            .description("Write your own messages here. Use {name}, <name> or <n> for the player's name. Nothing is sent while the list is empty.")
            .defaultValue(new ArrayList<>())
            .build());

    private final Setting<Order> killOrder = sgKill.add(new EnumSetting.Builder<Order>()
            .name("kill-order")
            .description("How the next kill message is picked.")
            .defaultValue(Order.Random)
            .build());

    private final Setting<Boolean> killSkipPrefix = sgKill.add(new BoolSetting.Builder()
            .name("skip-prefix")
            .description("Do not add the prefix to kill messages.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> killSkipSuffix = sgKill.add(new BoolSetting.Builder()
            .name("skip-suffix")
            .description("Do not add the suffix to kill messages.")
            .defaultValue(false)
            .build());

    private final Setting<Boolean> popEnabled = sgPop.add(new BoolSetting.Builder()
            .name("enabled")
            .description("Sends a message when a nearby player pops a totem.")
            .defaultValue(false)
            .build());

    private final Setting<List<String>> popMessages = sgPop.add(new StringListSetting.Builder()
            .name("pop-messages")
            .description("Write your own messages here. Use {name}, <name> or <n> for the player's name. Nothing is sent while the list is empty.")
            .defaultValue(new ArrayList<>())
            .build());

    private final Setting<Order> popOrder = sgPop.add(new EnumSetting.Builder<Order>()
            .name("pop-order")
            .description("How the next pop message is picked.")
            .defaultValue(Order.Random)
            .build());

    private final Setting<Boolean> popSkipPrefix = sgPop.add(new BoolSetting.Builder()
            .name("pop-skip-prefix")
            .description("Do not add the prefix to pop messages.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> popSkipSuffix = sgPop.add(new BoolSetting.Builder()
            .name("pop-skip-suffix")
            .description("Do not add the suffix to pop messages.")
            .defaultValue(false)
            .build());

    private final Random random = new Random();
    private final Deque<Pending> queue = new ArrayDeque<>();
    private final Set<Integer> deadNotified = new HashSet<>();
    private final Map<Integer, Long> lastHit = new HashMap<>();
    private final Map<Integer, Unclaimed> unclaimed = new HashMap<>();
    private int timer;
    private int killIndex;
    private int popIndex;
    private boolean skipPrefixNow;
    private boolean skipSuffixNow;

    private record Pending(String text, boolean skipPrefix, boolean skipSuffix) {}

    private record Unclaimed(String name, long expires) {}

    public Ez() {
        super(SixToolsAddon.CATEGORY, "ez", "Chat prefix and suffix, plus kill and pop messages you write yourself.");
        MeteorClient.EVENT_BUS.subscribe(new ChatAffix());
    }

    @Override
    public void onActivate() {
        timer = 0;
        killIndex = 0;
        popIndex = 0;
        queue.clear();
        deadNotified.clear();
        lastHit.clear();
        unclaimed.clear();
    }

    @Override
    public void onDeactivate() {
        queue.clear();
        deadNotified.clear();
        lastHit.clear();
        unclaimed.clear();
        skipPrefixNow = false;
        skipSuffixNow = false;
    }

    public class ChatAffix {
        @EventHandler
        private void onSend(SendMessageEvent event) {
            if (!isActive() && !stayActive.get()) return;
            applyAffix(event);
        }
    }

    private void applyAffix(SendMessageEvent event) {
        String message = event.message;
        if (message == null || message.isBlank()) return;

        for (String start : ignoredStarts.get()) {
            if (!start.isEmpty() && message.startsWith(start)) return;
        }

        String p = skipPrefixNow ? "" : prefix.get().trim();
        String s = skipSuffixNow ? "" : suffix.get().trim();

        StringBuilder out = new StringBuilder();
        if (!p.isEmpty()) out.append(p).append(' ');
        out.append(message);
        if (!s.isEmpty()) out.append(' ').append(s);

        if (out.length() <= MAX_CHAT_LENGTH) event.message = out.toString();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        timer++;
        if (!queue.isEmpty() && timer >= delay.get()) {
            Pending pending = queue.poll();
            skipPrefixNow = pending.skipPrefix();
            skipSuffixNow = pending.skipSuffix();
            try {
                ChatUtils.sendPlayerMsg(pending.text());
            } finally {
                skipPrefixNow = false;
                skipSuffixNow = false;
            }
            timer = 0;
        }

        if (!killEnabled.get()) return;
        if (deadNotified.size() > 256) deadNotified.clear();

        long now = System.currentTimeMillis();
        unclaimed.values().removeIf(entry -> entry.expires() < now);
        if (lastHit.size() > 64) lastHit.values().removeIf(time -> now - time > HIT_WINDOW_MS * 2);

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (!isTarget(player)) continue;

            int id = player.getId();
            if (player.getHealth() <= 0) {
                if (deadNotified.add(id)) {
                    String name = player.getName().getString();
                    if (!onlyMyKills.get() || hitRecently(id, now)) {
                        enqueue(killMessages.get(), killOrder.get(), true, name);
                    } else {
                        unclaimed.put(id, new Unclaimed(name, now + CLAIM_WINDOW_MS));
                    }
                }
            } else {
                deadNotified.remove(id);
            }
        }
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (event.entity instanceof PlayerEntity player) lastHit.put(player.getId(), System.currentTimeMillis());
    }

    @EventHandler
    private void onMessage(ReceiveMessageEvent event) {
        if (unclaimed.isEmpty() || mc.player == null) return;

        String text = event.getMessage().getString().toLowerCase();
        if (!text.contains(mc.player.getName().getString().toLowerCase())) return;

        var iterator = unclaimed.entrySet().iterator();
        while (iterator.hasNext()) {
            Unclaimed entry = iterator.next().getValue();
            if (!text.contains(entry.name().toLowerCase())) continue;
            iterator.remove();
            enqueue(killMessages.get(), killOrder.get(), true, entry.name());
        }
    }

    private boolean hitRecently(int id, long now) {
        Long time = lastHit.get(id);
        return time != null && now - time <= HIT_WINDOW_MS;
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (!popEnabled.get() || mc.player == null || mc.world == null) return;
        if (!(event.packet instanceof EntityStatusS2CPacket packet) || packet.getStatus() != 35) return;

        Entity entity = packet.getEntity(mc.world);
        if (entity instanceof PlayerEntity player && isTarget(player)) {
            enqueue(popMessages.get(), popOrder.get(), false, player.getName().getString());
        }
    }

    private boolean isTarget(PlayerEntity player) {
        if (player == null || player == mc.player) return false;
        if (ignoreFriends.get() && Friends.get().isFriend(player)) return false;
        if (onlyEnemies.get() && !Enemies.get().isEnemy(player.getName().getString())) return false;
        return mc.player.getEntityPos().distanceTo(player.getEntityPos()) <= range.get();
    }

    private void enqueue(List<String> messages, Order order, boolean kill, String name) {
        if (messages.isEmpty()) return;

        int index;
        if (order == Order.Sequential) {
            index = (kill ? killIndex++ : popIndex++) % messages.size();
        } else {
            index = random.nextInt(messages.size());
        }

        String text = NAME_TAG.matcher(messages.get(index)).replaceAll(Matcher.quoteReplacement(name));
        if (text.isBlank()) return;

        queue.add(new Pending(
                text,
                kill ? killSkipPrefix.get() : popSkipPrefix.get(),
                kill ? killSkipSuffix.get() : popSkipSuffix.get()
        ));
    }
}