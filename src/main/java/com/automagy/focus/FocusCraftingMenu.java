package com.automagy.focus;

import com.automagy.AMRegistry;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.casters.ICaster;
import com.leclowndu93150.thaumaturge.api.recipe.ArcaneCraftingTransaction;
import com.leclowndu93150.thaumaturge.api.recipe.ArcaneWorkbenchContext;
import com.leclowndu93150.thaumaturge.api.recipe.IArcaneCraftingStore;
import com.leclowndu93150.thaumaturge.content.recipe.workbench.ArcaneCraftingInput;
import com.leclowndu93150.thaumaturge.content.workbench.InventoryArcaneWorkbench;
import com.leclowndu93150.thaumaturge.content.workbench.MenuArcaneWorkbench;
import com.leclowndu93150.thaumaturge.content.workbench.SlotCrystalEssentia;
import com.leclowndu93150.thaumaturge.content.workbench.WorkbenchCraftingStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The crafting focus's grid: Thaumaturge's arcane workbench layout (3x3 grid, six crystal slots) with no wand slot, since
 * the caster that opened it pays. Crafts go through Thaumaturge's own virtual workbench, so every arcane recipe, its
 * research gate and its vis and crystal costs work as at a placed workbench.
 */
public class FocusCraftingMenu extends AbstractContainerMenu {
    static final int RESULT = 0;
    private static final int GRID_END = 1 + InventoryArcaneWorkbench.CRAFTING_SLOTS + InventoryArcaneWorkbench.CRYSTAL_SLOTS;
    private static final int WAND = InventoryArcaneWorkbench.WAND_SLOT;

    private final InventoryArcaneWorkbench grid = new InventoryArcaneWorkbench();
    private final ResultContainer result = new ResultContainer();
    private final Player player;
    private final int casterSlot;
    private final float craftVis;
    private final FocusCrafting.Upgrades upgrades;
    private final DataSlot visLow = DataSlot.standalone();
    private boolean updating;
    private boolean resultArcane;

    public FocusCraftingMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, buf.readVarInt(), 0f, FocusCrafting.Upgrades.NONE);
    }

    FocusCraftingMenu(int id, Inventory inv, int casterSlot, float craftVis, FocusCrafting.Upgrades upgrades) {
        super(AMRegistry.FOCUS_CRAFTING_MENU.get(), id);
        this.player = inv.player;
        this.casterSlot = casterSlot;
        this.craftVis = craftVis;
        this.upgrades = upgrades;
        addSlot(new Slot(result, 0, 160, 64) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                addSlot(new Slot(grid, col + row * 3, 41 + col * 23, 41 + row * 23));
            }
        }
        for (int i = 0; i < MenuArcaneWorkbench.PRIMAL_ORDER.size(); i++) {
            addSlot(new SlotCrystalEssentia(grid, InventoryArcaneWorkbench.CRAFTING_SLOTS + i, MenuArcaneWorkbench.CRYSTAL_X[i],
                    MenuArcaneWorkbench.CRYSTAL_Y[i], MenuArcaneWorkbench.PRIMAL_ORDER.get(i)));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, 16 + col * 18, 151 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, 16 + col * 18, 209));
        }
        addDataSlot(visLow);
        if (!player.level().isClientSide()) {
            restoreStored();
            grid.addChangedListener(this::updateResult);
            updateResult();
        }
    }

    public boolean visLow() {
        return visLow.get() != 0;
    }

    private ItemStack caster() {
        return player.getInventory().getItem(casterSlot);
    }

    private ItemStack focus() {
        ItemStack caster = caster();
        return caster.getItem() instanceof ICaster c ? c.getFocusStack(caster) : ItemStack.EMPTY;
    }

    private ArcaneWorkbenchContext context(ServerPlayer sp) {
        return ArcaneWorkbenchContext.virtual(sp, sp.getUUID(), sp.getUUID());
    }

    private void updateResult() {
        if (updating || !(player instanceof ServerPlayer sp)) {
            return;
        }
        updating = true;
        try {
            grid.setItem(WAND, caster().copy());
            ItemStack out = ItemStack.EMPTY;
            boolean low = false;
            resultArcane = false;
            ArcaneCraftingTransaction.Result arcane = ArcaneCraftingTransaction.preview(context(sp), sp, grid.asArcaneCraftInput());
            if (arcane.successful()) {
                resultArcane = true;
                if (surcharge() && !((ICaster) caster().getItem()).consumeVis(caster(), sp, craftVis, true, true)) {
                    low = true;
                } else {
                    out = arcane.output();
                }
            } else {
                out = vanilla(sp).map(r -> r.value().assemble(grid.asCraftInput())).orElse(ItemStack.EMPTY);
            }
            result.setItem(0, out);
            visLow.set(low ? 1 : 0);
        } finally {
            grid.setItem(WAND, ItemStack.EMPTY);
            updating = false;
        }
    }

    private boolean surcharge() {
        return !upgrades.gratis() && craftVis > 0 && caster().getItem() instanceof ICaster;
    }

    private Optional<RecipeHolder<CraftingRecipe>> vanilla(ServerPlayer sp) {
        return sp.level().recipeAccess().getRecipeFor(RecipeType.CRAFTING, grid.asCraftInput(), sp.level());
    }

    /** One craft, consuming the grid; empty if nothing could be made. */
    private ItemStack craft(ServerPlayer sp) {
        if (result.getItem(0).isEmpty() || !(caster().getItem() instanceof ICaster caster)) {
            return ItemStack.EMPTY;
        }
        updating = true;
        ItemStack out = ItemStack.EMPTY;
        try (Transaction tx = Transaction.openRoot()) {
            grid.setItem(WAND, caster().copy());
            if (resultArcane) {
                ArcaneCraftingInput.Positioned pos = grid.asPositionedArcaneCraftInput();
                WorkbenchCraftingStore store = new WorkbenchCraftingStore(grid, sp, pos.left(), pos.top(),
                        pos.input().width(), pos.input().height());
                ArcaneCraftingTransaction.Result r = ArcaneCraftingTransaction.craft(context(sp), sp, pos.input(), store, tx);
                out = r.successful() ? r.output() : ItemStack.EMPTY;
            } else {
                Optional<RecipeHolder<CraftingRecipe>> recipe = vanilla(sp);
                if (recipe.isPresent()) {
                    CraftingInput.Positioned pos = grid.asPositionedCraftInput();
                    WorkbenchCraftingStore store = new WorkbenchCraftingStore(grid, sp, pos.left(), pos.top(),
                            pos.input().width(), pos.input().height());
                    ItemStack made = recipe.get().value().assemble(pos.input());
                    if (store.consume(new IArcaneCraftingStore.Consumption(pos.input().items(),
                            recipe.get().value().getRemainingItems(pos.input()), AspectList.EMPTY, grid.wandStack()), tx)) {
                        out = made;
                    }
                }
            }
            if (!out.isEmpty()) {
                tx.commit();
            }
        } finally {
            ItemStack paid = grid.getItem(WAND);
            if (!out.isEmpty() && !paid.isEmpty()) {
                player.getInventory().setItem(casterSlot, paid);
            }
            grid.setItem(WAND, ItemStack.EMPTY);
            updating = false;
        }
        if (!out.isEmpty()) {
            if (resultArcane && surcharge()) {
                caster.consumeVis(caster(), sp, craftVis, true, false);
            }
            out.onCraftedBy(sp, out.getCount());
        }
        updateResult();
        return out;
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput input, Player who) {
        if (slotId != RESULT) {
            super.clicked(slotId, button, input, who);
            return;
        }
        if (!(who instanceof ServerPlayer sp)) {
            return;
        }
        if (input == ContainerInput.QUICK_MOVE) {
            for (int n = 0; n < 64; n++) {
                ItemStack made = craft(sp);
                if (made.isEmpty()) {
                    break;
                }
                if (!moveItemStackTo(made, GRID_END, slots.size(), true)) {
                    who.drop(made, false);
                    break;
                }
                if (!made.isEmpty()) {
                    who.drop(made, false);
                }
            }
        } else if (input == ContainerInput.PICKUP) {
            ItemStack carried = getCarried();
            ItemStack shown = result.getItem(0);
            if (shown.isEmpty() || (!carried.isEmpty() && (!ItemStack.isSameItemSameComponents(carried, shown)
                    || carried.getCount() + shown.getCount() > carried.getMaxStackSize()))) {
                return;
            }
            ItemStack made = craft(sp);
            if (!made.isEmpty()) {
                if (carried.isEmpty()) {
                    setCarried(made);
                } else {
                    carried.grow(made.getCount());
                }
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player who, int index) {
        Slot slot = slots.get(index);
        if (index == RESULT || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        boolean moved = index < GRID_END ? moveItemStackTo(stack, GRID_END, slots.size(), true)
                : moveItemStackTo(stack, 1 + InventoryArcaneWorkbench.CRAFTING_SLOTS, GRID_END, false)
                || moveItemStackTo(stack, 1, 1 + InventoryArcaneWorkbench.CRAFTING_SLOTS, false);
        if (!moved) {
            return ItemStack.EMPTY;
        }
        slot.setChanged();
        return before;
    }

    @Override
    public boolean stillValid(Player who) {
        return caster().getItem() instanceof ICaster && FocusCrafting.node(focus()) != null;
    }

    @Override
    public void removed(Player who) {
        super.removed(who);
        if (who.level().isClientSide()) {
            return;
        }
        List<ItemStack> left = new ArrayList<>();
        for (int i = 0; i < WAND; i++) {
            left.add(grid.removeItemNoUpdate(i));
        }
        ItemStack casterStack = caster();
        if (upgrades.storage() && casterStack.getItem() instanceof ICaster c && FocusCrafting.node(focus()) != null) {
            ItemStack focus = c.getFocusStack(casterStack).copy();
            focus.set(AMRegistry.STORED_GRID.get(), ItemContainerContents.fromItems(left));
            c.setFocus(casterStack, focus);
            return;
        }
        left.forEach(s -> {
            if (!s.isEmpty() && !who.getInventory().add(s)) {
                who.drop(s, false);
            }
        });
    }

    /** A storage-upgraded focus puts back what was left in the grid last time. */
    private void restoreStored() {
        ItemStack casterStack = caster();
        if (!(casterStack.getItem() instanceof ICaster c)) {
            return;
        }
        ItemStack focus = c.getFocusStack(casterStack);
        ItemContainerContents stored = focus.get(AMRegistry.STORED_GRID.get());
        if (stored == null) {
            return;
        }
        NonNullList<ItemStack> items = NonNullList.withSize(WAND, ItemStack.EMPTY);
        stored.copyInto(items);
        for (int i = 0; i < WAND; i++) {
            grid.setItem(i, items.get(i));
        }
        ItemStack emptied = focus.copy();
        emptied.remove(AMRegistry.STORED_GRID.get());
        c.setFocus(casterStack, emptied);
    }
}
