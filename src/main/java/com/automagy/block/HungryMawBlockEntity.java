package com.automagy.block;

import com.automagy.AMRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemUtil;

/** Every few ticks, pulls dropped items within reach of the maw into the container it is stuck to. */
public class HungryMawBlockEntity extends BlockEntity {
    private static final double REACH = 0.75;

    public HungryMawBlockEntity(BlockPos pos, BlockState state) {
        this(AMRegistry.HUNGRY_MAW_BE.get(), pos, state);
    }

    protected HungryMawBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** How many of a dropped stack the maw will eat into its container; the hungry maw eats everything. */
    protected int appetite(ItemStack stack, ResourceHandler<ItemResource> target) {
        return stack.getCount();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, HungryMawBlockEntity be) {
        if (level.getGameTime() % 4 != 0) {
            return;
        }
        Direction facing = state.getValue(HungryMawBlock.FACING);
        ResourceHandler<ItemResource> target = level.getCapability(Capabilities.Item.BLOCK, pos.relative(facing.getOpposite()), facing);
        if (target == null) {
            return;
        }
        AABB reach = new AABB(pos).inflate(REACH);
        boolean ate = false;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, reach, Entity::isAlive)) {
            ItemStack stack = item.getItem();
            int n = Math.min(stack.getCount(), be.appetite(stack, target));
            if (n <= 0) {
                continue;
            }
            ItemStack left = ItemUtil.insertItemReturnRemaining(target, stack.copyWithCount(n), false, null);
            int eaten = n - left.getCount();
            if (eaten > 0) {
                ate = true;
                if (eaten >= stack.getCount()) {
                    item.discard();
                } else {
                    item.setItem(stack.copyWithCount(stack.getCount() - eaten));
                }
            }
        }
        if (ate) {
            ((ServerLevel) level).sendParticles(ParticleTypes.PORTAL, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.2, 0.2, 0.2, 0.05);
            level.playSound(null, pos, SoundEvents.GENERIC_EAT.value(), SoundSource.BLOCKS, 0.3f, 1.4f);
        }
    }
}
