package com.automagy.block;

import com.automagy.AMRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import com.automagy.port.ItemResource;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.Nullable;

/**
 * The tenacious chest keeps one of everything. Players take what they like, but hoppers, golems and pipes can never
 * take an item's last one: through a hopper's face it refuses the last item, and its item capability
 * ({@link #handler}) caps every extraction at one less than the chest holds.
 */
public class TenaciousChestBlockEntity extends DeviceChestBlockEntity implements WorldlyContainer {
    private static final int[] SLOTS = java.util.stream.IntStream.range(0, 27).toArray();
    private @Nullable IItemHandler handler;

    public TenaciousChestBlockEntity(BlockPos pos, BlockState state) {
        super(AMRegistry.TENACIOUS_CHEST_BE.get(), pos, state, 0);
    }

    /** The automation view of the chest: the plain chest, except extractions always leave one behind. */
    public IItemHandler handler() {
        if (handler == null) {
            handler = new InvWrapper(this) {
                @Override
                public ItemStack extractItem(int i, int amount, boolean simulate) {
                    int spare = Math.min(amount, count(ItemResource.of(getStackInSlot(i))) - 1);
                    return spare > 0 ? super.extractItem(i, spare, simulate) : ItemStack.EMPTY;
                }
            };
        }
        return handler;
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return true;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return count(ItemResource.of(stack)) > 1;
    }

    @Override
    protected int settingCount() {
        return 0;
    }

    @Override
    protected String settingKey(int i) {
        return "";
    }

    @Override
    protected Component valueName(int i) {
        return Component.empty();
    }

    @Override
    protected void step(int i) {
    }
}
