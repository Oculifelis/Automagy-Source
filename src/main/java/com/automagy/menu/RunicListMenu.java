package com.automagy.menu;

import com.automagy.AMRegistry;
import com.automagy.filter.RunicList;
import com.automagy.item.RunicListItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

/**
 * Writing on a runic list. Nine ghost slots hold copies, never the items themselves: click one with an item to write
 * it there, click it with an empty hand to erase it, scroll over it to change its quantity. Shift-click an item in
 * your inventory to write it in the first free place. A button toggles partial matching.
 */
public class RunicListMenu extends AbstractContainerMenu {
    public static final int TOGGLE_PARTIAL = 100;
    private final Player player;
    private final InteractionHand hand;
    private final Container ghosts = new SimpleContainer(RunicList.SIZE);

    public RunicListMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, buf.readEnum(InteractionHand.class));
    }

    public RunicListMenu(int id, Inventory inv, InteractionHand hand) {
        super(AMRegistry.RUNIC_LIST_MENU.get(), id);
        this.player = inv.player;
        this.hand = hand;
        for (int i = 0; i < RunicList.SIZE; i++) {
            addSlot(new Ghost(ghosts, i, 62 + (i % 3) * 18, 17 + (i / 3) * 18));
        }
        int held = hand == InteractionHand.MAIN_HAND ? inv.getSelectedSlot() : -1;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(col == held ? new Locked(inv, col, 8 + col * 18, 142) : new Slot(inv, col, 8 + col * 18, 142));
        }
        refresh();
    }

    public ItemStack held() {
        return player.getItemInHand(hand);
    }

    public RunicList list() {
        return held().getOrDefault(AMRegistry.RUNIC_LIST.get(), RunicList.EMPTY);
    }

    private void write(RunicList list) {
        held().set(AMRegistry.RUNIC_LIST.get(), list);
        refresh();
    }

    private void refresh() {
        RunicList list = list();
        for (int i = 0; i < RunicList.SIZE; i++) {
            RunicList.Entry e = list.at(i);
            ghosts.setItem(i, e == null ? ItemStack.EMPTY : e.item().create());
        }
    }

    private void writeAt(int slot, ItemStack stack) {
        write(list().with(new RunicList.Entry(slot, ItemStackTemplate.fromNonEmptyStack(stack.copyWithCount(1)),
                Math.max(1, stack.getCount()))));
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput input, Player player) {
        if (slotId >= 0 && slotId < RunicList.SIZE) {
            if (input == ContainerInput.PICKUP || input == ContainerInput.QUICK_MOVE) {
                ItemStack carried = getCarried();
                if (carried.isEmpty() || carried.getItem() instanceof RunicListItem) {
                    write(list().without(slotId));
                } else {
                    writeAt(slotId, carried);
                }
            }
            return;
        }
        super.clicked(slotId, button, input, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index >= RunicList.SIZE) {
            ItemStack stack = slots.get(index).getItem();
            RunicList list = list();
            if (!stack.isEmpty() && !(stack.getItem() instanceof RunicListItem) && list.find(stack) == null) {
                for (int i = 0; i < RunicList.SIZE; i++) {
                    if (list.at(i) == null) {
                        writeAt(i, stack.copyWithCount(1));
                        break;
                    }
                }
            }
        }
        return ItemStack.EMPTY;
    }

    /** Ids: {@link #TOGGLE_PARTIAL}, or slot * 2 + (0 to raise, 1 to lower) the quantity, in steps that grow with it. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        RunicList list = list();
        if (id == TOGGLE_PARTIAL) {
            write(list.withPartial(!list.partial()));
            return true;
        }
        RunicList.Entry e = list.at(id / 2);
        if (e == null) {
            return false;
        }
        int q = e.qty();
        q = id % 2 == 0 ? q + step(q) : q - step(q - 1);
        write(list.with(new RunicList.Entry(e.slot(), e.item(), Math.clamp(q, 1, RunicList.MAX_QTY))));
        return true;
    }

    /** Quantity steps: 1 up to 16, then 4 up to 64, 16 up to 256, 64 beyond. */
    private static int step(int q) {
        return q < 16 ? 1 : q < 64 ? 4 : q < 256 ? 16 : 64;
    }

    @Override
    public boolean stillValid(Player player) {
        return held().getItem() instanceof RunicListItem;
    }

    /** A place on the list: shows a copy, takes nothing, gives nothing. */
    private static class Ghost extends Slot {
        Ghost(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }

    /** The hotbar slot holding the list being written: it stays put while the menu is open. */
    private static class Locked extends Slot {
        Locked(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
