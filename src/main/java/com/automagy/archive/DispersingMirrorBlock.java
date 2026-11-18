package com.automagy.archive;

import com.automagy.AMRegistry;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.BaseEntityBlock;

/** The dispersing mirror: takes deliveries and sends each order on to its requisition tome's output mirror. */
public class DispersingMirrorBlock extends ArchiveNodeBlock<DispersingMirrorBlockEntity> {
    public DispersingMirrorBlock(Properties properties) {
        super(properties, AMRegistry.DISPERSING_MIRROR_BE, null);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(DispersingMirrorBlock::new);
    }
}
