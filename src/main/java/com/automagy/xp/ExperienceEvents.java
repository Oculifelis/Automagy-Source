package com.automagy.xp;

import com.automagy.AMRegistry;
import com.leclowndu93150.thaumaturge.content.essentia.jar.BlockEntityJarBrain;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** The Amnesiac's Stone's hooks into other things: thrown bottles o' enchanting, and emptying the brain jar on it. */
public final class ExperienceEvents {
    private ExperienceEvents() {
    }

    /** A thrown item of the {@code automagia:amnesiac_drain} map landing on the stone gives its points to it. */
    public static void onImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof ThrowableItemProjectile thrown) || !(thrown.level() instanceof ServerLevel level)
                || !(event.getRayTraceResult() instanceof BlockHitResult hit)) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        int points = Experience.drainOf(thrown.getItem());
        if (points > 0 && level.getBlockState(pos).is(AMRegistry.AMNESIAC_STONE.get())) {
            event.setCanceled(true);
            thrown.discard();
            AmnesiacStoneBlockEntity.give(level, pos, points);
        }
    }

    /** Sneaking with an empty hand on a brain jar that stands on the stone pours all its experience out. */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        BlockPos pos = event.getPos();
        if (event.getHand() != InteractionHand.MAIN_HAND || !event.getEntity().isShiftKeyDown()
                || !event.getEntity().getMainHandItem().isEmpty()
                || !event.getLevel().getBlockState(pos.below()).is(AMRegistry.AMNESIAC_STONE.get())
                || !(event.getLevel().getBlockEntity(pos) instanceof BlockEntityJarBrain brain)) {
            return;
        }
        if (event.getLevel() instanceof ServerLevel level) {
            Experience.orbs(level, Vec3.atCenterOf(pos.above()), brain.xp());
            brain.setXp(0);
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }
}
