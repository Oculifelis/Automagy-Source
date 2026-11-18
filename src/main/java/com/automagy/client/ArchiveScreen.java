package com.automagy.client;

import com.automagy.archive.ArchiveMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * The scribe's list and the requisition tome's order sheet, on the large chest's background: five rows of items, the
 * row under them for the tome's slots and the buttons. On a tome: click +1, right-click -1, shift a stack, ctrl ten,
 * alt to ask for more than is stored.
 */
public class ArchiveScreen extends AbstractContainerScreen<ArchiveMenu> {
    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final int PAGE = ArchiveMenu.COLUMNS * ArchiveMenu.ROWS;
    private boolean showAll;
    private int scroll;
    private Button toggle;

    public ArchiveScreen(ArchiveMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 222);
        inventoryLabelY = imageHeight - 94;
        showAll = menu.isTome();
    }

    private List<ArchiveMenu.Entry> list() {
        return showAll ? menu.all() : menu.own();
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos + (menu.isTome() ? 62 : 8);
        int y = topPos + 115;
        addRenderableWidget(Button.builder(Component.literal("▲"), b -> scroll(-1)).bounds(x, y, 16, 18).build());
        addRenderableWidget(Button.builder(Component.literal("▼"), b -> scroll(1)).bounds(x + 18, y, 16, 18).build());
        if (menu.isTome()) {
            addRenderableWidget(Button.builder(Component.translatable("automagia.tome.order"), b -> {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, ArchiveMenu.ORDER);
                menu.clearSheet();
            }).bounds(leftPos + 116, y, 52, 18).build());
        } else {
            toggle = addRenderableWidget(Button.builder(toggleLabel(), b -> {
                showAll = !showAll;
                scroll = 0;
                toggle.setMessage(toggleLabel());
            }).bounds(x + 40, y, 120, 18).build());
        }
    }

    private Component toggleLabel() {
        return Component.translatable(showAll ? "automagia.scribe.show_all" : "automagia.scribe.show_own");
    }

    private void scroll(int rows) {
        int maxRow = Math.max(0, (list().size() + ArchiveMenu.COLUMNS - 1) / ArchiveMenu.COLUMNS - ArchiveMenu.ROWS);
        scroll = Math.max(0, Math.min(maxRow, scroll + rows));
    }

    /** The list index under the mouse, or -1. */
    private int indexAt(double mx, double my) {
        int col = (int) Math.floor((mx - leftPos - 8) / 18);
        int row = (int) Math.floor((my - topPos - 18) / 18);
        if (col < 0 || col >= ArchiveMenu.COLUMNS || row < 0 || row >= ArchiveMenu.ROWS) {
            return -1;
        }
        int i = (scroll + row) * ArchiveMenu.COLUMNS + col;
        return i < list().size() ? i : -1;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (indexAt(mx, my) >= 0 || (my >= topPos + 18 && my < topPos + 18 + 18 * ArchiveMenu.ROWS)) {
            scroll(dy > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int i = indexAt(event.x(), event.y());
        if (i >= 0 && menu.isTome()) {
            boolean plus = event.button() == 0;
            int code = event.hasShiftDown() ? (plus ? ArchiveMenu.PLUS_STACK : ArchiveMenu.MINUS_STACK)
                    : event.hasControlDown() ? (plus ? ArchiveMenu.PLUS_TEN : ArchiveMenu.MINUS_TEN)
                    : (plus ? ArchiveMenu.PLUS : ArchiveMenu.MINUS);
            if (event.hasAltDown()) {
                code |= ArchiveMenu.ALT;
            }
            menu.apply(i, code);
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, ArchiveMenu.clickId(i, code));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos, 0.0F, 0.0F, imageWidth, 125, 256, 256);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos + 125, 0.0F, 126.0F, imageWidth, 96, 256, 256);
        // the sixth row holds the tome's three slots and the buttons: cover the slots the buttons sit on
        int from = menu.isTome() ? 3 : 0;
        graphics.fill(leftPos + 7 + from * 18, topPos + 107, leftPos + 169, topPos + 125, 0xFFC6C6C6);
        List<ArchiveMenu.Entry> list = list();
        for (int slot = 0; slot < PAGE; slot++) {
            int i = scroll * ArchiveMenu.COLUMNS + slot;
            if (i >= list.size()) {
                break;
            }
            int x = leftPos + 8 + (slot % ArchiveMenu.COLUMNS) * 18;
            int y = topPos + 18 + (slot / ArchiveMenu.COLUMNS) * 18;
            ArchiveMenu.Entry e = list.get(i);
            graphics.item(e.item(), x, y);
            graphics.itemDecorations(font, e.item(), x, y, compact(e.stored()));
            int ordered = menu.isTome() ? menu.sheet(i) : 0;
            if (ordered > 0) {
                graphics.text(font, compact(ordered), x, y, 0xFFFFE040, true);
            }
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        int i = indexAt(mouseX, mouseY);
        if (i < 0) {
            return;
        }
        ArchiveMenu.Entry e = list().get(i);
        List<Component> lines = new ArrayList<>();
        lines.add(e.item().getHoverName());
        lines.add(Component.translatable("automagia.archive.stored", e.stored()).withStyle(ChatFormatting.GRAY));
        if (menu.isTome()) {
            lines.add(Component.translatable("automagia.archive.ordered", menu.sheet(i)).withStyle(ChatFormatting.YELLOW));
        }
        graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
    }

    private static String compact(int n) {
        if (n >= 1_000_000) {
            return n / 1_000_000 + "M";
        }
        if (n >= 10_000) {
            return n / 1000 + "k";
        }
        return Integer.toString(n);
    }
}
