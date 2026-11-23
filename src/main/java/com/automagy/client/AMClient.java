package com.automagy.client;

import com.automagy.AMRegistry;
import com.automagy.Automagy;
import com.automagy.block.DeviceChestBlockEntity;
import com.automagy.focus.CraftingKeyPayload;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.function.Function;

@EventBusSubscriber(modid = Automagy.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class AMClient {
    private AMClient() {
    }

    @SubscribeEvent
    static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(AMRegistry.VISHROOM_SOUP.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(AMRegistry.VISHROOM_SOUP_FLOWING.get(), RenderType.translucent());
        });
    }

    @SubscribeEvent
    static void extensions(RegisterClientExtensionsEvent event) {
        ResourceLocation still = Automagy.id("block/vishroom_soup_still");
        event.registerFluidType(FluidTextures.extensions(still, Automagy.id("block/vishroom_soup_flow"),
                ResourceLocation.withDefaultNamespace("block/water_overlay")), AMRegistry.VISHROOM_SOUP_TYPE.get());
        chestItem(event, AMRegistry.GREEDY_CHEST.get(), b -> AMRegistry.GREEDY_CHEST_BE.get().create(BlockPos.ZERO, b.defaultBlockState()));
        chestItem(event, AMRegistry.EAGER_CHEST.get(), b -> AMRegistry.EAGER_CHEST_BE.get().create(BlockPos.ZERO, b.defaultBlockState()));
        chestItem(event, AMRegistry.TENACIOUS_CHEST.get(), b -> AMRegistry.TENACIOUS_CHEST_BE.get().create(BlockPos.ZERO, b.defaultBlockState()));
    }

    /** The chest item drawn by its block entity renderer, as vanilla chests are. */
    private static void chestItem(RegisterClientExtensionsEvent event, Block block, Function<Block, BlockEntity> make) {
        event.registerItem(new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    Minecraft mc = Minecraft.getInstance();
                    BlockEntity shown = make.apply(block);
                    renderer = new BlockEntityWithoutLevelRenderer(mc.getBlockEntityRenderDispatcher(), mc.getEntityModels()) {
                        @Override
                        public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
                                                 MultiBufferSource buffers, int light, int overlay) {
                            Minecraft.getInstance().getBlockEntityRenderDispatcher().renderItem(shown, pose, buffers, light, overlay);
                        }
                    };
                }
                return renderer;
            }
        }, block.asItem());
    }

    @SubscribeEvent
    static void screens(RegisterMenuScreensEvent event) {
        event.register(AMRegistry.RUNIC_LIST_MENU.get(), RunicListScreen::new);
        event.register(AMRegistry.GOLEM_WORKBENCH_MENU.get(), GolemWorkbenchScreen::new);
        event.register(AMRegistry.ARCHIVE_MENU.get(), ArchiveScreen::new);
        event.register(AMRegistry.FOCUS_CRAFTING_MENU.get(), FocusCraftingScreen::new);
    }

    /** Opens the crafting focus on the hotbar (the key the original's research page names). */
    static final KeyMapping CRAFTING_KEY = new KeyMapping("key.automagia.focus_crafting", InputConstants.KEY_K, "key.categories.gameplay");

    @SubscribeEvent
    static void keys(RegisterKeyMappingsEvent event) {
        event.register(CRAFTING_KEY);
    }

    /** Called every client tick, from {@link AMClientTicks}. */
    static void clientTick() {
        while (CRAFTING_KEY.consumeClick()) {
            if (Minecraft.getInstance().player != null) {
                PacketDistributor.sendToServer(CraftingKeyPayload.INSTANCE);
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
        private final Material sprite;

        DeviceChestRenderer(BlockEntityRendererProvider.Context context, String name) {
            super(context);
            sprite = new Material(Sheets.CHEST_SHEET, Automagy.id("entity/chest/" + name));
        }

        @Override
        protected Material getMaterial(T be, ChestType type) {
            return sprite;
        }
    }
}
