package com.automagy.archive;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/** Something a wand links to an Inventarium core: a scribe, taskmaster, requisition tome or dispersing mirror. */
public abstract class ArchiveNodeBlockEntity extends BlockEntity {
    protected @Nullable BlockPos corePos;

    protected ArchiveNodeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public abstract InventariumCoreBlockEntity.Kind kind();

    /** The linked core, while it is loaded, formed and still has this node. */
    public @Nullable InventariumCoreBlockEntity core() {
        if (corePos == null || level == null || !level.isLoaded(corePos)) {
            return null;
        }
        return level.getBlockEntity(corePos) instanceof InventariumCoreBlockEntity core && core.formed()
                && core.has(kind(), worldPosition) ? core : null;
    }

    /** A wand on the node: links it to the preferred core in range, else the nearest one with room. */
    public Component link() {
        var cores = InventariumCoreBlockEntity.inRange(level, worldPosition);
        if (cores.isEmpty()) {
            return Component.translatable("automagia.inventarium.out_of_range");
        }
        for (InventariumCoreBlockEntity core : cores) {
            if (core.link(kind(), worldPosition)) {
                InventariumCoreBlockEntity old = core();
                if (old != null && old != core) {
                    old.unlink(kind(), worldPosition);
                }
                corePos = core.getBlockPos();
                setChanged();
                BlockPos p = corePos;
                return Component.translatable("automagia.inventarium.linked", p.getX(), p.getY(), p.getZ());
            }
        }
        return Component.translatable("automagia.inventarium.no_room");
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        InventariumCoreBlockEntity core = core();
        if (core != null) {
            core.unlink(kind(), worldPosition);
        }
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        corePos = in.read("core", BlockPos.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        Optional.ofNullable(corePos).ifPresent(p -> out.store("core", BlockPos.CODEC, p));
    }
}
