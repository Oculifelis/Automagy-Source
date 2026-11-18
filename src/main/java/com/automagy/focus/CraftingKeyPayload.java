package com.automagy.focus;

import com.automagy.Automagy;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The crafting key was pressed: open the crafting focus on the hotbar. */
public record CraftingKeyPayload() implements CustomPacketPayload {
    public static final CraftingKeyPayload INSTANCE = new CraftingKeyPayload();
    public static final Type<CraftingKeyPayload> TYPE = new Type<>(Automagy.id("crafting_key"));
    public static final StreamCodec<ByteBuf, CraftingKeyPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, CraftingKeyPayload::handle);
    }

    private static void handle(CraftingKeyPayload payload, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player && !(player.containerMenu instanceof FocusCraftingMenu)) {
            FocusCrafting.openFromKey(player);
        }
    }
}
