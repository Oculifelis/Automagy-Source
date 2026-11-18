package com.automagy.client;

import com.automagy.focus.FocusCraftingMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/** The crafting focus's grid, drawn on Thaumaturge's arcane workbench background (it has no wand slot to draw). */
public class FocusCraftingScreen extends AbstractContainerScreen<FocusCraftingMenu> {
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath("thaumaturge", "textures/gui/arcane_workbench.png");
    private static final Component VIS_LOW = Component.translatable("automagia.focus_crafting.vis_low");

    public FocusCraftingScreen(FocusCraftingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 190, 234);
        titleLabelX = 8;
        titleLabelY = 4;
        inventoryLabelX = 16;
        inventoryLabelY = 140;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);
        if (menu.visLow()) {
            graphics.text(font, VIS_LOW, leftPos + 136, topPos + 84, 0xFFC04040, false);
        }
    }
}
