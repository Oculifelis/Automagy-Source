package com.automagy.client;

import com.automagy.filter.RunicList;
import com.automagy.menu.RunicListMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** The runic list on the dispenser's 3x3 background; each ghost slot shows its quantity, scroll changes it. */
public class RunicListScreen extends AbstractContainerScreen<RunicListMenu> {
    private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace("textures/gui/container/dispenser.png");
    private Button partial;

    public RunicListScreen(RunicListMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        partial = addRenderableWidget(Button.builder(partialLabel(), b -> press(RunicListMenu.TOGGLE_PARTIAL))
                .bounds(leftPos + 118, topPos + 34, 52, 18).build());
    }

    private Component partialLabel() {
        return Component.translatable(menu.list().partial() ? "automagia.gui.list.partial" : "automagia.gui.list.exact");
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        partial.setMessage(partialLabel());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float a) {
        super.render(graphics, mouseX, mouseY, a);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float a, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);
    }

    @Override
    protected void renderSlotContents(GuiGraphics graphics, ItemStack stack, Slot slot, @Nullable String itemCount) {
        if (slot.index < RunicList.SIZE && !stack.isEmpty()) {
            RunicList.Entry e = menu.list().at(slot.index);
            itemCount = e != null && e.hasQty() ? Integer.toString(e.qty()) : null;
        }
        super.renderSlotContents(graphics, stack, slot, itemCount);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (hoveredSlot != null && hoveredSlot.index < RunicList.SIZE && hoveredSlot.hasItem() && scrollY != 0) {
            press(hoveredSlot.index * 2 + (scrollY > 0 ? 0 : 1));
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }
}
