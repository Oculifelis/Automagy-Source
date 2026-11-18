package com.automagy.focus;

import com.automagy.Automagy;
import com.leclowndu93150.thaumaturge.api.casters.ICaster;
import com.leclowndu93150.thaumaturge.api.recipe.ResearchGate;
import com.leclowndu93150.thaumaturge.api.spell.SpellNode;
import com.leclowndu93150.thaumaturge.api.spell.Spells;
import com.leclowndu93150.thaumaturge.api.spell.part.SpellPart;
import com.leclowndu93150.thaumaturge.content.casters.FocusPouchItem;
import java.util.Optional;
import java.util.function.ToIntFunction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;

/** Opening the crafting focus's grid: by casting it, or by the crafting key with a caster on the hotbar. */
public final class FocusCrafting {
    public static final ResourceKey<SpellPart> PART = ResourceKey.create(SpellPart.REGISTRY_KEY, Automagy.id("crafting"));
    /** The upgrade settings are honoured only once this research is done. */
    public static final ResearchGate UPGRADES = new ResearchGate(Automagy.id("focus_crafting_upgrades"), Optional.empty(), false);

    private FocusCrafting() {
    }

    /** The crafting effect's upgrade settings, each 0 or 1 in the focal manipulator. */
    public record Upgrades(boolean gratis, boolean storage, boolean transposing) {
        static final Upgrades NONE = new Upgrades(false, false, false);

        static Upgrades of(Player player, ToIntFunction<String> setting) {
            if (!ResearchGate.passes(player, UPGRADES)) {
                return NONE;
            }
            return new Upgrades(setting.applyAsInt("gratis") > 0, setting.applyAsInt("storage") > 0,
                    setting.applyAsInt("transposing") > 0);
        }
    }

    static void openFromCast(ServerPlayer player, CraftingGridEffect effect, Upgrades upgrades) {
        for (InteractionHand hand : InteractionHand.values()) {
            if (player.getItemInHand(hand).getItem() instanceof ICaster) {
                open(player, hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : Inventory.SLOT_OFFHAND,
                        effect, upgrades);
                return;
            }
        }
    }

    /** The crafting key: the first hotbar caster holding a crafting focus opens it; failing that, a transposing focus
     *  anywhere in the inventory or a focus pouch is put on the first hotbar caster and opened. */
    public static void openFromKey(ServerPlayer player) {
        Inventory inv = player.getInventory();
        int firstCaster = -1;
        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            ItemStack stack = inv.getItem(slot);
            if (stack.getItem() instanceof ICaster caster) {
                firstCaster = firstCaster < 0 ? slot : firstCaster;
                if (tryOpen(player, slot, caster.getFocusStack(stack))) {
                    return;
                }
            }
        }
        if (firstCaster < 0) {
            return;
        }
        ItemStack casterStack = inv.getItem(firstCaster);
        ICaster caster = (ICaster) casterStack.getItem();
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            ItemStack stack = inv.getItem(slot);
            if (transposes(player, stack)) {
                ItemStack old = caster.getFocusStack(casterStack);
                caster.setFocus(casterStack, stack.copyWithCount(1));
                stack.shrink(1);
                if (!old.isEmpty() && !inv.add(old)) {
                    player.drop(old, false);
                }
                tryOpen(player, firstCaster, caster.getFocusStack(casterStack));
                return;
            }
            if (stack.getItem() instanceof FocusPouchItem && fromPouch(player, stack, firstCaster)) {
                return;
            }
        }
        int casterSlot = firstCaster;
        CuriosApi.getCuriosInventory(player).ifPresent(curios -> {
            for (var found : curios.findCurios(s -> s.getItem() instanceof FocusPouchItem)) {
                if (fromPouch(player, found.stack(), casterSlot)) {
                    return;
                }
            }
        });
    }

    private static boolean fromPouch(ServerPlayer player, ItemStack pouch, int casterSlot) {
        NonNullList<ItemStack> contents = FocusPouchItem.getInventory(pouch);
        for (int i = 0; i < contents.size(); i++) {
            if (transposes(player, contents.get(i))) {
                ItemStack casterStack = player.getInventory().getItem(casterSlot);
                ICaster caster = (ICaster) casterStack.getItem();
                ItemStack old = caster.getFocusStack(casterStack);
                caster.setFocus(casterStack, contents.get(i).copyWithCount(1));
                contents.set(i, old);
                FocusPouchItem.setInventory(pouch, contents);
                return tryOpen(player, casterSlot, caster.getFocusStack(casterStack));
            }
        }
        return false;
    }

    private static boolean transposes(Player player, ItemStack focus) {
        SpellNode node = node(focus);
        return node != null && Upgrades.of(player, k -> node.settings().getOrDefault(k, 0)).transposing();
    }

    private static boolean tryOpen(ServerPlayer player, int casterSlot, ItemStack focus) {
        SpellNode node = node(focus);
        if (node == null) {
            return false;
        }
        CraftingGridEffect effect = Spells.part(player.registryAccess(), PART)
                .map(p -> p.behavior() instanceof CraftingGridEffect e ? e : null)
                .orElse(new CraftingGridEffect(2f, 1f));
        open(player, casterSlot, effect, Upgrades.of(player, k -> node.settings().getOrDefault(k, 0)));
        return true;
    }

    /** The crafting node in a focus's spell, if it has one. */
    static @Nullable SpellNode node(ItemStack focus) {
        if (focus.isEmpty()) {
            return null;
        }
        for (SpellNode n : Spells.spellOf(focus).nodes()) {
            if (n.part().equals(PART)) {
                return n;
            }
        }
        return null;
    }

    private static void open(ServerPlayer player, int casterSlot, CraftingGridEffect effect, Upgrades upgrades) {
        ItemStack casterStack = player.getInventory().getItem(casterSlot);
        if (!(casterStack.getItem() instanceof ICaster caster)) {
            return;
        }
        if (!upgrades.gratis() && effect.openVis() > 0 && !caster.consumeVis(casterStack, player, effect.openVis(), false, false)) {
            player.displayClientMessage(Component.translatable("automagia.focus_crafting.vis_low"), true);
            return;
        }
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new FocusCraftingMenu(id, inv, casterSlot, effect.craftVis(), upgrades),
                Component.translatable("spell.automagia.crafting.name")), buf -> buf.writeVarInt(casterSlot));
    }
}
