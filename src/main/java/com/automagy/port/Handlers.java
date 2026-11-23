package com.automagy.port;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/** Item handler helpers that count by {@link ItemResource}, in place of the newer transfer API's resource calls. */
public final class Handlers {
    private Handlers() {
    }

    /** Inserts up to {@code amount} of an item across the handler's slots; returns how many went (or would go) in. */
    public static int insert(IItemHandler h, ItemResource res, int amount, boolean simulate) {
        if (res.isEmpty() || amount <= 0) {
            return 0;
        }
        int left = amount;
        int max = res.getMaxStackSize();
        while (left > 0) {
            int chunk = Math.min(left, max);
            ItemStack rest = ItemHandlerHelper.insertItemStacked(h, res.toStack(chunk), simulate);
            int in = chunk - rest.getCount();
            left -= in;
            if (in < chunk) {
                break;
            }
        }
        return amount - left;
    }

    /** Takes up to {@code amount} of an item out of the handler's slots; returns how many came (or would come) out. */
    public static int extract(IItemHandler h, ItemResource res, int amount, boolean simulate) {
        if (res.isEmpty() || amount <= 0) {
            return 0;
        }
        int got = 0;
        for (int i = 0; i < h.getSlots() && got < amount; i++) {
            if (res.matches(h.getStackInSlot(i))) {
                got += h.extractItem(i, amount - got, simulate).getCount();
            }
        }
        return got;
    }

    /** Moves up to {@code max} of an item, as much as both sides allow; returns how many moved. */
    public static int move(IItemHandler from, IItemHandler to, ItemResource res, int max) {
        int out = extract(from, res, max, true);
        int fits = insert(to, res, out, true);
        if (fits <= 0) {
            return 0;
        }
        int taken = extract(from, res, fits, false);
        int in = insert(to, res, taken, false);
        if (in < taken) {
            insert(from, res, taken - in, false);
        }
        return in;
    }
}
