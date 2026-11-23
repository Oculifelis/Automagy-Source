package com.automagy.filter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * One input and one material in a crafting grid: the input turns into the result and keeps its components (what
 * newer versions do with {@code minecraft:crafting_transmute}).
 */
public class ListTransmuteRecipe extends CustomRecipe {
    public static final MapCodec<ListTransmuteRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(CustomRecipe::category),
            Ingredient.CODEC_NONEMPTY.fieldOf("input").forGetter(r -> r.input),
            Ingredient.CODEC_NONEMPTY.fieldOf("material").forGetter(r -> r.material),
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("result").forGetter(r -> r.result)
    ).apply(i, ListTransmuteRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, ListTransmuteRecipe> STREAM_CODEC = StreamCodec.composite(
            CraftingBookCategory.STREAM_CODEC, CustomRecipe::category,
            Ingredient.CONTENTS_STREAM_CODEC, r -> r.input,
            Ingredient.CONTENTS_STREAM_CODEC, r -> r.material,
            ByteBufCodecs.registry(net.minecraft.core.registries.Registries.ITEM), r -> r.result,
            ListTransmuteRecipe::new);
    public static final RecipeSerializer<ListTransmuteRecipe> SERIALIZER = new RecipeSerializer<>() {
        @Override
        public MapCodec<ListTransmuteRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, ListTransmuteRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    };

    private final Ingredient input;
    private final Ingredient material;
    private final Item result;

    public ListTransmuteRecipe(CraftingBookCategory category, Ingredient input, Ingredient material, Item result) {
        super(category);
        this.input = input;
        this.material = material;
        this.result = result;
    }

    @Override
    public boolean matches(CraftingInput grid, Level level) {
        return find(grid) >= 0;
    }

    /** The slot of the input, or -1 when the grid is not exactly one input and one material. */
    private int find(CraftingInput grid) {
        int in = -1;
        boolean mat = false;
        for (int i = 0; i < grid.size(); i++) {
            ItemStack s = grid.getItem(i);
            if (s.isEmpty()) {
                continue;
            }
            if (in < 0 && input.test(s)) {
                in = i;
            } else if (!mat && material.test(s)) {
                mat = true;
            } else {
                return -1;
            }
        }
        return mat ? in : -1;
    }

    @Override
    public ItemStack assemble(CraftingInput grid, HolderLookup.Provider registries) {
        int in = find(grid);
        return in < 0 ? ItemStack.EMPTY : grid.getItem(in).transmuteCopy(result, 1);
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return new ItemStack(result);
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return NonNullList.of(Ingredient.EMPTY, input, material);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<ListTransmuteRecipe> getSerializer() {
        return SERIALIZER;
    }
}
