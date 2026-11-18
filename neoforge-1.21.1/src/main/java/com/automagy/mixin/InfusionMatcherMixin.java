package com.automagy.mixin;

import com.automagy.AMRegistry;
import com.leclowndu93150.thaumaturge.content.infusion.InfusionInput;
import com.leclowndu93150.thaumaturge.content.infusion.InfusionJobRecipe;
import com.leclowndu93150.thaumaturge.content.infusion.InfusionRecipeMatcher;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.RecipeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The infusion matrix also looks for Automagy's infusion enchanting, before Thaumaturge's own recipe types. */
@Mixin(value = InfusionRecipeMatcher.class, remap = false)
public abstract class InfusionMatcherMixin {
    @Shadow
    private static <T extends InfusionJobRecipe> InfusionRecipeMatcher.Match scan(ServerLevel level, Player player,
                                                                                 InfusionInput input, RecipeType<T> type) {
        throw new AssertionError();
    }

    @Inject(method = "find", at = @At("HEAD"), cancellable = true)
    private static void automagy$enchanting(ServerLevel level, Player player, InfusionInput input,
                                            CallbackInfoReturnable<InfusionRecipeMatcher.Match> cir) {
        InfusionRecipeMatcher.Match match = scan(level, player, input, AMRegistry.INFUSION_ENCHANTING.get());
        if (match != null && !match.locked()) {
            cir.setReturnValue(match);
        }
    }
}
