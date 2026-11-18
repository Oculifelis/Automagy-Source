package com.automagy.item;

import com.automagy.entity.AvaricePearlEntity;
import com.leclowndu93150.thaumaturge.api.aspect.TTAspects;
import com.leclowndu93150.thaumaturge.api.casters.ICaster;
import com.leclowndu93150.thaumaturge.api.wands.WandAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Thrown like an ender pearl (see {@link AvaricePearlEntity}). The miser's pearl draws a little ordo vis from a wand
 * the thrower carries.
 */
public class AvaricePearlItem extends Item {
    /** Ordo drawn per miser's pearl throw, in centivis. */
    private static final int MISER_COST = 50;
    private final boolean miser;

    public AvaricePearlItem(boolean miser, Properties properties) {
        super(properties);
        this.miser = miser;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (miser && !player.getAbilities().instabuild && !payOrdo(player)) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("automagia.msg.needs_ordo"));
            }
            return InteractionResult.FAIL;
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDER_PEARL_THROW, SoundSource.NEUTRAL, 0.5f,
                0.4f / (level.getRandom().nextFloat() * 0.4f + 0.8f));
        if (level instanceof ServerLevel server) {
            Projectile.spawnProjectileFromRotation(AvaricePearlEntity::new, server, stack.copyWithCount(1), player, 0.0f, 1.5f, 1.0f);
        }
        player.getCooldowns().addCooldown(stack, 20);
        stack.consume(1, player);
        return InteractionResult.SUCCESS;
    }

    private static boolean payOrdo(Player player) {
        var items = player.getInventory().getNonEquipmentItems();
        for (int i = 0; i < items.size(); i++) {
            ItemStack s = items.get(i);
            if (s.getItem() instanceof ICaster) {
                int ordo = WandAccess.getVis(s, TTAspects.ORDO);
                if (ordo >= MISER_COST) {
                    // withVis returns a changed copy
                    items.set(i, WandAccess.withVis(s, TTAspects.ORDO, ordo - MISER_COST));
                    return true;
                }
            }
        }
        return false;
    }
}
