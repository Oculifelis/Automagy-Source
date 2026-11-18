package com.automagy.block;

import com.automagy.AMRegistry;
import com.automagy.item.CrystallineEyeItem;
import com.leclowndu93150.thaumaturge.api.casters.ICaster;
import com.leclowndu93150.thaumaturge.api.casters.IInteractWithCaster;
import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The melding mirror: a framed pane facing out of the block it is placed against. Right-click it with an attuned
 * crystalline eye to set the eye in its frame; sneak-right-click it with a wand to take the eye back out.
 */
public class MeldingMirrorBlock extends BaseEntityBlock implements IInteractWithCaster {
    public static final EnumProperty<Direction> FACING = DirectionalBlock.FACING;
    public static final BooleanProperty EYE = BooleanProperty.create("eye");
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        SHAPES.put(Direction.UP, Block.box(0, 0, 0, 16, 2, 16));
        SHAPES.put(Direction.DOWN, Block.box(0, 14, 0, 16, 16, 16));
        SHAPES.put(Direction.NORTH, Block.box(0, 0, 14, 16, 16, 16));
        SHAPES.put(Direction.SOUTH, Block.box(0, 0, 0, 16, 16, 2));
        SHAPES.put(Direction.WEST, Block.box(14, 0, 0, 16, 16, 16));
        SHAPES.put(Direction.EAST, Block.box(0, 0, 0, 2, 16, 16));
    }

    public MeldingMirrorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(EYE, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(MeldingMirrorBlock::new);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MeldingMirrorBlockEntity(pos, state);
    }

    @Override
    public <E extends BlockEntity> @Nullable BlockEntityTicker<E> getTicker(Level level, BlockState state, BlockEntityType<E> type) {
        return level.isClientSide() ? null : createTickerHelper(type, AMRegistry.MELDING_MIRROR_BE.get(), MeldingMirrorBlockEntity::serverTick);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof ICaster) {
            return InteractionResult.PASS;
        }
        if (!(stack.getItem() instanceof CrystallineEyeItem) || !(level.getBlockEntity(pos) instanceof MeldingMirrorBlockEntity mirror)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!stack.has(AMRegistry.LINK.get())) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("automagia.msg.eye_unattuned"));
            }
            return InteractionResult.SUCCESS;
        }
        if (!level.isClientSide()) {
            ItemStack old = mirror.eye();
            mirror.setEye(stack.split(1));
            if (!old.isEmpty()) {
                player.getInventory().placeItemBackInInventory(old);
            }
            level.setBlock(pos, state.setValue(EYE, true), Block.UPDATE_ALL);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean onCasterRightClick(Level level, ItemStack stack, Player player, BlockPos pos, Direction side, InteractionHand hand) {
        if (!player.isSecondaryUseActive() || !(level.getBlockEntity(pos) instanceof MeldingMirrorBlockEntity mirror) || mirror.eye().isEmpty()) {
            return false;
        }
        if (!level.isClientSide()) {
            player.getInventory().placeItemBackInInventory(mirror.eye());
            mirror.setEye(ItemStack.EMPTY);
            level.setBlock(pos, level.getBlockState(pos).setValue(EYE, false), Block.UPDATE_ALL);
        }
        return true;
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
        builder.add(FACING, EYE);
    }
}
