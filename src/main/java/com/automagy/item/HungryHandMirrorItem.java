package com.automagy.item;

import com.automagy.Automagy;
import com.automagy.filter.Filter;
import com.automagy.filter.RunicList;
import com.leclowndu93150.thaumaturge.content.device.mirror.BlockEntityMirror;
import com.leclowndu93150.thaumaturge.content.device.mirror.BlockMirror;
import com.leclowndu93150.thaumaturge.content.device.mirror.ItemHandMirror;
import com.leclowndu93150.thaumaturge.api.capability.KnowledgeAccess;
import com.leclowndu93150.thaumaturge.content.research.DeviceGate;
import com.leclowndu93150.thaumaturge.registry.TTDataComponents;
import com.leclowndu93150.thaumaturge.registry.TTSounds;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import com.automagy.port.ItemResource;
import org.jetbrains.annotations.Nullable;

/**
 * A hand mirror that holds up to two runic lists. Linked to a magic mirror like Thaumaturge's hand mirror, it sends what
 * its carrier picks up through the link on its own, but only items a list names: a whitelist sends what it names
 * (quantities ignored), a blacklist sends whatever the carrier holds beyond a named item's quantity.
 * Once linked: right-click with a runic list in the other hand to put it in, with any other item there to shove that
 * stack through by hand; sneak-right-click with the other hand empty to take the lists out.
 */
public class HungryHandMirrorItem extends Item {
    public static final net.minecraft.resources.ResourceLocation GATE = Automagy.id("auto_hand_mirror");

    public HungryHandMirrorItem(Properties properties) {
        super(properties);
    }

    public static final int LISTS = 2;

    public static List<ItemStack> lists(ItemStack mirror) {
        return mirror.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyStream().map(ItemStack::copy).toList();
    }

    /** Puts a list in: into a free place, else in place of the first list; returns the list it pushed out, if any. */
    private static ItemStack addList(ItemStack mirror, ItemStack list) {
        List<ItemStack> now = new ArrayList<>(lists(mirror));
        ItemStack out = ItemStack.EMPTY;
        if (now.size() >= LISTS) {
            out = now.removeFirst();
        }
        now.add(list);
        mirror.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(now));
        return out;
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!(level.getBlockState(pos).getBlock() instanceof BlockMirror mirror) || mirror.isEssentia()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof BlockEntityMirror) {
            stack.set(TTDataComponents.MIRROR_LINK.get(), GlobalPos.of(level.dimension(), pos));
            level.playSound(null, pos, TTSounds.JAR.get(), SoundSource.BLOCKS, 0.5f, 1f);
            if (context.getPlayer() != null) {
                context.getPlayer().sendSystemMessage(Component.translatable("message.thaumaturge.hand_mirror.linked"));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        InteractionResult result = useItem(level, player, hand);
        return new net.minecraft.world.InteractionResultHolder<>(result, player.getItemInHand(hand));
    }

    private InteractionResult useItem(Level level, Player player, InteractionHand hand) {
        ItemStack mirror = player.getItemInHand(hand);
        InteractionHand otherHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack other = player.getItemInHand(otherHand);
        if (!mirror.has(TTDataComponents.MIRROR_LINK.get())) {
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.translatable("automagia.msg.mirror_unlinked"), true);
            }
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer server) || !understood(player)) {
            return InteractionResult.PASS;
        }
        if (other.getItem() instanceof RunicListItem) {
            ItemStack list = other.split(1);
            ItemStack out = addList(mirror, list);
            if (!out.isEmpty()) {
                player.getInventory().placeItemBackInInventory(out);
            }
            player.displayClientMessage(Component.translatable("automagia.msg.list_in", list.getHoverName()), true);
        } else if (player.isSecondaryUseActive()) {
            if (other.isEmpty()) {
                lists(mirror).forEach(l -> player.getInventory().placeItemBackInInventory(l));
                mirror.remove(DataComponents.CONTAINER);
            }
        } else if (!other.isEmpty() && ItemHandMirror.transport(mirror, other.copy(), server)) {
            player.setItemInHand(otherHand, ItemStack.EMPTY);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, java.util.List<Component> tooltip, TooltipFlag flag) {
        GlobalPos link = stack.get(TTDataComponents.MIRROR_LINK.get());
        if (link != null) {
            tooltip.add(Component.translatable("automagia.tip.linked", link.pos().getX(), link.pos().getY(), link.pos().getZ()));
        }
        List<ItemStack> lists = lists(stack);
        if (lists.isEmpty()) {
            tooltip.add(Component.translatable("automagia.tip.no_list"));
        }
        lists.forEach(l -> tooltip.add(Component.translatable("automagia.tip.holds_list", l.getHoverName())));
    }

    /** After a pickup, every linked hungry hand mirror the player carries sends its share of what came in. */
    public static void onPickup(ItemEntityPickupEvent.Post event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack original = event.getOriginalStack();
        int picked = original.getCount() - event.getCurrentStack().getCount();
        // Pickups never call DeviceGate.passes: it prints "not understood" on every failed check. Only a player
        // carrying a linked mirror is checked, and silently; the message stays on a real use of the mirror.
        if (picked <= 0 || !carriesLinkedMirror(player)
                || !KnowledgeAccess.of(player).isResearchComplete(GATE)) {
            return;
        }
        ItemResource res = ItemResource.of(original);
        NonNullList<ItemStack> items = player.getInventory().items;
        for (ItemStack mirror : items) {
            if (picked <= 0) {
                return;
            }
            if (!(mirror.getItem() instanceof HungryHandMirrorItem) || !mirror.has(TTDataComponents.MIRROR_LINK.get())) {
                continue;
            }
            int held = count(items, res);
            int send = 0;
            for (ItemStack list : lists(mirror)) {
                send = Math.max(send, share(Filter.of(list), res, picked, held));
            }
            if (send > 0 && ItemHandMirror.transport(mirror, original.copyWithCount(send), player)) {
                take(items, res, send);
                picked -= send;
            }
        }
    }

    private static final java.util.Map<java.util.UUID, Long> LAST_WARNED = new java.util.WeakHashMap<>();

    /** Research check for a real use. The "not understood" line shows at most once every two seconds. */
    private static boolean understood(Player player) {
        if (KnowledgeAccess.of(player).isResearchComplete(GATE)) {
            return true;
        }
        long now = player.level().getGameTime();
        Long last = LAST_WARNED.get(player.getUUID());
        if (last == null || now - last >= 40 || now < last) {
            LAST_WARNED.put(player.getUUID(), now);
            DeviceGate.passes(player, GATE);
        }
        return false;
    }

    private static boolean carriesLinkedMirror(Player player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof HungryHandMirrorItem && stack.has(TTDataComponents.MIRROR_LINK.get())) {
                return true;
            }
        }
        return false;
    }

    /** Only named items go: all of a whitelisted one, and of a blacklisted one whatever is held over its quantity. */
    private static int share(@Nullable Filter f, ItemResource res, int picked, int held) {
        RunicList.Entry e = f == null ? null : f.entry(res);
        if (e == null) {
            return 0;
        }
        if (f.white()) {
            return picked;
        }
        return e.hasQty() ? Math.min(picked, held - e.qty()) : 0;
    }

    private static int count(List<ItemStack> items, ItemResource res) {
        int n = 0;
        for (ItemStack s : items) {
            if (res.matches(s)) {
                n += s.getCount();
            }
        }
        return n;
    }

    /** Takes from the main inventory first, then the hotbar, last slot first, so the stack in hand goes last. */
    private static void take(List<ItemStack> items, ItemResource res, int n) {
        for (int i = items.size() - 1; i >= 0 && n > 0; i--) {
            ItemStack s = items.get(i);
            if (res.matches(s)) {
                int t = Math.min(n, s.getCount());
                s.shrink(t);
                n -= t;
            }
        }
    }
}
