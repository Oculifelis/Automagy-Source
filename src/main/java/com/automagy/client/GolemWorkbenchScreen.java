package com.automagy.client;

import com.automagy.golem.GolemWorkbenchMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/** The golem's workbench on the large chest's background; patterns not yet opened by fire upgrades are shaded. */
public class GolemWorkbenchScreen extends AbstractContainerScreen<GolemWorkbenchMenu> {
    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");

    public GolemWorkbenchScreen(GolemWorkbenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 222);
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos, 0.0F, 0.0F, imageWidth, 125, 256, 256);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos + 125, 0.0F, 126.0F, imageWidth, 96, 256, 256);
        for (int p = menu.openPatterns(); p < 3; p++) {
            graphics.fill(leftPos + 7 + p * 54, topPos + 17, leftPos + 7 + p * 54 + 54, topPos + 17 + 54, 0xA0202020);
        }
        // a line between the patterns and the storage
        graphics.fill(leftPos + 7, topPos + 71, leftPos + 169, topPos + 72, 0xFF8B8B8B);
    }
}
