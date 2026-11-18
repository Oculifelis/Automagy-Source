package com.automagy.archive;

import com.automagy.AMRegistry;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.BaseEntityBlock;

/** The golemic taskmaster: placed against its drop-off (a container or a mirror), facing away from it. */
public class GolemTaskmasterBlock extends ArchiveNodeBlock.Facing<GolemTaskmasterBlockEntity> {
    public GolemTaskmasterBlock(Properties properties) {
        super(properties, AMRegistry.GOLEM_TASKMASTER_BE, GolemTaskmasterBlockEntity::serverTick);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(GolemTaskmasterBlock::new);
    }
}
