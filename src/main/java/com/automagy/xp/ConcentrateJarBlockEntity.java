package com.automagy.xp;

import com.automagy.AMRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.automagy.port.ValueInput;
import com.automagy.port.ValueOutput;

/** A warded jar of enchanter's concentrate: up to 1000 points of experience, kept when the jar is broken. */
public class ConcentrateJarBlockEntity extends BlockEntity {
    public static final int CAPACITY = 1000;

    private int xp;

    public ConcentrateJarBlockEntity(BlockPos pos, BlockState state) {
        super(AMRegistry.CONCENTRATE_JAR_BE.get(), pos, state);
    }

    public int xp() {
        return xp;
    }

    /** Adds up to {@code points}; how many went in. */
    public int add(int points) {
        int fit = Math.min(points, CAPACITY - xp);
        if (fit > 0) {
            xp += fit;
            changed();
        }
        return fit;
    }

    /** Takes exactly {@code points} if the jar holds them. */
    public boolean take(int points) {
        if (xp < points) {
            return false;
        }
        xp -= points;
        changed();
        return true;
    }

    private void changed() {
        setChanged();
        if (level != null) {
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag$, net.minecraft.core.HolderLookup.Provider reg$) {
        ValueInput in = ValueInput.of(tag$, reg$);
        super.loadAdditional(in.tag(), in.registries());
        xp = in.getIntOr("xp", 0);
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag$, net.minecraft.core.HolderLookup.Provider reg$) {
        ValueOutput out = ValueOutput.of(tag$, reg$);
        super.saveAdditional(out.tag(), out.registries());
        out.putInt("xp", xp);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(AMRegistry.STORED_XP.get(), xp);
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput components) {
        super.applyImplicitComponents(components);
        xp = components.getOrDefault(AMRegistry.STORED_XP.get(), 0);
    }
}
