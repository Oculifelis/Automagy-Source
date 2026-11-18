package com.automagy.golem;

import com.automagy.AMRegistry;
import com.leclowndu93150.thaumaturge.api.casters.IInteractWithCaster;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The Signum Entia block. Right-click with an imprinted Oculo Anima to put it in (an empty hand takes it out); a
 * wand cycles whether all, only adult or only young creatures count. It powers its neighbours and comparators by the
 * count.
 */
public class EntitySignalBlock extends BaseEntityBlock implements IInteractWithCaster {
    public EntitySignalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(EntitySignalBlock::new);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EntitySignalBlockEntity(pos, state);
    }

    @Override
    public <E extends BlockEntity> @Nullable BlockEntityTicker<E> getTicker(Level level, BlockState state, BlockEntityType<E> type) {
        return level.isClientSide() ? null : createTickerHelper(type, AMRegistry.ENTITY_SIGNAL_BE.get(), EntitySignalBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof EntityEyeItem) || !(level.getBlockEntity(pos) instanceof EntitySignalBlockEntity signal)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!stack.has(AMRegistry.ENTITY_WATCH.get())) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("automagia.entity_eye.blank"));
            }
            return InteractionResult.SUCCESS;
        }
        if (!level.isClientSide()) {
            ItemStack old = signal.eye();
            signal.setEye(stack.split(1));
            if (!old.isEmpty()) {
                player.getInventory().placeItemBackInInventory(old);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof EntitySignalBlockEntity signal && !signal.eye().isEmpty()) {
            player.getInventory().placeItemBackInInventory(signal.eye());
            signal.setEye(ItemStack.EMPTY);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean onCasterRightClick(Level level, ItemStack stack, Player player, BlockPos pos, Direction side, InteractionHand hand) {
        if (!(level.getBlockEntity(pos) instanceof EntitySignalBlockEntity signal)) {
            return false;
        }
        if (!level.isClientSide()) {
            signal.cycleAges();
            player.sendOverlayMessage(Component.translatable("automagia.entity_signal.ages." + signal.ages().name().toLowerCase()));
        }
        return true;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof EntitySignalBlockEntity signal ? signal.signal() : 0;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof EntitySignalBlockEntity signal ? signal.signal() : 0;
    }
}
