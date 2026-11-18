package com.automagy.block;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A small thing stuck to one face of a block, like an amethyst cluster: FACING points away from the block it is
 * stuck to, and it pops off when that block can no longer hold it.
 */
public abstract class AttachedBlock extends Block {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    private final Map<Direction, VoxelShape> shapes;

    protected AttachedBlock(float width, float height, Properties properties) {
        super(properties);
        shapes = shapes(width, height);
    }

    /** One shape per facing: a width x width block, {@code height} deep, against the face it is stuck to. */
    private static Map<Direction, VoxelShape> shapes(float width, float height) {
        double lo = 8.0 - width / 2.0;
        double hi = 8.0 + width / 2.0;
        double far = 16.0 - height;
        Map<Direction, VoxelShape> map = new java.util.EnumMap<>(Direction.class);
        map.put(Direction.NORTH, Block.box(lo, lo, far, hi, hi, 16.0));
        map.put(Direction.SOUTH, Block.box(lo, lo, 0.0, hi, hi, height));
        map.put(Direction.WEST, Block.box(far, lo, lo, 16.0, hi, hi));
        map.put(Direction.EAST, Block.box(0.0, lo, lo, height, hi, hi));
        map.put(Direction.DOWN, Block.box(lo, far, lo, hi, 16.0, hi));
        map.put(Direction.UP, Block.box(lo, 0.0, lo, hi, height, hi));
        return map;
    }

    public static BlockPos attachedTo(BlockPos pos, BlockState state) {
        return pos.relative(state.getValue(FACING).getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state.getValue(FACING));
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos behind = attachedTo(pos, state);
        return level.getBlockState(behind).isFaceSturdy(level, behind, facing);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)
                ? Blocks.AIR.defaultBlockState()
                : state;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getClickedFace());
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
