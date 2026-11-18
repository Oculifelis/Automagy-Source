package com.automagy.client;

import com.automagy.Automagy;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Client game-bus events: the crafting focus key. */
@EventBusSubscriber(modid = Automagy.MODID, value = Dist.CLIENT)
public final class AMClientTicks {
    private AMClientTicks() {
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        AMClient.clientTick();
    }
}
