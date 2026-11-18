package com.automagy.nether;

import com.automagy.AMRegistry;
import com.leclowndu93150.thaumaturge.api.casters.ICaster;
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

/** The Nethermind block: a wand starts it (paying vis from the wand) or stops it. */
public class NethermindBlock extends BaseEntityBlock implements IInteractWithCaster {
    private static final float START_VIS = 20f;

    public NethermindBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(NethermindBlock::new);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NethermindBlockEntity(pos, state);
    }

    @Override
    public <E extends BlockEntity> @Nullable BlockEntityTicker<E> getTicker(Level level, BlockState state, BlockEntityType<E> type) {
        return level.isClientSide() ? null : createTickerHelper(type, AMRegistry.NETHERMIND_BE.get(), NethermindBlockEntity::serverTick);
    }

    @Override
    public boolean onCasterRightClick(Level level, ItemStack stack, Player player, BlockPos pos, Direction side, InteractionHand hand) {
        if (!(level.getBlockEntity(pos) instanceof NethermindBlockEntity mind) || !(stack.getItem() instanceof ICaster caster)) {
            return false;
        }
        if (!level.isClientSide()) {
            if (mind.active()) {
                mind.stop();
                player.sendOverlayMessage(Component.translatable("automagia.nethermind.stopped"));
            } else if (mind.survey().problem() == null && !caster.consumeVis(stack, player, START_VIS, false, true)) {
                player.sendOverlayMessage(Component.translatable("automagia.nethermind.noVis"));
            } else {
                String key = mind.start();
                if (mind.active()) {
                    caster.consumeVis(stack, player, START_VIS, false, false);
                }
                player.sendSystemMessage(Component.translatable(key));
            }
        }
        return true;
    }
}
