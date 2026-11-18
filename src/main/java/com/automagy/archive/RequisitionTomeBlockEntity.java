package com.automagy.archive;

import com.automagy.AMRegistry;
import com.leclowndu93150.thaumaturge.api.aura.AuraHelper;
import com.leclowndu93150.thaumaturge.content.device.mirror.BlockEntityMirror;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ObjIntConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

/**
 * The requisition tome. It lists the whole Inventarium and takes orders, which its core hands to taskmasters. It has
 * three slots: an input slot whose items go through the input mirror, and two attuned crystalline eyes, one on the
 * input mirror (used like a melding mirror's eye) and one on the output mirror dispersing mirrors deliver to. Each
 * order costs {@value #VIS_PER_ORDER} vis from the aura. A tally box on it counts what is still on order.
 */
public class RequisitionTomeBlockEntity extends ArchiveNodeBlockEntity implements TallySource {
    public static final float VIS_PER_ORDER = 0.5f;
    public static final int INPUT = 0;
    public static final int INPUT_EYE = 1;
    public static final int OUTPUT_EYE = 2;
    private static final Codec<List<InventariumCoreBlockEntity.ItemStackCount>> ORDERS = RecordCodecBuilder.<InventariumCoreBlockEntity.ItemStackCount>create(i -> i.group(
            ItemResource.CODEC.fieldOf("item").forGetter(InventariumCoreBlockEntity.ItemStackCount::item),
            Codec.INT.fieldOf("count").forGetter(InventariumCoreBlockEntity.ItemStackCount::count))
            .apply(i, InventariumCoreBlockEntity.ItemStackCount::new)).listOf();

    private final SimpleContainer slots = new SimpleContainer(3) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return slot == INPUT || stack.is(AMRegistry.CRYSTALLINE_EYE.get());
        }

        @Override
        public void setChanged() {
            super.setChanged();
            RequisitionTomeBlockEntity.this.setChanged();
            sendInput();
        }
    };
    private final Map<ItemResource, Integer> onOrder = new HashMap<>();

    public RequisitionTomeBlockEntity(BlockPos pos, BlockState state) {
        super(AMRegistry.REQUISITION_TOME_BE.get(), pos, state);
    }

    @Override
    public InventariumCoreBlockEntity.Kind kind() {
        return InventariumCoreBlockEntity.Kind.TOME;
    }

    SimpleContainer slots() {
        return slots;
    }

    public @Nullable GlobalPos outputMirror() {
        return slots.getItem(OUTPUT_EYE).get(AMRegistry.LINK.get());
    }

    Map<ItemResource, Integer> onOrder() {
        return onOrder;
    }

    /** Puts the input slot's items through the input mirror: out of the partner of the mirror its eye watches. */
    private void sendInput() {
        ItemStack stack = slots.getItem(INPUT);
        GlobalPos watched = slots.getItem(INPUT_EYE).get(AMRegistry.LINK.get());
        if (stack.isEmpty() || watched == null || !(level instanceof ServerLevel server)) {
            return;
        }
        ServerLevel there = server.getServer().getLevel(watched.dimension());
        if (there == null || !there.isLoaded(watched.pos()) || !(there.getBlockEntity(watched.pos()) instanceof BlockEntityMirror mirror)
                || !mirror.paired || mirror.link == null || !mirror.verifyPairing()) {
            return;
        }
        ServerLevel far = server.getServer().getLevel(mirror.link.dimension());
        if (far != null && far.getBlockEntity(mirror.link.pos()) instanceof BlockEntityMirror out && out.transportDirect(stack.copy())) {
            slots.setItem(INPUT, ItemStack.EMPTY);
        }
    }

    /** Places an order; the answer says how it went. */
    Component order(Map<ItemResource, Integer> wanted) {
        InventariumCoreBlockEntity core = core();
        if (core == null) {
            return Component.translatable("automagia.tome.unlinked");
        }
        List<GolemTaskmasterBlockEntity> masters = new ArrayList<>();
        for (BlockPos p : core.nodes(InventariumCoreBlockEntity.Kind.TASKMASTER)) {
            if (level.isLoaded(p) && level.getBlockEntity(p) instanceof GolemTaskmasterBlockEntity t && t.core() == core) {
                masters.add(t);
            }
        }
        if (masters.isEmpty()) {
            return Component.translatable("automagia.tome.no_taskmaster");
        }
        if (AuraHelper.drainVis(level, worldPosition, VIS_PER_ORDER, true) < VIS_PER_ORDER) {
            return Component.translatable("automagia.tome.vis_low");
        }
        AuraHelper.drainVis(level, worldPosition, VIS_PER_ORDER, false);
        int n = 0;
        for (var e : wanted.entrySet()) {
            int left = e.getValue();
            while (left > 0) {
                int chunk = Math.min(left, e.getKey().getMaxStackSize());
                GolemTaskmasterBlockEntity master = masters.get(n++ % masters.size());
                master.enqueue(worldPosition, e.getKey(), chunk);
                left -= chunk;
            }
            onOrder.merge(e.getKey(), e.getValue(), Integer::sum);
        }
        setChanged();
        return Component.translatable("automagia.tome.ordered");
    }

    /** An order (or part of one) arrived, or will not come. */
    void settled(ItemResource item, int count) {
        onOrder.computeIfPresent(item, (k, v) -> v - count > 0 ? v - count : null);
        setChanged();
    }

    @Override
    public void tally(ObjIntConsumer<ItemResource> out) {
        onOrder.forEach(out::accept);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            Containers.dropContents(level, pos, slots);
        }
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        slots.clearContent();
        in.read("slots", ItemStack.OPTIONAL_CODEC.listOf()).ifPresent(list -> {
            for (int i = 0; i < Math.min(list.size(), slots.getContainerSize()); i++) {
                slots.setItem(i, list.get(i));
            }
        });
        onOrder.clear();
        in.read("on_order", ORDERS).ifPresent(list -> list.forEach(e -> onOrder.put(e.item(), e.count())));
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        List<ItemStack> list = new ArrayList<>();
        for (int i = 0; i < slots.getContainerSize(); i++) {
            list.add(slots.getItem(i));
        }
        out.store("slots", ItemStack.OPTIONAL_CODEC.listOf(), list);
        out.store("on_order", ORDERS, onOrder.entrySet().stream()
                .map(e -> new InventariumCoreBlockEntity.ItemStackCount(e.getKey(), e.getValue())).toList());
    }
}
