package com.automagy.xp;

import com.automagy.AMRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Amnesiac's Stone draws experience out: from a player who clicks it (one level, five sneaking) or stands on it
 * (all of it, a level at a time), and from items of the {@code automagia:amnesiac_drain} data map dropped or thrown
 * onto it. The experience goes into a brain jar standing on it, or comes out as orbs above.
 */
public class AmnesiacStoneBlockEntity extends BlockEntity {
    public AmnesiacStoneBlockEntity(BlockPos pos, BlockState state) {
        super(AMRegistry.AMNESIAC_STONE_BE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AmnesiacStoneBlockEntity be) {
        if (!(level instanceof ServerLevel server) || level.getGameTime() % 10 != 0) {
            return;
        }
        level.updateNeighbourForOutputSignal(pos, state.getBlock());
        AABB top = new AABB(pos.getX(), pos.getY() + 1, pos.getZ(), pos.getX() + 1, pos.getY() + 1.25, pos.getZ() + 1);
        for (Player player : server.getEntitiesOfClass(Player.class, top, p -> !p.isSpectator())) {
            give(server, pos, Experience.drain(player, 1));
        }
        for (ItemEntity item : server.getEntitiesOfClass(ItemEntity.class, top)) {
            ItemStack stack = item.getItem();
            int points = Experience.drainOf(stack);
            if (points > 0) {
                give(server, pos, points * stack.getCount());
                item.discard();
            }
        }
    }

    /** Experience drawn out at the stone at {@code pos}: into the brain above, the rest as orbs. */
    public static void give(ServerLevel level, BlockPos pos, int points) {
        if (points <= 0) {
            return;
        }
        int left = Experience.intoBrain(level, pos.above(), points);
        Experience.orbs(level, Vec3.atCenterOf(pos.above(Experience.brain(level, pos.above()) != null ? 2 : 1)), left);
        level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.3f, 0.5f + level.getRandom().nextFloat() * 0.3f);
    }
}
