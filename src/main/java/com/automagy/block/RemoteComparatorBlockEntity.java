package com.automagy.block;

import com.automagy.AMRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.automagy.port.ValueInput;
import com.automagy.port.ValueOutput;
import org.jetbrains.annotations.Nullable;

/** Holds the crystalline eye and reads the comparator signal of the block it watches, four times a second. */
public class RemoteComparatorBlockEntity extends BlockEntity implements com.automagy.port.PreRemove {
    public static final int RANGE = 20;
    private ItemStack eye = ItemStack.EMPTY;

    public RemoteComparatorBlockEntity(BlockPos pos, BlockState state) {
        super(AMRegistry.REMOTE_COMPARATOR_BE.get(), pos, state);
    }

    public ItemStack eye() {
        return eye;
    }

    void setEye(ItemStack eye) {
        this.eye = eye;
        setChanged();
    }

    /** The block the eye watches, if it is in this level and within range. */
    public @Nullable BlockPos target() {
        GlobalPos link = eye.get(AMRegistry.LINK.get());
        if (level == null || link == null || link.dimension() != level.dimension() || !link.pos().closerThan(worldPosition, RANGE + 0.5)) {
            return null;
        }
        return link.pos();
    }

    /** True when a vis reader sits beside this comparator, or a tally box under it: the comparator then only relays. */
    private boolean relaying() {
        if (level.getBlockState(worldPosition.below()).getBlock() instanceof TallyBoxBlock) {
            return true;
        }
        for (Direction d : Direction.values()) {
            if (level.getBlockState(worldPosition.relative(d)).getBlock() instanceof VisReaderBlock) {
                return true;
            }
        }
        return false;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RemoteComparatorBlockEntity be) {
        if (level.getGameTime() % 5 != 0) {
            return;
        }
        int power = 0;
        BlockPos target = be.target();
        if (target != null && level.isLoaded(target) && !be.relaying()) {
            BlockState watched = level.getBlockState(target);
            if (watched.hasAnalogOutputSignal()) {
                power = watched.getAnalogOutputSignal(level, target);
            }
        }
        if (power != state.getValue(RemoteComparatorBlock.POWER)) {
            level.setBlock(pos, state.setValue(RemoteComparatorBlock.POWER, power), Block.UPDATE_ALL);
        }
    }

    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null && !eye.isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, eye);
        }
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag$, net.minecraft.core.HolderLookup.Provider reg$) {
        ValueInput in = ValueInput.of(tag$, reg$);
        super.loadAdditional(in.tag(), in.registries());
        eye = in.read("eye", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag$, net.minecraft.core.HolderLookup.Provider reg$) {
        ValueOutput out = ValueOutput.of(tag$, reg$);
        super.saveAdditional(out.tag(), out.registries());
        out.store("eye", ItemStack.OPTIONAL_CODEC, eye);
    }
}
