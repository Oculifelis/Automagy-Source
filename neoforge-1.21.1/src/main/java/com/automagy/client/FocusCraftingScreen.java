package com.automagy.client;

import com.automagy.focus.FocusCraftingMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** The crafting focus's grid, drawn on Thaumaturge's arcane workbench background (it has no wand slot to draw). */
public class FocusCraftingScreen extends AbstractContainerScreen<FocusCraftingMenu> {
    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath("thaumaturge", "textures/gui/arcane_workbench.png");
    private static final Component VIS_LOW = Component.translatable("automagia.focus_crafting.vis_low");

    public FocusCraftingScreen(FocusCraftingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 190;
        imageHeight = 234;
        inventoryLabelY = imageHeight - 94;
        titleLabelX = 8;
        titleLabelY = 4;
        inventoryLabelX = 16;
        inventoryLabelY = 140;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float a) {
        super.render(graphics, mouseX, mouseY, a);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float a, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);
        if (menu.visLow()) {
            graphics.drawString(font, VIS_LOW, leftPos + 136, topPos + 84, 0xFFC04040, false);
        }
    }
}
