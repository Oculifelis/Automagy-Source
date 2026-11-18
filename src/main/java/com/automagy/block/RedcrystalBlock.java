package com.automagy.block;

import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.redstone.Orientation;
import org.jspecify.annotations.Nullable;

/**
 * Redcrystal: redstone grown into a crystal stuck to a block face. It carries a signal like redstone dust, losing one
 * strength per crystal, to every redcrystal touching it and around corners: two crystals stuck to the same block on
 * neighbouring faces pass the signal when the space between them is empty. It strongly powers the block it is stuck to
 * and weakly powers everything else around it.
 *
 * <p>POWER is the strength the crystal receives. Variants change what it gives out ({@link #emitted}), which
 * neighbours it takes from or gives to, and add far partners ({@link #farPartners}).
 */
public class RedcrystalBlock extends AttachedBlock {
    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    /** False while any redcrystal reads the plain redstone around it, so crystals never read their own signal back. */
    private static boolean emitting = true;

    public RedcrystalBlock(Properties properties) {
        super(8, 6, properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(POWER, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(RedcrystalBlock::new);
    }

    // ---- variant hooks

    /** What this crystal gives out to plain blocks, from the strength it receives. */
    protected int emitted(BlockState state) {
        return state.getValue(POWER);
    }

    /** The strength it settles on, from the best signal reaching it. */
    protected int received(BlockState state, int best) {
        return best;
    }

    /** What this crystal (at pos) passes to the redcrystal at target. */
    protected int offer(Level level, BlockPos pos, BlockState state, BlockPos target) {
        return Math.max(0, emitted(state) - 1);
    }

    /** Whether this crystal takes signal from the neighbour on side `from`. */
    protected boolean acceptsFrom(BlockState state, Direction from) {
        return true;
    }

    /** Crystals that are linked to this one from afar (resonating pairs, mercurial links), in this level. */
    protected List<BlockPos> farPartners(Level level, BlockPos pos, BlockState state) {
        return List.of();
    }

    /** Crystals elsewhere that read this one and must hear when it changes; same level by default. */
    protected void notifyFar(Level level, BlockPos pos, BlockState state) {
        for (BlockPos far : farPartners(level, pos, state)) {
            level.neighborChanged(far, this, null);
        }
    }

    // ---- wiring

    /** Crystals that share a signal with this one: the six touching it and those around its corners. */
    protected static List<BlockPos> linked(Level level, BlockPos pos, BlockState state) {
        List<BlockPos> out = new ArrayList<>();
        for (Direction d : Direction.values()) {
            if (level.getBlockState(pos.relative(d)).getBlock() instanceof RedcrystalBlock) {
                out.add(pos.relative(d));
            }
        }
        Direction f = state.getValue(FACING);
        BlockPos base = pos.relative(f.getOpposite());
        for (Direction d : Direction.values()) {
            if (d.getAxis() == f.getAxis()) {
                continue;
            }
            BlockPos corner = base.relative(d);
            BlockState other = level.getBlockState(corner);
            if (other.getBlock() instanceof RedcrystalBlock && other.getValue(FACING) == d && level.isEmptyBlock(pos.relative(d))) {
                out.add(corner);
            }
        }
        return out;
    }

    private int plainInput(Level level, BlockPos pos, BlockState state) {
        int best = 0;
        emitting = false;
        try {
            for (Direction d : Direction.values()) {
                if (!acceptsFrom(state, d)) {
                    continue;
                }
                BlockPos at = pos.relative(d);
                BlockState there = level.getBlockState(at);
                if (there.getBlock() instanceof RedcrystalBlock) {
                    continue;
                }
                // dust powered by this crystal would hand the same strength straight back: count it one weaker
                int signal = there.is(net.minecraft.world.level.block.Blocks.REDSTONE_WIRE)
                        ? there.getValue(RedStoneWireBlock.POWER) - 1
                        : level.getSignal(at, d);
                best = Math.max(best, signal);
            }
        } finally {
            emitting = true;
        }
        return best;
    }

    private int target(Level level, BlockPos pos, BlockState state) {
        int best = plainInput(level, pos, state);
        List<BlockPos> sources = new ArrayList<>(linked(level, pos, state));
        sources.addAll(farPartners(level, pos, state));
        for (BlockPos at : sources) {
            BlockState other = level.getBlockState(at);
            if (other.getBlock() instanceof RedcrystalBlock crystal) {
                Direction from = directionTo(pos, at);
                if (from == null || acceptsFrom(state, from)) {
                    best = Math.max(best, crystal.offer(level, at, other, pos));
                }
            }
        }
        best = Math.max(best, extraInput(level, pos, state));
        return received(state, Math.min(15, best));
    }

    /** Signal from outside the crystal network that a variant reads (mercurial links in other levels). */
    protected int extraInput(Level level, BlockPos pos, BlockState state) {
        return 0;
    }

    private static @Nullable Direction directionTo(BlockPos from, BlockPos to) {
        BlockPos d = to.subtract(from);
        for (Direction dir : Direction.values()) {
            if (dir.getUnitVec3i().equals(d)) {
                return dir;
            }
        }
        return null;
    }

    protected void update(Level level, BlockPos pos, BlockState state) {
        int power = target(level, pos, state);
        if (power == state.getValue(POWER)) {
            return;
        }
        BlockState now = state.setValue(POWER, power);
        level.setBlock(pos, now, Block.UPDATE_CLIENTS);
        spread(level, pos, now);
    }

    private void spread(Level level, BlockPos pos, BlockState state) {
        level.updateNeighborsAt(pos, this);
        level.updateNeighborsAt(attachedTo(pos, state), this);
        level.updateNeighborsAt(pos.relative(strongSide(state)), this);
        for (BlockPos at : linked(level, pos, state)) {
            if (!at.closerThan(pos, 1.01)) {
                level.neighborChanged(at, this, null);
            }
        }
        notifyFar(level, pos, state);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean moved) {
        if (!level.isClientSide()) {
            if (!state.canSurvive(level, pos)) {
                level.destroyBlock(pos, true);
            } else {
                update(level, pos, state);
            }
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!old.is(state.getBlock()) && !level.isClientSide()) {
            update(level, pos, state);
            spread(level, pos, level.getBlockState(pos));
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved) {
        if (!moved) {
            level.updateNeighborsAt(pos, this);
            level.updateNeighborsAt(attachedTo(pos, state), this);
            Direction f = state.getValue(FACING);
            for (Direction d : Direction.values()) {
                if (d.getAxis() != f.getAxis()) {
                    level.neighborChanged(attachedTo(pos, state).relative(d), this, null);
                }
            }
            notifyFar(level, pos, state);
        }
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    /** `direction` points from the asking block towards this crystal. */
    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return emitting && givesTo(state, direction.getOpposite()) ? emitted(state) : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return emitting && direction.getOpposite() == strongSide(state) ? emitted(state) : 0;
    }

    /** The side this crystal strongly powers: the block it is stuck to. */
    protected Direction strongSide(BlockState state) {
        return state.getValue(FACING).getOpposite();
    }

    /** Whether this crystal powers the plain block on side `side`. */
    protected boolean givesTo(BlockState state, Direction side) {
        return true;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        int power = emitted(state);
        if (power > 0 && random.nextInt(20) < power) {
            Direction f = state.getValue(FACING);
            double x = pos.getX() + 0.5 - f.getStepX() * 0.3 + (random.nextDouble() - 0.5) * 0.4;
            double y = pos.getY() + 0.5 - f.getStepY() * 0.3 + (random.nextDouble() - 0.5) * 0.4;
            double z = pos.getZ() + 0.5 - f.getStepZ() * 0.3 + (random.nextDouble() - 0.5) * 0.4;
            level.addParticle(DustParticleOptions.REDSTONE, x, y, z, 0, 0, 0);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWER);
    }
}
