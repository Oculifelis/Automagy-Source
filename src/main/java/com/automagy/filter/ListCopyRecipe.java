package com.automagy.filter;

import com.automagy.AMRegistry;
import com.automagy.item.RunicListItem;
import net.minecraft.core.NonNullList;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Two runic lists in a crafting grid: the first one's entries are written onto the second, of either kind, and the
 * first comes back unused.
 */
public class ListCopyRecipe extends CustomRecipe {
    public static final RecipeSerializer<ListCopyRecipe> SERIALIZER = new SimpleCraftingRecipeSerializer<>(ListCopyRecipe::new);

    public ListCopyRecipe(CraftingBookCategory category) {
        super(category);
    }

    /** Slot indices of the source and target lists, or null when the grid is not exactly two lists. */
    private static int[] find(CraftingInput input) {
        int[] found = {-1, -1};
        int n = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
            if (s.isEmpty()) {
                continue;
            }
            if (!(s.getItem() instanceof RunicListItem) || n == 2) {
                return null;
            }
            found[n++] = i;
        }
        return n == 2 && input.getItem(found[0]).has(AMRegistry.RUNIC_LIST.get()) ? found : null;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return find(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        int[] f = find(input);
        if (f == null) {
            return ItemStack.EMPTY;
        }
        ItemStack out = input.getItem(f[1]).copyWithCount(1);
        out.set(AMRegistry.RUNIC_LIST.get(), input.getItem(f[0]).get(AMRegistry.RUNIC_LIST.get()));
        return out;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> left = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        int[] f = find(input);
        if (f != null) {
            left.set(f[0], input.getItem(f[0]).copyWithCount(1));
        }
        return left;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<ListCopyRecipe> getSerializer() {
        return SERIALIZER;
    }
}
