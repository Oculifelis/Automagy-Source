package com.automagy.block;

import com.leclowndu93150.thaumaturge.api.casters.IInteractWithCaster;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.Nullable;

/**
 * Amplified redcrystal: any signal reaching it comes out at full strength, one way only (OUTPUT), so it also works
 * as a diode. It takes signal from every side but its output. A wand turns the output.
 */
public class AmplifiedRedcrystalBlock extends RedcrystalBlock implements IInteractWithCaster {
    public static final EnumProperty<Direction> OUTPUT = EnumProperty.create("output", Direction.class);

    public AmplifiedRedcrystalBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(OUTPUT, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(AmplifiedRedcrystalBlock::new);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) {
            return null;
        }
        Direction out = context.getNearestLookingDirection();
        if (out.getAxis() == state.getValue(FACING).getAxis()) {
            out = context.getHorizontalDirection();
        }
        if (out.getAxis() == state.getValue(FACING).getAxis()) {
            out = Direction.NORTH;
        }
        return state.setValue(OUTPUT, out);
    }

    @Override
    protected int received(BlockState state, int best) {
        return best > 0 ? 15 : 0;
    }

    @Override
    protected int offer(Level level, BlockPos pos, BlockState state, BlockPos target) {
        return target.equals(pos.relative(state.getValue(OUTPUT))) ? emitted(state) : 0;
    }

    @Override
    protected boolean acceptsFrom(BlockState state, Direction from) {
        return from != state.getValue(OUTPUT);
    }

    @Override
    protected boolean givesTo(BlockState state, Direction side) {
        return side == state.getValue(OUTPUT);
    }

    @Override
    protected Direction strongSide(BlockState state) {
        return state.getValue(OUTPUT);
    }

    @Override
    public boolean onCasterRightClick(Level level, ItemStack stack, Player player, BlockPos pos, Direction side, InteractionHand hand) {
        BlockState state = level.getBlockState(pos);
        if (!level.isClientSide()) {
            Direction facing = state.getValue(FACING);
            Direction out = state.getValue(OUTPUT);
            do {
                out = Direction.values()[(out.ordinal() + 1) % 6];
            } while (out.getAxis() == facing.getAxis());
            level.setBlock(pos, state.setValue(OUTPUT, out), Block.UPDATE_ALL);
            level.updateNeighborsAt(pos.relative(state.getValue(OUTPUT)), this);
            level.updateNeighborsAt(pos.relative(out), this);
            player.displayClientMessage(Component.translatable("automagia.msg.redcrystal_output",
                    Component.translatable("automagia.direction." + out.getSerializedName())), true);
        }
        return true;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(OUTPUT);
    }
}
