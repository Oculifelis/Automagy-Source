package com.automagy.block;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Resonating redcrystal sends its signal straight out along FACING, up to 15 blocks through open air, to a resonating
 * crystal facing back at it: stuck under a block, the other must be below it, stuck on top of a block. The pair
 * shares the signal as if they touched. A beam can be blocked or cleared at any time, so each crystal looks again
 * every second.
 */
public class ResonatingRedcrystalBlock extends RedcrystalBlock {
    public static final int RANGE = 15;
    private static final int RECHECK = 20;

    public ResonatingRedcrystalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(ResonatingRedcrystalBlock::new);
    }

    @Override
    protected List<BlockPos> farPartners(Level level, BlockPos pos, BlockState state) {
        Direction f = state.getValue(FACING);
        for (int i = 1; i <= RANGE; i++) {
            BlockPos at = pos.relative(f, i);
            if (!level.isLoaded(at)) {
                break;
            }
            BlockState there = level.getBlockState(at);
            if (there.isAir()) {
                continue;
            }
            if (i > 1 && there.getBlock() instanceof ResonatingRedcrystalBlock && there.getValue(FACING) == f.getOpposite()) {
                return List.of(at);
            }
            break;
        }
        return List.of();
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (!level.isClientSide()) {
            level.scheduleTick(pos, this, RECHECK);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        update(level, pos, state);
        level.scheduleTick(pos, this, RECHECK);
    }
}
