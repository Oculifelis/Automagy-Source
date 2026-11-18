package com.automagy.archive;

import com.automagy.AMRegistry;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/** The scrivener's oculus: sits on an unseen scribe; attuned crystalline eyes go in, an empty hand takes the last out. */
public class ScrivenerOculusBlock extends BaseEntityBlock {
    public ScrivenerOculusBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(ScrivenerOculusBlock::new);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ScrivenerOculusBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(AMRegistry.CRYSTALLINE_EYE.get()) || !stack.has(AMRegistry.LINK.get())
                || !(level.getBlockEntity(pos) instanceof ScrivenerOculusBlockEntity oculus)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!level.isClientSide() && oculus.insert(stack.copyWithCount(1))) {
            stack.consume(1, player);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ScrivenerOculusBlockEntity oculus) {
            ItemStack eye = oculus.takeLast();
            if (!eye.isEmpty() && !player.getInventory().add(eye)) {
                player.drop(eye, false);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
