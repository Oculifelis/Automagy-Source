package com.automagy.archive;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import com.automagy.port.ValueInput;
import com.automagy.port.ValueOutput;
import org.jetbrains.annotations.Nullable;

/** Something a wand links to an Inventarium core: a scribe, taskmaster, requisition tome or dispersing mirror. */
public abstract class ArchiveNodeBlockEntity extends BlockEntity implements com.automagy.port.PreRemove {
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

    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        InventariumCoreBlockEntity core = core();
        if (core != null) {
            core.unlink(kind(), worldPosition);
        }
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag$, net.minecraft.core.HolderLookup.Provider reg$) {
        ValueInput in = ValueInput.of(tag$, reg$);
        super.loadAdditional(in.tag(), in.registries());
        corePos = in.read("core", BlockPos.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag$, net.minecraft.core.HolderLookup.Provider reg$) {
        ValueOutput out = ValueOutput.of(tag$, reg$);
        super.saveAdditional(out.tag(), out.registries());
        Optional.ofNullable(corePos).ifPresent(p -> out.store("core", BlockPos.CODEC, p));
    }
}
