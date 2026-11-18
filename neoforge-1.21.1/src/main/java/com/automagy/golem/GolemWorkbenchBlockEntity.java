package com.automagy.golem;

import com.automagy.AMRegistry;
import com.leclowndu93150.thaumaturge.api.aspect.AspectCapabilities;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aspect.IAspectContainer;
import com.leclowndu93150.thaumaturge.api.aspect.TTAspects;
import com.leclowndu93150.thaumaturge.content.golem.BlockGolemFetter;
import com.leclowndu93150.thaumaturge.content.golem.EntityThaumaturgeGolem;
import com.leclowndu93150.thaumaturge.registry.TTBlocks;
import com.leclowndu93150.thaumaturge.registry.TTGolemTraits;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.automagy.port.ValueInput;
import com.automagy.port.ValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * The golem's workbench. Up to three 3x3 patterns (one, plus one per fire upgrade) and 27 storage slots holding
 * materials and products alike. While a golem linked to it (golem linking bell) stands within three blocks and no
 * powered golem fetter is under it, the golem works through the patterns: a pattern is crafted when the storage holds
 * its ingredients and less than one craft of its product, so golems taking the products away with seals keep the
 * bench busy. Golems below rank 2 that are not smart only follow patterns that fit 2x2 or use one kind of ingredient.
 * The golem tires with each craft and slows; a jar of fabrico within four blocks refreshes it.
 */
public class GolemWorkbenchBlockEntity extends BlockEntity implements com.automagy.port.PreRemove, GolemLinks.Device, MenuProvider {
    public static final int PATTERNS = 3;
    public static final int STORAGE = 27;
    private static final int WORK = 100;
    private static final int TIRED = 100;
    private static final int FABRICO_RANGE = 4;

    private final Set<UUID> golems = new HashSet<>();
    private final SimpleContainer patterns = new SimpleContainer(PATTERNS * 9) {
        @Override
        public void setChanged() {
            GolemWorkbenchBlockEntity.this.setChanged();
        }
    };
    private final SimpleContainer storage = new SimpleContainer(STORAGE) {
        @Override
        public void setChanged() {
            GolemWorkbenchBlockEntity.this.setChanged();
        }
    };
    private final Map<Direction, ItemStack> upgrades = new EnumMap<>(Direction.class);
    private int progress;
    private int fatigue;
    private int next;
    /** Synced to the menu: how many patterns are open. */
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return openPatterns();
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return 1;
        }
    };

    public GolemWorkbenchBlockEntity(BlockPos pos, BlockState state) {
        super(AMRegistry.GOLEM_WORKBENCH_BE.get(), pos, state);
    }

    public Container storage() {
        return storage;
    }

    @Override
    public Set<UUID> golems() {
        return golems;
    }

    @Override
    public void linksChanged() {
        setChanged();
    }

    // ---- upgrades

    public ItemStack upgrade(Direction side) {
        return upgrades.getOrDefault(side, ItemStack.EMPTY);
    }

    public int count(WorkbenchUpgrade.Effect effect) {
        int n = 0;
        for (ItemStack s : upgrades.values()) {
            WorkbenchUpgrade u = WorkbenchUpgrade.of(s);
            n += u != null && u.effect() == effect ? 1 : 0;
        }
        return n;
    }

    private int sum(WorkbenchUpgrade.Effect effect) {
        int n = 0;
        for (ItemStack s : upgrades.values()) {
            WorkbenchUpgrade u = WorkbenchUpgrade.of(s);
            n += u != null && u.effect() == effect ? u.amount() : 0;
        }
        return n;
    }

    /** Puts one of {@code stack} on {@code side}; false if that side is taken or the bench has its limit of it. */
    boolean addUpgrade(Direction side, ItemStack stack) {
        WorkbenchUpgrade u = WorkbenchUpgrade.of(stack);
        if (u == null || side.getAxis().isVertical() || !upgrade(side).isEmpty() || count(u.effect()) >= u.effect().limit) {
            return false;
        }
        upgrades.put(side, stack.split(1));
        changedUpgrades();
        return true;
    }

    ItemStack removeUpgrade(Direction side) {
        ItemStack s = upgrades.remove(side);
        if (s == null) {
            return ItemStack.EMPTY;
        }
        changedUpgrades();
        return s;
    }

    private void changedUpgrades() {
        setChanged();
        if (level != null) {
            level.setBlock(worldPosition, GolemWorkbenchBlock.withPlates(getBlockState(), this), 3);
        }
    }

    public int openPatterns() {
        return Math.min(PATTERNS, 1 + count(WorkbenchUpgrade.Effect.PATTERN));
    }

    // ---- work

    public static void serverTick(Level level, BlockPos pos, BlockState state, GolemWorkbenchBlockEntity be) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (level.getGameTime() % 20 == 0 && be.fatigue > 0) {
            be.fatigue--;
            be.refresh(server);
        }
        EntityThaumaturgeGolem golem = be.worker(server);
        if (golem == null || be.fettered()) {
            return;
        }
        double pace = 1 + be.sum(WorkbenchUpgrade.Effect.SPEED) / 100.0 * golem.getAttributeValue(Attributes.MOVEMENT_SPEED)
                + be.sum(WorkbenchUpgrade.Effect.STRENGTH) / 100.0 * golem.getAttributeValue(Attributes.ATTACK_DAMAGE);
        pace /= 1 + be.fatigue / (double) TIRED;
        be.progress += Math.max(1, (int) Math.round(pace * 10));
        if (be.progress < WORK * 10) {
            return;
        }
        be.progress = 0;
        if (be.craftOne(server, golem)) {
            golem.swingArm();
            be.fatigue += be.fatigueCost();
            be.setChanged();
        }
    }

    private int fatigueCost() {
        int cost = 10 + 5 * count(WorkbenchUpgrade.Effect.SPEED) + 15 * count(WorkbenchUpgrade.Effect.PLACEHOLDER)
                - sum(WorkbenchUpgrade.Effect.ENDURANCE) - 3 * count(WorkbenchUpgrade.Effect.STRENGTH);
        return Math.max(1, cost);
    }

    /** A linked golem, alive, within three blocks. */
    private @Nullable EntityThaumaturgeGolem worker(ServerLevel level) {
        for (UUID id : golems) {
            Entity e = level.getEntity(id);
            if (e instanceof EntityThaumaturgeGolem g && g.isAlive() && g.position().closerThan(worldPosition.getCenter(), 3.5)
                    && GolemLinks.linked(g, level, this, worldPosition)) {
                return g;
            }
        }
        return null;
    }

    private boolean fettered() {
        BlockState below = level.getBlockState(worldPosition.below());
        return below.is(TTBlocks.GOLEM_FETTER.get()) && below.getValue(BlockGolemFetter.POWERED);
    }

    /** Drinks one fabrico from a container nearby to take tiredness away. */
    private void refresh(ServerLevel level) {
        if (fatigue < TIRED / 2) {
            return;
        }
        Holder<IAspect> fabrico = level.registryAccess().lookupOrThrow(IAspect.REGISTRY_KEY).getOrThrow(TTAspects.FABRICO);
        for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-FABRICO_RANGE, -FABRICO_RANGE, -FABRICO_RANGE),
                worldPosition.offset(FABRICO_RANGE, FABRICO_RANGE, FABRICO_RANGE))) {
            if (p.equals(worldPosition)) {
                continue;
            }
            IAspectContainer c = level.getCapability(AspectCapabilities.CONTAINER, p, null);
            if (c != null && c.amountOf(fabrico) > 0 && c.drain(fabrico, 1)) {
                BlockEntity be = level.getBlockEntity(p);
                if (be != null) {
                    be.setChanged();
                }
                fatigue = Math.max(0, fatigue - TIRED / 2);
                return;
            }
        }
    }

    private boolean craftOne(ServerLevel level, EntityThaumaturgeGolem golem) {
        boolean advanced = golem.properties().rank() >= 2 || golem.properties().hasTrait(TTGolemTraits.SMART.get());
        int open = openPatterns();
        for (int k = 0; k < open; k++) {
            int p = (next + k) % open;
            List<ItemStack> pattern = pattern(p);
            if (pattern.stream().allMatch(ItemStack::isEmpty) || !advanced && !simple(pattern)) {
                continue;
            }
            for (List<ItemStack> grid : variants(pattern)) {
                if (tryCraft(level, grid)) {
                    next = (p + 1) % open;
                    return true;
                }
            }
        }
        return false;
    }

    private List<ItemStack> pattern(int p) {
        List<ItemStack> list = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            list.add(patterns.getItem(p * 9 + i));
        }
        return list;
    }

    /** Fits a 2x2 square, or uses one kind of ingredient. */
    private static boolean simple(List<ItemStack> pattern) {
        int minR = 3, maxR = -1, minC = 3, maxC = -1;
        ItemStack kind = ItemStack.EMPTY;
        boolean oneKind = true;
        for (int i = 0; i < 9; i++) {
            ItemStack s = pattern.get(i);
            if (s.isEmpty()) {
                continue;
            }
            minR = Math.min(minR, i / 3);
            maxR = Math.max(maxR, i / 3);
            minC = Math.min(minC, i % 3);
            maxC = Math.max(maxC, i % 3);
            if (kind.isEmpty()) {
                kind = s;
            } else if (!ItemStack.isSameItemSameComponents(kind, s)) {
                oneKind = false;
            }
        }
        return oneKind || maxR - minR < 2 && maxC - minC < 2;
    }

    /** The pattern as written; with the placeholder upgrade, also with each other stored item in the placeholder's place. */
    private List<List<ItemStack>> variants(List<ItemStack> pattern) {
        ItemStack holder = storage.getItem(0);
        if (count(WorkbenchUpgrade.Effect.PLACEHOLDER) == 0 || holder.isEmpty()
                || pattern.stream().noneMatch(s -> ItemStack.isSameItemSameComponents(s, holder))) {
            return List.of(pattern);
        }
        List<List<ItemStack>> out = new ArrayList<>();
        List<ItemStack> seen = new ArrayList<>();
        for (int i = 1; i < STORAGE; i++) {
            ItemStack s = storage.getItem(i);
            if (s.isEmpty() || ItemStack.isSameItemSameComponents(s, holder)
                    || seen.stream().anyMatch(t -> ItemStack.isSameItemSameComponents(t, s))) {
                continue;
            }
            seen.add(s);
            List<ItemStack> grid = new ArrayList<>(9);
            for (ItemStack p : pattern) {
                grid.add(ItemStack.isSameItemSameComponents(p, holder) ? s.copyWithCount(1) : p);
            }
            out.add(grid);
        }
        return out;
    }

    private boolean tryCraft(ServerLevel level, List<ItemStack> grid) {
        CraftingInput input = CraftingInput.of(3, 3, grid.stream().map(s -> s.copyWithCount(1)).toList());
        RecipeHolder<CraftingRecipe> recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).orElse(null);
        if (recipe == null) {
            return false;
        }
        ItemStack result = recipe.value().assemble(input, level.registryAccess());
        if (result.isEmpty() || held(result) >= result.getCount()) {
            return false;
        }
        int first = count(WorkbenchUpgrade.Effect.PLACEHOLDER) > 0 ? 1 : 0;
        List<ItemStack> need = new ArrayList<>();
        for (ItemStack s : grid) {
            if (!s.isEmpty()) {
                need.add(s);
            }
        }
        SimpleContainer trial = copy();
        for (ItemStack s : need) {
            if (!take(trial, s, first)) {
                return false;
            }
        }
        NonNullList<ItemStack> rest = recipe.value().getRemainingItems(input);
        if (!trial.canAddItem(result)) {
            return false;
        }
        trial.addItem(result.copy());
        List<ItemStack> thrown = new ArrayList<>();
        boolean tidy = count(WorkbenchUpgrade.Effect.TIDY) > 0;
        for (ItemStack r : rest) {
            if (r.isEmpty()) {
                continue;
            }
            ItemStack left = tidy ? trial.addItem(r.copy()) : r.copy();
            if (!left.isEmpty()) {
                thrown.add(left);
            }
        }
        for (int i = 0; i < STORAGE; i++) {
            storage.setItem(i, trial.getItem(i));
        }
        for (ItemStack t : thrown) {
            ItemEntity e = new ItemEntity(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.1, worldPosition.getZ() + 0.5, t);
            e.setDeltaMovement(level.getRandom().nextGaussian() * 0.05, 0.3, level.getRandom().nextGaussian() * 0.05);
            level.addFreshEntity(e);
        }
        return true;
    }

    private int held(ItemStack product) {
        int n = 0;
        for (int i = 0; i < STORAGE; i++) {
            ItemStack s = storage.getItem(i);
            n += ItemStack.isSameItemSameComponents(s, product) ? s.getCount() : 0;
        }
        return n;
    }

    private SimpleContainer copy() {
        SimpleContainer c = new SimpleContainer(STORAGE);
        for (int i = 0; i < STORAGE; i++) {
            c.setItem(i, storage.getItem(i).copy());
        }
        return c;
    }

    private static boolean take(SimpleContainer from, ItemStack what, int first) {
        for (int i = first; i < from.getContainerSize(); i++) {
            ItemStack s = from.getItem(i);
            if (ItemStack.isSameItemSameComponents(s, what)) {
                s.shrink(1);
                return true;
            }
        }
        return false;
    }

    // ---- menu, drops, saving

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new GolemWorkbenchMenu(id, inv, patterns, storage, data, worldPosition);
    }

    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null) {
            Containers.dropContents(level, pos, storage);
            for (ItemStack s : upgrades.values()) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, s);
            }
        }
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag$, net.minecraft.core.HolderLookup.Provider reg$) {
        ValueInput in = ValueInput.of(tag$, reg$);
        super.loadAdditional(in.tag(), in.registries());
        golems.clear();
        in.read("golems", UUIDUtil.CODEC.listOf()).ifPresent(golems::addAll);
        readList(in, "patterns", patterns);
        readList(in, "storage", storage);
        upgrades.clear();
        for (Direction d : Direction.Plane.HORIZONTAL) {
            in.read("upgrade_" + d.getSerializedName(), ItemStack.CODEC).ifPresent(s -> upgrades.put(d, s));
        }
        progress = in.getIntOr("progress", 0);
        fatigue = in.getIntOr("fatigue", 0);
        next = in.getIntOr("next", 0);
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag$, net.minecraft.core.HolderLookup.Provider reg$) {
        ValueOutput out = ValueOutput.of(tag$, reg$);
        super.saveAdditional(out.tag(), out.registries());
        out.store("golems", UUIDUtil.CODEC.listOf(), List.copyOf(golems));
        writeList(out, "patterns", patterns);
        writeList(out, "storage", storage);
        upgrades.forEach((d, s) -> out.store("upgrade_" + d.getSerializedName(), ItemStack.CODEC, s));
        out.putInt("progress", progress);
        out.putInt("fatigue", fatigue);
        out.putInt("next", next);
    }

    private static void readList(ValueInput in, String key, SimpleContainer into) {
        List<ItemStack> list = in.read(key, ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
        for (int i = 0; i < into.getContainerSize(); i++) {
            into.setItem(i, i < list.size() ? list.get(i) : ItemStack.EMPTY);
        }
    }

    private static void writeList(ValueOutput out, String key, SimpleContainer from) {
        List<ItemStack> list = new ArrayList<>(from.getContainerSize());
        for (int i = 0; i < from.getContainerSize(); i++) {
            list.add(from.getItem(i));
        }
        out.store(key, ItemStack.OPTIONAL_CODEC.listOf(), list);
    }
}
