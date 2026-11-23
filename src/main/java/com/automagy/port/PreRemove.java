package com.automagy.port;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Side effects a block entity runs on the server when its block is replaced by another block. */
public interface PreRemove {
    void preRemoveSideEffects(BlockPos pos, BlockState state);
}
