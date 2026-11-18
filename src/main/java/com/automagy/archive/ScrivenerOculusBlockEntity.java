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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Up to {@value #MAX_EYES} attuned crystalline eyes for the unseen scribe below. */
public class ScrivenerOculusBlockEntity extends BlockEntity {
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

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            eyes.forEach(e -> Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, e));
        }
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        eyes.clear();
        in.read("eyes", ItemStack.CODEC.listOf()).ifPresent(eyes::addAll);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.store("eyes", ItemStack.CODEC.listOf(), List.copyOf(eyes));
    }
}
