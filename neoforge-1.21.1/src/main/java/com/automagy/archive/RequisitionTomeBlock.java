package com.automagy.archive;

import com.automagy.AMRegistry;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** The requisition tome: right-click to see the whole Inventarium and place orders. */
public class RequisitionTomeBlock extends ArchiveNodeBlock<RequisitionTomeBlockEntity> {
    public RequisitionTomeBlock(Properties properties) {
        super(properties, AMRegistry.REQUISITION_TOME_BE, null);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(RequisitionTomeBlock::new);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof RequisitionTomeBlockEntity tome) {
            ArchiveMenu.openTome(sp, tome);
        }
        return InteractionResult.SUCCESS;
    }
}
