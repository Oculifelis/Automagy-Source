package com.automagy.nether;

import com.automagy.AMRegistry;
import com.leclowndu93150.thaumaturge.api.casters.IInteractWithCaster;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** The Siren's Lure block; a wand switches it on and off. */
public class SirenLureBlock extends BaseEntityBlock implements IInteractWithCaster {
    public SirenLureBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(SirenLureBlock::new);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SirenLureBlockEntity(pos, state);
    }

    @Override
    public <E extends BlockEntity> @Nullable BlockEntityTicker<E> getTicker(Level level, BlockState state, BlockEntityType<E> type) {
        return level.isClientSide() ? null : createTickerHelper(type, AMRegistry.SIREN_LURE_BE.get(), SirenLureBlockEntity::serverTick);
    }

    @Override
    public boolean onCasterRightClick(Level level, ItemStack stack, Player player, BlockPos pos, Direction side, InteractionHand hand) {
        if (!(level.getBlockEntity(pos) instanceof SirenLureBlockEntity lure)) {
            return false;
        }
        if (!level.isClientSide()) {
            boolean on = lure.toggle();
            player.sendOverlayMessage(Component.translatable(!on ? "automagia.siren_lure.off"
                    : lure.totems() < 2 ? "automagia.siren_lure.totems" : "automagia.siren_lure.on"));
        }
        return true;
    }
}
