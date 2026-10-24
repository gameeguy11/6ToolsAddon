package com.maparts.link;

import com.maparts.link.capture.FrameDataWaiter;
import com.maparts.link.command.LinkCommand;
import com.maparts.link.command.MapSelectCommand;
import com.maparts.link.command.MapUploadAllCommand;
import com.maparts.link.command.MapUploadCommand;
import com.maparts.link.notify.TradeNotifier;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

public final class MapartsLinkMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess) -> {
                    LinkCommand.register(dispatcher);
                    MapSelectCommand.register(dispatcher);
                    MapUploadCommand.register(dispatcher);
                    MapUploadAllCommand.register(dispatcher);
                });

        TradeNotifier.init();
        FrameDataWaiter.init();
    }
}
