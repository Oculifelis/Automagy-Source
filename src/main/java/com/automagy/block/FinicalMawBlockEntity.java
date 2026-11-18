package com.automagy.block;

import com.automagy.AMRegistry;
import com.automagy.filter.Filter;
import com.automagy.filter.RunicList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * A hungry maw with a runic list. A whitelist: it eats only what the list names, and a quantity limits how many the
 * container behind may hold. A blacklist: it eats everything but what the list names, and a quantity is left behind on
 * the ground. With no list it is as hungry as a hungry maw.
 */
public class FinicalMawBlockEntity extends HungryMawBlockEntity {
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
    protected int appetite(ItemStack stack, ResourceHandler<ItemResource> target) {
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
    private static int held(ResourceHandler<ItemResource> h, Filter f, RunicList.Entry e) {
        int n = 0;
        for (int i = 0; i < h.size(); i++) {
            if (f.entry(h.getResource(i)) == e) {
                n += h.getAmountAsInt(i);
            }
        }
        return n;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null && !list.isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, list);
        }
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        list = in.read("list", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.store("list", ItemStack.OPTIONAL_CODEC, list);
    }
}
