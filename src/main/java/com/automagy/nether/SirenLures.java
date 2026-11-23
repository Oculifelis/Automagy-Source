package com.automagy.nether;

import com.automagy.Automagy;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Moves creatures towards working Siren's Lures. Creatures of the {@code automagia:lure_immune} entity tag (golems,
 * bosses, slimes) and creatures that are fighting ignore it. One that makes no progress for ten seconds is let go and
 * not called again for thirty; endermen teleport closer instead of walking.
 */
public final class SirenLures {
    public static final TagKey<EntityType<?>> IMMUNE = TagKey.create(Registries.ENTITY_TYPE, Automagy.id("lure_immune"));
    private static final int GIVE_UP = 200;
    private static final int COOLDOWN = 600;
    private static final Map<Mob, Pull> PULLS = new WeakHashMap<>();

    private SirenLures() {
    }

    private static final class Pull {
        BlockPos lure;
        double best = Double.MAX_VALUE;
        long lastProgress;
        long freeUntil;
    }

    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide() || mob.tickCount % 20 != 0
                || SirenLureBlockEntity.loaded(mob.level()).isEmpty() || mob.getType().is(IMMUNE) || mob.getTarget() != null) {
            return;
        }
        long now = mob.level().getGameTime();
        Pull pull = PULLS.get(mob);
        if (pull != null && pull.freeUntil > now) {
            return;
        }
        SirenLureBlockEntity lure = strongest(mob);
        if (lure == null) {
            PULLS.remove(mob);
            return;
        }
        if (pull == null || !lure.getBlockPos().equals(pull.lure)) {
            pull = new Pull();
            pull.lure = lure.getBlockPos();
            pull.lastProgress = now;
            PULLS.put(mob, pull);
        }
        Vec3 goal = Vec3.atBottomCenterOf(lure.getBlockPos());
        double dist = mob.position().distanceToSqr(goal.x, mob.getY(), goal.z);
        if (dist < 4) {
            pull.lastProgress = now;
            return;
        }
        if (dist < pull.best - 0.5) {
            pull.best = dist;
            pull.lastProgress = now;
        } else if (now - pull.lastProgress > GIVE_UP) {
            pull.freeUntil = now + COOLDOWN;
            pull.best = Double.MAX_VALUE;
            mob.getNavigation().stop();
            return;
        }
        if (mob instanceof EnderMan man) {
            Vec3 step = goal.subtract(man.position()).scale(0.5);
            man.randomTeleport(man.getX() + step.x, man.getY(), man.getZ() + step.z, true);
        } else {
            mob.getNavigation().moveTo(goal.x, goal.y, goal.z, 1.0);
        }
    }

    /** The working lure in reach with the most totems, or null. */
    private static @Nullable SirenLureBlockEntity strongest(Mob mob) {
        SirenLureBlockEntity best = null;
        for (SirenLureBlockEntity lure : SirenLureBlockEntity.loaded(mob.level())) {
            if (!lure.working()) {
                continue;
            }
            BlockPos p = lure.getBlockPos();
            int r = lure.radius();
            if (Math.abs(mob.getX() - p.getX() - 0.5) > r || Math.abs(mob.getZ() - p.getZ() - 0.5) > r
                    || mob.getY() < lure.minY() || mob.getY() > lure.maxY() + 1) {
                continue;
            }
            if (best == null || lure.totems() > best.totems()) {
                best = lure;
            }
        }
        return best;
    }
}
