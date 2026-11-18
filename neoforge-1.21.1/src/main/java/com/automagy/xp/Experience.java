package com.automagy.xp;

import com.automagy.Automagy;
import com.leclowndu93150.thaumaturge.content.essentia.jar.BlockEntityJarBrain;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import org.jetbrains.annotations.Nullable;

/** Experience bookkeeping for the Amnesiac's Stone, the Soul Stone and the concentrate. */
public final class Experience {
    /**
     * Items that give up experience on an Amnesiac's Stone, thrown or dropped onto it: the item data map
     * {@code automagia:amnesiac_drain}, e.g. {@code "minecraft:experience_bottle": {"points": 10}}.
     */
    public record Drain(int points) {
        public static final Codec<Drain> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("points").forGetter(Drain::points)).apply(i, Drain::new));
    }

    public static final DataMapType<Item, Drain> DRAIN = DataMapType.builder(Automagy.id("amnesiac_drain"),
            Registries.ITEM, Drain.CODEC).synced(Drain.CODEC, false).build();

    private Experience() {
    }

    public static void register(RegisterDataMapTypesEvent event) {
        event.register(DRAIN);
    }

    public static int drainOf(ItemStack stack) {
        Drain d = stack.getItemHolder().getData(DRAIN);
        return d == null ? 0 : d.points();
    }

    /** Points needed to go from nothing to the start of {@code level}, as vanilla counts them. */
    public static int pointsFor(int level) {
        if (level <= 16) {
            return level * level + 6 * level;
        }
        if (level <= 31) {
            return (int) (2.5 * level * level - 40.5 * level + 360);
        }
        return (int) (4.5 * level * level - 162.5 * level + 2220);
    }

    /** Takes up to {@code levels} levels (or the last part-level) from the player; the points taken. */
    public static int drain(Player player, int levels) {
        int points;
        if (player.experienceLevel > 0) {
            points = pointsFor(player.experienceLevel) - pointsFor(Math.max(0, player.experienceLevel - levels));
        } else {
            points = Math.round(player.experienceProgress * player.getXpNeededForNextLevel());
        }
        if (points > 0) {
            player.giveExperiencePoints(-points);
        }
        return points;
    }

    /** Puts experience into the brain jar at {@code pos} if there is one; what did not fit. */
    public static int intoBrain(ServerLevel level, BlockPos pos, int points) {
        if (level.getBlockEntity(pos) instanceof BlockEntityJarBrain brain) {
            int fit = Math.min(points, BlockEntityJarBrain.XP_MAX - brain.xp());
            if (fit > 0) {
                brain.setXp(brain.xp() + fit);
                points -= fit;
            }
        }
        return points;
    }

    public static void orbs(ServerLevel level, Vec3 at, int points) {
        if (points > 0) {
            ExperienceOrb.award(level, at, points);
        }
    }

    public static @Nullable BlockEntityJarBrain brain(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof BlockEntityJarBrain brain ? brain : null;
    }
}
