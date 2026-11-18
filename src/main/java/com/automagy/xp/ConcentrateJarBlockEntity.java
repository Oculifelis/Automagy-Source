package com.automagy.xp;

import com.automagy.AMRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

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
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        xp = in.getIntOr("xp", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("xp", xp);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(AMRegistry.STORED_XP.get(), xp);
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        xp = components.getOrDefault(AMRegistry.STORED_XP.get(), 0);
    }
}
