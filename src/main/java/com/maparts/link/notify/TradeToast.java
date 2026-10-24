package com.maparts.link.notify;

import net.minecraft.client.toast.SystemToast;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

final class TradeToast extends SystemToast {
    TradeToast(Text title, Text description) {
        super(SystemToast.Type.PERIODIC_NOTIFICATION, title, description);
    }

    @Override
    public SoundEvent getSoundEvent() {
        return SoundEvents.ENTITY_VILLAGER_YES;
    }
}
