package com.automagy.block;

import com.leclowndu93150.thaumaturge.api.casters.IInteractWithCaster;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Redcrystal with a strength setting (LIMIT, 1-15), stepped up with a wand and down with sneak + wand.
 * {@link Dim} caps what it gives out at LIMIT; {@link Dense} gives out nothing below LIMIT.
 */
public abstract class LimitRedcrystalBlock extends RedcrystalBlock implements IInteractWithCaster {
    public static final IntegerProperty LIMIT = IntegerProperty.create("limit", 1, 15);

    protected LimitRedcrystalBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(LIMIT, 8));
    }

    protected abstract String messageKey();

    @Override
    public boolean onCasterRightClick(Level level, ItemStack stack, Player player, BlockPos pos, Direction side, InteractionHand hand) {
        BlockState state = level.getBlockState(pos);
        if (!level.isClientSide()) {
            int limit = Math.floorMod(state.getValue(LIMIT) - 1 + (player.isSecondaryUseActive() ? -1 : 1), 15) + 1;
            BlockState now = state.setValue(LIMIT, limit);
            level.setBlock(pos, now, Block.UPDATE_ALL);
            update(level, pos, now);
            level.updateNeighborsAt(attachedTo(pos, now), this);
            player.displayClientMessage(Component.translatable(messageKey(), limit), true);
        }
        return true;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIMIT);
    }

    public static class Dim extends LimitRedcrystalBlock {
        public Dim(Properties properties) {
            super(properties);
        }

        @Override
        protected MapCodec<? extends Block> codec() {
            return simpleCodec(Dim::new);
        }

        @Override
        protected int emitted(BlockState state) {
            return Math.min(state.getValue(POWER), state.getValue(LIMIT));
        }

        @Override
        protected String messageKey() {
            return "automagia.msg.redcrystal_cap";
        }
    }

    public static class Dense extends LimitRedcrystalBlock {
        public Dense(Properties properties) {
            super(properties);
        }

        @Override
        protected MapCodec<? extends Block> codec() {
            return simpleCodec(Dense::new);
        }

        @Override
        protected int emitted(BlockState state) {
            int power = state.getValue(POWER);
            return power >= state.getValue(LIMIT) ? power : 0;
        }

        @Override
        protected String messageKey() {
            return "automagia.msg.redcrystal_min";
        }
    }
}
