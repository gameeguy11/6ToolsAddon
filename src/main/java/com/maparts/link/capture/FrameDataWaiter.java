package com.maparts.link.capture;

import java.util.function.BooleanSupplier;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class FrameDataWaiter {
    private static final int POLL_EVERY_TICKS = 4;
    private static final int MAX_WAIT_TICKS = 20 * 20;

    private static Runnable pendingPoll;
    private static int elapsedTicks;
    private static int ticksUntilPoll;

    private FrameDataWaiter() {}

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (pendingPoll == null) {
                return;
            }

            if (ticksUntilPoll > 0) {
                ticksUntilPoll--;
                return;
            }

            ticksUntilPoll = POLL_EVERY_TICKS;
            elapsedTicks += POLL_EVERY_TICKS;

            Runnable poll = pendingPoll;
            pendingPoll = null;
            poll.run();
        });
    }

    public static void waitUntil(BooleanSupplier ready, Runnable onReady, Runnable onTimeout) {
        elapsedTicks = 0;
        ticksUntilPoll = 0;
        pendingPoll =
                new Runnable() {
                    @Override
                    public void run() {
                        if (ready.getAsBoolean()) {
                            onReady.run();
                            return;
                        }

                        if (elapsedTicks >= MAX_WAIT_TICKS) {
                            onTimeout.run();
                            return;
                        }

                        pendingPoll = this;
                    }
                };
    }

    public static boolean isBusy() {
        return pendingPoll != null;
    }
}
