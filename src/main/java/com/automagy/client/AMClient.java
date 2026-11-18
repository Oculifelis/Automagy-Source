package com.automagy.client;

import com.automagy.AMRegistry;
import com.automagy.Automagy;
import com.automagy.block.DeviceChestBlockEntity;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.client.renderer.blockentity.state.ChestRenderState;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import com.automagy.focus.CraftingKeyPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import com.mojang.blaze3d.platform.InputConstants;

@EventBusSubscriber(modid = Automagy.MODID, value = Dist.CLIENT)
public final class AMClient {
    private AMClient() {
    }

    @SubscribeEvent
    static void fluids(RegisterFluidModelsEvent event) {
        Identifier still = Identifier.fromNamespaceAndPath(Automagy.MODID, "block/vishroom_soup_still");
        event.register(new FluidModel.Unbaked(new Material(still),
                new Material(Identifier.fromNamespaceAndPath(Automagy.MODID, "block/vishroom_soup_flow")), new Material(Identifier.withDefaultNamespace("block/water_overlay")),
                net.neoforged.neoforge.client.fluid.FluidTintSources.constant(0xFFFFFFFF), FluidTextures.macroStill(still)),
                AMRegistry.VISHROOM_SOUP, AMRegistry.VISHROOM_SOUP_FLOWING);
    }


    @SubscribeEvent
    static void screens(RegisterMenuScreensEvent event) {
        event.register(AMRegistry.RUNIC_LIST_MENU.get(), RunicListScreen::new);
        event.register(AMRegistry.GOLEM_WORKBENCH_MENU.get(), GolemWorkbenchScreen::new);
        event.register(AMRegistry.ARCHIVE_MENU.get(), ArchiveScreen::new);
        event.register(AMRegistry.FOCUS_CRAFTING_MENU.get(), FocusCraftingScreen::new);
    }

    /** Opens the crafting focus on the hotbar (the key the original's research page names). */
    static final KeyMapping CRAFTING_KEY = new KeyMapping("key.automagia.focus_crafting", InputConstants.KEY_K, KeyMapping.Category.GAMEPLAY);

    @SubscribeEvent
    static void keys(RegisterKeyMappingsEvent event) {
        event.register(CRAFTING_KEY);
    }

    /** Called every client tick, from {@link AMClientTicks}. */
    static void clientTick() {
        while (CRAFTING_KEY.consumeClick()) {
            if (Minecraft.getInstance().player != null) {
                ClientPacketDistributor.sendToServer(CraftingKeyPayload.INSTANCE);
            }
        }
    }

    @SubscribeEvent
    static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(AMRegistry.AVARICE_PEARL_ENTITY.get(), ThrownItemRenderer::new);
        event.registerBlockEntityRenderer(AMRegistry.GREEDY_CHEST_BE.get(), c -> new DeviceChestRenderer<>(c, "greedy"));
        event.registerBlockEntityRenderer(AMRegistry.EAGER_CHEST_BE.get(), c -> new DeviceChestRenderer<>(c, "eager"));
        event.registerBlockEntityRenderer(AMRegistry.TENACIOUS_CHEST_BE.get(), c -> new DeviceChestRenderer<>(c, "tenacious"));
        event.registerBlockEntityRenderer(AMRegistry.HOURGLASS_BE.get(), HourglassRenderer::new);
    }

    /** A vanilla chest drawn with the device's own texture, textures/entity/chest/&lt;name&gt;.png. */
    static final class DeviceChestRenderer<T extends DeviceChestBlockEntity> extends ChestRenderer<T> {
        private final SpriteId sprite;

        DeviceChestRenderer(BlockEntityRendererProvider.Context context, String name) {
            super(context);
            sprite = Sheets.CHEST_MAPPER.apply(Automagy.id(name));
        }

        @Override
        protected SpriteId getCustomSprite(T be, ChestRenderState state) {
            return sprite;
        }
    }
}
