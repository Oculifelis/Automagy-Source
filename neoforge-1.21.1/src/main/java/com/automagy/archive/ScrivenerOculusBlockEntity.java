package com.automagy.archive;

import com.automagy.AMRegistry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.automagy.port.ValueInput;
import com.automagy.port.ValueOutput;

/** Up to {@value #MAX_EYES} attuned crystalline eyes for the unseen scribe below. */
public class ScrivenerOculusBlockEntity extends BlockEntity implements com.automagy.port.PreRemove {
    public static final int MAX_EYES = 5;
    private final List<ItemStack> eyes = new ArrayList<>();

    public ScrivenerOculusBlockEntity(BlockPos pos, BlockState state) {
        super(AMRegistry.SCRIVENER_OCULUS_BE.get(), pos, state);
    }

    boolean insert(ItemStack eye) {
        if (eyes.size() >= MAX_EYES) {
            return false;
        }
        eyes.add(eye);
        setChanged();
        return true;
    }

    ItemStack takeLast() {
        if (eyes.isEmpty()) {
            return ItemStack.EMPTY;
        }
        setChanged();
        return eyes.remove(eyes.size() - 1);
    }

    /** Where its eyes look, in this dimension. */
    List<BlockPos> targets() {
        List<BlockPos> out = new ArrayList<>();
        for (ItemStack eye : eyes) {
            GlobalPos link = eye.get(AMRegistry.LINK.get());
            if (link != null && level != null && link.dimension() == level.dimension()) {
                out.add(link.pos());
            }
        }
        return out;
    }

    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null) {
            eyes.forEach(e -> Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, e));
        }
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag$, net.minecraft.core.HolderLookup.Provider reg$) {
        ValueInput in = ValueInput.of(tag$, reg$);
        super.loadAdditional(in.tag(), in.registries());
        eyes.clear();
        in.read("eyes", ItemStack.CODEC.listOf()).ifPresent(eyes::addAll);
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag$, net.minecraft.core.HolderLookup.Provider reg$) {
        ValueOutput out = ValueOutput.of(tag$, reg$);
        super.saveAdditional(out.tag(), out.registries());
        out.store("eyes", ItemStack.CODEC.listOf(), List.copyOf(eyes));
    }
}
