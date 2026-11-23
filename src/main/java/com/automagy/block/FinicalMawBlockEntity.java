package com.automagy.block;

import com.automagy.AMRegistry;
import com.automagy.filter.Filter;
import com.automagy.filter.RunicList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import com.automagy.port.ValueInput;
import com.automagy.port.ValueOutput;
import com.automagy.port.ItemResource;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * A hungry maw with a runic list. A whitelist: it eats only what the list names, and a quantity limits how many the
 * container behind may hold. A blacklist: it eats everything but what the list names, and a quantity is left behind on
 * the ground. With no list it is as hungry as a hungry maw.
 */
public class FinicalMawBlockEntity extends HungryMawBlockEntity implements com.automagy.port.PreRemove {
    private ItemStack list = ItemStack.EMPTY;

    public FinicalMawBlockEntity(BlockPos pos, BlockState state) {
        super(AMRegistry.FINICAL_MAW_BE.get(), pos, state);
    }

    public ItemStack list() {
        return list;
    }

    void setList(ItemStack list) {
        this.list = list;
        setChanged();
    }

    @Override
    protected int appetite(ItemStack stack, IItemHandler target) {
        Filter f = Filter.of(list);
        if (f == null) {
            return stack.getCount();
        }
        ItemResource res = ItemResource.of(stack);
        RunicList.Entry e = f.entry(res);
        if (f.white()) {
            if (e == null) {
                return 0;
            }
            return e.hasQty() ? e.qty() - held(target, f, e) : stack.getCount();
        }
        if (e == null) {
            return stack.getCount();
        }
        return e.hasQty() ? stack.getCount() - e.qty() : 0;
    }

    /** How many of the items matching a list entry the container already holds. */
    private static int held(IItemHandler h, Filter f, RunicList.Entry e) {
        int n = 0;
        for (int i = 0; i < h.getSlots(); i++) {
            ItemStack s = h.getStackInSlot(i);
            if (!s.isEmpty() && f.entry(ItemResource.of(s)) == e) {
                n += s.getCount();
            }
        }
        return n;
    }

    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null && !list.isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, list);
        }
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag$, net.minecraft.core.HolderLookup.Provider reg$) {
        ValueInput in = ValueInput.of(tag$, reg$);
        super.loadAdditional(in.tag(), in.registries());
        list = in.read("list", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag$, net.minecraft.core.HolderLookup.Provider reg$) {
        ValueOutput out = ValueOutput.of(tag$, reg$);
        super.saveAdditional(out.tag(), out.registries());
        out.store("list", ItemStack.OPTIONAL_CODEC, list);
    }
}
