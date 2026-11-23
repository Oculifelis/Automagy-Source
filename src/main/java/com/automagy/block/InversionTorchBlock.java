package com.automagy.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jetbrains.annotations.Nullable;

/**
 * A true analog inverter: where a redstone torch is simply on or off, this one gives out 15 minus the strength of the
 * signal reaching the block it is stuck to. Like a torch it never powers that block itself, and it strongly powers
 * the block on its far side.
 */
public class InversionTorchBlock extends AttachedBlock {
    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    private static final int DELAY = 2;

    public InversionTorchBlock(Properties properties) {
        super(4, 10, properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(POWER, 15));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(InversionTorchBlock::new);
    }

    private static int target(Level level, BlockPos pos, BlockState state) {
        Direction back = state.getValue(FACING).getOpposite();
        return 15 - level.getSignal(pos.relative(back), back);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moved) {
        if (target(level, pos, state) != state.getValue(POWER) && !level.getBlockTicks().willTickThisTick(pos, this)) {
            level.scheduleTick(pos, this, DELAY);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int power = target(level, pos, state);
        if (power != state.getValue(POWER)) {
            level.setBlock(pos, state.setValue(POWER, power), Block.UPDATE_ALL);
            level.updateNeighborsAt(pos.relative(state.getValue(FACING)), this);
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!old.is(this)) {
            level.scheduleTick(pos, this, DELAY);
            level.updateNeighborsAt(pos.relative(state.getValue(FACING)), this);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean moved) {
        super.onRemove(state, world, pos, newState, moved);
        if (!moved && !state.is(newState.getBlock()) && world instanceof ServerLevel level) {
            level.updateNeighborsAt(pos, this);
            level.updateNeighborsAt(pos.relative(state.getValue(FACING)), this);
        }
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    /** `direction` points from the asking block towards this torch; the block it is stuck to asks along FACING. */
    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return direction == state.getValue(FACING) ? 0 : state.getValue(POWER);
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return direction == state.getValue(FACING).getOpposite() ? state.getValue(POWER) : 0;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        int power = state.getValue(POWER);
        if (power > 0 && random.nextInt(16) < power) {
            Direction f = state.getValue(FACING);
            double x = pos.getX() + 0.5 - f.getStepX() * 0.1 + (random.nextDouble() - 0.5) * 0.2;
            double y = pos.getY() + 0.5 - f.getStepY() * 0.1 + (random.nextDouble() - 0.5) * 0.2;
            double z = pos.getZ() + 0.5 - f.getStepZ() * 0.1 + (random.nextDouble() - 0.5) * 0.2;
            level.addParticle(DustParticleOptions.REDSTONE, x + f.getStepX() * 0.3, y + f.getStepY() * 0.3, z + f.getStepZ() * 0.3, 0, 0, 0);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWER);
    }
}
