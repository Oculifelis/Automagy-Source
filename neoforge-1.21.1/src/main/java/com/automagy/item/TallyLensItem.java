package com.automagy.item;

import com.automagy.AMRegistry;
import com.automagy.block.TallyBoxBlock;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** A tally box lens. The clear lens is what the others are ground from; unbound and yield go onto a tally box. */
public class TallyLensItem extends Item {
    private final TallyBoxBlock.Lens lens;

    public TallyLensItem(TallyBoxBlock.Lens lens, Properties properties) {
        super(properties);
        this.lens = lens;
    }

    public TallyBoxBlock.Lens lens() {
        return lens;
    }

    /** The lens item that comes back out of a box; none for a box without one. */
    public static ItemStack stackFor(TallyBoxBlock.Lens lens) {
        return switch (lens) {
            case NONE -> ItemStack.EMPTY;
            case UNBOUND -> new ItemStack(AMRegistry.TALLY_LENS_UNBOUND.get());
            case YIELD -> new ItemStack(AMRegistry.TALLY_LENS_YIELD.get());
        };
    }
}
