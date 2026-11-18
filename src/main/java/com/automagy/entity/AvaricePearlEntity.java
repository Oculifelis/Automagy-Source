package com.automagy.entity;

import com.automagy.AMRegistry;
import com.automagy.Automagy;
import com.leclowndu93150.thaumaturge.api.capability.KnowledgeAccess;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A thrown avarice or miser's pearl. The avarice pearl, where it lands, pulls the loose items around it to its thrower;
 * the more items and the more kinds it moves, the likelier it is to hurt its thrower or shatter (the first shattering
 * teaches Structured Avarice). The miser's pearl gathers items as it flies, at most ten stacks a throw, and never
 * shatters. An unbroken pearl comes back to its thrower.
 */
public class AvaricePearlEntity extends ThrowableItemProjectile {
    private static final double LANDING_REACH = 4.0;
    private static final double FLIGHT_REACH = 2.5;
    private static final int MISER_STACKS = 10;
    private int gathered;

    public AvaricePearlEntity(EntityType<? extends AvaricePearlEntity> type, Level level) {
        super(type, level);
    }

    public AvaricePearlEntity(Level level, LivingEntity thrower, ItemStack stack) {
        super(AMRegistry.AVARICE_PEARL_ENTITY.get(), thrower, level, stack);
    }

    @Override
    protected Item getDefaultItem() {
        return AMRegistry.AVARICE_PEARL.get();
    }

    private boolean miser() {
        return getItem().is(AMRegistry.MISERS_PEARL.get());
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && isAlive() && miser() && gathered < MISER_STACKS && getOwner() instanceof LivingEntity owner) {
            for (ItemEntity item : nearby(FLIGHT_REACH)) {
                if (gathered >= MISER_STACKS) {
                    break;
                }
                bring(item, owner);
                gathered++;
            }
        }
    }

    private List<ItemEntity> nearby(double reach) {
        return level().getEntitiesOfClass(ItemEntity.class, new AABB(position(), position()).inflate(reach), Entity::isAlive);
    }

    private static void bring(ItemEntity item, Entity to) {
        item.setPos(to.getX(), to.getY() + 0.25, to.getZ());
        item.setDeltaMovement(Vec3.ZERO);
        item.setNoPickUpDelay();
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        Entity owner = getOwner();
        boolean shattered = false;
        if (!miser() && owner instanceof LivingEntity thrower) {
            List<ItemEntity> items = nearby(LANDING_REACH);
            Set<Item> kinds = new HashSet<>();
            int count = 0;
            for (ItemEntity item : items) {
                kinds.add(item.getItem().getItem());
                count += item.getItem().getCount();
                bring(item, thrower);
            }
            // instability grows with the stacks and the kinds of thing moved
            float risk = Math.min(0.85f, items.size() * 0.03f + kinds.size() * 0.05f + count / 640f);
            if (random.nextFloat() < risk) {
                thrower.hurtServer(server, damageSources().indirectMagic(this, thrower), 1 + items.size() / 4f);
            }
            if (random.nextFloat() < risk * 0.6f) {
                shattered = true;
                shatter(server, thrower);
            }
        }
        if (!shattered) {
            ItemStack back = getItem().copyWithCount(1);
            if (owner instanceof Player player && player.isAlive()) {
                player.getInventory().placeItemBackInInventory(back);
            } else {
                spawnAtLocation(server, back);
            }
        }
        server.sendParticles(ParticleTypes.PORTAL, getX(), getY(), getZ(), 12, 0.3, 0.3, 0.3, 0.1);
        discard();
    }

    private void shatter(ServerLevel server, LivingEntity thrower) {
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.8f, 1.2f);
        ItemStack shards = new ItemStack(AMRegistry.SHATTERED_AVARICE_PEARL.get());
        if (thrower instanceof ServerPlayer player) {
            player.getInventory().placeItemBackInInventory(shards);
            var knowledge = KnowledgeAccess.of(player);
            if (knowledge != null && !knowledge.isResearchKnown(Automagy.id("avaricious"))
                    && knowledge.isResearchComplete(Automagy.id("greedy_chest"))) {
                knowledge.addResearch(Automagy.id("avaricious"));
                knowledge.sync(player);
                player.sendSystemMessage(Component.translatable("automagia.msg.avarice_insight"));
            }
        } else {
            spawnAtLocation(server, shards);
        }
    }
}
