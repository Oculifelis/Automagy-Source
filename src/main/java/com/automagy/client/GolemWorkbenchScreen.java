package com.automagy.client;

import com.automagy.golem.GolemWorkbenchMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** The golem's workbench: three pattern windows, the storage board and the player's inventory on greatwood boards.
 *  Pattern windows not yet opened by fire upgrades stay shut behind locked greatwood doors. */
public class GolemWorkbenchScreen extends AbstractContainerScreen<GolemWorkbenchMenu> {
    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath("automagia", "textures/gui/golem_workbench.png");

    public GolemWorkbenchScreen(GolemWorkbenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 178;
        imageHeight = 220;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float a) {
        super.render(graphics, mouseX, mouseY, a);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float a, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);
        for (int p = menu.openPatterns(); p < 3; p++) {
            int x = leftPos + GolemWorkbenchMenu.patternX(p);
            graphics.blit(BACKGROUND, x, topPos + 4, 180.0F, 0.0F, 54, 54, 256, 256);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // the boards leave no room for titles, as in the original
    }
}
