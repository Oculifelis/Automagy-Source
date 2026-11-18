package com.automagy.nether;

import com.leclowndu93150.thaumaturge.api.aspect.AspectCapabilities;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aspect.IAspectContainer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Paying essentia from the jars (any aspect container) around a device. */
final class Essentia {
    private Essentia() {
    }

    static List<IAspectContainer> around(Level level, BlockPos center, int radius) {
        List<IAspectContainer> found = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            if (level.getBlockEntity(p) != null) {
                IAspectContainer c = level.getCapability(AspectCapabilities.CONTAINER, p.immutable(), null);
                if (c != null) {
                    found.add(c);
                }
            }
        }
        return found;
    }

    /** Drains the whole cost from the containers, or nothing at all if they hold too little. */
    static boolean pay(List<IAspectContainer> jars, AspectList cost) {
        for (AspectInstance a : cost.entries()) {
            if (jars.stream().mapToInt(j -> j.amountOf(a.aspect())).sum() < a.amount()) {
                return false;
            }
        }
        for (AspectInstance a : cost.entries()) {
            int left = a.amount();
            for (IAspectContainer j : jars) {
                int take = Math.min(left, j.amountOf(a.aspect()));
                if (take > 0 && j.drain(a.aspect(), take)) {
                    left -= take;
                }
                if (left == 0) {
                    break;
                }
            }
        }
        return true;
    }

    static boolean holds(List<IAspectContainer> jars, ResourceKey<IAspect> aspect) {
        for (IAspectContainer j : jars) {
            for (Holder<IAspect> h : j.getAspects().aspects()) {
                if (h.is(aspect) && j.amountOf(h) > 0) {
                    return true;
                }
            }
        }
        return false;
    }
}
