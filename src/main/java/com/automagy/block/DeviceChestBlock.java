package com.automagy.block;

import com.automagy.item.RunicListItem;
import com.leclowndu93150.thaumaturge.api.casters.ICaster;
import com.leclowndu93150.thaumaturge.api.casters.IInteractWithCaster;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * A chest with a will of its own (greedy, eager, tenacious). It never pairs into a double chest. A wand configures it
 * ({@link DeviceChestBlockEntity#wandClick}); right-clicking it with a runic list puts the list in; sneak-right-click
 * with an empty hand takes the lists back out.
 */
public abstract class DeviceChestBlock extends ChestBlock implements IInteractWithCaster {
    protected DeviceChestBlock(Supplier<BlockEntityType<? extends ChestBlockEntity>> type, Properties properties) {
        super(type, SoundEvents.CHEST_OPEN, SoundEvents.CHEST_CLOSE, properties);
    }

    @Override
    public boolean chestCanConnectTo(BlockState state) {
        return false;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return blockEntityType().create(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return super.getTicker(level, state, type);
        }
        return type == blockEntityType() ? (l, p, s, be) -> ((DeviceChestBlockEntity) be).serverTick() : null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof DeviceChestBlockEntity chest) {
            chest.placedBy(player);
        }
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof ICaster) {
            return InteractionResult.PASS;
        }
        if (stack.getItem() instanceof RunicListItem && !player.isSecondaryUseActive()
                && level.getBlockEntity(pos) instanceof DeviceChestBlockEntity chest && chest.listSlots() > 0) {
            if (!level.isClientSide()) {
                chest.putList(player, hand);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof DeviceChestBlockEntity chest) {
            if (player.isSecondaryUseActive() && chest.hasLists()) {
                if (!level.isClientSide()) {
                    chest.takeLists(player);
                }
                return InteractionResult.SUCCESS;
            }
            if (!level.isClientSide()) {
                chest.beforeOpen(player);
            }
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }

    @Override
    public boolean onCasterRightClick(Level level, ItemStack stack, Player player, BlockPos pos, Direction side, InteractionHand hand) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof DeviceChestBlockEntity chest) {
            chest.wandClick(player, player.isSecondaryUseActive());
        }
        return true;
    }
}
