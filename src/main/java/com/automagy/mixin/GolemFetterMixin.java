package com.automagy.mixin;

import com.automagy.golem.GolemInhibitorBlockEntity;
import com.leclowndu93150.thaumaturge.content.golem.EntityThaumaturgeGolem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A golem in a golemic inhibitor's range counts as standing on a powered fetter. */
@Mixin(value = EntityThaumaturgeGolem.class, remap = false)
public abstract class GolemFetterMixin {
    @Inject(method = "isFettered", at = @At("RETURN"), cancellable = true)
    private void automagy$inhibitor(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && GolemInhibitorBlockEntity.inhibits((EntityThaumaturgeGolem) (Object) this)) {
            cir.setReturnValue(true);
        }
    }
}
