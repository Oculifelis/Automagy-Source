package com.automagy.golem;

import com.automagy.AMRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The golem's workbench on a large chest's background: the top three rows are the patterns (three 3x3 grids side by
 * side, the second and third open with fire upgrades), the bottom three the storage. Pattern slots hold copies, like a
 * runic list: click one with an item to write it there, with an empty hand to erase it.
 */
public class GolemWorkbenchMenu extends AbstractContainerMenu {
    private static final int GHOSTS = GolemWorkbenchBlockEntity.PATTERNS * 9;
    private static final int STORE_END = GHOSTS + GolemWorkbenchBlockEntity.STORAGE;
    private final Container patterns;
    private final ContainerData data;
    private final ContainerLevelAccess access;

    public GolemWorkbenchMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, new SimpleContainer(GHOSTS), new SimpleContainer(GolemWorkbenchBlockEntity.STORAGE),
                new SimpleContainerData(1), buf.readBlockPos());
    }

    public GolemWorkbenchMenu(int id, Inventory inv, Container patterns, Container storage, ContainerData data, BlockPos pos) {
        super(AMRegistry.GOLEM_WORKBENCH_MENU.get(), id);
        this.patterns = patterns;
        this.data = data;
        this.access = inv.player.level().isClientSide() ? ContainerLevelAccess.NULL : ContainerLevelAccess.create(inv.player.level(), pos);
        for (int p = 0; p < GolemWorkbenchBlockEntity.PATTERNS; p++) {
            for (int i = 0; i < 9; i++) {
                addSlot(new Ghost(patterns, p * 9 + i, 8 + (p * 3 + i % 3) * 18, 18 + (i / 3) * 18));
            }
        }
        for (int i = 0; i < GolemWorkbenchBlockEntity.STORAGE; i++) {
            addSlot(new Slot(storage, i, 8 + (i % 9) * 18, 72 + (i / 9) * 18));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 139 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, 8 + col * 18, 197));
        }
        addDataSlots(data);
    }

    /** Patterns in use: 1 to 3. */
    public int openPatterns() {
        return Math.max(1, data.get(0));
    }

    @Override
    public void clicked(int slotId, int button, ClickType input, Player player) {
        if (slotId >= 0 && slotId < GHOSTS) {
            if (slotId / 9 < openPatterns() && (input == ClickType.PICKUP || input == ClickType.QUICK_MOVE)) {
                ItemStack carried = getCarried();
                patterns.setItem(slotId, carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
            }
            return;
        }
        super.clicked(slotId, button, input, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (index < GHOSTS || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        boolean moved = index < STORE_END ? moveItemStackTo(stack, STORE_END, slots.size(), true)
                : moveItemStackTo(stack, GHOSTS, STORE_END, false);
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, AMRegistry.GOLEM_WORKBENCH.get());
    }

    /** A place in a pattern: shows a copy, takes nothing, gives nothing. */
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
}
