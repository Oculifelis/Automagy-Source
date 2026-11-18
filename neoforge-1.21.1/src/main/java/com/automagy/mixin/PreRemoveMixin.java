package com.automagy.mixin;

import com.automagy.port.PreRemove;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs a block entity's removal side effects before its block's own onRemove, as newer versions do. */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class PreRemoveMixin {
    @Inject(method = "onRemove", at = @At("HEAD"))
    private void automagy$preRemove(Level level, BlockPos pos, BlockState newState, boolean moved, CallbackInfo ci) {
        BlockState self = (BlockState) (Object) this;
        if (!level.isClientSide() && self.hasBlockEntity() && !self.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof PreRemove be) {
            be.preRemoveSideEffects(pos, self);
        }
    }
}
