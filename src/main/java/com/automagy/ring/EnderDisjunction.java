package com.automagy.ring;

import com.automagy.Automagy;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aspect.TTAspects;
import com.leclowndu93150.thaumaturge.content.essentia.item.ComponentEssentia;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * A ring of ender disjunction, read from the item data map {@code automagia:ender_disjunction}: whenever an ender
 * creature teleports within {@code range} of a wearer (leaving or arriving), it takes {@code damage} and the wearer
 * pays {@code perditio} essentia from a phial, jar or perditio crystal carried; nothing to pay, no harm done. A second
 * ring adds {@code second_ring} of its own damage (half, in the original).
 */
public record EnderDisjunction(float damage, double range, int perditio, float secondRing) {
    public static final Codec<EnderDisjunction> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.fieldOf("damage").forGetter(EnderDisjunction::damage),
            Codec.DOUBLE.optionalFieldOf("range", 16.0).forGetter(EnderDisjunction::range),
            Codec.INT.optionalFieldOf("perditio", 1).forGetter(EnderDisjunction::perditio),
            Codec.FLOAT.optionalFieldOf("second_ring", 0.5f).forGetter(EnderDisjunction::secondRing))
            .apply(i, EnderDisjunction::new));

    public static final DataMapType<Item, EnderDisjunction> DATA = DataMapType.builder(Automagy.id("ender_disjunction"),
            Registries.ITEM, CODEC).synced(CODEC, false).build();

    public static void register(RegisterDataMapTypesEvent event) {
        event.register(DATA);
    }

    public static void onTeleport(EntityTeleportEvent.EnderEntity event) {
        LivingEntity creature = event.getEntityLiving();
        if (!(creature.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 from = event.getPrev();
        Vec3 to = event.getTarget();
        for (Player player : level.players()) {
            if (player.isSpectator() || !creature.isAlive()) {
                continue;
            }
            List<EnderDisjunction> rings = new ArrayList<>();
            CuriosApi.getCuriosInventory(player).ifPresent(curios -> curios.findCurios(s -> s.typeHolder().getData(DATA) != null)
                    .forEach(r -> rings.add(r.stack().typeHolder().getData(DATA))));
            if (rings.isEmpty()) {
                continue;
            }
            rings.sort(Comparator.comparingDouble(EnderDisjunction::damage).reversed());
            EnderDisjunction first = rings.get(0);
            double range = rings.stream().mapToDouble(EnderDisjunction::range).max().orElse(0);
            if (player.position().distanceToSqr(from) > range * range && player.position().distanceToSqr(to) > range * range) {
                continue;
            }
            float damage = first.damage() + (rings.size() > 1 ? rings.get(1).damage() * first.secondRing() : 0f);
            if (pay(player, first.perditio())) {
                creature.hurtServer(level, level.damageSources().indirectMagic(player, player), damage);
            }
        }
    }

    /** Drains essentia of perditio from what the player carries: phials, then jars, then crystals. */
    private static boolean pay(Player player, int amount) {
        if (player.isCreative() || amount <= 0) {
            return true;
        }
        Holder<IAspect> perditio = player.registryAccess().lookupOrThrow(IAspect.REGISTRY_KEY).getOrThrow(TTAspects.PERDITIO);
        List<ItemStack> items = player.getInventory().getNonEquipmentItems();
        for (Function<ItemStack, ComponentEssentia<?>> kind : List.<Function<ItemStack, ComponentEssentia<?>>>of(
                ComponentEssentia::phial, ComponentEssentia::jar)) {
            for (ItemStack stack : items) {
                if (stack.isEmpty() || stack.getCount() != 1) {
                    continue;
                }
                ComponentEssentia<?> held = kind.apply(stack);
                AspectList aspects = held.getAspects();
                if (aspects.amountOf(perditio) >= amount) {
                    held.setAspects(aspects.remove(perditio, amount));
                    return true;
                }
            }
        }
        for (ItemStack stack : items) {
            if (!stack.isEmpty() && ComponentEssentia.crystal(stack).getAspects().amountOf(perditio) > 0) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }
}
