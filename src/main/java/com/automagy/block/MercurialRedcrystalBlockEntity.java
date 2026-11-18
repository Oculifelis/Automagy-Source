package com.automagy.block;

import com.automagy.AMRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/** Holds the mirror a mercurial redcrystal is bound to, and keeps it in the bound-crystal index while loaded. */
public class MercurialRedcrystalBlockEntity extends BlockEntity {
    private @Nullable GlobalPos mirror;
    private boolean indexed;

    public MercurialRedcrystalBlockEntity(BlockPos pos, BlockState state) {
        super(AMRegistry.MERCURIAL_REDCRYSTAL_BE.get(), pos, state);
    }

    public @Nullable GlobalPos mirror() {
        return mirror;
    }

    void setMirror(@Nullable GlobalPos mirror) {
        unindex();
        this.mirror = mirror;
        index();
        setChanged();
    }

    private @Nullable GlobalPos self() {
        return level == null ? null : GlobalPos.of(level.dimension(), worldPosition);
    }

    private void index() {
        if (!indexed && mirror != null && level != null && !level.isClientSide()) {
            MercurialRedcrystalBlock.bind(mirror, self());
            indexed = true;
        }
    }

    private void unindex() {
        if (indexed && mirror != null) {
            MercurialRedcrystalBlock.unbind(mirror, self());
            indexed = false;
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        index();
    }

    @Override
    public void setRemoved() {
        unindex();
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        unindex();
        super.onChunkUnloaded();
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        mirror = in.read("mirror", GlobalPos.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.storeNullable("mirror", GlobalPos.CODEC, mirror);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(AMRegistry.LINK.get(), mirror);
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        mirror = components.get(AMRegistry.LINK.get());
    }
}
