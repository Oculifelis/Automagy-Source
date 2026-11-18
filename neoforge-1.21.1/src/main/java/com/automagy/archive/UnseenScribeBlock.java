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

/** The unseen scribe: placed against the first container it is to read. Right-click lists what it reads. */
public class UnseenScribeBlock extends ArchiveNodeBlock.Facing<UnseenScribeBlockEntity> {
    public UnseenScribeBlock(Properties properties) {
        super(properties, AMRegistry.UNSEEN_SCRIBE_BE, UnseenScribeBlockEntity::serverTick);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(UnseenScribeBlock::new);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof UnseenScribeBlockEntity scribe) {
            ArchiveMenu.openScribe(sp, scribe);
        }
        return InteractionResult.SUCCESS;
    }
}
