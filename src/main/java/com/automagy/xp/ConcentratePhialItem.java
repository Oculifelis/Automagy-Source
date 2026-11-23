package com.automagy.xp;

import com.leclowndu93150.thaumaturge.registry.TTItems;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.level.Level;

/** A Phial of Enchanter's Concentrate: drunk (used), it gives 100 points of experience and leaves an empty phial. */
public class ConcentratePhialItem extends Item {
    public ConcentratePhialItem(Properties properties) {
        super(properties);
    }

    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        InteractionResult result = useItem(level, player, hand);
        return new net.minecraft.world.InteractionResultHolder<>(result, player.getItemInHand(hand));
    }

    private InteractionResult useItem(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            player.giveExperiencePoints(ConcentrateJarBlock.PHIAL);
            level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.6f, 1.1f);
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(TTItems.PHIAL.get())));
        }
        return InteractionResult.SUCCESS;
    }
}
